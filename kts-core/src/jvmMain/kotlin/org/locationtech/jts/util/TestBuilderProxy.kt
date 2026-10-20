/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.util

import org.locationtech.jts.geom.Geometry

/**
 * A proxy to call TestBuilder functions.
 * If the code is not being run in the context of the
 * TestBuilder, functions act as a no-op.
 *
 * It is recommended that functions only be inserted into
 * code temporarily (i.e. in a development environment).
 *
 * This class is experimental, and
 * is not recommended for production use.
 *
 * @author Martin Davis
 */
object TestBuilderProxy {
    private const val CLASS_FUNCTIONS_UTIL = "org.locationtech.jtstest.function.FunctionsUtil"
    private var tbClass: java.lang.Class<*>? = null
    private var methodShowIndicator: java.lang.reflect.Method? = null
    private var methodShowIndicatorLine: java.lang.reflect.Method? = null
    private fun init() {
        if (tbClass != null) return
        try {
            tbClass = TestBuilderProxy::class.java.getClassLoader().loadClass(CLASS_FUNCTIONS_UTIL)
            methodShowIndicator = tbClass!!.getMethod("showIndicator", Geometry::class.java)
            methodShowIndicatorLine =
                tbClass!!.getMethod("showIndicator", Geometry::class.java, java.awt.Color::class.java)
        } catch (ex: Exception) {
            // Fail silently to avoid unexpected output in production
            //System.err.println("TestBuilderProxy: Can't init");
        }
    }

    /**
     * Tests whether the proxy is active (i.e. the TestBuilder is available).
     * This allows avoiding expensive geometry creation if not needed.
     *
     * @return true if the proxy is active
     */
    @JvmStatic
    val isActive: Boolean
        get() {
            init()
            return tbClass != null
        }

    /**
     * Shows a geometry as an indicator in the TestBuilder Edit panel.
     * The geometry is only displayed until the next screen refresh.
     * The TestBuilder also provides a menu option to capture
     * indicators on a layer.
     *
     * @param geom the geometry to display
     */
    @JvmStatic
    fun showIndicator(geom: Geometry?) {
        init()
        if (methodShowIndicator == null) return
        try {
            methodShowIndicator!!.invoke(null, geom)
        } catch (e: java.lang.Exception) {
            // Fail silently to avoid unexpected output in production
            // Or perhaps should fail noisy, since at this point the function should be working?
        }
    }

    /**
     * Shows a geometry as an indicator in the TestBuilder Edit panel.
     * The geometry is only displayed until the next screen refresh.
     * The TestBuilder also provides a menu option to capture
     * indicators on a layer.
     *
     * @param geom the geometry to display
     */
    @JvmStatic
    fun showIndicator(geom: Geometry?, lineClr: java.awt.Color?) {
        init()
        if (methodShowIndicatorLine == null) return
        try {
            methodShowIndicatorLine!!.invoke(null, geom, lineClr)
        } catch (e: Exception) {
            // Fail silently to avoid unexpected output in production
            // Or perhaps should fail noisy, since at this point the function should be working?
        }
    }
}