package com.example.pgdemo.main.controller

import com.example.pgdemo.main.dto.SeedBootstrapRequest
import com.example.pgdemo.main.dto.SeedBootstrapResponse
import com.example.pgdemo.main.service.InternalSeedService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/seed")
class InternalSeedController(
    private val internalSeedService: InternalSeedService
) {
    @PostMapping("/bootstrap")
    fun bootstrap(@Valid @RequestBody request: SeedBootstrapRequest): ResponseEntity<SeedBootstrapResponse> {
        return ResponseEntity.ok(internalSeedService.bootstrap(request))
    }
}
