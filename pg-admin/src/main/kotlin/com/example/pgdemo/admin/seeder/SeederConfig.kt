package com.example.pgdemo.admin.seeder

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Configuration class for data seeder.
 * 데이터 시더를 위한 설정 클래스.
 *
 * Only active when the "seeder" profile is enabled.
 * "seeder" 프로필이 활성화된 경우에만 동작합니다.
 */
@Configuration
@Profile("seeder")
@EnableConfigurationProperties(SeederProperties::class)
class SeederConfig
