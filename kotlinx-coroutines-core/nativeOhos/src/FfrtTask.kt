package kotlinx.coroutines

import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
private fun extractClosurePtr(arg: COpaquePointer?): COpaquePointer {
    requireNotNull(arg)
    val headerSize = sizeOf<ffrt_function_header_t>()
    val closureOffset = arg.rawValue + headerSize
    return closureOffset.toLong().toCPointer<COpaquePointerVar>()!!.pointed.value!!
}

@OptIn(ExperimentalForeignApi::class)
private fun createFunctionWrapper(
    func: () -> Unit,
    kind: ffrt_function_kind_t
): CPointer<ffrt_function_header_t> {
    val stableRef = StableRef.create(func)
    val functionStorage = ffrt_alloc_auto_managed_function_storage_base(kind)
        ?: throw Error("Failed to allocate function storage")
    val header = functionStorage.reinterpret<ffrt_function_header_t>()
    header.pointed.exec = staticCFunction { arg ->
        extractClosurePtr(arg).asStableRef<() -> Unit>().get().invoke()
    }
    header.pointed.destroy = staticCFunction { arg ->
        extractClosurePtr(arg).asStableRef<() -> Unit>().dispose()
    }
    header.pointed.reserve[0] = 0uL
    header.pointed.reserve[1] = 0uL

    val headerSize = sizeOf<ffrt_function_header_t>()
    val closureStorageOffset = functionStorage.rawValue + headerSize
    closureStorageOffset.toLong().toCPointer<ULongVar>()?.pointed?.value =
        stableRef.asCPointer().rawValue.toLong().toULong()

    return header
}

@OptIn(ExperimentalForeignApi::class)
internal object Task {
    internal fun submit(
        func: () -> Unit,
        qos: Int
    ) {
        val funcWrapper = createFunctionWrapper(func, ffrt_function_kind_t.ffrt_function_kind_general)
        memScoped {
            val taskAttr = alloc<ffrt_task_attr_t>()
            ffrt_task_attr_init(taskAttr.ptr)
            ffrt_task_attr_set_qos(taskAttr.ptr, qos)
            ffrt_submit_base(funcWrapper, null, null, taskAttr.ptr)
        }
    }
}