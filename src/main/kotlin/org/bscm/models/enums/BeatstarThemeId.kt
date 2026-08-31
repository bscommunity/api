package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class BeatstarThemeId {
    // rock
    BAYOU,
    CHROME_SKULL,
    FINAL_BATTLE,
    HADES,
    HAUNTED_HOUSE,
    HELP_THE_EARTH,
    JULY_4TH,
    LIQUID_CHROME,
    RAVEN,
    ROCK_CITYSCAPE,
    THOR,
    ULTRAVIOLET,

    // pop
    BLUE_DUNES,
    EGYPTIAN_GOLD,
    FRIGHT_NIGHT,
    HEARTBEATS,
    PRIDE_2024,
    STAINED_GLASS,
    SUMMER_SUNDOWN,
    WOMEN_S_DAY,
    BREAK_FREE,
    INK_FLOW,
    LIQUID_CANDY,
    _30_SECONDS_TO_MARS,
    TROLLS_BAND_TOGETHER,
    BAND_TOGETHER,
    CHRISTMAS_JUMPER,
    ORBIT,
    LUNAR_NEW_YEAR_DRAGON,
    POP_CITYSCAPE,
    HAPPY_BIRTHDAY,
    LATIN_SOUNDS,
    MOON_MUSIC,
    JOHN_LENNON,
    EASTER_EGGS,
    LATIN_HEAT,
    MOTHER_S_DAY_2025,
    WORLD_SONG_CHAMPIONSHIP,

    // alternative
    BLACK_INK,
    THANKSGIVING,
    PSYCHEDELIC,
    LIQUID_URANIUM,
    ST_PATRICK_S_DAY,
    EGGSTRAVAGANZA,
    ALTERNATIVE_CITYSCAPE,
    THE_KILLERS,
    DAY_OF_THE_DEAD,
    FESTIVE_CANDY,
    CELESTIAL_CATS,
    BUDDHA,
    POSEIDON,

    // hip-hop
    FREE_THROW_FRENZY,
    DRAGON,
    GAME_TIME,
    GRAFFITI,
    LIQUID_FUEL,
    ROYAL_PANTHER,
    OVERSPRAY,
    HIP_HOP_CITYSCAPE,
    SAPPHIRE_SHARDS,
    YEAR_OF_THE_SNAKE,
    ICE,
    GOLDEN_WEEK,
    ANUBIS,
    BASKETBALL,

    // universal
    AMATERASU,
    MENTAL_HEALTH_AWARENESS,
    ODIN,
    RA,
    ZEUS,

    // rnb
    LIQUID_GOLD,
    NEON_RADIANCE,
    ROSE_GOLD,
    PALM_SKIES,
    VALENTINES,
    RNB_CITYSCAPE,
    CHERRY_BLOSSOM,
    GREEN_GAME_JAM_2024,
    _24K_GOLD,
    WARM_BLOSSOM,
    PEACOCK,
    JUNETEENTH,

    // dance
    FASTLANE,
    FRACTURED_SOUNDS,
    PLASMA,
    SONIC_WAVES,
    CARNAVAL,
    DANCE_CITYSCAPE,
    AMETHYST_SHARDS,
    CRETACEOUS,
    CARNAVAL_2025,
    DISCO_FLAMINGO,
    SONGKRAN,
    OUR_POWER_OUR_PLANET,
    NEON,
    AVALON_SOUNDWAVE,

    // country
    SPOTLIGHTS,
    LIQUID_AMBER,
    COUNTRY_CITYSCAPE,
    BLACK_STALLION,
    MEMORIAL_DAY,
    FATHERS_DAY;

    companion object {
        private val byBeatstarId = entries.associateBy { it.toBeatstarId() }

        /**
         * Converts from the original Beatstar theme ID format (e.g. "chrome-skull")
         * to the enum value (e.g. CHROME_SKULL).
         */
        fun fromBeatstarId(id: String): BeatstarThemeId =
            byBeatstarId[id]
                ?: throw IllegalArgumentException("Unknown Beatstar theme ID: $id")

        /**
         * Converts the enum value back to the original Beatstar theme ID format
         * (e.g. CHROME_SKULL → "chrome-skull").
         */
        fun BeatstarThemeId.toBeatstarId(): String =
            name.removePrefix("_").lowercase().replace('_', '-')
    }
}
