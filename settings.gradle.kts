pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}

rootProject.name = "Byway"
include(":app", ":core", ":server")
// 시연용 Gleo 흉내 앱(Gleo 음성 연동 설계 9절). 제출 APK 와 무관하다.
include(":gleosim")

// feature/<이름> 폴더는 자동으로 모듈이 된다. 기능을 추가해도 이 파일을 고치지 않는다.
file("feature").listFiles()
    ?.filter { it.isDirectory && !it.name.startsWith("_") && it.resolve("build.gradle.kts").exists() }
    ?.forEach { include(":feature:${it.name}") }
