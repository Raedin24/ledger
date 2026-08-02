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

// Release signing credentials come from a gitignored keystore.properties, or from
// the environment for CI. Neither the keystore nor its passwords enter the
// repository. The signing key is permanent: Android refuses to update an app
// whose certificate changed.
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
        // Diverges from `namespace`, which stays com.ledger.app: namespace only
        // decides where R and BuildConfig are generated.
        applicationId = "io.github.raedin24.ledger"
        minSdk = 26          // Keystore StrongBox / BiometricPrompt baseline
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // No default network config; the app requests no INTERNET permission.

        // FLAG_SECURE is on unless a build explicitly opts out. Declared here so
        // every build type inherits the safe value and only `screenshot` differs
        // — a new build type cannot forget to set it and silently ship a window
        // that screen-records.
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
        // Store/README screenshots only. FLAG_SECURE blocks screen capture, so it
        // has to come off to photograph the app at all — and the one thing we must
        // never do is take that flag off the build people install.
        //
        // The applicationIdSuffix is the point of this variant, not a detail: it
        // installs *alongside* the real app as a separate package, which means its
        // own database and its own Keystore entry. Screenshots therefore get seeded
        // demo data, and the real ledger is never opened, never cleared and never
        // photographed. The variant is meant to be uninstalled after use.
        create("screenshot") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".screenshot"
            versionNameSuffix = "-screenshot"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "true")
        }
        // Measurement-only variant. `debuggable` costs ART most of its optimisation,
        // so frame timings from the debug build describe the debug build and nothing
        // else — this is the one to profile on. Signed with the auto-generated debug
        // key purely so it installs; the real release stays deliberately unsigned.
        create("profile") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false          // adb run-as cannot read storage on release
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true       // ALLOW_SCREENSHOTS above needs BuildConfig generated
    }
    // Compose compiler now comes from the org.jetbrains.kotlin.plugin.compose
    // Gradle plugin (Kotlin 2.x); no composeOptions{} block needed.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Room schema export (unblocked now the project path has no spaces). Emitted
// JSON under app/schemas/<variant>/<db>/<version>.json — check these in.
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
    // Backports the Android 12 splash-screen API, and (the reason it's here) lets
    // the system splash be suppressed to a bare page so the in-app splash owns the
    // seal animation end to end.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Dependency injection
    implementation("com.google.dagger:hilt-android:2.58")   // 2.58 = last Hilt supporting AGP 8.x
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-compiler:2.58")

    // Room + encrypted storage (SQLCipher passphrase wrapped by Android Keystore)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    // Modern, maintained SQLCipher AAR (replaces retired android-database-sqlcipher;
    // fixes 16 KB page-size packaging for Android 15+). Same net.sqlcipher.* API.
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Biometric app-lock
    implementation("androidx.biometric:biometric:1.1.0")

    // Kotlinx serialization for JSON export/backup
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

// Releases are always signed: without credentials the build fails rather than
// producing an unsigned APK. Checked on the task rather than at configuration
// time, so debug builds and tests still run with no signing material present.
tasks.matching { it.name == "packageRelease" }.configureEach {
    // Read into a local at configuration time. Referencing the script property
    // straight from doFirst captures the script object itself, which the
    // configuration cache cannot serialise.
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
