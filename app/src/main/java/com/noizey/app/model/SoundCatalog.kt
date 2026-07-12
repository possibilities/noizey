package com.noizey.app.model

enum class SoundCategory(val label: String) {
    GENERATED("Generated"),
    NATURE("Nature"),
}

enum class GeneratorKind {
    WHITE,
    PINK,
    BROWN,
    GRAY,
    GREEN,
    BLUE,
    VIOLET,
    DEEP_FAN,
    CABIN_HUM,
    SOFT_RAIN,
    RAIN_WINDOW,
    HEAVY_RAIN,
    DISTANT_THUNDER,
    OCEAN,
    STREAM,
    WATERFALL,
    WIND_TREES,
    FOREST_NIGHT,
    FIREPLACE,
}

data class SoundDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: SoundCategory,
    val kind: GeneratorKind,
    val defaultVolume: Float,
)

object SoundCatalog {
    val generated = listOf(
        SoundDefinition("brown", "Brown noise", "Deep, soft low-frequency hush", SoundCategory.GENERATED, GeneratorKind.BROWN, 0.62f),
        SoundDefinition("pink", "Pink noise", "Balanced and gentle across octaves", SoundCategory.GENERATED, GeneratorKind.PINK, 0.55f),
        SoundDefinition("white", "White noise", "Bright, even broadband masking", SoundCategory.GENERATED, GeneratorKind.WHITE, 0.42f),
        SoundDefinition("gray", "Gray noise", "Perceptually balanced, smooth detail", SoundCategory.GENERATED, GeneratorKind.GRAY, 0.46f),
        SoundDefinition("green", "Green noise", "Calm, centered natural spectrum", SoundCategory.GENERATED, GeneratorKind.GREEN, 0.50f),
        SoundDefinition("blue", "Blue noise", "Crisp high-frequency veil", SoundCategory.GENERATED, GeneratorKind.BLUE, 0.32f),
        SoundDefinition("violet", "Violet noise", "Airy, precise upper-frequency mask", SoundCategory.GENERATED, GeneratorKind.VIOLET, 0.26f),
        SoundDefinition("deep_fan", "Deep fan", "Steady air with a low motor bed", SoundCategory.GENERATED, GeneratorKind.DEEP_FAN, 0.52f),
        SoundDefinition("cabin_hum", "Cabin hum", "Low mechanical drone and soft air", SoundCategory.GENERATED, GeneratorKind.CABIN_HUM, 0.50f),
    )

    val nature = listOf(
        SoundDefinition("soft_rain", "Soft rain", "Fine rain falling at a distance", SoundCategory.NATURE, GeneratorKind.SOFT_RAIN, 0.50f),
        SoundDefinition("rain_window", "Rain on window", "Close droplets against glass", SoundCategory.NATURE, GeneratorKind.RAIN_WINDOW, 0.46f),
        SoundDefinition("heavy_rain", "Heavy rain", "Dense, enveloping downpour", SoundCategory.NATURE, GeneratorKind.HEAVY_RAIN, 0.43f),
        SoundDefinition("distant_thunder", "Distant thunder", "Occasional low rolling rumbles", SoundCategory.NATURE, GeneratorKind.DISTANT_THUNDER, 0.42f),
        SoundDefinition("ocean", "Ocean waves", "Slow swells folding onto shore", SoundCategory.NATURE, GeneratorKind.OCEAN, 0.56f),
        SoundDefinition("stream", "Running stream", "Clear water moving over stones", SoundCategory.NATURE, GeneratorKind.STREAM, 0.48f),
        SoundDefinition("waterfall", "Waterfall", "Wide, constant wall of water", SoundCategory.NATURE, GeneratorKind.WATERFALL, 0.44f),
        SoundDefinition("wind_trees", "Wind in trees", "Breathing gusts through leaves", SoundCategory.NATURE, GeneratorKind.WIND_TREES, 0.48f),
        SoundDefinition("forest_night", "Forest night", "Night air and distant crickets", SoundCategory.NATURE, GeneratorKind.FOREST_NIGHT, 0.46f),
        SoundDefinition("fireplace", "Fireplace", "Warm ember bed with soft crackle", SoundCategory.NATURE, GeneratorKind.FIREPLACE, 0.42f),
    )

    val all = generated + nature
    val byId = all.associateBy(SoundDefinition::id)
}
