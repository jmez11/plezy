package com.edde746.plezy

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

/**
 * Native mirror of PlezyActivity.getAndroidTvDetection(): any TV signal counts,
 * except that FEATURE_AUTOMOTIVE vetoes the verdict outright, and known VR
 * headsets (Quest/Oculus/Meta) are always handheld. Kept in sync with
 * the Dart-facing detection so native gating matches PlatformDetector.isTV().
 */
object TvDetection {
  fun isTv(context: Context): Boolean {
    val pm = context.packageManager
    val uiModeType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK

    // A VR headset is never a TV: Quest/Oculus/Meta devices can report TV-like
    // signals (no touchscreen flag, stray leanback flags). Models and
    // manufacturers vary by SKU, so check lowercase substrings.
    val model = Build.MODEL?.lowercase() ?: ""
    val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
    if (model.contains("oculus") || model.contains("quest") ||
        manufacturer.contains("oculus") || manufacturer.contains("meta")) {
      return false
    }

    // A car is never a TV: rotary-only head units report no touchscreen, and an
    // OEM image can carry a stray leanback flag.
    if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false

    @Suppress("DEPRECATION")
    return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
      pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
      pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
      pm.hasSystemFeature("amazon.hardware.fire_tv") ||
      !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
  }
}
