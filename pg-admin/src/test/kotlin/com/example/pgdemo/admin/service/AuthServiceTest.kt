package com.example.pgdemo.admin.service

import com.example.pgdemo.admin.security.JwtProperties
import com.example.pgdemo.admin.security.JwtTokenProvider
import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.RefreshToken
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import com.example.pgdemo.common.domain.repository.RefreshTokenRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mock
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("인증 서비스 테스트")
class AuthServiceTest {

    @Mock
    private lateinit var adminUserRepository: AdminUserRepository

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var jwtProperties: JwtProperties
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        jwtProperties = JwtProperties().apply {
            secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            accessTokenExpiry = Duration.ofMinutes(15)
            refreshTokenExpiry = Duration.ofDays(7)
        }
        jwtTokenProvider = JwtTokenProvider(jwtProperties)
        authService = AuthService(
            adminUserRepository,
            refreshTokenRepository,
            jwtTokenProvider,
            jwtProperties,
            passwordEncoder
        )
    }

    @Test
    @DisplayName("refresh 토큰 해시 검증 후 회전")
    fun `refresh 토큰 해시 검증 후 회전`() {
        val adminUser = adminUser()
        val originalTokenId = UUID.randomUUID()
        val refreshToken = jwtTokenProvider.generateRefreshToken(adminUser, originalTokenId)
        val refreshTokenHash = hashToken(refreshToken)
        val existingToken = RefreshToken().apply {
            this.adminUser = adminUser
            this.tokenHash = refreshTokenHash
            this.expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiry)
        }

        `when`(refreshTokenRepository.findValidToken(eqValue(refreshTokenHash), anyInstant()))
            .thenReturn(existingToken)

        val response = authService.refresh(refreshToken)

        assertNotNull(response.accessToken)
        assertNotNull(response.refreshToken)

        verify(refreshTokenRepository).revokeByTokenHash(eqValue(refreshTokenHash), anyInstant())

        val savedTokenCaptor = org.mockito.ArgumentCaptor.forClass(RefreshToken::class.java)
        verify(refreshTokenRepository).save(savedTokenCaptor.capture())
        val savedToken = savedTokenCaptor.value
        assertEquals(adminUser, savedToken.adminUser)
        assertEquals(hashToken(response.refreshToken), savedToken.tokenHash)
        assertTrue(savedToken.expiresAt.isAfter(Instant.now().minusSeconds(1)))
    }

    @Test
    @DisplayName("logout 시 모든 refresh 토큰 폐기")
    fun `logout 시 모든 refresh 토큰 폐기`() {
        val adminUserId = UUID.randomUUID()

        authService.logout(adminUserId)

        verify(refreshTokenRepository).revokeAllByUserId(eqValue(adminUserId), anyInstant())
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

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun anyInstant(): Instant {
        return ArgumentMatchers.any(Instant::class.java) ?: Instant.EPOCH
    }

    private fun <T> eqValue(value: T): T {
        return ArgumentMatchers.eq(value) ?: value
    }
}
