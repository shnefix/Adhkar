plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.azkar.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.azkar.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    val hasReleaseSigning = providers.gradleProperty("AZKAR_STORE_FILE").isPresent
    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("AZKAR_STORE_FILE").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("AZKAR_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("AZKAR_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("AZKAR_KEY_PASSWORD").orNull
            }
        }
        buildTypes {
            release {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
                // CI can verify the shrunk artifact without storing production credentials.
                signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
