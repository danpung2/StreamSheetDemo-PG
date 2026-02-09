package com.example.pgdemo.admin.security

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Login security configuration properties.
 * 로그인 보안 설정 프로퍼티.
 *
 * Configures brute-force protection parameters.
 * 브루트포스 방어 파라미터를 설정합니다.
 */
@ConfigurationProperties(prefix = "security.login")
class LoginProperties {
    /**
     * Maximum number of failed login attempts before account lockout.
     * 계정 잠금 전 최대 로그인 실패 횟수.
     */
    var maxFailedAttempts: Int = 5

    /**
     * Duration for which the account remains locked after exceeding max failed attempts.
     * 최대 실패 횟수 초과 후 계정이 잠기는 기간.
     */
    var lockDuration: Duration = Duration.ofMinutes(30)
}
