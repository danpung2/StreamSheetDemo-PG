package com.example.pgdemo.admin.seeder

import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.Headquarters
import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.entity.RefundTransaction
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
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import org.slf4j.LoggerFactory
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
        
        // Refund reasons for random selection
        // 랜덤 선택을 위한 환불 사유
        private val REFUND_REASONS = listOf(
            "고객 변심", "상품 불량", "배송 지연", "오배송", "주문 취소", 
            "사이즈 교환", "품절", "가격 오류", "중복 결제", null
        )
        
        // Sample company names
        // 샘플 회사명
        private val COMPANY_NAMES = listOf(
            "스타벅스", "투썸플레이스", "이디야커피", "맥도날드", "버거킹", 
            "롯데리아", "서브웨이", "파리바게뜨", "뚜레쥬르", "CU",
            "GS25", "세븐일레븐", "이마트24", "올리브영", "다이소",
            "무신사", "29CM", "쿠팡", "마켓컬리", "배달의민족"
        )
        
        private val LAST_NAMES = listOf("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")
        private val FIRST_NAMES = listOf("민준", "서연", "하준", "지우", "도윤", "수아", "예준", "하은", "시우", "지아")
    }
    
    // Counters for progress tracking
    // 진행 상황 추적을 위한 카운터
    private val headquartersCounter = AtomicInteger(0)
    private val merchantCounter = AtomicInteger(0)
    private val paymentCounter = AtomicInteger(0)
    private val refundCounter = AtomicInteger(0)
    private val adminCounter = AtomicInteger(0)
    
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
        
        repeat(HEADQUARTERS_COUNT) { index ->
            val hq = Headquarters().apply {
                headquartersCode = "HQ${String.format("%05d", index + 1)}"
                name = "${COMPANY_NAMES[index % COMPANY_NAMES.size]} 본사 ${(index / COMPANY_NAMES.size) + 1}"
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
        
        repeat(MERCHANT_COUNT) { index ->
            val hq = headquarters[index % headquarters.size]
            val merchant = Merchant().apply {
                merchantCode = "M${String.format("%08d", index + 1)}"
                this.headquarters = hq
                name = "${hq.name.replace(" 본사", "")} ${generateBranchName(index)}"
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
                refundReason = REFUND_REASONS[Random.nextInt(REFUND_REASONS.size)]
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

        // 이미 존재하는 이메일은 다시 생성하지 않습니다.
        // Skip creating accounts that already exist (by email).
        val existingEmails = adminUserRepository.findAll().asSequence().map { it.email }.toHashSet()
        var skippedCount = 0
        
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
    
    private fun generateBranchName(index: Int): String {
        val districts = listOf("강남", "홍대", "신촌", "명동", "종로", "이태원", "압구정", "여의도", "광화문", "잠실")
        val suffixes = listOf("점", "역점", "본점", "지점", "센터점")
        return "${districts[index % districts.size]}${suffixes[Random.nextInt(suffixes.size)]}"
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
