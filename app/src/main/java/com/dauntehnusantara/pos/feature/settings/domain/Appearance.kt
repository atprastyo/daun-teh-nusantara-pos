package com.dauntehnusantara.pos.feature.settings.domain

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { LIGHT, DARK }
interface AppearanceRepository {
    val theme:Flow<ThemeMode>
    suspend fun setTheme(mode:ThemeMode)
}
