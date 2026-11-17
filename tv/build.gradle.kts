plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "moe.rukamori.archivetune.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jusdots.jusplayer.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "15.0.0-tv"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEVICE", "\"tv\"")
        buildConfigField("String", "DISTRIBUTION", "\"foss\"")
        buildConfigField("boolean", "UPDATER_AVAILABLE", "true")
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"1165706613961789445\"")
        buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "1165706613961789445L")
        buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"discord-1165706613961789445\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../app/src/main/kotlin", "../app/src/main/java", "src/main/kotlin")
            res.srcDirs("../app/src/main/res", "src/main/res")
            assets.srcDirs("../app/src/main/assets", "src/main/assets")
            jniLibs.srcDirs("../app/src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // TV Compose & Leanback
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.leanback)

    // Base AndroidX & Compose dependencies
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)
    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.work.runtime)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.shimmer)

    // Image loading
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // Media3 Playback
    implementation(libs.media3)
    implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-ui-compose:${libs.versions.media3.get()}")

    // Database & DI
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    // Subprojects
    if (rootProject.file("core/build.gradle.kts").isFile) {
        implementation(project(":core"))
    }
    if (rootProject.file("lyrics/kugou/build.gradle.kts").isFile) {
        implementation(project(":lyrics:kugou"))
    }
    if (rootProject.file("lyrics/lrclib/build.gradle.kts").isFile) {
        implementation(project(":lyrics:lrclib"))
    }
    if (rootProject.file("lyrics/simpmusic/build.gradle.kts").isFile) {
        implementation(project(":lyrics:simpmusic"))
    }
    if (rootProject.file("lyrics/paxsenix/build.gradle.kts").isFile) {
        implementation(project(":lyrics:paxsenix"))
    }
    if (rootProject.file("lyrics/betterlyrics/build.gradle.kts").isFile) {
        implementation(project(":lyrics:betterlyrics"))
    }
    if (rootProject.file("lyrics/unison/build.gradle.kts").isFile) {
        implementation(project(":lyrics:unison"))
    }
    if (rootProject.file("lyrics/youlyplus/build.gradle.kts").isFile) {
        implementation(project(":lyrics:youlyplus"))
    }
    if (rootProject.file("canvas/build.gradle.kts").isFile) {
        implementation(project(":canvas"))
    }
    if (rootProject.file("shazamkit/build.gradle.kts").isFile) {
        implementation(project(":shazamkit"))
    }
    if (rootProject.file("spotifycore/build.gradle.kts").isFile) {
        implementation(project(":spotifycore"))
    }
    if (rootProject.file("lastfm/build.gradle.kts").isFile) {
        implementation(project(":lastfm"))
    }
}
