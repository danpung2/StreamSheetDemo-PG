plugins {
    id("org.springframework.boot")
}

// 메인 서비스 모듈 - 결제/환불 API + 배치 처리
// Main service module - Payment/Refund API + Batch processing

dependencies {
    // 공통 모듈 의존성
    // Common module dependency
    implementation(project(":pg-common"))
    
    // Spring Boot Web
    // REST API 제공 / REST API support
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Spring Batch
    // 배치 처리 / Batch processing
    implementation("org.springframework.boot:spring-boot-starter-batch")
    
    // Quartz Scheduler
    // 스케줄러 / Scheduler
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    
    // Actuator (상태 모니터링)
    // Health monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Jackson Kotlin Module
    // JSON 직렬화 / JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // 개발 도구
    // Development tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

// 실행 가능한 JAR 빌드
// Build executable JAR
tasks.bootJar {
    archiveFileName.set("pg-main.jar")
}
