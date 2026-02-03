package com.example.pgdemo.main

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * PG Main Service Application
 * 결제/환불 API 서비스 및 배치 처리를 담당합니다.
 * Handles payment/refund API services and batch processing.
 */
@SpringBootApplication(scanBasePackages = ["com.example.pgdemo"])
class PgMainApplication

fun main(args: Array<String>) {
    runApplication<PgMainApplication>(*args)
}
