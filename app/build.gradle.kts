import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
}

val injectedSigningStoreFile = gradle.startParameter.projectProperties["android.injected.signing.store.file"]
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
if (injectedSigningStoreFile != null && !File(injectedSigningStoreFile).isAbsolute) {
    throw GradleException(
        "Relative signing path '$injectedSigningStoreFile' is not supported by AGP externalOverride. " +
            "Use an absolute keystore path (for example, '/Users/you/keys/photobook_keystore.jks').",
    )
}

val releaseKeystoreProperties = Properties().apply {
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.photobook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.photobook.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Strip locales we don't translate; cuts AndroidX/MLKit translations significantly.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            val keystorePath = releaseKeystoreProperties.getProperty("storeFile")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }


            if (keystorePath != null) {
                val candidate = File(keystorePath).let { file ->
                    if (file.isAbsolute) file else rootProject.file(keystorePath)
                }
                if (!candidate.exists()) {
                    throw GradleException("Release keystore not found at: ${candidate.absolutePath}")
                }

                storeFile = candidate
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                    ?: throw GradleException("storePassword missing in keystore.properties")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                    ?: throw GradleException("keyAlias missing in keystore.properties")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
                    ?: throw GradleException("keyPassword missing in keystore.properties")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs {
            // Ensure native libs are stored uncompressed and 16KB page-aligned
            // Required by Google Play for devices with 16KB memory pages
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // No universal APK — keeps per-ABI APKs lean (arm64-v8a ~48MB instead of ~140MB).
            // Use Play Store AAB for automatic per-device delivery.
            isUniversalApk = false
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.paging:paging-runtime-ktx:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")
    implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    baselineProfile(project(":baselineprofile"))
}

tasks.register("verifyApkSize") {
    group = "verification"
    description = "Fails when any generated APK exceeds 60 MB."

    doLast {
        val maxBytes = 60L * 1024L * 1024L
        val apkRoot = layout.buildDirectory.dir("outputs/apk").get().asFile
        if (!apkRoot.exists()) return@doLast

        val apks = apkRoot.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .toList()
        if (apks.isEmpty()) return@doLast

        apks.forEach { apk ->
            val sizeBytes = apk.length()
            check(sizeBytes <= maxBytes) {
                "APK size gate failed for ${apk.path}: ${sizeBytes / (1024 * 1024)} MB > 60 MB"
            }
        }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("verifyApkSize")
}
