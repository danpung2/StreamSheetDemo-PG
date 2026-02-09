plugins {
    id("org.springframework.boot")
}

// 어드민 모듈 - 관리자 포털 (Thymeleaf + JWT 인증)
// Admin module - Admin portal (Thymeleaf + JWT authentication)

dependencies {
    // 공통 모듈 의존성
    // Common module dependency
    implementation(project(":pg-common"))
    
    // Spring Boot Web
    // REST API 및 MVC / REST API and MVC
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Thymeleaf
    // 서버 사이드 템플릿 엔진 / Server-side template engine
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    
    // Spring Security
    // 인증/인가 / Authentication/Authorization
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Spring AOP
    // 테넌트 권한 검증 Aspect용 / For tenant access authorization aspect
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // StreamSheet
    // Excel export with StreamSheet
    implementation("io.github.danpung2:streamsheet-spring-boot-starter:1.0.0")
    implementation("io.github.danpung2:streamsheet-mongodb:1.0.0")
    
    // JWT (JJWT)
    // JWT 토큰 처리 / JWT token handling
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // Actuator (상태 모니터링)
    // Health monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Jackson Kotlin Module
    // JSON 직렬화 / JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // 개발 도구
    // Development tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    
    // Security 테스트
    // Security testing
    testImplementation("org.springframework.security:spring-security-test")

    // Testcontainers (integration tests)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:mongodb")
}

// 실행 가능한 JAR 빌드
// Build executable JAR
tasks.bootJar {
    archiveFileName.set("pg-admin.jar")
}
