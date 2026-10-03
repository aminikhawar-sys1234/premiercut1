package com.ahstudio.captions.core.language

object LanguageTag {
    fun baseOf(tag: String): String = tag.substringBefore('-').lowercase()
}
