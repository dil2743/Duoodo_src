plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

// Permissions the merged manifest may contain; anything else fails the build.
val allowedPermissions = setOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    // Added by AndroidX for internal broadcasts.
    "app.duoodo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

// Release signing from the environment; unsigned otherwise.
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE")

android {
    namespace = "app.duoodo"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.duoodo"
        minSdk = 26
        targetSdk = 35
        // versionCode = major * 10000 + minor * 100 + patch; release tags must match versionName.
        versionCode = 300
        versionName = "0.3.0"
    }

    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.all {
            // Live relay test is opt-in (DUOODO_LIVE=1); PeerTool is driven by DUOODO_PEER.
            listOf("DUOODO_LIVE", "DUOODO_PEER", "DUOODO_CODE", "DUOODO_WAIT", "DUOODO_TEXT", "DUOODO_RELAYS").forEach { k -> it.environment(k, System.getenv(k) ?: "") }
            it.testLogging.showStandardStreams = true
            it.outputs.upToDateWhen { false }
            // Screenshot tests compare pixels, which drift with fonts/JDK on other machines; they
            // only run via recordPaparazziDebug / verifyPaparazziDebug, never in the normal test gate.
            if (gradle.startParameter.taskNames.none { t -> t.contains("Paparazzi") }) {
                it.exclude("**/ScreenshotTest*")
            }
            listOf("https.proxyHost", "https.proxyPort").forEach { k ->
                System.getProperty(k)?.let { v -> it.systemProperty(k, v) }
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // Play services pulls in an old Fragment; ActivityResult APIs need >= 1.3.
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.bouncycastle)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

listOf("Debug", "Release").forEach { variant ->
    val verify = tasks.register("verify${variant}Permissions") {
        group = "verification"
        description = "Checks the merged $variant manifest against allowedPermissions."
        val manifest = layout.buildDirectory.file(
            "intermediates/merged_manifests/${variant.lowercase()}/process${variant}Manifest/AndroidManifest.xml"
        )
        dependsOn("process${variant}Manifest")
        inputs.file(manifest)
        doLast {
            val requested = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*android:name="([^"]+)"""")
                .findAll(manifest.get().asFile.readText()).map { it.groupValues[1] }.toSet()
            val unexpected = requested - allowedPermissions
            check(unexpected.isEmpty()) {
                "Permissions not in allowedPermissions: $unexpected"
            }
        }
    }
    tasks.matching { it.name == "assemble$variant" }.configureEach { dependsOn(verify) }
}
