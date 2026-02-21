package bot.inker.bc.razor.i18n

import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.state.CharacterInfo
import bot.inker.bc.razor.state.CharacterState
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChatMessageResolverTest {

    private val tm = TranslationManager()
    private val resolver = ChatMessageResolver(tm)

    private fun characterLookup(vararg chars: CharacterState): (Int) -> CharacterInfo? {
        val map = chars.associateBy { it.memberNumber }
        return { memberNumber -> map[memberNumber] }
    }

    private fun makeCharacter(
        memberNumber: Int,
        name: String,
        rawData: com.google.gson.JsonElement? = null
    ): CharacterState {
        return CharacterState(
            id = memberNumber.toString(),
            memberNumber = memberNumber,
            name = name,
            rawData = rawData,
        )
    }

    // --- Literal message types ---

    @Test
    fun chatMessageReturnsContentAsIs() {
        val msg = ReceivedChatMessage(
            sender = 1,
            content = "Hello world!",
            type = ChatMessageType.CHAT,
        )
        assertEquals("Hello world!", resolver.resolve(msg) { null })
    }

    @Test
    fun whisperMessageReturnsContentAsIs() {
        val msg = ReceivedChatMessage(
            sender = 1,
            content = "Secret message",
            type = ChatMessageType.WHISPER,
            target = 2,
        )
        assertEquals("Secret message", resolver.resolve(msg) { null })
    }

    @Test
    fun emoteMessageReturnsContentAsIs() {
        val msg = ReceivedChatMessage(
            sender = 1,
            content = "waves happily",
            type = ChatMessageType.EMOTE,
        )
        assertEquals("waves happily", resolver.resolve(msg) { null })
    }

    // --- Action messages ---

    @Test
    fun actionMessageResolvesTemplateKey() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 100) })
            add(JsonObject().apply {
                addProperty("GroupName", "ItemArms")
                addProperty("AssetName", "HempRope")
                addProperty("Tag", "NextAsset")
            })
            add(JsonObject().apply { addProperty("TargetCharacter", 200) })
            add(JsonObject().apply { addProperty("FocusGroupName", "ItemArms") })
        }

        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActionUse",
            type = ChatMessageType.ACTION,
            dictionary = dict,
        )

        val alice = makeCharacter(100, "Alice")
        val bob = makeCharacter(200, "Bob")
        val result = resolver.resolve(msg, characterLookup(alice, bob))

        // Template: "SourceCharacter uses a NextAsset on DestinationCharacter FocusAssetGroup."
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("Bob"))
        assertFalse(result.contains("SourceCharacter"))
        assertFalse(result.contains("DestinationCharacter"))
    }

    // --- Activity messages ---

    @Test
    fun activityMessageResolvesFromDictionary() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 100) })
            add(JsonObject().apply { addProperty("TargetCharacter", 200) })
        }

        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActivityKiss",
            type = ChatMessageType.ACTIVITY,
            dictionary = dict,
        )

        val alice = makeCharacter(100, "Alice")
        val bob = makeCharacter(200, "Bob")
        val result = resolver.resolve(msg, characterLookup(alice, bob))
        // ActivityKiss maps to "Kiss" which is short and doesn't have tags
        assertEquals("Kiss", result)
    }

    // --- ServerMessage ---

    @Test
    fun serverMessageResolvesFromInterfaceText() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 100) })
        }

        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActionCoin",
            type = ChatMessageType.SERVER_MESSAGE,
            dictionary = dict,
        )

        val alice = makeCharacter(100, "Alice")
        val result = resolver.resolve(msg, characterLookup(alice))
        // Template: "SourceCharacter flips a coin. The result is: CoinResult."
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("flips a coin"))
    }

    // --- Dictionary substitution ---

    @Test
    fun dictionaryTextTagSubstitution() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 100) })
            add(JsonObject().apply {
                addProperty("Tag", "CoinResult")
                addProperty("Text", "Heads")
            })
        }

        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActionCoin",
            type = ChatMessageType.SERVER_MESSAGE,
            dictionary = dict,
        )

        val alice = makeCharacter(100, "Alice")
        val result = resolver.resolve(msg, characterLookup(alice))
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("Heads"))
        assertFalse(result.contains("CoinResult"))
    }

    @Test
    fun craftNameOverridesAssetName() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 100) })
            add(JsonObject().apply { addProperty("TargetCharacter", 200) })
            add(JsonObject().apply {
                addProperty("GroupName", "ItemArms")
                addProperty("AssetName", "HempRope")
                addProperty("Tag", "NextAsset")
                addProperty("CraftName", "My Custom Rope")
            })
            add(JsonObject().apply { addProperty("FocusGroupName", "ItemArms") })
        }

        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActionUse",
            type = ChatMessageType.ACTION,
            dictionary = dict,
        )

        val alice = makeCharacter(100, "Alice")
        val bob = makeCharacter(200, "Bob")
        val result = resolver.resolve(msg, characterLookup(alice, bob))
        assertTrue(result.contains("My Custom Rope"))
    }

    @Test
    fun missingCharacterFallsBackToMemberNumber() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 999) })
        }

        val msg = ReceivedChatMessage(
            sender = 999,
            content = "ActionCoin",
            type = ChatMessageType.SERVER_MESSAGE,
            dictionary = dict,
        )

        val result = resolver.resolve(msg) { null }
        assertTrue(result.contains("999"))
    }

    // --- Pronouns ---

    @Test
    fun defaultPronounsAreSheHer() {
        val char = makeCharacter(1, "Alice")
        val pronouns = resolver.extractPronouns(char)
        assertEquals(ChatMessageResolver.SHE_HER, pronouns)
    }

    @Test
    fun heHimPronounsFromRawData() {
        val rawData = JsonObject().apply {
            addProperty("MemberNumber", 1)
            addProperty("Name", "Bob")
            addProperty("Pronoun", 1)
        }
        val char = makeCharacter(1, "Bob", rawData)
        val pronouns = resolver.extractPronouns(char)
        assertEquals(ChatMessageResolver.HE_HIM, pronouns)
    }

    @Test
    fun theyThemPronounsFromRawData() {
        val rawData = JsonObject().apply {
            addProperty("MemberNumber", 1)
            addProperty("Name", "Alex")
            addProperty("Pronoun", 2)
        }
        val char = makeCharacter(1, "Alex", rawData)
        val pronouns = resolver.extractPronouns(char)
        assertEquals(ChatMessageResolver.THEY_THEM, pronouns)
    }

    @Test
    fun pronounSubstitutionInTemplate() {
        val dict = JsonArray().apply {
            add(JsonObject().apply { addProperty("SourceCharacter", 100) })
        }

        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActionActivateSafewordRelease",
            type = ChatMessageType.ACTION,
            dictionary = dict,
        )

        val alice = makeCharacter(100, "Alice")
        val result = resolver.resolve(msg, characterLookup(alice))
        // Template: "SourceCharacter used PronounPossessive safeword..."
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("her"))
        assertFalse(result.contains("PronounPossessive"))
    }

    // --- Null/missing dictionary ---

    @Test
    fun nullDictionaryReturnsTemplateAsIs() {
        val msg = ReceivedChatMessage(
            sender = 100,
            content = "ActionCoin",
            type = ChatMessageType.SERVER_MESSAGE,
            dictionary = null,
        )

        val result = resolver.resolve(msg) { null }
        // Should return the looked-up template without substitution
        assertEquals(tm.interfaceText("ActionCoin"), result)
    }

    @Test
    fun unknownContentKeyReturnsKeyItself() {
        val msg = ReceivedChatMessage(
            sender = 100,
            content = "CompletelyFakeContentKey",
            type = ChatMessageType.ACTION,
            dictionary = null,
        )

        val result = resolver.resolve(msg) { null }
        assertEquals("CompletelyFakeContentKey", result)
    }
}