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
package org.locationtech.jts.io.gml2

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.gml2.GeometryStrategies.ParseStrategy
import java.util.*

/**
 * A SAX [DefaultHandler] which builds [Geometry]s
 * from GML2-formatted geometries.
 * An XML parser can delegate SAX events to this handler
 * to parse and building Geometrys.
 *
 * This handler currently ignores both namespaces and prefixes.
 *
 * Hints:
 *
 *  * If your parent handler is a DefaultHandler register the parent handler to receive the errors and locator calls.
 *  * Use [GeometryStrategies.findStrategy] to help check for applicability
 *
 * @see DefaultHandler
 *
 * @author David Zwiers, Vivid Solutions.
 */
open class GMLHandler(private val gf: GeometryFactory? = null, private val delegate: org.xml.sax.ErrorHandler? = null) : org.xml.sax.helpers.DefaultHandler() {
    /**
     * This class is intended to log the SAX activity within a given element until its termination.
     * At this time, a new object of value is created and passed to the parent.
     * An object of value is typically either java.lang.* or a JTS Geometry
     * This class is not intended for use outside this distribution,
     * and may change in subsequent versions.
     *
     * @author David Zwiers, Vivid Solutions.
     */
    class Handler(strategy: ParseStrategy?, attributes: org.xml.sax.Attributes?) {
        var attrs: org.xml.sax.Attributes? = null
        protected var strategy: ParseStrategy?
        var text: StringBuilder? = null

        /**
         * Caches text for the future
         * @param str
         */
        fun addText(str: String?) {
            if (text == null) text = StringBuilder()
            text!!.append(str)
        }

        var children: MutableList<Any>? = null

        /**
         * @param strategy
         * @param attributes Nullable
         */
        init {
            if (attributes != null) attrs = org.xml.sax.helpers.AttributesImpl(attributes)
            this.strategy = strategy
        }

        /**
         * Store param for the future
         *
         * @param obj
         */
        fun keep(obj: Any) {
            if (children == null) children = LinkedList<Any>()
            children!!.add(obj)
        }

        /**
         * @param gf GeometryFactory
         * @return Parsed Object
         * @throws SAXException
         */
        @Throws(org.xml.sax.SAXException::class)
        fun create(gf: GeometryFactory): Any {
            return strategy!!.parse(this, gf)!!
        }
    }

    private val stack: Stack<Any?> = Stack()

    /**
     * Tests whether this handler has completed parsing
     * a geometry.
     * If this is the case, [.getGeometry] can be called
     * to get the value of the parsed geometry.
     *
     * @return if the parsing of the geometry is complete
     */
    val isGeometryComplete: Boolean
        get() {
            if (stack.size > 1) return false
            // top level node on stack needs to have at least one child 
            val h = stack.peek() as Handler
            return if (h.children!!.size < 1) false else true
        }

    /**
     * Gets the geometry parsed by this handler.
     * This method should only be called AFTER the parser has completed execution
     *
     * @return the parsed Geometry, or a GeometryCollection if more than one geometry was parsed
     * @throws IllegalStateException if called before the parse is complete
     */
    val geometry: Geometry
        get() {
            if (stack.size == 1) {
                val h = stack.peek() as Handler
                return if (h.children!!.size == 1) h!!.children!!.get(0)!! as Geometry else gf!!.createGeometryCollection(
                    h.children!!.toTypedArray() as Array<Geometry>
                )
            }
            throw IllegalStateException(
                "Parse did not complete as expected, there are " + stack.size
                        + " elements on the Stack"
            )
        }
    //////////////////////////////////////////////
    // Parsing Methods
    /**
     * @see org.xml.sax.helpers.DefaultHandler.characters
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (!stack.isEmpty()) (stack.peek() as Handler).addText(String(ch, start, length))
    }

    /**
     * @see org.xml.sax.helpers.DefaultHandler.ignorableWhitespace
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) {
        if (!stack.isEmpty()) (stack.peek() as Handler).addText(" ")
    }

    /**
     * @see org.xml.sax.helpers.DefaultHandler.endElement
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun endElement(uri: String, localName: String, qName: String) {
        val thisAction = stack.pop() as Handler
        (stack.peek() as Handler).keep(thisAction.create(gf!!))
    }

    /**
     * @see org.xml.sax.helpers.DefaultHandler.startElement
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun startElement(
        uri: String, localName: String, qName: String,
        attributes: org.xml.sax.Attributes
    ) {
        // create a handler
        var ps: ParseStrategy? = GeometryStrategies.findStrategy(uri, localName)
        if (ps == null) {
            val qn = qName.substring(qName.indexOf(':') + 1, qName.length)
            ps = GeometryStrategies.findStrategy(null, qn)
        }
        val h = Handler(ps, attributes)
        // and add it to the stack
        stack.push(h)
    }

    //////////////////////////////////////////////
    // Logging Methods
    private var locator: org.xml.sax.Locator? = null

    /**
     * Creates a new handler.
     * Allows the user to specify a delegate object for error / warning messages.
     * If the delegate also implements ContentHandler then the document Locator will be passed on.
     *
     * @param gf Geometry Factory
     * @param delegate Nullable
     *
     * @see ErrorHandler
     *
     * @see ContentHandler
     *
     * @see ContentHandler.setDocumentLocator
     * @see org.xml.sax.Locator
     */
    init {
        stack.push(Handler(null, null))
    }

    /**
     * @see org.xml.sax.helpers.DefaultHandler.setDocumentLocator
     */
//    protected var documentLocator: org.xml.sax.Locator?
//        protected get() = locator
//        set(locator) {
//            this.locator = locator
//            if (delegate is org.xml.sax.ContentHandler) (delegate as org.xml.sax.ContentHandler).setDocumentLocator(
//                locator
//            )
//        }
    //////////////////////////////////////////////
    // ERROR Methods
    /**
     * @see org.xml.sax.helpers.DefaultHandler.fatalError
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun fatalError(e: org.xml.sax.SAXParseException) {
        if (delegate != null) delegate.fatalError(e) else super.fatalError(e)
    }

    /**
     * @see org.xml.sax.helpers.DefaultHandler.error
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun error(e: org.xml.sax.SAXParseException) {
        if (delegate != null) delegate.error(e) else super.error(e)
    }

    /**
     * @see org.xml.sax.helpers.DefaultHandler.warning
     */
    @Throws(org.xml.sax.SAXException::class)
    override fun warning(e: org.xml.sax.SAXParseException) {
        if (delegate != null) delegate.warning(e) else super.warning(e)
    }
}