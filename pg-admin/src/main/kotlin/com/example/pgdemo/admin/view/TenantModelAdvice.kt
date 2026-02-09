package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 전역 모델 속성 설정
 * Global model attribute advice
 *
 * NOTE: 모든 뷰 컨트롤러에서 공통으로 사용할 모델 속성을 설정합니다.
 *       특히 사이드바 등에 표시될 테넌트 이름을 관리합니다.
 *       Manage common model attributes for all view controllers, 
 *       especially the tenant name displayed in the sidebar.
 */
@ControllerAdvice(basePackages = ["com.example.pgdemo.admin.view"])
class TenantModelAdvice(
    private val headquartersRepository: HeadquartersRepository,
    private val merchantRepository: MerchantRepository
) {
    private val log = LoggerFactory.getLogger(TenantModelAdvice::class.java)

    /**
     * 현재 세션의 테넌트 이름을 모델에 추가합니다.
     * Adds the current session's tenant name to the model.
     *
     * @return 테넌트 이름 (기본값: Global) / Tenant name (default: Global)
     */
    @ModelAttribute("tenantName")
    fun tenantName(): String {
        val tenantInfo = TenantContext.get() ?: return "Global"
        
        return try {
            when (tenantInfo.tenantType) {
                TenantType.OPERATOR -> "Global"
                TenantType.HEADQUARTERS -> {
                    tenantInfo.tenantId?.let { id ->
                        headquartersRepository.findById(id).map { it.name }.orElse("Unknown HQ")
                    } ?: "Unknown HQ"
                }
                TenantType.MERCHANT -> {
                    tenantInfo.tenantId?.let { id ->
                        merchantRepository.findById(id).map { it.name }.orElse("Unknown Merchant")
                    } ?: "Unknown Merchant"
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to fetch tenant name for {}:{}", tenantInfo.tenantType, tenantInfo.tenantId, ex)
            "Global"
        }
    }
}
