plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.zubaer.maxvideoplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zubaer.maxvideoplayer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-step1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public OAuth client identifiers/redirect URIs are build-time configuration, never
        // secrets. Empty values keep the provider visibly NOT_CONFIGURED instead of shipping fake
        // credentials. Configure them in gradle.properties or CI for production distribution.
        buildConfigField(
            "String",
            "CLOUD_GOOGLE_CLIENT_ID",
            providers.gradleProperty("MAX_GOOGLE_DRIVE_CLIENT_ID").orElse("").get().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLOUD_GOOGLE_REDIRECT_URI",
            providers.gradleProperty("MAX_GOOGLE_DRIVE_REDIRECT_URI").orElse("maxvideoplayer://oauth/google").get().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLOUD_MICROSOFT_CLIENT_ID",
            providers.gradleProperty("MAX_ONEDRIVE_CLIENT_ID").orElse("").get().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLOUD_MICROSOFT_REDIRECT_URI",
            providers.gradleProperty("MAX_ONEDRIVE_REDIRECT_URI").orElse("maxvideoplayer://oauth/microsoft").get().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLOUD_DROPBOX_CLIENT_ID",
            providers.gradleProperty("MAX_DROPBOX_CLIENT_ID").orElse("").get().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLOUD_DROPBOX_REDIRECT_URI",
            providers.gradleProperty("MAX_DROPBOX_REDIRECT_URI").orElse("maxvideoplayer://oauth/dropbox").get().asBuildConfigString(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.okhttp)
    implementation(libs.smbj)
    // Step 10 security pin: SMBJ 0.14.0 requests bcprov-jdk18on 1.79. Pin the same
    // artifact to the vendor-fixed 1.84 line; Gradle resolves the runtime graph to 1.84.
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.commons.net)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
