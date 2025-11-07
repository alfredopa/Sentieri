package net.federicomatera.agpxp.models

import java.io.Serializable

data class Track(
    var trackPoints:  List<WayPoint>,
    var name: String? = null,
    val comment: String? = null,
    val description: String? = null,
    val src: String? = null,
    val number: Int? = null,
    val type: String? = null
): Serializable
