package com.example.pgdemo.main.controller

import com.example.pgdemo.main.batch.PaymentExportViewSyncStateStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/sync/payment-export-view")
class PaymentExportViewSyncController(
    private val syncStateStore: PaymentExportViewSyncStateStore
) {
    data class ResetResponse(
        val status: String
    )

    @PostMapping("/reset")
    fun reset(@RequestParam(name = "drop", defaultValue = "true") drop: Boolean): ResponseEntity<ResetResponse> {
        syncStateStore.reset(dropViewCollection = drop)
        return ResponseEntity.ok(ResetResponse(status = "OK"))
    }
}
