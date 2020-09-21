package org.locationtech.kts.io

import org.locationtech.kts.assertEquals
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.Ordinate
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.legacy.EnumSet
import org.locationtech.jts.legacy.contains
import org.locationtech.jts.legacy.size
import test.kts.GeometryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Test for [WKTReader]
 *
 * @version 1.7
 */
class WKTReaderTest : GeometryTestCase() {
    // WKT readers used throughout this test
    private val readerXY: WKTReader
    private val readerXYOld: WKTReader
    private val readerXYZ: WKTReader
    private val readerXYM: WKTReader
    private val readerXYZM: WKTReader

    @Test
    @Throws(Exception::class)
    fun testPoint() {

        // arrange
        val coordinates = doubleArrayOf(10.0, 10.0)
        val seqPt2D = createSequence(Ordinate.createXY(), coordinates)
        val seqPt2DE = createSequence(Ordinate.createXY(), DoubleArray(0))
        val seqPt3D = createSequence(Ordinate.createXYZ(), coordinates)
        val seqPt2DM = createSequence(Ordinate.createXYM(), coordinates)
        val seqPt3DM = createSequence(Ordinate.createXYZM(), coordinates)

        // act
        val pt2D = readerXY.read("POINT (10 10)") as Point?
        val pt2DE = readerXY.read("POINT EMPTY") as Point?
        val pt3D = readerXYZ.read("POINT Z(10 10 10)") as Point?
        val pt2DM = readerXYM.read("POINT M(10 10 11)") as Point?
        val pt3DM = readerXYZM.read("POINT ZM(10 10 10 11)") as Point?

        // assert
        assertTrue(checkEqual(seqPt2D, pt2D!!.coordinateSequence!!))
        assertTrue(checkEqual(seqPt2DE, pt2DE!!.coordinateSequence!!))
        assertTrue(checkEqual(seqPt3D, pt3D!!.coordinateSequence!!))
        assertTrue(checkEqual(seqPt2DM, pt2DM!!.coordinateSequence!!))
        assertTrue(checkEqual(seqPt3DM, pt3DM!!.coordinateSequence!!))
    }

    @Test
    @Throws(Exception::class)
    fun testLineString() {

        // arrange
        val coordinates = doubleArrayOf(10.0, 10.0, 20.0, 20.0, 30.0, 40.0)
        val seqLs2D = createSequence(Ordinate.createXY(), coordinates)
        val seqLs2DE = createSequence(Ordinate.createXY(), DoubleArray(0))
        val seqLs3D = createSequence(Ordinate.createXYZ(), coordinates)
        val seqLs2DM = createSequence(Ordinate.createXYM(), coordinates)
        val seqLs3DM = createSequence(Ordinate.createXYZM(), coordinates)

        // act
        val ls2D = readerXY
            .read("LINESTRING (10 10, 20 20, 30 40)") as LineString?
        val ls2DE = readerXY
            .read("LINESTRING EMPTY") as LineString?
        val ls3D = readerXYZ
            .read("LINESTRING Z(10 10 10, 20 20 10, 30 40 10)") as LineString?
        val ls2DM = readerXYM
            .read("LINESTRING M(10 10 11, 20 20 11, 30 40 11)") as LineString?
        val ls3DM = readerXYZM
            .read("LINESTRING ZM(10 10 10 11, 20 20 10 11, 30 40 10 11)") as LineString?

        // assert
        assertTrue(checkEqual(seqLs2D, ls2D!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs2DE, ls2DE!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs3D, ls3D!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs2DM, ls2DM!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs3DM, ls3DM!!.coordinateSequence!!))
    }

