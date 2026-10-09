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

// FixtureEquivalenceTest reads the cross-language goldens (fixtures/*.png) off disk and writes the pinned
// bands back next to them, so the directory is named here from the root project layout instead of being
// guessed from the test JVM's working directory - the same reason :app declares its manifest input
// (app/build.gradle.kts). Declaring it as an input also means a regenerated PNG cannot leave the test
// reporting a stale green.
val fixturesDir = rootProject.layout.projectDirectory.dir("fixtures")

tasks.withType<Test>().configureEach {
    systemProperty("evenlight.fixturesDir", fixturesDir.asFile.absolutePath)
    inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)

    // The one-shot golden-writing switch, in both spellings: `-P` (a project property, per-invocation) and
    // the plan's `-D` form. `-D` on the Gradle CLI alone cannot work, because it lands on the daemon's JVM
    // and never reaches the forked test worker; it is forwarded here. Whether a forwarded daemon property
    // could then stick across builds and turn the gate run into another writing run was measured, not
    // assumed: `-Devenlight.probe=stickyA` on one invocation read back as null on the next one against the
    // same warm daemon, so the flag lives exactly as long as the invocation that set it.
    val writeFixtures = providers.gradleProperty("evenlight.writeFixtures")
        .orElse(providers.systemProperty("evenlight.writeFixtures"))
    if (writeFixtures.isPresent) {
        systemProperty("evenlight.writeFixtures", writeFixtures.get())
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
