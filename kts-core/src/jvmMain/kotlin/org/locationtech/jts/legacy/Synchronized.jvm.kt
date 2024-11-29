package org.locationtech.jts.legacy

/**
 * Marks the JVM method generated from the annotated function as `synchronized`, meaning that the method
 * will be protected from concurrent execution by multiple threads by the monitor of the instance (or,
 * for static methods, the class) on which the method is defined.
 *
 * Note that for an extension function, the monitor of the facade class, where it gets compiled to a static method, is used.
 * Therefore, this annotation is recommended to be applied only to member functions and properties.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
public actual annotation class Synchronized actual constructor()