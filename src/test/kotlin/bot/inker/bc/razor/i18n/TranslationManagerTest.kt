package bot.inker.bc.razor.i18n

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TranslationManagerTest {

    private val tm = TranslationManager()

    @Test
    fun interfaceTextReturnsEnglishValue() {
        val result = tm.interfaceText("ActionUse")
        assertEquals(
            "SourceCharacter uses a NextAsset on DestinationCharacter FocusAssetGroup.",
            result,
        )
    }

    @Test
    fun interfaceTextReturnKeyWhenMissing() {
        assertEquals("NonExistentKey12345", tm.interfaceText("NonExistentKey12345"))
    }

    @Test
    fun interfaceTextOrNullReturnsNullWhenMissing() {
        assertNull(tm.interfaceTextOrNull("NonExistentKey12345"))
    }

    @Test
    fun activityDictionaryTextReturnsValue() {
        val result = tm.activityDictionaryText("ActivityKiss")
        assertEquals("Kiss", result)
    }

    @Test
    fun activityDictionaryTextReturnKeyWhenMissing() {
        assertEquals("FakeActivity999", tm.activityDictionaryText("FakeActivity999"))
    }

    @Test
    fun assetStringReturnsValue() {
        // AssetStrings contains keys like "ArmsChainSetBoxTie"
        val result = tm.assetStringOrNull("ArmsChainSetBoxTie")
        assertNotNull(result)
        assertTrue(result!!.contains("SourceCharacter"))
    }

    @Test
    fun screenTextLazyLoadsAndReturns() {
        val result = tm.screenText("ChatRoom", "PublicChat")
        assertEquals("Talk to everyone", result)
    }

    @Test
    fun screenTextReturnKeyForMissingScreen() {
        assertEquals("SomeKey", tm.screenText("NonExistentScreen", "SomeKey"))
    }

    @Test
    fun screenTextReturnKeyForMissingKey() {
        assertEquals("MissingKey999", tm.screenText("ChatRoom", "MissingKey999"))
    }

    @Test
    fun assetNameReturnsDisplayName() {
        val result = tm.assetName("Blush", "Blush")
        assertEquals("Blush", result)
    }

    @Test
    fun assetGroupNameReturnsGroupDisplayName() {
        val result = tm.assetGroupName("Blush")
        assertEquals("Blush", result)
    }

    @Test
    fun assetNameReturnFallbackWhenMissing() {
        assertEquals("FakeAsset", tm.assetName("ItemArms", "FakeAsset"))
    }

    @Test
    fun assetGroupNameReturnFallbackWhenMissing() {
        assertEquals("FakeGroup", tm.assetGroupName("FakeGroup"))
    }

    @Test
    fun lookupTextSearchesMultipleSources() {
        // Interface text
        assertNotNull(tm.lookupText("ActionUse"))
        // Activity dictionary
        assertNotNull(tm.lookupText("ActivityKiss"))
        // Missing
        assertNull(tm.lookupText("NonExistentKey12345"))
    }

    @Test
    fun colorGroupReturnsValue() {
        // ColorGroups should have some entries
        val missing = tm.colorGroup("MissingColor")
        assertEquals("MissingColor", missing)
    }

    @Test
    fun layerNameReturnsValue() {
        val missing = tm.layerName("MissingLayer")
        assertEquals("MissingLayer", missing)
    }

    @Test
    fun translationManagerWithLanguage() {
        val cnTm = TranslationManager("CN")
        // CN translation should exist for common keys
        val kiss = cnTm.activityDictionaryText("ActivityKiss")
        // Should be Chinese translation if available, otherwise English fallback
        assertNotNull(kiss)
        assertNotEquals("ActivityKiss", kiss) // Should resolve to something
    }
}