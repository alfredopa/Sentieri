package com.apstudio.sentieri

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import mil.nga.geopackage.tiles.features.FeatureTiles
import org.osmdroid.util.MapTileIndex

/**
 * Modulo personalizzato per osmdroid che carica le mattonelle (tiles) 
 * generate al volo dai dati vettoriali di un GeoPackage.
 */
class GeoPackageFeatureTileModule(
    private val featureTiles: FeatureTiles
) : MapTileModuleProviderBase(1, 1) { // 1 thread è solitamente sufficiente per database locali

    override fun getName(): String = "GeoPackageFeatureTileModule"

    override fun getThreadGroupName(): String = "GeoPackageFeatureTileModule"

    override fun getTileLoader(): TileLoader = object : TileLoader() {
        /**
         * Carica la tile richiesta. In osmdroid 6.x le tile sono identificate da un Long (index).
         */
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            // Estrae coordinate e zoom dall'indice della mattonella
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val zoom = MapTileIndex.getZoom(pMapTileIndex)

            // Chiede a GeoPackage di renderizzare il bitmap per questa specifica tile.
            // Questa operazione è molto veloce grazie all'indice spaziale del database.
            val bitmap = try {
                featureTiles.drawTile(x, y, zoom)
            } catch (_: Exception) {
                null
            } ?: return null

            return BitmapDrawable(null, bitmap)
        }
    }

    override fun getMinimumZoomLevel(): Int = 0
    override fun getMaximumZoomLevel(): Int = 22

    override fun setTileSource(pTileSource: ITileSource?) {
        // Non utilizzato in questo modulo che attinge direttamente dal GeoPackage
    }

    override fun getUsesDataConnection(): Boolean = false

    fun close() {
        try {
            featureTiles.close()
        } catch (_: Exception) {
        }
    }
}
