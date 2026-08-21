package com.example.spatialtennis.data.repository

import com.example.spatialtennis.domain.model.CupProgress

interface CupProgressRepository {
    fun load(): CupProgress

    fun save(progress: CupProgress)
}
