package dev.bluehouse.enablevolte

import android.content.Context
import android.os.Bundle
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Stores the complete Root CarrierConfig profile per subscription. CarrierConfig overrideConfig
 * replaces the previous override bundle; retaining individual toggle calls would therefore make
 * the newest control silently discard every earlier one.
 *
 * Shizuku never writes this store. Its overrides intentionally remain session-only.
 */
internal class RootCarrierConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun hasProfile(subscriptionId: Int): Boolean =
        preferences.all.keys.any { it.startsWith(prefix(subscriptionId)) }

    fun load(subscriptionId: Int): Bundle {
        val result = Bundle()
        val keyPrefix = prefix(subscriptionId)
        preferences.all.forEach { (storedKey, rawValue) ->
            if (!storedKey.startsWith(keyPrefix)) return@forEach
            val carrierKey = storedKey.removePrefix(keyPrefix)
            val decoded = RootCarrierConfigCodec.decode(rawValue as? String ?: return@forEach) ?: return@forEach
            put(result, carrierKey, decoded)
        }
        return result
    }

    fun merge(subscriptionId: Int, delta: Bundle): Bundle =
        load(subscriptionId).apply {
            delta.keySet().forEach { key ->
                delta.get(key)?.let { put(this, key, it) }
            }
        }

    fun save(subscriptionId: Int, profile: Bundle) {
        val keyPrefix = prefix(subscriptionId)
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(keyPrefix) }.forEach { editor.remove(it) }
        profile.keySet().forEach { key ->
            profile.get(key)?.let { value ->
                RootCarrierConfigCodec.encode(value)?.let { editor.putString(keyPrefix + key, it) }
            }
        }
        editor.commit()
    }

    fun clear(subscriptionId: Int) {
        val keyPrefix = prefix(subscriptionId)
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(keyPrefix) }.forEach { editor.remove(it) }
        editor.commit()
    }

    private fun prefix(subscriptionId: Int) = "sub_${subscriptionId}_"

    private fun put(bundle: Bundle, key: String, value: Any) {
        when (value) {
            is Boolean -> bundle.putBoolean(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is String -> bundle.putString(key, value)
            is IntArray -> bundle.putIntArray(key, value)
            is BooleanArray -> bundle.putBooleanArray(key, value)
            is LongArray -> bundle.putLongArray(key, value)
            is Array<*> -> if (value.all { it is String }) {
                @Suppress("UNCHECKED_CAST")
                bundle.putStringArray(key, value as Array<String>)
            }
        }
    }

    companion object {
        private const val PREFERENCES = "pixel_ims_root_carrier_config"
    }
}

internal object RootCarrierConfigCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(value: Any): String? =
        when (value) {
            is Boolean -> "bool:${if (value) 1 else 0}"
            is Int -> "int:$value"
            is Long -> "long:$value"
            is String -> "string:${encodeString(value)}"
            is IntArray -> "ints:${value.joinToString(",")}"
            is BooleanArray -> "bools:${value.joinToString(",") { if (it) "1" else "0" }}"
            is LongArray -> "longs:${value.joinToString(",")}"
            is Array<*> -> if (value.all { it is String }) {
                "strings:${value.joinToString(",") { encodeString(it as String) }}"
            } else {
                null
            }
            else -> null
        }

    fun decode(encoded: String): Any? =
        runCatching {
            val type = encoded.substringBefore(':')
            val value = encoded.substringAfter(':', "")
            when (type) {
                "bool" -> value == "1"
                "int" -> value.toInt()
                "long" -> value.toLong()
                "string" -> decodeString(value)
                "ints" -> if (value.isBlank()) intArrayOf() else value.split(',').map(String::toInt).toIntArray()
                "bools" -> if (value.isBlank()) booleanArrayOf() else value.split(',').map { it == "1" }.toBooleanArray()
                "longs" -> if (value.isBlank()) longArrayOf() else value.split(',').map(String::toLong).toLongArray()
                "strings" -> if (value.isBlank()) emptyArray<String>() else value.split(',').map(::decodeString).toTypedArray()
                else -> null
            }
        }.getOrNull()

    private fun encodeString(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeString(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)
}
