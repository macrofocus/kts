/*
 * Copyright (c) 2026 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.macrofocus.kts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

// kotlinx.serialization of geometries, for documents and workspaces: `@Serializable(with = GeometryGeoJsonSerializer::class)`
// on a property, or `@file:UseSerializers(GeometryGeoJsonSerializer::class)`.

/** A geometry as its well-known text, in any format. */
object GeometryWktSerializer : KSerializer<Geometry> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("org.macrofocus.kts.GeometryWkt", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Geometry) = encoder.encodeString(value.toWkt())
    override fun deserialize(decoder: Decoder): Geometry = wkt(decoder.decodeString())
}

/**
 * A geometry as a GeoJSON object in JSON (readable by other tools), as a GeoJSON string in other formats. Reads a
 * GeoJSON string too, and a Feature or FeatureCollection as its geometry.
 */
object GeometryGeoJsonSerializer : KSerializer<Geometry> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("org.macrofocus.kts.GeometryGeoJson", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Geometry) {
        val text = value.toGeoJson()
        if (encoder is JsonEncoder) encoder.encodeJsonElement(Json.parseToJsonElement(text)) else encoder.encodeString(text)
    }

    override fun deserialize(decoder: Decoder): Geometry {
        val text = if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive && element.isString) element.content else element.toString()
        } else decoder.decodeString()
        return requireNotNull(GeoJson.readGeometry(text)) { "A GeoJSON Feature without geometry" }
    }
}

/** An envelope as `[minX, minY, maxX, maxY]` (the GeoJSON "bbox"), a null envelope as `[]`. */
object EnvelopeSerializer : KSerializer<Envelope> {
    private val delegate = DoubleArraySerializer()
    override val descriptor: SerialDescriptor = SerialDescriptor("org.macrofocus.kts.Envelope", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: Envelope) = encoder.encodeSerializableValue(
        delegate, if (value.isNull) DoubleArray(0) else doubleArrayOf(value.minX, value.minY, value.maxX, value.maxY)
    )

    override fun deserialize(decoder: Decoder): Envelope {
        val bounds = decoder.decodeSerializableValue(delegate)
        return when (bounds.size) {
            0 -> Envelope()
            4 -> envelope(bounds[0], bounds[1], bounds[2], bounds[3])
            else -> throw IllegalArgumentException("An envelope has 4 bounds, not ${bounds.size}")
        }
    }
}
