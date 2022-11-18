/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Some of the code has been derived from GWT 2.9.0, which came with the following license:
 *
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.locationtech.jts.legacy

import kotlin.jvm.JvmStatic

object Character {
    const val MIN_RADIX = 2

    const val MAX_RADIX = 36

    @JvmStatic
    fun isWhitespace(value: Char): Boolean {
        return value.isWhitespace()
    }

    @JvmStatic
    fun isDigit(value: Char): Boolean {
        return "0123456789".contains(value)
    }

    /*
   * TODO: correct Unicode handling.
   */
    fun digit(c: Char, radix: Int): Int {
        if (radix < MIN_RADIX || radix > MAX_RADIX) {
            return -1
        }
        if (c >= '0' && c.toInt() < '0'.toInt() + Math.min(radix, 10)) {
            return c - '0'
        }

        // The offset by 10 is to re-base the alpha values
        if (c >= 'a' && c.toInt() < radix + 'a'.toInt() - 10) {
            return c - 'a' + 10
        }
        return if (c >= 'A' && c.toInt() < radix + 'A'.toInt() - 10) {
            c - 'A' + 10
        } else -1
    }
}

