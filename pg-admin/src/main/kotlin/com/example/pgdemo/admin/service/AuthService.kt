package com.example.pgdemo.admin.service

import com.example.pgdemo.admin.dto.TokenResponse
import com.example.pgdemo.admin.security.JwtProperties
import com.example.pgdemo.admin.security.JwtTokenProvider
import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.RefreshToken
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import com.example.pgdemo.common.domain.repository.RefreshTokenRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val adminUserRepository: AdminUserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun login(email: String, password: String): TokenResponse {
        val adminUser = adminUserRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!passwordEncoder.matches(password, adminUser.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        return issueTokens(adminUser)
    }

    @Transactional
    fun refresh(refreshToken: String): TokenResponse {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        val adminUserId = jwtTokenProvider.getUserId(refreshToken)
        jwtTokenProvider.getTokenId(refreshToken)

        val now = Instant.now()
        val tokenHash = hashToken(refreshToken)
        val existingToken = refreshTokenRepository.findValidToken(tokenHash, now)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")

        if (existingToken.adminUser.id != adminUserId) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        refreshTokenRepository.revokeByTokenHash(tokenHash, now)

        val adminUser = existingToken.adminUser
        return issueTokens(adminUser)
    }

    @Transactional
    fun logout(adminUserId: UUID) {
        refreshTokenRepository.revokeAllByUserId(adminUserId, Instant.now())
    }

    private fun issueTokens(adminUser: AdminUser): TokenResponse {
        val accessToken = jwtTokenProvider.generateAccessToken(adminUser)
        val refreshTokenId = UUID.randomUUID()
        val refreshToken = jwtTokenProvider.generateRefreshToken(adminUser, refreshTokenId)

        val refreshTokenEntity = RefreshToken().apply {
            this.adminUser = adminUser
            this.tokenHash = hashToken(refreshToken)
            this.expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiry)
        }
        refreshTokenRepository.save(refreshTokenEntity)

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
