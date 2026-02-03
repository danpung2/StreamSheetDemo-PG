package com.example.pgdemo.main.config

import org.springframework.context.annotation.Configuration

/**
 * JPA 설정 확장 포인트.
 * JPA configuration extension point.
 *
 * NOTE: Repository/Entity 스캔은 PgMainApplication의 @EnableJpaRepositories/@EntityScan에서 수행합니다.
 *       Repository/Entity scanning is done in PgMainApplication.
 */
@Configuration
class JpaConfig
