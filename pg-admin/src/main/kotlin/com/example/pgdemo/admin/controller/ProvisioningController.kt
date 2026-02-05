package com.example.pgdemo.admin.controller

import com.example.pgdemo.admin.dto.CreateAdminUserRequest
import com.example.pgdemo.admin.dto.CreateAdminUserResponse
import com.example.pgdemo.admin.dto.CreateApiKeyRequest
import com.example.pgdemo.admin.dto.CreateApiKeyResponse
import com.example.pgdemo.admin.service.ProvisioningService
import com.example.pgdemo.admin.tenant.AdminOnly
import com.example.pgdemo.admin.tenant.OperatorOnly
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/provisioning")
@AdminOnly
class ProvisioningController(
    private val provisioningService: ProvisioningService
) {
    @PostMapping("/users")
    fun createUser(@Valid @RequestBody request: CreateAdminUserRequest): CreateAdminUserResponse {
        return provisioningService.createAdminUser(request)
    }

    @PostMapping("/api-keys")
    @OperatorOnly
    fun createApiKey(@RequestBody request: CreateApiKeyRequest): CreateApiKeyResponse {
        return provisioningService.createApiKey(request)
    }
}
