package com.example.engine.plugin

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.domain.plugin.InstalledPlugin
import com.example.domain.plugin.PluginCategory
import com.example.domain.plugin.PluginItemManifest
import com.example.domain.plugin.PluginLifecycleState
import com.example.domain.plugin.PluginManifest
import com.example.domain.plugin.PluginModule
import com.example.domain.plugin.PluginValidationResult
import com.example.domain.plugin.StandardPluginModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PluginManager {
  private const val TAG = "PluginManager"
  private const val REGISTRY_FILE_NAME = "plugins_registry.json"

  private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
  val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

  private val activeModules = mutableMapOf<String, PluginModule>()
  private var isInitialized = false

  fun initialize(context: Context) {
    if (isInitialized) return
    isInitialized = true
    loadRegistry(context)
    autoDiscoverExtractedPlugins(context)
    autoDiscoverUploadedZips(context)
    ensureDefaultFiltersPackInstalled(context)
    
    // Initialize loaded modules
    for (plugin in _installedPlugins.value) {
      val module = StandardPluginModule(plugin)
      module.onInitialize(context)
      if (plugin.isEnabled) {
        module.onEnable()
      } else {
        module.onDisable()
      }
      activeModules[plugin.manifest.id] = module
    }
  }

  /**
   * Ensures default Hollywood & Cinematic filter plugins are available.
   */
  fun ensureDefaultFiltersPackInstalled(context: Context) {
    // Built-in cinematic filters are loaded dynamically via FilterPresets / ColorPipeline
  }

  /**
   * Automatically scans for untracked extracted plugins in the plugins directory.
   */
  fun autoDiscoverExtractedPlugins(context: Context) {
    try {
      val pluginsBaseDir = File(context.filesDir, "plugins")
      if (!pluginsBaseDir.exists() || !pluginsBaseDir.isDirectory) return
      
      val existingIds = _installedPlugins.value.map { it.manifest.id }.toSet()
      val subDirs = pluginsBaseDir.listFiles { f -> f.isDirectory } ?: return
      var discoveredAny = false
      val currentList = _installedPlugins.value.toMutableList()

      for (dir in subDirs) {
        if (dir.name in existingIds) continue
        val allJsonFiles = dir.walkTopDown().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.toList()
        val manifestFile = allJsonFiles.find { it.name.equals("plugin.json", ignoreCase = true) }
          ?: allJsonFiles.find { it.name.equals("manifest.json", ignoreCase = true) }
          ?: allJsonFiles.find { it.name.equals("integration.json", ignoreCase = true) }
          ?: allJsonFiles.find { it.name.equals("package.json", ignoreCase = true) }
          ?: allJsonFiles.find { it.name.equals("filters.json", ignoreCase = true) }
          ?: allJsonFiles.find { it.name.equals("luts.json", ignoreCase = true) }

        val integrationFile = allJsonFiles.find { it.name.equals("integration.json", ignoreCase = true) }
        val readmeFile = dir.walkTopDown().find { it.isFile && (it.name.equals("README.txt", ignoreCase = true) || it.name.equals("README.md", ignoreCase = true)) }

        val manifest = parseAndBuildManifest(
          manifestFile = manifestFile,
          integrationFile = integrationFile,
          readmeFile = readmeFile,
          rootDir = dir,
          zipFileName = dir.name
        )

        val installedPlugin = InstalledPlugin(
          manifest = manifest,
          installDirAbsolutePath = dir.absolutePath,
          isEnabled = true,
          installedTimestamp = System.currentTimeMillis()
        )
        currentList.removeAll { it.manifest.id == manifest.id }
        currentList.add(installedPlugin)
        discoveredAny = true
        Log.d(TAG, "Auto-discovered unindexed plugin: ${manifest.name} (${manifest.id})")
      }

      if (discoveredAny) {
        _installedPlugins.value = currentList
        saveRegistry(context)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error during autoDiscoverExtractedPlugins", e)
    }
  }

  /**
   * Scans storage locations for any uploaded ZIP files and auto-installs them.
   */
  fun autoDiscoverUploadedZips(context: Context) {
    try {
      val searchDirs = listOfNotNull(
        context.cacheDir,
        context.filesDir,
        context.getExternalFilesDir(null)
      )

      for (dir in searchDirs) {
        val zipFiles = dir.listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) && !f.name.startsWith("plugin_upload_") } ?: continue
        for (zip in zipFiles) {
          Log.d(TAG, "Found uploaded plugin zip: ${zip.name}, attempting auto-installation...")
          installPluginFromZipFile(context, zip)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in autoDiscoverUploadedZips", e)
    }
  }

  /**
   * Registers a custom or dynamically loaded PluginModule.
   */
  fun registerModule(context: Context, module: PluginModule) {
    module.onInitialize(context)
    if (module.state == PluginLifecycleState.ENABLED) {
      module.onEnable()
    }
    activeModules[module.id] = module
    Log.d(TAG, "Registered PluginModule: ${module.id} (${module.name})")
  }

  /**
   * Unregisters and unloads a PluginModule.
   */
  fun unregisterModule(pluginId: String) {
    activeModules[pluginId]?.let { module ->
      module.onDisable()
      module.onUnload()
      activeModules.remove(pluginId)
      Log.d(TAG, "Unregistered PluginModule: $pluginId")
    }
  }

  /**
   * Returns all currently active loaded PluginModules.
   */
  fun getLoadedModules(): List<PluginModule> {
    return activeModules.values.toList()
  }

  /**
   * Retrieves specific PluginModule by ID.
   */
  fun getModule(pluginId: String): PluginModule? {
    return activeModules[pluginId]
  }

  /**
   * Installs a plugin from a Content Uri (e.g. selected via system File Picker).
   */
  fun installPluginFromUri(context: Context, uri: Uri): PluginValidationResult {
    return try {
      val contentResolver = context.contentResolver
      val inputStream = contentResolver.openInputStream(uri)
        ?: return PluginValidationResult.Error("Could not open input stream from file URI.")
      val tempZipFile = File(context.cacheDir, "plugin_upload_${System.currentTimeMillis()}.zip")
      
      FileOutputStream(tempZipFile).use { output ->
        inputStream.copyTo(output)
      }

      val result = installPluginFromZipFile(context, tempZipFile)
      tempZipFile.delete()
      result
    } catch (e: Exception) {
      Log.e(TAG, "Error installing plugin from Uri", e)
      PluginValidationResult.Error("Failed to read plugin ZIP: ${e.localizedMessage}")
    }
  }

  /**
   * Installs or updates a plugin from a local ZIP File.
   * Performs path traversal security checks, extracts package contents,
   * validates manifest structure, and registers the plugin.
   */
  fun installPluginFromZipFile(context: Context, zipFile: File): PluginValidationResult {
    if (!zipFile.exists() || zipFile.length() == 0L) {
      return PluginValidationResult.Error("ZIP file does not exist or is empty.")
    }

    val pluginsBaseDir = File(context.filesDir, "plugins")
    if (!pluginsBaseDir.exists()) {
      pluginsBaseDir.mkdirs()
    }

    val tempExtractDir = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}")
    if (!tempExtractDir.mkdirs()) {
      return PluginValidationResult.Error("Failed to create temporary extraction directory.")
    }

    try {
      // 1. Safe ZIP Extraction with Path Traversal Protection
      ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
        var entry: ZipEntry? = zipIn.nextEntry
        while (entry != null) {
          val destinationFile = File(tempExtractDir, entry.name)
          
          // SECURITY: Prevent Zip Slip path traversal vulnerability
          val canonicalDest = destinationFile.canonicalPath
          val canonicalBase = tempExtractDir.canonicalPath
          if (!canonicalDest.startsWith(canonicalBase + File.separator) && canonicalDest != canonicalBase) {
            tempExtractDir.deleteRecursively()
            return PluginValidationResult.Error("Security Error: ZIP entry attempted path traversal (${entry.name})")
          }

          if (entry.isDirectory) {
            destinationFile.mkdirs()
          } else {
            destinationFile.parentFile?.mkdirs()
            FileOutputStream(destinationFile).use { out ->
              zipIn.copyTo(out)
            }
          }
          zipIn.closeEntry()
          entry = zipIn.nextEntry
        }
      }

      // 2. Locate Manifest & Descriptor Files (plugin.json, manifest.json, integration.json, package.json)
      val allJsonFiles = tempExtractDir.walkTopDown().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.toList()
      
      var manifestFile = allJsonFiles.find { it.name.equals("plugin.json", ignoreCase = true) }
        ?: allJsonFiles.find { it.name.equals("manifest.json", ignoreCase = true) }
        ?: allJsonFiles.find { it.name.equals("integration.json", ignoreCase = true) }
        ?: allJsonFiles.find { it.name.equals("package.json", ignoreCase = true) }

      val integrationFile = allJsonFiles.find { it.name.equals("integration.json", ignoreCase = true) }
      val readmeFile = tempExtractDir.walkTopDown().find { it.isFile && (it.name.equals("README.txt", ignoreCase = true) || it.name.equals("README.md", ignoreCase = true)) }

      val effectiveRootDir = manifestFile?.parentFile ?: tempExtractDir

      // 3. Parse and Merge Manifest & Integration JSONs with Resilient Fallbacks
      val manifest = parseAndBuildManifest(
        manifestFile = manifestFile,
        integrationFile = integrationFile,
        readmeFile = readmeFile,
        rootDir = effectiveRootDir,
        zipFileName = zipFile.nameWithoutExtension
      )

      val sourceDir = effectiveRootDir

      // 4. Copy to permanent Plugin Directory (/data/data/com.example/files/plugins/<plugin_id>/)
      val targetPluginDir = File(pluginsBaseDir, manifest.id)
      if (targetPluginDir.exists()) {
        targetPluginDir.deleteRecursively()
      }
      sourceDir.copyRecursively(targetPluginDir, overwrite = true)
      tempExtractDir.deleteRecursively()

      // 5. Register Plugin
      val installedPlugin = InstalledPlugin(
        manifest = manifest,
        installDirAbsolutePath = targetPluginDir.absolutePath,
        isEnabled = true,
        installedTimestamp = System.currentTimeMillis()
      )

      val currentList = _installedPlugins.value.toMutableList()
      currentList.removeAll { it.manifest.id == manifest.id }
      currentList.add(installedPlugin)
      _installedPlugins.value = currentList

      val module = StandardPluginModule(installedPlugin)
      registerModule(context, module)

      saveRegistry(context)

      Log.d(TAG, "Successfully installed plugin '${manifest.name}' v${manifest.version} with ${manifest.items.size} assets.")
      return PluginValidationResult.Success(
        installedPlugin = installedPlugin,
        message = "Plugin '${manifest.name}' installed successfully with ${manifest.items.size} assets!"
      )

    } catch (e: Exception) {
      Log.e(TAG, "Error unzipping and installing plugin package", e)
      tempExtractDir.deleteRecursively()
      return PluginValidationResult.Error("Failed to extract plugin package: ${e.localizedMessage}")
    }
  }

  fun uninstallPlugin(context: Context, pluginId: String): Boolean {
    val plugin = _installedPlugins.value.find { it.manifest.id == pluginId } ?: return false
    try {
      unregisterModule(pluginId)
      val dir = File(plugin.installDirAbsolutePath)
      if (dir.exists()) {
        dir.deleteRecursively()
      }
      val newList = _installedPlugins.value.filter { it.manifest.id != pluginId }
      _installedPlugins.value = newList
      saveRegistry(context)
      Log.d(TAG, "Uninstalled plugin: $pluginId")
      return true
    } catch (e: Exception) {
      Log.e(TAG, "Error uninstalling plugin $pluginId", e)
      return false
    }
  }

  fun registerDynamicPlugin(context: Context, plugin: InstalledPlugin) {
    val existing = _installedPlugins.value.filter { it.manifest.id != plugin.manifest.id }
    _installedPlugins.value = existing + plugin

    val module = StandardPluginModule(plugin)
    module.onInitialize(context)
    if (plugin.isEnabled) module.onEnable()
    activeModules[plugin.manifest.id] = module

    saveRegistry(context)
  }

  fun togglePluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
    val newList = _installedPlugins.value.map {
      if (it.manifest.id == pluginId) it.copy(isEnabled = enabled) else it
    }
    _installedPlugins.value = newList
    
    activeModules[pluginId]?.let { module ->
      if (enabled) module.onEnable() else module.onDisable()
    }
    
    saveRegistry(context)
  }

  fun getEnabledItemsForCategory(category: PluginCategory): List<Pair<InstalledPlugin, PluginItemManifest>> {
    val result = mutableListOf<Pair<InstalledPlugin, PluginItemManifest>>()
    for (plugin in _installedPlugins.value) {
      if (!plugin.isEnabled) continue
      val pluginCat = plugin.manifest.category
      for (item in plugin.manifest.items) {
        val itemCategory = if (item.categoryKey.isNotBlank() && item.categoryKey != "items" && item.categoryKey != "assets" && item.categoryKey != "other") {
          PluginCategory.fromKey(item.categoryKey)
        } else {
          pluginCat
        }
        if (itemCategory == category || (category == PluginCategory.FILTER && pluginCat == PluginCategory.FILTER)) {
          result.add(Pair(plugin, item))
        }
      }
    }
    return result
  }

  fun getEnabledItemsForCategoryKey(categoryKey: String): List<Pair<InstalledPlugin, PluginItemManifest>> {
    return getEnabledItemsForCategory(PluginCategory.fromKey(categoryKey))
  }

  /**
   * Resiliently extracts manifest metadata and auto-discovers all fonts and templates.
   */
  private fun parseAndBuildManifest(
    manifestFile: File?,
    integrationFile: File?,
    readmeFile: File?,
    rootDir: File,
    zipFileName: String
  ): PluginManifest {
    var manifestJsonObj: JSONObject? = null
    var integrationJsonObj: JSONObject? = null

    if (manifestFile != null && manifestFile.exists()) {
      try {
        val text = manifestFile.readText().trim()
        if (text.startsWith("{")) {
          manifestJsonObj = JSONObject(text)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not parse manifestFile as JSONObject", e)
      }
    }

    if (integrationFile != null && integrationFile.exists()) {
      try {
        val text = integrationFile.readText().trim()
        if (text.startsWith("{")) {
          integrationJsonObj = JSONObject(text)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not parse integrationFile as JSONObject", e)
      }
    }

    // Merge attributes: Check manifest first, then integration
    fun optField(vararg keys: String): String {
      for (k in keys) {
        val v = manifestJsonObj?.optString(k, "")?.trim() ?: ""
        if (v.isNotBlank()) return v
        val iv = integrationJsonObj?.optString(k, "")?.trim() ?: ""
        if (iv.isNotBlank()) return iv
      }
      return ""
    }

    // Check nested objects: manifest.optJSONObject("plugin"), integration.optJSONObject("plugin")
    fun optNestedField(vararg keys: String): String {
      val nestedContainers = listOf("plugin", "manifest", "package", "metadata", "info", "extension")
      for (containerKey in nestedContainers) {
        val mSub = manifestJsonObj?.optJSONObject(containerKey)
        val iSub = integrationJsonObj?.optJSONObject(containerKey)
        for (k in keys) {
          val v = mSub?.optString(k, "")?.trim() ?: ""
          if (v.isNotBlank()) return v
          val iv = iSub?.optString(k, "")?.trim() ?: ""
          if (iv.isNotBlank()) return iv
        }
      }
      return ""
    }

    // 1. Resilient ID Resolution
    var rawId = optField("id", "plugin_id", "pluginId", "pluginID", "package", "package_name", "packageName", "identifier", "slug", "key")
    if (rawId.isBlank()) {
      rawId = optNestedField("id", "plugin_id", "pluginId", "package", "identifier", "slug")
    }

    // 2. Resilient Name Resolution
    var rawName = optField("name", "title", "plugin_name", "pluginName", "displayName", "display_name", "label", "package_title")
    if (rawName.isBlank()) {
      rawName = optNestedField("name", "title", "plugin_name", "displayName", "label")
    }

    // Derive ID if missing
    val id = if (rawId.isNotBlank()) {
      rawId.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
    } else if (rawName.isNotBlank()) {
      rawName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
    } else {
      zipFileName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "plugin_${System.currentTimeMillis()}" }
    }

    // Derive Name if missing
    val name = if (rawName.isNotBlank()) {
      rawName
    } else {
      id.replace('_', ' ').replace('-', ' ').split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() } }
        .ifBlank { "Extension Pack" }
    }

    val version = optField("version", "ver", "v").ifBlank { "1.0.0" }
    val author = optField("author", "creator", "publisher", "vendor", "developer").ifBlank { "AH Studio Community" }
    
    var description = optField("description", "desc", "summary", "about")
    if (description.isBlank() && readmeFile != null && readmeFile.exists()) {
      try {
        val lines = readmeFile.readLines().filter { it.isNotBlank() }
        description = lines.take(2).joinToString(" ")
      } catch (_: Exception) {}
    }
    if (description.isBlank()) {
      description = "Creative templates and typography pack for AH Video Studio."
    }

    var rawType = optField("type", "category", "plugin_type", "kind")
    if (rawType.isBlank() && (zipFileName.contains("filter", ignoreCase = true) || zipFileName.contains("lut", ignoreCase = true))) {
      rawType = "filter"
    }

    val icon = optField("icon", "preview", "thumbnail")
    val minVersion = optField("minimumAppVersion", "min_version").ifBlank { "1.0.0" }

    // 3. Asset Discovery: Gather items from JSON + Directory structures
    val discoveredItems = mutableListOf<PluginItemManifest>()
    val seenItemIds = mutableSetOf<String>()

    fun addItem(item: PluginItemManifest) {
      if (item.id !in seenItemIds) {
        seenItemIds.add(item.id)
        discoveredItems.add(item)
      }
    }

    // A. Parse explicitly declared items in manifestJsonObj / integrationJsonObj
    val explicitArrays = listOf("items", "assets", "filters", "luts", "presets", "templates", "fonts", "captions", "quotes", "business", "youtube", "islamic", "reels", "stickers", "audio")
    for (arrName in explicitArrays) {
      val arr = manifestJsonObj?.optJSONArray(arrName) ?: integrationJsonObj?.optJSONArray(arrName)
      if (arr != null) {
        val effectiveDefaultCategory = when {
          arrName in listOf("filters", "luts", "presets") -> "filter"
          arrName in listOf("fonts") -> "font"
          arrName in listOf("templates", "captions", "quotes", "business", "youtube", "islamic", "reels") -> "text_template"
          arrName in listOf("stickers") -> "sticker"
          arrName in listOf("audio") -> "audio"
          rawType.isNotBlank() && rawType != "items" && rawType != "assets" -> rawType
          zipFileName.contains("filter", ignoreCase = true) -> "filter"
          else -> "filter"
        }
        for (i in 0 until arr.length()) {
          val itemObj = arr.optJSONObject(i) ?: continue
          val item = parseItemJsonObject(itemObj, id, discoveredItems.size, effectiveDefaultCategory)
          addItem(item)
        }
      }
    }

    // B. Scan filters/ and luts/ directories for filters.json, luts.json, and .cube/.lut files
    val filterDirNames = listOf("filters", "luts", "presets", "color_grading", "LUTs", "Filters", "Presets")
    for (fDirName in filterDirNames) {
      val fDir = File(rootDir, fDirName)
      if (fDir.exists() && fDir.isDirectory) {
        fDir.walkTopDown().forEach { file ->
          if (file.isFile) {
            if (file.extension.equals("json", ignoreCase = true)) {
              try {
                val fJsonText = file.readText().trim()
                if (fJsonText.startsWith("[")) {
                  val arr = JSONArray(fJsonText)
                  for (j in 0 until arr.length()) {
                    val fObj = arr.optJSONObject(j) ?: continue
                    addItem(parseItemJsonObject(fObj, id, discoveredItems.size, "filter"))
                  }
                } else if (fJsonText.startsWith("{")) {
                  val rootFObj = JSONObject(fJsonText)
                  val arr = rootFObj.optJSONArray("filters") ?: rootFObj.optJSONArray("items") ?: rootFObj.optJSONArray("luts")
                  if (arr != null) {
                    for (j in 0 until arr.length()) {
                      val fObj = arr.optJSONObject(j) ?: continue
                      addItem(parseItemJsonObject(fObj, id, discoveredItems.size, "filter"))
                    }
                  } else {
                    addItem(parseItemJsonObject(rootFObj, id, discoveredItems.size, "filter"))
                  }
                }
              } catch (e: Exception) {
                Log.w(TAG, "Error parsing filter json: ${file.path}", e)
              }
            } else if (file.extension.equals("cube", ignoreCase = true) || file.extension.equals("lut", ignoreCase = true)) {
              val filterId = "${id}_lut_${file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
              val filterName = file.nameWithoutExtension.replace('_', ' ').split(' ')
                .joinToString(" ") { it.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() } }
              addItem(
                PluginItemManifest(
                  id = filterId,
                  name = filterName,
                  description = "3D Cinematic LUT Filter",
                  preview = "",
                  file = file.relativeTo(rootDir).path,
                  emoji = "🎬",
                  categoryKey = "filter",
                  parameters = mapOf("lutFile" to file.relativeTo(rootDir).path)
                )
              )
            }
          }
        }
      }
    }

    // C. Scan rootDir for top-level .cube/.lut files or filters.json
    rootDir.listFiles()?.forEach { file ->
      if (file.isFile && (file.extension.equals("cube", ignoreCase = true) || file.extension.equals("lut", ignoreCase = true))) {
        val filterId = "${id}_lut_${file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
        val filterName = file.nameWithoutExtension.replace('_', ' ').split(' ')
          .joinToString(" ") { it.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() } }
        addItem(
          PluginItemManifest(
            id = filterId,
            name = filterName,
            description = "3D Cinematic LUT Filter",
            preview = "",
            file = file.relativeTo(rootDir).path,
            emoji = "🎬",
            categoryKey = "filter",
            parameters = mapOf("lutFile" to file.relativeTo(rootDir).path)
          )
        )
      }
    }

    // B. Scan fonts/ directory for fonts.json and font binaries (.ttf, .otf)
    val fontsDir = File(rootDir, "fonts")
    if (fontsDir.exists() && fontsDir.isDirectory) {
      fontsDir.walkTopDown().forEach { file ->
        if (file.isFile) {
          if (file.extension.equals("json", ignoreCase = true)) {
            try {
              val fontJsonText = file.readText().trim()
              val relPath = file.relativeTo(rootDir).path
              val isUrdu = file.path.contains("urdu", ignoreCase = true)
              val lang = if (isUrdu) "Urdu" else "English"
              val defaultEmoji = if (isUrdu) "🇵🇰" else "🔤"

              if (fontJsonText.startsWith("[")) {
                val jsonArr = JSONArray(fontJsonText)
                for (j in 0 until jsonArr.length()) {
                  val fObj = jsonArr.optJSONObject(j) ?: continue
                  val fId = fObj.optString("id", "${id}_font_${discoveredItems.size}")
                  val fName = fObj.optString("name", fObj.optString("title", "Font ${discoveredItems.size}"))
                  val fFile = fObj.optString("file", fObj.optString("path", ""))
                  val fSample = fObj.optString("sample", fObj.optString("sampleText", if (isUrdu) "جمیل نوری نستعلیق خطاطی" else fName))
                  val params = mutableMapOf<String, Any>(
                    "language" to lang,
                    "category" to lang,
                    "sample" to fSample,
                    "fontFamily" to fObj.optString("fontFamily", fName)
                  )
                  addItem(
                    PluginItemManifest(
                      id = fId,
                      name = fName,
                      description = fObj.optString("description", "$lang Font Option"),
                      preview = fObj.optString("preview", ""),
                      file = fFile,
                      emoji = fObj.optString("emoji", defaultEmoji),
                      categoryKey = "font",
                      parameters = params
                    )
                  )
                }
              } else if (fontJsonText.startsWith("{")) {
                val rootFObj = JSONObject(fontJsonText)
                val list = rootFObj.optJSONArray("fonts") ?: rootFObj.optJSONArray("items")
                if (list != null) {
                  for (j in 0 until list.length()) {
                    val fObj = list.optJSONObject(j) ?: continue
                    val fId = fObj.optString("id", "${id}_font_${discoveredItems.size}")
                    val fName = fObj.optString("name", fObj.optString("title", "Font ${discoveredItems.size}"))
                    val fFile = fObj.optString("file", fObj.optString("path", ""))
                    val fSample = fObj.optString("sample", fObj.optString("sampleText", if (isUrdu) "جمیل نوری نستعلیق خطاطی" else fName))
                    val params = mutableMapOf<String, Any>(
                      "language" to lang,
                      "category" to lang,
                      "sample" to fSample,
                      "fontFamily" to fObj.optString("fontFamily", fName)
                    )
                    addItem(
                      PluginItemManifest(
                        id = fId,
                        name = fName,
                        description = fObj.optString("description", "$lang Font Option"),
                        preview = fObj.optString("preview", ""),
                        file = fFile,
                        emoji = fObj.optString("emoji", defaultEmoji),
                        categoryKey = "font",
                        parameters = params
                      )
                    )
                  }
                }
              }
            } catch (e: Exception) {
              Log.w(TAG, "Error parsing font json file: ${file.path}", e)
            }
          } else if (file.extension.equals("ttf", ignoreCase = true) || file.extension.equals("otf", ignoreCase = true)) {
            val relPath = file.relativeTo(rootDir).path
            val fontId = "${id}_${file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
            val isUrdu = file.path.contains("urdu", ignoreCase = true) || file.name.contains("urdu", ignoreCase = true) || file.name.contains("nastaliq", ignoreCase = true)
            val lang = if (isUrdu) "Urdu" else "English"
            val fontTitle = file.nameWithoutExtension.replace('_', ' ').split(' ')
              .joinToString(" ") { it.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() } }
            
            addItem(
              PluginItemManifest(
                id = fontId,
                name = fontTitle,
                description = "$lang Typography Font File",
                preview = "",
                file = relPath,
                emoji = if (isUrdu) "🇵🇰" else "🔤",
                categoryKey = "font",
                parameters = mapOf(
                  "language" to lang,
                  "category" to lang,
                  "sample" to if (isUrdu) "اردو خطاطی ویڈیو ٹیکسٹ" else fontTitle,
                  "fontFamily" to file.nameWithoutExtension
                )
              )
            )
          }
        }
      }
    }

    // C. Scan templates/ directory for template JSON files (captions, quotes, business, youtube, islamic, reels)
    val templatesDir = File(rootDir, "templates")
    if (templatesDir.exists() && templatesDir.isDirectory) {
      templatesDir.walkTopDown().forEach { file ->
        if (file.isFile && file.extension.equals("json", ignoreCase = true)) {
          try {
            val tplText = file.readText().trim()
            if (tplText.startsWith("{")) {
              val tplObj = JSONObject(tplText)
              val tplId = tplObj.optString("id", "${id}_${file.nameWithoutExtension}")
              val parentFolder = file.parentFile?.name?.lowercase() ?: ""
              
              val tplCategory = when {
                tplObj.optString("category").isNotBlank() -> tplObj.optString("category")
                parentFolder.contains("islamic") -> "Islamic"
                parentFolder.contains("quote") -> "Quotes"
                parentFolder.contains("caption") -> "Captions"
                parentFolder.contains("business") -> "Business"
                parentFolder.contains("youtube") -> "YouTube"
                parentFolder.contains("reel") -> "Reels"
                else -> "Text Templates"
              }

              val tplEmoji = when {
                tplObj.optString("emoji").isNotBlank() -> tplObj.optString("emoji")
                tplCategory.equals("Islamic", ignoreCase = true) -> "🕌"
                tplCategory.equals("Business", ignoreCase = true) -> "💼"
                tplCategory.equals("YouTube", ignoreCase = true) -> "▶️"
                tplCategory.equals("Reels", ignoreCase = true) -> "📱"
                tplCategory.equals("Quotes", ignoreCase = true) -> "📜"
                tplCategory.equals("Captions", ignoreCase = true) -> "💬"
                else -> "✨"
              }

              val defaultName = file.nameWithoutExtension.replace('_', ' ').split(' ')
                .joinToString(" ") { it.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() } }
              val tplName = tplObj.optString("name", tplObj.optString("title", defaultName))

              val params = mutableMapOf<String, Any>()
              val keys = tplObj.keys()
              while (keys.hasNext()) {
                val k = keys.next()
                val v = tplObj.get(k)
                if (v is JSONArray) {
                  val l = mutableListOf<Any>()
                  for (idx in 0 until v.length()) l.add(v.get(idx))
                  params[k] = l
                } else {
                  params[k] = v
                }
              }

              params["category"] = tplCategory
              if (!params.containsKey("sampleText")) {
                params["sampleText"] = tplObj.optString("text", tplObj.optString("sample", tplName))
              }
              if (!params.containsKey("fontFamily")) {
                params["fontFamily"] = if (tplCategory.equals("Islamic", ignoreCase = true)) "jameel_nastaliq" else "Sans-Serif"
              }

              addItem(
                PluginItemManifest(
                  id = tplId,
                  name = tplName,
                  description = tplObj.optString("description", "$tplCategory Video Template"),
                  preview = tplObj.optString("preview", ""),
                  file = file.relativeTo(rootDir).path,
                  emoji = tplEmoji,
                  categoryKey = "text_template",
                  parameters = params
                )
              )
            }
          } catch (e: Exception) {
            Log.w(TAG, "Error parsing template json file: ${file.path}", e)
          }
        }
      }
    }

    // Determine finalized plugin category/type
    val finalType = when {
      rawType.isNotBlank() && rawType != "items" && rawType != "assets" -> rawType
      discoveredItems.any { it.itemCategory == PluginCategory.FILTER || it.categoryKey == "filter" } -> "filter"
      discoveredItems.any { it.categoryKey == "font" } && discoveredItems.any { it.categoryKey == "text_template" } -> "templates_and_fonts"
      discoveredItems.any { it.categoryKey == "font" } -> "font"
      discoveredItems.any { it.categoryKey == "text_template" } -> "text_template"
      discoveredItems.any { it.categoryKey == "sticker" } -> "sticker"
      discoveredItems.any { it.categoryKey == "audio" } -> "audio"
      else -> "filter"
    }

    return PluginManifest(
      id = id,
      name = name,
      version = version,
      author = author,
      description = description,
      type = finalType,
      minimumAppVersion = minVersion,
      icon = icon,
      items = discoveredItems
    )
  }

  private fun parseItemJsonObject(itemObj: JSONObject, pluginId: String, index: Int, defaultCategory: String): PluginItemManifest {
    val itemId = itemObj.optString("id", "${pluginId}_item_$index")
    val itemName = itemObj.optString("name", itemObj.optString("title", "Asset $index"))
    val itemDesc = itemObj.optString("description", "")
    val itemPreview = itemObj.optString("preview", "")
    val itemFile = itemObj.optString("file", itemObj.optString("path", ""))
    val itemEmoji = itemObj.optString("emoji", "🎬")
    
    val rawCat = itemObj.optString("categoryKey", itemObj.optString("category", defaultCategory))
    val itemCatKey = if (rawCat.isBlank() || rawCat == "items" || rawCat == "assets") {
      if (defaultCategory.isNotBlank() && defaultCategory != "items" && defaultCategory != "assets") defaultCategory else "filter"
    } else {
      rawCat
    }

    val paramsMap = mutableMapOf<String, Any>()
    val paramsObj = itemObj.optJSONObject("parameters")
    if (paramsObj != null) {
      val keys = paramsObj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val v = paramsObj.get(k)
        if (v is JSONArray) {
          val list = mutableListOf<Any>()
          for (j in 0 until v.length()) {
            list.add(v.get(j))
          }
          paramsMap[k] = list
        } else {
          paramsMap[k] = v
        }
      }
    } else {
      val keys = itemObj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        if (k !in listOf("id", "name", "description", "preview", "file", "emoji", "categoryKey", "category")) {
          val v = itemObj.get(k)
          if (v is JSONArray) {
            val list = mutableListOf<Any>()
            for (j in 0 until v.length()) list.add(v.get(j))
            paramsMap[k] = list
          } else {
            paramsMap[k] = v
          }
        }
      }
    }

    return PluginItemManifest(
      id = itemId,
      name = itemName,
      description = itemDesc,
      preview = itemPreview,
      file = itemFile,
      emoji = itemEmoji,
      categoryKey = itemCatKey,
      parameters = paramsMap
    )
  }

  private fun parseManifestJson(jsonStr: String): PluginManifest? {
    return try {
      val obj = JSONObject(jsonStr)
      val id = obj.optString("id", "plugin_${System.currentTimeMillis()}")
      val name = obj.optString("name", "Plugin Extension")
      val version = obj.optString("version", "1.0.0")
      val author = obj.optString("author", "Community Creator")
      val description = obj.optString("description", "")
      val type = obj.optString("type", obj.optString("category", "other"))
      val minVersion = obj.optString("minimumAppVersion", "1.0.0")
      val icon = obj.optString("icon", "")

      val itemsList = mutableListOf<PluginItemManifest>()
      val itemsArray = obj.optJSONArray("items") ?: obj.optJSONArray("assets")
      if (itemsArray != null) {
        for (i in 0 until itemsArray.length()) {
          val itemObj = itemsArray.optJSONObject(i) ?: continue
          itemsList.add(parseItemJsonObject(itemObj, id, i, type))
        }
      }

      PluginManifest(
        id = id,
        name = name,
        version = version,
        author = author,
        description = description,
        type = type,
        minimumAppVersion = minVersion,
        icon = icon,
        items = itemsList
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing plugin manifest JSON", e)
      null
    }
  }

  private fun saveRegistry(context: Context) {
    try {
      val jsonArray = JSONArray()
      for (plugin in _installedPlugins.value) {
        val obj = JSONObject().apply {
          put("id", plugin.manifest.id)
          put("name", plugin.manifest.name)
          put("version", plugin.manifest.version)
          put("author", plugin.manifest.author)
          put("description", plugin.manifest.description)
          put("type", plugin.manifest.type)
          put("minimumAppVersion", plugin.manifest.minimumAppVersion)
          put("icon", plugin.manifest.icon)
          put("installDirAbsolutePath", plugin.installDirAbsolutePath)
          put("isEnabled", plugin.isEnabled)
          put("installedTimestamp", plugin.installedTimestamp)

          val itemsArray = JSONArray()
          for (item in plugin.manifest.items) {
            val itemObj = JSONObject().apply {
              put("id", item.id)
              put("name", item.name)
              put("description", item.description)
              put("preview", item.preview)
              put("file", item.file)
              put("emoji", item.emoji)
              put("categoryKey", item.categoryKey)
              
              if (item.parameters.isNotEmpty()) {
                val pObj = JSONObject()
                for ((k, v) in item.parameters) {
                  if (v is List<*>) {
                    val arr = JSONArray()
                    v.forEach { elem -> arr.put(elem) }
                    pObj.put(k, arr)
                  } else {
                    pObj.put(k, v)
                  }
                }
                put("parameters", pObj)
              }
            }
            itemsArray.put(itemObj)
          }
          put("items", itemsArray)
        }
        jsonArray.put(obj)
      }

      val registryFile = File(context.filesDir, REGISTRY_FILE_NAME)
      registryFile.writeText(jsonArray.toString(2))
      Log.d(TAG, "Saved plugin registry with ${_installedPlugins.value.size} plugins.")
    } catch (e: Exception) {
      Log.e(TAG, "Error saving plugin registry", e)
    }
  }

  private fun loadRegistry(context: Context) {
    try {
      val registryFile = File(context.filesDir, REGISTRY_FILE_NAME)
      if (!registryFile.exists()) return

      val jsonStr = registryFile.readText()
      if (jsonStr.isBlank()) return

      val jsonArray = JSONArray(jsonStr)
      val loadedList = mutableListOf<InstalledPlugin>()

      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val installDir = obj.optString("installDirAbsolutePath")
        val dirFile = File(installDir)
        if (!dirFile.exists()) continue // Skipped deleted plugins

        val manifest = parseManifestJson(obj.toString()) ?: continue
        val isEnabled = obj.optBoolean("isEnabled", true)
        val timestamp = obj.optLong("installedTimestamp", System.currentTimeMillis())

        loadedList.add(
          InstalledPlugin(
            manifest = manifest,
            installDirAbsolutePath = installDir,
            isEnabled = isEnabled,
            installedTimestamp = timestamp
          )
        )
      }

      _installedPlugins.value = loadedList
      Log.d(TAG, "Loaded ${loadedList.size} installed plugins from registry.")
    } catch (e: Exception) {
      Log.e(TAG, "Error loading plugin registry", e)
    }
  }
}
