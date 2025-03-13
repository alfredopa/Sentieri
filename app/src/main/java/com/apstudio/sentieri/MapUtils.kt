package com.apstudio.sentieri

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.icu.text.DecimalFormat
import android.location.Location
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat.getColor
import com.apstudio.sentieri.db.LayerItem
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.OfflineTileProvider
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
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.text.format

object MapUtils {

    fun setMapOfflineSource(activity: Activity?, map: MapView?) {
        // con permesso di accesso a tutti i  file, mantengo la cartella /storage/emulated/0/Sentieri/Mappe/
        // come base per le mappe offline
        val f = File(Environment.getExternalStorageDirectory().absolutePath + "/Sentieri/Mappe")
        // val f = activity?.getExternalFilesDir(null)

        if (f.exists()) {
            val list = f.listFiles()
            if (list != null) {
                for (aList in list) {
                    if (aList.isDirectory) {
                        continue
                    }
                    var name = aList.name.lowercase(Locale.getDefault())
                    if (aList.name.contains(".map")) {
                        val maps: Array<File?> = arrayOfNulls(1)
                        //val f = aList
                        //if (f!!.exists()) {
                        maps[0] = aList
                        //}
                        val fromFiles = MapsForgeTileSource.createFromFiles(maps)

                        val forge = MapsForgeTileProvider(
                            SimpleRegisterReceiver(activity),
                            fromFiles, null
                        )
                        map!!.tileProvider = forge
                        return
                    }

                    if (!name.contains(".")) {
                        continue
                    }
                    name = name.substring(name.lastIndexOf(".") + 1)
                    if (name.isEmpty()) {
                        continue
                    }
                    if (ArchiveFileFactory.isFileExtensionRegistered(name)) {
                        try {
                            val tileProvider = OfflineTileProvider(
                                SimpleRegisterReceiver(
                                    activity
                                ), arrayOf(aList)
                            )
                            map!!.tileProvider = tileProvider
                            val archives = tileProvider.archives
                            // importante setIgnoreTileSource consente apertura rapida della mappa evitando il controllo del tipo di sorgente presente nel file tiles
                            archives[0].setIgnoreTileSource(true)
                            map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
                            return
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
        } else {
            val context: Context = activity!!.application
            val toast = Toast.makeText(context, "File mappa non presente!", Toast.LENGTH_LONG)
            toast.show()
            toast.view?.setBackgroundColor(getColor(context, R.color.purple_500))
        }
    }

    fun disegnaLine(line: Polyline): Polyline {
        // min / max values used in the example
        // scalar meaning is "speed" in this example with no unit
        val MIN_SCALAR = 0
        val MAX_SCALAR = 50
        // hue range da rosso  for "alto" a blu per "basso"
        val MIN_HUE = 255 // green
        val MAX_HUE = 0 // red
        val SAT = 1.0f
        val LUM = 0.5f
        var mMapping: ColorMappingVariationHue? = null
        val mContainer: ColorMappingForScalarContainer?
        val paintBorder = Paint()
        val paintMapping = Paint()
        // create border paint
        paintBorder.color = Color.BLACK
        paintBorder.isAntiAlias = true
        paintBorder.strokeWidth = 10f
        paintBorder.style = Paint.Style.STROKE
        paintBorder.strokeJoin = Paint.Join.ROUND
        paintBorder.strokeCap = Paint.Cap.ROUND
        paintBorder.isAntiAlias = true
        // create mapping paint
        paintMapping.color = Color.MAGENTA
        paintMapping.isAntiAlias = true
        paintMapping.strokeWidth = 7f
        paintMapping.style = Paint.Style.FILL_AND_STROKE
        paintMapping.strokeJoin = Paint.Join.ROUND
        paintMapping.strokeCap = Paint.Cap.ROUND
        paintMapping.isAntiAlias = true

        // cerca valore min e max di Elevazione nell'array di oggetti Waypoint
        val minVal =
            line.actualPoints.minWithOrNull(Comparator.comparing { it.altitude.toFloat() })?.altitude
        val maxVal =
            (line.actualPoints.maxWithOrNull(Comparator.comparing { it.altitude.toFloat() }))?.altitude

        if (maxVal != null) {
            mMapping = ColorMappingVariationHue(
                minVal!!.toFloat(),
                maxVal.toFloat(),
                MIN_HUE.toFloat(),
                MAX_HUE.toFloat(),
                SAT,
                LUM
            )
        }

        mContainer = ColorMappingForScalarContainer(mMapping)
        line.actualPoints.forEach {
            mContainer.add(it.altitude.toFloat())
        }

        if (line.actualPoints.size != 0) {
            line.color = Color.CYAN
            // setup border
            line.outlinePaintLists.add(MonochromaticPaintList(paintBorder))
            // Colore del percorso con gradiente
            line.outlinePaintLists.add(PolychromaticPaintList(paintMapping, mMapping, true))

            // gestione del milestone
            val arrowPaint = Paint()
            arrowPaint.color = Color.argb(180, 230, 18, 18)
            arrowPaint.strokeWidth = 10.0f
            arrowPaint.style = Paint.Style.STROKE
            arrowPaint.isAntiAlias = true
            val arrowPath = Path() // a simple arrow towards the right
            /*arrowPath.moveTo(-10f, -10f)
            arrowPath.lineTo(10f, 0f)
            arrowPath.lineTo(-10f, 10f)*/
            arrowPath.moveTo(-10f, -10f)
            arrowPath.lineTo(10f, 0f)
            arrowPath.lineTo(-10f, 10f)
            arrowPath.close()
            val managers: MutableList<MilestoneManager> = ArrayList()
            managers.add(
                MilestoneManager(
                    MilestonePixelDistanceLister(50.0, 50.0),
                    MilestonePathDisplayer(0.0, true, arrowPath, arrowPaint)
                )
            )
            line.setMilestoneManagers(managers)

            // gestione del milestone
            //--- SE E' ABILITATA LA VISUALIZZAZIONE DELLE OUTLINE IL MILESTONE VIENE INIBITO.
            /*val path = Path()
            path.moveTo(-10f, -10f)
            path.lineTo(10f, 10f)
            path.lineTo(-10f, 10f)
            path.close()
            val managers: MutableList<MilestoneManager> = ArrayList()
            managers.add(MilestoneManager(MilestoneMeterDistanceLister(50.0), MilestonePathDisplayer(0.0, true, path, getFillPaint(Color.MAGENTA))))
            line.setMilestoneManagers(managers)*/

            // Colore del percorso con Linea Blu
            //line.color = Color.rgb(157, 69, 235)

            // aggiunge marker inizio e fine percorso
            /*val startMarker = Marker(mMapView)
            startMarker.icon = contesto?.let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_start
                )
            }
            startMarker.title = "Inizio"
            punto = line.actualPoints[0]
            startMarker.position = punto
            mMapView?.overlays?.add(startMarker)
            val endMarker = Marker(mMapView)
            endMarker.icon = contesto?.let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_finish
                )
            }
            punto = line.actualPoints[line.actualPoints.size - 1]
            endMarker.position = punto
            endMarker.title = "Fine"
            mMapView?.overlays?.add(endMarker)*/
        }
        return line
    }

    fun markInizioFine(
        contesto: Context,
        punto: GeoPoint,
        mappa: MapView,
        overTraccia: FolderOverlay,
        tipo: Int
    ) {
        // aggiunge marker inizio oppure fine percorso in base al valore tipo 0 = inizio, 1 = fine
        val marker = Marker(mappa)
        if (tipo == 0) {
            marker.icon = contesto.let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_start
                )
            }
        } else {
            marker.icon = contesto.let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_finish
                )
            }
        }

        marker.title = "Inizio"
        marker.position = punto
        overTraccia.add(marker)
    }

