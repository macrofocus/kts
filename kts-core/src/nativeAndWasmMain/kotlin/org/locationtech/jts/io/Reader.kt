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

actual abstract class Reader constructor() {
    actual open fun read(): Int {
        val a = CharArray(1)
        if (read(a, 0, 1) < 1)
            return -1 // EOF
        return a[0].toInt()
    }

    actual abstract fun read(dst: CharArray, off: Int, len: Int): Int
    actual abstract fun close()
}

actual class StringReader actual constructor(val str: String) : Reader() {
    private var position: Int = 0

    actual override fun read(): Int = when (position) {
        str.length -> -1
        else -> str[position++].toInt()
    }

    actual override fun read(dst: CharArray, off: Int, len: Int): Int {
        var cnt = 0
        for (i in off until off + len) {
            val r = read()
            if (r == -1) return cnt
            cnt++
            dst[i] = r.toChar()
        }
        return len
    }

    actual override fun close() {}
}