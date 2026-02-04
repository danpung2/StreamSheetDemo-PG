package com.example.pgdemo.admin.export

import com.example.pgdemo.admin.tenant.TenantAccessDeniedException
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.admin.tenant.TenantPermissionMatrix
import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.mongodb.MongoStreamingDataSource
import com.streamsheet.core.schema.AnnotationExcelSchema
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.sequences.sequence
import org.springframework.data.domain.Sort
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
    private val displayZone = ZoneId.systemDefault()
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(displayZone)

    fun exportPayments(
        startDate: LocalDate,
        endDate: LocalDate,
        outputStream: OutputStream,
        headquartersId: UUID?,
        merchantId: UUID?
    ) {
        val fromUtc = startDate.atStartOfDay(displayZone).toInstant()
        val toUtcExclusive = endDate.plusDays(1).atStartOfDay(displayZone).toInstant()
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
        transactionType: String?,
        transactionStatus: String?
    ) {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireExport(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        validateRequestedFilters(tenantInfo.tenantType, tenantInfo.tenantId, headquartersId, merchantId)

        require(toUtcExclusive.isAfter(fromUtc)) {
            "toUtc must be after fromUtc"
        }

        val normalizedType = transactionType
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                when (it) {
                    "payment" -> "payment"
                    "refund" -> "refund"
                    else -> null
                }
            }
        require(transactionType == null || transactionType.isBlank() || normalizedType != null) {
            "Invalid transactionType"
        }

        val trimmed = transactionStatus?.trim()?.takeIf { it.isNotBlank() }
        val paymentStatusFilter = trimmed?.let { runCatching { PaymentStatus.valueOf(it) }.getOrNull() }
        val refundStatusFilter = trimmed?.let { runCatching { RefundStatus.valueOf(it) }.getOrNull() }
        require(trimmed == null || paymentStatusFilter != null || refundStatusFilter != null) {
            "Invalid transactionStatus"
        }

        val exportPaymentsByType = normalizedType == null || normalizedType == "payment"
        val exportRefundsByType = normalizedType == null || normalizedType == "refund"
        val exportPayments = exportPaymentsByType && (trimmed == null || paymentStatusFilter != null)
        val exportRefunds = exportRefundsByType && (trimmed == null || refundStatusFilter != null)

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

        data class DatedDto(
            val at: Instant,
            val typePriority: Int,
            val tieId: String,
            val dto: TransactionExportDto
        )

        fun paymentDatedDto(view: PaymentExportView): DatedDto {
            val status = view.paymentStatus
            val dto = TransactionExportDto(
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
                transactionDate = displayFormatter.format(view.paymentDate),
                paymentId = null
            )
            return DatedDto(at = view.paymentDate, typePriority = 1, tieId = view.transactionId.toString(), dto = dto)
        }

        fun refundDatedDto(view: PaymentExportView): DatedDto {
            val refundId = view.refundId ?: throw IllegalStateException("refundId is missing")
            val refundDate = view.refundDate ?: throw IllegalStateException("refundDate is missing")
            val dto = TransactionExportDto(
                transactionId = refundId.toString(),
                transactionType = "Refund",
                headquartersCode = view.headquartersCode,
                headquartersName = view.headquartersName,
                merchantCode = view.merchantCode,
                merchantName = view.merchantName,
                orderId = view.orderId,
                amount = view.refundAmount ?: 0L,
                method = view.paymentMethod,
                status = view.refundStatus ?: "-",
                transactionDate = displayFormatter.format(refundDate),
                paymentId = view.transactionId.toString()
            )
            return DatedDto(at = refundDate, typePriority = 0, tieId = refundId.toString(), dto = dto)
        }

        fun mergeDated(a: Sequence<DatedDto>, b: Sequence<DatedDto>): Sequence<TransactionExportDto> {
            val ia = a.iterator()
            val ib = b.iterator()

            fun nextOrNull(it: Iterator<DatedDto>): DatedDto? = if (it.hasNext()) it.next() else null

            return sequence {
                var ca: DatedDto? = nextOrNull(ia)
                var cb: DatedDto? = nextOrNull(ib)

                while (ca != null || cb != null) {
                    val pickA = when {
                        cb == null -> true
                        ca == null -> false
                        ca.at.isAfter(cb.at) -> true
                        cb.at.isAfter(ca.at) -> false
                        ca.typePriority < cb.typePriority -> true
                        cb.typePriority < ca.typePriority -> false
                        else -> ca.tieId > cb.tieId
                    }

                    if (pickA) {
                        yield(ca!!.dto)
                        ca = nextOrNull(ia)
                    } else {
                        yield(cb!!.dto)
                        cb = nextOrNull(ib)
                    }
                }
            }
        }

        val schema = AnnotationExcelSchema.create<TransactionExportDto>()

        val paymentsSort = Sort.by(Sort.Direction.DESC, "paymentDate")
            .and(Sort.by(Sort.Direction.DESC, "transactionId"))
        val refundsSort = Sort.by(Sort.Direction.DESC, "refundDate")
            .and(Sort.by(Sort.Direction.DESC, "refundId"))

        val dataSource: StreamingDataSource<TransactionExportDto> = when {
            exportPayments && exportRefunds -> {
                val paymentQuery = Query.query(paymentCriteria).with(paymentsSort)
                val refundQuery = Query.query(refundCriteria).with(refundsSort)

                val paymentSource = MongoStreamingDataSource.create(mongoTemplate, PaymentExportView::class.java, paymentQuery)
                val refundSource = MongoStreamingDataSource.create(mongoTemplate, PaymentExportView::class.java, refundQuery)

                val paymentDatedSource = MappedStreamingDataSource(paymentSource, name = "payment_export_view") { view: PaymentExportView ->
                    sequence {
                        // paymentQuery already ranges by paymentDate; filter here is defensive.
                        if (view.paymentDate >= fromUtc && view.paymentDate < toUtcExclusive) {
                            val status = view.paymentStatus
                            if (paymentStatusFilter == null || status == paymentStatusFilter.name) {
                                yield(paymentDatedDto(view))
                            }
                        }
                    }
                }

                val refundDatedSource = MappedStreamingDataSource(refundSource, name = "payment_export_view") { view: PaymentExportView ->
                    sequence {
                        // refundQuery ensures refundId/refundDate are present and within range.
                        val refundStatus = view.refundStatus
                        if (refundStatusFilter == null || refundStatus == refundStatusFilter.name) {
                            yield(refundDatedDto(view))
                        }
                    }
                }

                object : StreamingDataSource<TransactionExportDto> {
                    override val sourceName: String
                        get() = "transactions"

                    override fun stream(): Sequence<TransactionExportDto> {
                        return mergeDated(paymentDatedSource.stream(), refundDatedSource.stream())
                    }

                    override fun stream(filter: Map<String, Any>): Sequence<TransactionExportDto> {
                        return mergeDated(paymentDatedSource.stream(filter), refundDatedSource.stream(filter))
                    }

                    override fun close() {
                        paymentDatedSource.close()
                        refundDatedSource.close()
                    }
                }
            }
            exportPayments -> {
                val paymentQuery = Query.query(paymentCriteria).with(paymentsSort)
                val paymentSource = MongoStreamingDataSource.create(mongoTemplate, PaymentExportView::class.java, paymentQuery)
                MappedStreamingDataSource(paymentSource, name = "transactions") { view: PaymentExportView ->
                    sequence {
                        yield(paymentDatedDto(view).dto)
                    }
                }
            }
            else -> {
                val refundQuery = Query.query(refundCriteria).with(refundsSort)
                val refundSource = MongoStreamingDataSource.create(mongoTemplate, PaymentExportView::class.java, refundQuery)
                MappedStreamingDataSource(refundSource, name = "transactions") { view: PaymentExportView ->
                    sequence {
                        yield(refundDatedDto(view).dto)
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
