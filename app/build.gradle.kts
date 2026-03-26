plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {  //이거 줄 왜 안없어짐>?
    namespace = "com.example.vocaapp"
    compileSdk = 36 //36 -> 34

    defaultConfig {
        applicationId = "com.example.vocaapp"
        minSdk = 26 //36 -> 26
        targetSdk = 36  //36 -> 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore:24.10.0")

    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.leinardi.android:speed-dial:3.3.0")

    // 카메라 의존성
    val camerax_version = "1.2.0"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")


    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation("com.google.firebase:firebase-ai")

    // JSON 파싱을 위한 GSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Guava (ListenableFuture 처리를 위해 필요)
    implementation("com.google.guava:guava:31.1-android")

    // FCM 라이브러리 추가
    implementation("com.google.firebase:firebase-messaging:23.4.1")

    implementation("com.onesignal:OneSignal:[5.0.0, 5.99.99]")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.google.firebase:firebase-functions:21.0.0")
}
