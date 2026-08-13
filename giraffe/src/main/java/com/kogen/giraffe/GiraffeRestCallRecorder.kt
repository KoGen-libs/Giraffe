package com.kogen.giraffe

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.kogen.giraffe.analizer.GiraffeRestBodyAnalyzer
import com.kogen.giraffe.db.dao.GiraffeRestLogDao
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.GiraffeRestHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeRestMessageEntity
import com.kogen.giraffe.di.inject
import com.kogen.giraffe.di.setApplicationContext
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

private val TAG = GiraffeRestCallRecorder::class.java.simpleName

/**
 * Protocol-agnostic core shared by [GiraffeOkHttpInterceptor] and the Ktor client plugin
 * (`GiraffeKtorPlugin`, in the `:giraffe-ktor`... actually same module, see that file) - each
 * front end is responsible only for capturing the bytes/headers of its own HTTP client library's
 * request/response shape and handing them here; everything from analysis to persistence to the
 * traffic notification is identical regardless of which client made the call.
 *
 * Mirrors [GiraffeInterceptor]'s `isEnabled`/debuggable-check and error-swallowing persistence,
 * but has an easier time of it: a REST call's request and response are both already known by the
 * time [record] is called (unlike gRPC's streamed messages arriving over the lifetime of a call),
 * so there's no equivalent of that class's chat-row race to guard against.
 */
internal class GiraffeRestCallRecorder(
    context: Context,
    private val loggingEnabled: Boolean = true,
) {
    /** See [GiraffeInterceptor.isEnabled] - same check, same reasoning, duplicated rather than shared because the two classes otherwise have nothing in common to justify a base class for it. */
    val isEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // SupervisorJob so one call's logging failure can't cancel every other in-flight (or future)
    // launch on this scope - without it, an uncaught exception poisons the shared Job and every
    // subsequent scope.launch would be dead-on-arrival for the rest of the process's lifetime.
    // CoroutineExceptionHandler is the last-resort net: this whole class is a passive debug
    // observer, so nothing it does should ever be able to crash the host app it's attached to -
    // better to silently drop one piece of diagnostic data than take the app down over it.
    // Mirrors GiraffeInterceptor's scope for the same reasoning.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Unexpected error in Giraffe's REST traffic logging - ignoring it", throwable)
        }
    )
    private val appContext = context.applicationContext
    private lateinit var restLogDao: GiraffeRestLogDao
    private lateinit var notificationService: GiraffeNotificationService
    private val analyzer = GiraffeRestBodyAnalyzer()

    init {
        if (isEnabled) {
            setApplicationContext(context)
            restLogDao = inject()
            notificationService = inject()
            scope.launch {
                try {
                    restLogDao.sanitizeStuckRestCalls()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sanitize stuck REST calls", e)
                }
            }
        }
    }

    /**
     * Records one finished (or failed) REST call. [requestBody]/[responseBody] are `null` when
     * that side genuinely has no body (a GET request, a call that never got a response at all) -
     * not to be confused with an empty `ByteArray`, which still gets analyzed and recorded as an
     * empty message.
     */
    fun record(
        callId: String,
        url: String,
        httpMethod: String,
        startTimestamp: Long,
        requestHeaders: List<Pair<String, String>>,
        requestContentType: String?,
        requestBody: ByteArray?,
        responseHeaders: List<Pair<String, String>>,
        responseContentType: String?,
        responseBody: ByteArray?,
        httpStatusCode: Int?,
        error: Throwable?,
    ) {
        if (!isEnabled) return

        scope.launch {
            // Parses arbitrary request/response bytes (images, video, pdf, unknown binaries) -
            // exactly the kind of code most likely to choke on some edge case, so - unlike this
            // file's other steps - it wasn't wrapped at all. Mirrors GiraffeInterceptor's
            // equivalent try/catch around analyzer.analyze().
            val requestAnalysis = requestBody?.let {
                try {
                    analyzer.analyze(requestContentType, it, appContext)
                } catch (_: Exception) {
                    null
                }
            }
            val responseAnalysis = responseBody?.let {
                try {
                    analyzer.analyze(responseContentType, it, appContext)
                } catch (_: Exception) {
                    null
                }
            }

            val status = when {
                error != null -> GiraffeChatStatus.Error
                httpStatusCode != null && httpStatusCode < 400 -> GiraffeChatStatus.Ok
                else -> GiraffeChatStatus.Error
            }

            log(
                "■ $httpMethod $url\n" +
                    "status=${httpStatusCode ?: "-"}${error?.let { " error=${it.message}" }.orEmpty()}\n" +
                    "request=${requestAnalysis?.textContent}\n" +
                    "response=${responseAnalysis?.textContent}"
            )

            try {
                notificationService.sendTrafficNotification(
                    methodName = httpMethod,
                    host = url,
                    message = responseAnalysis?.textContent
                        ?: requestAnalysis?.textContent
                        ?: error?.message
                        ?: httpStatusCode?.toString()
                        ?: "",
                    notificationId = UUID.fromString(callId),
                    isRestCall = true,
                )
            } catch (e: Exception) {
                // A failed notification shouldn't also take the DB write below down with it -
                // each side effect here is independently best-effort.
                Log.w(TAG, "Failed to post traffic notification for callId=$callId", e)
            }

            val call = GiraffeRestCallEntity(
                callId = callId,
                url = url,
                httpMethod = httpMethod,
                timestamp = startTimestamp,
                status = status,
                httpStatusCode = httpStatusCode,
            )
            val headers = requestHeaders.map { (key, value) ->
                GiraffeRestHeaderEntity(callId = callId, isResponse = false, key = key, value = value)
            } + responseHeaders.map { (key, value) ->
                GiraffeRestHeaderEntity(callId = callId, isResponse = true, key = key, value = value)
            }
            val messages = listOfNotNull(
                requestAnalysis?.let {
                    GiraffeRestMessageEntity(
                        callId = callId,
                        isIncoming = false,
                        contentType = it.contentType,
                        textContent = it.textContent,
                        filePath = it.filePath,
                        timestamp = startTimestamp,
                    )
                },
                responseAnalysis?.let {
                    GiraffeRestMessageEntity(
                        callId = callId,
                        isIncoming = true,
                        contentType = it.contentType,
                        textContent = it.textContent,
                        filePath = it.filePath,
                        timestamp = System.currentTimeMillis(),
                    )
                },
            )

            // Same principle as GiraffeInterceptor's persist{} helper: this is Giraffe's own
            // bookkeeping running alongside someone else's HTTP call, and a failure in it must
            // never propagate - there's nothing upstream to catch it anyway, this runs detached
            // in its own coroutine.
            try {
                restLogDao.recordRestCall(call, headers, messages)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist REST call callId=$callId", e)
            }
        }
    }

    private fun log(message: String) {
        if (loggingEnabled) {
            Log.d(TAG, message)
        }
    }
}
