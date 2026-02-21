package bot.inker.bc.razor

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OmittablePropertyTest {

    @Test
    fun `getValue returns null when key is absent`() {
        val fields = mutableMapOf<String, Any?>()
        val delegate = OmittableProperty<String>(fields, "Name")
        assertNull(delegate.getValue(null, ::dummyProp))
    }

    @Test
    fun `setValue puts value into map`() {
        val fields = mutableMapOf<String, Any?>()
        val delegate = OmittableProperty<String>(fields, "Name")
        delegate.setValue(null, ::dummyProp, "Alice")
        assertEquals("Alice", fields["Name"])
    }

    @Test
    fun `setValue with null puts null into map`() {
        val fields = mutableMapOf<String, Any?>()
        val delegate = OmittableProperty<String>(fields, "Owner")
        delegate.setValue(null, ::dummyProp, null)
        assertTrue(fields.containsKey("Owner"))
        assertNull(fields["Owner"])
    }

    @Test
    fun `getValue returns previously set value`() {
        val fields = mutableMapOf<String, Any?>()
        val delegate = OmittableProperty<Int>(fields, "Money")
        delegate.setValue(null, ::dummyProp, 500)
        assertEquals(500, delegate.getValue(null, ::dummyProp))
    }

    @Test
    fun `transform is applied on setValue`() {
        val fields = mutableMapOf<String, Any?>()
        val delegate = OmittableProperty<String>(fields, "Upper") { value ->
            value?.uppercase()
        }
        delegate.setValue(null, ::dummyProp, "hello")
        assertEquals("HELLO", fields["Upper"])
    }

    @Test
    fun `transform receives null when value is null`() {
        val fields = mutableMapOf<String, Any?>()
        var transformCalled = false
        val delegate = OmittableProperty<String>(fields, "Field") { value ->
            transformCalled = true
            value?.uppercase()
        }
        delegate.setValue(null, ::dummyProp, null)
        assertTrue(transformCalled)
        assertTrue(fields.containsKey("Field"))
        assertNull(fields["Field"])
    }

    @Test
    fun `unset key is absent from map`() {
        val fields = mutableMapOf<String, Any?>()
        OmittableProperty<String>(fields, "Unset") // never set
        assertFalse(fields.containsKey("Unset"))
    }

    companion object {
        private var dummyProp: String? = null
    }
}
