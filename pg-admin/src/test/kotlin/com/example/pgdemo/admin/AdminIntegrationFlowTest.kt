package com.example.pgdemo.admin

import com.example.pgdemo.admin.dto.LoginRequest
import com.example.pgdemo.admin.dto.RefreshRequest
import com.example.pgdemo.admin.dto.TokenResponse
import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.RefreshToken
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import com.example.pgdemo.common.domain.repository.RefreshTokenRepository
import javax.sql.DataSource
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=update",
        "security.jwt.secret=test-jwt-secret-for-integration-tests-minimum-32-bytes"
    ]
)
@Import(AdminIntegrationFlowTest.TestJpaConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("관리자 기능 통합 플로우 테스트")
class AdminIntegrationFlowTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("pgdemo")
            withUsername("pgdemo")
            withPassword("pgdemo")
        }

        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:6.0")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var adminUserRepository: AdminUserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private var adminUserId: java.util.UUID? = null

    @BeforeAll
    @DisplayName("데이터베이스 연결 확인")
    fun verifyDatabaseConnection() {
        try {
            dataSource.connection.use { connection ->
                if (!connection.isValid(2)) {
                    fail("Database connection is not valid.")
                }
            }
            mongoTemplate.executeCommand("{ ping: 1 }")
        } catch (ex: Exception) {
            fail("Database connection failed.", ex)
        }
    }

    @AfterEach
    @DisplayName("테스트 데이터 정리")
    fun cleanup() {
        val userId = adminUserId
        if (userId != null) {
            val tokens = refreshTokenRepository.findByAdminUserId(userId)
            if (tokens.isNotEmpty()) {
                refreshTokenRepository.deleteAll(tokens)
            }
            adminUserRepository.deleteById(userId)
            adminUserId = null
        }
    }

    @Test
    @DisplayName("로그인, 토큰 갱신, 로그아웃 플로우 테스트")
    fun `login refresh logout flow`() {
        val password = "password123!"
        val email = "integration-${System.nanoTime()}@pgdemo.com"
        val adminUser = AdminUser().apply {
            this.email = email
            passwordHash = passwordEncoder.encode(password)
            name = "Integration Admin"
            tenantType = TenantType.OPERATOR
            role = UserRole.ADMIN
            status = "ACTIVE"
        }
        val savedUser = adminUserRepository.save(adminUser)
        adminUserId = savedUser.id

        val loginResponse = post(
            path = "/api/auth/login",
            request = LoginRequest(email = email, password = password),
            responseType = TokenResponse::class.java
        )

        assertThat(loginResponse.statusCode).isEqualTo(HttpStatus.OK)
        val loginTokens = loginResponse.body
        requireNotNull(loginTokens)
        assertThat(loginTokens.accessToken).isNotBlank()
        assertThat(loginTokens.refreshToken).isNotBlank()

        val refreshResponse = post(
            path = "/api/auth/refresh",
            request = RefreshRequest(refreshToken = loginTokens.refreshToken),
            responseType = TokenResponse::class.java
        )

        assertThat(refreshResponse.statusCode).isEqualTo(HttpStatus.OK)
        val refreshTokens = refreshResponse.body
        requireNotNull(refreshTokens)

        val logoutResponse = post(
            path = "/api/auth/logout",
            request = null,
            responseType = Void::class.java,
            bearerToken = refreshTokens.accessToken
        )

        assertThat(logoutResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
    }

    @Test
    @DisplayName("엑셀 다운로드 시 헤더 및 바디 응답 확인")
    fun `export download returns headers and body`() {
        val password = "password123!"
        val email = "integration-export-${System.nanoTime()}@pgdemo.com"
        val adminUser = AdminUser().apply {
            this.email = email
            passwordHash = passwordEncoder.encode(password)
            name = "Integration Export Admin"
            tenantType = TenantType.OPERATOR
            role = UserRole.ADMIN
            status = "ACTIVE"
        }
        val savedUser = adminUserRepository.save(adminUser)
        adminUserId = savedUser.id

        val loginResponse = post(
            path = "/api/auth/login",
            request = LoginRequest(email = email, password = password),
            responseType = TokenResponse::class.java
        )

        assertThat(loginResponse.statusCode).isEqualTo(HttpStatus.OK)
        val loginTokens = loginResponse.body
        requireNotNull(loginTokens)

        val startDate = LocalDate.now().minusDays(1).toString()
        val endDate = LocalDate.now().toString()
        val url = "http://localhost:$port/admin/exports/payments/download?startDate=$startDate&endDate=$endDate"
        val headers = HttpHeaders().apply {
            setBearerAuth(loginTokens.accessToken)
        }
        val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), ByteArray::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType?.toString())
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        assertThat(response.headers["Content-Disposition"]).isNotNull
        val body = response.body
        requireNotNull(body)
        assertThat(body.isNotEmpty()).isTrue()
    }

    private fun <T> post(
        path: String,
        request: Any?,
        responseType: Class<T>,
        bearerToken: String? = null
    ): ResponseEntity<T> {
        val url = "http://localhost:$port$path"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (!bearerToken.isNullOrBlank()) {
                setBearerAuth(bearerToken)
            }
        }
        val entity = HttpEntity(request, headers)
        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType)
    }

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [AdminUserRepository::class, RefreshTokenRepository::class])
    @EntityScan(basePackageClasses = [AdminUser::class, RefreshToken::class])
    class TestJpaConfig
}
