package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.Headquarters
import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.enum.BusinessType
import com.example.pgdemo.common.domain.enum.StoreType
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.main.dto.HeadquartersResponse
import com.example.pgdemo.main.dto.MerchantResponse
import com.example.pgdemo.main.dto.SeedBootstrapRequest
import com.example.pgdemo.main.dto.SeedBootstrapResponse
import java.time.LocalDate
import kotlin.random.Random
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InternalSeedService(
    private val headquartersRepository: HeadquartersRepository,
    private val merchantRepository: MerchantRepository
) {
    private val companyNames = listOf(
        "스타벅스", "투썸플레이스", "이디야커피", "메가MGC커피", "컴포즈커피",
        "빽다방", "폴바셋", "할리스", "엔제리너스", "파스쿠찌",
        "맥도날드", "버거킹", "롯데리아", "KFC", "맘스터치",
        "파리바게뜨", "뚜레쥬르", "던킨", "배스킨라빈스", "성심당",
        "BBQ", "BHC", "교촌치킨", "굽네치킨", "네네치킨",
        "CU", "GS25", "세븐일레븐", "이마트24", "올리브영",
        "쿠팡", "네이버", "카카오", "배달의민족", "토스"
    )

    private val corpSuffixes = listOf("코리아", "리테일", "그룹", "파트너스", "홀딩스", "프랜차이즈")

    private val locations = listOf(
        "강남", "강남역", "역삼", "선릉", "삼성", "코엑스", "논현", "신사", "압구정", "청담",
        "홍대", "합정", "상수", "망원", "연남", "신촌", "이대", "공덕", "상암",
        "명동", "을지로", "종로", "광화문", "시청", "서울역", "용산", "이태원",
        "잠실", "석촌", "송파", "가락", "문정", "위례",
        "판교", "정자", "서현", "야탑", "광교", "영통",
        "송도", "센트럴파크", "청라"
    )

    private val branchSuffixes = listOf("점", "역점", "DT점", "센터점", "타워점", "몰점", "캠퍼스점")

    @Transactional
    fun bootstrap(request: SeedBootstrapRequest): SeedBootstrapResponse {
        val rng = request.seed?.let { Random(it) } ?: Random.Default

        val headquarters = ensureHeadquarters(request.headquartersCount, rng)
        val merchants = headquarters.flatMap { hq ->
            ensureMerchants(hq, request.merchantsPerHeadquarters, rng)
        }

        return SeedBootstrapResponse(
            headquarters = headquarters.map { it.toResponse() },
            merchants = merchants.map { it.toResponse() }
        )
    }

    private fun ensureHeadquarters(targetCount: Int, rng: Random): List<Headquarters> {
        val existing = headquartersRepository.findAll(PageRequest.of(0, targetCount)).content.toMutableList()
        val usedNames = headquartersRepository.findAll().asSequence().map { it.name.lowercase() }.toHashSet()

        var seq = existing.size + 1
        while (existing.size < targetCount) {
            val code = nextUniqueHeadquartersCode(seq)
            val baseName = companyNames[(seq - 1) % companyNames.size]
            val name = nextUniqueHeadquartersName(baseName, usedNames, rng)

            val hq = Headquarters().apply {
                headquartersCode = code
                this.name = name
                businessNumber = nextBusinessNumber(seq)
                contractType = if (rng.nextInt(100) < 25) "PREMIUM" else "STANDARD"
                status = "ACTIVE"
            }
            existing.add(headquartersRepository.save(hq))
            seq += 1
        }
        return existing
    }

    private fun ensureMerchants(hq: Headquarters, merchantsPerHeadquarters: Int, rng: Random): List<Merchant> {
        val hqId = hq.id ?: throw IllegalStateException("Headquarters id is missing")

        val existing = merchantRepository.findByHeadquartersId(hqId).toMutableList()
        val usedNames = existing.asSequence().map { it.name.lowercase() }.toHashSet()

        var seq = existing.size + 1
        while (existing.size < merchantsPerHeadquarters) {
            val merchantCode = nextUniqueMerchantCode(hq, seq)
            val name = nextUniqueMerchantName(hqId, hq.name, usedNames, rng, seq)

            val start = LocalDate.now().minusDays(rng.nextLong(0, 365 * 2L))
            val end = start.plusDays(rng.nextLong(30, 365 * 3L))

            val m = Merchant().apply {
                this.headquarters = hq
                this.merchantCode = merchantCode
                this.name = name
                this.storeType = if (rng.nextBoolean()) StoreType.FRANCHISE else StoreType.DIRECT
                this.businessType = BusinessType.entries[rng.nextInt(BusinessType.entries.size)]
                this.contractStartDate = start
                this.contractEndDate = end
                this.storeNumber = seq
                this.status = "ACTIVE"
            }

            existing.add(merchantRepository.save(m))
            usedNames.add(name.lowercase())
            seq += 1
        }

        return existing
    }

    private fun nextUniqueHeadquartersCode(seq: Int): String {
        // HQ code is globally unique.
        var n = seq
        while (true) {
            val code = "SEEDHQ" + n.toString().padStart(5, '0')
            if (!headquartersRepository.existsByHeadquartersCode(code)) {
                return code
            }
            n += 1
        }
    }

    private fun nextUniqueHeadquartersName(baseName: String, usedNamesLower: MutableSet<String>, rng: Random): String {
        val lowered = baseName.lowercase()
        if (!usedNamesLower.contains(lowered) && !headquartersRepository.existsByNameIgnoreCase(baseName)) {
            usedNamesLower.add(lowered)
            return baseName
        }

        for (suffix in corpSuffixes.shuffled(rng)) {
            val candidate = "$baseName $suffix"
            val candidateLower = candidate.lowercase()
            if (!usedNamesLower.contains(candidateLower) && !headquartersRepository.existsByNameIgnoreCase(candidate)) {
                usedNamesLower.add(candidateLower)
                return candidate
            }
        }

        var n = 2
        while (true) {
            val candidate = "$baseName $n"
            val candidateLower = candidate.lowercase()
            if (!usedNamesLower.contains(candidateLower) && !headquartersRepository.existsByNameIgnoreCase(candidate)) {
                usedNamesLower.add(candidateLower)
                return candidate
            }
            n += 1
        }
    }

    private fun nextUniqueMerchantCode(hq: Headquarters, seq: Int): String {
        val hqCode = hq.headquartersCode.takeLast(5)
        var n = seq
        while (true) {
            val code = "SEEDM" + hqCode + n.toString().padStart(5, '0')
            if (!merchantRepository.existsByMerchantCode(code)) {
                return code
            }
            n += 1
        }
    }

    private fun nextUniqueMerchantName(
        headquartersId: java.util.UUID,
        hqName: String,
        usedNamesLower: MutableSet<String>,
        rng: Random,
        seq: Int
    ): String {
        var attempts = 0
        while (attempts < 50) {
            val location = locations[rng.nextInt(locations.size)]
            val suffix = branchSuffixes[rng.nextInt(branchSuffixes.size)]
            val candidate = "$hqName ${location}${suffix}"
            val lowered = candidate.lowercase()
            if (!usedNamesLower.contains(lowered) && !merchantRepository.existsByHeadquartersIdAndNameIgnoreCase(
                    headquartersId = headquartersId,
                    name = candidate
                )) {
                usedNamesLower.add(lowered)
                return candidate
            }
            attempts += 1
        }

        val fallback = "$hqName ${locations[seq % locations.size]}${seq}호점"
        usedNamesLower.add(fallback.lowercase())
        return fallback
    }

    private fun nextBusinessNumber(seq: Int): String {
        // Not required to be unique in schema; keep it plausible.
        val a = 100 + (seq % 900)
        val b = 10 + (seq % 90)
        val c = 10000 + (seq % 90000)
        return "$a-$b-$c"
    }

    private fun Headquarters.toResponse(): HeadquartersResponse {
        val id = this.id ?: throw IllegalStateException("Headquarters id is missing")
        return HeadquartersResponse(
            id = id,
            headquartersCode = headquartersCode,
            name = name,
            status = status
        )
    }

    private fun Merchant.toResponse(): MerchantResponse {
        val id = this.id ?: throw IllegalStateException("Merchant id is missing")
        return MerchantResponse(
            id = id,
            merchantCode = merchantCode,
            name = name,
            storeType = storeType,
            businessType = businessType,
            status = status,
            contractStartDate = contractStartDate,
            contractEndDate = contractEndDate,
            storeNumber = storeNumber,
            headquartersId = headquarters?.id
        )
    }
}
