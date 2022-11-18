/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Some of the code has been derived from the Tiffany library, which came with the following license:
 *
 * Copyright 2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.locationtech.jts.io

@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual typealias Writer = java.io.Writer

actual open class PrintWriter actual constructor(val w: Writer) : Writer() {
    actual open fun print(s: String) = w.write(s)
    actual open fun print(ch: Char) = w.write(ch.toInt())
    actual open fun print(value: Float) = print(value.toString())
    actual open fun print(value: Double) = print(value.toString())
    actual open fun print(value: Boolean) = print(value.toString())
    actual open fun print(value: Int) = print(value.toString())
    actual open fun print(value: Long) = print(value.toString())
    actual open fun print(value: Any?) = print(value.toString())

    actual open fun println() = w.write(10)
    actual open fun println(s: String) {
        w.write(s); println()
    }

    actual open fun println(ch: Char) {
        w.write(ch.toInt()); println()
    }

    actual open fun println(value: Float) = println(value.toString())
    actual open fun println(value: Double) = println(value.toString())
    actual open fun println(value: Boolean) = println(value.toString())
    actual open fun println(value: Int) = println(value.toString())
    actual open fun println(value: Long) = println(value.toString())
    actual open fun println(value: Any?) = println(value.toString())

    actual override fun write(src: CharArray, off: Int, len: Int) {
        w.write(src, off, len)
    }

    actual override fun flush() {}
    actual override fun close() {}
}
actual typealias StringWriter = java.io.StringWriter