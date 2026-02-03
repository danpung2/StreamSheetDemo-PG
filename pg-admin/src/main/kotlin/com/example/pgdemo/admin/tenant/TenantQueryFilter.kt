package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import java.util.UUID

/**
 * 테넌트 기반 쿼리 필터 헬퍼
 * Tenant-based query filter helper
 *
 * NOTE: Repository 쿼리에서 테넌트 필터링을 적용하기 위한 유틸리티입니다.
 *       Utility for applying tenant filtering in repository queries.
 */
object TenantQueryFilter {

    /**
     * 본사 ID 필터 조건 생성
     * Generate headquarters ID filter condition
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @return 필터링할 본사 ID 목록, null이면 모든 본사 / List of headquarters IDs to filter, null means all
     */
    fun getHeadquartersFilter(tenantInfo: TenantInfo): UUID? {
        return when (tenantInfo.tenantType) {
            // 운영자는 모든 본사 조회 가능
            // Operator can query all headquarters
            TenantType.OPERATOR -> null
            // 본사는 자신의 본사만 조회 가능
            // Headquarters can only query their own
            TenantType.HEADQUARTERS -> tenantInfo.tenantId
            // 가맹점은 본사 조회 불가 (연결된 본사 정보는 별도 로직 필요)
            // Merchant cannot query headquarters (linked HQ needs separate logic)
            TenantType.MERCHANT -> null
        }
    }

    /**
     * 가맹점 조회 시 본사 ID 필터 조건 생성
     * Generate headquarters ID filter for merchant queries
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @return 필터링할 본사 ID, null이면 본사 필터 없음 / Headquarters ID to filter, null means no HQ filter
     */
    fun getMerchantHeadquartersFilter(tenantInfo: TenantInfo): UUID? {
        return when (tenantInfo.tenantType) {
            // 운영자는 모든 가맹점 조회 가능
            // Operator can query all merchants
            TenantType.OPERATOR -> null
            // 본사는 자신에게 속한 가맹점만 조회 가능
            // Headquarters can only query merchants belonging to them
            TenantType.HEADQUARTERS -> tenantInfo.tenantId
            // 가맹점은 자신만 조회 (별도 merchantId 필터 필요)
            // Merchant can only query themselves (separate merchantId filter needed)
            TenantType.MERCHANT -> null
        }
    }

    /**
     * 가맹점 ID 필터 조건 생성
     * Generate merchant ID filter condition
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @return 필터링할 가맹점 ID, null이면 가맹점 ID 필터 없음 / Merchant ID to filter, null means no merchant ID filter
     */
    fun getMerchantFilter(tenantInfo: TenantInfo): UUID? {
        return when (tenantInfo.tenantType) {
            // 운영자/본사는 별도 필터 없음 (본사 필터로 처리)
            // Operator/Headquarters have no separate filter (handled by HQ filter)
            TenantType.OPERATOR -> null
            TenantType.HEADQUARTERS -> null
            // 가맹점은 자신만 조회 가능
            // Merchant can only query themselves
            TenantType.MERCHANT -> tenantInfo.tenantId
        }
    }

    /**
     * 결제/환불 조회 시 필터 정보 생성
     * Generate filter information for payment/refund queries
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @return 테넌트 조회 필터 / Tenant query filter
     */
    fun getTransactionFilter(tenantInfo: TenantInfo): TransactionFilter {
        return when (tenantInfo.tenantType) {
            // 운영자는 모든 거래 조회 가능
            // Operator can query all transactions
            TenantType.OPERATOR -> TransactionFilter(
                headquartersId = null,
                merchantId = null,
                filterType = FilterType.ALL
            )
            // 본사는 자신에게 속한 가맹점의 거래만 조회 가능
            // Headquarters can only query transactions of their merchants
            TenantType.HEADQUARTERS -> TransactionFilter(
                headquartersId = tenantInfo.tenantId,
                merchantId = null,
                filterType = FilterType.BY_HEADQUARTERS
            )
            // 가맹점은 자신의 거래만 조회 가능
            // Merchant can only query their own transactions
            TenantType.MERCHANT -> TransactionFilter(
                headquartersId = null,
                merchantId = tenantInfo.tenantId,
                filterType = FilterType.BY_MERCHANT
            )
        }
    }
}

/**
 * 거래 조회 필터
 * Transaction query filter
 */
data class TransactionFilter(
    /**
     * 본사 ID 필터 (null이면 필터 없음)
     * Headquarters ID filter (null means no filter)
     */
    val headquartersId: UUID?,
    
    /**
     * 가맹점 ID 필터 (null이면 필터 없음)
     * Merchant ID filter (null means no filter)
     */
    val merchantId: UUID?,
    
    /**
     * 필터 유형
     * Filter type
     */
    val filterType: FilterType
)

/**
 * 필터 유형
 * Filter type
 */
enum class FilterType {
    /** 모든 데이터 조회 / Query all data */
    ALL,
    /** 본사별 필터링 / Filter by headquarters */
    BY_HEADQUARTERS,
    /** 가맹점별 필터링 / Filter by merchant */
    BY_MERCHANT
}
