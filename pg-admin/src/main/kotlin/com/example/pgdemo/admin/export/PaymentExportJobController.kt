package com.example.pgdemo.admin.export

import com.example.pgdemo.common.domain.enum.ExportJobStatus
import jakarta.servlet.http.HttpServletResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import com.example.pgdemo.admin.security.RequestedByResolver

@Controller
@RequestMapping("/admin/exports/payments/jobs")
class PaymentExportJobController(
    private val jobService: PaymentExportJobService
) {

    private val displayZone = ZoneId.systemDefault()
    data class CreateJobRequest(
        val fromUtc: Instant,
        val toUtc: Instant,
        val headquartersId: UUID?,
        val merchantId: UUID?,
        val transactionStatus: String?
    )

    data class CreateJobResponse(
        val jobId: String
    )

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun createJobForm(
        @RequestParam("fromUtc") fromUtc: String,
        @RequestParam("toUtc") toUtc: String,
        @RequestParam("headquartersId", required = false) headquartersId: String?,
        @RequestParam("merchantId", required = false) merchantId: String?,
        @RequestParam("transactionStatus", required = false) transactionStatus: String?
    ): String {
        val parsedFromUtc = LocalDateTime.parse(fromUtc).atZone(displayZone).toInstant()
        val parsedToUtc = LocalDateTime.parse(toUtc).atZone(displayZone).toInstant()
        val parsedHeadquartersId = headquartersId?.takeIf { it.isNotBlank() }?.let(UUID::fromString)
        val parsedMerchantId = merchantId?.takeIf { it.isNotBlank() }?.let(UUID::fromString)
        val requestedBy = RequestedByResolver.currentLabel()
        jobService.createJob(
            PaymentExportJobService.CreateRequest(
                fromUtc = parsedFromUtc,
                toUtc = parsedToUtc,
                headquartersId = parsedHeadquartersId,
                merchantId = parsedMerchantId,
                transactionStatus = transactionStatus?.trim()?.takeIf { it.isNotBlank() }
            ),
            requestedBy = requestedBy
        )
        return "redirect:/admin/exports/payments"
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun createJobApi(@RequestBody request: CreateJobRequest): CreateJobResponse {
        val requestedBy = RequestedByResolver.currentLabel()
        val job = jobService.createJob(
            PaymentExportJobService.CreateRequest(
                fromUtc = request.fromUtc,
                toUtc = request.toUtc,
                headquartersId = request.headquartersId,
                merchantId = request.merchantId,
                transactionStatus = request.transactionStatus?.trim()?.takeIf { it.isNotBlank() }
            ),
            requestedBy = requestedBy
        )
        return CreateJobResponse(jobId = job.jobId)
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun listJobs(
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        @RequestParam(name = "fromUtc", required = false) fromUtc: Instant?,
        @RequestParam(name = "toUtc", required = false) toUtc: Instant?,
        @RequestParam(name = "headquartersId", required = false) headquartersId: UUID?,
        @RequestParam(name = "merchantId", required = false) merchantId: UUID?,
        @RequestParam(name = "status", required = false) status: ExportJobStatus?
    ): Page<com.example.pgdemo.common.domain.document.ExportJob> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        return jobService.listJobs(pageable, fromUtc, toUtc, headquartersId, merchantId, status)
    }

    @GetMapping("/{jobId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun getJob(@PathVariable jobId: String): com.example.pgdemo.common.domain.document.ExportJob {
        return jobService.getJob(jobId)
    }

    @PostMapping("/{jobId}/cancel", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun cancelJob(@PathVariable jobId: String): String {
        jobService.cancelQueuedJob(jobId)
        return "redirect:/admin/exports/payments"
    }

    @GetMapping("/{jobId}/download")
    fun download(@PathVariable jobId: String, response: HttpServletResponse) {
        val job = jobService.getJob(jobId)
        require(job.status == ExportJobStatus.COMPLETED) { "Export job is not completed" }
        val outputLocation = job.outputLocation ?: throw IllegalStateException("Export output is missing")
        val filePath = Path.of(outputLocation)
        require(Files.exists(filePath)) { "Export file not found" }

        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(displayZone)
        val filename = "transactions_export_${formatter.format(job.fromUtc)}_${formatter.format(job.toUtc)}.xlsx"
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=\"$filename\"")

        Files.newInputStream(filePath).use { input ->
            response.outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }
}
