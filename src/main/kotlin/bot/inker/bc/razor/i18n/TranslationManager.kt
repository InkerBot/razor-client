package bot.inker.bc.razor.i18n

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class TranslationManager @JvmOverloads constructor(
    val language: String = "",
    private val classLoader: ClassLoader = TranslationManager::class.java.classLoader,
) {
    private val gson = Gson()

    private val interfaceTexts: Map<String, String> =
        loadSimpleDictionary("bc/i18n/Interface.json")

    private val activityDictionary: Map<String, String> =
        loadSimpleDictionary("bc/i18n/ActivityDictionary.json")

    private val assetStrings: Map<String, String> =
        loadSimpleDictionary("bc/i18n/assets/AssetStrings.json")

    private val colorGroups: Map<String, String> =
        loadSimpleDictionary("bc/i18n/assets/ColorGroups.json")

    private val layerNames: Map<String, String> =
        loadSimpleDictionary("bc/i18n/assets/LayerNames.json")

    private val assetNames: Map<String, Map<String, String>> =
        loadAssetDictionary("bc/i18n/assets/Female3DCG.json")

    private val screenTextsCache = ConcurrentHashMap<String, Map<String, String>>()


    fun interfaceText(key: String): String = interfaceTexts[key] ?: key

    fun interfaceTextOrNull(key: String): String? = interfaceTexts[key]

    fun activityDictionaryText(key: String): String = activityDictionary[key] ?: key

    fun activityDictionaryTextOrNull(key: String): String? = activityDictionary[key]

    fun assetString(key: String): String = assetStrings[key] ?: key

    fun assetStringOrNull(key: String): String? = assetStrings[key]


    fun colorGroup(key: String): String = colorGroups[key] ?: key

    fun layerName(key: String): String = layerNames[key] ?: key


    fun screenText(screen: String, key: String): String {
        val texts = screenTextsCache.computeIfAbsent(screen) {
            loadSimpleDictionary("bc/i18n/screens/$it.json")
        }
        return texts[key] ?: key
    }

    fun screenTextOrNull(screen: String, key: String): String? {
        val texts = screenTextsCache.computeIfAbsent(screen) {
            loadSimpleDictionary("bc/i18n/screens/$it.json")
        }
        return texts[key]
    }


    fun assetName(group: String, asset: String): String =
        assetNames[group]?.get(asset) ?: asset

    fun assetGroupName(group: String): String =
        assetNames[group]?.get("") ?: group


    fun lookupText(key: String): String? {
        interfaceTexts[key]?.let { return it }
        activityDictionary[key]?.let { return it }
        assetStrings[key]?.let { return it }
        return null
    }


    private fun loadSimpleDictionary(resourcePath: String): Map<String, String> {
        val type: Type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
        val data: Map<String, Map<String, String>> = loadResource(resourcePath, type)
            ?: return emptyMap()
        val english = data[""] ?: emptyMap()
        if (language.isEmpty()) return english
        val translated = data[language] ?: return english
        return english + translated
    }

    private fun loadAssetDictionary(resourcePath: String): Map<String, Map<String, String>> {
        val type: Type = object : TypeToken<Map<String, Map<String, Map<String, String>>>>() {}.type
        val data: Map<String, Map<String, Map<String, String>>> = loadResource(resourcePath, type)
            ?: return emptyMap()
        val english = data[""] ?: emptyMap()
        if (language.isEmpty()) return english
        val translated = data[language] ?: return english
        val merged = LinkedHashMap<String, Map<String, String>>()
        for (group in english.keys.union(translated.keys)) {
            val en = english[group] ?: emptyMap()
            val tr = translated[group] ?: emptyMap()
            merged[group] = en + tr
        }
        return merged
    }

    private fun <T> loadResource(path: String, type: Type): T? {
        val stream = classLoader.getResourceAsStream(path) ?: return null
        return stream.use { s ->
            InputStreamReader(s, StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, type)
            }
        }
    }
}