    @Test
    @Throws(Exception::class)
    fun testLinearRing() {
        val coordinates = doubleArrayOf(10.0, 10.0, 20.0, 20.0, 30.0, 40.0, 10.0, 10.0)
        val seqLs2D = createSequence(Ordinate.createXY(), coordinates)
        val seqLs2DE = createSequence(Ordinate.createXY(), DoubleArray(0))
        val seqLs3D = createSequence(Ordinate.createXYZ(), coordinates)
        val seqLs2DM = createSequence(Ordinate.createXYM(), coordinates)
        val seqLs3DM = createSequence(Ordinate.createXYZM(), coordinates)

        // act
        val ls2D = readerXY
            .read("LINEARRING (10 10, 20 20, 30 40, 10 10)") as LineString?
        val ls2DE = readerXY
            .read("LINEARRING EMPTY") as LineString?
        val ls3D = readerXYZ
            .read("LINEARRING Z(10 10 10, 20 20 10, 30 40 10, 10 10 10)") as LineString?
        val ls2DM = readerXYM
            .read("LINEARRING M(10 10 11, 20 20 11, 30 40 11, 10 10 11)") as LineString?
        val ls3DM = readerXYZM
            .read("LINEARRING ZM(10 10 10 11, 20 20 10 11, 30 40 10 11, 10 10 10 11)") as LineString?

        // assert
        assertTrue(checkEqual(seqLs2D, ls2D!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs2DE, ls2DE!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs3D, ls3D!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs2DM, ls2DM!!.coordinateSequence!!))
        assertTrue(checkEqual(seqLs3DM, ls3DM!!.coordinateSequence!!))
    }

