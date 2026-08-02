// Pure-Kotlin (framework-free) domain module. No Android dependencies here on
// purpose: the capture/parse/validate/dedup/categorize logic must be unit
// testable on the JVM without an emulator, and auditable in isolation.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(17) }