    fun alertSegui(context: Context, viewModel: SentieriViewModel, line: Polyline) {
        val allarme = EditText(context)
        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom)
        with(builder)
        {
            setTitle("Importa traccia")
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            val distanza = String.format("%,d", viewModel.trackDistanza.toInt())
            val ascesa = String.format("%,d", viewModel.trackAscesa)
            val discesa = String.format("%,d", viewModel.trackDiscesa)
            allarme.setText("\nDistanza: $distanza\nAscesa: $ascesa\nDiscesa: $discesa\n\nSeguire la traccia selezionata?")
            allarme.setPadding(20, 10, 20, 30) // Aggiungi padding per una migliore leggibilità
            layout.addView(allarme)
// Set the LinearLayout as the view for the dialog
            builder.setView(layout)

            setPositiveButton(
                "Segui"
            ) { _, _ ->
                if (viewModel.tracciaDaSeguire != "") {
                    alertVerificaSegui(context) { segui ->
                        if (segui) {
                            // resetta tracce con flag segui true
                            viewModel.layerItems.forEach {
                                it.segui = false
                            }
                            // aggiunge traccia con flag segui true alla lista layerItems
                            viewModel.layerItems.add(
                                LayerItem(
                                    line.title,
                                    line.isEnabled,
                                    false,
                                    true,
                                    viewModel.trackDistanza,
                                    viewModel.trackAscesa,
                                    viewModel.trackDiscesa
                                )
                            )
                            // L'utente ha premuto "Segui"
                            // Esegui le azioni per seguire la traccia
                        } else {
                            // aggiunge traccia con flag segui false alla lista layerItems
                            viewModel.layerItems.add(
                                LayerItem(
                                    line.title,
                                    line.isEnabled,
                                    false,
                                    false,
                                    viewModel.trackDistanza,
                                    viewModel.trackAscesa,
                                    viewModel.trackDiscesa
                                )
                            )
                            // L'utente ha premuto "Annulla"
                            // Esegui le azioni per annullare l'operazione
                        }
                    }
                } else
                    viewModel.layerItems.add(
                        LayerItem(
                            line.title, line.isEnabled, false, true,
                            viewModel.trackDistanza, viewModel.trackAscesa, viewModel.trackDiscesa
                        )
                    )
                viewModel.tracciaDaSeguire = line.title
                viewModel.alertFuoriTraccia = true
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                // aggiunge traccia con flag segui false alla lista layerItems
                viewModel.layerItems.add(
                    LayerItem(
                        line.title, line.isEnabled, false, false,
                        viewModel.trackDistanza, viewModel.trackAscesa, viewModel.trackDiscesa
                    )
                )
            }
//create()
            show()
        }
    }

    fun alertVerificaSegui(context: Context, callback: (Boolean) -> Unit) {
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        with(builder)
        {
            setTitle("Segui traccia")
            setMessage("E' già stata selezionata una traccia da seguire. Vuoi sostituirla con questa?")
            setPositiveButton(
                "Segui"
            ) { _, _ ->
                callback(true)
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                callback(false)
            }
            create()
            show()
        }

    }

    fun getDistanceInMeters(p1: GeoPoint, p2: GeoPoint): Int {
        val output = FloatArray(1)
        Location.distanceBetween(
            p1.latitude,
            p1.longitude,
            p2.latitude,
            p2.longitude,
            output
        )
        return output[0].roundToInt()
    }

    /*fun distance(geoPoint1: GeoPoint, geoPoint2: GeoPoint): Int {
    val r = 6371e3 // Raggio medio della Terra in metri
    val lat1 = Math.toRadians(geoPoint1.latitude)
    val lon1 = Math.toRadians(geoPoint1.longitude)
    val lat2 = Math.toRadians(geoPoint2.latitude)
    val lon2 = Math.toRadians(geoPoint2.longitude)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return (r * c).roundToInt()
    }*/

    // funzioni per altitudine barometrica
