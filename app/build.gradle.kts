plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.room")
    id("androidx.baselineprofile")
}

import java.util.Properties

val releaseKeystoreProperties = Properties()
val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
if (releaseKeystorePropertiesFile.exists()) {
    releaseKeystorePropertiesFile.inputStream().use(releaseKeystoreProperties::load)
}
val hasReleaseSigning = releaseKeystoreProperties.getProperty("storeFile")?.trim()?.isNotEmpty() == true

val bundledLabelModel by configurations.creating
val bundledLabelModelDependency = "com.google.mlkit:image-labeling:17.0.9"

android {
    namespace = "com.photobook.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.photobook.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "2.0.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePropertiesFile.exists()) {
                val storeFilePath = releaseKeystoreProperties.getProperty("storeFile")
                if (!storeFilePath.isNullOrBlank()) {
                    storeFile = rootProject.file(storeFilePath)
                }
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
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
        buildConfig = true
    }

    sourceSets {
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/model-assets"))
        getByName("benchmark").manifest.srcFile("src/benchmark/AndroidManifest.xml")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs {
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
            isUniversalApk = false
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
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
    implementation("androidx.exifinterface:exifinterface:1.4.2")
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

    add("bundledLabelModel", bundledLabelModelDependency)
    implementation("com.google.ai.edge.litert:litert:1.4.1")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.1.0-rc2")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    add("benchmarkImplementation", "androidx.profileinstaller:profileinstaller:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    baselineProfile(project(":baselineprofile"))
}

tasks.register("verifyApkSize") {
    group = "verification"
    description = "Fails when any generated release APK exceeds 30 MB."

    doLast {
        val maxBytes = 30L * 1024L * 1024L
        val apkRoot = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        check(apkRoot.exists()) {
            "Release APK size gate could not find ${apkRoot.path}; run assembleRelease first."
        }

        val apks = apkRoot.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .toList()
        check(apks.isNotEmpty()) {
            "Release APK size gate found no release APKs under ${apkRoot.path}."
        }

        apks.forEach { apk ->
            val sizeBytes = apk.length()
            check(sizeBytes <= maxBytes) {
                "APK size gate failed for ${apk.path}: ${sizeBytes / (1024 * 1024)} MB > 30 MB"
            }
        }
    }
}

tasks.register("verifyReleaseBundleSize") {
    group = "verification"
    description = "Fails when any generated release AAB exceeds 20 MB."

    doLast {
        val maxBytes = 20L * 1024L * 1024L
        val bundleRoot = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
        check(bundleRoot.exists()) {
            "Release AAB size gate could not find ${bundleRoot.path}; run bundleRelease first."
        }

        val bundles = bundleRoot.walkTopDown()
            .filter { it.isFile && it.extension == "aab" }
            .toList()
        check(bundles.isNotEmpty()) {
            "Release AAB size gate found no release bundles under ${bundleRoot.path}."
        }

        bundles.forEach { bundle ->
            val sizeBytes = bundle.length()
            check(sizeBytes <= maxBytes) {
                "Release bundle size gate failed for ${bundle.path}: ${sizeBytes / (1024 * 1024)} MB > 20 MB"
            }
        }
    }
}

tasks.register("printReleaseMetadata") {
    group = "verification"
    description = "Prints release version metadata for verification."
    doLast {
        println("versionCode=${android.defaultConfig.versionCode}")
        println("versionName=${android.defaultConfig.versionName}")
        println("minSdk=${android.defaultConfig.minSdk}")
        println("targetSdk=${android.defaultConfig.targetSdk}")
    }
}

val extractBundledLabelModel by tasks.registering(Copy::class) {
    from(bundledLabelModel.map { files -> files.map(::zipTree) }) {
        include("**/labeler_assets/image_classifier.tflite")
        eachFile {
            path = "photobook/food_live_label_model.tflite"
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/model-assets"))
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("Assets")
}.configureEach {
    dependsOn(extractBundledLabelModel)
}
