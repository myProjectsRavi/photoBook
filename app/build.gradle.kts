import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
    id("androidx.room")
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
val releaseKeystorePath = releaseKeystoreProperties.getProperty("storeFile")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val bundledLabelModelDependency = "com.google.mlkit:image-labeling:17.0.9"

// Resolve the pinned model artifact only to extract its bundled model asset. The
// ML Kit runtime is intentionally not packaged; the standalone LiteRT runtime
// executes the app-local model without deferred model delivery or cloud calls.
val bundledLabelModel = configurations.create("bundledLabelModel") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val extractBundledLabelModel = tasks.register("extractBundledLabelModel") {
    val outputDirectory = layout.buildDirectory.dir("generated/assets/bundledLabelModel")
    inputs.property("modelArtifact", bundledLabelModelDependency)
    outputs.file(
        outputDirectory.map { directory ->
            directory.file("photobook/food_live_label_model.tflite")
        },
    )
    doLast {
        project.delete(outputDirectory)
        project.sync {
            from(project.zipTree(bundledLabelModel.resolve().single())) {
                include("assets/mlkit_label_default_model/mobile_ica_8bit_with_metadata_tflite")
                eachFile { path = "photobook/food_live_label_model.tflite" }
                includeEmptyDirs = false
            }
            into(outputDirectory)
        }
    }
}
tasks.matching { task ->
    (task.name.startsWith("merge") && task.name.endsWith("Assets")) ||
        task.name.contains("Lint", ignoreCase = true)
}.configureEach {
    dependsOn(extractBundledLabelModel)
}

android {
    namespace = "com.photobook.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.photobook.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "2.0.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Strip locales we don't translate; cuts AndroidX/MLKit translations significantly.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                val candidate = File(releaseKeystorePath).let { file ->
                    if (file.isAbsolute) file else rootProject.file(releaseKeystorePath)
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
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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

    sourceSets {
        getByName("main").assets.srcDir(
            layout.buildDirectory.dir("generated/assets/bundledLabelModel"),
        )
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs {
            // Ensure native libs are stored uncompressed and 16KB page-aligned
            // Required by Google Play for devices with 16KB memory pages
            useLegacyPackaging = false
            excludes += setOf("**/x86/**", "**/x86_64/**")
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
            // No universal APK. Preserve the production 30 MiB hard ceiling here. The user-facing
            // Google Play delivered download/install size is a separate release metric and must be
            // measured with bundletool / Play Console against the .aab.
            isUniversalApk = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.9")

    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-compiler:2.55")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")

    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("androidx.exifinterface:exifinterface:1.4.1")

    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.crypto.tink:tink-android:1.23.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

kapt {
    correctErrorTypes = true
}

tasks.register("verifyApkSize") {
    group = "verification"
    description = "Verifies release ABI-specific APKs stay below the production 30 MiB ceiling."
    dependsOn("assembleRelease")
    doLast {
        val maxBytes = 30L * 1024L * 1024L
        val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = apkDir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        if (apks.isEmpty()) {
            throw GradleException("No release APKs found in ${apkDir.absolutePath}")
        }
        apks.forEach { apk ->
            val size = apk.length()
            val mib = size.toDouble() / (1024.0 * 1024.0)
            println("releaseApk=${apk.name} bytes=$size mib=${"%.2f".format(mib)}")
            if (size >= maxBytes) {
                throw GradleException("Release APK ${apk.name} is ${"%.2f".format(mib)} MiB, exceeding the 30 MiB ceiling")
            }
        }
    }
}

tasks.register("verifyReleaseBundleSize") {
    group = "verification"
    description = "Verifies release AAB stays below the production 20 MiB ceiling."
    dependsOn("bundleRelease")
    doLast {
        val maxBytes = 20L * 1024L * 1024L
        val bundleDir = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
        val bundles = bundleDir.walkTopDown().filter { it.isFile && it.extension == "aab" }.toList()
        if (bundles.size != 1) {
            throw GradleException("Expected exactly one release AAB in ${bundleDir.absolutePath}; found ${bundles.size}")
        }
        val aab = bundles.single()
        val size = aab.length()
        val mib = size.toDouble() / (1024.0 * 1024.0)
        println("releaseAab=${aab.name} bytes=$size mib=${"%.2f".format(mib)}")
        if (size >= maxBytes) {
            throw GradleException("Release AAB is ${"%.2f".format(mib)} MiB, exceeding the 20 MiB ceiling")
        }
    }
}

tasks.register("printReleaseMetadata") {
    group = "verification"
    description = "Prints release metadata for CI evidence."
    doLast {
        println("versionCode=${android.defaultConfig.versionCode}")
        println("versionName=${android.defaultConfig.versionName}")
        println("targetSdk=${android.defaultConfig.targetSdk}")
    }
}

bundledLabelModel.dependencies.add(
    dependencies.create(bundledLabelModelDependency)
)
