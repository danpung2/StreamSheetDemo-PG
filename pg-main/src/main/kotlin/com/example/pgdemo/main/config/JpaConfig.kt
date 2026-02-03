package com.example.pgdemo.main.config

import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(
    basePackageClasses = [
        PaymentTransactionRepository::class,
        RefundTransactionRepository::class,
        MerchantRepository::class
    ]
)
@EntityScan(basePackageClasses = [PaymentTransaction::class, RefundTransaction::class, Merchant::class])
class JpaConfig
