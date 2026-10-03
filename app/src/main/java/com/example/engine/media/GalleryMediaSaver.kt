package com.example.engine.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object GalleryMediaSaver {
  private const val TAG = "GalleryMediaSaver"

  data class SaveResult(
    val uri: Uri?,
    val file: File,
    val isSavedToPublicGallery: Boolean
  )

  /**
   * Auto-saves exported video files into the device's public Gallery (Movies/VideoStudio).
   * Registers the file in MediaStore and triggers MediaScanner so it is immediately visible
   * in Google Photos, Samsung Gallery, and device media pickers.
   */
  fun saveVideoToGallery(context: Context, sourceFile: File, title: String): SaveResult {
    if (!sourceFile.exists() || sourceFile.length() == 0L) {
      Log.e(TAG, "Source video file does not exist or is empty")
      return SaveResult(null, sourceFile, false)
    }

    val sanitizedTitle = title.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_").ifBlank { "ExportedVideo" }
    val timeStamp = System.currentTimeMillis()
    val fileName = "${sanitizedTitle}_$timeStamp.mp4"

    // 1. MediaStore API for Android 10+ (API 29+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val contentValues = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
          put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoStudio")
          put(MediaStore.Video.Media.IS_PENDING, 1)
          put(MediaStore.Video.Media.DATE_ADDED, timeStamp / 1000)
          put(MediaStore.Video.Media.DATE_TAKEN, timeStamp)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
          resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(sourceFile).use { inputStream ->
              inputStream.copyTo(outputStream)
            }
          }
          contentValues.clear()
          contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
          resolver.update(uri, contentValues, null, null)

          Log.d(TAG, "Successfully auto-saved video to MediaStore: $uri")

          // Also copy to public Movies directory for direct File path references if needed
          val publicFile = saveToPublicMoviesFolder(context, sourceFile, fileName)
          return SaveResult(uri, publicFile ?: sourceFile, true)
        }
      } catch (e: Exception) {
        Log.e(TAG, "MediaStore Q+ insert failed, falling back to legacy public copy", e)
      }
    }

    // 2. Legacy / Public Folder Copy for older Android versions or fallback
    val publicFile = saveToPublicMoviesFolder(context, sourceFile, fileName)
    if (publicFile != null && publicFile.exists()) {
      var scannedUri: Uri? = Uri.fromFile(publicFile)
      try {
        MediaScannerConnection.scanFile(
          context.applicationContext,
          arrayOf(publicFile.absolutePath),
          arrayOf("video/mp4")
        ) { _, uri ->
          if (uri != null) {
            scannedUri = uri
          }
          Log.d(TAG, "MediaScanner finished scanning $publicFile -> $uri")
        }
      } catch (e: Exception) {
        Log.w(TAG, "MediaScannerConnection error", e)
      }
      return SaveResult(scannedUri, publicFile, true)
    }

    return SaveResult(Uri.fromFile(sourceFile), sourceFile, false)
  }

  private fun saveToPublicMoviesFolder(context: Context, sourceFile: File, fileName: String): File? {
    return try {
      val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoStudio")
      if (!moviesDir.exists()) {
        moviesDir.mkdirs()
      }
      val destFile = File(moviesDir, fileName)
      FileInputStream(sourceFile).use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      }
      destFile
    } catch (e: Exception) {
      Log.e(TAG, "Public Movies folder save failed", e)
      null
    }
  }
}
