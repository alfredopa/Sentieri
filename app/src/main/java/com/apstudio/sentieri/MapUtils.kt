package com.apstudio.sentieri

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.icu.text.DecimalFormat
import android.location.Location
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.db.LayerItem
import com.google.android.material.snackbar.Snackbar
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.advancedpolyline.ColorMappingForScalarContainer
import org.osmdroid.views.overlay.advancedpolyline.ColorMappingVariationHue
import org.osmdroid.views.overlay.advancedpolyline.MonochromaticPaintList
import org.osmdroid.views.overlay.advancedpolyline.PolychromaticPaintList
import org.osmdroid.views.overlay.milestones.MilestoneManager
import org.osmdroid.views.overlay.milestones.MilestonePathDisplayer
import org.osmdroid.views.overlay.milestones.MilestonePixelDistanceLister
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MapUtils {

    /**
     * Disegna una polilinea con gradiente di colore.
     * @param line La Polyline da colorare.
     * @param pendenze Lista opzionale di pendenze. Se null, usa l'altitudine dei punti.
     */
    fun disegnaPercorsoColorato(line: Polyline, pendenze: List<Float>? = null) {
        val values = pendenze ?: line.actualPoints.map { it.altitude.toFloat() }
        if (values.isEmpty()) return

        val minVal: Float
        val maxVal: Float
        val minHue: Float
        val maxHue: Float

        if (pendenze == null) {
            // Gradiente per Altitudine: Verde (basso) -> Rosso (alto)
            minVal = values.minOrNull() ?: 0f
            maxVal = values.maxOrNull() ?: 0f
            minHue = 255f // Verde
            maxHue = 0f   // Rosso
        } else {
            // Gradiente per Pendenza: Blu (-20%) -> Rosso (+20%)
            minVal = -20f
            maxVal = 20f
            minHue = 240f // Blu
            maxHue = 0f   // Rosso
        }

        val mMapping = ColorMappingVariationHue(minVal, maxVal, minHue, maxHue, 1.0f, 0.5f)
        val mContainer = ColorMappingForScalarContainer(mMapping)
        values.forEach { mContainer.add(it) }

        val paint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        // Pulisce eventuali colorazioni precedenti per evitare accumuli
        line.outlinePaintLists.clear()
        line.outlinePaintLists.add(PolychromaticPaintList(paint, mMapping, true))
    }

    fun applicaFrecceDirezione(line: Polyline) {
        line.setMilestoneManagers(mutableListOf())
        val arrowPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 5.0f
            style = Paint.Style.FILL_AND_STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val arrowPath = Path().apply {
            moveTo(10f, 0f)
            lineTo(-10f, -8f)
            lineTo(-10f, 8f)
            close()
        }

        val managers = mutableListOf<MilestoneManager>()
        managers.add(MilestoneManager(MilestonePixelDistanceLister(100.0, 100.0), MilestonePathDisplayer(0.0, true, arrowPath, arrowPaint)))
        line.setMilestoneManagers(managers)
    }

    fun markInizioFine(contesto: Context?, punto: GeoPoint, mappa: MapView, overTraccia: FolderOverlay, tipo: Int) {
        val marker = Marker(mappa)
        if (tipo == 0) {
            marker.icon = AppCompatResources.getDrawable(contesto!!, R.drawable.ic_start)
            marker.title = "Inizio"
            marker.id = "Inizio"
        } else {
            marker.icon = AppCompatResources.getDrawable(contesto!!, R.drawable.ic_finish)
            marker.title = "Fine"
            marker.id = "Fine"
        }
        marker.position = punto
        overTraccia.add(marker)
        mappa.invalidate() 
    }

    fun apreMappa(context: Context, mapView: MapView, viewModel: SentieriViewModel, uri: Uri): Boolean {
        if (uri == Uri.EMPTY) return false
        
        val uriPathHelper = URIPathHelper()
        val filePath = uriPathHelper.getPath(context, uri) ?: return false
        val maps: Array<File?> = arrayOfNulls(1)
        val f = File(filePath)
        if (f.exists()) {
            maps[0] = f
        } else {
            Toast.makeText(context, "Il file selezionato non esiste", Toast.LENGTH_LONG).show()
            return false
        }

        val extension = f.extension
        if (!ArchiveFileFactory.isFileExtensionRegistered(extension) && extension != "map") {
            Toast.makeText(context, "Il file selezionato non contiene dati mappa", Toast.LENGTH_LONG).show()
            return false
        }

        if (f.name.contains(".map")) {
            val preferenze = PreferenceManager.getDefaultSharedPreferences(context)
            val savedThemePath = preferenze.getString("seleziona_tema_mapsforge", "OSMARENDER")
            var theme: XmlRenderTheme = InternalRenderTheme.OSMARENDER

            if (savedThemePath != null && savedThemePath != "OSMARENDER") {
                val themeFile = File(savedThemePath)
                if (themeFile.exists()) {
                    try {
                        theme = ExternalRenderTheme(themeFile)
                    } catch (e: Exception) {
                        Log.e("MapUtils", "Errore nel caricamento del tema esterno", e)
                    }
                }
            }
            val fromFiles = MapsForgeTileSource.createFromFiles(maps, theme, "ThemeName")
            mapView.tileProvider = MapsForgeTileProvider(SimpleRegisterReceiver(context), fromFiles, null)
        } else {
            val offlineMappa = OfflineTileProvider(SimpleRegisterReceiver(context), maps)
            mapView.tileProvider = offlineMappa
            offlineMappa.archives.firstOrNull()?.setIgnoreTileSource(true)
            val archive = offlineMappa.archives.firstOrNull()
            if (archive != null) {
                mapView.controller.setCenter(viewModel.ultPosizione)
                mapView.controller.setZoom(viewModel.ultZoom)
                //mapView.controller.setZoom(12.0)
            }
        }
        
        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        viewModel.connessione = false
        mapView.setUseDataConnection(false)
        viewModel.menuMap = 0
        viewModel.uriMappa = uri
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putInt("MenuMap", 0)
            putString("URIMappa", uri.toString())
        }
        mapView.controller.setCenter(viewModel.ultPosizione)
        mapView.invalidate()
        return true
    }

    fun online(context: Context, mapView: MapView, viewModel: SentieriViewModel, mappa: Int) {
        val tileProvider: MapTileProviderBasic = when (mappa) {
            1 -> MapTileProviderBasic(context, TileSourceFactory.MAPNIK)
            2 -> MapTileProviderBasic(context, TileSourceFactory.OpenTopo)
            3 -> mappaMapBox(context)
            else -> MapTileProviderBasic(context, TileSourceFactory.MAPNIK)
        }
        viewModel.connessione = true
        viewModel.menuMap = mappa
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putInt("MenuMap", mappa)
        }
        mapView.setUseDataConnection(true)
        mapView.tileProvider = tileProvider
        mapView.setTileSource(tileProvider.tileSource)
        mapView.invalidate()
    }

    private fun mappaMapBox(context: Context): MapTileProviderBasic {
        val source = MapBoxTileSource("MapBox", 1, 19, 256, ".png")
        source.retrieveAccessToken(context)
        source.setMapboxMapid("mapbox.satellite")
        source.accessToken = "pk.eyJ1IjoiYWxmcmVkb3BhIiwiYSI6ImNtMDBzMmQ3ODBoMWIya3NuejJ5NnNzMG0ifQ.kXnCG27oE6go9msYdp3pkA"
        TileSourceFactory.addTileSource(source)
        return MapTileProviderBasic(context, source)
    }

    fun alertSegui(context: Context, viewModel: SentieriViewModel, line: Polyline) {
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        with(builder) {
            setTitle("Importa traccia")
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_track_details, null)
            dialogView.findViewById<TextView>(R.id.tv_distanza).text = String.format(Locale.getDefault(), "%,d m", viewModel.trackDistanza.toInt())
            dialogView.findViewById<TextView>(R.id.tv_ascesa).text = String.format(Locale.getDefault(), "%,d m", viewModel.trackAscesa)
            dialogView.findViewById<TextView>(R.id.tv_discesa).text = String.format(Locale.getDefault(), "%,d m", viewModel.trackDiscesa)

            setView(dialogView)
            setPositiveButton("Segui") { _, _ ->
                if (viewModel.tracciaDaSeguire != "") {
                    alertVerificaSegui(context) { segui ->
                        if (segui) {
                            viewModel.layerItems.forEach { it.segui = false }
                            viewModel.layerItems.add(LayerItem(line.title, line.isEnabled,
                                direzione = false,
                                segui = true,
                                distanza = viewModel.trackDistanza,
                                ascesa = viewModel.trackAscesa,
                                discesa = viewModel.trackDiscesa
                            ))
                        } else {
                            viewModel.layerItems.add(LayerItem(line.title, line.isEnabled,
                                direzione = false,
                                segui = false,
                                distanza = viewModel.trackDistanza,
                                ascesa = viewModel.trackAscesa,
                                discesa = viewModel.trackDiscesa
                            ))
                        }
                    }
                } else {
                    viewModel.layerItems.add(LayerItem(line.title, line.isEnabled,
                        direzione = false,
                        segui = true,
                        distanza = viewModel.trackDistanza,
                        ascesa = viewModel.trackAscesa,
                        discesa = viewModel.trackDiscesa
                    ))
                }
                viewModel.tracciaDaSeguire = line.title
                viewModel.alertFuoriTraccia = true
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.layerItems.add(LayerItem(line.title, line.isEnabled,
                    direzione = false,
                    segui = false,
                    distanza = viewModel.trackDistanza,
                    ascesa = viewModel.trackAscesa,
                    discesa = viewModel.trackDiscesa
                ))
            }
            show()
        }
    }

    fun alertVerificaSegui(context: Context, callback: (Boolean) -> Unit) {
        AlertDialog.Builder(context, R.style.AlertDialogCustom).apply {
            setTitle("Segui traccia")
            setMessage("E' già stata selezionata una traccia da seguire. Vuoi sostituirla con questa?")
            setPositiveButton("Segui") { _, _ -> callback(true) }
            setNegativeButton(android.R.string.cancel) { _, _ -> callback(false) }
            show()
        }
    }

    fun getDistanceInMeters(p1: GeoPoint, p2: GeoPoint): Int {
        val output = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, output)
        return output[0].roundToInt()
    }

    fun getSealevelPressure(alt: Float, p: Float): Float = (p / (1 - alt / 44330.0f).toDouble().pow(5.255)).toFloat()

    fun calcolaAltitudineIpso(pressioneAttuale: Float, pressioneRiferimento: Float): Float {
        return (44330.0F * (1 - (pressioneAttuale / pressioneRiferimento).pow(1 / 5.256F)))
    }

    fun formatDecimal(value: Float): String = DecimalFormat("#.##").format(value)

    fun dataOraIso8601(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(LocalDateTime.now())

    fun convertMillisToISO8601JavaTime(timestampMillis: Long): String {
        val instant = Instant.ofEpochMilli(timestampMillis)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").format(localDateTime)
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.path?.substringAfterLast('/') ?: ""
    }

    fun douglasPeucker(points: ArrayList<GeoPoint>, epsilon: Double): ArrayList<GeoPoint> {
        if (points.size < 3) return points
        var dmax = 0.0
        var index = 0
        val end = points.size - 1
        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) { index = i; dmax = d }
        }
        return if (dmax > epsilon) {
            val res1 = douglasPeucker(ArrayList(points.subList(0, index + 1)), epsilon)
            val res2 = douglasPeucker(ArrayList(points.subList(index, end + 1)), epsilon)
            ArrayList<GeoPoint>(res1.subList(0, res1.size - 1)).apply { addAll(res2) }
        } else {
            arrayListOf(points[0], points[end])
        }
    }

    private fun perpendicularDistance(pt: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        val mag = sqrt(dx * dx + dy * dy)
        if (mag > 0.0) {
            val u = ((pt.longitude - start.longitude) * dx + (pt.latitude - start.latitude) * dy) / (mag * mag)
            return when {
                u <= 0.0 -> distance(pt, start)
                u >= 1.0 -> distance(pt, end)
                else -> distance(pt, GeoPoint(start.latitude + u * dy, start.longitude + u * dx))
            }
        }
        return 0.0
    }

    private fun distance(pt1: GeoPoint, pt2: GeoPoint): Double {
        val lat1 = Math.toRadians(pt1.latitude); val lon1 = Math.toRadians(pt1.longitude)
        val lat2 = Math.toRadians(pt2.latitude); val lon2 = Math.toRadians(pt2.longitude)
        val dLat = lat2 - lat1; val dLon = lon2 - lon1
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6371e3 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatSeconds(totalSeconds: Long): String = String.format(Locale.ITALY, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)

    fun formatElapsedTime(elapsedTime: Long): String = formatSeconds(elapsedTime / 1000)

    fun prnDataFromUtc(dataOra: String): String {
        return if (dataOra == "") "" else {
            val odt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
            val dateTime = LocalDateTime.parse(dataOra, odt)
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").format(dateTime)
        }
    }

    fun formattastring(distanza: Int): String {
        return if (distanza < 1_000) String.format(Locale.getDefault(), "%d m", distanza)
        else String.format(Locale.getDefault(), "%.1f km", distanza / 1_000.0)
    }

    fun showCustomSnackbar(view: View, message: String) {
        val snackbar = Snackbar.make(view, "", Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view as ViewGroup
        snackbarView.apply {
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
            val params = layoutParams as ViewGroup.MarginLayoutParams
            val density = view.resources.displayMetrics.density
            params.setMargins((20 * density).toInt(), params.topMargin, (20 * density).toInt(), (12 * density).toInt())
            layoutParams = params
            addView(LayoutInflater.from(view.context).inflate(R.layout.custom_snackbar_layout, null).apply {
                findViewById<TextView>(R.id.snackbar_text).text = message
            }, 0)
        }
        snackbar.show()
    }

    fun disegnaLineaSfondo(line: Polyline) {
        line.outlinePaintLists.clear()
        line.setMilestoneManagers(ArrayList())
        line.outlinePaintLists.add(MonochromaticPaintList(Paint().apply {
            color = Color.BLACK; isAntiAlias = true; strokeWidth = 12f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }))
    }

    fun calcolaPendenzeSmussate(line: Polyline, finestra: Int = 10): MutableList<Float> {
        val punti = line.actualPoints
        if (punti.size < 2) return mutableListOf()
        val nette = mutableListOf<Float>().apply { add(0f) }
        for (i in 1 until punti.size) {
            val dist = punti[i-1].distanceToAsDouble(punti[i])
            nette.add(if (dist > 0) ((punti[i].altitude - punti[i-1].altitude) / dist * 100).toFloat() else 0f)
        }
        return nette.indices.map { i ->
            nette.subList(maxOf(0, i - finestra/2), minOf(nette.size, i + finestra/2 + 1)).average().toFloat()
        }.toMutableList()
    }

    fun getOutputStreamForPublicDownload(context: Context, fileName: String): OutputStream? {
        // Ottieni l'URI per la directory di download pubblica
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var fileUriToDelete: Uri? = null

        try {
            // 1. Cerca se un file con lo stesso nome esiste già
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID), // Richiedi solo l'ID
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", // Condizione WHERE
                arrayOf(fileName), // Valore per la condizione WHERE
                null // Ordinamento
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // Ottieni l'ID del file esistente
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    fileUriToDelete = ContentUris.withAppendedId(collection, id)
                }
            } ?: run {
                Log.e("MapUtils", "Errore durante la query iniziale per verificare l'esistenza del file '$fileName'.")
            }
        } catch (e: Exception) {
            Log.e("MapUtils", "Eccezione durante la query iniziale per il file '$fileName'.", e)
        }

        // 2. Se abbiamo trovato un URI per il file, tenta di eliminarlo
        if (fileUriToDelete != null) {
            try {
                val deleteResult = context.contentResolver.delete(fileUriToDelete!!, null, null)
                if (deleteResult > 0) {
                    Log.d("MapUtils", "File esistente '$fileName' (URI: $fileUriToDelete) eliminato con successo (record affected: $deleteResult). Procedo con la creazione del nuovo file.")
                } else {
                    Log.w("MapUtils", "Tentativo di eliminare il file esistente '$fileName' (URI: $fileUriToDelete) fallito (deleteResult = $deleteResult). Il file potrebbe non essere più presente o non eliminabile.")
                    // Se l'eliminazione fallisce, potremmo voler gestire questo come un errore critico per la sovrascrittura.
                    // Tuttavia, per ora, proviamo a procedere sperando che MediaStore gestisca la creazione di un nome univoco.
                    // Potremmo anche scegliere di interrompere qui: return null
                }
            } catch (e: Exception) {
                Log.e("MapUtils", "Eccezione durante l'eliminazione del file esistente '$fileName' (URI: $fileUriToDelete).", e)
                // In caso di eccezione durante l'eliminazione, è probabile che la sovrascrittura fallisca.
                // return null // Interrompi se l'eliminazione fallisce
            }
        } else {
            Log.d("MapUtils", "File '$fileName' non trovato nella directory di download. Nessuna eliminazione necessaria.")
        }

        // 3. Prepara i ContentValues per il nuovo file
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip") // Assicurati che il MIME type sia corretto
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        // 4. Inserisci un nuovo record per il file e ottieni l'OutputStream.
        // Il MediaStore gestirà la creazione di nomi univoci se l'inserimento fallisce per nome duplicato
        // e il delete precedente non ha funzionato.
        // Importante: La funzione ora ritorna l'OutputStream associato all'URI che il MediaStore ha effettivamente creato.
        return context.contentResolver.insert(collection, values)?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.also { outputStream ->
                Log.d("MapUtils", "OutputStream creato con successo per l'URI: $uri.")
                // Qui è importante notare che l'OutputStream è pronto.
                // Se il file è stato rinominato internamente dal MediaStore (es. a Sardegna (1).zip),
                // questo è il momento in cui dovremmo esserne a conoscenza.
                // Per semplicità, al momento, ci affidiamo al fatto che l'OutputStream sia valido.
            } ?: run {
                Log.e("MapUtils", "Impossibile aprire l'OutputStream per l'URI '$uri' del file.")
                null
            }
        } ?: run {
            Log.e("MapUtils", "Impossibile inserire un nuovo file con nome '$fileName' nel MediaStore.")
            null
        }
    }

    fun decomprimiZipInCartellaMappe(context: Context, nomeZip: String): Boolean { // <-- Modifica la firma qui
        Log.d("MapUtils", "Inizio decompressione zip: $nomeZip") // Aggiungi un log per capire se questa funzione viene chiamata

        return try {
            @Suppress("DEPRECATION")
            val zipFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nomeZip)

            if (!zipFile.exists()) {
                Log.e("MapUtils", "File zip da decomprimere non trovato: ${zipFile.absolutePath}")
                false // Restituisce false se il file zip non esiste
            } else {
                // Assicurati che la directory di destinazione esista
                val destDir = File(context.externalMediaDirs.first(), "Mappe")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                    Log.d("MapUtils", "Creata directory di destinazione: ${destDir.absolutePath}")
                } else {
                    Log.d("MapUtils", "Directory di destinazione già esistente: ${destDir.absolutePath}")
                }

                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        // Controllo di sicurezza per evitare attacchi Zip Slip
                        val entryPath = entry.name
                        val entryFile = File(destDir, entryPath)
                        val canonicalDestDir = destDir.canonicalPath
                        val canonicalEntryFile = entryFile.canonicalPath

                        if (!canonicalEntryFile.startsWith(canonicalDestDir)) {
                            Log.e("MapUtils", "Zip Slip attack detected! Entry path: $entryPath")
                            throw SecurityException("Zip Slip attack detected")
                        }

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                            Log.d("MapUtils", "Creata directory: ${entryFile.absolutePath}")
                        } else {
                            entryFile.parentFile?.mkdirs() // Crea le directory padre se necessario
                            BufferedOutputStream(FileOutputStream(entryFile)).use { bos ->
                                val buf = ByteArray(4096); var bytesRead: Int
                                while (zis.read(buf).also { bytesRead = it } != -1) {
                                    bos.write(buf, 0, bytesRead)
                                }
                            }
                            //Log.d("MapUtils", "Estratto file: ${entryFile.absolutePath}")
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                Log.d("MapUtils", "Decompressione completata con successo per: $nomeZip")
                true // Restituisce true in caso di successo
            }
        } catch (e: Exception) {
            Log.e("MapUtils", "Errore durante la decompressione di $nomeZip", e)
            false // Restituisce false in caso di qualsiasi eccezione
        }
    }
}
