package de.bypixeltv.skredis.config

import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.introspector.PropertyUtils

class LenientPropertyUtils : PropertyUtils() {
    override fun getProperty(type: Class<*>, name: String): Property {
        return try {
            super.getProperty(type, name)
        } catch (_: Exception) {
            // Return a fake property that ignores any set calls
            object : Property(name, null) {
                override fun getActualTypeArguments(): Array<out Class<*>?>? {
                    return emptyArray()
                }

                override fun set(target: Any, value: Any) {
                    // do nothing
                }

                override fun get(target: Any): Any? {
                    return null
                }

                override fun getAnnotations(): List<Annotation> {
                    return emptyList()
                }

                override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
                    return null
                }
            }
        }
    }
}
