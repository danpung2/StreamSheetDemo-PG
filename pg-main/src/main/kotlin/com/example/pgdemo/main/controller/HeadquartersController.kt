package com.example.pgdemo.main.controller

import com.example.pgdemo.main.dto.HeadquartersRequest
import com.example.pgdemo.main.dto.HeadquartersResponse
import com.example.pgdemo.main.service.HeadquartersService
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
@RequestMapping("/api/v1/headquarters")
class HeadquartersController(
    private val headquartersService: HeadquartersService
) {
    @PostMapping
    fun createHeadquarters(@Valid @RequestBody request: HeadquartersRequest): ResponseEntity<HeadquartersResponse> {
        val response = headquartersService.createHeadquarters(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listHeadquarters(pageable: Pageable): Page<HeadquartersResponse> {
        return headquartersService.listHeadquarters(pageable)
    }

    @GetMapping("/{id}")
    fun getHeadquarters(@PathVariable id: UUID): HeadquartersResponse {
        return headquartersService.getHeadquarters(id)
    }
}
