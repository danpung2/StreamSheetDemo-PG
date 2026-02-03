package com.example.pgdemo.admin.view

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class DashboardController {

    @GetMapping("", "/dashboard")
    fun dashboard(model: Model): String {
        model.addAttribute("pageTitle", "Dashboard")
        model.addAttribute(
            "kpis",
            mapOf(
                "totalVolume" to "\$12.4M",
                "successfulPayments" to "4,291",
                "disputes" to "12",
                "pendingReviews" to "7"
            )
        )
        model.addAttribute(
            "recentPayments",
            listOf(
                mapOf(
                    "id" to "PMT-24018",
                    "merchant" to "Nova Market",
                    "amount" to "\$1,240.00",
                    "status" to "Captured",
                    "statusClass" to "success",
                    "updatedAt" to "2m ago"
                ),
                mapOf(
                    "id" to "PMT-24017",
                    "merchant" to "Atlas Health",
                    "amount" to "\$840.00",
                    "status" to "Authorized",
                    "statusClass" to "warning",
                    "updatedAt" to "8m ago"
                ),
                mapOf(
                    "id" to "PMT-24016",
                    "merchant" to "Solace Mobility",
                    "amount" to "\$3,120.00",
                    "status" to "Captured",
                    "statusClass" to "success",
                    "updatedAt" to "21m ago"
                )
            )
        )
        return "dashboard"
    }
}
