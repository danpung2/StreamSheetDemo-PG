package com.example.pgdemo.admin.export

import com.example.pgdemo.admin.tenant.TenantAccessDeniedException
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.admin.tenant.TenantPermissionMatrix
import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.mongodb.MongoStreamingDataSource
import com.streamsheet.core.schema.AnnotationExcelSchema
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.sequences.sequence
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class ExportService(
    private val excelExporter: ExcelExporter,
    private val mongoTemplate: MongoTemplate,
    private val tenantPermissionMatrix: TenantPermissionMatrix
) {
    private val utcDisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC)

    fun exportPayments(
        startDate: LocalDate,
        endDate: LocalDate,
        outputStream: OutputStream,
        headquartersId: UUID?,
        merchantId: UUID?
    ) {
        val fromUtc = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val toUtcExclusive = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        exportPayments(
            fromUtc = fromUtc,
            toUtcExclusive = toUtcExclusive,
            outputStream = outputStream,
            headquartersId = headquartersId,
            merchantId = merchantId
        )
    }

    fun exportTransactions(
        fromUtc: Instant,
        toUtcExclusive: Instant,
        outputStream: OutputStream,
        headquartersId: UUID?,
        merchantId: UUID?,
        transactionStatus: String?
    ) {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireExport(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        validateRequestedFilters(tenantInfo.tenantType, tenantInfo.tenantId, headquartersId, merchantId)

        require(toUtcExclusive.isAfter(fromUtc)) {
            "toUtc must be after fromUtc"
        }

        val trimmed = transactionStatus?.trim()?.takeIf { it.isNotBlank() }
        val paymentStatusFilter = trimmed?.let { runCatching { PaymentStatus.valueOf(it) }.getOrNull() }
        val refundStatusFilter = trimmed?.let { runCatching { RefundStatus.valueOf(it) }.getOrNull() }
        require(trimmed == null || paymentStatusFilter != null || refundStatusFilter != null) {
            "Invalid transactionStatus"
        }

        val exportPayments = trimmed == null || paymentStatusFilter != null
        val exportRefunds = trimmed == null || refundStatusFilter != null

        fun applyTenantFilters(criteria: Criteria) {
            when (tenantInfo.tenantType) {
                TenantType.OPERATOR -> {
                    headquartersId?.let { criteria.and("headquartersId").`is`(it) }
                    merchantId?.let { criteria.and("merchantId").`is`(it) }
                }
                TenantType.HEADQUARTERS -> {
                    val tenantHeadquartersId = tenantInfo.tenantId
                        ?: throw TenantAccessDeniedException("Headquarters tenant requires tenantId")
                    criteria.and("headquartersId").`is`(tenantHeadquartersId)
                    merchantId?.let { criteria.and("merchantId").`is`(it) }
                }
                TenantType.MERCHANT -> {
                    val tenantMerchantId = tenantInfo.tenantId
                        ?: throw TenantAccessDeniedException("Merchant tenant requires tenantId")
                    criteria.and("merchantId").`is`(tenantMerchantId)
                }
            }
        }

        val paymentCriteria = Criteria.where("paymentDate")
            .gte(fromUtc)
            .lt(toUtcExclusive)
        applyTenantFilters(paymentCriteria)
        paymentStatusFilter?.let { paymentCriteria.and("paymentStatus").`is`(it.name) }

        val refundCriteria = Criteria.where("refundDate")
            .gte(fromUtc)
            .lt(toUtcExclusive)
            .and("refundId").ne(null)
        applyTenantFilters(refundCriteria)
        refundStatusFilter?.let { refundCriteria.and("refundStatus").`is`(it.name) }

        val criteria = when {
            exportPayments && exportRefunds -> Criteria().orOperator(paymentCriteria, refundCriteria)
            exportPayments -> paymentCriteria
            else -> refundCriteria
        }

        val query = Query.query(criteria)
        val baseSource = MongoStreamingDataSource.create(mongoTemplate, PaymentExportView::class.java, query)

        val schema = AnnotationExcelSchema.create<TransactionExportDto>()
        val dataSource = MappedStreamingDataSource(baseSource) { view: PaymentExportView ->
            sequence {
                if (exportPayments && view.paymentDate >= fromUtc && view.paymentDate < toUtcExclusive) {
                    val status = view.paymentStatus
                    if (paymentStatusFilter == null || status == paymentStatusFilter.name) {
                        yield(
                            TransactionExportDto(
                                transactionId = view.transactionId.toString(),
                                transactionType = "Payment",
                                headquartersCode = view.headquartersCode,
                                headquartersName = view.headquartersName,
                                merchantCode = view.merchantCode,
                                merchantName = view.merchantName,
                                orderId = view.orderId,
                                amount = view.amount,
                                method = view.paymentMethod,
                                status = status,
                                transactionDate = utcDisplayFormatter.format(view.paymentDate),
                                paymentId = null
                            )
                        )
                    }
                }

                val refundId = view.refundId
                val refundDate = view.refundDate
                val refundStatus = view.refundStatus
                if (exportRefunds && refundId != null && refundDate != null && refundDate >= fromUtc && refundDate < toUtcExclusive) {
                    if (refundStatusFilter == null || refundStatus == refundStatusFilter.name) {
                        yield(
                            TransactionExportDto(
                                transactionId = refundId.toString(),
                                transactionType = "Refund",
                                headquartersCode = view.headquartersCode,
                                headquartersName = view.headquartersName,
                                merchantCode = view.merchantCode,
                                merchantName = view.merchantName,
                                orderId = view.orderId,
                                amount = view.refundAmount ?: 0L,
                                method = view.paymentMethod,
                                status = refundStatus ?: "-",
                                transactionDate = utcDisplayFormatter.format(refundDate),
                                paymentId = view.transactionId.toString()
                            )
                        )
                    }
                }
            }
        }

        excelExporter.export(schema, dataSource, outputStream)
    }

    fun exportPayments(
        fromUtc: Instant,
        toUtcExclusive: Instant,
        outputStream: OutputStream,
        headquartersId: UUID?,
        merchantId: UUID?
    ) {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireExport(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        validateRequestedFilters(tenantInfo.tenantType, tenantInfo.tenantId, headquartersId, merchantId)

        require(toUtcExclusive.isAfter(fromUtc)) {
            "toUtc must be after fromUtc"
        }

        val criteria = Criteria.where("paymentDate")
            .gte(fromUtc)
            .lt(toUtcExclusive)

        when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> {
                headquartersId?.let { criteria.and("headquartersId").`is`(it) }
                merchantId?.let { criteria.and("merchantId").`is`(it) }
            }
            TenantType.HEADQUARTERS -> {
                val tenantHeadquartersId = tenantInfo.tenantId
                    ?: throw TenantAccessDeniedException("Headquarters tenant requires tenantId")
                criteria.and("headquartersId").`is`(tenantHeadquartersId)
                merchantId?.let { criteria.and("merchantId").`is`(it) }
            }
            TenantType.MERCHANT -> {
                val tenantMerchantId = tenantInfo.tenantId
                    ?: throw TenantAccessDeniedException("Merchant tenant requires tenantId")
                criteria.and("merchantId").`is`(tenantMerchantId)
            }
        }

        val schema = AnnotationExcelSchema.create<PaymentExportDto>()
        val dataSource = MongoStreamingDataSource.create<PaymentExportDto>(
            mongoTemplate = mongoTemplate,
            query = Query.query(criteria)
        )

        excelExporter.export(schema, dataSource, outputStream)
    }

    private fun validateRequestedFilters(
        tenantType: TenantType,
        tenantId: UUID?,
        headquartersId: UUID?,
        merchantId: UUID?
    ) {
        when (tenantType) {
            TenantType.OPERATOR -> Unit
            TenantType.HEADQUARTERS -> {
                if (headquartersId != null && headquartersId != tenantId) {
                    throw TenantAccessDeniedException("Headquarters cannot filter other headquarters")
                }
            }
            TenantType.MERCHANT -> {
                if (headquartersId != null) {
                    throw TenantAccessDeniedException("Merchant cannot filter by headquarters")
                }
                if (merchantId != null && merchantId != tenantId) {
                    throw TenantAccessDeniedException("Merchant cannot filter other merchants")
                }
            }
        }
    }
}
