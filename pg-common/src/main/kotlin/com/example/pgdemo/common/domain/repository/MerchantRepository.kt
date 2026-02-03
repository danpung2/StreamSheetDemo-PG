package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.`enum`.BusinessType
import com.example.pgdemo.common.domain.`enum`.StoreType
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository interface for Merchant entity.
 * 업체(가맹점) 엔티티에 대한 Repository 인터페이스.
 * 
 * Provides data access operations for merchant management.
 * 업체 관리를 위한 데이터 접근 연산을 제공합니다.
 */
@Repository
interface MerchantRepository : JpaRepository<Merchant, UUID> {
    
    /**
     * Find merchant by unique code.
     * 고유 코드로 업체를 조회합니다.
     * 
     * @param merchantCode the unique merchant code / 고유한 업체 코드
     * @return the merchant if found, null otherwise / 업체가 존재하면 반환, 없으면 null
     */
    fun findByMerchantCode(merchantCode: String): Merchant?
    
    /**
     * Check if merchant exists by code.
     * 코드로 업체 존재 여부를 확인합니다.
     * 
     * @param merchantCode the merchant code to check / 확인할 업체 코드
     * @return true if exists, false otherwise / 존재하면 true, 아니면 false
     */
    fun existsByMerchantCode(merchantCode: String): Boolean
    
    /**
     * Find all merchants belonging to a headquarters.
     * 특정 본사에 소속된 모든 업체를 조회합니다.
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @return list of merchants under the headquarters / 해당 본사 소속 업체 목록
     */
    fun findByHeadquartersId(headquartersId: UUID): List<Merchant>
    
    /**
     * Find all merchants belonging to a headquarters with pagination.
     * 특정 본사에 소속된 모든 업체를 페이지네이션으로 조회합니다.
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated merchants result / 페이지네이션된 업체 결과
     */
    fun findByHeadquartersId(headquartersId: UUID, pageable: Pageable): Page<Merchant>
    
    /**
     * Find merchants by status.
     * 상태별 업체를 조회합니다.
     * 
     * @param status the status to filter by / 필터링할 상태
     * @return list of merchants with the given status / 해당 상태의 업체 목록
     */
    fun findByStatus(status: String): List<Merchant>
    
    /**
     * Find merchants by status with pagination.
     * 상태별 업체를 페이지네이션으로 조회합니다.
     * 
     * @param status the status to filter by / 필터링할 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated merchants result / 페이지네이션된 업체 결과
     */
    fun findByStatus(status: String, pageable: Pageable): Page<Merchant>
    
    /**
     * Find merchants by store type.
     * 점포 유형별 업체를 조회합니다.
     * 
     * @param storeType the store type / 점포 유형
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated merchants result / 페이지네이션된 업체 결과
     */
    fun findByStoreType(storeType: StoreType, pageable: Pageable): Page<Merchant>
    
    /**
     * Find merchants by business type.
     * 업종별 업체를 조회합니다.
     * 
     * @param businessType the business type / 업종
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated merchants result / 페이지네이션된 업체 결과
     */
    fun findByBusinessType(businessType: BusinessType, pageable: Pageable): Page<Merchant>
    
    /**
     * Search merchants by name containing keyword.
     * 이름에 키워드를 포함하는 업체를 검색합니다.
     * 
     * @param name the keyword to search / 검색할 키워드
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated search result / 페이지네이션된 검색 결과
     */
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Merchant>
    
    /**
     * Find merchants by headquarters and status.
     * 본사 및 상태별 업체를 조회합니다.
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @param status the status to filter by / 필터링할 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated merchants result / 페이지네이션된 업체 결과
     */
    fun findByHeadquartersIdAndStatus(
        headquartersId: UUID, 
        status: String, 
        pageable: Pageable
    ): Page<Merchant>
    
    /**
     * Count merchants by headquarters.
     * 본사별 업체 수를 조회합니다.
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @return the count of merchants under the headquarters / 해당 본사 소속 업체 수
     */
    fun countByHeadquartersId(headquartersId: UUID): Long
    
    /**
     * Count merchants by headquarters and status.
     * 본사 및 상태별 업체 수를 조회합니다.
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @param status the status to filter by / 필터링할 상태
     * @return the count of merchants / 업체 수
     */
    fun countByHeadquartersIdAndStatus(headquartersId: UUID, status: String): Long
    
    /**
     * Find merchants with contracts expiring within date range.
     * 특정 기간 내에 계약이 만료되는 업체를 조회합니다.
     * 
     * @param startDate the start of date range / 시작 날짜
     * @param endDate the end of date range / 종료 날짜
     * @return list of merchants with expiring contracts / 계약 만료 예정 업체 목록
     */
    @Query("""
        SELECT m FROM Merchant m 
        WHERE m.contractEndDate BETWEEN :startDate AND :endDate 
        AND m.status = 'ACTIVE'
    """)
    fun findByContractExpiring(
        @Param("startDate") startDate: LocalDate, 
        @Param("endDate") endDate: LocalDate
    ): List<Merchant>
    
    /**
     * Find all merchant IDs under a headquarters.
     * 특정 본사 소속 모든 업체의 ID를 조회합니다.
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @return list of merchant IDs / 업체 ID 목록
     */
    @Query("SELECT m.id FROM Merchant m WHERE m.headquarters.id = :headquartersId")
    fun findAllIdsByHeadquartersId(@Param("headquartersId") headquartersId: UUID): List<UUID>
    
    /**
     * Find merchants by IDs.
     * ID 목록으로 업체를 조회합니다.
     * 
     * @param ids the list of merchant IDs / 업체 ID 목록
     * @return list of merchants / 업체 목록
     */
    fun findByIdIn(ids: List<UUID>): List<Merchant>

    @Query(
        """
        SELECT m FROM Merchant m
        WHERE (:headquartersId IS NULL OR m.headquarters.id = :headquartersId)
          AND (:status IS NULL OR m.status = :status)
          AND (
            :merchantQuery IS NULL OR
            LOWER(m.name) LIKE LOWER(CONCAT('%', :merchantQuery, '%')) OR
            LOWER(m.merchantCode) LIKE LOWER(CONCAT('%', :merchantQuery, '%'))
          )
        """
    )
    fun searchMerchants(
        @Param("headquartersId") headquartersId: UUID?,
        @Param("status") status: String?,
        @Param("merchantQuery") merchantQuery: String?,
        pageable: Pageable
    ): Page<Merchant>
}
