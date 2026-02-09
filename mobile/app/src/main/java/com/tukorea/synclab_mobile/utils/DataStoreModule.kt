package com.tukorea.synclab_mobile.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.userSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")