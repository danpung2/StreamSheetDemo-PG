package com.example.pgdemo.admin.service

import com.example.pgdemo.admin.dto.TokenResponse
import com.example.pgdemo.admin.security.JwtProperties
import com.example.pgdemo.admin.security.JwtTokenProvider
import com.example.pgdemo.admin.security.LoginProperties
import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.RefreshToken
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import com.example.pgdemo.common.domain.repository.RefreshTokenRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Authentication service handling login, token refresh, and logout.
 * 로그인, 토큰 갱신, 로그아웃을 처리하는 인증 서비스.
 *
 * Includes brute-force protection via account lockout after max failed attempts.
 * 최대 실패 횟수 초과 시 계정 잠금을 통한 브루트포스 방어를 포함합니다.
 */
@Service
class AuthService(
    private val adminUserRepository: AdminUserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val loginProperties: LoginProperties,
    private val passwordEncoder: PasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun login(email: String, password: String): TokenResponse {
        val adminUser = adminUserRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        val now = Instant.now()

        // Check if account is locked / 계정 잠금 상태 확인
        if (isAccountLocked(adminUser, now)) {
            logger.warn("Login attempt for locked account: {}", email)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is locked. Please try again later.")
        }

        // Validate password / 비밀번호 검증
        if (!passwordEncoder.matches(password, adminUser.passwordHash)) {
            handleFailedLogin(adminUser, now)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        // Successful login - reset failed attempts and update last login time
        // 로그인 성공 - 실패 횟수 초기화 및 마지막 로그인 시간 업데이트
        handleSuccessfulLogin(adminUser, now)

        return issueTokens(adminUser)
    }

    /**
     * Check if the account is currently locked.
     * 계정이 현재 잠겨 있는지 확인합니다.
     */
    private fun isAccountLocked(adminUser: AdminUser, now: Instant): Boolean {
        val lockedUntil = adminUser.lockedUntil ?: return false
        return lockedUntil.isAfter(now)
    }

    /**
     * Handle failed login attempt - increment counter and lock if threshold reached.
     * 로그인 실패 처리 - 카운터 증가 및 임계값 도달 시 잠금.
     */
    private fun handleFailedLogin(adminUser: AdminUser, now: Instant) {
        val userId = adminUser.id ?: return

        adminUserRepository.incrementFailedLoginAttempts(userId)

        // Check if we need to lock the account (current count + 1 >= max)
        // 계정 잠금이 필요한지 확인 (현재 카운트 + 1 >= 최대값)
        val newFailedAttempts = adminUser.failedLoginAttempts + 1
        if (newFailedAttempts >= loginProperties.maxFailedAttempts) {
            val lockUntil = now.plus(loginProperties.lockDuration)
            adminUserRepository.lockUserUntil(userId, lockUntil)
            logger.warn(
                "Account locked due to {} failed login attempts: {} (locked until: {})",
                newFailedAttempts,
                adminUser.email,
                lockUntil
            )
        } else {
            logger.info(
                "Failed login attempt {} of {} for: {}",
                newFailedAttempts,
                loginProperties.maxFailedAttempts,
                adminUser.email
            )
        }
    }

    /**
     * Handle successful login - reset failed attempts and update last login time.
     * 로그인 성공 처리 - 실패 횟수 초기화 및 마지막 로그인 시간 업데이트.
     */
    private fun handleSuccessfulLogin(adminUser: AdminUser, now: Instant) {
        val userId = adminUser.id ?: return

        // Only reset if there were previous failed attempts or account was locked
        // 이전 실패 시도가 있었거나 계정이 잠겨 있었던 경우에만 초기화
        if (adminUser.failedLoginAttempts > 0 || adminUser.lockedUntil != null) {
            adminUserRepository.resetFailedLoginAttempts(userId)
        }

        adminUserRepository.updateLastLoginAt(userId, now)
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
