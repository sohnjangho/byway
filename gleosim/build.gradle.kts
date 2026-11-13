// 시연용 Gleo 흉내 앱(Gleo 음성 연동 설계 9절). 에뮬레이터에 디버그 빌드로만 설치한다. 제출 APK 가 아니다.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.byway.gleosim"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byway.gleosim"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation("junit:junit:4.13.2")
}
