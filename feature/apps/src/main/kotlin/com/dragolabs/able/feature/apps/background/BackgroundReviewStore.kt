package com.dragolabs.able.feature.apps.background

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reviewed background settings, kept as JSON in Able's private app storage (never synced, never
 * leaves the device). Keyed by Android user and package.
 */
class BackgroundReviewStore(context: Context) {

    @Serializable
    private data class FileContent(val version: Int = 1, val reviews: Map<String, ReviewedSettings> = emptyMap())

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, ReviewedSettings>?>(null)

    /** All reviews keyed by [key]; null until [load] finished. */
    val reviews: StateFlow<Map<String, ReviewedSettings>?> = state.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (state.value != null) return@withLock
        state.value = withContext(Dispatchers.IO) {
            runCatching { decode(file.readFully().decodeToString()) }.getOrDefault(emptyMap())
        }
    }

    suspend fun put(userId: Int, packageName: String, review: ReviewedSettings): Boolean =
        update { it + (key(userId, packageName) to review) }

    suspend fun remove(userId: Int, packageName: String): Boolean = update { it - key(userId, packageName) }

    private suspend fun update(transform: (Map<String, ReviewedSettings>) -> Map<String, ReviewedSettings>): Boolean {
        load()
        return mutex.withLock {
            val next = transform(state.value.orEmpty())
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val out = file.startWrite()
                    try {
                        out.write(encode(next).encodeToByteArray())
                        file.finishWrite(out)
                    } catch (error: Throwable) {
                        file.failWrite(out)
                        throw error
                    }
                }.isSuccess
            }
            if (written) state.value = next
            written
        }
    }

    companion object {
        private const val FILE_NAME = "background_reviews.json"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun key(userId: Int, packageName: String): String = "$userId:$packageName"

        internal fun encode(reviews: Map<String, ReviewedSettings>): String = json.encodeToString(FileContent.serializer(), FileContent(reviews = reviews))

        internal fun decode(text: String): Map<String, ReviewedSettings> =
            if (text.isBlank()) emptyMap() else json.decodeFromString(FileContent.serializer(), text).reviews
    }
}
