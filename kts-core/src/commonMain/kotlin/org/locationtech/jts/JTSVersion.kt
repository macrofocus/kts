/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts

import kotlin.jvm.JvmStatic

/**
 * JTS API version information.
 *
 * Versions consist of a 3-part version number: `major.minor.patch`
 * An optional release status string may be present in the string version of
 * the version.
 *
 * @version 1.7
 */
class JTSVersion private constructor() {
    /**
     * Gets the full version number, suitable for display.
     *
     * @return the full version number, suitable for display.
     */
    override fun toString(): String {
        val ver = "$major.$minor.$patch"
        return if (!RELEASE_INFO.isNullOrEmpty()) "$ver $RELEASE_INFO" else ver
    }

    companion object {
        /**
         * The current version number of the JTS API.
         */
        val CURRENT_VERSION = JTSVersion()
        /**
         * Gets the major number of the release version.
         *
         * @return the major number of the release version.
         */
        /**
         * The major version number.
         */
        const val major = 1
        /**
         * Gets the minor number of the release version.
         *
         * @return the minor number of the release version.
         */
        /**
         * The minor version number.
         */
        const val minor = 19
        /**
         * Gets the patch number of the release version.
         *
         * @return the patch number of the release version.
         */
        /**
         * The patch version number.
         */
        const val patch = 0

        /**
         * An optional string providing further release info (such as "alpha 1");
         */
        private const val RELEASE_INFO: String = ""

        /**
         * Prints the current JTS version to stdout.
         *
         * @param args the command-line arguments (none are required).
         */
        @JvmStatic
        fun main(args: Array<String>) {
            println(CURRENT_VERSION)
        }
    }
}