package com.example.mysmarttv.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** The app's TV banner (the wide logo the Google TV home uses), or its icon if it has no banner. */
fun loadBanner(context: Context, packageName: String): ImageBitmap? = try {
    val pm = context.packageManager
    val drawable = pm.getApplicationBanner(packageName) ?: pm.getApplicationIcon(packageName)
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 320
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 180
    drawable.toBitmap(width, height).asImageBitmap()
} catch (e: PackageManager.NameNotFoundException) {
    null
}

fun appLabel(context: Context, packageName: String): String? = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (e: PackageManager.NameNotFoundException) {
    null
}

/** Every app that shows up in the TV's own app list, except this one. */
fun installedTvApps(context: Context): List<Channel> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .mapNotNull { pkg -> appLabel(context, pkg)?.let { Channel(it, pkg) } }
        .sortedBy { it.name.lowercase() }
}

fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
        return
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
