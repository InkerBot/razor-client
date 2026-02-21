package bot.inker.bc.razor.protocol.account

import bot.inker.bc.razor.internal.LZString
import bot.inker.bc.razor.protocol.common.ItemBundle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountUpdateScopeTest {

    @Test
    fun `assigned fields appear in map with correct wire names`() {
        val scope = AccountUpdateScope()
        scope.money = 500
        scope.title = "Queen"
        scope.description = "A mysterious figure"

        assertEquals(3, scope.fields.size)
        assertEquals(500, scope.fields["Money"])
        assertEquals("Queen", scope.fields["Title"])
        assertEquals("A mysterious figure", scope.fields["Description"])
    }

    @Test
    fun `unassigned fields are absent from map`() {
        val scope = AccountUpdateScope()
        scope.money = 100

        assertTrue(scope.fields.containsKey("Money"))
        assertFalse(scope.fields.containsKey("Owner"))
        assertFalse(scope.fields.containsKey("Title"))
        assertFalse(scope.fields.containsKey("FriendList"))
    }

    @Test
    fun `null assignment puts key with null value`() {
        val scope = AccountUpdateScope()
        scope.owner = null

        assertTrue(scope.fields.containsKey("Owner"))
        assertNull(scope.fields["Owner"])
    }

    @Test
    fun `lz fields are auto-compressed`() {
        val scope = AccountUpdateScope()
        val rawJson = """{"12345":"Alice","67890":"Bob"}"""
        scope.friendNames = rawJson

        assertTrue(scope.fields.containsKey("FriendNames"))
        val compressed = scope.fields["FriendNames"] as String
        // Verify it was compressed by decompressing and comparing
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals(rawJson, decompressed)
    }

    @Test
    fun `lz field with null puts null`() {
        val scope = AccountUpdateScope()
        scope.friendNames = null

        assertTrue(scope.fields.containsKey("FriendNames"))
        assertNull(scope.fields["FriendNames"])
    }

    @Test
    fun `submissivesList is lz-compressed`() {
        val scope = AccountUpdateScope()
        scope.submissivesList = "test data"

        val compressed = scope.fields["SubmissivesList"] as String
        assertEquals("test data", LZString.decompressFromUTF16(compressed))
    }

    @Test
    fun `list fields store correct types`() {
        val scope = AccountUpdateScope()
        scope.friendList = listOf(111, 222, 333)
        scope.whiteList = listOf(444)
        scope.blackList = emptyList()
        scope.ghostList = listOf(555)

        assertEquals(listOf(111, 222, 333), scope.fields["FriendList"])
        assertEquals(listOf(444), scope.fields["WhiteList"])
        assertEquals(emptyList<Int>(), scope.fields["BlackList"])
        assertEquals(listOf(555), scope.fields["GhostList"])
    }

    @Test
    fun `complex types are stored correctly`() {
        val scope = AccountUpdateScope()
        val ownership = Ownership(memberNumber = 12345, name = "Owner1", stage = 1)
        scope.ownership = ownership

        assertEquals(ownership, scope.fields["Ownership"])
    }

    @Test
    fun `appearance stores ItemBundle list`() {
        val scope = AccountUpdateScope()
        val items = listOf(
            ItemBundle(group = "Cloth", name = "Dress"),
            ItemBundle(group = "Hat", name = "Crown"),
        )
        scope.appearance = items

        assertEquals(items, scope.fields["Appearance"])
    }

    @Test
    fun `setRaw puts arbitrary key-value`() {
        val scope = AccountUpdateScope()
        scope.setRaw("CustomField", 42)
        scope.setRaw("Another", null)

        assertEquals(42, scope.fields["CustomField"])
        assertTrue(scope.fields.containsKey("Another"))
        assertNull(scope.fields["Another"])
    }

    @Test
    fun `empty scope has no fields`() {
        val scope = AccountUpdateScope()
        assertTrue(scope.fields.isEmpty())
    }

    @Test
    fun `reading back assigned value works`() {
        val scope = AccountUpdateScope()
        scope.money = 999
        assertEquals(999, scope.money)
    }

    @Test
    fun `reading unassigned value returns null`() {
        val scope = AccountUpdateScope()
        assertNull(scope.money)
        assertNull(scope.owner)
    }

    @Test
    fun `skill and reputation store correctly`() {
        val scope = AccountUpdateScope()
        val skills = listOf(Skill("Bondage", 5, 50.0))
        val reps = listOf(Reputation("Dominant", 10))
        scope.skill = skills
        scope.reputation = reps

        assertEquals(skills, scope.fields["Skill"])
        assertEquals(reps, scope.fields["Reputation"])
    }

    @Test
    fun `difficulty stores correctly`() {
        val scope = AccountUpdateScope()
        val diff = Difficulty(level = 3, lastChange = 1700000000L)
        scope.difficulty = diff

        assertEquals(diff, scope.fields["Difficulty"])
    }

    @Test
    fun `all identity fields use correct wire names`() {
        val scope = AccountUpdateScope()
        scope.money = 100
        scope.owner = "test"
        scope.title = "t"
        scope.labelColor = "#FFF"
        scope.description = "d"
        scope.allowedInteractions = 1

        assertEquals(
            setOf("Money", "Owner", "Title", "LabelColor", "Description", "AllowedInteractions"),
            scope.fields.keys
        )
    }
}
