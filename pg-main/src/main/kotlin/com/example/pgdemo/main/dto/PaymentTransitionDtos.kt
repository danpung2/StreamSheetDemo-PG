package com.example.pgdemo.main.dto

import jakarta.validation.constraints.NotBlank

data class PaymentFailRequest(
    @field:NotBlank(message = "failureReason is required")
    val failureReason: String
)
