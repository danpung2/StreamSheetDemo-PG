# PG Demo (StreamSheet)

> **이 프로젝트는 데모/샘플 목적으로 제작되었습니다.**
> 모든 데이터(본사, 가맹점, 거래 내역 등)는 **가상의 테스트 데이터**이며, 실제 서비스나 업체와 무관합니다.

StreamSheet를 **실제 서비스 형태로** 붙여서,
대용량 거래 데이터(결제/환불)를 **스트리밍 방식으로 엑셀(.xlsx)로 내보내는** PG 데모 프로젝트입니다.

이 데모는 PG 도메인의 최소 형태를 포함합니다.

- 운영사(OPERATOR) / 본사(HEADQUARTERS) / 업체(MERCHANT) 멀티 테넌트 모델
- 결제/환불 트랜잭션 라이프사이클(요청→진행→완료/실패/취소)
- 트랜잭션(Postgres) → Export 뷰(Mongo) 동기화 → StreamSheet Export

## 🚀 라이브 데모 (Live Demo)

아래 주소에서 실제 배포된 어드민 포털을 체험해 보실 수 있습니다

### 데모 이용 방법:
1. [데모 시작 페이지](https://pg-demo.chunsamsik.com/demo)에 접속합니다.
2. **"Start Demo"** 버튼을 클릭합니다.
3. **본사 관리자(Headquarters Manager)** 권한으로 자동 로그인됩니다.
4. **"Exports"** 메뉴로 이동하여 StreamSheet가 제공하는 엑셀 내보내기 기능을 직접 체험해 보세요.

## 빠른 실행(docker compose)

상세한 절차/옵션은 [GUIDE.ko.md](GUIDE.ko.md)에 있고, 여기서는 “바로 실행”만 제공합니다.

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

필수 설정(기본값 없음; 누락 시 기동 실패):

- `JWT_SECRET`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_MONGODB_URI`

위 값들은 환경 변수 또는 Docker secret(파일을 `/run/secrets`에 마운트하고, 파일명은 위 키와 동일)로 주입할 수 있습니다.

참고: 테스트(`./gradlew test`)는 Postgres/MongoDB가 실행 중이고 접근 가능해야 합니다(기본 설정은 `localhost` 기준).

접속

- 관리자 페이지(pg-admin): `http://localhost:8081`

데모 접속(문서에 비밀번호 미노출)

- 데모 모드가 활성화된 경우: `http://localhost:8081/demo` 접속 후 `Start Demo` 클릭
- 로컬에서 seeder를 실행하는 경우: 데모 계정은 `hq_manager@pgdemo.com`이고, 비밀번호는 `SEEDER_PASSWORD_HQ_MANAGER`에 설정한 값입니다.

Export 확인

- Exports: `http://localhost:8081/admin/exports/payments`

## StreamSheet가 “간단한” 부분(실제 코드)

이 프로젝트의 Export는 아래 3단계로 끝납니다.

1) DTO에 스키마를 어노테이션으로 선언
2) Mongo 쿼리를 `MongoStreamingDataSource`로 감싸 스트리밍 소스로 만들기
3) `excelExporter.export(...)` 한 줄 호출

### 1) Excel 스키마(어노테이션)

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

### 2) Mongo 스트리밍 + Export 호출

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

// 결제/환불을 시간 기준으로 머지한 StreamingDataSource 구성
val dataSource: StreamingDataSource<TransactionExportDto> = /* ... */

excelExporter.export(schema, dataSource, outputStream)
```

## PG 데모 도메인(간단)

이 프로젝트는 멀티 테넌트 형태로 데이터를 분리해서 Export를 시연합니다.

- 운영사(OPERATOR): 전체 조회/필터 및 Export
- 본사(HEADQUARTERS): 소속 업체 범위에서 조회/Export
- 업체(MERCHANT): 자기 데이터 범위에서 조회/Export

## 트랜잭션 프로세스(요약)

- Payment: `PENDING(Requested)` → `PROCESSING(Processed)` → `COMPLETED(Completed)`
  - 예외: `FAILED`, `CANCELLED`
- Refund: (Payment이 `COMPLETED`일 때만) `PENDING` → `PROCESSING` → `COMPLETED`
  - 예외: `FAILED`

docker compose를 사용하면 `traffic`가 위 흐름에 맞춰 트랜잭션을 계속 생성합니다.

### 3) Spring Boot 설정(메모리 윈도우)

StreamSheet는 Apache POI SXSSF 기반이라, “윈도우(row access window)” 등 몇 가지 설정만으로 대용량을 안정적으로 다룹니다.

`pg-admin/src/main/resources/application.yml`

```yaml
streamsheet:
  row-access-window-size: 100
  flush-batch-size: 1000
  compress-temp-files: true
```

## 문서

- 자세한 사용법: [GUIDE.ko.md](GUIDE.ko.md)
