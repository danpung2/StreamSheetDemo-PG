package com.example.pgdemo.admin.export

import com.example.pgdemo.common.domain.document.ExportJob
import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.ExportJobType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentExportJobCleanup(
    private val mongoTemplate: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *")
    fun cleanupOldJobs() {
        val cutoff = Instant.now().minus(Duration.ofDays(7))
        val criteria = Criteria.where("jobType").`is`(ExportJobType.PAYMENTS)
            .and("finishedAt").lt(cutoff)
            .and("status").`in`(ExportJobStatus.COMPLETED, ExportJobStatus.FAILED, ExportJobStatus.CANCELLED)

        val query = Query.query(criteria).limit(500)
        val jobs = mongoTemplate.find(query, ExportJob::class.java)
        if (jobs.isEmpty()) {
            return
        }

        jobs.forEach { job ->
            val output = job.outputLocation
            if (output != null) {
                runCatching { Files.deleteIfExists(Path.of(output)) }
                    .onFailure { ex ->
                        log.debug("Failed deleting export file: {}", output, ex)
                    }
            }
        }

        val ids = jobs.map { it.jobId }
        mongoTemplate.remove(
            Query.query(Criteria.where("_id").`in`(ids)),
            ExportJob::class.java
        )
    }
}
