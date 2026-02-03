package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.RefreshToken
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for RefreshToken entity.
 * 리프레시 토큰 엔티티에 대한 Repository.
 */
@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    
    // 토큰 해시로 조회 / Find by token hash
    fun findByTokenHash(tokenHash: String): RefreshToken?
    
    // 유효한 토큰 조회 / Find valid token
    @Query("""
        SELECT r FROM RefreshToken r 
        WHERE r.tokenHash = :tokenHash 
        AND r.expiresAt > :currentTime 
        AND r.revokedAt IS NULL
    """)
    fun findValidToken(
        @Param("tokenHash") tokenHash: String, 
        @Param("currentTime") currentTime: Instant
    ): RefreshToken?
    
    // 사용자별 토큰 조회 / Find by admin user
    fun findByAdminUserId(adminUserId: UUID): List<RefreshToken>
    
    // 토큰 폐기 / Revoke token
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.tokenHash = :tokenHash")
    fun revokeByTokenHash(
        @Param("tokenHash") tokenHash: String, 
        @Param("revokedAt") revokedAt: Instant
    ): Int
    
    // 사용자의 모든 토큰 폐기 / Revoke all user tokens
    @Modifying
    @Query("""
        UPDATE RefreshToken r SET r.revokedAt = :revokedAt 
        WHERE r.adminUser.id = :adminUserId AND r.revokedAt IS NULL
    """)
    fun revokeAllByUserId(
        @Param("adminUserId") adminUserId: UUID, 
        @Param("revokedAt") revokedAt: Instant
    ): Int
    
    // 만료된 토큰 삭제 / Delete expired tokens
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :expirationDate")
    fun deleteExpiredTokens(@Param("expirationDate") expirationDate: Instant): Int
}
