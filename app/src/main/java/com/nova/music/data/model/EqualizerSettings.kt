package com.nova.music.data.model

data class EqualizerSettings(
    val presetName: String = "Custom",
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f,
    val bands: List<BandLevel> = emptyList()
)

data class BandLevel(
    val frequency: Int,
    val level: Float
)

enum class EqualizerPreset(val displayName: String) {
    NORMAL("Normal"),
    CLASSICAL("Classical"),
    DANCE("Dance"),
    JAZZ("Jazz"),
    POP("Pop"),
    ROCK("Rock"),
    BASS_BOOST("Bass Boost"),
    TREBLE_BOOST("Treble Boost")
} 