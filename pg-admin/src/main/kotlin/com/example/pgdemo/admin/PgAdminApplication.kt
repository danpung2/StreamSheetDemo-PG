package com.example.pgdemo.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 * PG Admin Application
 * 관리자 포털 서비스를 담당합니다. (Thymeleaf + JWT 인증)
 * Handles admin portal service. (Thymeleaf + JWT authentication)
 */
@SpringBootApplication(scanBasePackages = ["com.example.pgdemo"])
@EnableJpaRepositories(basePackages = ["com.example.pgdemo.common.domain.repository"])
@EnableMongoRepositories(basePackages = ["com.example.pgdemo.common.domain.repository"])
@EntityScan(basePackages = ["com.example.pgdemo.common.domain.entity", "com.example.pgdemo.common.domain.document"])
class PgAdminApplication

fun main(args: Array<String>) {
    runApplication<PgAdminApplication>(*args)
}
