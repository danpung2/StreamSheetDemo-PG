package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.Headquarters
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.main.dto.HeadquartersRequest
import com.example.pgdemo.main.dto.HeadquartersResponse
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HeadquartersService(
    private val headquartersRepository: HeadquartersRepository
) {
    @Transactional
    fun createHeadquarters(request: HeadquartersRequest): HeadquartersResponse {
        if (headquartersRepository.existsByHeadquartersCode(request.headquartersCode)) {
            throw IllegalArgumentException("headquartersCode already exists")
        }
        if (headquartersRepository.existsByNameIgnoreCase(request.name)) {
            throw IllegalArgumentException("headquarters name already exists")
        }

        val hq = Headquarters().apply {
            headquartersCode = request.headquartersCode
            name = request.name
            businessNumber = request.businessNumber
            contractType = request.contractType
            status = request.status?.takeIf { it.isNotBlank() } ?: "ACTIVE"
        }
        val saved = headquartersRepository.save(hq)
        val id = saved.id ?: throw IllegalStateException("Headquarters id is missing")
        return HeadquartersResponse(
            id = id,
            headquartersCode = saved.headquartersCode,
            name = saved.name,
            status = saved.status
        )
    }

    @Transactional(readOnly = true)
    fun getHeadquarters(id: UUID): HeadquartersResponse {
        val hq = headquartersRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Headquarters not found") }
        val hqId = hq.id ?: throw IllegalStateException("Headquarters id is missing")
        return HeadquartersResponse(
            id = hqId,
            headquartersCode = hq.headquartersCode,
            name = hq.name,
            status = hq.status
        )
    }

    @Transactional(readOnly = true)
    fun listHeadquarters(pageable: Pageable): Page<HeadquartersResponse> {
        return headquartersRepository.findAll(pageable)
            .map { hq ->
                val hqId = hq.id ?: throw IllegalStateException("Headquarters id is missing")
                HeadquartersResponse(
                    id = hqId,
                    headquartersCode = hq.headquartersCode,
                    name = hq.name,
                    status = hq.status
                )
            }
    }
}
