package com.example.pgdemo.main.dto

import jakarta.validation.constraints.Min

data class SeedBootstrapRequest(
    @field:Min(1)
    val headquartersCount: Int = 1,
    @field:Min(1)
    val merchantsPerHeadquarters: Int = 10,
    val seed: Int? = null
)

data class SeedBootstrapResponse(
    val headquarters: List<HeadquartersResponse>,
    val merchants: List<MerchantResponse>
)
