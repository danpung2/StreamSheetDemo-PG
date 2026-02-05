package com.example.pgdemo.admin.seeder

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.annotation.Profile
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * Command line runner for data seeding.
 * 데이터 시딩을 위한 커맨드라인 러너.
 * 
 * This runner is only active when the "seeder" profile is enabled.
 * 이 러너는 "seeder" 프로필이 활성화된 경우에만 동작합니다.
 * 
 * Usage / 사용법:
 * - Start application with --spring.profiles.active=seeder
 * - 애플리케이션을 --spring.profiles.active=seeder 옵션으로 실행
 * 
 * Example / 예시:
 * ```
 * java -jar pg-admin.jar --spring.profiles.active=seeder
 * ```
 * 
 * Or via Gradle / 또는 Gradle로:
 * ```
 * ./gradlew :pg-admin:bootRun --args='--spring.profiles.active=seeder'
 * ```
 *
 * Reset + seed (WARNING: wipes relational DB + Mongo demo collections) / 리셋+시딩(주의: DB/Mongo 데이터 삭제):
 * ```
 * ./gradlew :pg-admin:bootRun --args='--spring.profiles.active=seeder --pgdemo.seeder.reset=true'
 * ```
 */
@Component
@Profile("seeder")
@Order(1)
class DataSeederRunner(
    private val dataSeeder: DataSeeder,
    @Value("\${pgdemo.seeder.reset:false}") private val resetBeforeSeed: Boolean,
    private val applicationContext: ConfigurableApplicationContext
) : CommandLineRunner {
    
    companion object {
        private val logger = LoggerFactory.getLogger(DataSeederRunner::class.java)
    }
    
    override fun run(vararg args: String?) {
        logger.info("============================================")
        logger.info("  Data Seeder Runner Activated / 데이터 시더 러너 활성화")
        logger.info("============================================")
        
        try {
            if (resetBeforeSeed) {
                logger.warn("RESET enabled: deleting existing data before seeding / RESET 활성화: 시딩 전 기존 데이터 삭제")
                dataSeeder.resetAll()
            }
            dataSeeder.seedAll()
            
            logger.info("============================================")
            logger.info("  Seeding completed successfully / 시딩 성공적으로 완료")
            logger.info("  You can now restart without 'seeder' profile")
            logger.info("  이제 'seeder' 프로필 없이 재시작하세요")
            logger.info("============================================")

            // Ensure one-shot seeding process terminates (for docker-compose orchestration).
            val exitCode = SpringApplication.exit(applicationContext)
            exitProcess(exitCode)
        } catch (e: Exception) {
            logger.error("Failed to seed data / 데이터 시딩 실패", e)
            throw e
        }
    }
}
