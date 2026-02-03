package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.`enum`.RefundStatus
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
 * Repository interface for RefundTransaction entity.
 * 환불 트랜잭션 엔티티에 대한 Repository 인터페이스.
 * 
 * Provides data access operations for refund transaction management.
 * 환불 트랜잭션 관리를 위한 데이터 접근 연산을 제공합니다.
 */
@Repository
interface RefundTransactionRepository : JpaRepository<RefundTransaction, UUID> {
    
    /**
     * Find refund transaction by payment ID.
     * 결제 ID로 환불 트랜잭션을 조회합니다.
     * 
     * @param paymentId the payment ID / 결제 ID
     * @return list of refund transactions / 환불 트랜잭션 목록
     */
    fun findByPaymentId(paymentId: UUID): List<RefundTransaction>
    
    /**
     * Find single refund by payment ID (for full refunds).
     * 결제 ID로 단일 환불을 조회합니다 (전액 환불의 경우).
     * 
     * @param paymentId the payment ID / 결제 ID
     * @return the refund transaction if found, null otherwise / 환불 트랜잭션이 존재하면 반환
     */
    fun findFirstByPaymentId(paymentId: UUID): RefundTransaction?
    
    /**
     * Check if refund exists for payment.
     * 결제에 대한 환불 존재 여부를 확인합니다.
     * 
     * @param paymentId the payment ID / 결제 ID
     * @return true if refund exists, false otherwise / 환불이 존재하면 true
     */
    fun existsByPaymentId(paymentId: UUID): Boolean
    
    /**
     * Find refunds by status.
     * 상태별 환불을 조회합니다.
     * 
     * @param status the refund status / 환불 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated refund transactions / 페이지네이션된 환불 트랜잭션
     */
    fun findByStatus(status: RefundStatus, pageable: Pageable): Page<RefundTransaction>
    
    /**
     * Find refunds requested within date range.
     * 특정 기간 내에 요청된 환불을 조회합니다.
     * 
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated refund transactions / 페이지네이션된 환불 트랜잭션
     */
    fun findByRequestedAtBetween(
        startDate: Instant, 
        endDate: Instant, 
        pageable: Pageable
    ): Page<RefundTransaction>
    
    /**
     * Find refunds by payment and status.
     * 결제 및 상태별 환불을 조회합니다.
     * 
     * @param paymentId the payment ID / 결제 ID
     * @param status the refund status / 환불 상태
     * @return list of refund transactions / 환불 트랜잭션 목록
     */
    fun findByPaymentIdAndStatus(paymentId: UUID, status: RefundStatus): List<RefundTransaction>
    
    /**
     * Count refunds by payment.
     * 결제별 환불 건수를 조회합니다.
     * 
     * @param paymentId the payment ID / 결제 ID
     * @return the count of refunds / 환불 건수
     */
    fun countByPaymentId(paymentId: UUID): Long
    
    /**
     * Calculate total refund amount by payment.
     * 결제별 총 환불 금액을 계산합니다.
     * 
     * @param paymentId the payment ID / 결제 ID
     * @return the total refund amount / 총 환불 금액
     */
    @Query("""
        SELECT COALESCE(SUM(r.refundAmount), 0) FROM RefundTransaction r 
        WHERE r.payment.id = :paymentId 
        AND r.status = 'COMPLETED'
    """)
    fun sumRefundAmountByPaymentId(@Param("paymentId") paymentId: UUID): Long
    
    /**
     * Find refunds by merchant (through payment relationship).
     * 업체별 환불을 조회합니다 (결제 관계를 통해).
     * 
     * @param merchantId the merchant ID / 업체 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated refund transactions / 페이지네이션된 환불 트랜잭션
     */
    @Query("""
        SELECT r FROM RefundTransaction r 
        JOIN r.payment p 
        WHERE p.merchant.id = :merchantId
    """)
    fun findByMerchantId(
        @Param("merchantId") merchantId: UUID, 
        pageable: Pageable
    ): Page<RefundTransaction>
    
    /**
     * Find refunds by merchant and date range.
     * 업체 및 기간별 환불을 조회합니다.
     * 
     * @param merchantId the merchant ID / 업체 ID
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated refund transactions / 페이지네이션된 환불 트랜잭션
     */
    @Query("""
        SELECT r FROM RefundTransaction r 
        JOIN r.payment p 
        WHERE p.merchant.id = :merchantId 
        AND r.requestedAt BETWEEN :startDate AND :endDate
    """)
    fun findByMerchantIdAndDateRange(
        @Param("merchantId") merchantId: UUID,
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant,
        pageable: Pageable
    ): Page<RefundTransaction>
    
    /**
     * Find refunds by headquarters (through payment and merchant relationship).
     * 본사별 환불을 조회합니다 (결제 및 업체 관계를 통해).
     * 
     * @param headquartersId the headquarters ID / 본사 ID
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated refund transactions / 페이지네이션된 환불 트랜잭션
     */
    @Query("""
        SELECT r FROM RefundTransaction r 
        JOIN r.payment p 
        JOIN p.merchant m 
        WHERE m.headquarters.id = :headquartersId
    """)
    fun findByHeadquartersId(
        @Param("headquartersId") headquartersId: UUID, 
        pageable: Pageable
    ): Page<RefundTransaction>
    
    /**
     * Calculate total refund amount by merchant and date range.
     * 업체 및 기간별 총 환불 금액을 계산합니다.
     * 
     * @param merchantId the merchant ID / 업체 ID
     * @param startDate the start of date range / 시작 일시
     * @param endDate the end of date range / 종료 일시
     * @param status the refund status (typically COMPLETED) / 환불 상태 (일반적으로 COMPLETED)
     * @return the total refund amount / 총 환불 금액
     */
    @Query("""
        SELECT COALESCE(SUM(r.refundAmount), 0) FROM RefundTransaction r 
        JOIN r.payment p 
        WHERE p.merchant.id = :merchantId 
        AND r.requestedAt BETWEEN :startDate AND :endDate
        AND r.status = :status
    """)
    fun sumRefundAmountByMerchantIdAndDateRange(
        @Param("merchantId") merchantId: UUID,
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant,
        @Param("status") status: RefundStatus
    ): Long
    
    /**
     * Update refund status.
     * 환불 상태를 업데이트합니다.
     * 
     * @param id the refund ID / 환불 ID
     * @param status the new status / 새 상태
     * @param processedAt the processed timestamp / 처리 시간
     * @return the number of updated rows / 업데이트된 행 수
     */
    @Modifying
    @Query("""
        UPDATE RefundTransaction r 
        SET r.status = :status, r.processedAt = :processedAt 
        WHERE r.id = :id
    """)
    fun updateStatus(
        @Param("id") id: UUID, 
        @Param("status") status: RefundStatus,
        @Param("processedAt") processedAt: Instant
    ): Int
    
    /**
     * Find refunds needing synchronization to MongoDB.
     * MongoDB로 동기화가 필요한 환불을 조회합니다.
     * 
     * @param lastSyncTime the last synchronization timestamp / 마지막 동기화 시간
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated refund transactions / 페이지네이션된 환불 트랜잭션
     */
    @Query("""
        SELECT r FROM RefundTransaction r 
        WHERE r.updatedAt > :lastSyncTime 
        ORDER BY r.updatedAt ASC
    """)
    fun findRefundsForSync(
        @Param("lastSyncTime") lastSyncTime: Instant, 
        pageable: Pageable
    ): Page<RefundTransaction>
}
