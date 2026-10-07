plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.burry071.evenlight"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.burry071.evenlight"
        minSdk = 27
        targetSdk = 35
    }
    buildTypes { getByName("debug") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// ManifestTest reads the source manifest off disk instead of the merged artifact, so the file is not an
// inferred input of :app:testDebugUnitTest. Without this declaration an incremental build can report the
// task UP-TO-DATE right after someone adds a permission, i.e. the guard stops guarding exactly the edit it
// exists to catch. Path comes from layout.projectDirectory (the :app module dir), never the test JVM's
// working directory, so the input points at the same file the test reads on every host.
val sourceManifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")

tasks.withType<Test>().configureEach {
    inputs.file(sourceManifest)
        .withPropertyName("evenlightSourceManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    testImplementation("junit:junit:4.13.2")
}
