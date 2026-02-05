package com.example.pgdemo.main.dto

import java.util.UUID
import jakarta.validation.constraints.NotBlank

data class HeadquartersResponse(
    val id: UUID,
    val headquartersCode: String,
    val name: String,
    val status: String
)

data class HeadquartersRequest(
    @field:NotBlank(message = "headquartersCode is required")
    val headquartersCode: String,
    @field:NotBlank(message = "name is required")
    val name: String,
    @field:NotBlank(message = "businessNumber is required")
    val businessNumber: String,
    @field:NotBlank(message = "contractType is required")
    val contractType: String,
    val status: String?
)
