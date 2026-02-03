package com.example.pgdemo.admin.view

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class MerchantViewController {

    @GetMapping("/merchants")
    fun merchants(model: Model): String {
        model.addAttribute("pageTitle", "Merchants")
        model.addAttribute(
            "merchants",
            listOf(
                mapOf(
                    "name" to "Nova Market",
                    "industry" to "Retail",
                    "risk" to "Moderate",
                    "payments" to "124",
                    "owner" to "N. Arora"
                ),
                mapOf(
                    "name" to "Atlas Health",
                    "industry" to "Healthcare",
                    "risk" to "Low",
                    "payments" to "62",
                    "owner" to "J. Rivera"
                ),
                mapOf(
                    "name" to "Solace Mobility",
                    "industry" to "Transport",
                    "risk" to "Elevated",
                    "payments" to "98",
                    "owner" to "K. Chen"
                )
            )
        )
        return "merchants/list"
    }
}
