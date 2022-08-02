/*
 * Simple lifecycle-aware (State)Flow utilities for Android apps
 *
 * Licensed under the MIT License (MIT)
 *
 * Copyright (c) 2022 Danny Lin <oss@kdrag0n.dev>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.kdrag0n.app.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import dev.kdrag0n.app.log.logD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** All flow collectors in a Fragment should be in this block */
fun Fragment.launchStarted(block: suspend CoroutineScope.() -> Unit) =
    viewLifecycleOwner.launchStarted(block)

/** All flow collectors in an Activity should be in this block */
fun LifecycleOwner.launchStarted(block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}

/** For StateFlows: only collect update/change events, not initial value */
suspend fun <T> Flow<T>.collectUpdates(collector: FlowCollector<T>) =
    drop(1).collect(collector)

/**
 * For use in launchStarted blocks: non-blocking collect
 * Always use this to avoid blocking mistakes, as all collectors share the launchStarted block
 */
fun <T> Flow<T>.launchCollect(scope: CoroutineScope, collector: FlowCollector<T>) =
    scope.launch {
        collect(collector)
    }

/**
 * For use in launchStarted blocks: non-blocking collect -- update events only
 * Always use this to avoid blocking mistakes, as all collectors share the launchStarted block
 */
fun <T> Flow<T>.launchCollectUpdates(scope: CoroutineScope, collector: FlowCollector<T>) =
    scope.launch {
        collectUpdates(collector)
    }

/** Simple event flow with no associated data */
@OptIn(FlowPreview::class)
class EventFlow : AbstractFlow<Unit>() {
    private val flow = MutableSharedFlow<Unit>()

    override suspend fun collectSafely(collector: FlowCollector<Unit>) =
        flow.collect(collector)

    suspend fun emit() {
        flow.emit(Unit)
    }

    fun emit(viewModel: ViewModel) {
        viewModel.viewModelScope.launch {
            flow.emit(Unit)
        }
    }

    fun emit(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            flow.emit(Unit)
        }
    }

    fun tryEmit() {
        flow.tryEmit(Unit)
    }
}

/** Drop-in replacement for MutableStateFlow that logs changes */
class DebugMutableStateFlow<T>(
    private val orig: MutableStateFlow<T>
) : MutableStateFlow<T> by orig {
    override var value: T
        get() = orig.value
        set(value) {
            logD(Throwable()) { "setValue: flow=$orig | value=$value" }
            orig.value = value
        }
}

/**
 * Like Flow.combine, but returns a new StateFlow
 * Intended for use in ViewModels or lower layers
 */
fun <T1, T2, R> StateFlow<T1>.combineState(
    other: StateFlow<T2>,
    scope: CoroutineScope,
    sharingStarted: SharingStarted = SharingStarted.Lazily,
    transform: (T1, T2) -> R,
) = combine(other) { a, b -> transform(a, b) }
    .stateIn(scope, sharingStarted, transform(value, other.value))

/**
 * Like Flow.map, but returns a new StateFlow
 * Intended for use in ViewModels or lower layers
 */
fun <T, R> StateFlow<T>.mapState(
    scope: CoroutineScope,
    sharingStarted: SharingStarted = SharingStarted.Eagerly,
    transform: (T) -> R,
) = map { transform(it) }
    .stateIn(scope, sharingStarted, transform(value))

/** Like Mutex.withLock, but doesn't run block if the lock is already held */
inline fun <T> Mutex.tryWithLock(block: () -> T): T? {
    if (tryLock()) {
        try {
            return block()
        } finally {
            unlock()
        }
    }

    return null
}
