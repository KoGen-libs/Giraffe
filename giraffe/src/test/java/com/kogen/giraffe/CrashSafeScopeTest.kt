package com.kogen.giraffe

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * [GiraffeInterceptor]'s background `scope` is built as
 * `SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler {...}` specifically so that
 * (a) an uncaught exception in one launch never crashes the host app, and (b) it doesn't also
 * poison the scope for every later launch. `scope` itself is a private field that needs the whole
 * class's Context/DI setup to construct, so this proves the exact pattern in isolation instead.
 */
class CrashSafeScopeTest {

    @Test
    fun `an uncaught exception in one launch is swallowed by the handler, not propagated`() = runBlocking {
        var caught: Throwable? = null
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                caught = throwable
            }
        )

        val job = scope.launch { error("boom") }
        job.join()

        assertThat(caught).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a failed launch does not stop later launches on the same scope`() = runBlocking {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }
        )

        scope.launch { error("boom") }.join()

        val second = scope.launch { /* no-op - just needs to actually run to completion */ }
        second.join()

        assertThat(second.isCompleted).isTrue()
        assertThat(second.isCancelled).isFalse()
    }

    @Test
    fun `without a SupervisorJob, a failed launch DOES poison the scope for later launches`() = runBlocking {
        // Negative control: proves the SupervisorJob is actually load-bearing above, not just
        // decorative - a plain Job() (CoroutineScope's default) cancels every sibling/future
        // launch once one child fails uncaught.
        val scope = CoroutineScope(
            Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }
        )

        scope.launch { error("boom") }.join()

        val second = scope.launch { /* no-op */ }
        second.join()

        assertThat(second.isCancelled).isTrue()
    }
}
