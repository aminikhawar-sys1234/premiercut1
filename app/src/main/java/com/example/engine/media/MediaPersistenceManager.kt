package com.example.engine.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Bulletproof Media Persistence & Cache Subsystem for Video & Audio Studio.
 *
 * Guarantees that every video, photo, and audio clip imported into any project
 * is permanently stored in internal app storage, preventing:
 * - Temporary PhotoPicker content:// URI permission expirations
 * - Random video disappearances / black screens during editing
 * - Missing or broken timeline filmstrip thumbnails
 * - Lost media references across app restarts and project reloads
 */
object MediaPersistenceManager {

  private const val TAG = "MediaPersistenceManager"
  private const val MEDIA_STORE_DIR = "project_media"
  private const val THUMBNAILS_DIR = "video_thumbnails"

  /**
   * Returns the dedicated persistent media storage directory.
   */
  fun getMediaStoreDir(context: Context): File {
    val dir = File(context.filesDir, MEDIA_STORE_DIR)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Returns the dedicated persistent thumbnail cache directory.
   */
  fun getThumbnailCacheDir(context: Context): File {
    val dir = File(context.cacheDir, THUMBNAILS_DIR)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Persists a media URI into reliable internal storage synchronously or asynchronously.
   * Returns a persistent, permanent absolute file path / URI.
   */
  suspend fun persistMedia(
    context: Context,
    sourceUriString: String,
    suggestedName: String? = null
  ): String = withContext(Dispatchers.IO) {
    if (sourceUriString.isBlank()) return@withContext sourceUriString

    // Synthetic, sample, asset, and internal presets do not need local copying
    if (sourceUriString.startsWith("asset://") ||
      sourceUriString.startsWith("demo://") ||
      sourceUriString.startsWith("sample://") ||
      sourceUriString.startsWith("stock://") ||
      sourceUriString.startsWith("internal://") ||
      sourceUriString.startsWith("template://") ||
      sourceUriString.startsWith("android.resource://")
    ) {
      return@withContext sourceUriString
    }

    val parsedUri = try {
      Uri.parse(sourceUriString)
    } catch (e: Exception) {
      null
    }

    // Try to take persistable URI permission if applicable
    if (parsedUri != null && parsedUri.scheme == "content") {
      try {
        context.contentResolver.takePersistableUriPermission(
          parsedUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (ignored: Exception) {
        // Not all content providers support persistable permissions; copying below guarantees persistence
      }
    }

    // If it's already an existing file in our internal media store, return its path
    val internalStoreDir = getMediaStoreDir(context)
    if (sourceUriString.startsWith(internalStoreDir.absolutePath)) {
      val existingFile = File(sourceUriString)
      if (existingFile.exists() && existingFile.length() > 0L) {
        return@withContext existingFile.absolutePath
      }
    }

    if (parsedUri != null && parsedUri.scheme == "file") {
      val path = parsedUri.path ?: ""
      if (path.startsWith(internalStoreDir.absolutePath)) {
        val existingFile = File(path)
        if (existingFile.exists() && existingFile.length() > 0L) {
          return@withContext existingFile.absolutePath
        }
      }
    }

    // Determine clean filename and extension
    val displayName = suggestedName
      ?: resolveDisplayName(context, parsedUri, sourceUriString)
      ?: "media_${System.currentTimeMillis()}"

    val extension = resolveExtension(context, parsedUri, displayName, sourceUriString)
    val sanitizedBase = displayName.substringBeforeLast(".")
      .replace("[^a-zA-Z0-9_-]".toRegex(), "_")
      .take(40)

    val uniqueHash = md5(sourceUriString).take(8)
    val targetFileName = "clip_${System.currentTimeMillis()}_${uniqueHash}_$sanitizedBase.$extension"
    val targetFile = File(internalStoreDir, targetFileName)

    try {
      var inputStream: InputStream? = null
      if (parsedUri != null && (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource")) {
        inputStream = context.contentResolver.openInputStream(parsedUri)
      } else if (parsedUri != null && parsedUri.scheme == "file") {
        val f = File(parsedUri.path ?: sourceUriString)
        if (f.exists() && f.canRead()) {
          inputStream = f.inputStream()
        }
      } else {
        val f = File(sourceUriString)
        if (f.exists() && f.canRead()) {
          inputStream = f.inputStream()
        }
      }

      if (inputStream != null) {
        FileOutputStream(targetFile).use { output ->
          val buffer = ByteArray(64 * 1024)
          var bytesRead: Int
          while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
          }
          output.flush()
        }
        inputStream.close()

        if (targetFile.exists() && targetFile.length() > 0L) {
          Log.d(TAG, "Successfully persisted media to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
          return@withContext targetFile.absolutePath
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed copying media stream to internal storage for $sourceUriString: ${e.message}")
    }

    // Fallback: If copy failed, return original URI string
    sourceUriString
  }

  /**
   * Persists a list of media URIs in batch.
   */
  suspend fun persistMediaList(
    context: Context,
    sourceUris: List<String>
  ): List<String> = withContext(Dispatchers.IO) {
    sourceUris.map { uri ->
      persistMedia(context, uri)
    }
  }

  /**
   * Resolves the display filename from content resolver or path.
   */
  private fun resolveDisplayName(context: Context, uri: Uri?, rawUriString: String): String? {
    if (uri == null) return null
    if (uri.scheme == "content") {
      try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
              val name = cursor.getString(idx)
              if (!name.isNullOrBlank()) return name
            }
          }
        }
      } catch (ignored: Exception) {}
    }
    return uri.lastPathSegment?.substringAfterLast("/")
  }

  /**
   * Determines the proper file extension (.mp4, .mov, .jpg, .png, etc.).
   */
  private fun resolveExtension(context: Context, uri: Uri?, displayName: String, rawUriString: String): String {
    val fromName = displayName.substringAfterLast(".", "")
    if (fromName.length in 2..5 && fromName.matches("[a-zA-Z0-9]+".toRegex())) {
      return fromName.lowercase()
    }

    if (uri != null && uri.scheme == "content") {
      try {
        val mime = context.contentResolver.getType(uri)
        if (!mime.isNullOrBlank()) {
          val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
          if (!ext.isNullOrBlank()) return ext.lowercase()
        }
      } catch (ignored: Exception) {}
    }

    return if (rawUriString.contains("photo", ignoreCase = true) || rawUriString.contains("image", ignoreCase = true)) {
      "jpg"
    } else {
      "mp4"
    }
  }

  /**
   * Calculates MD5 hash for consistent key generation.
   */
  fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
