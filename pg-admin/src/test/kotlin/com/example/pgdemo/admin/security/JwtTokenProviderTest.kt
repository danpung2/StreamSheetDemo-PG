package com.example.pgdemo.admin.security

import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.enum.UserRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("JWT 토큰 제공자 테스트")
class JwtTokenProviderTest {

    private fun jwtProperties(): JwtProperties {
        return JwtProperties().apply {
            secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            accessTokenExpiry = Duration.ofMinutes(15)
            refreshTokenExpiry = Duration.ofDays(7)
        }
    }

    private fun adminUser(): AdminUser {
        return AdminUser().apply {
            id = UUID.randomUUID()
            email = "admin@example.com"
            passwordHash = "hashed"
            name = "Admin"
            tenantType = TenantType.OPERATOR
            tenantId = UUID.randomUUID()
            role = UserRole.ADMIN
        }
    }

    @Test
    @DisplayName("access token 생성 후 검증 및 클레임 추출")
    fun `access token 생성 후 검증 및 클레임 추출`() {
        val properties = jwtProperties()
        val provider = JwtTokenProvider(properties)
        val user = adminUser()

        val token = provider.generateAccessToken(user)

        assertTrue(provider.validateAccessToken(token))

        val secretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))
        val claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

        assertEquals(user.id.toString(), claims.subject)
        assertEquals(user.tenantType.name, claims.get("tenantType", String::class.java))
        assertEquals(user.tenantId.toString(), claims.get("tenantId", String::class.java))
        assertEquals(user.role.name, claims.get("role", String::class.java))
        assertTrue(claims.expiration.after(claims.issuedAt))
    }

    @Test
    @DisplayName("refresh token 생성 후 검증 및 tokenId 추출")
    fun `refresh token 생성 후 검증 및 tokenId 추출`() {
        val properties = jwtProperties()
        val provider = JwtTokenProvider(properties)
        val user = adminUser()
        val tokenId = UUID.randomUUID()

        val token = provider.generateRefreshToken(user, tokenId)

        assertTrue(provider.validateRefreshToken(token))
        assertEquals(user.id, provider.getUserId(token))
        assertEquals(tokenId, provider.getTokenId(token))

        val secretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))
        val claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
        assertEquals(user.id.toString(), claims.subject)
        assertEquals(tokenId.toString(), claims.get("tokenId", String::class.java))
        assertTrue(claims.expiration.after(claims.issuedAt))
    }
}
