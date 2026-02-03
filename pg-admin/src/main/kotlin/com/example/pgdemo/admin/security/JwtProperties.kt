package com.example.pgdemo.admin.security

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security.jwt")
class JwtProperties {
    lateinit var secret: String
    lateinit var accessTokenExpiry: Duration
    lateinit var refreshTokenExpiry: Duration
}