    @Test
    fun testLinearRingNotClosed() {
        try {
            readerXY.read("LINEARRING (10 10, 20 20, 30 40, 10 99)")
            fail()
        } catch (e: Throwable) {
            assertTrue(e is IllegalArgumentException)
            assertTrue(e.message!!.contains("not form a closed linestring"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testPolygon() {
        val shell = doubleArrayOf(10.0, 10.0, 10.0, 20.0, 20.0, 20.0, 20.0, 15.0, 10.0, 10.0)
        val ring1 = doubleArrayOf(11.0, 11.0, 12.0, 11.0, 12.0, 12.0, 12.0, 11.0, 11.0, 11.0)
        val ring2 = doubleArrayOf(11.0, 19.0, 11.0, 18.0, 12.0, 18.0, 12.0, 19.0, 11.0, 19.0)
        val csPoly2D = arrayOf(
            createSequence(Ordinate.createXY(), shell),
            createSequence(Ordinate.createXY(), ring1),
            createSequence(Ordinate.createXY(), ring2)
        )
        val csPoly2DE = createSequence(Ordinate.createXY(), DoubleArray(0))
        val csPoly3D = arrayOf(
            createSequence(Ordinate.createXYZ(), shell),
            createSequence(Ordinate.createXYZ(), ring1),
            createSequence(Ordinate.createXYZ(), ring2)
        )
        val csPoly2DM = arrayOf(
            createSequence(Ordinate.createXYM(), shell),
            createSequence(Ordinate.createXYM(), ring1),
            createSequence(Ordinate.createXYM(), ring2)
        )
        val csPoly3DM = arrayOf(
            createSequence(Ordinate.createXYZM(), shell),
            createSequence(Ordinate.createXYZM(), ring1),
            createSequence(Ordinate.createXYZM(), ring2)
        )
        var rdr = readerXY
        val poly2D = arrayOf(
            rdr.read("POLYGON ((10 10, 10 20, 20 20, 20 15, 10 10))") as Polygon?,
            rdr.read("POLYGON ((10 10, 10 20, 20 20, 20 15, 10 10), (11 11, 12 11, 12 12, 12 11, 11 11))") as Polygon?,
            rdr.read("POLYGON ((10 10, 10 20, 20 20, 20 15, 10 10), (11 11, 12 11, 12 12, 12 11, 11 11), (11 19, 11 18, 12 18, 12 19, 11 19))") as Polygon?
        )
        val poly2DE = rdr.read("POLYGON EMPTY") as Polygon?
        rdr = readerXYZ
        val poly3D = arrayOf(
            rdr.read("POLYGON Z((10 10 10, 10 20 10, 20 20 10, 20 15 10, 10 10 10))") as Polygon?,
            rdr.read("POLYGON Z((10 10 10, 10 20 10, 20 20 10, 20 15 10, 10 10 10), (11 11 10, 12 11 10, 12 12 10, 12 11 10, 11 11 10))") as Polygon?,
            rdr.read("POLYGON Z((10 10 10, 10 20 10, 20 20 10, 20 15 10, 10 10 10), (11 11 10, 12 11 10, 12 12 10, 12 11 10, 11 11 10), (11 19 10, 11 18 10, 12 18 10, 12 19 10, 11 19 10))") as Polygon?
        )
        rdr = readerXYM
        val poly2DM = arrayOf(
            rdr.read("POLYGON M((10 10 11, 10 20 11, 20 20 11, 20 15 11, 10 10 11))") as Polygon?,
            rdr.read("POLYGON M((10 10 11, 10 20 11, 20 20 11, 20 15 11, 10 10 11), (11 11 11, 12 11 11, 12 12 11, 12 11 11, 11 11 11))") as Polygon?,
            rdr.read("POLYGON M((10 10 11, 10 20 11, 20 20 11, 20 15 11, 10 10 11), (11 11 11, 12 11 11, 12 12 11, 12 11 11, 11 11 11), (11 19 11, 11 18 11, 12 18 11, 12 19 11, 11 19 11))") as Polygon?
        )
        rdr = readerXYZM
        val poly3DM = arrayOf(
            rdr.read("POLYGON ZM((10 10 10 11, 10 20 10 11, 20 20 10 11, 20 15 10 11, 10 10 10 11))") as Polygon?,
            rdr.read("POLYGON ZM((10 10 10 11, 10 20 10 11, 20 20 10 11, 20 15 10 11, 10 10 10 11), (11 11 10 11, 12 11 10 11, 12 12 10 11, 12 11 10 11, 11 11 10 11))") as Polygon?,
            rdr.read("POLYGON ZM((10 10 10 11, 10 20 10 11, 20 20 10 11, 20 15 10 11, 10 10 10 11), (11 11 10 11, 12 11 10 11, 12 12 10 11, 12 11 10 11, 11 11 10 11), (11 19 10 11, 11 18 10 11, 12 18 10 11, 12 19 10 11, 11 19 10 11))") as Polygon?
        )
        // assert
        assertTrue(checkEqual(csPoly2D[0], poly2D[2]!!.exteriorRing!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly2D[1], poly2D[2]!!.getInteriorRingN(0)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly2D[2], poly2D[2]!!.getInteriorRingN(1)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly2DE, poly2DE!!.exteriorRing!!.coordinateSequence, 2))
        assertTrue(checkEqual(csPoly3D[0], poly3D[2]!!.exteriorRing!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly3D[1], poly3D[2]!!.getInteriorRingN(0)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly3D[2], poly3D[2]!!.getInteriorRingN(1)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly2DM[0], poly2DM[2]!!.exteriorRing!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly2DM[1], poly2DM[2]!!.getInteriorRingN(0)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly2DM[2], poly2DM[2]!!.getInteriorRingN(1)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly3DM[0], poly3DM[2]!!.exteriorRing!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly3DM[1], poly3DM[2]!!.getInteriorRingN(0)!!.coordinateSequence!!))
        assertTrue(checkEqual(csPoly3DM[2], poly3DM[2]!!.getInteriorRingN(1)!!.coordinateSequence!!))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXY() {
        val mp = readerXY.read("MULTIPOINT ((10 10), (20 20))") as MultiPoint?
        val cs = createSequences(Ordinate.createXY(), mpCoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXYOldSyntax() {
        val mp = readerXY.read("MULTIPOINT (10 10, 20 20)") as MultiPoint?
        val cs = createSequences(Ordinate.createXY(), mpCoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXY_Empty() {
        val mp = readerXY.read("MULTIPOINT EMPTY") as MultiPoint?
        checkEmpty(mp)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXY_WithEmpty() {
        val mp = readerXY.read("MULTIPOINT ((10 10), EMPTY, (20 20))") as MultiPoint?
        val cs = createSequences(Ordinate.createXY(), mpCoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkEmpty(mp.getGeometryN(1))
        checkCS(cs[1], mp.getGeometryN(2))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXYM() {
        val mp = readerXYM.read("MULTIPOINT M((10 10 11), (20 20 11))") as MultiPoint?
        val cs = createSequences(Ordinate.createXYM(), mpCoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXYZ() {
        val mp = readerXYZ.read("MULTIPOINT Z((10 10 10), (20 20 10))") as MultiPoint?
        val cs = createSequences(Ordinate.createXYZ(), mpCoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPointXYZM() {
        val mp = readerXYZM.read("MULTIPOINT ZM((10 10 10 11), (20 20 10 11))") as MultiPoint?
        val cs = createSequences(Ordinate.createXYZM(), mpCoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    var mLcoords = arrayOf(doubleArrayOf(10.0, 10.0, 20.0, 20.0), doubleArrayOf(15.0, 15.0, 30.0, 15.0))
    @Test
    @Throws(Exception::class)
    fun testMultiLineStringXY() {
        val mp = readerXY.read("MULTILINESTRING ((10 10, 20 20), (15 15, 30 15))") as MultiLineString?
        val cs = createSequences(Ordinate.createXY(), mLcoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiLineStringXY_Empty() {
        val mp = readerXY.read("MULTILINESTRING EMPTY") as MultiLineString?
        checkEmpty(mp)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiLineStringXY_WithEmpty() {
        val mp = readerXY.read("MULTILINESTRING ((10 10, 20 20), EMPTY, (15 15, 30 15))") as MultiLineString?
        val cs = createSequences(Ordinate.createXY(), mLcoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkEmpty(mp.getGeometryN(1))
        checkCS(cs[1], mp.getGeometryN(2))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiLineStringXYM() {
        val mp = readerXYM.read("MULTILINESTRING M((10 10 11, 20 20 11), (15 15 11, 30 15 11))") as MultiLineString?
        val cs = createSequences(Ordinate.createXYM(), mLcoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiLineStringXYZ() {
        val mp = readerXYZ.read("MULTILINESTRING Z((10 10 10, 20 20 10), (15 15 10, 30 15 10))") as MultiLineString?
        val cs = createSequences(Ordinate.createXYZ(), mLcoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    @Test
    @Throws(Exception::class)
    fun testMultiLineStringYZM() {
        val mp =
            readerXYZM.read("MULTILINESTRING ZM((10 10 10 11, 20 20 10 11), (15 15 10 11, 30 15 10 11))") as MultiLineString?
        val cs = createSequences(Ordinate.createXYZM(), mLcoords)
        checkCS(cs[0], mp!!.getGeometryN(0))
        checkCS(cs[1], mp.getGeometryN(1))
    }

    var mAcoords = arrayOf(
        doubleArrayOf(10.0, 10.0, 10.0, 20.0, 20.0, 20.0, 20.0, 15.0, 10.0, 10.0),
        doubleArrayOf(11.0, 11.0, 12.0, 11.0, 12.0, 12.0, 12.0, 11.0, 11.0, 11.0),
        doubleArrayOf(60.0, 60.0, 70.0, 70.0, 80.0, 60.0, 60.0, 60.0)
    )

    @Test
    @Throws(Exception::class)
    fun testMultiPolygonXY() {
        val mp = readerXY.read(
            "MULTIPOLYGON (((10 10, 10 20, 20 20, 20 15, 10 10), (11 11, 12 11, 12 12, 12 11, 11 11)), ((60 60, 70 70, 80 60, 60 60)))"
        ) as MultiPolygon?
        val cs = createSequences(Ordinate.createXY(), mAcoords)
        checkCS(cs[0], (mp!!.getGeometryN(0) as Polygon).exteriorRing)
        checkCS(cs[1], (mp.getGeometryN(0) as Polygon).getInteriorRingN(0))
        checkCS(cs[2], (mp.getGeometryN(1) as Polygon).exteriorRing)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPolygonXY_Empty() {
        val mp = readerXY.read("MULTIPOLYGON EMPTY") as MultiPolygon?
        checkEmpty(mp)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPolygonXY_WithEmpty() {
        val mp = readerXY.read(
            "MULTIPOLYGON (((10 10, 10 20, 20 20, 20 15, 10 10), (11 11, 12 11, 12 12, 12 11, 11 11)), EMPTY, ((60 60, 70 70, 80 60, 60 60)))"
        ) as MultiPolygon?
        val cs = createSequences(Ordinate.createXY(), mAcoords)
        checkCS(cs[0], (mp!!.getGeometryN(0) as Polygon).exteriorRing)
        checkCS(cs[1], (mp.getGeometryN(0) as Polygon).getInteriorRingN(0))
        checkEmpty(mp.getGeometryN(1) as Polygon)
        checkCS(cs[2], (mp.getGeometryN(2) as Polygon).exteriorRing)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPolygonXYM() {
        val mp = readerXYM.read(
            "MULTIPOLYGON M(((10 10 11, 10 20 11, 20 20 11, 20 15 11, 10 10 11), (11 11 11, 12 11 11, 12 12 11, 12 11 11, 11 11 11)), ((60 60 11, 70 70 11, 80 60 11, 60 60 11)))"
        ) as MultiPolygon?
        val cs = createSequences(Ordinate.createXYM(), mAcoords)
        checkCS(cs[0], (mp!!.getGeometryN(0) as Polygon).exteriorRing)
        checkCS(cs[1], (mp.getGeometryN(0) as Polygon).getInteriorRingN(0))
        checkCS(cs[2], (mp.getGeometryN(1) as Polygon).exteriorRing)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPolygonXYZ() {
        val mp = readerXYZ.read(
            "MULTIPOLYGON Z(((10 10 10, 10 20 10, 20 20 10, 20 15 10, 10 10 10), (11 11 10, 12 11 10, 12 12 10, 12 11 10, 11 11 10)), ((60 60 10, 70 70 10, 80 60 10, 60 60 10)))"
        ) as MultiPolygon?
        val cs = createSequences(Ordinate.createXYZ(), mAcoords)
        checkCS(cs[0], (mp!!.getGeometryN(0) as Polygon).exteriorRing)
        checkCS(cs[1], (mp.getGeometryN(0) as Polygon).getInteriorRingN(0))
        checkCS(cs[2], (mp.getGeometryN(1) as Polygon).exteriorRing)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiPolygonYZM() {
        val mp = readerXYZM.read(
            "MULTIPOLYGON ZM(((10 10 10 11, 10 20 10 11, 20 20 10 11, 20 15 10 11, 10 10 10 11), (11 11 10 11, 12 11 10 11, 12 12 10 11, 12 11 10 11, 11 11 10 11)), ((60 60 10 11, 70 70 10 11, 80 60 10 11, 60 60 10 11)))"
        ) as MultiPolygon?
        val cs = createSequences(Ordinate.createXYZM(), mAcoords)
        checkCS(cs[0], (mp!!.getGeometryN(0) as Polygon).exteriorRing)
        checkCS(cs[1], (mp.getGeometryN(0) as Polygon).getInteriorRingN(0))
        checkCS(cs[2], (mp.getGeometryN(1) as Polygon).exteriorRing)
    }

    @Test
    @Throws(Exception::class)
    fun testGeometryCollection() {

        // arrange
        val coordinates = arrayOf(
            doubleArrayOf(10.0, 10.0),
            doubleArrayOf(30.0, 30.0),
            doubleArrayOf(15.0, 15.0, 20.0, 20.0),
            DoubleArray(0),
            doubleArrayOf(10.0, 10.0, 20.0, 20.0, 30.0, 40.0, 10.0, 10.0)
        )
        val css = arrayOf(
            createSequence(Ordinate.createXY(), coordinates[0]),
            createSequence(Ordinate.createXY(), coordinates[1]),
            createSequence(Ordinate.createXY(), coordinates[2]),
            createSequence(Ordinate.createXY(), coordinates[3]),
            createSequence(Ordinate.createXY(), coordinates[4])
        )

        // arrange
        val rdr: WKTReader = getWKTReader(Ordinate.createXY(), 1.0)
        val gc0 =
            rdr.read("GEOMETRYCOLLECTION (POINT (10 10), POINT (30 30), LINESTRING (15 15, 20 20))") as GeometryCollection?
        val gc1 =
            rdr.read("GEOMETRYCOLLECTION (POINT (10 10), LINEARRING EMPTY, LINESTRING (15 15, 20 20))") as GeometryCollection?
        val gc2 =
            rdr.read("GEOMETRYCOLLECTION (POINT (10 10), LINEARRING (10 10, 20 20, 30 40, 10 10), LINESTRING (15 15, 20 20))") as GeometryCollection?
        val gc3 = rdr.read("GEOMETRYCOLLECTION EMPTY") as GeometryCollection?

        // assert
        assertTrue(checkEqual(css[0], (gc0!!.getGeometryN(0) as Point).coordinateSequence!!))
        assertTrue(checkEqual(css[1], (gc0!!.getGeometryN(1) as Point).coordinateSequence!!))
        assertTrue(checkEqual(css[2], (gc0!!.getGeometryN(2) as LineString).coordinateSequence!!))
        assertTrue(checkEqual(css[0], (gc1!!.getGeometryN(0) as Point).coordinateSequence!!))
        assertTrue(checkEqual(css[3], (gc1!!.getGeometryN(1) as LinearRing).coordinateSequence!!))
        assertTrue(checkEqual(css[2], (gc1!!.getGeometryN(2) as LineString).coordinateSequence!!))
        assertTrue(checkEqual(css[0], (gc2!!.getGeometryN(0) as Point).coordinateSequence!!))
        assertTrue(checkEqual(css[4], (gc2!!.getGeometryN(1) as LinearRing).coordinateSequence!!))
        assertTrue(checkEqual(css[2], (gc2!!.getGeometryN(2) as LineString).coordinateSequence!!))
        assertTrue(gc3!!.isEmpty)
    }

    @Test
    @Throws(Exception::class)
    fun testNaN() {

        // arrange
        val seq = createSequence(Ordinate.createXYZ(), doubleArrayOf(10.0, 10.0))
        seq.setOrdinate(0, CoordinateSequence.Z, Double.NaN)

        // act
        val pt1 = readerXYOld.read("POINT (10 10 NaN)") as Point?
        val pt2 = readerXYOld.read("POINT (10 10 nan)") as Point?
        val pt3 = readerXYOld.read("POINT (10 10 NAN)") as Point?

        // assert
        assertTrue(checkEqual(seq, pt1!!.coordinateSequence!!))
        assertTrue(checkEqual(seq, pt2!!.coordinateSequence!!))
        assertTrue(checkEqual(seq, pt3!!.coordinateSequence!!))
    }

    @Test
    @Throws(Exception::class)
    fun testLargeNumbers() {
        val precisionModel = PrecisionModel(1E9)
        val geometryFactory = GeometryFactory(precisionModel, 0)
        val reader = WKTReader(geometryFactory, allowOldJtsCoordinateSyntax = false)
        val point1 = (reader.read("POINT (123456789.01234567890 10)") as Point?)!!.coordinateSequence
        val point2 = geometryFactory.createPoint(Coordinate(123456789.01234567890, 10.0)).coordinateSequence
        assertEquals(point1!!.getOrdinate(0, CoordinateSequence.X), point2!!.getOrdinate(0, CoordinateSequence.X), 1E-7)
        assertEquals(point1!!.getOrdinate(0, CoordinateSequence.Y), point2!!.getOrdinate(0, CoordinateSequence.Y), 1E-7)
    }

    // ToDo: Locale not supported
//    @Throws(Exception::class)
//    fun testTurkishLocale() {
//        val original = java.util.Locale.getDefault()
//        try {
//            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
//            val point = readerXY.read("point (10 20)") as Point?
//            assertEquals(10.0, point!!.x, 1E-7)
//            assertEquals(20.0, point!!.y, 1E-7)
//        } finally {
//            java.util.Locale.setDefault(original)
//        }
//    }

    private fun checkCS(cs: CoordinateSequence?, geom: Geometry?) {
        assertTrue(checkEqual(cs!!, extractCS(geom)!!))
    }

    private fun extractCS(geom: Geometry?): CoordinateSequence? {
        if (geom is Point) return geom.coordinateSequence
        if (geom is LineString) return geom.coordinateSequence
        throw IllegalArgumentException("Can't extract coordinate sequence from geometry of type " + geom!!.geometryType)
    }

    private fun checkEmpty(geom: Geometry?) {
        assertTrue(geom!!.isEmpty)
        if (geom is GeometryCollection) {
            assertTrue(geom.numGeometries == 0)
        }
    }

    companion object {
        var mpCoords = arrayOf(doubleArrayOf(10.0, 10.0), doubleArrayOf(20.0, 20.0))
        private fun createSequences(
            ordinateFlags: EnumSet<Ordinate>,
            xyarray: Array<DoubleArray>
        ): Array<CoordinateSequence?> {
            val csarray = arrayOfNulls<CoordinateSequence>(xyarray.size)
            for (i in xyarray.indices) {
                csarray[i] = createSequence(ordinateFlags, xyarray[i])
            }
            return csarray
        }

        private fun createSequence(ordinateFlags: EnumSet<Ordinate>, xy: DoubleArray): CoordinateSequence {

            // get the number of dimension to verify size of provided ordinate values array
            val dimension = requiredDimension(ordinateFlags)

            // inject additional values
            val ordinateValues = injectZM(ordinateFlags, xy)
            require(ordinateValues.size % dimension == 0) { "ordinateFlags and number of provided ordinate values don't match" }

            // get the required size of the sequence
            val size = ordinateValues.size / dimension

            // create a sequence capable of storing all ordinate values.
            val res: CoordinateSequence = getCSFactory(ordinateFlags)
                .create(size, requiredDimension(ordinateFlags))

            // fill in values
            var k = 0
            var i = 0
            while (i < ordinateValues.size) {
                for (j in 0 until dimension) res.setOrdinate(k, j, ordinateValues[i + j])
                k++
                i += dimension
            }
            return res
        }

        private fun requiredDimension(ordinateFlags: EnumSet<Ordinate>): Int {
            return ordinateFlags.size
        }

        private fun injectZM(ordinateFlags: EnumSet<Ordinate>, xy: DoubleArray): DoubleArray {
            val size = xy.size / 2
            val dimension = requiredDimension(ordinateFlags)
            val res = DoubleArray(size * dimension)
            var k = 0
            var i = 0
            while (i < xy.size) {
                res[k++] = xy[i]
                res[k++] = xy[i + 1]
                if (ordinateFlags.contains(Ordinate.Z)) res[k++] = 10.0
                if (ordinateFlags.contains(Ordinate.M)) res[k++] = 11.0
                i += 2
            }
            return res
        }
    }

    init {
        readerXY = getWKTReader(Ordinate.createXY(), 1.0)
        readerXY.setIsOldJtsCoordinateSyntaxAllowed(false)
        readerXYOld = getWKTReader(Ordinate.createXY(), 1.0)
        // set this explicitly because GeometryTestCase default is false
        readerXYOld.setIsOldJtsCoordinateSyntaxAllowed(true)
        readerXYZ = getWKTReader(Ordinate.createXYZ(), 1.0)
        readerXYM = getWKTReader(Ordinate.createXYM(), 1.0)
        readerXYZM = getWKTReader(Ordinate.createXYZM(), 1.0)
    }
}