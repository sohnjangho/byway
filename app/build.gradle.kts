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

// 공유 서버 주소는 시연 빌드에만 -Pbyway.serverUrl=http://<주소>:8787 로 넣는다. 저장소와 제출 APK 에는 주소가 없다(공유 설계 6-1).
val serverUrl = (findProperty("byway.serverUrl") as String?).orEmpty().trim().trimEnd('/')
val serverHost = serverUrl.substringAfter("://", "").substringBefore('/').substringBefore(':')

// 서버 주소가 있으면 그 호스트 하나만 평문을 허용한다. 없으면 평문을 모두 막는다.
val networkConfig = tasks.register("writeNetworkConfig") {
    val out = layout.buildDirectory.file("generated/netconfig/res/xml/network_security_config.xml")
    inputs.property("host", serverHost)
    outputs.file(out)
    doLast {
        val domain = if (serverHost.isEmpty()) "" else
            "    <domain-config cleartextTrafficPermitted=\"true\">\n        <domain includeSubdomains=\"false\">$serverHost</domain>\n    </domain-config>\n"
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<network-security-config>\n" +
                "    <base-config cleartextTrafficPermitted=\"false\" />\n$domain</network-security-config>\n",
        )
    }
}
tasks.named("preBuild") { dependsOn(networkConfig) }

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
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/netconfig/res"))

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation(project(":core"))
    rootProject.subprojects.filter { it.path.startsWith(":feature:") }.forEach { implementation(it) }

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("ai.pleos.playground:SpeechToText:3.3.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
