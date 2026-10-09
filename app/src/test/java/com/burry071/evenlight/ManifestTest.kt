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

    /**
     * The provider is a crash the code cannot see: `raw photo` and `share` compile, run and throw
     * `IllegalArgumentException: Failed to find configured root` (or `FileUriExposedException`, if the action was
     * written with a `file://` URI instead) on the phone and nowhere else. So the assertion covers the whole chain -
     * the declaration, its authority, and the XML file the authority resolves through - and the permission set is
     * asserted to have stayed `{CAMERA}` while it was added (ledger R7).
     */
    @Test fun declaresFileProviderForTheTwoActionsThatLeaveTheApp() {
        assertTrue("no FileProvider declaration", manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(
            "authority must be the applicationId plus .fileprovider",
            manifest.contains("android:authorities=\"\${applicationId}.fileprovider\"")
        )
        assertTrue("provider must not be exported", manifest.contains("android:exported=\"false\""))
        assertTrue("provider must grant uri permissions", manifest.contains("android:grantUriPermissions=\"true\""))
        val paths = File("src/main/res/xml/file_paths.xml")
        assertTrue("no ${paths.path} - the authority would resolve to nothing", paths.isFile)
    }
}
