package com.dragolabs.able.core.data

import android.os.Environment
import android.os.StatFs
import com.dragolabs.able.core.model.StorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Free/used internal storage (STO-001 summary). Returns null when Android does not report it. */
class StorageSummaryRepository {
    suspend fun read(): StorageSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            StorageSummary(totalBytes = stat.totalBytes, availableBytes = stat.availableBytes)
        }.getOrNull()?.takeIf { it.totalBytes > 0 }
    }
}
