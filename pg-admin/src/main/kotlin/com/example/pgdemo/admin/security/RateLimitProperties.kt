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
    enum class Bucket {
        LOGIN,
        DEMO,
        EXPORT
    }

    /**
     * Whether rate limiting is enabled.
     * Rate Limiting 활성화 여부.
     */
    var enabled: Boolean = true

    /**
     * Default values for login-related endpoints.
     */
    var maxAttempts: Int = 10
    var windowDuration: Duration = Duration.ofMinutes(1)
    var blockDuration: Duration = Duration.ofMinutes(5)

    /**
     * Limits for public demo issuance endpoint.
     */
    var demoMaxAttempts: Int = 5
    var demoWindowDuration: Duration = Duration.ofMinutes(1)
    var demoBlockDuration: Duration = Duration.ofMinutes(10)

    /**
     * Limits for export endpoints (resource-heavy).
     */
    var exportMaxAttempts: Int = 10
    var exportWindowDuration: Duration = Duration.ofMinutes(1)
    var exportBlockDuration: Duration = Duration.ofMinutes(2)

    fun getMaxAttempts(bucket: Bucket): Int {
        return when (bucket) {
            Bucket.LOGIN -> maxAttempts
            Bucket.DEMO -> demoMaxAttempts
            Bucket.EXPORT -> exportMaxAttempts
        }
    }

    fun getWindowDuration(bucket: Bucket): Duration {
        return when (bucket) {
            Bucket.LOGIN -> windowDuration
            Bucket.DEMO -> demoWindowDuration
            Bucket.EXPORT -> exportWindowDuration
        }
    }

    fun getBlockDuration(bucket: Bucket): Duration {
        return when (bucket) {
            Bucket.LOGIN -> blockDuration
            Bucket.DEMO -> demoBlockDuration
            Bucket.EXPORT -> exportBlockDuration
        }
    }
}
