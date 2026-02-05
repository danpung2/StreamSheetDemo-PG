# PG Demo (StreamSheet)

PG Demo is a **realistic service-style** sample that shows how to integrate StreamSheet to export
large-scale payment/refund transactions to **streaming Excel (.xlsx)**.

This demo includes a minimal PG domain:

- Multi-tenant model: Operator (OPERATOR) / Headquarters (HEADQUARTERS) / Merchant (MERCHANT)
- Transaction lifecycle for payments/refunds (Requested → Processed → Completed/Failed/Cancelled)
- Postgres (transactions) → Mongo (export view) sync → StreamSheet export

## Quick Start (docker compose)

For detailed steps and options, see `GUIDE.ko.md`. This section is the minimal “just run it”.

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

Access

- Admin UI (pg-admin): `http://localhost:8081`
- Login: `http://localhost:8081/login`
  - `admin@pgdemo.com` / `admin123!`

Export

- Exports: `http://localhost:8081/admin/exports/payments`

## What StreamSheet Looks Like in Practice (real code)

In this demo, an export is basically:

1) Define an Excel schema on a DTO via annotations
2) Wrap a Mongo query with `MongoStreamingDataSource`
3) Call `excelExporter.export(...)`

### 1) Excel schema (annotations)

`pg-admin/src/main/kotlin/com/example/pgdemo/admin/export/TransactionExportDto.kt`

```kotlin
@ExcelSheet(name = "거래 내역")
data class TransactionExportDto(
    @ExcelColumn(header = "거래번호", width = 40, order = 1)
    val transactionId: String,

    @ExcelColumn(header = "거래유형", width = 10, order = 2)
    val transactionType: String,

    @ExcelColumn(header = "금액", width = 15, order = 8)
    val amount: Long,

    @ExcelColumn(header = "거래일시", width = 20, order = 11)
    val transactionDate: String,
)
```

### 2) Mongo streaming + export call

`pg-admin/src/main/kotlin/com/example/pgdemo/admin/export/ExportService.kt`

```kotlin
val schema = AnnotationExcelSchema.create<TransactionExportDto>()

val paymentSource = MongoStreamingDataSource.create(
    mongoTemplate,
    PaymentExportView::class.java,
    paymentQuery
)

val refundSource = MongoStreamingDataSource.create(
    mongoTemplate,
    PaymentExportView::class.java,
    refundQuery
)

// Build a StreamingDataSource that merges payments/refunds by time.
val dataSource: StreamingDataSource<TransactionExportDto> = /* ... */

excelExporter.export(schema, dataSource, outputStream)
```

## PG Domain (Brief)

Exports are scoped by tenant type to demonstrate realistic data access boundaries.

- Operator (OPERATOR): system-wide access and export
- Headquarters (HEADQUARTERS): export within its merchants
- Merchant (MERCHANT): export within its own data

## Transaction Flow (Summary)

- Payment: `PENDING (Requested)` → `PROCESSING (Processed)` → `COMPLETED (Completed)`
  - Exceptions: `FAILED`, `CANCELLED`
- Refund: (only when the payment is `COMPLETED`) `PENDING` → `PROCESSING` → `COMPLETED`
  - Exception: `FAILED`

When running via docker compose, the `traffic` service continuously generates transactions following this lifecycle.

### 3) Spring Boot config (memory window)

`pg-admin/src/main/resources/application.yml`

```yaml
streamsheet:
  row-access-window-size: 100
  flush-batch-size: 1000
  compress-temp-files: true
```

## Docs

- Detailed guide: `GUIDE.md`, `GUIDE.ko.md`
