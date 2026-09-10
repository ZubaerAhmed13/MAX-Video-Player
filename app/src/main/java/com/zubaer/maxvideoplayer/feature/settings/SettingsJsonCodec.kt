package com.zubaer.maxvideoplayer.feature.settings

import java.time.Instant

object SettingsJsonCodec {
    const val FORMAT = "max-video-player-settings"
    const val VERSION = 1
    const val MAX_IMPORT_BYTES = 256 * 1024

    fun encode(settings: AppSettings, exportedAt: String = Instant.now().toString()): String = buildString {
        append('{')
        append("\"format\":\"").append(FORMAT).append("\",")
        append("\"version\":").append(VERSION).append(',')
        append("\"exportedAt\":\"").append(escape(exportedAt)).append("\",")
        append("\"settings\":{")
        append("\"appLockEnabled\":").append(settings.appLockEnabled).append(',')
        append("\"autoLockTimeout\":\"").append(settings.autoLockTimeout.name).append("\",")
        append("\"biometricUnlockEnabled\":").append(settings.biometricUnlockEnabled).append(',')
        append("\"protectPrivateScreens\":").append(settings.protectPrivateScreens).append(',')
        append("\"reduceMotion\":").append(settings.reduceMotion).append(',')
        append("\"contrastMode\":\"").append(settings.contrastMode.name).append("\",")
        append("\"useSystemCaptionStyle\":").append(settings.useSystemCaptionStyle).append(',')
        append("\"sleepFadeDuration\":\"").append(settings.sleepFadeDuration.name).append('"')
        append("}}")
    }

    fun decode(text: String, current: AppSettings): SettingsImportResult {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
            return SettingsImportResult.Failure("Settings file exceeds the 256 KB safety limit.")
        }
        val root = runCatching { JsonParser(text).parseObjectDocument() }.getOrElse {
            return SettingsImportResult.Failure("Settings file is malformed JSON.")
        }
        if (root.string("format") != FORMAT) return SettingsImportResult.Failure("This is not a MAX Video Player settings export.")
        val version = root.number("version")?.toIntOrNull() ?: return SettingsImportResult.Failure("Settings version is missing or invalid.")
        if (version != VERSION) return SettingsImportResult.Failure("Settings version $version is not supported by this build.")
        val objectValue = root.values["settings"] as? JsonValue.ObjectValue
            ?: return SettingsImportResult.Failure("Settings payload is missing.")
        val values = objectValue.values
        val known = KNOWN_KEYS
        val ignored = values.keys.count { it !in known }

        fun bool(key: String, fallback: Boolean): Boolean = (values[key] as? JsonValue.BooleanValue)?.value ?: fallback
        fun enumString(key: String): String? = (values[key] as? JsonValue.StringValue)?.value

        val timeout = enumString("autoLockTimeout")?.let { raw -> AutoLockTimeout.entries.firstOrNull { it.name == raw } }
            ?: current.autoLockTimeout
        val contrast = enumString("contrastMode")?.let { raw -> AccessibilityContrastMode.entries.firstOrNull { it.name == raw } }
            ?: current.contrastMode
        val fade = enumString("sleepFadeDuration")?.let { raw -> SleepFadeDuration.entries.firstOrNull { it.name == raw } }
            ?: current.sleepFadeDuration
        val imported = AppSettings(
            appLockEnabled = bool("appLockEnabled", current.appLockEnabled),
            autoLockTimeout = timeout,
            biometricUnlockEnabled = bool("biometricUnlockEnabled", current.biometricUnlockEnabled),
            protectPrivateScreens = bool("protectPrivateScreens", current.protectPrivateScreens),
            reduceMotion = bool("reduceMotion", current.reduceMotion),
            contrastMode = contrast,
            useSystemCaptionStyle = bool("useSystemCaptionStyle", current.useSystemCaptionStyle),
            sleepFadeDuration = fade,
        )
        val changed = known.filter { key -> valueFor(key, imported) != valueFor(key, current) }
        return SettingsImportResult.Ready(imported, SettingsImportSummary(changed, ignored))
    }

    private fun valueFor(key: String, settings: AppSettings): Any = when (key) {
        "appLockEnabled" -> settings.appLockEnabled
        "autoLockTimeout" -> settings.autoLockTimeout
        "biometricUnlockEnabled" -> settings.biometricUnlockEnabled
        "protectPrivateScreens" -> settings.protectPrivateScreens
        "reduceMotion" -> settings.reduceMotion
        "contrastMode" -> settings.contrastMode
        "useSystemCaptionStyle" -> settings.useSystemCaptionStyle
        "sleepFadeDuration" -> settings.sleepFadeDuration
        else -> Unit
    }

    private fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    private val KNOWN_KEYS = setOf(
        "appLockEnabled",
        "autoLockTimeout",
        "biometricUnlockEnabled",
        "protectPrivateScreens",
        "reduceMotion",
        "contrastMode",
        "useSystemCaptionStyle",
        "sleepFadeDuration",
    )
}

private sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue {
        fun string(key: String): String? = (values[key] as? StringValue)?.value
        fun number(key: String): String? = (values[key] as? NumberValue)?.raw
    }
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val raw: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

private class JsonParser(private val source: String) {
    private var index = 0

    fun parseObjectDocument(): JsonValue.ObjectValue {
        skipWhitespace()
        val value = parseObject()
        skipWhitespace()
        if (index != source.length) fail()
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (index >= source.length) fail()
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.StringValue(parseString())
            't' -> { literal("true"); JsonValue.BooleanValue(true) }
            'f' -> { literal("false"); JsonValue.BooleanValue(false) }
            'n' -> { literal("null"); JsonValue.NullValue }
            '-', in '0'..'9' -> JsonValue.NumberValue(parseNumber())
            else -> fail()
        }
    }

    private fun parseObject(): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (peek('}')) { index++; return JsonValue.ObjectValue(values) }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek('}') -> { index++; return JsonValue.ObjectValue(values) }
                else -> fail()
            }
        }
    }

    private fun parseArray(): JsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (peek(']')) { index++; return JsonValue.ArrayValue(values) }
        while (true) {
            values += parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek(']') -> { index++; return JsonValue.ArrayValue(values) }
                else -> fail()
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        return buildString {
            while (index < source.length) {
                val c = source[index++]
                when (c) {
                    '"' -> return@buildString
                    '\\' -> {
                        if (index >= source.length) fail()
                        when (val escaped = source[index++]) {
                            '"', '\\', '/' -> append(escaped)
                            'b' -> append('\b')
                            'f' -> append('\u000c')
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            'u' -> {
                                if (index + 4 > source.length) fail()
                                val hex = source.substring(index, index + 4)
                                append(hex.toIntOrNull(16)?.toChar() ?: fail())
                                index += 4
                            }
                            else -> fail()
                        }
                    }
                    else -> {
                        if (c.code < 0x20) fail()
                        append(c)
                    }
                }
            }
            fail()
        }
    }

    private fun parseNumber(): String {
        val start = index
        if (peek('-')) index++
        if (peek('0')) index++ else {
            if (index >= source.length || source[index] !in '1'..'9') fail()
            while (index < source.length && source[index].isDigit()) index++
        }
        if (peek('.')) {
            index++
            if (index >= source.length || !source[index].isDigit()) fail()
            while (index < source.length && source[index].isDigit()) index++
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            if (index >= source.length || !source[index].isDigit()) fail()
            while (index < source.length && source[index].isDigit()) index++
        }
        return source.substring(start, index)
    }

    private fun literal(value: String) {
        if (!source.regionMatches(index, value, 0, value.length)) fail()
        index += value.length
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun expect(c: Char) {
        if (!peek(c)) fail()
        index++
    }

    private fun peek(c: Char): Boolean = index < source.length && source[index] == c
    private fun fail(): Nothing = throw IllegalArgumentException("Malformed JSON at offset $index")
}
