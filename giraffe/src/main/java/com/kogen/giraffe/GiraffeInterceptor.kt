package com.kogen.giraffe

import android.content.Context
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
import kotlinx.coroutines.Job
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
 * retained internally, so a short-lived `Activity` context is safe to pass here.
 * @param loggingEnabled when `false`, suppresses this interceptor's own `Log.d` output while
 * still recording traffic to the database and notifications.
 */
class GiraffeInterceptor(
    context: Context,
    private val loggingEnabled: Boolean = true,
) : ClientInterceptor {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var giraffeLogDao: GiraffeLogDao
    private var notificationService: GiraffeNotificationService
    private var analyzer: GiraffeMessageAnalyzer

    init {
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

    /** Wraps [next]'s call so every header/message/close event is mirrored into logging, storage, and notifications. */
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        val chatId = UUID.randomUUID()
        val methodShortName = method.fullMethodName.substringAfterLast("/")
        val host = next.authority()
        val url = "$host/${method.fullMethodName}"

        // Set in start() below, then awaited by every later write for this same call (message
        // inserts, close) so they land after the chat row start() creates - see
        // insertMessageEnsuringChat's doc for why that's a best-effort ordering, not a
        // correctness requirement: those writes ensure the chat row themselves if it's still
        // missing (or was deleted) by the time they run.
        var chatReadyJob: Job? = null

        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                chatReadyJob = scope.launch {
                    val chat = chatStub(chatId, url, methodShortName)

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

                    persist("start chatId=$chatId") { giraffeLogDao.startChat(chat, reqHeaders) }
                }

                super.start(
                    object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener
                    ) {
                        override fun onMessage(message: RespT) {
                            processMessage(
                                method = methodShortName,
                                host = host,
                                url = url,
                                chatId = chatId,
                                chatReadyJob = chatReadyJob,
                                isIncoming = true,
                                message = message as Any,
                            )
                            super.onMessage(message)
                        }

                        override fun onClose(status: Status, trailers: Metadata) {
                            scope.launch {
                                chatReadyJob?.join()

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

                                persist("close chatId=$chatId") {
                                    giraffeLogDao.completeChat(
                                        chatStub = chatStub(chatId, url, methodShortName),
                                        finalStatus = chatStatus,
                                        responseHeaders = respHeaders,
                                    )
                                }
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
                    url = url,
                    chatId = chatId,
                    chatReadyJob = chatReadyJob,
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
        url: String,
        chatId: UUID,
        chatReadyJob: Job?,
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

                // Prefer landing after the chat row start() itself creates, but don't depend on
                // it: insertMessageEnsuringChat (re-)creates that row from a stub if start()'s
                // own write hasn't landed yet, or if the chat was deleted (e.g. the user cleared
                // history) while this call was still in flight.
                chatReadyJob?.join()
                persist("message chatId=$chatId") {
                    giraffeLogDao.insertMessageEnsuringChat(chatStub(chatId, url, method), dbMessage)
                }
            }
        }
    }

    /** A stand-in chat row good enough to satisfy the [GiraffeMessageEntity]/[GiraffeHeaderEntity] foreign key when the real one (from [start]) hasn't landed yet or was deleted mid-call. */
    private fun chatStub(chatId: UUID, url: String, methodShortName: String) = GiraffeChatEntity(
        chatId = chatId.toString(),
        url = url,
        methodShortName = methodShortName,
        timestamp = System.currentTimeMillis(),
        status = GiraffeChatStatus.InProgress,
    )

    /**
     * Runs [write], swallowing any failure: this is Giraffe's own bookkeeping running alongside
     * someone else's gRPC call, and a hiccup in it (a constraint violation despite the safeguards
     * above, disk I/O, whatever) must never crash the host app that call belongs to.
     */
    private suspend fun persist(what: String, write: suspend () -> Unit) {
        try {
            write()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist $what", e)
        }
    }

    private fun log(message: String) {
        if (loggingEnabled) {
            Log.d(TAG, message)
        }
    }
}
