package bot.inker.bc.razor.i18n

import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.state.CharacterInfo
import bot.inker.bc.razor.state.CharacterState
import com.google.gson.JsonElement
import com.google.gson.JsonObject

class ChatMessageResolver(private val translations: TranslationManager) {

    fun resolve(
        message: ReceivedChatMessage,
        characterLookup: (Int) -> CharacterInfo?,
    ): String {
        val template = when (message.type) {
            ChatMessageType.CHAT,
            ChatMessageType.WHISPER,
            ChatMessageType.EMOTE ->
                return message.content

            ChatMessageType.ACTION ->
                lookupActionText(message.content)

            ChatMessageType.ACTIVITY ->
                lookupActivityText(message.content)

            ChatMessageType.SERVER_MESSAGE ->
                lookupServerMessageText(message.content)

            ChatMessageType.HIDDEN,
            ChatMessageType.STATUS,
            ChatMessageType.LOCAL_MESSAGE ->
                lookupInterfaceText(message.content)
        }

        return applyDictionary(template, message.dictionary, characterLookup)
    }

    private fun lookupActionText(key: String): String {
        translations.interfaceTextOrNull(key)?.let { return it }
        translations.assetStringOrNull(key)?.let { return it }
        return key
    }

    private fun lookupActivityText(key: String): String {
        return translations.activityDictionaryText(key)
    }

    private fun lookupServerMessageText(key: String): String {
        translations.interfaceTextOrNull(key)?.let { return it }
        translations.screenTextOrNull("ChatRoom", key)?.let { return it }
        return key
    }

    private fun lookupInterfaceText(key: String): String {
        return translations.interfaceText(key)
    }

    private fun applyDictionary(
        text: String,
        dictionary: JsonElement?,
        characterLookup: (Int) -> CharacterInfo?,
    ): String {
        if (dictionary == null || !dictionary.isJsonArray) return text

        val substitutions = mutableMapOf<String, String>()
        var sourceChar: CharacterInfo? = null
        var targetChar: CharacterInfo? = null

        for (entry in dictionary.asJsonArray) {
            if (!entry.isJsonObject) continue
            val obj = entry.asJsonObject

            processEntry(obj, characterLookup, substitutions)

            if (obj.has("SourceCharacter")) {
                sourceChar = characterLookup(obj.get("SourceCharacter").asInt)
            }
            if (obj.has("TargetCharacter")) {
                targetChar = characterLookup(obj.get("TargetCharacter").asInt)
            }
        }

        if (sourceChar != null) {
            addPronouns(substitutions, sourceChar, prefix = "")
        }
        if (targetChar != null) {
            addPronouns(substitutions, targetChar, prefix = "Target")
        }

        return applySubstitutions(text, substitutions)
    }

    private fun processEntry(
        entry: JsonObject,
        characterLookup: (Int) -> CharacterInfo?,
        substitutions: MutableMap<String, String>,
    ) {
        // SourceCharacter: { "SourceCharacter": memberNumber }
        if (entry.has("SourceCharacter")) {
            val memberNumber = entry.get("SourceCharacter").asInt
            val char = characterLookup(memberNumber)
            substitutions["SourceCharacter"] = char?.name ?: memberNumber.toString()
        }

        // TargetCharacter: { "TargetCharacter": memberNumber }
        if (entry.has("TargetCharacter")) {
            val memberNumber = entry.get("TargetCharacter").asInt
            val char = characterLookup(memberNumber)
            val name = char?.name ?: memberNumber.toString()
            substitutions["TargetCharacter"] = name
            substitutions["TargetCharacterName"] = name
            substitutions["DestinationCharacter"] = name
            substitutions["DestinationCharacterName"] = name
        }

        // FocusGroupName: { "FocusGroupName": "GroupName" }
        if (entry.has("FocusGroupName")) {
            val groupName = entry.get("FocusGroupName").asString
            substitutions["FocusAssetGroup"] = translations.assetGroupName(groupName)
        }

        // Asset reference: { "GroupName": "G", "AssetName": "A", "Tag": "NextAsset", "CraftName": "C" }
        if (entry.has("AssetName") && entry.has("GroupName")) {
            val groupName = entry.get("GroupName").asString
            val assetName = entry.get("AssetName").asString
            val craftName = entry.get("CraftName")?.takeIf { !it.isJsonNull }?.asString
            val tag = entry.get("Tag")?.takeIf { !it.isJsonNull }?.asString ?: "AssetName"
            substitutions[tag] = craftName ?: translations.assetName(groupName, assetName)
        }

        // Literal text: { "Tag": "SomeTag", "Text": "literal text" }
        if (entry.has("Tag") && entry.has("Text")) {
            val tag = entry.get("Tag").asString
            val text = entry.get("Text").asString
            substitutions[tag] = text
        }

        // Text lookup: { "Tag": "SomeTag", "TextToLookUp": "key" }
        if (entry.has("Tag") && entry.has("TextToLookUp")) {
            val tag = entry.get("Tag").asString
            val key = entry.get("TextToLookUp").asString
            substitutions[tag] = translations.lookupText(key) ?: key
        }

        // Shock intensity: { "ShockIntensity": 5 }
        if (entry.has("ShockIntensity")) {
            substitutions["ShockIntensity"] = entry.get("ShockIntensity").asInt.toString()
        }
    }

    private fun addPronouns(
        substitutions: MutableMap<String, String>,
        character: CharacterInfo,
        prefix: String,
    ) {
        val pronouns = extractPronouns(character)
        substitutions["${prefix}PronounSubject"] = pronouns.subject
        substitutions["${prefix}PronounObject"] = pronouns.objectForm
        substitutions["${prefix}PronounPossessive"] = pronouns.possessive
    }

    internal fun extractPronouns(character: CharacterInfo): Pronouns {
        if (character is CharacterState) {
            val rawData = character.rawData
            if (rawData != null && rawData.isJsonObject) {
                val pronoun = rawData.asJsonObject.get("Pronoun")
                if (pronoun != null && pronoun.isJsonPrimitive) {
                    return when (pronoun.asInt) {
                        1 -> HE_HIM
                        2 -> THEY_THEM
                        else -> SHE_HER
                    }
                }
            }
        }
        return SHE_HER
    }

    private fun applySubstitutions(text: String, substitutions: Map<String, String>): String {
        if (substitutions.isEmpty()) return text
        var result = text
        // Sort by key length descending to replace longer tags first,
        // avoiding partial replacement (e.g. "TargetCharacterName" before "TargetCharacter")
        for ((tag, value) in substitutions.entries.sortedByDescending { it.key.length }) {
            result = result.replace(tag, value)
        }
        return result
    }

    data class Pronouns(
        val subject: String,
        val objectForm: String,
        val possessive: String,
    )

    companion object {
        val SHE_HER = Pronouns("She", "her", "her")
        val HE_HIM = Pronouns("He", "him", "his")
        val THEY_THEM = Pronouns("They", "them", "their")
    }
}