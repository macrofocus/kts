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

expect abstract class EnumSet<T : Enum<T>>

//expect fun <T> enumSetOf(e1: T): Set<T>
expect inline fun <reified T : Enum<T>> enumSetOf(values: Set<T> = emptySet()): EnumSet<T>
expect fun <T : Enum<T>> EnumSet<T>.values(): Set<T>
expect fun <T : Enum<T>> EnumSet<T>.clone(): EnumSet<T>
expect val <T : Enum<T>> EnumSet<T>.size: Int
expect fun <T : Enum<T>> EnumSet<T>.contains(value: T): Boolean
expect fun <T : Enum<T>> EnumSet<T>.add(value: T)
expect fun <T : Enum<T>> EnumSet<T>.remove(value: T)


