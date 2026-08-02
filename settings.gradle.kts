pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Lets Gradle auto-download a matching JDK (e.g. 17) when one isn't installed,
// so `jvmToolchain(17)` / compileOptions 17 don't fail on a fresh machine.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ledger"
include(":app")
include(":core-domain")
