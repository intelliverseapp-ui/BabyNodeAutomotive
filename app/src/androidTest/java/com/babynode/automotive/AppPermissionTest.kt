package com.babynode.automotive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPermissionTest {

    @Test
    fun installedPackageDoesNotRequestRecordAudio() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        val packageInfo =
            context.packageManager
                .getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )

        val requestedPermissions =
            packageInfo.requestedPermissions
                ?.toSet()
                .orEmpty()

        assertFalse(
            requestedPermissions.contains(
                Manifest.permission.RECORD_AUDIO
            )
        )
    }
}
