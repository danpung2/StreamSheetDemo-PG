package com.example.pgdemo.admin.view

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class PaymentViewController {

    @GetMapping("/payments")
    fun payments(model: Model): String {
        model.addAttribute("pageTitle", "Payments")
        model.addAttribute(
            "payments",
            listOf(
                mapOf(
                    "id" to "PMT-24018",
                    "merchant" to "Nova Market",
                    "amount" to "\$1,240.00",
                    "method" to "Card",
                    "status" to "Captured",
                    "statusClass" to "success",
                    "createdAt" to "2026-02-02"
                ),
                mapOf(
                    "id" to "PMT-24017",
                    "merchant" to "Atlas Health",
                    "amount" to "\$840.00",
                    "method" to "Bank Transfer",
                    "status" to "Pending",
                    "statusClass" to "warning",
                    "createdAt" to "2026-02-02"
                ),
                mapOf(
                    "id" to "PMT-24016",
                    "merchant" to "Solace Mobility",
                    "amount" to "\$3,120.00",
                    "method" to "Card",
                    "status" to "Failed",
                    "statusClass" to "danger",
                    "createdAt" to "2026-02-01"
                )
            )
        )
        return "payments/list"
    }

    @GetMapping("/payments/{paymentId}")
    fun paymentDetail(@PathVariable paymentId: String, model: Model): String {
        model.addAttribute("pageTitle", "Payment Detail")
        model.addAttribute(
            "payment",
            mapOf(
                "id" to paymentId,
                "merchant" to "Nova Market",
                "amount" to "\$1,240.00",
                "method" to "Card",
                "customer" to "J. Rivera",
                "status" to "Captured",
                "statusClass" to "success",
                "events" to listOf(
                    mapOf("time" to "09:20", "title" to "Authorized", "note" to "Risk review passed"),
                    mapOf("time" to "09:24", "title" to "Captured", "note" to "Settlement queued"),
                    mapOf("time" to "09:28", "title" to "Completed", "note" to "Ledger updated")
                )
            )
        )
        return "payments/detail"
    }

    @GetMapping("/exports/payments")
    fun paymentExports(model: Model): String {
        model.addAttribute("pageTitle", "Exports")
        model.addAttribute(
            "exportJobs",
            listOf(
                mapOf(
                    "id" to "EXP-0402",
                    "range" to "2026-02-01 — 2026-02-02",
                    "requestedBy" to "J. Rivera",
                    "status" to "Queued",
                    "statusClass" to "warning",
                    "queuedAt" to "10m ago"
                ),
                mapOf(
                    "id" to "EXP-0401",
                    "range" to "2026-01-30 — 2026-01-31",
                    "requestedBy" to "N. Arora",
                    "status" to "Completed",
                    "statusClass" to "success",
                    "queuedAt" to "2h ago"
                )
            )
        )
        return "exports/payments"
    }
}