// restituisce il valore della pressione livello mare a partire da quota conosciuta
    fun getSealevelPressure(alt: Float, p: Float): Float {
// P0 = P * (1 - 0,0065 * h)^(-5,2556)
//return p * ((1 -0.0065 *(alt/1000)).pow(-5.2556).toFloat())
        return (p / (1 - alt / 44330.0f).toDouble().pow(5.255)).toFloat()
    }

    fun calcolaAltitudine(pressioneAttuale: Float, pressioneRiferimento: Float): Float {
// metodo con gradiente barometrico
        val gradienteBarometrico = 0.125f
        return (pressioneRiferimento - pressioneAttuale) / gradienteBarometrico
    }

    fun calcolaAltitudineIpso(pressioneAttuale: Float, pressioneRiferimento: Float): Float {
// metodo con formula ipsometrica
        val BAROMETRIC_CONSTANT = 44330.0F
        val EXPONENTIAL_COEFFICIENT = 1 / 5.256F
        return (BAROMETRIC_CONSTANT * (1 - (pressioneAttuale / pressioneRiferimento).pow(
            EXPONENTIAL_COEFFICIENT
        )))
    }

    fun formatDecimal(value: Float): String {
        val decimalFormat = DecimalFormat("#.##")
        return decimalFormat.format(value)
    }

    fun dataOraIso8601(): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
        return formatter.format(now)
    }

    fun formatMillisToHHmmss(millis: Long): String {
        val duration = Duration.ofMillis(millis)
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()

        return LocalTime.of(hours.toInt(), minutes, seconds)
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }

    // Da "HH:mm:ss" a secondi
    fun timeStringToSeconds(timeString: String): Int {
        val time = LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm:ss"))
        return time.toSecondOfDay()
    }

    // Da secondi a "HH:mm:ss"
    fun secondsToTimeString(seconds: Int): String {
        val time = LocalTime.ofSecondOfDay(seconds.toLong())
        return time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }

    fun extractFileName(input: String): String {
        if (input.isEmpty()) {
            return "" // Restituisci una stringa vuota se l'input è nullo o vuoto
        }
        val startIndex = input.lastIndexOf('/') + 1 // Trova l'ultimo '/'
        val endIndex = input.lastIndexOf('.')

        return if (startIndex > -1 && endIndex > startIndex) {
            input.substring(startIndex, endIndex)
        } else {
            input.substring(startIndex) // Restituisci l'intera stringa se non viene trovato '.'
        }
        /*val startIndex = input.indexOf('/') + 1 // Trova l'indice del carattere '/' e aggiungi 1 per iniziare dopo di esso
        val endIndex = input.lastIndexOf('.') // Trova l'indice dell'ultimo '.'
        return if (startIndex in 0..<endIndex) {
        input.substring(startIndex, endIndex)
        } else {
        "" // Restituisci una stringa vuota se non viene trovato '/' o '.'
        }*/
    }

    // restituisce il nome del file dall'URI
