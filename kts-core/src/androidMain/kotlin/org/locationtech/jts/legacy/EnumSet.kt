/*
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.legacy

actual typealias EnumSet<T> = java.util.EnumSet<T>
//actual fun <T> enumSetOf(e1: T): Set<T> = EnumSet.of(e1)
actual inline fun <reified T : Enum<T>> enumSetOf(values: Set<T>) : EnumSet<T> = EnumSet.noneOf(T::class.java).apply { addAll(values) }
actual fun <T: Enum<T>> EnumSet<T>.values() : Set<T> = this
actual fun <T: Enum<T>> EnumSet<T>.clone() : EnumSet<T> = this.clone()
actual val <T : Enum<T>> EnumSet<T>.size: Int
    get() = this.size
actual fun <T: Enum<T>> EnumSet<T>.contains(value: T) : Boolean = this.contains(value)
actual fun <T: Enum<T>> EnumSet<T>.add(value: T) {
    this.add(value)
}
actual fun <T: Enum<T>> EnumSet<T>.remove(value: T) {
    this.add(value)
}
