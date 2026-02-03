package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository interface for AdminUser entity.
 * 관리자 사용자 엔티티에 대한 Repository 인터페이스.
 * 
 * Provides data access operations for admin user management and authentication.
 * 관리자 사용자 관리 및 인증을 위한 데이터 접근 연산을 제공합니다.
 */
@Repository
interface AdminUserRepository : JpaRepository<AdminUser, UUID> {
    
    /**
     * Find admin user by email.
     * 이메일로 관리자 사용자를 조회합니다.
     * 
     * @param email the email address / 이메일 주소
     * @return the admin user if found, null otherwise / 관리자 사용자가 존재하면 반환
     */
    fun findByEmail(email: String): AdminUser?
    
    /**
     * Check if email is already registered.
     * 이메일이 이미 등록되어 있는지 확인합니다.
     * 
     * @param email the email to check / 확인할 이메일
     * @return true if exists, false otherwise / 존재하면 true
     */
    fun existsByEmail(email: String): Boolean
    
    /**
     * Find admin users by tenant type.
     * 테넌트 유형별 관리자 사용자를 조회합니다.
     * 
     * @param tenantType the tenant type / 테넌트 유형
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated admin users / 페이지네이션된 관리자 사용자
     */
    fun findByTenantType(tenantType: TenantType, pageable: Pageable): Page<AdminUser>
    
    /**
     * Find admin users by tenant ID (headquarters or merchant ID).
     * 테넌트 ID별 관리자 사용자를 조회합니다 (본사 또는 업체 ID).
     * 
     * @param tenantId the tenant ID / 테넌트 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated admin users / 페이지네이션된 관리자 사용자
     */
    fun findByTenantId(tenantId: UUID, pageable: Pageable): Page<AdminUser>
    
    /**
     * Find admin users by tenant type and tenant ID.
     * 테넌트 유형 및 ID별 관리자 사용자를 조회합니다.
     * 
     * @param tenantType the tenant type / 테넌트 유형
     * @param tenantId the tenant ID / 테넌트 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated admin users / 페이지네이션된 관리자 사용자
     */
    fun findByTenantTypeAndTenantId(
        tenantType: TenantType, 
        tenantId: UUID, 
        pageable: Pageable
    ): Page<AdminUser>
    
    /**
     * Find admin users by role.
     * 역할별 관리자 사용자를 조회합니다.
     * 
     * @param role the user role / 사용자 역할
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated admin users / 페이지네이션된 관리자 사용자
     */
    fun findByRole(role: UserRole, pageable: Pageable): Page<AdminUser>
    
    /**
     * Find admin users by status.
     * 상태별 관리자 사용자를 조회합니다.
     * 
     * @param status the user status / 사용자 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated admin users / 페이지네이션된 관리자 사용자
     */
    fun findByStatus(status: String, pageable: Pageable): Page<AdminUser>
    
    /**
     * Find admin users by tenant ID and status.
     * 테넌트 ID 및 상태별 관리자 사용자를 조회합니다.
     * 
     * @param tenantId the tenant ID / 테넌트 ID
     * @param status the user status / 사용자 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated admin users / 페이지네이션된 관리자 사용자
     */
    fun findByTenantIdAndStatus(tenantId: UUID, status: String, pageable: Pageable): Page<AdminUser>
    
    /**
     * Count admin users by tenant ID.
     * 테넌트별 관리자 사용자 수를 조회합니다.
     * 
     * @param tenantId the tenant ID / 테넌트 ID
     * @return the count of admin users / 관리자 사용자 수
     */
    fun countByTenantId(tenantId: UUID): Long
    
    /**
     * Count admin users by tenant ID and status.
     * 테넌트 및 상태별 관리자 사용자 수를 조회합니다.
     * 
     * @param tenantId the tenant ID / 테넌트 ID
     * @param status the user status / 사용자 상태
     * @return the count of admin users / 관리자 사용자 수
     */
    fun countByTenantIdAndStatus(tenantId: UUID, status: String): Long
    
    /**
     * Update last login timestamp.
     * 마지막 로그인 시간을 업데이트합니다.
     * 
     * @param id the admin user ID / 관리자 사용자 ID
     * @param lastLoginAt the last login timestamp / 마지막 로그인 시간
     * @return the number of updated rows / 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE AdminUser a SET a.lastLoginAt = :lastLoginAt WHERE a.id = :id")
    fun updateLastLoginAt(@Param("id") id: UUID, @Param("lastLoginAt") lastLoginAt: Instant): Int
    
    /**
     * Increment failed login attempts.
     * 로그인 실패 횟수를 증가시킵니다.
     * 
     * @param id the admin user ID / 관리자 사용자 ID
     * @return the number of updated rows / 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE AdminUser a SET a.failedLoginAttempts = a.failedLoginAttempts + 1 WHERE a.id = :id")
    fun incrementFailedLoginAttempts(@Param("id") id: UUID): Int
    
    /**
     * Reset failed login attempts.
     * 로그인 실패 횟수를 초기화합니다.
     * 
     * @param id the admin user ID / 관리자 사용자 ID
     * @return the number of updated rows / 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE AdminUser a SET a.failedLoginAttempts = 0, a.lockedUntil = NULL WHERE a.id = :id")
    fun resetFailedLoginAttempts(@Param("id") id: UUID): Int
    
    /**
     * Lock user account until specified time.
     * 지정된 시간까지 사용자 계정을 잠급니다.
     * 
     * @param id the admin user ID / 관리자 사용자 ID
     * @param lockedUntil the lock expiration timestamp / 잠금 만료 시간
     * @return the number of updated rows / 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE AdminUser a SET a.lockedUntil = :lockedUntil WHERE a.id = :id")
    fun lockUserUntil(@Param("id") id: UUID, @Param("lockedUntil") lockedUntil: Instant): Int
    
    /**
     * Find currently locked users (for admin monitoring).
     * 현재 잠긴 사용자를 조회합니다 (관리자 모니터링용).
     * 
     * @param currentTime the current timestamp / 현재 시간
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated locked admin users / 페이지네이션된 잠긴 관리자 사용자
     */
    @Query("""
        SELECT a FROM AdminUser a 
        WHERE a.lockedUntil IS NOT NULL AND a.lockedUntil > :currentTime
    """)
    fun findLockedUsers(
        @Param("currentTime") currentTime: Instant, 
        pageable: Pageable
    ): Page<AdminUser>
    
    /**
     * Search admin users by name or email.
     * 이름 또는 이메일로 관리자 사용자를 검색합니다.
     * 
     * @param keyword the search keyword / 검색 키워드
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated search results / 페이지네이션된 검색 결과
     */
    @Query("""
        SELECT a FROM AdminUser a 
        WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
        OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    fun searchByNameOrEmail(
        @Param("keyword") keyword: String, 
        pageable: Pageable
    ): Page<AdminUser>
    
    /**
     * Find all platform admin users (for system-wide operations).
     * 모든 플랫폼 관리자를 조회합니다 (시스템 전체 작업용).
     * 
     * @return list of platform admin users / 플랫폼 관리자 목록
     */
    @Query("SELECT a FROM AdminUser a WHERE a.tenantType = 'PLATFORM'")
    fun findAllPlatformAdmins(): List<AdminUser>
}
