package com.example.pgdemo.admin.security

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Rate limiting configuration properties.
 * Rate Limiting 설정 프로퍼티.
 *
 * Configures request rate limits for login API to prevent abuse.
 * 악용 방지를 위한 로그인 API 요청 제한을 설정합니다.
 */
@ConfigurationProperties(prefix = "security.rate-limit")
class RateLimitProperties {
    /**
     * Whether rate limiting is enabled.
     * Rate Limiting 활성화 여부.
     */
    var enabled: Boolean = true

    /**
     * Maximum number of login attempts allowed within the time window.
     * 시간 윈도우 내 허용되는 최대 로그인 시도 횟수.
     */
    var maxAttempts: Int = 10

    /**
     * Time window duration for rate limiting.
     * Rate Limiting 시간 윈도우 기간.
     */
    var windowDuration: Duration = Duration.ofMinutes(1)

    /**
     * Duration to block requests after rate limit is exceeded.
     * Rate Limit 초과 후 요청을 차단하는 기간.
     */
    var blockDuration: Duration = Duration.ofMinutes(5)
}