// questo metodo si applica per gli URI con schema content
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.path!!.lastIndexOf('/').plus(1).let { uri.path!!.substring(it) }
    }

    // (algoritmo douglasPeuckerReduction da osmdroid.util) per la riduzione del numero di punti
    fun douglasPeucker(points: ArrayList<GeoPoint>, epsilon: Double): ArrayList<GeoPoint> {
        if (points.size < 3) return points

        // Trova il punto con la massima distanza dalla linea
        var dmax = 0.0
        var index = 0
        val end = points.size - 1
        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        // Se la massima distanza è maggiore di epsilon, ricorsivamente semplifica
        if (dmax > epsilon) {
            val recResults1 = douglasPeucker(ArrayList(points.subList(0, index + 1)), epsilon)
            val recResults2 = douglasPeucker(ArrayList(points.subList(index, end + 1)), epsilon)

            // Costruisci la lista dei risultati
            val result = ArrayList<GeoPoint>(recResults1.subList(0, recResults1.size - 1))
            result.addAll(recResults2)
            return result
        } else {
            // Restituisci solo il primo e l'ultimo punto
            return arrayListOf(points[0], points[end])
        }
    }

    // Calcola la distanza perpendicolare da un punto a una linea
    private fun perpendicularDistance(
        pt: GeoPoint,
        lineStart: GeoPoint,
        lineEnd: GeoPoint
    ): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude

        val mag = sqrt(dx * dx + dy * dy)
        if (mag > 0.0) {
            val u =
                ((pt.longitude - lineStart.longitude) * dx + (pt.latitude - lineStart.latitude) * dy) / (mag * mag)

            if (u <= 0.0) return distance(pt, lineStart)
            if (u >= 1.0)
                return distance(pt, lineEnd)

            val intersection = GeoPoint(
                lineStart.latitude + u * dy,
                lineStart.longitude + u * dx
            )
            return distance(pt, intersection)
        }
        return 0.0
    }

    // Calcola la distanza tra due punti
    private fun distance(pt1: GeoPoint, pt2: GeoPoint): Double {
        val lat1 = Math.toRadians(pt1.latitude)
        val lon1 = Math.toRadians(pt1.longitude)
        val lat2 = Math.toRadians(pt2.latitude)
        val lon2 = Math.toRadians(pt2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val r = 6371e3 // Raggio medio della Terra in metri
        return r * c
    }

    fun formatSeconds(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        // Use String.format to add leading zeros
        return String.format(Locale.ITALY,"%02d:%02d:%02d", hours, minutes, seconds)
    }

// da BikeRoutes...
    /*fun getFormattedRideTime(rideTimeMinutes: Int): String {
    val rideTimeRemainedMinutes = rideTimeMinutes % 60
    val rideTimeHours = (rideTimeMinutes - rideTimeRemainedMinutes) / 60
    return if (rideTimeHours > 0)
    String.format("%d h %d min", rideTimeHours, rideTimeRemainedMinutes)
    else {
    if (rideTimeMinutes > 0)
        String.format("%d min", rideTimeRemainedMinutes)
    else
        String.format("1 min", rideTimeRemainedMinutes)

    }
    }*/


    /*fun NodeList.toList(): List<Node> {
        return (0 until this.length).map { this.item(it) }
    }

    class GpxAnalyzer {
        private val calculator = GeodeticCalculator()
        private val factory = DocumentBuilderFactory.newInstance()

        fun calculateMovingTime(gpxFile: File): Long {
            // Load the GPX file
            val document = factory.newDocumentBuilder().parse(gpxFile)

            // Extract the track points
            val trackPoints = document.getElementsByTagName("trkpt")

            // Filter the track points
            val trackPointsList = trackPoints.toList()
            val filteredPoints = trackPointsList.filter {
                it.attributes.getNamedItem("lat")?.textContent?.isNotEmpty() == true &&
                        it.attributes.getNamedItem("lon")?.textContent?.isNotEmpty() == true
            }

            // Calculate the moving time
            var movingTime = 0L
            var previousPoint: GlobalCoordinates? = null
            var previousTime: String = ""
            for (point in filteredPoints) {
                val lat = point.attributes.getNamedItem("lat").textContent.toDouble()
                val lon = point.attributes.getNamedItem("lon").textContent.toDouble()
                val time = point.getElementsByTagName("time").item(0).textContent

                // Convert the coordinates to GlobalCoordinates objects
                val currentPoint = GlobalCoordinates(lat, lon)

                // Calculate the distance between the current point and the previous point
                if (previousPoint != null) {
                    val distance = calculator.calculateGeodeticCurve(Ellipsoid.WGS84, previousPoint, currentPoint).ellipsoidalDistance
                    val timeDiff = Instant.parse(time).toEpochMilli() - Instant.parse(previousTime).toEpochMilli()

                    // If the distance is greater than 0 and the time difference is less than 12 hours, add the time difference to the moving time
                    if (distance > 0 && timeDiff < 12 * 60 * 60 * 1000) {
                        movingTime += timeDiff
                    }
                }

                // Update the previous point
                previousPoint = currentPoint
                previousTime = time
            }

            return movingTime
        }
    }*/
}


