import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("org.jetbrains.kotlin.jvm") }

// Deviation from the plan's `kotlin { jvmToolchain(17) }`: this machine has only JDK 21
// installed and no toolchain download repository, so a 17 toolchain spec fails to resolve.
// Pinning emitted bytecode to 17 instead keeps the module plain JVM with zero Android
// dependencies, still consumable by :app (which compiles with jvmTarget 17).
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies { testImplementation("junit:junit:4.13.2") }
