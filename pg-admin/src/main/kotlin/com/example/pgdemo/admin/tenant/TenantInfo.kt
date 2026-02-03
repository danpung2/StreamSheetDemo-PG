package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import java.util.UUID

/**
 * 테넌트 정보 DTO
 * Tenant information Data Transfer Object
 *
 * NOTE: 현재 요청 컨텍스트의 테넌트 정보를 담는 불변 객체입니다.
 *       This immutable object holds tenant information for the current request context.
 */
data class TenantInfo(
    /**
     * 사용자 ID
     * User ID
     */
    val userId: UUID,

    /**
     * 테넌트 유형 (OPERATOR, HEADQUARTERS, MERCHANT)
     * Tenant type (OPERATOR, HEADQUARTERS, MERCHANT)
     */
    val tenantType: TenantType,

    /**
     * 테넌트 ID (본사 ID 또는 가맹점 ID, OPERATOR는 null)
     * Tenant ID (headquarters ID or merchant ID, null for OPERATOR)
     */
    val tenantId: UUID?,

    /**
     * 사용자 역할 (ADMIN, MANAGER, VIEWER)
     * User role (ADMIN, MANAGER, VIEWER)
     */
    val role: UserRole
) {
    /**
     * 운영자 권한인지 확인
     * Check if this is an operator tenant
     */
    fun isOperator(): Boolean = tenantType == TenantType.OPERATOR

    /**
     * 본사 권한인지 확인
     * Check if this is a headquarters tenant
     */
    fun isHeadquarters(): Boolean = tenantType == TenantType.HEADQUARTERS

    /**
     * 가맹점 권한인지 확인
     * Check if this is a merchant tenant
     */
    fun isMerchant(): Boolean = tenantType == TenantType.MERCHANT

    /**
     * 특정 본사에 대한 접근 권한이 있는지 확인
     * Check if the tenant has access to a specific headquarters
     *
     * @param headquartersId 본사 ID / Headquarters ID
     * @return 접근 가능 여부 / Whether access is allowed
     */
    fun canAccessHeadquarters(headquartersId: UUID): Boolean {
        return when (tenantType) {
            // 운영자는 모든 본사에 접근 가능
            // Operator can access all headquarters
            TenantType.OPERATOR -> true
            // 본사는 자신의 본사만 접근 가능
            // Headquarters can only access their own
            TenantType.HEADQUARTERS -> tenantId == headquartersId
            // 가맹점은 본사 직접 접근 불가
            // Merchant cannot directly access headquarters
            TenantType.MERCHANT -> false
        }
    }

    /**
     * 특정 가맹점에 대한 접근 권한이 있는지 확인
     * Check if the tenant has access to a specific merchant
     *
     * @param merchantId 가맹점 ID / Merchant ID
     * @param merchantHeadquartersId 가맹점이 속한 본사 ID / Headquarters ID the merchant belongs to
     * @return 접근 가능 여부 / Whether access is allowed
     */
    fun canAccessMerchant(merchantId: UUID, merchantHeadquartersId: UUID?): Boolean {
        return when (tenantType) {
            // 운영자는 모든 가맹점에 접근 가능
            // Operator can access all merchants
            TenantType.OPERATOR -> true
            // 본사는 자신에게 속한 가맹점만 접근 가능
            // Headquarters can only access merchants belonging to them
            TenantType.HEADQUARTERS -> tenantId == merchantHeadquartersId
            // 가맹점은 자신만 접근 가능
            // Merchant can only access themselves
            TenantType.MERCHANT -> tenantId == merchantId
        }
    }

    /**
     * 관리자 역할인지 확인
     * Check if the user has admin role
     */
    fun isAdmin(): Boolean = role == UserRole.ADMIN

    /**
     * 매니저 이상 역할인지 확인
     * Check if the user has manager or higher role
     */
    fun isManagerOrAbove(): Boolean = role == UserRole.ADMIN || role == UserRole.MANAGER
}
