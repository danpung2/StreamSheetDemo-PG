package com.example.pgdemo.main.controller

import com.example.pgdemo.main.dto.RefundRequest
import com.example.pgdemo.main.dto.RefundResponse
import com.example.pgdemo.main.service.RefundService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments/{id}/refund")
class RefundController(
    private val refundService: RefundService
) {
    @PostMapping
    fun requestRefund(
        @PathVariable("id") paymentId: UUID,
        @Valid @RequestBody request: RefundRequest
    ): ResponseEntity<RefundResponse> {
        val response = refundService.requestRefund(paymentId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
