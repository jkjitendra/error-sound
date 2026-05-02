package com.drostwades.errorsound

data class BuiltInSound(
    val id: String,
    val displayName: String,
    val resourcePath: String,
) {
    override fun toString(): String = displayName
}

object BuiltInSounds {
    const val CUSTOM_FILE_ID: String = "__custom_file__"

    val all: List<BuiltInSound> = listOf(
        BuiltInSound("boom", "Boom", "/audios/boom.wav"),
        BuiltInSound("dog_laughing_meme", "Dog Laughing Meme", "/audios/dog_laughing_meme.wav"),
        BuiltInSound("faaa", "Faaa", "/audios/faaa.wav"),
        BuiltInSound("huh", "Huh", "/audios/huh.wav"),
        BuiltInSound("punch", "Punch", "/audios/punch.wav"),
        BuiltInSound("yeah_boy", "Yeah Boy", "/audios/yeah_boy.wav"),
        BuiltInSound("yooo", "Yooo", "/audios/yooo.wav"),
        BuiltInSound("snoring_meme", "Snoring", "/audios/snoring_meme.wav"),
        BuiltInSound("bruh", "Bruh", "/audios/bruh.wav"),
        BuiltInSound("metal_gear", "Metal Gear Alert", "/audios/metal_gear_alert.wav"),
        BuiltInSound("roblox_death", "Roblox death", "/audios/roblox_death.wav"),
        BuiltInSound("minecraft_damage_old", "Minecraft Damage (old)", "/audios/minecraft_damage_old.wav"),
        BuiltInSound("shock_meme", "Shock", "/audios/shock_meme.wav"),
        BuiltInSound("kiepscy", "Kiepscy", "/audios/swiat_wg_kiepskich.wav"),
    )

    private val byId: Map<String, BuiltInSound> = all.associateBy { it.id }

    val default: BuiltInSound = all.first()

    fun customFileOption(customPath: String): BuiltInSound {
        val label = if (customPath.isBlank()) "Custom File" else "Custom File (${customPath.substringAfterLast('/')})"
        return BuiltInSound(CUSTOM_FILE_ID, label, "")
    }

    fun allWithCustom(customPath: String): List<BuiltInSound> {
        return if (customPath.isBlank()) all else all + customFileOption(customPath)
    }

    fun findByIdOrDefault(id: String, customPath: String = ""): BuiltInSound {
        if (id == CUSTOM_FILE_ID) {
            return customFileOption(customPath)
        }
        return byId[id] ?: default
    }
}
