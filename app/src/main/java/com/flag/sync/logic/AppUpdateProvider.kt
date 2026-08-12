 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.logic

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateProvider {
    // Oficial GitHub API endpoint for the latest release
    private const val UPDATE_URL = "https://api.github.com/repos/ViictorNunes/FlagSync/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val downloadUrl: String,
        val changelog: String,
    )

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                
                val serverVersionName = obj.getString("tag_name").replace("v", "").trim()
                val currentVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.trim()

                // Simple semantic version check (could be improved with a proper comparator)
                if (serverVersionName != currentVersionName) {
                    val assets = obj.getJSONArray("assets")
                    var apkUrl = ""
                    
                    // Find the first APK in the release assets
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (apkUrl.isNotEmpty()) {
                        Result.success(
                            UpdateInfo(
                                versionName = serverVersionName,
                                downloadUrl = apkUrl,
                                changelog = obj.optString("body", "")
                            )
                        )
                    } else {
                        Result.success(null)
                    }
                } else {
                    Result.success(null)
                }
            } else {
                Result.failure(Exception("Server error ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndInstall(context: Context, url: String, onProgress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()
            
            val destinationFile = File(updatesDir, "update.apk")
            if (destinationFile.exists()) destinationFile.delete()

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Server error ${connection.responseCode}"))
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = destinationFile.outputStream()

            val data = ByteArray(8192)
            var total = 0L
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            installApk(context, destinationFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
