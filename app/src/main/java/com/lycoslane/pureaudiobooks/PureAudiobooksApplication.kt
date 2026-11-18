package com.lycoslane.pureaudiobooks

import android.app.Application
import com.lycoslane.pureaudiobooks.core.audio.PlayerController
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDatabase
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import com.lycoslane.pureaudiobooks.domain.repository.AudiobookRepository

class PureAudiobooksApplication : Application() {

    lateinit var preferences: AppPreferences
        private set

    lateinit var database: PureAudiobooksDatabase
        private set

    lateinit var repository: AudiobookRepository
        private set

    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        database = PureAudiobooksDatabase.getInstance(this)
        repository = AudiobookRepository(this, database.dao(), preferences)
        playerController = PlayerController(this, database.dao(), preferences)
    }
}
