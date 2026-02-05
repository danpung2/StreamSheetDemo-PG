package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.Headquarters
import com.example.pgdemo.common.domain.enum.BusinessType
import com.example.pgdemo.common.domain.enum.StoreType
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.main.dto.MerchantRequest
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@DisplayName("가맹점 서비스 테스트")
class MerchantServiceTest {
    @Test
    @DisplayName("유효한 요청에 대해 가맹점 생성 및 응답 반환")
    fun `createMerchant returns response for valid request`() {
        val merchantRepository = Mockito.mock(MerchantRepository::class.java)
        val headquartersRepository = Mockito.mock(HeadquartersRepository::class.java)
        val service = MerchantService(merchantRepository, headquartersRepository)

        Mockito.`when`(merchantRepository.existsByMerchantCode("M-200"))
            .thenReturn(false)

        val headquartersId = UUID.randomUUID()
        Mockito.`when`(merchantRepository.existsByHeadquartersIdAndNameIgnoreCase(headquartersId, "Burger House"))
            .thenReturn(false)

        val hq = Headquarters().apply {
            id = headquartersId
            headquartersCode = "HQ-TEST"
            name = "Test HQ"
            businessNumber = "000-00-00000"
            contractType = "STANDARD"
            status = "ACTIVE"
        }
        Mockito.`when`(headquartersRepository.findById(headquartersId))
            .thenReturn(Optional.of(hq))

        val merchantId = UUID.randomUUID()
        Mockito.`when`(merchantRepository.save(Mockito.any(Merchant::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.getArgument<Merchant>(0)
                saved.id = merchantId
                saved
            }

        val response = service.createMerchant(
            MerchantRequest(
                merchantCode = "M-200",
                name = "Burger House",
                headquartersId = headquartersId,
                storeType = StoreType.FRANCHISE,
                businessType = BusinessType.RESTAURANT,
                contractStartDate = LocalDate.of(2024, 2, 1),
                contractEndDate = LocalDate.of(2026, 2, 1),
                storeNumber = 12
            )
        )

        assertEquals(merchantId, response.id)
        assertEquals("M-200", response.merchantCode)
        assertEquals("Burger House", response.name)
        assertEquals(StoreType.FRANCHISE, response.storeType)
        assertEquals(BusinessType.RESTAURANT, response.businessType)
        assertEquals("ACTIVE", response.status)
        assertEquals(LocalDate.of(2024, 2, 1), response.contractStartDate)
        assertEquals(LocalDate.of(2026, 2, 1), response.contractEndDate)
        assertEquals(12, response.storeNumber)
    }

    @Test
    @DisplayName("가맹점을 찾을 수 없을 때 예외 발생")
    fun `getMerchant throws when merchant not found`() {
        val merchantRepository = Mockito.mock(MerchantRepository::class.java)
        val headquartersRepository = Mockito.mock(HeadquartersRepository::class.java)
        val service = MerchantService(merchantRepository, headquartersRepository)

        val merchantId = UUID.randomUUID()
        Mockito.`when`(merchantRepository.findById(merchantId))
            .thenReturn(Optional.empty())

        val exception = assertThrows(ResourceNotFoundException::class.java) {
            service.getMerchant(merchantId)
        }

        assertEquals("Merchant not found", exception.message)
    }

    @Test
    @DisplayName("StoreType 누락 시 예외 발생")
    fun `createMerchant throws when storeType missing`() {
        val merchantRepository = Mockito.mock(MerchantRepository::class.java)
        val headquartersRepository = Mockito.mock(HeadquartersRepository::class.java)
        val service = MerchantService(merchantRepository, headquartersRepository)

        Mockito.`when`(merchantRepository.existsByMerchantCode("M-201"))
            .thenReturn(false)

        val headquartersId = UUID.randomUUID()
        Mockito.`when`(merchantRepository.existsByHeadquartersIdAndNameIgnoreCase(headquartersId, "Bakery"))
            .thenReturn(false)

        val hq = Headquarters().apply {
            id = headquartersId
            headquartersCode = "HQ-TEST"
            name = "Test HQ"
            businessNumber = "000-00-00000"
            contractType = "STANDARD"
            status = "ACTIVE"
        }
        Mockito.`when`(headquartersRepository.findById(headquartersId))
            .thenReturn(Optional.of(hq))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.createMerchant(
                MerchantRequest(
                    merchantCode = "M-201",
                    name = "Bakery",
                    headquartersId = headquartersId,
                    storeType = null,
                    businessType = BusinessType.RETAIL,
                    contractStartDate = LocalDate.of(2024, 3, 1),
                    contractEndDate = null,
                    storeNumber = null
                )
            )
        }

        assertEquals("storeType is required", exception.message)
    }
}
