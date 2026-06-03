package kotlinx.coroutines

import kotlin.coroutines.CoroutineContext

public enum class Qos (
    public val qos: Int
) : CoroutineContext.Element {
    FFRT_QOS_BACKGROUND(0),
    FFRT_QOS_UTILITY(1),
    FFRT_QOS_DEFAULT(2),
    // 默认值
    FFRT_QOS_USER_INITIATED(3),
    FFRT_QOS_DEADLINE_REQUEST(4),
    FFRT_QOS_USER_INTERACTIVE(5);

    public companion object Key : CoroutineContext.Key<Qos>

    override public val key: CoroutineContext.Key<*>
        get() = Key
}