package com.example.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.domain.StudioPreferencesManager
import java.util.Locale

object LocaleManager {

  val supportedLanguages = listOf(
    LanguageOption("English", "en", "English"),
    LanguageOption("اردو", "ur", "Urdu"),
    LanguageOption("中文", "zh", "Chinese"),
    LanguageOption("हिन्दी", "hi", "Hindi"),
    LanguageOption("العربية", "ar", "Arabic")
  )

  data class LanguageOption(
    val displayName: String,
    val code: String,
    val englishName: String
  )

  fun setLocale(context: Context, languageCode: String) {
    StudioPreferencesManager.updateLanguage(languageCode)
    try {
      val appLocale = LocaleListCompat.forLanguageTags(languageCode)
      AppCompatDelegate.setApplicationLocales(appLocale)
    } catch (_: Throwable) {
      // Fallback configuration update
      updateConfiguration(context, languageCode)
    }
  }

  fun updateConfiguration(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val res = context.resources
    val config = Configuration(res.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      config.setLocales(LocaleList(locale))
    } else {
      @Suppress("DEPRECATION")
      config.locale = locale
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      config.setLayoutDirection(locale)
    }
    return context.createConfigurationContext(config)
  }
}
