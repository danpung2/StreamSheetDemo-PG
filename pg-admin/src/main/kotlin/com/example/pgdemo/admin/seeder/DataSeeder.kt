package com.example.pgdemo.admin.seeder

import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.Headquarters
import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.document.ExportJob
import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.`enum`.BusinessType
import com.example.pgdemo.common.domain.`enum`.PaymentStatus
import com.example.pgdemo.common.domain.`enum`.RefundStatus
import com.example.pgdemo.common.domain.`enum`.StoreType
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefreshTokenRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Data seeding service for demo environment.
 * 데모 환경을 위한 데이터 시딩 서비스.
 * 
 * This service generates realistic test data including:
 * - 100 headquarters
 * - 10,000 merchants (distributed across headquarters)
 * - 1,000,000 payment transactions
 * - 50,000 refund transactions
 * - 500 admin users
 */
@Service
class DataSeeder(
    private val headquartersRepository: HeadquartersRepository,
    private val merchantRepository: MerchantRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val refundTransactionRepository: RefundTransactionRepository,
    private val adminUserRepository: AdminUserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val mongoTemplate: MongoTemplate,
    private val passwordEncoder: PasswordEncoder
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DataSeeder::class.java)
        
        // Configuration constants
        // 설정 상수
        const val HEADQUARTERS_COUNT = 100
        const val MERCHANT_COUNT = 10_000
        const val PAYMENT_COUNT = 1_000_000
        const val REFUND_COUNT = 50_000
        const val ADMIN_USER_COUNT = 500
        
        // Batch sizes for optimized insertion
        // 최적화된 삽입을 위한 배치 크기
        const val HEADQUARTERS_BATCH_SIZE = 50
        const val MERCHANT_BATCH_SIZE = 500
        const val PAYMENT_BATCH_SIZE = 5_000
        const val REFUND_BATCH_SIZE = 2_500
        const val ADMIN_BATCH_SIZE = 100
        
        // Payment methods for random selection
        // 랜덤 선택을 위한 결제 수단
        private val PAYMENT_METHODS = listOf("CARD", "BANK_TRANSFER", "KAKAO_PAY", "NAVER_PAY", "TOSS")
        
        // Refund reasons categorised by domain
        // 도메인별 환불 사유
        private val COMMON_REFUND_REASONS = listOf(
            "고객 변심", "주문 취소", "중복 결제", "기타 사유", null
        )

        private val FNB_REFUND_REASONS = listOf(
            "음식 상태 불량", "메뉴 오제조", "배달 지연", "이물질 혼입", 
            "포장 불량", "맛 불만족"
        ) + COMMON_REFUND_REASONS

        private val RETAIL_REFUND_REASONS = listOf(
            "상품 불량", "사이즈 교환", "색상 교환", "오배송", 
            "마감 미흡", "유통기한 경과"
        ) + COMMON_REFUND_REASONS
        
        // Sample company names
        // 샘플 회사명
        private val COMPANY_NAMES = listOf(
            // Coffee & Cafe
            "스타벅스", "투썸플레이스", "이디야커피", "메가MGC커피", "컴포즈커피",
            "빽다방", "폴바셋", "할리스", "엔제리너스", "파스쿠찌",
            "커피빈", "탐앤탐스", "매머드커피", "더벤티", "공차",
            "카페베네", "드롭탑", "블루보틀", "설빙", "쥬씨",
            // Fast Food & Burger
            "맥도날드", "버거킹", "롯데리아", "KFC", "맘스터치",
            "노브랜드버거", "쉐이크쉑", "프랭크버거", "모스버거", "타코벨",
            "이삭토스트", "에그드랍", "서브웨이", "퀴즈노스", "봉구스밥버거",
            // Bakery & Dessert
            "파리바게뜨", "뚜레쥬르", "던킨", "크리스피크림도넛", "배스킨라빈스",
            "성심당", "이성당", "삼송빵집", "태극당", "옵스",
            // Chicken & Pizza
            "BBQ", "BHC", "교촌치킨", "굽네치킨", "네네치킨",
            "처갓집양념치킨", "페리카나", "멕시카나", "60계치킨", "노랑통닭",
            "도미노피자", "피자헛", "파파존스", "미스터피자", "피자알볼로",
            "피자스쿨", "청년피자", "반올림피자", "피자마루", "59쌀피자",
            // Retail & CVS
            "CU", "GS25", "세븐일레븐", "이마트24", "미니스톱",
            "올리브영", "다이소", "롯데마트", "이마트", "홈플러스",
            "코스트코", "하나로마트", "하이마트", "전자랜드", "ABC마트",
            // Tech & Platform
            "쿠팡", "네이버", "카카오", "배달의민족", "요기요",
            "마켓컬리", "무신사", "29CM", "에이블리", "지그재그",
            "토스", "당근마켓", "야놀자", "여기어때", "쏘카",
            // Fashion & Others
            "나이키", "아디다스", "유니클로", "자라", "H&M",
            "스파오", "탑텐", "에잇세컨즈", "CGV", "롯데시네마",
            "메가박스", "교보문고", "영풍문고", "알라딘", "예스24"
        )
        
        private val LAST_NAMES = listOf("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")
        private val FIRST_NAMES = listOf("민준", "서연", "하준", "지우", "도윤", "수아", "예준", "하은", "시우", "지아")

        // Locations used for branch naming.
        // 매장명 생성을 위한 지역/랜드마크 토큰
        private val BRANCH_LOCATIONS = listOf(
            // Seoul - Gangnam
            "강남", "강남역", "역삼", "선릉", "삼성", "코엑스", "대치", "논현", "신사", "압구정", "청담", "도산공원",
            // Seoul - Songpa/Gangdong
            "잠실", "잠실역", "석촌", "송파", "가락", "문정", "장지", "위례", "천호", "성내", "길동", "둔촌",
            // Seoul - Seocho/Yangjae
            "서초", "교대", "방배", "반포", "잠원", "양재", "양재역", "매봉", "서초대로",
            // Seoul - Mapo/Seodaemun
            "홍대", "홍대입구", "합정", "상수", "망원", "연남", "신촌", "이대", "아현", "공덕", "마포", "상암",
            // Seoul - Yeongdeungpo/Guro
            "여의도", "IFC", "영등포", "타임스퀘어", "문래", "신도림", "가산디지털", "구로디지털", "대림", "신림", "서울대입구",
            // Seoul - Jung/Jongno/Yongsan
            "명동", "을지로", "종로", "광화문", "시청", "동대문", "DDP", "혜화", "대학로", "서울역", "용산", "삼각지",
            // Seoul - Seongdong/Gwangjin
            "왕십리", "성수", "서울숲", "뚝섬", "건대입구", "어린이대공원",
            // Seoul - Yongsan/Hannam
            "이태원", "한남", "경리단", "남산",
            // Gyeonggi - Seongnam/Suwon/Yongin
            "판교", "정자", "서현", "야탑", "수내", "광교", "영통", "인계", "수원역", "분당", "죽전", "기흥",
            // Incheon/Songdo
            "송도", "센트럴파크", "청라", "부평", "인천터미널",
            // Other cities
            "부산서면", "해운대", "광안리", "대구동성로", "대전둔산", "광주상무", "제주연동", "제주공항"
        )

        private val BRANCH_SUFFIXES = listOf(
            "점", "역점", "DT점", "센터점", "타워점", "몰점", "캠퍼스점", "파크점", "시티점", "스퀘어점", "아울렛점"
        )
    }
    
    // Counters for progress tracking
    // 진행 상황 추적을 위한 카운터
    private val headquartersCounter = AtomicInteger(0)
    private val merchantCounter = AtomicInteger(0)
    private val paymentCounter = AtomicInteger(0)
    private val refundCounter = AtomicInteger(0)
    private val adminCounter = AtomicInteger(0)

    enum class BranchNamingStyle {
        BONJEOM_FIRST,
        NUMBERED_ONLY,
        LOCATION_ONLY
    }

    private class HqMerchantNameGenerator(
        private val headquartersName: String,
        private val namingStyle: BranchNamingStyle,
        private val random: Random
    ) {
        private val usedNames = HashSet<String>()
        private val locations = BRANCH_LOCATIONS.shuffled(random)
        private val suffixes = BRANCH_SUFFIXES
        private val flagshipLocation = locations.firstOrNull() ?: "본점"
        private var index = 0
        private var branchSeq = 1

        fun nextName(): String {
            while (true) {
                val candidate = buildCandidate()
                if (usedNames.add(candidate)) {
                    return candidate
                }

                // Collision fallback: use a realistic numbered suffix (e.g. 강남2호점).
                // 충돌 시: 현실적인 번호 표기(예: 강남2호점)로 유니크 보장
                val fallbackLocation = locations.getOrNull(index % locations.size) ?: flagshipLocation
                val fallback = "$headquartersName ${fallbackLocation}${branchSeq}호점"
                branchSeq += 1
                if (usedNames.add(fallback)) {
                    return fallback
                }
            }
        }

        private fun buildCandidate(): String {
            val current = index
            index += 1

            if (namingStyle == BranchNamingStyle.BONJEOM_FIRST && current == 0) {
                return "$headquartersName ${flagshipLocation}본점"
            }

            val location = locations[current % locations.size]

            return when (namingStyle) {
                BranchNamingStyle.NUMBERED_ONLY -> {
                    val seq = current + 1
                    "$headquartersName ${location}${seq}호점"
                }

                BranchNamingStyle.BONJEOM_FIRST,
                BranchNamingStyle.LOCATION_ONLY -> {
                    val suffix = suffixes[current % suffixes.size]
                    "$headquartersName ${location}${suffix}"
                }
            }
        }
    }

    private fun pickBranchNamingStyle(random: Random): BranchNamingStyle {
        // Weighted choice; different HQs follow different conventions.
        // 가중치 기반 선택: HQ마다 매장명 관행이 다르도록
        return when (random.nextInt(100)) {
            in 0..44 -> BranchNamingStyle.BONJEOM_FIRST      // 45%
            in 45..79 -> BranchNamingStyle.LOCATION_ONLY     // 35%
            else -> BranchNamingStyle.NUMBERED_ONLY          // 20%
        }
    }

    private fun uniqueHeadquartersName(baseName: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(baseName)) {
            return baseName
        }

        // Avoid debug-ish "#n"; use more plausible corp suffixes.
        // 디버그성 "#n" 대신 현실적인 법인/브랜드 접미사 사용
        val suffixes = listOf("코리아", "리테일", "그룹", "파트너스", "홀딩스", "프랜차이즈")
        for (suffix in suffixes) {
            val candidate = "$baseName $suffix"
            if (usedNames.add(candidate)) {
                return candidate
            }
        }

        // Last resort: append a deterministic numeric token (still realistic-ish).
        // 최후 수단: 숫자 토큰(현실적 표기) 추가
        var n = 2
        while (true) {
            val candidate = "$baseName $n"
            if (usedNames.add(candidate)) {
                return candidate
            }
            n += 1
        }
    }

    /**
     * Delete existing demo data so we can reseed.
     * 재시딩을 위해 기존 데모 데이터를 삭제합니다.
     */
    @Transactional
    fun resetAll() {
        val startTime = System.currentTimeMillis()
        logger.warn("=== Resetting demo data (relational DB) / 데모 데이터 리셋(관계형 DB) ===")

        // Mongo collections used by admin (export jobs, materialized export view).
        // admin에서 사용하는 Mongo 컬렉션(export job, 물질화 export view)
        runCatching { mongoTemplate.dropCollection(ExportJob::class.java) }
            .onSuccess { logger.warn("Dropped Mongo collection: ExportJob") }
            .onFailure { e -> logger.warn("Failed to drop Mongo collection: ExportJob", e) }

        runCatching { mongoTemplate.dropCollection(PaymentExportView::class.java) }
            .onSuccess { logger.warn("Dropped Mongo collection: PaymentExportView") }
            .onFailure { e -> logger.warn("Failed to drop Mongo collection: PaymentExportView", e) }

        // Reset pg-main sync cursor so the materialized view can be rebuilt from scratch.
        // pg-main 동기화 커서를 리셋하여 물질화 뷰를 처음부터 재구축 가능하게 함
        runCatching {
            mongoTemplate.remove(
                Query.query(Criteria.where("_id").`is`("payment_export_view")),
                "sync_state"
            )
        }
            .onSuccess { logger.warn("Removed Mongo sync_state: payment_export_view") }
            .onFailure { e -> logger.warn("Failed to remove Mongo sync_state: payment_export_view", e) }

        // FK-safe delete order
        // FK 고려 삭제 순서
        refundTransactionRepository.deleteAllInBatch()
        paymentTransactionRepository.deleteAllInBatch()
        refreshTokenRepository.deleteAllInBatch()
        merchantRepository.deleteAllInBatch()
        headquartersRepository.deleteAllInBatch()

        val durationMs = System.currentTimeMillis() - startTime
        logger.warn("=== Reset complete in ${durationMs}ms / 리셋 완료 (${durationMs}ms) ===")
    }
    
    /**
     * Seed all data. Main entry point.
     * 모든 데이터를 시딩합니다. 메인 진입점.
     */
    fun seedAll() {
        val startTime = System.currentTimeMillis()
        logger.info("=== Starting data seeding process / 데이터 시딩 프로세스 시작 ===")
        
        // Check if data already exists
        // 데이터가 이미 존재하는지 확인
        if (headquartersRepository.count() > 0) {
            logger.warn("Data already exists. Skipping seeding. / 데이터가 이미 존재합니다. 시딩을 건너뜁니다.")
            return
        }
        
        // Step 1: Seed headquarters
        // 1단계: 본사 시딩
        val headquarters = seedHeadquarters()
        
        // Step 2: Seed merchants
        // 2단계: 업체 시딩
        val merchants = seedMerchants(headquarters)
        
        // Step 3: Seed payments
        // 3단계: 결제 시딩
        val payments = seedPayments(merchants)
        
        // Step 4: Seed refunds
        // 4단계: 환불 시딩
        seedRefunds(payments)
        
        // Step 5: Seed admin users
        // 5단계: 관리자 사용자 시딩
        seedAdminUsers(headquarters, merchants)
        
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime) / 1000
        
        logger.info("=== Data seeding completed in ${duration}s / 데이터 시딩 완료 (${duration}초) ===")
        logger.info("- Headquarters: $HEADQUARTERS_COUNT / 본사: $HEADQUARTERS_COUNT")
        logger.info("- Merchants: $MERCHANT_COUNT / 업체: $MERCHANT_COUNT")
        logger.info("- Payments: $PAYMENT_COUNT / 결제: $PAYMENT_COUNT")
        logger.info("- Refunds: $REFUND_COUNT / 환불: $REFUND_COUNT")
        logger.info("- Admin Users: $ADMIN_USER_COUNT / 관리자: $ADMIN_USER_COUNT")
    }
    
    /**
     * Seed headquarters data.
     * 본사 데이터를 시딩합니다.
     */
    @Transactional
    fun seedHeadquarters(): List<Headquarters> {
        logger.info("Seeding $HEADQUARTERS_COUNT headquarters... / 본사 ${HEADQUARTERS_COUNT}개 시딩 중...")
        
        val result = mutableListOf<Headquarters>()
        val batch = mutableListOf<Headquarters>()
        
        val usedNames = HashSet<String>(HEADQUARTERS_COUNT)
        repeat(HEADQUARTERS_COUNT) { index ->
            val hq = Headquarters().apply {
                headquartersCode = "HQ${String.format("%05d", index + 1)}"
                val baseName = COMPANY_NAMES[index % COMPANY_NAMES.size]
                name = uniqueHeadquartersName(baseName, usedNames)
                businessNumber = "${100 + (index / 100)}-${10 + ((index / 10) % 10)}-${10000 + index}"
                contractType = if (Random.nextBoolean()) "PREMIUM" else "STANDARD"
                status = if (Random.nextInt(100) < 95) "ACTIVE" else "INACTIVE"
            }
            batch.add(hq)
            
            if (batch.size >= HEADQUARTERS_BATCH_SIZE) {
                result.addAll(headquartersRepository.saveAll(batch))
                headquartersCounter.addAndGet(batch.size)
                batch.clear()
                logProgress("Headquarters / 본사", headquartersCounter.get(), HEADQUARTERS_COUNT)
            }
        }
        
        // Save remaining batch
        // 남은 배치 저장
        if (batch.isNotEmpty()) {
            result.addAll(headquartersRepository.saveAll(batch))
            headquartersCounter.addAndGet(batch.size)
        }
        
        logger.info("Headquarters seeding complete: ${result.size} / 본사 시딩 완료: ${result.size}개")
        return result
    }
    
    /**
     * Seed merchant data.
     * 업체 데이터를 시딩합니다.
     */
    @Transactional
    fun seedMerchants(headquarters: List<Headquarters>): List<Merchant> {
        logger.info("Seeding $MERCHANT_COUNT merchants... / 업체 ${MERCHANT_COUNT}개 시딩 중...")
        
        val result = mutableListOf<Merchant>()
        val batch = mutableListOf<Merchant>()
        val businessTypes = BusinessType.entries.toTypedArray()
        val storeTypes = StoreType.entries.toTypedArray()

        val nameGenerators = headquarters.associate { hq ->
            val seed = (hq.id?.hashCode() ?: hq.headquartersCode.hashCode())
            val random = Random(seed)
            val style = pickBranchNamingStyle(random)
            hq.id!! to HqMerchantNameGenerator(
                headquartersName = hq.name,
                namingStyle = style,
                random = random
            )
        }

        repeat(MERCHANT_COUNT) { index ->
            val hq = headquarters[index % headquarters.size]
            val generator = nameGenerators[hq.id] ?: error("Missing name generator for headquarters: ${hq.id}")
            val merchant = Merchant().apply {
                merchantCode = "M${String.format("%08d", index + 1)}"
                this.headquarters = hq
                name = generator.nextName()
                storeNumber = index + 1
                storeType = storeTypes[Random.nextInt(storeTypes.size)]
                businessType = businessTypes[Random.nextInt(businessTypes.size)]
                status = if (Random.nextInt(100) < 92) "ACTIVE" else "INACTIVE"
                contractStartDate = LocalDate.now().minusDays(Random.nextLong(365, 1825))
                contractEndDate = if (Random.nextBoolean()) LocalDate.now().plusDays(Random.nextLong(30, 730)) else null
            }
            batch.add(merchant)
            
            if (batch.size >= MERCHANT_BATCH_SIZE) {
                result.addAll(merchantRepository.saveAll(batch))
                merchantCounter.addAndGet(batch.size)
                batch.clear()
                logProgress("Merchants / 업체", merchantCounter.get(), MERCHANT_COUNT)
            }
        }
        
        // Save remaining batch
        // 남은 배치 저장
        if (batch.isNotEmpty()) {
            result.addAll(merchantRepository.saveAll(batch))
            merchantCounter.addAndGet(batch.size)
        }
        
        logger.info("Merchants seeding complete: ${result.size} / 업체 시딩 완료: ${result.size}개")
        return result
    }
    
    /**
     * Seed payment transaction data.
     * 결제 트랜잭션 데이터를 시딩합니다.
     */
    @Transactional
    fun seedPayments(merchants: List<Merchant>): List<PaymentTransaction> {
        logger.info("Seeding $PAYMENT_COUNT payments... / 결제 ${PAYMENT_COUNT}개 시딩 중...")
        
        val result = mutableListOf<PaymentTransaction>()
        val batch = mutableListOf<PaymentTransaction>()
        val now = Instant.now()

        // Seed payments so that requestedAt falls within the last 24 hours.
        // This makes the admin dashboard (24h KPIs) and /admin/payments default view non-empty right after seeding.
        // NOTE: We intentionally keep a small buffer so processed/completed timestamps don't end up in the future.
        val windowFrom = now.minus(Duration.ofHours(24))
        val windowTo = now.minus(Duration.ofSeconds(10))
        val windowMillis = Duration.between(windowFrom, windowTo).toMillis().coerceAtLeast(1)
        
        repeat(PAYMENT_COUNT) { index ->
            val merchant = merchants[Random.nextInt(merchants.size)]
            val payment = PaymentTransaction().apply {
                this.merchant = merchant
                orderId = "ORD${System.nanoTime()}-${String.format("%09d", index + 1)}"
                amount = (Random.nextLong(1000, 500000) / 100) * 100  // Round to 100
                paymentMethod = PAYMENT_METHODS[Random.nextInt(PAYMENT_METHODS.size)]
                status = generatePaymentStatus()

                requestedAt = windowFrom.plusMillis(Random.nextLong(windowMillis))

                val processingDelayMs = samplePaymentProcessingDelayMs(status)
                val processed = requestedAt.plusMillis(processingDelayMs)

                when (status) {
                    PaymentStatus.PAYMENT_COMPLETED -> {
                        processedAt = processed
                        completedAt = processed.plusMillis(samplePaymentSettlementDelayMs())
                    }
                    PaymentStatus.PAYMENT_FAILED -> {
                        processedAt = processed
                        failureReason = listOf(
                            "잔액 부족", "카드 한도 초과", "통신 오류", 
                            "결제 거절", "유효하지 않은 카드"
                        ).random()
                    }
                    PaymentStatus.PAYMENT_PROCESSING -> {
                        processedAt = processed
                    }
                    PaymentStatus.PAYMENT_CANCELLED -> {
                        processedAt = processed
                        completedAt = processed.plusMillis(samplePaymentCancellationDelayMs())
                    }
                    else -> { /* PAYMENT_PENDING - no additional timestamps */ }
                }
            }
            batch.add(payment)
            
            if (batch.size >= PAYMENT_BATCH_SIZE) {
                val saved = paymentTransactionRepository.saveAll(batch)
                result.addAll(saved)
                paymentCounter.addAndGet(batch.size)
                batch.clear()
                logProgress("Payments / 결제", paymentCounter.get(), PAYMENT_COUNT)
            }
        }
        
        // Save remaining batch
        // 남은 배치 저장
        if (batch.isNotEmpty()) {
            result.addAll(paymentTransactionRepository.saveAll(batch))
            paymentCounter.addAndGet(batch.size)
        }
        
        logger.info("Payments seeding complete: ${result.size} / 결제 시딩 완료: ${result.size}개")
        return result
    }
    
    /**
     * Seed refund transaction data.
     * 환불 트랜잭션 데이터를 시딩합니다.
     */
    @Transactional
    fun seedRefunds(payments: List<PaymentTransaction>) {
        logger.info("Seeding $REFUND_COUNT refunds... / 환불 ${REFUND_COUNT}개 시딩 중...")
        
        // Filter completed payments for refunds
        // 환불을 위해 완료된 결제만 필터링
        val completedPayments = payments.filter { it.status == PaymentStatus.PAYMENT_COMPLETED }
        
        if (completedPayments.isEmpty()) {
            logger.warn("No completed payments found for refund seeding. / 환불 시딩을 위한 완료된 결제가 없습니다.")
            return
        }
        
        val batch = mutableListOf<RefundTransaction>()
        val now = Instant.now()

        val refundCandidates = completedPayments
            .filter { it.completedAt != null && it.completedAt!!.isBefore(now.minus(Duration.ofMinutes(10))) }
            .ifEmpty { completedPayments }

        val latestRefundRequestedAt = now.minus(Duration.ofMinutes(4))
        val usedPaymentIndices = mutableSetOf<Int>()
        
        repeat(REFUND_COUNT) {
            // Select a random completed payment (may reuse for partial refunds)
            // 랜덤으로 완료된 결제 선택 (부분 환불을 위해 재사용 가능)
            val paymentIndex = Random.nextInt(refundCandidates.size)
            val payment = refundCandidates[paymentIndex]

            val isFullRefund = !usedPaymentIndices.contains(paymentIndex) && Random.nextInt(100) < 10
            val refundAmount = if (isFullRefund) {
                payment.amount
            } else {
                (payment.amount * Random.nextDouble(0.05, 0.25)).toLong() / 100 * 100
            }
            usedPaymentIndices.add(paymentIndex)
            
            val refund = RefundTransaction().apply {
                this.payment = payment
                this.refundAmount = maxOf(refundAmount, 100) // Minimum 100 won
                refundReason = getRefundReason(payment.merchant!!.businessType)
                status = generateRefundStatus()

                val base = (payment.completedAt ?: payment.processedAt ?: payment.requestedAt)

                val earliestRequestedAt = base.plus(Duration.ofMinutes(1))
                val latestRequestedAt = minOf(base.plus(Duration.ofHours(6)), latestRefundRequestedAt)
                val refundRequestedAt = if (latestRequestedAt.isAfter(earliestRequestedAt)) {
                    val rangeMs = Duration.between(earliestRequestedAt, latestRequestedAt).toMillis().coerceAtLeast(1)
                    earliestRequestedAt.plusMillis(Random.nextLong(rangeMs))
                } else {
                    earliestRequestedAt
                }

                requestedAt = refundRequestedAt

                when (status) {
                    RefundStatus.REFUND_COMPLETED -> {
                        processedAt = requestedAt.plusMillis(sampleRefundProcessingDelayMs())
                        completedAt = processedAt!!.plusMillis(sampleRefundSettlementDelayMs())
                    }
                    RefundStatus.REFUND_FAILED -> {
                        processedAt = requestedAt.plusMillis(sampleRefundProcessingDelayMs())
                        failureReason = listOf(
                            "환불 기간 초과", "계좌 정보 오류", "시스템 오류", "환불 불가 상품"
                        ).random()
                    }
                    RefundStatus.REFUND_PROCESSING -> {
                        processedAt = requestedAt.plusMillis(sampleRefundProcessingDelayMs())
                    }
                    else -> { /* REFUND_PENDING - no additional timestamps */ }
                }
            }
            batch.add(refund)
            
            if (batch.size >= REFUND_BATCH_SIZE) {
                refundTransactionRepository.saveAll(batch)
                refundCounter.addAndGet(batch.size)
                batch.clear()
                logProgress("Refunds / 환불", refundCounter.get(), REFUND_COUNT)
            }
        }
        
        // Save remaining batch
        // 남은 배치 저장
        if (batch.isNotEmpty()) {
            refundTransactionRepository.saveAll(batch)
            refundCounter.addAndGet(batch.size)
        }
        
        logger.info("Refunds seeding complete: $REFUND_COUNT / 환불 시딩 완료: ${REFUND_COUNT}개")
    }
    
    /**
     * Seed admin user data.
     * 관리자 사용자 데이터를 시딩합니다.
     */
    @Transactional
    fun seedAdminUsers(headquarters: List<Headquarters>, merchants: List<Merchant>) {
        logger.info("Seeding $ADMIN_USER_COUNT admin users... / 관리자 ${ADMIN_USER_COUNT}개 시딩 중...")
        
        val batch = mutableListOf<AdminUser>()
        val roles = UserRole.entries.toTypedArray()
        val encodedPassword = passwordEncoder.encode("password123!")
        val encodedOperatorPassword = passwordEncoder.encode("admin123!")

        // 이미 존재하는 이메일은 다시 생성하지 않습니다.
        // Skip creating accounts that already exist (by email).
        val existingEmails = adminUserRepository.findAll().asSequence().map { it.email }.toHashSet()
        var skippedCount = 0
        
        // Ensure operator admin for demos (idempotent).
        // 데모용 운영사 관리자 계정 보장(멱등)
        run {
            val email = "admin@pgdemo.com"
            val existing = adminUserRepository.findByEmail(email)
            if (existing == null) {
                batch.add(
                    createAdminUser(
                        email = email,
                        role = UserRole.ADMIN,
                        tenantType = TenantType.OPERATOR,
                        tenantId = null,
                        encodedPassword = encodedOperatorPassword
                    ).apply {
                        name = "시스템 관리자"
                        status = "ACTIVE"
                    }
                )
                existingEmails.add(email)
            } else {
                existing.email = email
                existing.passwordHash = encodedOperatorPassword
                existing.name = "시스템 관리자"
                existing.tenantType = TenantType.OPERATOR
                existing.tenantId = null
                existing.role = UserRole.ADMIN
                existing.status = "ACTIVE"
                adminUserRepository.save(existing)
                existingEmails.add(email)
            }
        }

        // Create operator admins (platform-wide)
        // 운영자 관리자 생성 (플랫폼 전체)
        val operatorCount = minOf(50, ADMIN_USER_COUNT / 10)
        repeat(operatorCount) { index ->
            val email = "operator${index + 1}@pgdemo.com"
            if (existingEmails.contains(email)) {
                skippedCount += 1
                return@repeat
            }
            batch.add(createAdminUser(
                email = email,
                role = if (index < 5) UserRole.ADMIN else roles[Random.nextInt(roles.size)],
                tenantType = TenantType.OPERATOR,
                tenantId = null,
                encodedPassword = encodedPassword
            ))
            existingEmails.add(email)
        }
        
        // Create headquarters admins
        // 본사 관리자 생성
        val hqAdminCount = minOf(200, (ADMIN_USER_COUNT - operatorCount) / 2)
        repeat(hqAdminCount) { index ->
            val hq = headquarters[index % headquarters.size]
            val email =
                "hq${(index / (hqAdminCount / headquarters.size + 1)) + 1}_admin${(index % 10) + 1}@pgdemo.com"

            if (existingEmails.contains(email)) {
                skippedCount += 1
                return@repeat
            }

            batch.add(createAdminUser(
                email = email,
                role = roles[Random.nextInt(roles.size)],
                tenantType = TenantType.HEADQUARTERS,
                tenantId = hq.id,
                encodedPassword = encodedPassword
            ))
            existingEmails.add(email)
            
            if (batch.size >= ADMIN_BATCH_SIZE) {
                adminUserRepository.saveAll(batch)
                adminCounter.addAndGet(batch.size)
                batch.clear()
                logProgress("Admin Users / 관리자", adminCounter.get(), ADMIN_USER_COUNT)
            }
        }
        
        // Create merchant admins
        // 업체 관리자 생성
        val merchantAdminCount = ADMIN_USER_COUNT - operatorCount - hqAdminCount
        repeat(merchantAdminCount) { index ->
            val merchant = merchants[index % merchants.size]
            val email = "merchant${(index / 10) + 1}_admin${(index % 10) + 1}@pgdemo.com"

            if (existingEmails.contains(email)) {
                skippedCount += 1
                return@repeat
            }

            batch.add(createAdminUser(
                email = email,
                role = roles[Random.nextInt(roles.size)],
                tenantType = TenantType.MERCHANT,
                tenantId = merchant.id,
                encodedPassword = encodedPassword
            ))
            existingEmails.add(email)
            
            if (batch.size >= ADMIN_BATCH_SIZE) {
                adminUserRepository.saveAll(batch)
                adminCounter.addAndGet(batch.size)
                batch.clear()
                logProgress("Admin Users / 관리자", adminCounter.get(), ADMIN_USER_COUNT)
            }
        }

        // Ensure deterministic demo accounts for documentation/manual testing.
        // 문서/수동 테스트를 위한 고정 데모 계정(본사/업체) 보장
        val firstHeadquartersId = headquarters.firstOrNull()?.id
        val firstMerchantId = merchants.firstOrNull()?.id

        if (firstHeadquartersId != null) {
            val email = "hq_admin@pgdemo.com"
            val existing = adminUserRepository.findByEmail(email)
            if (existing == null) {
                batch.add(
                    createAdminUser(
                        email = email,
                        role = UserRole.ADMIN,
                        tenantType = TenantType.HEADQUARTERS,
                        tenantId = firstHeadquartersId,
                        encodedPassword = encodedPassword
                    ).apply {
                        name = "HQ Admin"
                        status = "ACTIVE"
                    }
                )
                existingEmails.add(email)
            } else {
                existing.email = email
                existing.passwordHash = encodedPassword
                existing.name = "HQ Admin"
                existing.tenantType = TenantType.HEADQUARTERS
                existing.tenantId = firstHeadquartersId
                existing.role = UserRole.ADMIN
                existing.status = "ACTIVE"
                adminUserRepository.save(existing)
                existingEmails.add(email)
            }
        }

        if (firstMerchantId != null) {
            val email = "merchant_admin@pgdemo.com"
            val existing = adminUserRepository.findByEmail(email)
            if (existing == null) {
                batch.add(
                    createAdminUser(
                        email = email,
                        role = UserRole.ADMIN,
                        tenantType = TenantType.MERCHANT,
                        tenantId = firstMerchantId,
                        encodedPassword = encodedPassword
                    ).apply {
                        name = "Merchant Admin"
                        status = "ACTIVE"
                    }
                )
                existingEmails.add(email)
            } else {
                existing.email = email
                existing.passwordHash = encodedPassword
                existing.name = "Merchant Admin"
                existing.tenantType = TenantType.MERCHANT
                existing.tenantId = firstMerchantId
                existing.role = UserRole.ADMIN
                existing.status = "ACTIVE"
                adminUserRepository.save(existing)
                existingEmails.add(email)
            }
        }
        
        // Save remaining batch
        // 남은 배치 저장
        if (batch.isNotEmpty()) {
            adminUserRepository.saveAll(batch)
            adminCounter.addAndGet(batch.size)
        }

        logger.info(
            "Admin users seeding complete: saved=${adminCounter.get()}, skipped=$skippedCount / 관리자 시딩 완료: 저장=${adminCounter.get()}개, 스킵=$skippedCount"
        )
    }
    
    // ======== Helper Methods / 헬퍼 메서드 ========
    
    private fun createAdminUser(
        email: String,
        role: UserRole,
        tenantType: TenantType,
        tenantId: java.util.UUID?,
        encodedPassword: String
    ): AdminUser {
        return AdminUser().apply {
            this.email = email
            passwordHash = encodedPassword
            name = "${LAST_NAMES[Random.nextInt(LAST_NAMES.size)]}${FIRST_NAMES[Random.nextInt(FIRST_NAMES.size)]}"
            this.tenantType = tenantType
            this.tenantId = tenantId
            this.role = role
            status = if (Random.nextInt(100) < 95) "ACTIVE" else "INACTIVE"
        }
    }
    
    private fun generatePaymentStatus(): PaymentStatus {
        return when (Random.nextInt(1000)) {
            in 0..974 -> PaymentStatus.PAYMENT_COMPLETED    // 97.5%
            in 975..976 -> PaymentStatus.PAYMENT_FAILED     // 0.2%
            in 977..991 -> PaymentStatus.PAYMENT_PROCESSING // 1.5%
            in 992..995 -> PaymentStatus.PAYMENT_PENDING    // 0.4%
            else -> PaymentStatus.PAYMENT_CANCELLED         // 0.4%
        }
    }

    private fun samplePaymentProcessingDelayMs(status: PaymentStatus): Long {
        val roll = Random.nextInt(1000)
        val base = when (roll) {
            in 0..799 -> Random.nextLong(80, 260)     // 80%: fast path
            in 800..949 -> Random.nextLong(260, 650)  // 15%: normal path
            in 950..989 -> Random.nextLong(650, 1200) // 4%: slow-ish tail
            else -> Random.nextLong(1200, 2500)       // 1%: rare outliers
        }
        return when (status) {
            PaymentStatus.PAYMENT_FAILED -> (base * 1.25).toLong().coerceAtMost(5000)
            PaymentStatus.PAYMENT_CANCELLED -> (base * 1.10).toLong().coerceAtMost(5000)
            else -> base
        }
    }

    private fun samplePaymentSettlementDelayMs(): Long {
        return when (Random.nextInt(1000)) {
            in 0..899 -> Random.nextLong(20, 160)   // 90%
            in 900..989 -> Random.nextLong(160, 420) // 9%
            else -> Random.nextLong(420, 900)        // 1%
        }
    }

    private fun samplePaymentCancellationDelayMs(): Long {
        return when (Random.nextInt(1000)) {
            in 0..899 -> Random.nextLong(60, 300)    // 90%
            in 900..989 -> Random.nextLong(300, 900) // 9%
            else -> Random.nextLong(900, 2000)       // 1%
        }
    }

    private fun generateRefundStatus(): RefundStatus {
        return when (Random.nextInt(1000)) {
            in 0..959 -> RefundStatus.REFUND_COMPLETED    // 96.0%
            in 960..964 -> RefundStatus.REFUND_FAILED     // 0.5%
            in 965..984 -> RefundStatus.REFUND_PROCESSING // 2.0%
            else -> RefundStatus.REFUND_PENDING           // 1.5%
        }
    }

    private fun sampleRefundProcessingDelayMs(): Long {
        return when (Random.nextInt(1000)) {
            in 0..799 -> Random.nextLong(800, 3_000)     // 80%
            in 800..949 -> Random.nextLong(3_000, 8_000) // 15%
            in 950..989 -> Random.nextLong(8_000, 20_000) // 4%
            else -> Random.nextLong(20_000, 60_000)      // 1%
        }
    }

    private fun getRefundReason(businessType: BusinessType?): String? {
        val type = businessType ?: BusinessType.OTHER
        val reasons = when (type) {
            BusinessType.CAFE, 
            BusinessType.RESTAURANT, 
            BusinessType.FAST_FOOD -> FNB_REFUND_REASONS
            
            BusinessType.RETAIL, 
            BusinessType.CONVENIENCE_STORE -> RETAIL_REFUND_REASONS
            
            BusinessType.OTHER -> FNB_REFUND_REASONS + RETAIL_REFUND_REASONS // Mix for others
        }
        return reasons[Random.nextInt(reasons.size)]
    }

    private fun sampleRefundSettlementDelayMs(): Long {
        return when (Random.nextInt(1000)) {
            in 0..899 -> Random.nextLong(2_000, 15_000)   // 90%
            in 900..989 -> Random.nextLong(15_000, 60_000) // 9%
            else -> Random.nextLong(60_000, 180_000)       // 1%
        }
    }
    
    private fun logProgress(entity: String, current: Int, total: Int) {
        val percentage = (current.toDouble() / total * 100).toInt()
        if (percentage % 10 == 0 || current == total) {
            logger.info("  [$entity] Progress: $current / $total ($percentage%)")
        }
    }
}
