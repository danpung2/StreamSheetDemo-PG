package com.example.pgdemo.main

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 * PG Main Service Application
 * 결제/환불 API 서비스 및 배치 처리를 담당합니다.
 * Handles payment/refund API services and batch processing.
 */
@SpringBootApplication(scanBasePackages = ["com.example.pgdemo"])
@EnableJpaRepositories(basePackages = ["com.example.pgdemo.common.domain.repository"])
@EnableMongoRepositories(basePackages = ["com.example.pgdemo.common.domain.repository"])
@EntityScan(basePackages = ["com.example.pgdemo.common.domain.entity", "com.example.pgdemo.common.domain.document"])
class PgMainApplication

fun main(args: Array<String>) {
    runApplication<PgMainApplication>(*args)
}
