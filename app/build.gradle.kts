import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("androidx.room")
}

// Release signing details come from a gitignored keystore.properties, or from
// environment variables for CI. Neither the keystore nor its passwords are in
// the repository. The signing key is permanent: Android won't update an app
// whose certificate has changed.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseStore = signingValue("storeFile", "LEDGER_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "LEDGER_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "LEDGER_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "LEDGER_KEY_PASSWORD")
val canSignRelease = listOf(
    releaseStore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.ledger.app"
    compileSdk = 36

    defaultConfig {
        // Different from `namespace`, which stays com.ledger.app. The namespace
        // only decides where R and BuildConfig are generated.
        applicationId = "io.github.raedin24.ledger"
        minSdk = 26  // needed for Keystore StrongBox and BiometricPrompt
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // No default network config; the app requests no INTERNET permission.

        // FLAG_SECURE (no screenshots or screen recording) is on unless a build
        // type turns it off. Setting it here means every build type gets the safe
        // value, and only `screenshot` changes it.
        buildConfigField("boolean", "ALLOW_SCREENSHOTS", "false")
    }

    signingConfigs {
        create("release") {
            if (canSignRelease) {
                storeFile = rootProject.file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        // For README screenshots only. FLAG_SECURE blocks screen capture, so it has
        // to be off to photograph the app, and it must never be off in the build
        // people install.
        //
        // The applicationIdSuffix makes this a separate package that installs next
        // to the real app, with its own database and Keystore entry. Screenshots
        // use made-up demo data, and the real ledger is never opened. Uninstall it
        // after use.
        create("screenshot") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".screenshot"
            versionNameSuffix = "-screenshot"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "true")
        }
        // For performance measurement only. A debuggable build runs much slower,
        // so timings from the debug build don't reflect the real app; profile this
        // one instead. It is signed with the debug key so it installs.
        //
        // Like `screenshot`, it is a separate package with its own empty database,
        // with the demo seed and ingest benchmark turned on. Never shipped.
        create("profile") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".profile"
            versionNameSuffix = "-profile"
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "true")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false  // release storage can't be read with adb run-as
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true  // ALLOW_SCREENSHOTS above needs BuildConfig
    }
    // The Compose compiler comes from the org.jetbrains.kotlin.plugin.compose
    // Gradle plugin, so no composeOptions block is needed.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Room exports each schema version as JSON under
// app/schemas/<variant>/<db>/<version>.json. Commit these files.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core-domain"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.0")   // FragmentActivity for BiometricPrompt
    // The Android 12 splash-screen API. Used to reduce the system splash to a
    // plain page, so the in-app splash can run the whole seal animation.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Dependency injection
    implementation("com.google.dagger:hilt-android:2.58")  // 2.58 is the last Hilt version that supports AGP 8.x
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-compiler:2.58")

    // Room and encrypted storage (SQLCipher, with the key protected by the Android Keystore)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    // The maintained SQLCipher library. It also packages correctly for the
    // 16 KB page size on Android 15+.
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Biometric app-lock
    implementation("androidx.biometric:biometric:1.1.0")

    // Kotlinx serialization for JSON export and backup
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

// Release builds are always signed: without signing details the build
// fails instead of producing an unsigned APK. This is checked when the task
// runs, not at configuration time, so debug builds and tests work without
// any signing setup.
tasks.matching { it.name == "packageRelease" }.configureEach {
    // Copy into a local value at configuration time. Using the script property
    // inside doFirst would capture the whole script object, which the
    // configuration cache can't store.
    val signingConfigured = canSignRelease
    doFirst {
        if (!signingConfigured) throw GradleException(
            """
            Release signing is not configured.

            Create keystore.properties in the project root (gitignored):

                storeFile=ledger-release.jks
                storePassword=<store password>
                keyAlias=ledger
                keyPassword=<key password>

            Generate the keystore with:

                keytool -genkeypair -v -keystore ledger-release.jks \
                    -alias ledger -keyalg RSA -keysize 4096 -validity 10000

            CI can supply LEDGER_KEYSTORE, LEDGER_KEYSTORE_PASSWORD,
            LEDGER_KEY_ALIAS and LEDGER_KEY_PASSWORD instead of the file.
            """.trimIndent()
        )
    }
}
