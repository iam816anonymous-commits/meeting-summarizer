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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
