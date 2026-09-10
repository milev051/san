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
        versionCode = 7
        versionName = "0.7"
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

    // CameraX prosirenja: drugi put do proizvodjacevog nocnog rezima.
    // Platformski `CameraExtensionCharacteristics` na S23 ne prijavljuje nocni
    // rezim ni na jednoj kameri, a CameraX ume da razgovara i sa starijim
    // OEM bibliotekama koje platforma ne izlaze.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-extensions:1.3.4")
    testImplementation("junit:junit:4.13.2")
}
