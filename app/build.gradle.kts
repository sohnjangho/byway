import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서명 키는 저장소 밖에 둔다. 파일이 없으면 release 빌드를 만들지 않는다.
val releaseKeyFile = File(System.getProperty("user.home"), ".byway/release.properties")

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name == "assembleRelease" } && !releaseKeyFile.exists()) {
        throw GradleException("서명 설정이 없습니다: $releaseKeyFile")
    }
}

android {
    namespace = "com.byway"
    compileSdk = 34
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.byway"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeyFile.exists()) {
            val key = Properties().apply { releaseKeyFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = File(key.getProperty("storeFile"))
                storePassword = key.getProperty("storePassword")
                keyAlias = key.getProperty("keyAlias")
                keyPassword = key.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(project(":core"))
    rootProject.subprojects.filter { it.path.startsWith(":feature:") }.forEach { implementation(it) }

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("ai.pleos.playground:SpeechToText:3.3.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
