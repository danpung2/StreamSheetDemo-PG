package com.example.pgdemo.admin.security

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security.demo")
class DemoProperties {
    /**
     * Enable passwordless demo session issuance.
     * 공개 데모 세션(비밀번호 없이) 발급 기능 활성화 여부.
     */
    var enabled: Boolean = false

    /**
     * Demo user email to issue tokens for.
     * 데모 토큰을 발급할 대상 사용자 이메일.
     */
    var userEmail: String = "hq_manager@pgdemo.com"

    /**
     * Demo access token TTL.
     * 데모 access token 만료 시간.
     */
    var accessTokenExpiry: Duration = Duration.ofMinutes(15)

    /**
     * Cookie SameSite attribute.
     */
    var cookieSameSite: String = "Lax"

    /**
     * Cookie Secure flag override.
     * null이면 요청(HTTPS 여부) 기반으로 자동 결정.
     */
    var cookieSecure: Boolean? = null
}
