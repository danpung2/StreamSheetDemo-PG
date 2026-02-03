package com.example.pgdemo.main.controller

import com.example.pgdemo.main.dto.MerchantRequest
import com.example.pgdemo.main.dto.MerchantResponse
import com.example.pgdemo.main.service.MerchantService
import jakarta.validation.Valid
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/merchants")
class MerchantController(
    private val merchantService: MerchantService
) {
    @PostMapping
    fun createMerchant(@Valid @RequestBody request: MerchantRequest): ResponseEntity<MerchantResponse> {
        val response = merchantService.createMerchant(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listMerchants(pageable: Pageable): Page<MerchantResponse> {
        return merchantService.listMerchants(pageable)
    }

    @GetMapping("/{id}")
    fun getMerchant(@PathVariable id: UUID): MerchantResponse {
        return merchantService.getMerchant(id)
    }
}
