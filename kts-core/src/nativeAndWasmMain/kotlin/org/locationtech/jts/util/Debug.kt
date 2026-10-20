package org.locationtech.jts.util

/**
 * Provides routines to simplify and localize debugging output.
 * Debugging is controlled via a Java system property value.
 * If the system property with the name given in
 * DEBUG_PROPERTY_NAME (currently "jts.debug") has the value
 * "on" or "true" debugging is enabled.
 * Otherwise, debugging is disabled.
 * The system property can be set by specifying the following JVM option:
 * <pre>
 * -Djts.debug=on
</pre> *
 *
 * @version 1.7
 */
actual class Debug {
    actual fun println() {
    }

    actual companion object {
        actual fun println() {
        }

        actual var isDebugging: Boolean = false

        actual fun print(str: String) {
        }

        actual fun println(obj: Any) {
        }
    }

}