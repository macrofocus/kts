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

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect abstract class Reader {
    open fun read(): Int
    abstract fun read(dst: CharArray, off: Int, len: Int): Int
    abstract fun close()
}

expect class StringReader(str: String) : Reader {
    override fun read(): Int
    override fun read(dst: CharArray, off: Int, len: Int): Int
    override fun close()
}