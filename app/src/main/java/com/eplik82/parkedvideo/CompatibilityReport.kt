package com.eplik82.parkedvideo

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/** Telefonis kuvatav kokkuvõte sellest, mida Android Auto rakenduse kohta näeb. */
object CompatibilityReport {
    private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
    private const val CAR_APP_SERVICE_ACTION = "androidx.car.app.CarAppService"

    fun build(context: Context): String {
        val pm = context.packageManager
        val yes = context.getString(R.string.compat_yes)
        val no = context.getString(R.string.compat_no)

        val androidAuto = try {
            pm.getPackageInfo(ANDROID_AUTO_PACKAGE, 0).versionName ?: yes
        } catch (e: PackageManager.NameNotFoundException) {
            context.getString(R.string.compat_missing)
        }

        // Sama päring, millega Android Auto rakendusi otsib.
        val service = pm.queryIntentServices(
            Intent(CAR_APP_SERVICE_ACTION).setPackage(context.packageName),
            PackageManager.GET_RESOLVED_FILTER,
        ).firstOrNull()
        val categories = service?.filter?.categoriesIterator()?.asSequence()
            ?.joinToString { it.substringAfterLast('.') }
            ?: "–"

        val own = pm.getPackageInfo(context.packageName, 0)
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        return context.getString(
            R.string.compat_report,
            androidAuto,
            if (service != null) yes else no,
            categories,
            own.versionName,
            PackageInfoCompat.getLongVersionCode(own).toString(),
            if (debuggable) yes else no,
            installer(context) ?: context.getString(R.string.compat_unknown),
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
        )
    }

    private fun installer(context: Context): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (e: Exception) {
        null
    }
}
