package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.main.dto.MerchantRequest
import com.example.pgdemo.main.dto.MerchantResponse
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MerchantService(
    private val merchantRepository: MerchantRepository,
    private val headquartersRepository: HeadquartersRepository
) {
    @Transactional
    fun createMerchant(request: MerchantRequest): MerchantResponse {
        if (merchantRepository.existsByMerchantCode(request.merchantCode)) {
            throw IllegalArgumentException("merchantCode already exists")
        }

        val merchant = Merchant().apply {
            merchantCode = request.merchantCode
            name = request.name
            storeType = request.storeType ?: throw IllegalArgumentException("storeType is required")
            businessType = request.businessType ?: throw IllegalArgumentException("businessType is required")
            contractStartDate = request.contractStartDate
                ?: throw IllegalArgumentException("contractStartDate is required")
            contractEndDate = request.contractEndDate
            storeNumber = request.storeNumber
        }

        merchant.headquarters = inferHeadquartersFromName(merchant.name)

        return merchantRepository.save(merchant).toResponse()
    }

    @Transactional
    fun backfillHeadquartersForOrphanMerchants(limit: Int = 500) {
        val page = merchantRepository.findByHeadquartersIsNull(org.springframework.data.domain.PageRequest.of(0, limit))
        if (page.isEmpty) {
            return
        }

        page.content.forEach { merchant ->
            val name = merchant.name
            val hq = inferHeadquartersFromName(name)
            if (hq != null) {
                merchant.headquarters = hq
                merchantRepository.save(merchant)
            }
        }
    }

    private fun inferHeadquartersFromName(name: String): com.example.pgdemo.common.domain.entity.Headquarters? {
        val brand = name.trim().split(Regex("\\s+"), limit = 2).firstOrNull()?.trim().orEmpty()
        if (brand.isBlank()) {
            return null
        }

        val candidates = headquartersRepository.findByName(brand)
            .filter { it.status == "ACTIVE" && it.id != null }
        return if (candidates.size == 1) candidates.first() else null
    }

    @Transactional(readOnly = true)
    fun getMerchant(id: UUID): MerchantResponse {
        val merchant = merchantRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Merchant not found") }
        return merchant.toResponse()
    }

    @Transactional(readOnly = true)
    fun listMerchants(pageable: Pageable): Page<MerchantResponse> {
        return merchantRepository.findAll(pageable).map { it.toResponse() }
    }
}

private fun Merchant.toResponse(): MerchantResponse {
    val merchantId = id ?: throw IllegalStateException("Merchant id is missing")

    return MerchantResponse(
        id = merchantId,
        merchantCode = merchantCode,
        name = name,
        storeType = storeType,
        businessType = businessType,
        status = status,
        contractStartDate = contractStartDate,
        contractEndDate = contractEndDate,
        storeNumber = storeNumber,
        headquartersId = headquarters?.id
    )
}
