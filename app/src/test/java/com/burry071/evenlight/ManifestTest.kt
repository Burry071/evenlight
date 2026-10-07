package com.burry071.evenlight

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTest {
    private val manifest: String by lazy { File("src/main/AndroidManifest.xml").readText() }

    @Test fun declaresNoInternetPermission() {
        assertFalse("INTERNET must never appear", manifest.contains("android.permission.INTERNET"))
    }

    @Test fun declaresNoNetworkStatePermission() {
        assertFalse(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test fun cameraIsTheOnlyRuntimePermission() {
        val permissions = Regex("android\\.permission\\.([A-Z_]+)")
            .findAll(manifest).map { it.groupValues[1] }.toSet()
        assertTrue("expected CAMERA, found $permissions", permissions == setOf("CAMERA"))
    }
}
