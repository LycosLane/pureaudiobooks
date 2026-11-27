package com.lycoslane.pureaudiobooks.core.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Checks Android battery optimization status for PureAudiobooks and opens the
 * relevant system settings screen so users can set battery usage to Unrestricted
 * without requiring the restricted REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission.
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }.getOrDefault(true)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val batteryListIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            context.startActivity(appDetailsIntent)
        }.onFailure {
            runCatching {
                context.startActivity(batteryListIntent)
            }
        }
    }
}
