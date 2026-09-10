plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "studio.room211.san"
    compileSdk = 34

    defaultConfig {
        applicationId = "studio.room211.san"
        // 26 je isti prag kao Diktat i Most. Camera2 postoji od 21, ali
        // MediaStore upis sa RELATIVE_PATH trazi 29; ispod toga se pise
        // direktno u DCIM, pa je 26 najniza granica koja jos ima smisla.
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    buildTypes {
        release {
            // Bez ovoga Material biblioteka nadme APK sa 0.9 na 6.5 MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Potpisuje se debug kljucem: aplikacija se ionako samo sideload-uje.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
}
