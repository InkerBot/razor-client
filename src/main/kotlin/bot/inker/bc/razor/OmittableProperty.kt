package bot.inker.bc.razor

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OmittableProperty<T>(
    private val fields: MutableMap<String, Any?>,
    private val wireName: String,
    private val transform: ((T?) -> Any?)? = null,
) : ReadWriteProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        @Suppress("UNCHECKED_CAST")
        return fields[wireName] as? T
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        fields[wireName] = if (transform != null) transform(value) else value
    }
}
