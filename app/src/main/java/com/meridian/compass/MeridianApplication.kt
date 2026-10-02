package com.meridian.compass

import android.app.Application
import com.meridian.compass.data.AppDatabase
import com.meridian.compass.data.TrailRepository
import com.meridian.compass.data.WaypointRepository
import com.meridian.compass.settings.SettingsRepository

class MeridianApplication : Application() {
    lateinit var waypointRepository: WaypointRepository
        private set
    lateinit var trailRepository: TrailRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        waypointRepository = WaypointRepository(db.waypointDao())
        trailRepository = TrailRepository(db.trailDao())
        settingsRepository = SettingsRepository(this)
    }
}
