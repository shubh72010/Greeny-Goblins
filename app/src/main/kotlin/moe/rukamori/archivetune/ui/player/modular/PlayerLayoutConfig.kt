package moe.rukamori.archivetune.ui.player.modular

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.utils.dataStore

val PlayerLayoutKey = stringPreferencesKey("modular_player_layout")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

enum class PlayerLayoutPreset(val id: String) {
    CLASSIC("classic"),
    COMPACT("compact"),
    MINIMAL("minimal"),
    CUSTOM("custom"),
}

object PlayerLayoutRepository {
    fun observeLayoutPreset(context: Context): Flow<PlayerLayoutPreset> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[PlayerLayoutKey] ?: PlayerLayoutPreset.CLASSIC.id
            PlayerLayoutPreset.entries.find { it.id == raw } ?: PlayerLayoutPreset.CLASSIC
        }
    }

    suspend fun getLayoutPreset(context: Context): PlayerLayoutPreset {
        return context.dataStore.data.first().let { prefs ->
            val raw = prefs[PlayerLayoutKey] ?: PlayerLayoutPreset.CLASSIC.id
            PlayerLayoutPreset.entries.find { it.id == raw } ?: PlayerLayoutPreset.CLASSIC
        }
    }

    suspend fun setLayoutPreset(context: Context, preset: PlayerLayoutPreset) {
        context.dataStore.edit { it[PlayerLayoutKey] = preset.id }
    }

    fun getLayout(preset: PlayerLayoutPreset): PlayerLayout = when (preset) {
        PlayerLayoutPreset.CLASSIC -> DefaultPlayerLayouts.classic
        PlayerLayoutPreset.COMPACT -> DefaultPlayerLayouts.compact
        PlayerLayoutPreset.MINIMAL -> DefaultPlayerLayouts.minimal
        PlayerLayoutPreset.CUSTOM -> DefaultPlayerLayouts.classic
    }

    private val customLayoutKey = stringPreferencesKey("modular_player_custom_layout")

    fun observeCustomLayout(context: Context): Flow<PlayerLayout?> {
        return context.dataStore.data.map { prefs ->
            prefs[customLayoutKey]?.let { raw ->
                runCatching { json.decodeFromString<PlayerLayout>(raw) }.getOrNull()
            }
        }
    }

    suspend fun saveCustomLayout(context: Context, layout: PlayerLayout) {
        context.dataStore.edit { it[customLayoutKey] = json.encodeToString(layout) }
    }

    suspend fun getCustomLayout(context: Context): PlayerLayout? {
        return context.dataStore.data.first()[customLayoutKey]?.let { raw ->
            runCatching { json.decodeFromString<PlayerLayout>(raw) }.getOrNull()
        }
    }
}
