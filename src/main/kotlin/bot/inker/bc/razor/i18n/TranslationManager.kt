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

    private val assetStrings: Map<String, String>

    private val colorGroups: Map<String, String> =
        loadSimpleDictionary("bc/i18n/assets/ColorGroups.json")

    private val layerNames: Map<String, String>

    private val assetNames: Map<String, Map<String, String>>

    private val modActivities: Map<String, ModActivity>

    private val screenTextsCache = ConcurrentHashMap<String, Map<String, String>>()

    data class ModActivity(
        val label: String? = null,
        val labelSelf: String? = null,
        val dialog: String? = null,
        val dialogSelf: String? = null,
    )

    init {
        val modIndex = loadModIndex()

        // Load base data
        var mergedAssetStrings = loadSimpleDictionary("bc/i18n/assets/AssetStrings.json")
        var mergedLayerNames = loadSimpleDictionary("bc/i18n/assets/LayerNames.json")
        var mergedAssetNames = loadAssetDictionary("bc/i18n/assets/Female3DCG.json")
        val allModActivities = LinkedHashMap<String, ModActivity>()

        // Merge mod data
        for ((modName, fileTypes) in modIndex) {
            if ("activities" in fileTypes) {
                allModActivities.putAll(loadModActivities("bc/i18n/mods/$modName/activities.json"))
            }
            if ("assets" in fileTypes) {
                mergedAssetNames = mergeAssetDictionaries(
                    mergedAssetNames,
                    loadAssetDictionary("bc/i18n/mods/$modName/assets.json")
                )
            }
            if ("layerNames" in fileTypes) {
                mergedLayerNames = mergedLayerNames +
                    loadSimpleDictionary("bc/i18n/mods/$modName/layerNames.json")
            }
            if ("assetStrings" in fileTypes) {
                mergedAssetStrings = mergedAssetStrings +
                    loadSimpleDictionary("bc/i18n/mods/$modName/assetStrings.json")
            }
        }

        assetStrings = mergedAssetStrings
        layerNames = mergedLayerNames
        assetNames = mergedAssetNames
        modActivities = allModActivities
    }


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


    fun modActivityLabel(name: String): String? = modActivities[name]?.label

    fun modActivityLabelSelf(name: String): String? = modActivities[name]?.labelSelf

    fun modActivityDialog(name: String): String? = modActivities[name]?.dialog

    fun modActivityDialogSelf(name: String): String? = modActivities[name]?.dialogSelf


    fun lookupText(key: String): String? {
        interfaceTexts[key]?.let { return it }
        activityDictionary[key]?.let { return it }
        assetStrings[key]?.let { return it }
        return null
    }


    private fun loadModIndex(): Map<String, List<String>> {
        val type: Type = object : TypeToken<Map<String, List<String>>>() {}.type
        return loadResource("bc/i18n/mods/index.json", type) ?: emptyMap()
    }

    private fun loadModActivities(resourcePath: String): Map<String, ModActivity> {
        // Format: { "": { name: { field: value } }, "CN": { name: { field: value } } }
        val type: Type =
            object : TypeToken<Map<String, Map<String, Map<String, String>>>>() {}.type
        val data: Map<String, Map<String, Map<String, String>>> =
            loadResource(resourcePath, type) ?: return emptyMap()

        val english = data[""] ?: emptyMap()
        val translated = if (language.isNotEmpty()) data[language] else null

        val result = LinkedHashMap<String, ModActivity>()
        for (name in english.keys.union(translated?.keys ?: emptySet())) {
            val enFields = english[name] ?: emptyMap()
            val trFields = translated?.get(name)
            result[name] = ModActivity(
                label = trFields?.get("label") ?: enFields["label"],
                labelSelf = trFields?.get("labelSelf") ?: enFields["labelSelf"],
                dialog = trFields?.get("dialog") ?: enFields["dialog"],
                dialogSelf = trFields?.get("dialogSelf") ?: enFields["dialogSelf"],
            )
        }
        return result
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

    private fun mergeAssetDictionaries(
        base: Map<String, Map<String, String>>,
        overlay: Map<String, Map<String, String>>,
    ): Map<String, Map<String, String>> {
        if (overlay.isEmpty()) return base
        val merged = LinkedHashMap(base)
        for ((group, names) in overlay) {
            val existing = merged[group]
            merged[group] = if (existing != null) existing + names else names
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
