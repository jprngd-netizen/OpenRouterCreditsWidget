plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---- Release signing ----
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = mutableMapOf<String, String>().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.readLines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) put(parts[0].trim(), parts[1].trim())
        }
    }
}

android {
    namespace = "com.gabrielsalem.openroutercredits"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gabrielsalem.openroutercredits"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                keystoreProps.getOrDefault("storeFile", "app/upload-keystore.jks")
            )
            storePassword = keystoreProps.getOrDefault("storePassword", "")
            keyAlias = keystoreProps.getOrDefault("keyAlias", "")
            keyPassword = keystoreProps.getOrDefault("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
