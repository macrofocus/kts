KTS Topology Suite
==================

The KTS Topology Suite is a Kotlin library of spatial predicates and functions for processing geometry conforming to the Simple Features Specification for SQL published by the Open Geospatial Consortium. KTS is also a port of the well established Java library [JTS](https://github.com/locationtech/jts). It is being developed by [Luc Girardin](mailto:luc.girardin@macrofocus.com) at [Macrofocus Gmbh](https://www.macrofocus.com/).

## Building

### JVM

` ./gradlew :kts-core:jvmJar`

-> `kts-core/build/libs/kts-core-jvm.jar`

### JavaScript

` ./gradlew :kts-core:jsBrowserProductionLibraryDistribution`

-> `kts-core/build/dist/js/productionLibrary`

### WebAssembly

` ./gradlew :kts-core:wasmJsBrowserProductionLibraryDistribution`

-> `kts-core/build/dist/wasmJs/productionLibrary`

### Native

` ./gradlew :kts-core:assemble`

-> `kts-core/build/bin/`

## Testing

### JVM

Note: will also run all the Java tests located in `kts-core/src/jvmTest/java/org/locationtech/jts`.

` ./gradlew :kts-core:jvmTest`

### Binary compatibility with JTS (JVM)

Compiles the Java tests against the official jts-core jar, runs the resulting classes once against jts-core and once
against kts-core, and fails if any test fails only with kts-core (e.g. `NoSuchMethodError`).

` ./gradlew :kts-core:jtsCompat`

### JavaScript

` ./gradlew :kts-core:jsTest`

### WebAssembly

` ./gradlew :kts-core:wasmJsNodeTest`

### Native

` ./gradlew :kts-core:nativeTest`

## Kotlin extensions (kts-kotlin)

`kts-kotlin` adds Kotlin-specific API on top of `kts-core` (package `org.macrofocus.kts`), for every target of `kts-core`:

```kotlin
val factory = geometryFactory(srid = 4326, storage = CoordinateStorage.PackedDouble)
val shape = factory.build {
    polygon {
        xy(0, 0); xy(10, 0); xy(10, 10); xy(0, 10)          // rings are closed automatically
        hole { xy(2, 2); xy(4, 2); xy(4, 4) }
    }
}
val ring = shape - shape.buffer(-1.0) { join = Join.Mitre }  // + - and or xor
val all = parcels.unionAll()                                 // cascaded union
shape.forEachXY { x, y -> /* no Coordinate allocated */ }
val mercator = shape.transformCoordinates { y = ln(tan(PI / 4 + y * PI / 360)) * 180 / PI }
```

For performance:

* **The DSL** packs vertices in a `DoubleArray`. With `CoordinateStorage.PackedDouble`, that array becomes the
  coordinate sequence without allocating a `Coordinate` per vertex: about 2x faster than `createLineString(Array<Coordinate>)`.
* **`PackedRTree`** is a static Hilbert-packed R-tree (after Flatbush). On 1,000,000 boxes it builds 3x faster than
  `HPRtree` and 15x faster than `STRtree`. Its queries take about as long as `HPRtree`'s and are 3x faster than
  `STRtree`'s. It also has an inline, list-free `query` and k-nearest `neighbors`.
* **`PointLocator`** finds which side of a polygon a point is on with packed segments in horizontal bands, allocating nothing per point. It is 8-9x faster than
  `IndexedPointInAreaLocator` and 10x faster than `PreparedGeometry.contains(Point)`, with the same robust
  orientation, so the results are identical.

Also, with coroutine and `Flow` variants of the bulk operations (on `Dispatchers.Default` by default; times on a
20-core Mac):

* **`RegionLocator<T>`** finds the region under a point for hit testing on a map. It combines a `PackedRTree`, a
  `PointLocator` per polygon and a tolerance for lines and points. When several regions match, it returns the first in
  the order given. For 1,000,000 points among 2,500 polygons it takes 487 ms, against 925 ms for STRtree plus
  `PreparedGeometry`. `locateAll(xs, ys)` runs concurrently and takes 38 ms. `Flow.locateIn(locator)` pairs a stream of
  points with their regions.
* **`emitPath(sink, transform, tolerance, clip)`** sends geometries to any `PathSink`, such as a Compose or Skia path,
  straight from the coordinate sequences. It skips vertices closer than the tolerance on screen, holes smaller than it,
  and parts outside the clip. A `PackedPath` records the result for cheap replay, and `toPackedPaths` records many
  concurrently.
* **Geography** in degrees: `haversineDistance`, `geodesicLength`/`geodesicArea`, `WebMercator` (a `ViewTransform`
  composable with `then`), `geoEnvelopeAround(lon, lat, meters)`, `splitAtAntimeridian()` and `bufferMeters()`.
* **`GeoJson`** reads and writes GeoJSON straight into packed arrays: 4.2 MB in 29 ms, faster than kotlinx's
  `parseToJsonElement` (55 ms), which builds no geometry. `features()` reads a collection lazily as a `Sequence`, and
  `featureFlow()` parses it off the collecting thread.
* **`spatialJoin(left, right, predicate)`** prepares the right side once and tests the converse predicate. 200,000
  points against 2,500 polygons take 264 ms, and 43 ms with `spatialJoinConcurrently`. `spatialJoinFlow` emits pairs
  as each chunk finishes, and `PackedRTree.join` pairs two trees.
* **`unionAllConcurrently()`** groups geometries along the Hilbert curve before unioning them: 2,500 polygons take
  173 ms, against 756 ms for `unionAll()`.
* **Also:** `userData<T>()`, `labelPoint()` (the pole of inaccessibility), `equalsNormalized`/`shapeDistance` for
  tests, and kotlinx.serialization serializers (`GeometryGeoJsonSerializer`, which writes a GeoJSON object in JSON,
  `GeometryWktSerializer` and `EnvelopeSerializer`).

Benchmarks: ` ./gradlew :kts-kotlin:jvmTest -Pkts.benchmark=true --tests '*Benchmark*'`

## Publishing

` ./gradlew publishToMavenLocal`

-> `~/.m2/org/macrofocus/kts/kts-core/`

## License

KTS is open source software.  It is dual-licensed under:

* [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-v20.html)
* [Eclipse Distribution License 1.0](http://www.eclipse.org/org/documents/edl-v10.php) (a BSD Style License)

## Caveats

* At present the port is incomplete.