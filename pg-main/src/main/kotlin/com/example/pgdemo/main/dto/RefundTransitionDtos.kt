package com.example.pgdemo.main.dto

import jakarta.validation.constraints.NotBlank

data class RefundFailRequest(
    @field:NotBlank(message = "failureReason is required")
    val failureReason: String
)
