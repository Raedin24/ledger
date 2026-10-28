// Plain Kotlin domain module with no Android dependencies, so the capture,
// parsing, validation, dedup and categorisation logic can be unit tested on
// the JVM without an emulator.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(17) }
