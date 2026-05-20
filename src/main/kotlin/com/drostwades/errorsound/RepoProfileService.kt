package com.drostwades.errorsound

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RepoProfileService(private val project: Project) {

    private var cachedResult: RepoProfileLoadResult? = null

    @Synchronized
    fun load(refresh: Boolean = false): RepoProfileLoadResult {
        if (!refresh) {
            cachedResult?.let { return it }
        }
        return loadFromDisk().also { cachedResult = it }
    }

    @Synchronized
    fun reload(): RepoProfileLoadResult = load(refresh = true)

    fun openProfileFile(): Boolean {
        val path = profilePath() ?: return false
        if (!Files.isRegularFile(path)) return false
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return false
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
        return true
    }

    private fun loadFromDisk(): RepoProfileLoadResult {
        val path = profilePath()
            ?: return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.NO_PROJECT_BASE_PATH,
                warnings = listOf("Project base path is unavailable; repo profile was not loaded."),
            )

        if (!Files.exists(path)) {
            return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.ABSENT,
                path = path.toString(),
            )
        }

        val warnings = mutableListOf<String>()
        if (!Files.isRegularFile(path)) {
            return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = listOf("${FILE_NAME} exists but is not a regular file."),
            )
        }

        val size = runCatching { Files.size(path) }.getOrDefault(0L)
        if (size > MAX_PROFILE_BYTES) {
            return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = listOf("${FILE_NAME} is too large to load safely."),
            )
        }

        val json = try {
            Files.readString(path)
        } catch (t: Throwable) {
            return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = listOf("${FILE_NAME} could not be read: ${t.message ?: t::class.java.simpleName}."),
            )
        }

        val parsed = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = listOf("${FILE_NAME} contains invalid JSON: ${e.message ?: "parse error"}."),
            )
        }

        val root = parsed.asObjectOrWarn(FILE_NAME, warnings)
            ?: return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = warnings.ifEmpty { listOf("${FILE_NAME} must contain a JSON object.") },
            )

        warnUnknownFields(root, TOP_LEVEL_FIELDS, FILE_NAME, warnings)

        val schemaVersion = root.requiredInt("schemaVersion", FILE_NAME, warnings)
            ?: return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = warnings + "${FILE_NAME}.schemaVersion must be $SUPPORTED_SCHEMA_VERSION.",
            )
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return RepoProfileLoadResult(
                status = RepoProfileLoadResult.Status.INVALID,
                path = path.toString(),
                warnings = warnings + "${FILE_NAME}.schemaVersion must be $SUPPORTED_SCHEMA_VERSION.",
            )
        }

        val profileName = root.optionalString("profileName", FILE_NAME, warnings)?.trim()?.take(MAX_PROFILE_NAME_LENGTH)
        val enabled = root.optionalBoolean("enabled", FILE_NAME, warnings) ?: true
        val overrides = root.optionalObject("overrides", FILE_NAME, warnings)
            ?.let { parseOverrides(it, warnings) }
            ?: RepoProfileState.Overrides()

        val profile = RepoProfileState(
            schemaVersion = schemaVersion,
            profileName = profileName,
            enabled = enabled,
            overrides = overrides,
        )
        return RepoProfileLoadResult(
            status = if (enabled) RepoProfileLoadResult.Status.LOADED else RepoProfileLoadResult.Status.DISABLED,
            path = path.toString(),
            profile = profile,
            warnings = warnings,
        )
    }

    private fun parseOverrides(obj: JsonObject, warnings: MutableList<String>): RepoProfileState.Overrides {
        warnUnknownFields(obj, OVERRIDE_FIELDS, "$FILE_NAME.overrides", warnings)

        val monitoring = obj.optionalObject("monitoring", "$FILE_NAME.overrides", warnings)
        val sound = obj.optionalObject("sound", "$FILE_NAME.overrides", warnings)
        val volume = obj.optionalObject("volume", "$FILE_NAME.overrides", warnings)
        val duration = obj.optionalObject("duration", "$FILE_NAME.overrides", warnings)
        val visual = obj.optionalObject("visualNotifications", "$FILE_NAME.overrides", warnings)
        val minProcessDuration = obj.optionalObject("minimumProcessDuration", "$FILE_NAME.overrides", warnings)

        return RepoProfileState.Overrides(
            masterEnabled = monitoring?.optionalBoolean("enabled", "$FILE_NAME.overrides.monitoring", warnings),
            monitorConfiguration = monitoring?.optionalBoolean("configuration", "$FILE_NAME.overrides.monitoring", warnings),
            monitorCompilation = monitoring?.optionalBoolean("compilation", "$FILE_NAME.overrides.monitoring", warnings),
            monitorTestFailure = monitoring?.optionalBoolean("testFailure", "$FILE_NAME.overrides.monitoring", warnings),
            monitorNetwork = monitoring?.optionalBoolean("network", "$FILE_NAME.overrides.monitoring", warnings),
            monitorException = monitoring?.optionalBoolean("exception", "$FILE_NAME.overrides.monitoring", warnings),
            monitorGeneric = monitoring?.optionalBoolean("generic", "$FILE_NAME.overrides.monitoring", warnings),
            monitorSuccess = monitoring?.optionalBoolean("success", "$FILE_NAME.overrides.monitoring", warnings),
            useGlobalBuiltInSound = sound?.optionalBoolean("useGlobalBuiltInSound", "$FILE_NAME.overrides.sound", warnings),
            builtInSoundId = sound?.optionalSoundId("globalBuiltInSoundId", "$FILE_NAME.overrides.sound", warnings),
            soundPerKind = sound?.let { parseSoundOverrides(it, warnings) } ?: emptyMap(),
            volumePercent = volume?.optionalClampedInt(
                "globalVolumePercent",
                "$FILE_NAME.overrides.volume",
                0,
                100,
                warnings,
            ),
            volumePerKind = volume?.let { parseVolumeOverrides(it, warnings) } ?: emptyMap(),
            alertDurationSeconds = duration?.optionalClampedInt(
                "alertDurationSeconds",
                "$FILE_NAME.overrides.duration",
                1,
                10,
                warnings,
            ),
            useActualSoundDuration = duration?.optionalBoolean(
                "useActualSoundDuration",
                "$FILE_NAME.overrides.duration",
                warnings,
            ),
            showVisualNotification = visual?.optionalBoolean(
                "showVisualNotification",
                "$FILE_NAME.overrides.visualNotifications",
                warnings,
            ),
            visualNotificationOnError = visual?.optionalBoolean("onError", "$FILE_NAME.overrides.visualNotifications", warnings),
            visualNotificationOnSuccess = visual?.optionalBoolean("onSuccess", "$FILE_NAME.overrides.visualNotifications", warnings),
            minProcessDurationSeconds = minProcessDuration?.optionalClampedInt(
                "seconds",
                "$FILE_NAME.overrides.minimumProcessDuration",
                0,
                300,
                warnings,
            ),
        ).also {
            monitoring?.let { warnUnknownFields(it, MONITORING_FIELDS, "$FILE_NAME.overrides.monitoring", warnings) }
            sound?.let { warnUnknownFields(it, SOUND_FIELDS, "$FILE_NAME.overrides.sound", warnings) }
            volume?.let { warnUnknownFields(it, VOLUME_FIELDS, "$FILE_NAME.overrides.volume", warnings) }
            duration?.let { warnUnknownFields(it, DURATION_FIELDS, "$FILE_NAME.overrides.duration", warnings) }
            visual?.let { warnUnknownFields(it, VISUAL_FIELDS, "$FILE_NAME.overrides.visualNotifications", warnings) }
            minProcessDuration?.let {
                warnUnknownFields(it, MIN_PROCESS_DURATION_FIELDS, "$FILE_NAME.overrides.minimumProcessDuration", warnings)
            }
        }
    }

    private fun parseSoundOverrides(
        sound: JsonObject,
        warnings: MutableList<String>,
    ): Map<ErrorKind, RepoProfileState.KindSoundOverride> {
        val rows = mutableMapOf<ErrorKind, RepoProfileState.KindSoundOverride>()
        sound.optionalObject("perKind", "$FILE_NAME.overrides.sound", warnings)?.let { perKind ->
            parseKindObject(perKind, "$FILE_NAME.overrides.sound.perKind", warnings) { kind, row, path ->
                warnUnknownFields(row, KIND_SOUND_FIELDS, path, warnings)
                rows[kind] = RepoProfileState.KindSoundOverride(
                    enabled = row.optionalBoolean("enabled", path, warnings),
                    soundId = row.optionalSoundId("soundId", path, warnings),
                )
            }
        }
        sound.optionalObject("success", "$FILE_NAME.overrides.sound", warnings)?.let { success ->
            warnUnknownFields(success, KIND_SOUND_FIELDS, "$FILE_NAME.overrides.sound.success", warnings)
            rows[ErrorKind.SUCCESS] = RepoProfileState.KindSoundOverride(
                enabled = success.optionalBoolean("enabled", "$FILE_NAME.overrides.sound.success", warnings),
                soundId = success.optionalSoundId("soundId", "$FILE_NAME.overrides.sound.success", warnings),
            )
        }
        return rows
    }

    private fun parseVolumeOverrides(
        volume: JsonObject,
        warnings: MutableList<String>,
    ): Map<ErrorKind, RepoProfileState.KindVolumeOverride> {
        val rows = mutableMapOf<ErrorKind, RepoProfileState.KindVolumeOverride>()
        volume.optionalObject("perKind", "$FILE_NAME.overrides.volume", warnings)?.let { perKind ->
            parseKindObject(perKind, "$FILE_NAME.overrides.volume.perKind", warnings) { kind, row, path ->
                warnUnknownFields(row, KIND_VOLUME_FIELDS, path, warnings)
                val enabled = row.optionalBoolean("enabled", path, warnings)
                val value = row.optionalClampedInt("volumePercent", path, 0, 100, warnings)
                when {
                    enabled == false -> rows[kind] = RepoProfileState.KindVolumeOverride(enabled = false)
                    enabled == true && value != null -> rows[kind] =
                        RepoProfileState.KindVolumeOverride(enabled = true, volumePercent = value)
                    enabled == null && value != null -> rows[kind] =
                        RepoProfileState.KindVolumeOverride(enabled = true, volumePercent = value)
                    enabled == true -> warnings += "$path.volumePercent is required when volume override is enabled; row ignored."
                }
            }
        }
        return rows
    }

    private fun parseKindObject(
        obj: JsonObject,
        path: String,
        warnings: MutableList<String>,
        consume: (ErrorKind, JsonObject, String) -> Unit,
    ) {
        obj.entrySet().forEach { (name, element) ->
            val kind = ErrorKind.entries.firstOrNull { it.name == name }
            if (kind == null || kind == ErrorKind.NONE) {
                warnings += "$path.$name is not a supported error kind; ignored."
                return@forEach
            }
            val rowPath = "$path.$name"
            val row = element.asObjectOrWarn(rowPath, warnings) ?: return@forEach
            consume(kind, row, rowPath)
        }
    }

    private fun profilePath(): Path? =
        project.basePath?.let { Paths.get(it).resolve(FILE_NAME).normalize() }

    private fun warnUnknownFields(
        obj: JsonObject,
        allowed: Set<String>,
        path: String,
        warnings: MutableList<String>,
    ) {
        obj.keySet()
            .filterNot { it in allowed }
            .forEach { warnings += "$path.$it is not supported by schema v$SUPPORTED_SCHEMA_VERSION; ignored." }
    }

    private fun JsonElement.asObjectOrWarn(path: String, warnings: MutableList<String>): JsonObject? {
        if (!isJsonObject) {
            warnings += "$path must be a JSON object."
            return null
        }
        return asJsonObject
    }

    private fun JsonObject.optionalObject(name: String, path: String, warnings: MutableList<String>): JsonObject? {
        val element = get(name) ?: return null
        return element.asObjectOrWarn("$path.$name", warnings)
    }

    private fun JsonObject.requiredInt(name: String, path: String, warnings: MutableList<String>): Int? {
        val element = get(name)
        if (element == null) {
            warnings += "$path.$name is required."
            return null
        }
        return element.strictInt("$path.$name", warnings)
    }

    private fun JsonObject.optionalString(name: String, path: String, warnings: MutableList<String>): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            warnings += "$path.$name must be a string; ignored."
            return null
        }
        return element.asString
    }

    private fun JsonObject.optionalBoolean(name: String, path: String, warnings: MutableList<String>): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            warnings += "$path.$name must be a boolean; ignored."
            return null
        }
        return element.asBoolean
    }

    private fun JsonObject.optionalSoundId(name: String, path: String, warnings: MutableList<String>): String? {
        val id = optionalString(name, path, warnings)?.trim() ?: return null
        if (id == BuiltInSounds.CUSTOM_FILE_ID || BuiltInSounds.all.none { it.id == id }) {
            warnings += "$path.$name has unsupported built-in sound id '$id'; ignored."
            return null
        }
        return id
    }

    private fun JsonObject.optionalClampedInt(
        name: String,
        path: String,
        min: Int,
        max: Int,
        warnings: MutableList<String>,
    ): Int? {
        val element = get(name) ?: return null
        val value = element.strictInt("$path.$name", warnings) ?: return null
        val clamped = value.coerceIn(min, max)
        if (clamped != value) {
            warnings += "$path.$name was clamped to $clamped."
        }
        return clamped
    }

    private fun JsonElement.strictInt(path: String, warnings: MutableList<String>): Int? {
        if (!isJsonPrimitive || !asJsonPrimitive.isNumber) {
            warnings += "$path must be an integer; ignored."
            return null
        }
        val text = asJsonPrimitive.asString
        return text.toIntOrNull()
            ?: run {
                warnings += "$path must be an integer; ignored."
                null
            }
    }

    companion object {
        const val FILE_NAME = ".error-sound-alert.json"
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val MAX_PROFILE_BYTES = 256 * 1024
        private const val MAX_PROFILE_NAME_LENGTH = 120

        private val TOP_LEVEL_FIELDS = setOf("schemaVersion", "profileName", "enabled", "overrides")
        private val OVERRIDE_FIELDS = setOf(
            "monitoring",
            "sound",
            "volume",
            "duration",
            "visualNotifications",
            "minimumProcessDuration",
        )
        private val MONITORING_FIELDS = setOf(
            "enabled",
            "configuration",
            "compilation",
            "testFailure",
            "network",
            "exception",
            "generic",
            "success",
        )
        private val SOUND_FIELDS = setOf("useGlobalBuiltInSound", "globalBuiltInSoundId", "perKind", "success")
        private val VOLUME_FIELDS = setOf("globalVolumePercent", "perKind")
        private val DURATION_FIELDS = setOf("alertDurationSeconds", "useActualSoundDuration")
        private val VISUAL_FIELDS = setOf("showVisualNotification", "onError", "onSuccess")
        private val MIN_PROCESS_DURATION_FIELDS = setOf("seconds")
        private val KIND_SOUND_FIELDS = setOf("enabled", "soundId")
        private val KIND_VOLUME_FIELDS = setOf("enabled", "volumePercent")

        fun getInstance(project: Project): RepoProfileService =
            project.getService(RepoProfileService::class.java)
    }
}
