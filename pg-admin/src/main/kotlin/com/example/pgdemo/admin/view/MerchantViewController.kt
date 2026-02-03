package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.client.PgMainApiClient
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.RestClientException

@Controller
@RequestMapping("/admin")
class MerchantViewController(
    private val pgMainApiClient: PgMainApiClient
) {

    @GetMapping("/merchants")
    fun merchants(model: Model): String {
        model.addAttribute("pageTitle", "Merchants")
        var loadError = false
        val merchantResponses = try {
            pgMainApiClient.listMerchants(0, 50)?.content.orEmpty()
        } catch (ex: RestClientException) {
            loadError = true
            emptyList()
        }
        model.addAttribute(
            "merchants",
            merchantResponses.map { merchant ->
                mapOf(
                    "name" to merchant.name,
                    "industry" to formatLabel(merchant.businessType.name),
                    "risk" to formatLabel(merchant.status),
                    "payments" to "0",
                    "owner" to "-"
                )
            }
        )
        if (loadError) {
            model.addAttribute("loadError", true)
        }
        return "merchants/list"
    }

    private fun formatLabel(value: String): String {
        return value.lowercase()
            .split("_", " ")
            .joinToString(" ") { segment ->
                segment.replaceFirstChar { char -> char.uppercase() }
            }
    }
}
