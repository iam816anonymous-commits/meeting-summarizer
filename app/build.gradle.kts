plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.iam816anonymous.meetingsummarizer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iam816anonymous.meetingsummarizer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf("sherpa-onnx/native/**", "META-INF/DEPENDENCIES")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.8") {
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation(libs.junit)
    // Use a real JSON implementation in JVM unit tests instead of Android's mocked android.jar stubs.
    testImplementation("org.json:json:20240303")
}
