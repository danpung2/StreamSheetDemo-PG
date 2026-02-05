package com.example.pgdemo.main.dto

import com.example.pgdemo.common.domain.enum.BusinessType
import com.example.pgdemo.common.domain.enum.StoreType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.UUID

data class MerchantRequest(
    @field:NotBlank(message = "merchantCode is required")
    val merchantCode: String,
    @field:NotBlank(message = "name is required")
    val name: String,
    @field:NotNull(message = "headquartersId is required")
    val headquartersId: UUID?,
    @field:NotNull(message = "storeType is required")
    val storeType: StoreType?,
    @field:NotNull(message = "businessType is required")
    val businessType: BusinessType?,
    @field:NotNull(message = "contractStartDate is required")
    val contractStartDate: LocalDate?,
    val contractEndDate: LocalDate?,
    val storeNumber: Int?
)

data class MerchantResponse(
    val id: UUID,
    val merchantCode: String,
    val name: String,
    val storeType: StoreType,
    val businessType: BusinessType,
    val status: String,
    val contractStartDate: LocalDate,
    val contractEndDate: LocalDate?,
    val storeNumber: Int?,
    val headquartersId: UUID?
)
