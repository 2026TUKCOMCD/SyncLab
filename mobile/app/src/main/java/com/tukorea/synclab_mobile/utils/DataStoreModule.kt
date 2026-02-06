package com.tukorea.synclab_mobile.utils // utils(복수형)인지 확인하세요!

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// 변수명을 userSettingsStore로 변경하여 ViewModel과 맞춥니다.
val Context.userSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")