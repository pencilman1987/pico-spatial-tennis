package com.example.spatialtennis.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.spatialtennis.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
