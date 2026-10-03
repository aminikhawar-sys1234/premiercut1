package com.ahstudio.transition.provider

import com.ahstudio.transition.core.TransitionDefinition
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.validation.PackageFileSource
import com.ahstudio.transition.validation.PackageValidator
import java.io.File

/** Package source on local disk — the same path a future downloader writes into (§27). */
class DirectoryFileSource(private val root: File) : PackageFileSource {
    override fun exists(path: String) = File(root, path).isFile
    override fun readBytes(path: String) = File(root, path).takeIf { it.isFile }?.readBytes()
}

class DirectoryTransitionPackageProvider(private val packageRoot: File) : TransitionProvider {
    override fun loadDefinitions(): List<TransitionDefinition> {
        val manifest = File(packageRoot, "manifest.json").takeIf { it.isFile }?.readBytes()
            ?: return emptyList()
        return when (val r = PackageValidator.validateAndLoad(manifest, DirectoryFileSource(packageRoot))) {
            is TransitionResult.Ok -> r.value
            is TransitionResult.Err -> emptyList()   // invalid package never breaks the app (§26)
        }
    }
}
