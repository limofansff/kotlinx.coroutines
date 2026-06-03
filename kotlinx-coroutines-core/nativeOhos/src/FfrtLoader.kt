@file:OptIn(ExperimentalForeignApi::class, InternalCoroutinesApi::class)
package kotlinx.coroutines

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.internal.SynchronizedObject
import platform.posix.RTLD_NOW
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym
import kotlin.concurrent.Volatile

private typealias FfrtSetCpuWorkerMaxNumFunc = CPointer<CFunction<(Int, UInt) -> Int>>
private typealias FfrtThisTaskSetLegacyModeFunc = CPointer<CFunction<(Boolean) -> Unit>>

internal object FfrtLoader {
    private const val LIBRARY_NAME = "libffrt.so"
    private val lock = SynchronizedObject()

    @Volatile private var inited = false
    @Volatile private var available = false

    private var handle: COpaquePointer? = null
    private var setCpuWorkerMaxNumPtr: FfrtSetCpuWorkerMaxNumFunc? = null
    private var setThisTaskLegacyModePtr: FfrtThisTaskSetLegacyModeFunc? = null

    fun tryInit(): Boolean {
        if (inited) {
            return available
        }

        return synchronized(lock) {
            if (inited) return@synchronized available
            val libraryHandle = dlopen(LIBRARY_NAME, RTLD_NOW)
            if (libraryHandle == null) {
                available = false
                inited = true
                return@synchronized available
            }
            val symbolSetWorkerNum = dlsym(libraryHandle, "ffrt_set_cpu_worker_max_num")
            val symbolSetLegacyMode = dlsym(libraryHandle, "ffrt_this_task_set_legacy_mode")
            if (symbolSetWorkerNum == null || symbolSetLegacyMode == null) {
                dlclose(libraryHandle)
                available = false
                inited = true
                return@synchronized available
            }
            handle = libraryHandle
            setCpuWorkerMaxNumPtr = symbolSetWorkerNum.reinterpret()
            setThisTaskLegacyModePtr = symbolSetLegacyMode.reinterpret()
            available = true
            inited = true
            return@synchronized available
        }
    }

    fun isAvailable(): Boolean = inited && available

    fun setCpuWorkerMaxNum(qos: Int, num: UInt): Int {
        val fn = setCpuWorkerMaxNumPtr ?: error("ffrt not loaded")
        return fn(qos, num)
    }

    fun setThisTaskLegacyMode(mode: Boolean) {
        val fn = setThisTaskLegacyModePtr ?: error("ffrt not loaded")
        fn(mode)
    }

    fun close() = synchronized(lock) {
        handle?.let { dlclose(it) }
        handle = null
        setCpuWorkerMaxNumPtr = null
        setThisTaskLegacyModePtr = null
        inited = false
        available = false
    }
}
