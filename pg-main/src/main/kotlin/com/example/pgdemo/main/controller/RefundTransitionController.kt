package com.example.pgdemo.main.controller

import com.example.pgdemo.main.dto.RefundFailRequest
import com.example.pgdemo.main.dto.RefundResponse
import com.example.pgdemo.main.service.RefundTransitionService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/refunds")
class RefundTransitionController(
    private val refundTransitionService: RefundTransitionService
) {
    @PostMapping("/{id}/process")
    fun processRefund(@PathVariable id: UUID): RefundResponse {
        return refundTransitionService.processRefund(id)
    }

    @PostMapping("/{id}/complete")
    fun completeRefund(@PathVariable id: UUID): RefundResponse {
        return refundTransitionService.completeRefund(id)
    }

    @PostMapping("/{id}/fail")
    fun failRefund(@PathVariable id: UUID, @Valid @RequestBody request: RefundFailRequest): RefundResponse {
        return refundTransitionService.failRefund(id, request)
    }
}
