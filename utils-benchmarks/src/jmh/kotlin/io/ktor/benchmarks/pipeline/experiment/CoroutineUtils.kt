package io.ktor.benchmarks.pipeline.experiment

import kotlin.coroutines.Continuation

inline fun <R, A> (suspend R.(A) -> Unit).startCoroutineUninterceptedOrReturn3(
    receiver: R,
    arg: A,
    continuation: Continuation<Unit>
): Any? {
    val function = (this as Function3<R, A, Continuation<Unit>, Any?>)
    return function.invoke(receiver, arg, continuation)
}
