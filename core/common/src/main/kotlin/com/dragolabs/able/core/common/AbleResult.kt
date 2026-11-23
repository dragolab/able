package com.dragolabs.able.core.common

sealed interface AbleResult<out T> {
    data class Success<T>(val data: T) : AbleResult<T>
    data class Failure(val error: AbleError) : AbleResult<Nothing>
    data object Loading : AbleResult<Nothing>
}

sealed interface AbleError {
    val message: String
    val cause: Throwable?

    data class Security(override val message: String, override val cause: Throwable? = null) : AbleError
    data class PermissionMissing(val permission: String, override val message: String = "Permission missing: $permission") : AbleError {
        override val cause: Throwable? = null
    }
    data class ShizukuUnavailable(override val message: String = "Shizuku service is not connected") : AbleError {
        override val cause: Throwable? = null
    }
    data class HardwareUnavailable(val featureName: String, override val message: String = "Hardware feature not supported: $featureName") : AbleError {
        override val cause: Throwable? = null
    }
    data class Storage(override val message: String, override val cause: Throwable? = null) : AbleError
    data class Validation(override val message: String, override val cause: Throwable? = null) : AbleError
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AbleError
}

inline fun <T, R> AbleResult<T>.map(transform: (T) -> R): AbleResult<R> = when (this) {
    is AbleResult.Success -> AbleResult.Success(transform(data))
    is AbleResult.Failure -> this
    is AbleResult.Loading -> AbleResult.Loading
}

fun <T> AbleResult<T>.getOrNull(): T? = when (this) {
    is AbleResult.Success -> data
    else -> null
}
