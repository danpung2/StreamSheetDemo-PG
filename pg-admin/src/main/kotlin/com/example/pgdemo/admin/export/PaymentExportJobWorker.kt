package com.example.pgdemo.admin.export

import com.example.pgdemo.common.domain.enum.ExportJobStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentExportJobWorker(
    private val jobService: PaymentExportJobService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val exportDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "pgdemo-exports")

    @Scheduled(fixedDelay = 2000)
    fun processOne() {
        val job = jobService.claimNextQueuedJob() ?: return

        try {
            Files.createDirectories(exportDir)
            val outputPath = exportDir.resolve("payments_export_${job.jobId}.xlsx")
            jobService.runExportJob(job, outputPath.toString())
            jobService.markCleaning(job.jobId)
            jobService.markCompleted(job.jobId, outputPath.toString())
        } catch (ex: Exception) {
            log.warn("Export job failed: jobId={}", job.jobId, ex)
            jobService.markFailed(job.jobId, ex.message ?: ex.javaClass.simpleName)
        }
    }
}
