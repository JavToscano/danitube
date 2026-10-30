package com.danitube.app.data.suggest

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The parent's switch for suggested videos: streaming them, finding more and auto-downloading. */
@Singleton
class SuggestionSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("suggestions", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
