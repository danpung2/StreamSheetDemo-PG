package com.example.pgdemo.main.service

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class MerchantHeadquartersBackfillRunner(
    private val merchantService: MerchantService
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        merchantService.backfillHeadquartersForOrphanMerchants()
    }
}
