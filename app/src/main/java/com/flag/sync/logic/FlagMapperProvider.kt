 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.logic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object FlagMapperProvider {

    private const val MAPPING_FILENAME = "id_name_mapping.json"
    private const val MAPPING_ASSET_PATH = "mappings/$MAPPING_FILENAME"
    private const val MAPPING_URL = "https://raw.githubusercontent.com/ViictorNunes/FlagSync/master/app/src/main/assets/mappings/id_name_mapping.json"
    private const val PREFS_NAME = "flag_sync_mapping_prefs"
    private const val KEY_ETAG = "mapping_etag"

    @Volatile
    private var instance: FlagMapper? = null

    fun get(): FlagMapper {
        return instance ?: throw IllegalStateException(
            "FlagMapperProvider was not initialized. Call init(context) before use."
        )
    }

    suspend fun init(context: Context) {
        if (instance != null) return
        withContext(Dispatchers.IO) {
            synchronized(this) {
                if (instance == null) {
                    val localFile = File(context.filesDir, MAPPING_FILENAME)
                    val json = if (localFile.exists()) {
                        localFile.readText()
                    } else {
                        readAssetFile(context, MAPPING_ASSET_PATH)
                    }
                    instance = FlagMapper(json)
                }
            }
        }
    }

    /**
     * Checks if a new mapping is available by comparing the ETag from GitHub headers.
     */
    suspend fun checkForUpdate(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        val connection = URL(MAPPING_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD" // Fast check
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val serverEtag = connection.getHeaderField("ETag")
            val serverLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val localEtag = prefs.getString(KEY_ETAG, "")
            val localFile = File(context.filesDir, MAPPING_FILENAME)

            // 1. Reliable Check: Compare ETags if we have a local one
            if (!localEtag.isNullOrEmpty() && serverEtag != null) {
                return@withContext Result.success(serverEtag != localEtag)
            }

            // 2. Check for fresh install / asset usage
            if (!localFile.exists()) {
                val assetLength = context.assets.open(MAPPING_ASSET_PATH).use { it.available().toLong() }

                // If lengths match exactly, it's definitely the same file
                if (serverLength != -1L && serverLength == assetLength) {
                    return@withContext Result.success(false)
                }

                // If lengths are very close (within 100 bytes), ignore (likely line ending / tiny metadata diff)
                if (serverLength != -1L && Math.abs(serverLength - assetLength) < 100) {
                    return@withContext Result.success(false)
                }
            } else if (serverLength != -1L) {
                // local file exists but no ETag (legacy). Compare lengths strictly.
                if (serverLength == localFile.length()) {
                    return@withContext Result.success(false)
                }
            }

            // 3. Fallback: only trigger if we have strong evidence of a difference
            val isProbablyDifferent = if (serverEtag != null && !localEtag.isNullOrEmpty()) {
                serverEtag != localEtag
            } else if (serverLength != -1L && !localFile.exists()) {
                val assetLength = context.assets.open(MAPPING_ASSET_PATH).use { it.available().toLong() }
                serverLength != assetLength
            } else {
                false // Be conservative to avoid false positives
            }

            Result.success(isProbablyDifferent)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads the latest mapping from the server and updates the instance.
     */
    suspend fun updateFromNetwork(context: Context, onProgress: ((Float) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        val connection = URL(MAPPING_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                val etag = connection.getHeaderField("ETag") ?: connection.contentLength.toString()
                val fileLength = connection.contentLength
                val input = connection.inputStream

                // Ensure directory exists
                val updatesDir = File(context.filesDir, "")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val output = File(context.filesDir, MAPPING_FILENAME).outputStream()
                val data = ByteArray(8192)
                var total = 0L
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgress?.invoke(total.toFloat() / fileLength.toFloat())
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                val json = File(context.filesDir, MAPPING_FILENAME).readText()
                val newMapper = FlagMapper(json)

                if (newMapper.size() > 0) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_ETAG, etag).apply()

                    instance = newMapper
                    Result.success(newMapper.size())
                } else {
                    Result.failure(Exception("Mapping file is invalid"))
                }
            } else {
                Result.failure(Exception("Server error ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun readAssetFile(context: Context, path: String): String {
        return context.assets.open(path).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
    }
}