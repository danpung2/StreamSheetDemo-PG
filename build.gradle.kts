import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22" apply false
    kotlin("plugin.jpa") version "1.9.22" apply false
    id("org.springframework.boot") version "3.2.2" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

// 모든 프로젝트(루트 포함) 공통 설정
// Common settings for all projects (including root)
allprojects {
    group = "com.example"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenLocal()  // StreamSheet 로컬 Maven 저장소 우선 / Local Maven for StreamSheet
        mavenCentral()
    }
}

// 하위 프로젝트 공통 설정
// Common settings for subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")
    
    // Java 17 사용
    // Use Java 17
    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kotlin 컴파일 옵션
    // Kotlin compile options
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    // 테스트 설정
    // Test configuration
    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // 공통 의존성
    // Common dependencies
    dependencies {
        // Kotlin 리플렉션 / Kotlin reflection
        implementation(kotlin("reflect"))
        
        // 테스트 의존성 / Test dependencies
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}
