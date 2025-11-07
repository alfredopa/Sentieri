package net.federicomatera.agpxp.models

import java.io.Serializable

data class Gpx(
    val schemas: Map<String, String>? = null,
    val xmlns: String? = null,
    val version: String? = null,
    val creator: String? = null,
    val metadata: GpxMetadata? = null,
    val tracks: List<Track>? = null,
    val routes: List<Route>? = null,
    val wayPoints: List<WayPoint>? = null
): Serializable
