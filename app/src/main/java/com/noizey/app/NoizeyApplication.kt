package com.noizey.app

import android.app.Application
import com.noizey.app.data.PreferencesRepository
import com.noizey.app.playback.PlaybackStore

class NoizeyApplication : Application() {
    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
        PlaybackStore.initialize(preferencesRepository)
    }
}
