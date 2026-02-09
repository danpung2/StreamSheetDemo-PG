package com.example.pgdemo.admin.security

import com.example.pgdemo.common.domain.entity.AdminUser
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))

    fun generateAccessToken(adminUser: AdminUser): String {
        return generateAccessToken(
            adminUser = adminUser,
            accessTokenExpiry = jwtProperties.accessTokenExpiry,
            additionalClaims = emptyMap()
        )
    }

    fun generateAccessToken(
        adminUser: AdminUser,
        accessTokenExpiry: Duration,
        additionalClaims: Map<String, Any?>
    ): String {
        val userId = adminUser.id ?: throw IllegalStateException("Admin user id is required")
        val now = Instant.now()
        val expiry = now.plus(accessTokenExpiry)

        val builder = Jwts.builder()
            .subject(userId.toString())
            .claim("tenantType", adminUser.tenantType.name)
            .claim("tenantId", adminUser.tenantId?.toString())
            .claim("role", adminUser.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))

        additionalClaims.forEach { (key, value) ->
            if (value != null) {
                builder.claim(key, value)
            }
        }

        return builder
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    fun generateRefreshToken(adminUser: AdminUser, tokenId: UUID): String {
        val userId = adminUser.id ?: throw IllegalStateException("Admin user id is required")
        val now = Instant.now()
        val expiry = now.plus(jwtProperties.refreshTokenExpiry)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("tokenId", tokenId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    fun validateAccessToken(token: String): Boolean = runCatching { parseClaims(token) }.isSuccess

    fun validateRefreshToken(token: String): Boolean = runCatching { parseClaims(token) }.isSuccess

    fun getUserId(token: String): UUID = UUID.fromString(parseClaims(token).subject)

    fun getTokenId(token: String): UUID = UUID.fromString(parseClaims(token).get("tokenId", String::class.java))

    fun isDemoToken(token: String): Boolean {
        val value = parseClaims(token).get("demo")
        return value == true
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
