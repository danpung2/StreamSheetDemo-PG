package com.example.pgdemo.admin.export

import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.example.pgdemo.admin.security.RequestedByResolver
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/admin/exports")
class ExportController(
    private val exportService: ExportService,
    private val exportHistoryStore: ExportHistoryStore
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val demoAuthority = SimpleGrantedAuthority("ROLE_DEMO")

    @GetMapping("/payments/download")
    fun downloadPayments(
        @RequestParam("startDate") startDate: String,
        @RequestParam("endDate") endDate: String,
        @RequestParam("headquartersId", required = false) headquartersId: String?,
        @RequestParam("merchantId", required = false) merchantId: String?,
        response: HttpServletResponse
    ) {
        val parsedStartDate = LocalDate.parse(startDate, dateFormatter)
        val parsedEndDate = LocalDate.parse(endDate, dateFormatter)
        require(!parsedEndDate.isBefore(parsedStartDate)) {
            "endDate must be on or after startDate"
        }

        if (isDemoSession()) {
            val maxDays = 7L
            val days = ChronoUnit.DAYS.between(parsedStartDate, parsedEndDate) + 1
            require(days in 1..maxDays) {
                "Demo export is limited to $maxDays days"
            }
        }

        val headquartersUuid = headquartersId?.let(UUID::fromString)
        val merchantUuid = merchantId?.let(UUID::fromString)

        val filename = "payment_export_${parsedStartDate}_${parsedEndDate}.xlsx"
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=\"$filename\"")

        val requestedBy = RequestedByResolver.currentLabel()
        val range = "$parsedStartDate — $parsedEndDate"
        val queuedAt = Instant.now()

        response.outputStream.use { outputStream ->
            exportService.exportPayments(
                startDate = parsedStartDate,
                endDate = parsedEndDate,
                outputStream = outputStream,
                headquartersId = headquartersUuid,
                merchantId = merchantUuid
            )
        }

        exportHistoryStore.recordPaymentExport(
            range = range,
            requestedBy = requestedBy,
            status = "Completed",
            statusClass = "success",
            queuedAt = queuedAt,
            downloadFilename = filename
        )
    }

    private fun isDemoSession(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.contains(demoAuthority)
    }
}
