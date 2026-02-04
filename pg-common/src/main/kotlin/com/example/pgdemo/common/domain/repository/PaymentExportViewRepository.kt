package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.document.PaymentExportView
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for PaymentExportView MongoDB document.
 * 결제 내보내기 View MongoDB 문서에 대한 Repository.
 * 
 * Used for StreamSheet Excel export operations.
 * StreamSheet 엑셀 내보내기 작업에 사용됩니다.
 */
@Repository
interface PaymentExportViewRepository : MongoRepository<PaymentExportView, UUID> {
    
    // 트랜잭션 ID로 조회 / Find by transaction ID
    fun findByTransactionId(transactionId: UUID): PaymentExportView?
    
    // 본사별 조회 / Find by headquarters
    fun findByHeadquartersId(headquartersId: UUID, pageable: Pageable): Page<PaymentExportView>
    
    // 본사 코드별 조회 / Find by headquarters code
    fun findByHeadquartersCode(headquartersCode: String, pageable: Pageable): Page<PaymentExportView>
    
    // 업체별 조회 / Find by merchant
    fun findByMerchantId(merchantId: UUID, pageable: Pageable): Page<PaymentExportView>
    
    // 업체 코드별 조회 / Find by merchant code
    fun findByMerchantCode(merchantCode: String, pageable: Pageable): Page<PaymentExportView>
    
    // 결제 상태별 조회 / Find by payment status
    fun findByPaymentStatus(paymentStatus: String, pageable: Pageable): Page<PaymentExportView>
    
    // 기간별 조회 / Find by date range
    fun findByPaymentDateBetween(
        startDate: Instant, 
        endDate: Instant, 
        pageable: Pageable
    ): Page<PaymentExportView>
    
    // 본사 + 기간별 조회 (내보내기용) / Find by headquarters and date range (for export)
    fun findByHeadquartersIdAndPaymentDateBetween(
        headquartersId: UUID,
        startDate: Instant,
        endDate: Instant,
        pageable: Pageable
    ): Page<PaymentExportView>
    
    // 업체 + 기간별 조회 / Find by merchant and date range
    fun findByMerchantIdAndPaymentDateBetween(
        merchantId: UUID,
        startDate: Instant,
        endDate: Instant,
        pageable: Pageable
    ): Page<PaymentExportView>
    
    // 내보내기 기간 코드별 조회 / Find by export period
    fun findByExportPeriod(exportPeriod: String, pageable: Pageable): Page<PaymentExportView>
    
    // 본사별 건수 조회 / Count by headquarters
    fun countByHeadquartersId(headquartersId: UUID): Long
    
    // 업체별 건수 조회 / Count by merchant
    fun countByMerchantId(merchantId: UUID): Long
    
    // 환불 포함 데이터 조회 / Find with refund data
    @Query("{ 'refundId': { \$ne: null } }")
    fun findWithRefund(pageable: Pageable): Page<PaymentExportView>
    
    // 트랜잭션 ID 존재 여부 확인 / Check if transaction exists
    fun existsByTransactionId(transactionId: UUID): Boolean
    
    // 트랜잭션 ID로 삭제 / Delete by transaction ID
    fun deleteByTransactionId(transactionId: UUID)
    
    // 동기화 시간 이후 데이터 조회 / Find synced after
    fun findBySyncedAtAfter(syncedAt: Instant, pageable: Pageable): Page<PaymentExportView>
}
