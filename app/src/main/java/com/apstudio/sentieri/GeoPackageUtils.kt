package com.apstudio.sentieri

import android.content.Context
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager

object GeoPackageUtils {
    private var manager: GeoPackageManager? = null

    fun getManager(context: Context): GeoPackageManager {
        if (manager == null) {
            manager = GeoPackageFactory.getManager(context.applicationContext)
        }
        return manager!!
    }
}
