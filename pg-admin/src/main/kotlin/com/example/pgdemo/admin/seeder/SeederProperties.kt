package com.example.pgdemo.admin.seeder

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Seeder configuration properties for demo account passwords.
 * 데모 계정 비밀번호를 위한 Seeder 설정 프로퍼티.
 *
 * Passwords should be injected via environment variables or Docker secrets.
 * 비밀번호는 환경 변수 또는 Docker secrets를 통해 주입해야 합니다.
 */
@ConfigurationProperties(prefix = "pgdemo.seeder.passwords")
class SeederProperties {
    /**
     * Operator admin password.
     * 운영사 관리자 비밀번호.
     */
    lateinit var operator: String

    /**
     * Headquarters admin password.
     * 본사 관리자 비밀번호.
     */
    lateinit var hqAdmin: String

    /**
     * Headquarters manager password (for public demo).
     * 본사 매니저 비밀번호 (공개 데모용).
     */
    lateinit var hqManager: String

    /**
     * Merchant admin password.
     * 업체 관리자 비밀번호.
     */
    lateinit var merchant: String
}
