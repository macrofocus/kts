KTS Topology Suite
==================

The KTS Topology Suite is an Kotlin library of spatial predicates and functions for processing geometry conforming to the Simple Features Specification for SQL published by the Open Geospatial Consortium. KTS is also a port of the well established Java library [JTS](https://github.com/locationtech/jts).

## Building

### JVM

` ./gradlew :kts-core:jvmJar`

-> `kts-core/build/libs/kts-core-jvm.jar`

### JavaScript

` ./gradlew :kts-core:jsJar`

-> `kts-core/build/distributions/kts-core.js`

### Native

` ./gradlew :kts-core:linkNative`

-> `kts-core/build/bin/native/releaseShared/`

## Testing

### JVM

Note: will also run all the Java tests located in `kts-core/src/jvmTest/java/org/locationtech/jts`.

` ./gradlew :kts-core:jvmTest`

### JavaScript

` ./gradlew :kts-core:jsTest`

### Native

` ./gradlew :kts-core:nativeTest`

## Publishing

` ./gradlew publishToMavenLocal`

-> `~/.m2/org/macrofocus/kts/kts-core/`

## License

KTS is open source software.  It is dual-licensed under:

* [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-v20.html)
* [Eclipse Distribution License 1.0](http://www.eclipse.org/org/documents/edl-v10.php) (a BSD Style License)

## Caveats

* At present the port is incomplete.