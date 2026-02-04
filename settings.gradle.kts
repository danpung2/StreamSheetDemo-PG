pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "StreamSheetDemo"

// 멀티모듈 정의
// Multi-module definition
include(
    "pg-common",
    "pg-main",
    "pg-admin"
)
