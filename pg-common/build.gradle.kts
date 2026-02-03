plugins {
    kotlin("plugin.jpa")
}

// 공통 모듈 - 엔티티, Repository, DTO 등 공유 코드
// Common module - shared entities, repositories, DTOs

dependencies {
    // Spring Boot 의존성 관리
    // Spring Boot dependency management
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    
    // Spring Data JPA
    // 데이터 접근 계층 / Data access layer
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // Spring Data MongoDB
    // MongoDB 데이터 접근 / MongoDB data access
    api("org.springframework.boot:spring-boot-starter-data-mongodb")
    
    // Validation
    // 유효성 검증 / Validation
    api("org.springframework.boot:spring-boot-starter-validation")
    
    // PostgreSQL Driver
    // PostgreSQL 드라이버
    runtimeOnly("org.postgresql:postgresql")
    
    // StreamSheet (JPA, MongoDB 지원)
    // StreamSheet for Excel export
    api("io.github.danpung2:streamsheet-spring-boot-starter:0.0.1-SNAPSHOT")
    api("io.github.danpung2:streamsheet-jpa:0.0.1-SNAPSHOT")
    api("io.github.danpung2:streamsheet-mongodb:0.0.1-SNAPSHOT")
}
