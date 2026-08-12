package com.kogen.giraffe

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.kogen.giraffe.analizer.GiraffeMessageAnalyzer
import com.kogen.giraffe.db.dao.GiraffeLogDao
import com.kogen.giraffe.db.entity.GiraffeChatEntity
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.db.entity.GiraffeHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeMessageEntity
import com.kogen.giraffe.di.inject
import com.kogen.giraffe.di.setApplicationContext
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private val TAG = GiraffeInterceptor::class.java.simpleName

/**
 * A gRPC [ClientInterceptor] that logs every call's headers and messages, persists them to
 * Giraffe's own database, and surfaces a live notification for each call - giving an in-app debug
 * view of gRPC traffic without touching server-side logs or a network proxy.
 *
 * Attach it once when building the [io.grpc.Channel]/`ManagedChannel`, e.g.:
 * ```
 * ManagedChannelBuilder.forAddress(host, port)
 *     .intercept(GiraffeInterceptor(context))
 *     .build()
 * ```
 * Tapping the notification (or a chat row in the in-app log viewer) opens
 * [com.kogen.giraffe.ui.common.main.GiraffeActivity] with the full request/response history for
 * that call.
 *
 * @param context used to bootstrap Giraffe's DI graph and database; the application context is
 * retained internally, so a short-lived `Activity` context is safe to pass here. Also used to
 * read whether the host app is debuggable - see [isEnabled].
 * @param loggingEnabled when `false`, suppresses this interceptor's own `Log.d` output while
 * still recording traffic to the database and notifications.
 */
class GiraffeInterceptor(
    context: Context,
    private val loggingEnabled: Boolean = true,
) : ClientInterceptor {

    /**
     * `true` only when the host app itself is debuggable (`android:debuggable` in its manifest,
     * which Android sets from the app's own build type - not this library's). Checking this
     * (rather than nothing) means the real implementation still refuses to do anything in a
     * non-debuggable build even if it somehow ended up on that build's classpath by mistake - e.g.
     * a plain `implementation` dependency instead of the intended
     * `debugImplementation(...:giraffe:...)` / `releaseImplementation(...:giraffe-no-op:...)`
     * split. This can't be read from Giraffe's own `BuildConfig.DEBUG`: the published AAR is
     * always built as the library's "release" variant regardless of which build type of the
     * *consuming* app it ends up in, so Giraffe has no build-type of its own to ask.
     */
    private val isEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var giraffeLogDao: GiraffeLogDao
    private lateinit var notificationService: GiraffeNotificationService
    private lateinit var analyzer: GiraffeMessageAnalyzer

    init {
        if (isEnabled) {
            setApplicationContext(context)
            giraffeLogDao = inject()
            notificationService = inject()
            analyzer = inject()
            scope.launch {
                // A call that was mid-flight when the process died would otherwise stay
                // "InProgress" forever; flag any such rows as Interrupted on every fresh start.
                giraffeLogDao.sanitizeStuckChats()
            }
        }
    }

    /** Wraps [next]'s call so every header/message/close event is mirrored into logging, storage, and notifications - or, in a non-debuggable host build, just forwards to [next] untouched (see [isEnabled]). */
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        if (!isEnabled) return next.newCall(method, callOptions)

        val chatId = UUID.randomUUID()
        val methodShortName = method.fullMethodName.substringAfterLast("/")
        val host = next.authority()
        val url = "$host/${method.fullMethodName}"

        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                scope.launch {
                    val chat = GiraffeChatEntity(
                        chatId = chatId.toString(),
                        url = url,
                        methodShortName = methodShortName,
                        timestamp = System.currentTimeMillis(),
                        status = GiraffeChatStatus.InProgress,
                    )

                    val reqHeaders = headers.keys().map { keyName ->
                        val key = Metadata.Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER)
                        GiraffeHeaderEntity(
                            chatId = chatId.toString(),
                            isResponse = false,
                            key = keyName,
                            value = headers.get(key) ?: ""
                        )
                    }

                    log(
                        "▶ START method=$methodShortName\n" +
                                "host=$host\n" +
                            "headers=${reqHeaders.joinToString { "${it.key}=${it.value}\n" }}"
                    )

                    giraffeLogDao.startChat(chat, reqHeaders)
                }

                super.start(
                    object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener
                    ) {
                        override fun onMessage(message: RespT) {
                            processMessage(
                                method = methodShortName,
                                host = host,
                                chatId = chatId,
                                isIncoming = true,
                                message = message as Any,
                            )
                            super.onMessage(message)
                        }

                        override fun onClose(status: Status, trailers: Metadata) {
                            scope.launch {
                                val respHeaders = trailers.keys().map { keyName ->
                                    val key =
                                        Metadata.Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER)
                                    GiraffeHeaderEntity(
                                        chatId = chatId.toString(),
                                        isResponse = true,
                                        key = keyName,
                                        value = trailers.get(key).orEmpty(),
                                    )
                                }

                                val chatStatus =
                                    if (status.isOk || status.code == Status.Code.CANCELLED) GiraffeChatStatus.Ok
                                    else GiraffeChatStatus.Error

                                log(
                                    "■ CLOSE method=$methodShortName\n" +
                                            "host=$host\n" +
                                        "status=${status.code}\n" +
                                            "description=${status.description}\n" +
                                        "cause=${status.cause}\n" +
                                        "trailers=${respHeaders.joinToString { "${it.key}=${it.value}\n" }}"
                                )

                                giraffeLogDao.completeChat(
                                    chatId = chatId.toString(),
                                    finalStatus = chatStatus,
                                    responseHeaders = respHeaders,
                                )
                            }

                            super.onClose(status, trailers)
                        }
                    },
                    headers
                )
            }

            override fun sendMessage(message: ReqT) {
                processMessage(
                    method = methodShortName,
                    host = host,
                    chatId = chatId,
                    isIncoming = false,
                    message = message as Any,
                )
                super.sendMessage(message)
            }
        }

    }

    /** Analyzes one request/response message, then fans the result out to logging, the DB, and a notification. */
    private fun processMessage(
        method: String,
        host: String,
        chatId: UUID,
        isIncoming: Boolean,
        message: Any,
    ) {
        scope.launch {
            val analysis = try {
                analyzer.analyze(message)
            } catch (_: Exception) {
                null
            }

            val direction = if (isIncoming) "◀ RESPONSE" else "▶ REQUEST"
            log(
                "$direction\n" +
                        "method=$method\n" +
                        "host=$host\n" +
                    "contentType=${analysis?.contentType}\n" +
                        "filePath=${analysis?.filePath}\n" +
                    "text=${analysis?.textContent}"
            )

            notificationService.sendTrafficNotification(
                methodName = method,
                host = host,
                message = analysis?.textContent ?: message.toString(),
                notificationId = chatId,
            )

            if (analysis != null) {
                val dbMessage = GiraffeMessageEntity(
                    chatId = chatId.toString(),
                    isIncoming = isIncoming,
                    contentType = analysis.contentType,
                    textContent = analysis.textContent,
                    filePath = analysis.filePath,
                    timestamp = System.currentTimeMillis(),
                )
                giraffeLogDao.insertMessage(dbMessage)
            }
        }
    }

    private fun log(message: String) {
        if (loggingEnabled) {
            Log.d(TAG, message)
        }
    }
}
