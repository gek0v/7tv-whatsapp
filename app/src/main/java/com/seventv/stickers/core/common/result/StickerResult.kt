package com.seventv.stickers.core.common.result

/**
 * Functional result type representing either a successful result with data [T]
 * or a failure containing a [StickerError].
 */
sealed interface StickerResult<out T> {
    data class Success<out T>(val data: T) : StickerResult<T>
    data class Failure(val error: StickerError) : StickerResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): StickerError? = when (this) {
        is Success -> null
        is Failure -> error
    }
}
