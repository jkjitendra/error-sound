package com.drostwades.errorsound

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.time.Instant
import java.util.UUID
import java.util.regex.PatternSyntaxException

object RuleImportExportService {
    private const val SCHEMA_VERSION = 2
    private val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2)

    private val gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    private val topLevelFields = setOf(
        "schemaVersion",
        "exportedAt",
        "pluginVersion",
        "customRules",
        "suppressionRules",
        "exitCodeRules",
    )
    private val customRuleFields = setOf("id", "enabled", "pattern", "matchTarget", "kind")
    private val suppressionRuleFields = setOf("id", "enabled", "pattern", "matchTarget", "description")
    private val exitCodeRuleFields = setOf("exitCode", "enabled", "kind", "soundId", "suppress")

    fun exportRules(
        customRules: List<AlertSettings.CustomRuleState>,
        suppressionRules: List<AlertSettings.SuppressionRuleState>,
        exitCodeRules: List<AlertSettings.ExitCodeRuleState>,
        pluginVersion: String,
    ): String {
        val bundle = RuleImportExportBundle(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = Instant.now().toString(),
            pluginVersion = pluginVersion,
            customRules = customRules.map { rule ->
                RuleImportExportBundle.CustomRule(
                    id = rule.id,
                    enabled = rule.enabled,
                    pattern = rule.pattern,
                    matchTarget = rule.matchTarget,
                    kind = rule.kind,
                )
            },
            suppressionRules = suppressionRules.map { rule ->
                RuleImportExportBundle.SuppressionRule(
                    id = rule.id,
                    enabled = rule.enabled,
                    pattern = rule.pattern,
                    matchTarget = rule.matchTarget,
                    description = rule.description,
                )
            },
            exitCodeRules = exitCodeRules.map { rule ->
                RuleImportExportBundle.ExitCodeRule(
                    exitCode = rule.exitCode,
                    enabled = rule.enabled,
                    kind = rule.kind,
                    soundId = rule.soundId,
                    suppress = rule.suppress,
                )
            },
        )
        return gson.toJson(bundle)
    }

    fun importRules(json: String): RuleImportExportResult {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            throw IllegalArgumentException("Invalid JSON: ${e.message ?: "Unable to parse file."}")
        }

        val rootObject = root.asObject("Top-level JSON value")
        rejectUnknownFields(rootObject, topLevelFields, "Top-level object")

        val schemaVersion = rootObject.requiredInt("schemaVersion", "schemaVersion")
        if (schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            throw IllegalArgumentException(
                "Unsupported rules schema version: $schemaVersion. Supported versions are ${SUPPORTED_SCHEMA_VERSIONS.joinToString(", ")}."
            )
        }

        rootObject.optionalString("exportedAt", "exportedAt")
        rootObject.optionalString("pluginVersion", "pluginVersion")

        val warnings = mutableListOf<String>()
        var skippedCount = 0
        val customRules = mutableListOf<AlertSettings.CustomRuleState>()
        val suppressionRules = mutableListOf<AlertSettings.SuppressionRuleState>()
        val exitCodeRules = mutableListOf<AlertSettings.ExitCodeRuleState>()

        rootObject.optionalArray("customRules", "customRules")?.forEachIndexed { index, element ->
            if (index >= CustomRuleEngine.MAX_RULES) {
                if (index == CustomRuleEngine.MAX_RULES) {
                    warnings += "Skipped custom rules after row ${CustomRuleEngine.MAX_RULES}; only ${CustomRuleEngine.MAX_RULES} custom rules are supported."
                }
                skippedCount++
                return@forEachIndexed
            }

            val path = "customRules[${index + 1}]"
            val rule = runCatching { parseCustomRule(element, path, warnings) }
                .getOrElse { error ->
                    warnings += "Skipped $path: ${error.message ?: "Invalid rule."}"
                    skippedCount++
                    null
                }
            if (rule != null) customRules += rule
        }

        rootObject.optionalArray("suppressionRules", "suppressionRules")?.forEachIndexed { index, element ->
            if (index >= SuppressionRuleEngine.MAX_RULES) {
                if (index == SuppressionRuleEngine.MAX_RULES) {
                    warnings += "Skipped suppression rules after row ${SuppressionRuleEngine.MAX_RULES}; only ${SuppressionRuleEngine.MAX_RULES} suppression rules are supported."
                }
                skippedCount++
                return@forEachIndexed
            }

            val path = "suppressionRules[${index + 1}]"
            val rule = runCatching { parseSuppressionRule(element, path, warnings) }
                .getOrElse { error ->
                    warnings += "Skipped $path: ${error.message ?: "Invalid rule."}"
                    skippedCount++
                    null
                }
            if (rule != null) suppressionRules += rule
        }

        rootObject.optionalArray("exitCodeRules", "exitCodeRules")?.forEachIndexed { index, element ->
            val path = "exitCodeRules[${index + 1}]"
            val rule = runCatching { parseExitCodeRule(element, path, warnings) }
                .getOrElse { error ->
                    warnings += "Skipped $path: ${error.message ?: "Invalid rule."}"
                    skippedCount++
                    null
                }
            if (rule != null) exitCodeRules += rule
        }

        return RuleImportExportResult(
            customRules = customRules,
            suppressionRules = suppressionRules,
            exitCodeRules = exitCodeRules,
            warnings = warnings,
            skippedCount = skippedCount,
        )
    }

    private fun parseSuppressionRule(
        element: JsonElement,
        path: String,
        warnings: MutableList<String>,
    ): AlertSettings.SuppressionRuleState {
        val obj = element.asObject(path)
        rejectUnknownFields(obj, suppressionRuleFields, path)

        val id = obj.optionalString("id", "$path.id")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also {
                warnings += "$path has no id; generated a new one."
            }
        val enabled = obj.optionalBoolean("enabled", "$path.enabled") ?: true
        val rawPattern = obj.requiredString("pattern", "$path.pattern")
        val pattern = rawPattern.trim().let {
            if (it.length > SuppressionRuleEngine.MAX_PATTERN_LENGTH) {
                warnings += "$path.pattern was truncated to ${SuppressionRuleEngine.MAX_PATTERN_LENGTH} characters."
                it.take(SuppressionRuleEngine.MAX_PATTERN_LENGTH)
            } else {
                it
            }
        }
        val matchTarget = obj.requiredString("matchTarget", "$path.matchTarget")
        validateMatchTarget(matchTarget, "$path.matchTarget")
        val description = obj.optionalString("description", "$path.description")
            ?.trim()
            ?.let {
                if (it.length > SuppressionRuleEngine.MAX_DESCRIPTION_LENGTH) {
                    warnings += "$path.description was truncated to ${SuppressionRuleEngine.MAX_DESCRIPTION_LENGTH} characters."
                    it.take(SuppressionRuleEngine.MAX_DESCRIPTION_LENGTH)
                } else {
                    it
                }
            }
            ?: ""

        if (pattern.isNotBlank()) {
            try {
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            } catch (e: PatternSyntaxException) {
                warnings += "$path.pattern is not a valid regex and will be ignored at runtime until edited: ${e.description ?: e.message ?: "Invalid regex pattern"}"
            }
        }

        return AlertSettings.SuppressionRuleState(
            id = id,
            enabled = enabled,
            pattern = pattern,
            matchTarget = matchTarget,
            description = description,
        )
    }

    private fun parseCustomRule(
        element: JsonElement,
        path: String,
        warnings: MutableList<String>,
    ): AlertSettings.CustomRuleState {
        val obj = element.asObject(path)
        rejectUnknownFields(obj, customRuleFields, path)

        val id = obj.optionalString("id", "$path.id")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also {
                warnings += "$path has no id; generated a new one."
            }
        val enabled = obj.optionalBoolean("enabled", "$path.enabled") ?: true
        val rawPattern = obj.requiredString("pattern", "$path.pattern")
        val pattern = rawPattern.trim().let {
            if (it.length > CustomRuleEngine.MAX_PATTERN_LENGTH) {
                warnings += "$path.pattern was truncated to ${CustomRuleEngine.MAX_PATTERN_LENGTH} characters."
                it.take(CustomRuleEngine.MAX_PATTERN_LENGTH)
            } else {
                it
            }
        }
        val matchTarget = obj.requiredString("matchTarget", "$path.matchTarget")
        validateMatchTarget(matchTarget, "$path.matchTarget")
        val kind = obj.requiredString("kind", "$path.kind")
        validateKind(kind, "$path.kind")

        if (pattern.isNotBlank()) {
            try {
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            } catch (e: PatternSyntaxException) {
                warnings += "$path.pattern is not a valid regex and will be ignored at runtime until edited: ${e.description ?: e.message ?: "Invalid regex pattern"}"
            }
        }

        return AlertSettings.CustomRuleState(
            id = id,
            enabled = enabled,
            pattern = pattern,
            matchTarget = matchTarget,
            kind = kind,
        )
    }

    private fun parseExitCodeRule(
        element: JsonElement,
        path: String,
        warnings: MutableList<String>,
    ): AlertSettings.ExitCodeRuleState {
        val obj = element.asObject(path)
        rejectUnknownFields(obj, exitCodeRuleFields, path)

        val exitCode = obj.requiredInt("exitCode", "$path.exitCode")
        val enabled = obj.optionalBoolean("enabled", "$path.enabled") ?: true
        val kind = obj.requiredString("kind", "$path.kind")
        validateKind(kind, "$path.kind")
        val suppress = obj.optionalBoolean("suppress", "$path.suppress") ?: false
        val importedSoundId = obj.optionalNullableString("soundId", "$path.soundId")
        val soundId = importedSoundId
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != BuiltInSounds.CUSTOM_FILE_ID }
            ?.also {
                if (BuiltInSounds.all.none { sound -> sound.id == it }) {
                    throw IllegalArgumentException("$path.soundId '$it' is not a bundled sound id.")
                }
            }
        if (importedSoundId == BuiltInSounds.CUSTOM_FILE_ID) {
            warnings += "$path.soundId uses a custom-file marker; imported it as default sound resolution."
        }

        return AlertSettings.ExitCodeRuleState(
            exitCode = exitCode,
            enabled = enabled,
            kind = kind,
            soundId = soundId,
            suppress = suppress,
        )
    }

    private fun validateMatchTarget(value: String, path: String) {
        if (AlertSettings.MatchTarget.entries.none { it.name == value }) {
            throw IllegalArgumentException("$path '$value' is not supported.")
        }
    }

    private fun validateKind(value: String, path: String) {
        val kind = ErrorKind.entries.find { it.name == value }
        if (kind == null || kind !in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS) {
            throw IllegalArgumentException("$path '$value' is not an allowed error kind.")
        }
    }

    private fun rejectUnknownFields(obj: JsonObject, allowed: Set<String>, path: String) {
        val unknown = obj.keySet().filterNot { it in allowed }
        if (unknown.isNotEmpty()) {
            throw IllegalArgumentException("$path contains unsupported field(s): ${unknown.joinToString(", ")}.")
        }
    }

    private fun JsonElement.asObject(path: String): JsonObject {
        if (!isJsonObject) throw IllegalArgumentException("$path must be an object.")
        return asJsonObject
    }

    private fun JsonObject.optionalArray(name: String, path: String): List<JsonElement>? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) throw IllegalArgumentException("$path must be an array.")
        return element.asJsonArray.toList()
    }

    private fun JsonObject.requiredString(name: String, path: String): String =
        (get(name) ?: throw IllegalArgumentException("$path is required.")).strictString(path)

    private fun JsonObject.optionalString(name: String, path: String): String? =
        get(name)?.strictString(path)

    private fun JsonObject.optionalNullableString(name: String, path: String): String? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        return element.strictString(path)
    }

    private fun JsonObject.requiredInt(name: String, path: String): Int =
        (get(name) ?: throw IllegalArgumentException("$path is required.")).strictInt(path)

    private fun JsonObject.optionalBoolean(name: String, path: String): Boolean? =
        get(name)?.strictBoolean(path)

    private fun JsonElement.strictString(path: String): String {
        val primitive = strictPrimitive(path)
        if (!primitive.isString) throw IllegalArgumentException("$path must be a string.")
        return primitive.asString
    }

    private fun JsonElement.strictBoolean(path: String): Boolean {
        val primitive = strictPrimitive(path)
        if (!primitive.isBoolean) throw IllegalArgumentException("$path must be a boolean.")
        return primitive.asBoolean
    }

    private fun JsonElement.strictInt(path: String): Int {
        val primitive = strictPrimitive(path)
        if (!primitive.isNumber) throw IllegalArgumentException("$path must be an integer.")
        return try {
            primitive.asBigDecimal.intValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("$path must be an integer within the supported range.")
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("$path must be an integer.")
        }
    }

    private fun JsonElement.strictPrimitive(path: String): JsonPrimitive {
        if (!isJsonPrimitive) throw IllegalArgumentException("$path must be a primitive value.")
        return asJsonPrimitive
    }
}
