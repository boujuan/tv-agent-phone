plugins {
    alias(libs.plugins.android.application)        // compiles the Kotlin too (AGP 9)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The release key, where it's configured: Gradle properties (~/.gradle/gradle.properties on a computer that has the
// key) or environment variables (a release workflow) of these names; TV_AGENT_PHONE_* first, else tv-agent's own
// TV_AGENT_* (one project key for both apps). See RELEASING.md. Never in this repository.
fun releaseKey(name: String): String? =
    listOf("TV_AGENT_PHONE_$name", "TV_AGENT_$name").firstNotNullOfOrNull { n ->
        (findProperty(n) as String?) ?: System.getenv(n)
    }

android {
    namespace = "com.boujuan.tvagent.phone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.boujuan.tvagent.phone"
        minSdk = 29
        targetSdk = 37
        // Released as v<versionName> tags (see RELEASING.md); bump versionCode with every release.
        versionCode = 6
        versionName = "0.2.4"
    }

    signingConfigs {
        if (releaseKey("KEYSTORE") != null) {
            create("release") {
                storeFile = file(releaseKey("KEYSTORE")!!)
                storePassword = releaseKey("KEYSTORE_PASSWORD")
                keyAlias = releaseKey("KEY_ALIAS")
                // keytool's PKCS12 keystores have one password for the store and the key.
                keyPassword = releaseKey("KEY_PASSWORD") ?: releaseKey("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The release key if configured, else the debug key (CI on branches, a computer without the key): that APK
            // installs, but can't update an install signed with the release key, nor the other way round.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // LiveAgentTest runs against a real agent only when given its pairing link (-PtvAgentPairLink=…).
        unitTests.all {
            it.systemProperty("tvAgentPairLink", findProperty("tvAgentPairLink") ?: "")
            it.systemProperty("tvAgentVoice", findProperty("tvAgentVoice") ?: "")
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.coroutines.test)
}
