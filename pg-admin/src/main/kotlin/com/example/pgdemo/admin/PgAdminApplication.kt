package com.example.pgdemo.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * PG Admin Application
 * 관리자 포털 서비스를 담당합니다. (Thymeleaf + JWT 인증)
 * Handles admin portal service. (Thymeleaf + JWT authentication)
 */
@SpringBootApplication(scanBasePackages = ["com.example.pgdemo"])
class PgAdminApplication

fun main(args: Array<String>) {
    runApplication<PgAdminApplication>(*args)
}
