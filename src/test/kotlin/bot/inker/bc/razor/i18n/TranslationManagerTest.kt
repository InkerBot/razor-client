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

    // --- Mod activity tests ---

    @Test
    fun modActivityLabelReturnsEnglishValue() {
        assertEquals("Poke", tm.modActivityLabel("TestModPoke"))
    }

    @Test
    fun modActivityLabelSelfReturnsEnglishValue() {
        assertEquals("Poke Self", tm.modActivityLabelSelf("TestModPoke"))
    }

    @Test
    fun modActivityDialogReturnsEnglishValue() {
        assertEquals(
            "SourceCharacter pokes TargetCharacter.",
            tm.modActivityDialog("TestModPoke"),
        )
    }

    @Test
    fun modActivityDialogSelfReturnsEnglishValue() {
        assertEquals(
            "SourceCharacter pokes themselves.",
            tm.modActivityDialogSelf("TestModPoke"),
        )
    }

    @Test
    fun modActivityReturnsNullForMissing() {
        assertNull(tm.modActivityLabel("NonExistentModActivity"))
        assertNull(tm.modActivityDialog("NonExistentModActivity"))
    }

    @Test
    fun modActivityPartialFieldsReturnNull() {
        // TestModWave only has label and dialog, no labelSelf or dialogSelf
        assertEquals("Wave", tm.modActivityLabel("TestModWave"))
        assertNull(tm.modActivityLabelSelf("TestModWave"))
        assertEquals(
            "SourceCharacter waves at TargetCharacter.",
            tm.modActivityDialog("TestModWave"),
        )
        assertNull(tm.modActivityDialogSelf("TestModWave"))
    }

    @Test
    fun modActivityWithLanguageReturnTranslated() {
        val cnTm = TranslationManager("CN")
        assertEquals("戳", cnTm.modActivityLabel("TestModPoke"))
        assertEquals("SourceCharacter戳了TargetCharacter。", cnTm.modActivityDialog("TestModPoke"))
    }

    @Test
    fun modActivityWithLanguageFallsBackToEnglish() {
        val cnTm = TranslationManager("CN")
        // TestModWave has no CN translation, should fall back to English
        assertEquals("Wave", cnTm.modActivityLabel("TestModWave"))
    }

    // --- Mod asset name tests ---

    @Test
    fun modAssetNameMergedIntoExistingGroup() {
        assertEquals("Test Mod Cuffs", tm.assetName("ItemArms", "TestModCuffs"))
    }

    @Test
    fun modAssetNameInNewGroup() {
        assertEquals("Test Mod Item", tm.assetName("TestModGroup", "TestModItem"))
    }

    @Test
    fun modAssetNameWithLanguage() {
        val cnTm = TranslationManager("CN")
        assertEquals("测试手铐", cnTm.assetName("ItemArms", "TestModCuffs"))
    }

    @Test
    fun baseAssetNameStillWorksWithMods() {
        // Existing base game assets should not be affected
        assertEquals("Blush", tm.assetName("Blush", "Blush"))
    }

    // --- Mod layer name tests ---

    @Test
    fun modLayerNameReturnsValue() {
        assertEquals("Chain", tm.layerName("TestModCuffs/Chain"))
        assertEquals("Lock", tm.layerName("TestModCuffs/Lock"))
    }

    @Test
    fun modLayerNameWithLanguage() {
        val cnTm = TranslationManager("CN")
        assertEquals("链条", cnTm.layerName("TestModCuffs/Chain"))
    }

    // --- Mod asset string tests ---

    @Test
    fun modAssetStringReturnsValue() {
        assertEquals(
            "SourceCharacter locks TestModCuffs on TargetCharacter.",
            tm.assetString("TestModCuffs/Set"),
        )
    }

    @Test
    fun modAssetStringOrNullReturnsValue() {
        assertNotNull(tm.assetStringOrNull("TestModCuffs/Set"))
        assertNull(tm.assetStringOrNull("TestModCuffs/NonExistent"))
    }

    @Test
    fun modAssetStringWithLanguage() {
        val cnTm = TranslationManager("CN")
        assertEquals(
            "SourceCharacter给TargetCharacter戴上了TestModCuffs。",
            cnTm.assetString("TestModCuffs/Set"),
        )
        // English fallback for key without CN translation
        assertEquals(
            "SourceCharacter removes TestModCuffs from TargetCharacter.",
            cnTm.assetString("TestModCuffs/Remove"),
        )
    }

    // --- Graceful handling of missing mod index ---

    @Test
    fun missingModIndexDoesNotBreak() {
        // Use a classloader that won't find mod index
        val emptyLoader = object : ClassLoader(null) {
            override fun getResourceAsStream(name: String?) =
                TranslationManager::class.java.classLoader.getResourceAsStream(
                    if (name?.startsWith("bc/i18n/mods/") == true) "nonexistent" else name
                )
        }
        // Should construct without errors even if mod index is missing
        val tmNoMods = TranslationManager(classLoader = emptyLoader)
        assertNull(tmNoMods.modActivityLabel("TestModPoke"))
    }
}