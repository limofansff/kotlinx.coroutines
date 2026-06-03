package kotlinx.coroutines

import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.LOG_APP
import platform.ohos.LOG_ERROR
import platform.ohos.OH_LOG_Print
import kotlin.coroutines.*
import kotlin.native.*

internal actual fun createDefaultDispatcher(): CoroutineDispatcher {
    return if (FfrtLoader.tryInit()) {
        FfrtDefaultDispatcher
    } else {
        DefaultDispatcher
    }
}

@OptIn(ExperimentalForeignApi::class)
private object FfrtDefaultDispatcher : CoroutineDispatcher() {
    private val DEFAULT_QOS = Qos.FFRT_QOS_USER_INITIATED.qos

    init {
        for (qos in 0..5) {
            val ret = FfrtLoader.setCpuWorkerMaxNum(qos, 32u)
            if (ret == -1) {
                OH_LOG_Print(LOG_APP, LOG_ERROR, 0u, "OhosMainDispatchers",
                    "ffrt_set_cpu_worker_max_num fail, qos: %d", qos)
            }
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val qos = context[Qos.Key]?.qos ?: DEFAULT_QOS
        Task.submit({
            FfrtLoader.setThisTaskLegacyMode(true)
            block.run()
            FfrtLoader.setThisTaskLegacyMode(false)
        }, qos)
    }
}

private object DefaultDispatcher : CoroutineDispatcher() {
    private val ctx = newFixedThreadPoolContext(
        Platform.getAvailableProcessors().coerceAtLeast(2),
        "Dispatchers.Default"
    )

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        ctx.dispatch(context, block)
    }
}