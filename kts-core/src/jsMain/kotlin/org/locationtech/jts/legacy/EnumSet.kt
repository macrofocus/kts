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

actual abstract class EnumSet<T:Enum<T>>(val values: MutableSet<T>)
//actual fun <T> enumSetOf(e1: T): Set<T> = setOf(e1)
//actual inline fun <reified T:Enum<T>> enumSetOf(values: Set<T>) : EnumSet<T> = object : EnumSet<T>(values.toMutableSet()) {}
actual inline fun <reified T : Enum<T>> enumSetOf(vararg values: T): EnumSet<T> = object : EnumSet<T>(values.toMutableSet()) {}
actual fun <T: Enum<T>> EnumSet<T>.values() : Set<T> = this.values
actual fun <T: Enum<T>> EnumSet<T>.clone() : EnumSet<T> = object : EnumSet<T>(this.values) {}
actual val <T : Enum<T>> EnumSet<T>.size: Int
    get() = this.values.size
actual fun <T : Enum<T>> EnumSet<T>.contains(value: T): Boolean {
    return this.values.contains(value)
}
actual fun <T : Enum<T>> EnumSet<T>.add(value: T) {
    this.values.add(value)
}
actual fun <T : Enum<T>> EnumSet<T>.remove(value: T) {
    this.values.remove(value)
}