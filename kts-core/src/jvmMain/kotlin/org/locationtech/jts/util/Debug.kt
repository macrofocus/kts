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
package org.locationtech.jts.util

import org.locationtech.jts.geom.*

/**
 * @version 1.7
 */
/**
 * Provides routines to simplify and localize debugging output.
 * Debugging is controlled via a Java system property value.
 * If the system property with the name given in
 * DEBUG_PROPERTY_NAME (currently "jts.debug") has the value
 * "on" or "true" debugging is enabled.
 * Otherwise, debugging is disabled.
 * The system property can be set by specifying the following JVM option:
 * <pre>
 * -Djts.debug=on
</pre> *
 *
 * @version 1.7
 */
actual class Debug private constructor() {
    private val out: java.io.PrintStream
    private val printArgs: Array<java.lang.Class<*>?>
    private var watchObj: Any? = null
    private val args = arrayOfNulls<Any>(1)

    private class SegmentFindingFilter(private val p0: Coordinate, private val p1: Coordinate) :
        CoordinateSequenceFilter {
        override var isDone = false
            private set

        fun hasSegment(): Boolean {
            return isDone
        }

        override fun filter(seq: CoordinateSequence?, i: Int) {
            if (i == 0) return
            isDone = (p0.equals2D(seq!!.getCoordinate(i - 1))
                    && p1.equals2D(seq.getCoordinate(i)))
        }

        override val isGeometryChanged: Boolean
            get() = false
    }

    init {
        out = java.lang.System.out
        printArgs = arrayOfNulls<java.lang.Class<*>>(1)
        try {
            printArgs[0] = java.lang.Class.forName("java.io.PrintStream")
        } catch (ex: java.lang.Exception) {
            // ignore this exception - it will fail later anyway
        }
    }

    fun instancePrintWatch() {
        if (watchObj == null) return
        instancePrint(watchObj!!)
    }

    fun instancePrintIfWatch(obj: Any) {
        if (obj !== watchObj) return
        if (watchObj == null) return
        instancePrint(watchObj!!)
    }

    fun instancePrint(obj: Any) {
        if (obj is Collection<*>) {
            instancePrint(obj.iterator())
        } else if (obj is Iterator<*>) {
            instancePrint(obj)
        } else {
            instancePrintObject(obj)
        }
    }

    fun instancePrint(it: Iterator<*>) {
        while (it.hasNext()) {
            val obj = it.next()!!
            instancePrintObject(obj)
        }
    }

    fun instancePrintObject(obj: Any) {
        //if (true) throw new RuntimeException("DEBUG TRAP!");
        var printMethod: java.lang.reflect.Method? = null
        try {
            val cls: java.lang.Class<*> = obj.javaClass
            try {
                printMethod = cls.getMethod("print", *printArgs)
                args[0] = out
                out.print(DEBUG_LINE_TAG)
                printMethod.invoke(obj, *args)
            } catch (ex: java.lang.NoSuchMethodException) {
                instancePrint(obj.toString())
            }
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace(out)
        }
    }

    actual fun println() {
        out.println()
    }

    private fun instanceAddWatch(obj: Any) {
        watchObj = obj
    }

    private fun instancePrint(str: String) {
        out.print(DEBUG_LINE_TAG)
        out.print(str)
    }

    actual companion object {
        var DEBUG_PROPERTY_NAME = "jts.debug"
        var DEBUG_PROPERTY_VALUE_ON = "on"
        var DEBUG_PROPERTY_VALUE_TRUE = "true"
        actual var isDebugging = false

        init {
            val debugValue: String = java.lang.System.getProperty(DEBUG_PROPERTY_NAME)
            if (debugValue != null) {
                if (debugValue.equals(DEBUG_PROPERTY_VALUE_ON, ignoreCase = true)
                    || debugValue.equals(DEBUG_PROPERTY_VALUE_TRUE, ignoreCase = true)
                ) isDebugging = true
            }
        }

        private val stopwatch: Stopwatch = Stopwatch()
        private var lastTimePrinted: Long = 0

        /**
         * Prints the status of debugging to <tt>System.out</tt>
         *
         * @param args the cmd-line arguments (no arguments are required)
         */
        @JvmStatic
        fun main(args: Array<String>) {
            println(
                "JTS Debugging is " +
                        if (isDebugging) "ON" else "OFF"
            )
        }

        private val debug = Debug()
        private val fact = GeometryFactory()
        private const val DEBUG_LINE_TAG = "D! "
        fun toLine(p0: Coordinate, p1: Coordinate): LineString {
            return fact.createLineString(arrayOf(p0, p1))
        }

        fun toLine(p0: Coordinate, p1: Coordinate, p2: Coordinate): LineString {
            return fact.createLineString(arrayOf(p0, p1, p2))
        }

        fun toLine(p0: Coordinate, p1: Coordinate, p2: Coordinate, p3: Coordinate): LineString {
            return fact.createLineString(arrayOf(p0, p1, p2, p3))
        }

        actual fun println() {
            if (!isDebugging) {
                return
            }
            debug.println()
        }

        actual fun print(str: String) {
            if (!isDebugging) {
                return
            }
            debug.instancePrint(str)
        }

        /*
  public static void println(String str) {
    if (! debugOn) return;
    debug.instancePrint(str);
    debug.println();
  }
*/
        fun print(obj: Any) {
            if (!isDebugging) return
            debug.instancePrint(obj)
        }

        fun print(isTrue: Boolean, obj: Any) {
            if (!isDebugging) return
            if (!isTrue) return
            debug.instancePrint(obj)
        }

        actual fun println(obj: Any) {
            if (!isDebugging) {
                return
            }
            debug.instancePrint(obj)
            debug.println()
        }

        fun resetTime() {
            stopwatch.reset()
            lastTimePrinted = stopwatch.time
        }

        fun printTime(tag: String) {
            if (!isDebugging) {
                return
            }
            val time: Long = stopwatch.time
            val elapsedTime = time - lastTimePrinted
            debug.instancePrint(
                formatField(Stopwatch.getTimeString(time), 10)
                        + " (" + formatField(Stopwatch.getTimeString(elapsedTime), 10) + " ) "
                        + tag
            )
            debug.println()
            lastTimePrinted = time
        }

        private fun formatField(s: String, fieldLen: Int): String {
            val nPad = fieldLen - s.length
            if (nPad <= 0) return s
            val padStr = spaces(nPad) + s
            return padStr.substring(padStr.length - fieldLen)
        }

        private fun spaces(n: Int): String {
            val ch = CharArray(n)
            for (i in 0 until n) {
                ch[i] = ' '
            }
            return String(ch)
        }

        fun equals(c1: Coordinate, c2: Coordinate?, tolerance: Double): Boolean {
            return c1.distance(c2!!) <= tolerance
        }

        /**
         * Adds an object to be watched.
         * A watched object can be printed out at any time.
         *
         * Currently only supports one watched object at a time.
         * @param obj
         */
        fun addWatch(obj: Any) {
            debug.instanceAddWatch(obj)
        }

        fun printWatch() {
            debug.instancePrintWatch()
        }

        fun printIfWatch(obj: Any) {
            debug.instancePrintIfWatch(obj)
        }

        fun breakIf(cond: Boolean) {
            if (cond) doBreak()
        }

        fun breakIfEqual(o1: Any, o2: Any) {
            if (o1 == o2) doBreak()
        }

        fun breakIfEqual(p0: Coordinate, p1: Coordinate?, tolerance: Double) {
            if (p0.distance(p1!!) <= tolerance) doBreak()
        }

        private fun doBreak() {
            // Put breakpoint on following statement to break here
            return
        }

        fun hasSegment(geom: Geometry, p0: Coordinate, p1: Coordinate): Boolean {
            val filter = SegmentFindingFilter(p0, p1)
            geom.apply(filter)
            return filter.hasSegment()
        }
    }
}