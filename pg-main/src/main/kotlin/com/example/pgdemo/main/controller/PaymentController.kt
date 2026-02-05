package com.example.pgdemo.main.controller

import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.main.dto.PaymentFailRequest
import com.example.pgdemo.main.dto.PaymentRequest
import com.example.pgdemo.main.dto.PaymentResponse
import com.example.pgdemo.main.service.PaymentService
import com.example.pgdemo.main.service.PaymentTransitionService
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val paymentTransitionService: PaymentTransitionService
) {
    @PostMapping
    fun requestPayment(@Valid @RequestBody request: PaymentRequest): ResponseEntity<PaymentResponse> {
        val response = paymentService.requestPayment(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listPayments(
        @RequestParam(name = "from", required = false) fromUtc: Instant?,
        @RequestParam(name = "to", required = false) toUtc: Instant?,
        @RequestParam(name = "headquartersId", required = false) headquartersId: UUID?,
        @RequestParam(name = "merchantId", required = false) merchantId: UUID?,
        @RequestParam(name = "status", required = false) status: PaymentStatus?,
        pageable: Pageable
    ): Page<PaymentResponse> {
        return paymentService.listPayments(
            pageable = pageable,
            merchantId = merchantId,
            headquartersId = headquartersId,
            status = status,
            fromUtc = fromUtc,
            toUtc = toUtc
        )
    }

    @GetMapping("/{id}")
    fun getPayment(@PathVariable id: UUID): PaymentResponse {
        return paymentService.getPayment(id)
    }

    @PostMapping("/{id}/process")
    fun processPayment(@PathVariable id: UUID): PaymentResponse {
        return paymentTransitionService.processPayment(id)
    }

    @PostMapping("/{id}/complete")
    fun completePayment(@PathVariable id: UUID): PaymentResponse {
        return paymentTransitionService.completePayment(id)
    }

    @PostMapping("/{id}/cancel")
    fun cancelPayment(@PathVariable id: UUID): PaymentResponse {
        return paymentTransitionService.cancelPayment(id)
    }

    @PostMapping("/{id}/fail")
    fun failPayment(@PathVariable id: UUID, @Valid @RequestBody request: PaymentFailRequest): PaymentResponse {
        return paymentTransitionService.failPayment(id, request)
    }
}
