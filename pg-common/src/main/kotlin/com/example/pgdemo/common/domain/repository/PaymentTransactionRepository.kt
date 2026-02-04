package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.`enum`.PaymentStatus
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository interface for PaymentTransaction entity. 결제 트랜잭션 엔티티에 대한 Repository 인터페이스.
 *
 * Provides data access operations for payment transaction management. 결제 트랜잭션 관리를 위한 데이터 접근 연산을
 * 제공합니다.
 */
@Repository
interface PaymentTransactionRepository :
        JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {

    @Query(
            """
        SELECT COUNT(p) FROM PaymentTransaction p
        WHERE p.merchant.id = :merchantId
          AND p.requestedAt BETWEEN :startDate AND :endDate
        """
    )
    fun countByMerchantIdAndRequestedAtBetween(
            @Param("merchantId") merchantId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant
    ): Long

    @Query(
            """
        SELECT COUNT(p) FROM PaymentTransaction p
        WHERE p.merchant.id = :merchantId
          AND p.requestedAt BETWEEN :startDate AND :endDate
          AND p.status = :status
        """
    )
    fun countByMerchantIdAndRequestedAtBetweenAndStatus(
            @Param("merchantId") merchantId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("status") status: PaymentStatus
    ): Long

    @Query(
            """
        SELECT COUNT(p) FROM PaymentTransaction p
        WHERE p.requestedAt BETWEEN :startDate AND :endDate
        """
    )
    fun countByRequestedAtBetween(
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant
    ): Long

    @Query(
            """
        SELECT COUNT(p) FROM PaymentTransaction p
        WHERE p.requestedAt BETWEEN :startDate AND :endDate
          AND p.status = :status
        """
    )
    fun countByRequestedAtBetweenAndStatus(
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("status") status: PaymentStatus
    ): Long

    @Query(
            """
        SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p
        WHERE p.requestedAt BETWEEN :startDate AND :endDate
          AND p.status = :status
        """
    )
    fun sumAmountByRequestedAtBetweenAndStatus(
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("status") status: PaymentStatus
    ): Long

    @Query(
            """
        SELECT COUNT(p) FROM PaymentTransaction p
        JOIN p.merchant m
        WHERE m.headquarters.id = :headquartersId
          AND p.requestedAt BETWEEN :startDate AND :endDate
        """
    )
    fun countByHeadquartersIdAndRequestedAtBetween(
            @Param("headquartersId") headquartersId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant
    ): Long

    @Query(
            """
        SELECT COUNT(p) FROM PaymentTransaction p
        JOIN p.merchant m
        WHERE m.headquarters.id = :headquartersId
          AND p.requestedAt BETWEEN :startDate AND :endDate
          AND p.status = :status
        """
    )
    fun countByHeadquartersIdAndRequestedAtBetweenAndStatus(
            @Param("headquartersId") headquartersId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("status") status: PaymentStatus
    ): Long

    @Query(
            """
        SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p
        JOIN p.merchant m
        WHERE m.headquarters.id = :headquartersId
          AND p.requestedAt BETWEEN :startDate AND :endDate
          AND p.status = :status
        """
    )
    fun sumAmountByHeadquartersIdAndRequestedAtBetweenAndStatus(
            @Param("headquartersId") headquartersId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("status") status: PaymentStatus
    ): Long

    @Query(
            value =
                    """
        SELECT COALESCE(
          percentile_cont(0.95) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (COALESCE(completed_at, processed_at, requested_at) - requested_at)) * 1000
          ),
          0
        )
        FROM payment_transaction
        WHERE requested_at BETWEEN :startDate AND :endDate
        """,
            nativeQuery = true
    )
    fun p95DelayMsByRequestedAtBetween(
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant
    ): Double

    @Query(
            value =
                    """
        SELECT COALESCE(
          percentile_cont(0.95) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (COALESCE(p.completed_at, p.processed_at, p.requested_at) - p.requested_at)) * 1000
          ),
          0
        )
        FROM payment_transaction p
        JOIN merchant m ON m.id = p.merchant_id
        WHERE m.headquarters_id = :headquartersId
          AND p.requested_at BETWEEN :startDate AND :endDate
        """,
            nativeQuery = true
    )
    fun p95DelayMsByHeadquartersIdAndRequestedAtBetween(
            @Param("headquartersId") headquartersId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant
    ): Double

    @Query(
            value =
                    """
        SELECT COALESCE(
          percentile_cont(0.95) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (COALESCE(completed_at, processed_at, requested_at) - requested_at)) * 1000
          ),
          0
        )
        FROM payment_transaction
        WHERE merchant_id = :merchantId
          AND requested_at BETWEEN :startDate AND :endDate
        """,
            nativeQuery = true
    )
    fun p95DelayMsByMerchantIdAndRequestedAtBetween(
            @Param("merchantId") merchantId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant
    ): Double

    interface MerchantFailureAgg {
        fun getMerchantId(): UUID
        fun getTotalCount(): Long
        fun getFailedCount(): Long
    }

    @Query(
            value =
                    """
        SELECT
          p.merchant_id AS merchantId,
          COUNT(*) AS totalCount,
          SUM(CASE WHEN p.status = 'PAYMENT_FAILED' THEN 1 ELSE 0 END) AS failedCount
        FROM payment_transaction p
        WHERE p.requested_at BETWEEN :startDate AND :endDate
        GROUP BY p.merchant_id
        HAVING COUNT(*) >= :minCount
        ORDER BY (CAST(SUM(CASE WHEN p.status = 'PAYMENT_FAILED' THEN 1 ELSE 0 END) AS float) / COUNT(*)) DESC
        LIMIT :limit
        """,
            nativeQuery = true
    )
    fun topMerchantsByFailureRate(
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("minCount") minCount: Long,
            @Param("limit") limit: Int
    ): List<MerchantFailureAgg>

    @Query(
            value =
                    """
        SELECT
          p.merchant_id AS merchantId,
          COUNT(*) AS totalCount,
          SUM(CASE WHEN p.status = 'PAYMENT_FAILED' THEN 1 ELSE 0 END) AS failedCount
        FROM payment_transaction p
        JOIN merchant m ON m.id = p.merchant_id
        WHERE m.headquarters_id = :headquartersId
          AND p.requested_at BETWEEN :startDate AND :endDate
        GROUP BY p.merchant_id
        HAVING COUNT(*) >= :minCount
        ORDER BY (CAST(SUM(CASE WHEN p.status = 'PAYMENT_FAILED' THEN 1 ELSE 0 END) AS float) / COUNT(*)) DESC
        LIMIT :limit
        """,
            nativeQuery = true
    )
    fun topMerchantsByFailureRateForHeadquarters(
            @Param("headquartersId") headquartersId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("minCount") minCount: Long,
            @Param("limit") limit: Int
    ): List<MerchantFailureAgg>

    /**
     * Find payment transaction by order ID. 주문 ID로 결제 트랜잭션을 조회합니다.
     *
     * @param orderId the order ID / 주문 ID
     * @return the payment transaction if found, null otherwise / 결제 트랜잭션이 존재하면 반환
     */
    fun findByOrderId(orderId: String): PaymentTransaction?

    /**
     * Check if order ID exists. 주문 ID 존재 여부를 확인합니다.
     *
     * @param orderId the order ID to check / 확인할 주문 ID
     * @return true if exists, false otherwise / 존재하면 true
     */
    fun existsByOrderId(orderId: String): Boolean

    /**
     * Find all payments by merchant. 특정 업체의 모든 결제를 조회합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @return list of payment transactions / 결제 트랜잭션 목록
     */
    fun findByMerchantId(merchantId: UUID): List<PaymentTransaction>

    /**
     * Find all payments by merchant with pagination. 특정 업체의 모든 결제를 페이지네이션으로 조회합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    fun findByMerchantId(merchantId: UUID, pageable: Pageable): Page<PaymentTransaction>

    /**
     * Find payments by status. 상태별 결제를 조회합니다.
     *
     * @param status the payment status / 결제 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    fun findByStatus(status: PaymentStatus, pageable: Pageable): Page<PaymentTransaction>

    /**
     * Find payments by merchant and status. 업체 및 상태별 결제를 조회합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @param status the payment status / 결제 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    fun findByMerchantIdAndStatus(
            merchantId: UUID,
            status: PaymentStatus,
            pageable: Pageable
    ): Page<PaymentTransaction>

    /**
     * Find payments requested within date range. 특정 기간 내에 요청된 결제를 조회합니다.
     *
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    fun findByRequestedAtBetween(
            startDate: Instant,
            endDate: Instant,
            pageable: Pageable
    ): Page<PaymentTransaction>

    /**
     * Find payments by merchant and date range. 업체 및 기간별 결제를 조회합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    fun findByMerchantIdAndRequestedAtBetween(
            merchantId: UUID,
            startDate: Instant,
            endDate: Instant,
            pageable: Pageable
    ): Page<PaymentTransaction>

    /**
     * Find payments by payment method. 결제 수단별 결제를 조회합니다.
     *
     * @param paymentMethod the payment method / 결제 수단
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    fun findByPaymentMethod(paymentMethod: String, pageable: Pageable): Page<PaymentTransaction>

    /**
     * Count payments by merchant. 업체별 결제 건수를 조회합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @return the count of payments / 결제 건수
     */
    fun countByMerchantId(merchantId: UUID): Long

    /**
     * Count payments by merchant and status. 업체 및 상태별 결제 건수를 조회합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @param status the payment status / 결제 상태
     * @return the count of payments / 결제 건수
     */
    fun countByMerchantIdAndStatus(merchantId: UUID, status: PaymentStatus): Long

    /**
     * Calculate total amount by merchant and date range. 업체 및 기간별 총 결제 금액을 계산합니다.
     *
     * @param merchantId the merchant ID / 업체 ID
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param status the payment status (typically COMPLETED) / 결제 상태 (일반적으로 COMPLETED)
     * @return the total amount / 총 금액
     */
    @Query(
            """
        SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p 
        WHERE p.merchant.id = :merchantId 
        AND p.requestedAt BETWEEN :startDate AND :endDate
        AND p.status = :status
    """
    )
    fun sumAmountByMerchantIdAndDateRange(
            @Param("merchantId") merchantId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            @Param("status") status: PaymentStatus
    ): Long

    /**
     * Find payments by headquarters (through merchant relationship). 본사별 결제를 조회합니다 (업체 관계를 통해).
     *
     * @param headquartersId the headquarters ID / 본사 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    @Query(
            """
        SELECT p FROM PaymentTransaction p 
        JOIN p.merchant m 
        WHERE m.headquarters.id = :headquartersId
    """
    )
    fun findByHeadquartersId(
            @Param("headquartersId") headquartersId: UUID,
            pageable: Pageable
    ): Page<PaymentTransaction>

    /**
     * Find payments by headquarters and date range. 본사 및 기간별 결제를 조회합니다.
     *
     * @param headquartersId the headquarters ID / 본사 ID
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    @Query(
            """
        SELECT p FROM PaymentTransaction p 
        JOIN p.merchant m 
        WHERE m.headquarters.id = :headquartersId 
        AND p.requestedAt BETWEEN :startDate AND :endDate
    """
    )
    fun findByHeadquartersIdAndDateRange(
            @Param("headquartersId") headquartersId: UUID,
            @Param("startDate") startDate: Instant,
            @Param("endDate") endDate: Instant,
            pageable: Pageable
    ): Page<PaymentTransaction>

    /**
     * Update payment status. 결제 상태를 업데이트합니다.
     *
     * @param id the payment ID / 결제 ID
     * @param status the new status / 새 상태
     * @param processedAt the processed timestamp / 처리 시간
     * @return the number of updated rows / 업데이트된 행 수
     */
    @Modifying
    @Query(
            """
        UPDATE PaymentTransaction p 
        SET p.status = :status, p.processedAt = :processedAt 
        WHERE p.id = :id
    """
    )
    fun updateStatus(
            @Param("id") id: UUID,
            @Param("status") status: PaymentStatus,
            @Param("processedAt") processedAt: Instant
    ): Int

    /**
     * Find payments needing synchronization to MongoDB. MongoDB로 동기화가 필요한 결제를 조회합니다.
     *
     * @param lastSyncTime the last synchronization timestamp / 마지막 동기화 시간
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated payment transactions / 페이지네이션된 결제 트랜잭션
     */
    @Query(
            """
        SELECT p FROM PaymentTransaction p 
        WHERE p.updatedAt > :lastSyncTime 
        ORDER BY p.updatedAt ASC
    """
    )
    fun findPaymentsForSync(
            @Param("lastSyncTime") lastSyncTime: Instant,
            pageable: Pageable
    ): Page<PaymentTransaction>

    @Query(
            """
        SELECT p FROM PaymentTransaction p
        WHERE p.updatedAt > :lastSyncTime
           OR p.updatedAt = :lastSyncTime
        ORDER BY p.updatedAt ASC, p.id ASC
    """
    )
    fun findPaymentsForSyncFromTimeInclusive(
            @Param("lastSyncTime") lastSyncTime: Instant,
            pageable: Pageable
    ): Page<PaymentTransaction>

    @Query(
            """
        SELECT p FROM PaymentTransaction p
        WHERE p.updatedAt > :lastSyncTime
           OR (p.updatedAt = :lastSyncTime AND p.id > :lastSyncId)
        ORDER BY p.updatedAt ASC, p.id ASC
    """
    )
    fun findPaymentsForSyncCursor(
            @Param("lastSyncTime") lastSyncTime: Instant,
            @Param("lastSyncId") lastSyncId: UUID,
            pageable: Pageable
    ): Page<PaymentTransaction>

    /**
     * Find all IDs pending synchronization (for batch job). 동기화 대기 중인 모든 ID를 조회합니다 (배치 작업용).
     *
     * @param lastSyncTime the last synchronization timestamp / 마지막 동기화 시간
     * @return list of payment IDs / 결제 ID 목록
     */
    @Query("SELECT p.id FROM PaymentTransaction p WHERE p.updatedAt > :lastSyncTime")
    fun findIdsForSync(@Param("lastSyncTime") lastSyncTime: Instant): List<UUID>
}
