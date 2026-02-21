package bot.inker.bc.razor.internal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class LZStringTest {

    @Test
    fun `round trip simple string`() {
        val input = "Hello, World!"
        val compressed = LZString.compressToUTF16(input)
        assertNotNull(compressed)
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals(input, decompressed)
    }

    @Test
    fun `round trip empty string`() {
        val compressed = LZString.compressToUTF16("")
        assertNotNull(compressed)
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals("", decompressed)
    }

    @Test
    fun `null input returns empty string`() {
        assertEquals("", LZString.compressToUTF16(null))
        assertEquals("", LZString.decompressFromUTF16(null))
    }

    @Test
    fun `decompress empty string throws`() {
        // Empty string is not a valid compressed payload
        assertThrows(StringIndexOutOfBoundsException::class.java) {
            LZString.decompressFromUTF16("")
        }
    }

    @Test
    fun `round trip JSON-like data`() {
        val json = """[{"MemberNumber":12345,"MemberName":"TestUser"},{"MemberNumber":67890,"MemberName":"AnotherUser"}]"""
        val compressed = LZString.compressToUTF16(json)
        assertNotNull(compressed)
        assertTrue(compressed!!.length < json.length, "Compressed should be smaller than original")
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals(json, decompressed)
    }

    @Test
    fun `round trip Unicode content`() {
        val input = "Hello こんにちは 你好 مرحبا"
        val compressed = LZString.compressToUTF16(input)
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals(input, decompressed)
    }

    @Test
    fun `round trip repeated content compresses well`() {
        val input = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val compressed = LZString.compressToUTF16(input)
        assertNotNull(compressed)
        assertTrue(compressed!!.length < input.length, "Repeated content should compress well")
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals(input, decompressed)
    }

    @Test
    fun `round trip long text`() {
        val sb = StringBuilder()
        for (i in 0 until 1000) {
            sb.append("Line $i: This is a test line with some content. ")
        }
        val input = sb.toString()
        val compressed = LZString.compressToUTF16(input)
        assertNotNull(compressed)
        val decompressed = LZString.decompressFromUTF16(compressed)
        assertEquals(input, decompressed)
    }
}
