package com.example.spatialtennis.data.repository

import android.content.Context
import com.example.spatialtennis.domain.model.CupProgress

class SharedPreferencesCupProgressRepository(context: Context) : CupProgressRepository {
    private val preferences =
        context.getSharedPreferences("spatial_tennis_progress", Context.MODE_PRIVATE)

    override fun load(): CupProgress =
        CupProgress(
            championCount = preferences.getInt(KEY_CHAMPION_COUNT, 0),
            bestStage = preferences.getInt(KEY_BEST_STAGE, 0),
        )

    override fun save(progress: CupProgress) {
        preferences.edit()
            .putInt(KEY_CHAMPION_COUNT, progress.championCount)
            .putInt(KEY_BEST_STAGE, progress.bestStage)
            .apply()
    }

    private companion object {
        const val KEY_CHAMPION_COUNT = "champion_count"
        const val KEY_BEST_STAGE = "best_stage"
    }
}
