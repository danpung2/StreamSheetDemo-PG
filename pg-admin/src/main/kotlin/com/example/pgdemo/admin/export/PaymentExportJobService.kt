package com.example.pgdemo.admin.export

import com.example.pgdemo.admin.tenant.TenantAccessDeniedException
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.admin.tenant.TenantPermissionMatrix
import com.example.pgdemo.common.domain.document.ExportJob
import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.ExportJobType
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.repository.ExportJobRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class PaymentExportJobService(
    private val exportJobRepository: ExportJobRepository,
    private val mongoTemplate: MongoTemplate,
    private val merchantRepository: MerchantRepository,
    private val tenantPermissionMatrix: TenantPermissionMatrix,
    private val exportService: ExportService
) {
    data class CreateRequest(
        val fromUtc: Instant,
        val toUtc: Instant,
        val headquartersId: UUID?,
        val merchantId: UUID?
    )

    fun createJob(request: CreateRequest, requestedBy: String): ExportJob {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireExport(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        require(request.toUtc.isAfter(request.fromUtc)) { "toUtc must be after fromUtc" }
        validateRange(tenantInfo.tenantType, request.fromUtc, request.toUtc)

        val resolvedHeadquartersId = resolveHeadquartersFilter(tenantInfo.tenantType, tenantInfo.tenantId, request.headquartersId)
        val resolvedMerchantId = resolveMerchantFilter(tenantInfo.tenantType, tenantInfo.tenantId, request.merchantId)

        validateMerchantTenantAccess(tenantInfo.tenantType, tenantInfo.tenantId, resolvedMerchantId, resolvedHeadquartersId)

        val jobId = UUID.randomUUID().toString()
        val now = Instant.now()
        val job = ExportJob(
            jobId = jobId,
            jobType = ExportJobType.PAYMENTS,
            tenantType = tenantInfo.tenantType,
            tenantId = tenantInfo.tenantId,
            userId = tenantInfo.userId,
            userRole = tenantInfo.role,
            headquartersId = resolvedHeadquartersId,
            merchantId = resolvedMerchantId,
            fromUtc = request.fromUtc,
            toUtc = request.toUtc,
            status = ExportJobStatus.QUEUED,
            progressLabel = "조회 준비",
            queuedAt = now,
            startedAt = null,
            finishedAt = null,
            requestedBy = requestedBy,
            errorSummary = null,
            outputLocation = null
        )
        return exportJobRepository.save(job)
    }

    fun getJob(jobId: String): ExportJob {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireRead(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        val job = exportJobRepository.findById(jobId)
            .orElseThrow { IllegalArgumentException("Export job not found") }
        require(job.jobType == ExportJobType.PAYMENTS) { "Export job type mismatch" }
        require(canAccessJob(tenantInfo.tenantType, tenantInfo.tenantId, job)) { "Export job access denied" }
        return job
    }

    fun listJobs(
        pageable: Pageable,
        fromUtc: Instant?,
        toUtc: Instant?,
        headquartersId: UUID?,
        merchantId: UUID?,
        status: ExportJobStatus?
    ): Page<ExportJob> {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireRead(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        val criteria = Criteria.where("jobType").`is`(ExportJobType.PAYMENTS)
        applyTenantCriteria(criteria, tenantInfo.tenantType, tenantInfo.tenantId)

        if (fromUtc != null) {
            criteria.and("toUtc").gte(fromUtc)
        }
        if (toUtc != null) {
            criteria.and("fromUtc").lt(toUtc)
        }
        if (status != null) {
            criteria.and("status").`is`(status)
        }

        when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> {
                headquartersId?.let { criteria.and("headquartersId").`is`(it) }
                merchantId?.let { criteria.and("merchantId").`is`(it) }
            }
            TenantType.HEADQUARTERS -> {
                merchantId?.let { criteria.and("merchantId").`is`(it) }
            }
            TenantType.MERCHANT -> Unit
        }

        val query = Query.query(criteria).with(pageable)
        val items = mongoTemplate.find(query, ExportJob::class.java)
        val count = mongoTemplate.count(Query.query(criteria), ExportJob::class.java)
        return PageImpl(items, pageable, count)
    }

    fun cancelQueuedJob(jobId: String): ExportJob {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireWrite(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        val existing = getJob(jobId)
        if (existing.status != ExportJobStatus.QUEUED) {
            return existing
        }

        val now = Instant.now()
        val query = Query.query(
            Criteria.where("_id").`is`(jobId)
                .and("status").`is`(ExportJobStatus.QUEUED)
        )
        val update = Update()
            .set("status", ExportJobStatus.CANCELLED)
            .set("progressLabel", "취소")
            .set("finishedAt", now)
        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            ExportJob::class.java
        ) ?: existing
    }

    fun claimNextQueuedJob(): ExportJob? {
        val query = Query.query(
            Criteria.where("jobType").`is`(ExportJobType.PAYMENTS)
                .and("status").`is`(ExportJobStatus.QUEUED)
        )
            .limit(1)
            .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "queuedAt"))

        val now = Instant.now()
        val update = Update()
            .set("status", ExportJobStatus.RUNNING)
            .set("progressLabel", "파일 생성")
            .set("startedAt", now)

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            ExportJob::class.java
        )
    }

    fun runExportJob(job: ExportJob, outputLocation: String) {
        val tenantInfo = com.example.pgdemo.admin.tenant.TenantInfo(
            userId = job.userId,
            tenantType = job.tenantType,
            tenantId = job.tenantId,
            role = job.userRole
        )
        TenantContext.set(tenantInfo)
        try {
            java.io.FileOutputStream(outputLocation).use { outputStream ->
                exportService.exportPayments(
                    fromUtc = job.fromUtc,
                    toUtcExclusive = job.toUtc,
                    outputStream = outputStream,
                    headquartersId = job.headquartersId,
                    merchantId = job.merchantId
                )
            }
        } finally {
            TenantContext.clear()
        }
    }

    fun markCompleted(jobId: String, outputLocation: String) {
        val now = Instant.now()
        val query = Query.query(Criteria.where("_id").`is`(jobId))
        val update = Update()
            .set("status", ExportJobStatus.COMPLETED)
            .set("progressLabel", "완료")
            .set("finishedAt", now)
            .set("outputLocation", outputLocation)
            .set("errorSummary", null)
        mongoTemplate.updateFirst(query, update, ExportJob::class.java)
    }

    fun markCleaning(jobId: String) {
        val query = Query.query(Criteria.where("_id").`is`(jobId))
        val update = Update().set("progressLabel", "정리")
        mongoTemplate.updateFirst(query, update, ExportJob::class.java)
    }

    fun markFailed(jobId: String, errorSummary: String) {
        val now = Instant.now()
        val query = Query.query(Criteria.where("_id").`is`(jobId))
        val update = Update()
            .set("status", ExportJobStatus.FAILED)
            .set("progressLabel", "실패")
            .set("finishedAt", now)
            .set("errorSummary", errorSummary.take(500))
        mongoTemplate.updateFirst(query, update, ExportJob::class.java)
    }

    private fun validateRange(tenantType: TenantType, fromUtc: Instant, toUtc: Instant) {
        val maxDays = when (tenantType) {
            TenantType.OPERATOR -> 90L
            TenantType.HEADQUARTERS, TenantType.MERCHANT -> 31L
        }
        val duration = Duration.between(fromUtc, toUtc)
        require(duration <= Duration.ofDays(maxDays)) { "date range must be within $maxDays days" }
    }

    private fun resolveHeadquartersFilter(tenantType: TenantType, tenantId: UUID?, requested: UUID?): UUID? {
        return when (tenantType) {
            TenantType.OPERATOR -> requested
            TenantType.HEADQUARTERS -> tenantId
            TenantType.MERCHANT -> null
        }
    }

    private fun resolveMerchantFilter(tenantType: TenantType, tenantId: UUID?, requested: UUID?): UUID? {
        return when (tenantType) {
            TenantType.OPERATOR, TenantType.HEADQUARTERS -> requested
            TenantType.MERCHANT -> tenantId
        }
    }

    private fun validateMerchantTenantAccess(
        tenantType: TenantType,
        tenantId: UUID?,
        merchantId: UUID?,
        headquartersId: UUID?
    ) {
        if (merchantId == null) {
            return
        }

        val merchant = merchantRepository.findById(merchantId).orElseThrow {
            IllegalArgumentException("Merchant not found")
        }
        val merchantHeadquartersId = merchant.headquarters?.id

        when (tenantType) {
            TenantType.OPERATOR -> {
                if (headquartersId != null && merchantHeadquartersId != headquartersId) {
                    throw TenantAccessDeniedException("Merchant is not under the selected headquarters")
                }
            }
            TenantType.HEADQUARTERS -> {
                if (tenantId == null) {
                    throw TenantAccessDeniedException("Headquarters tenant requires tenantId")
                }
                if (merchantHeadquartersId != tenantId) {
                    throw TenantAccessDeniedException("Headquarters cannot access other headquarters merchants")
                }
            }
            TenantType.MERCHANT -> {
                if (tenantId == null) {
                    throw TenantAccessDeniedException("Merchant tenant requires tenantId")
                }
                if (merchantId != tenantId) {
                    throw TenantAccessDeniedException("Merchant cannot access other merchants")
                }
            }
        }
    }

    private fun applyTenantCriteria(criteria: Criteria, tenantType: TenantType, tenantId: UUID?) {
        when (tenantType) {
            TenantType.OPERATOR -> Unit
            TenantType.HEADQUARTERS -> criteria.and("headquartersId").`is`(tenantId)
            TenantType.MERCHANT -> criteria.and("merchantId").`is`(tenantId)
        }
    }

    private fun canAccessJob(tenantType: TenantType, tenantId: UUID?, job: ExportJob): Boolean {
        return when (tenantType) {
            TenantType.OPERATOR -> true
            TenantType.HEADQUARTERS -> tenantId != null && job.headquartersId == tenantId
            TenantType.MERCHANT -> tenantId != null && job.merchantId == tenantId
        }
    }
}
