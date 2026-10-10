plugins {
    id("com.android.application")
}

val yt2mp3VersionCode = providers.gradleProperty("yt2mp3VersionCode").get().toInt()
val yt2mp3VersionName = providers.gradleProperty("yt2mp3VersionName").get()
val youtubedlAndroidVersion = "0.18.1"

android {
    namespace = "com.andre.yt2mp3"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.andre.yt2mp3"
        minSdk = 24
        targetSdk = 36
        versionCode = yt2mp3VersionCode
        versionName = yt2mp3VersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    splits {
        abi {
            isEnable = true
            reset()
            include(providers.gradleProperty("yt2mp3Abi").getOrElse("arm64-v8a"))
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    applicationVariants.all {
        val variantName = buildType.name
        outputs.all {
            val abi = filters.find {
                it.filterType == com.android.build.OutputFile.ABI
            }?.identifier ?: "universal"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "yt2mp3-v${yt2mp3VersionName}-build-${yt2mp3VersionCode}-${abi}-${variantName}.apk"
        }
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroidVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroidVersion")
    // Match the downloader's ZIP implementation to preserve Unix runtime symlinks.
    implementation("org.apache.commons:commons-compress:1.12")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
