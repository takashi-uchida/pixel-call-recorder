package com.callrecorder.pixel.common

import com.callrecorder.pixel.error.CallRecorderError

/**
 * A generic wrapper for operation results that can either succeed or fail.
 * Provides consistent error handling across the application.
 */
sealed class Result<out T> {
    
    /**
     * Represents a successful operation with data
     */
    data class Success<T>(val data: T) : Result<T>()
    
    /**
     * Represents a failed operation with error information
     */
    data class Error(val error: CallRecorderError) : Result<Nothing>()
    
    /**
     * Represents a loading state (useful for UI)
     */
    object Loading : Result<Nothing>()

    /**
     * Returns true if the result is successful
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Returns true if the result is an error
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Returns true if the result is loading
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * Returns the data if successful, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * Returns the error if failed, null otherwise
     */
    fun errorOrNull(): CallRecorderError? = when (this) {
        is Error -> error
        else -> null
    }

    /**
     * Returns the data if successful, or the default value if failed
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }

    /**
     * Executes the given action if the result is successful
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    /**
     * Executes the given action if the result is an error
     */
    inline fun onError(action: (CallRecorderError) -> Unit): Result<T> {
        if (this is Error) {
            action(error)
        }
        return this
    }

    /**
     * Executes the given action if the result is loading
     */
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) {
            action()
        }
        return this
    }

    /**
     * Maps the success value to a new type
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    /**
     * Flat maps the success value to a new Result
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> this
    }

    companion object {
        /**
         * Creates a successful result
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * Creates an error result
         */
        fun <T> error(error: CallRecorderError): Result<T> = Error(error)

        /**
         * Creates a loading result
         */
        fun <T> loading(): Result<T> = Loading

        /**
         * Wraps a potentially throwing operation in a Result
         */
        inline fun <T> runCatching(action: () -> T): Result<T> = try {
            success(action())
        } catch (e: CallRecorderError) {
            error(e)
        } catch (e: Exception) {
            error(com.callrecorder.pixel.error.SystemError.UnknownError)
        }

        /**
         * Wraps a suspend function that might throw in a Result
         */
        suspend inline fun <T> runCatchingSuspend(crossinline action: suspend () -> T): Result<T> = try {
            success(action())
        } catch (e: CallRecorderError) {
            error(e)
        } catch (e: Exception) {
            error(com.callrecorder.pixel.error.SystemError.UnknownError)
        }
    }
}

/**
 * Extension function to convert nullable values to Result
 */
fun <T> T?.toResult(error: CallRecorderError): Result<T> = 
    this?.let { Result.success(it) } ?: Result.error(error)

/**
 * Extension function to convert boolean operations to Result
 */
fun Boolean.toResult(error: CallRecorderError): Result<Unit> = 
    if (this) Result.success(Unit) else Result.error(error)
