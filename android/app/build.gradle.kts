plugins {
    id("com.android.application")
    // Flutter's Gradle plugin must be applied after the Android plugin.
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.lifesaver.cardiac"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.lifesaver.cardiac"
        // The safety baseline targets Wear OS 3 or newer.
        minSdk = 30
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // No release signing configuration is committed. Public release
            // remains blocked until privacy, signing, and beta gates are complete.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // 1.17.0 is the API 36 line and matches Flutter 3.44.8 / AGP 9.0.1.
    implementation("androidx.core:core-ktx:1.17.0")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
}

flutter {
    source = "../.."
}
