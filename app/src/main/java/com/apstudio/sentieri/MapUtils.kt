package com.apstudio.sentieri

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.icu.text.DecimalFormat
import android.location.Location
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.apstudio.sentieri.db.LayerItem
import com.google.android.material.snackbar.Snackbar
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
import org.osmdroid.views.overlay.milestones.MilestoneMeterDistanceLister
import org.osmdroid.views.overlay.milestones.MilestonePathDisplayer
import org.osmdroid.views.overlay.milestones.MilestonePixelDistanceLister
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MapUtils {

        fun disegnaLine(line: Polyline): Polyline {
        // min / max values used in the example
        // scalar meaning is "speed" in this example with no unit
        //val MIN_SCALAR = 0
        //val MAX_SCALAR = 50
        // hue range da rosso  for "alto" a blu per "basso"
        val MIN_HUE = 255 // green
        val MAX_HUE = 0 // red
        val SAT = 1.0f
        val LUM = 0.5f
        var mMapping: ColorMappingVariationHue? = null
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

            val mContainer = ColorMappingForScalarContainer(mMapping)
        line.actualPoints.forEach {
            mContainer.add(it.altitude.toFloat())
        }

        if (line.actualPoints.isNotEmpty()) {
            // setup border
            line.outlinePaintLists.add(MonochromaticPaintList(paintBorder))
            // Colore del percorso con gradiente
            line.outlinePaintLists.add(PolychromaticPaintList(paintMapping, mMapping, true))

            // gestione del milestone
            val arrowPaint = Paint()
            arrowPaint.color = Color.argb(180, 230, 18, 18)
            arrowPaint.strokeWidth = 12.0f
            arrowPaint.style = Paint.Style.STROKE
            arrowPaint.isAntiAlias = true
            val arrowPath = Path() // a simple arrow towards the right
            /*arrowPath.moveTo(-10f, -10f)
            arrowPath.lineTo(10f, 0f)
            arrowPath.lineTo(-10f, 10f)*/

            // Arrow tip (rightmost point)
            arrowPath.moveTo(15f, 0f) // Tip of the arrow (adjust x to control length)

            // Right feather
            arrowPath.lineTo(5f, -5f) // Adjust for feather angle
            arrowPath.lineTo(5f, -2f) // Back side of the feather
            arrowPath.lineTo(-15f, -2f) // Back of arrow body (adjust x to control body length)

            // Left feather
            arrowPath.lineTo(-15f, 2f) // Back of arrow body
            arrowPath.lineTo(5f, 2f) // Back side of the feather
            arrowPath.lineTo(5f, 5f) // Adjust for feather angle
            arrowPath.close()
            val managers: MutableList<MilestoneManager> = ArrayList()
            managers.add(
                MilestoneManager(
                    MilestonePixelDistanceLister(35.0, 35.0),
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
        val folderMarker = overTraccia
        val marker = Marker(mappa)
        if (tipo == 0) {
            marker.icon = contesto.let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_start
                )
            }
            marker.title = "Inizio"
            marker.id =  "Inizio"
        } else {
            marker.icon = contesto.let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_finish
                )
            }
            marker.title = "Fine"
            marker.id =  "Fine"
        }
        marker.position = punto
        folderMarker.add(marker)
    }

    fun alertSegui(context: Context, viewModel: SentieriViewModel, line: Polyline) {
        val detailsTextView = TextView(context)
        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom)
        with(builder) {
            setTitle("Importa traccia")
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_track_details, null)
            // 2. Trova i TextView all'interno del layout gonfiato
            val distanzaTextView = dialogView.findViewById<TextView>(R.id.tv_distanza)
            val ascesaTextView = dialogView.findViewById<TextView>(R.id.tv_ascesa)
            val discesaTextView = dialogView.findViewById<TextView>(R.id.tv_discesa)
            // 3. Formatta i valori numerici (come facevi già)
            val distanza = String.format(Locale.getDefault(), "%,d m", viewModel.trackDistanza.toInt())
            val ascesa = String.format(Locale.getDefault(), "%,d m", viewModel.trackAscesa)
            val discesa = String.format(Locale.getDefault(), "%,d m", viewModel.trackDiscesa)
            // 4. Imposta i valori nei rispettivi TextView
            distanzaTextView.text = distanza
            ascesaTextView.text = ascesa
            discesaTextView.text = discesa

            // 5. Imposta il layout gonfiato come vista del dialogo
            setView(dialogView)
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
                                    direzione = false,
                                    segui = true,
                                    distanza = viewModel.trackDistanza,
                                    ascesa = viewModel.trackAscesa,
                                    discesa = viewModel.trackDiscesa
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
                                    direzione = false,
                                    segui = false,
                                    distanza = viewModel.trackDistanza,
                                    ascesa = viewModel.trackAscesa,
                                    discesa = viewModel.trackDiscesa
                                )
                            )
                            // L'utente ha premuto "Annulla"
                            // Esegui le azioni per annullare l'operazione
                        }
                    }
                } else
                    viewModel.layerItems.add(
                        LayerItem(
                            line.title, line.isEnabled,
                            direzione = false,
                            segui = true,
                            distanza = viewModel.trackDistanza,
                            ascesa = viewModel.trackAscesa,
                            discesa = viewModel.trackDiscesa
                        )
                    )
                viewModel.tracciaDaSeguire = line.title
                viewModel.alertFuoriTraccia = true
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                // aggiunge traccia con flag segui false alla lista layerItems
                viewModel.layerItems.add(
                    LayerItem(
                        line.title, line.isEnabled, direzione = false, segui = false,
                        distanza = viewModel.trackDistanza, ascesa = viewModel.trackAscesa, discesa = viewModel.trackDiscesa
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

/*    fun calcolaAltitudine(pressioneAttuale: Float, pressioneRiferimento: Float): Float {
// metodo con gradiente barometrico
        val gradienteBarometrico = 0.125f
        return (pressioneRiferimento - pressioneAttuale) / gradienteBarometrico
    }*/

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

    fun convertMillisToISO8601JavaTime(timestampMillis: Long): String {
        val instant = Instant.ofEpochMilli(timestampMillis)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'") // XXX per l'offset del fuso orario
        return localDateTime.format(formatter)
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

    fun formatElapsedTime(elapsedTime: Long): String {
        val totalSeconds = elapsedTime / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        // Use String.format to add leading zeros
        return String.format(Locale.ITALY, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun prnDataFromUtc(dataOra: String): String {
        return if (dataOra == "")
            ""
        else {
            // data ora UTC
            val odt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            val dateTime = LocalDateTime.parse(dataOra, odt)
            formatter.format(dateTime)
        }
    }

    fun formattastring(distanza: Int): String {
        // visualizza distanza in metri o km
        return if (distanza < 1_000)
            String.format(Locale.getDefault(), "%d m", distanza)
        else {
            NumberFormat.getNumberInstance(Locale.getDefault())
            val km = distanza / 1_000.0
            String.format(Locale.getDefault(), "%.1f km", km)
        }
    }

    fun showCustomSnackbar(view: View, message: String) {
        // 1. Crea lo Snackbar come al solito
        val snackbar = Snackbar.make(view, "", Snackbar.LENGTH_LONG)

        // 2. Prendi la view generica dello Snackbar. Non fare più il cast a SnackbarLayout.
        val snackbarView = snackbar.view

        // 3. Rimuovi il padding predefinito per avere controllo totale
        snackbarView.setPadding(0, 0, 0, 0)
        // Rendi trasparente lo sfondo predefinito dello Snackbar
        snackbarView.setBackgroundColor(Color.TRANSPARENT)

        // --- LA PARTE CHIAVE È QUI (MODIFICATA) ---
        // 4. Prendi i LayoutParams generici (ViewGroup.MarginLayoutParams) e imposta i margini.
        //    Questo funziona perché la view dello Snackbar si trova sempre dentro un contenitore
        //    che supporta i margini (solitamente un CoordinatorLayout o FrameLayout).
        val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
        val marginInDp = 20 // Scegli il margine che preferisci in dp
        val marginInPx = (marginInDp * view.resources.displayMetrics.density).toInt()

        // Imposta i margini orizzontali e un margine inferiore per staccarlo dal fondo
        val bottomMarginInDp = 12 // Aggiungi un margine inferiore se vuoi
        val bottomMarginInPx = (bottomMarginInDp * view.resources.displayMetrics.density).toInt()

        params.setMargins(marginInPx, params.topMargin, marginInPx, bottomMarginInPx)
        snackbarView.layoutParams = params
        // ----------------------------------------

        // 5. Infla il tuo layout personalizzato
        val inflater = LayoutInflater.from(view.context)
        val customView = inflater.inflate(R.layout.custom_snackbar_layout, null)

        // Imposta il testo del tuo layout
        val textView = customView.findViewById<TextView>(R.id.snackbar_text)
        textView.text = message

        // 6. Aggiungi la tua view personalizzata.
        //    Dato che non possiamo più usare addView su SnackbarLayout, cerchiamo un modo alternativo.
        //    Il modo più sicuro è rimuovere le view esistenti (il TextView di default)
        //    e aggiungere la nostra. Ma dato che abbiamo reso lo sfondo trasparente,
        //    possiamo provare a sovrapporla. Il metodo più semplice è usare addView
        //    se la snackbarView è un ViewGroup.
        if (snackbarView is ViewGroup) {
            snackbarView.addView(customView, 0)
        }

        // 7. Mostra lo Snackbar
        snackbar.show()
    }

    /**
     * Configura una Polyline per visualizzare un gradiente di colore basato sull'altitudine.
     * Questa sarà la linea di sfondo (Livello 1).
     * VERSIONE CORRETTA che popola il ColorMappingForScalarContainer.
     */
    fun disegnaLineaSfondo(line: Polyline, pendenze: MutableList<Float>) {
        // Range di colori: da Verde (basso) a Rosso (alto)
        val MIN_HUE = 120f // Verde
        val MAX_HUE = 0f   // Rosso
        val SAT = 1.0f
        val LUM = 0.5f

        val borderPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 14f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
        }

        // Configura il Paint per il gradiente
        val paintMapping = Paint().apply {
            strokeWidth = 12f // Spessore generoso per essere visibile sotto
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }


        // Trova pendenza min/max
        val minVal = pendenze.min()
        val maxVal = pendenze.max()
        if (maxVal > minVal) {
            // 1. Definisci come mappare i valori (pendenza) ai colori (hue)
            val colorMapping = ColorMappingVariationHue(
                minVal.toFloat(), maxVal.toFloat(), MIN_HUE, MAX_HUE, SAT, LUM
            )

            //    Crea il contenitore e POPOLALO con i valori di pendenza di ogni punto.
            val colorContainer = ColorMappingForScalarContainer(colorMapping)
            pendenze.forEach {
                // Aggiungi la pendenza di ogni punto al contenitore.
                // Se un punto non ha altitudine, usa 0.0 o un valore di fallback.
                colorContainer.add(it)
            }

            // ALTRO METODO CON ALTITUDINE
            // Trova altitudine min/max
            /*val minVal = line.actualPoints.minOfOrNull { it.altitude } ?: 0.0
            val maxVal = line.actualPoints.maxOfOrNull { it.altitude } ?: 0.0

            if (maxVal > minVal) {
                // 1. Definisci come mappare i valori (altitudine) ai colori (hue)
                val colorMapping = ColorMappingVariationHue(
                    minVal.toFloat(), maxVal.toFloat(), MIN_HUE, MAX_HUE, SAT, LUM
                )

                // 2. *** PASSAGGIO FONDAMENTALE MANCANTE ***
                //    Crea il contenitore e POPOLALO con i valori di altitudine di ogni punto.
                val colorContainer = ColorMappingForScalarContainer(colorMapping)
                line.actualPoints.forEach {
                    // Aggiungi l'altitudine di ogni punto al contenitore.
                    // Se un punto non ha altitudine, usa 0.0 o un valore di fallback.
                    colorContainer.add(it.altitude.toFloat())
                }*/

            // 3. Applica la lista di paint policromatici alla polyline.
            //    Questa lista userà il colorMapping che ora sa a quale punto associare ogni colore
            //    grazie al colorContainer popolato.
            line.outlinePaintLists.add(MonochromaticPaintList(borderPaint))
            line.outlinePaintLists.add(PolychromaticPaintList(paintMapping, colorMapping, true))


        } else {
            // Fallback a colore singolo se non c'è dislivello
            paintMapping.color = Color.CYAN
            line.outlinePaintLists.add(MonochromaticPaintList(paintMapping))
        }
    }

    /**
     * Configura una Polyline per visualizzare il primo piano: bordo, linea interna e frecce.
     * Questa sarà la linea sopra lo sfondo (Livello 2).
     * SPECIFICO PER OSMDROID 6.1.x
     */
    fun disegnaLineaPrimopiano(line: Polyline) {
        // Rendi la linea base trasparente, perché disegneremo tutto con outlinePaintLists e milestone
        line.paint.color = Color.TRANSPARENT

        // 1. Bordo nero per contrasto
        /*val borderPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 15f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
        }
        line.outlinePaintLists.add(MonochromaticPaintList(borderPaint))

        // 2. Linea interna bianca
        val innerPaint = Paint().apply {
            color = Color.TRANSPARENT
            strokeWidth = 9f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
        line.outlinePaintLists.add(MonochromaticPaintList(innerPaint))*/

        // 3. Frecce direzionali
        val arrowPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE // Riempi le frecce per visibilità
            isAntiAlias = true
        }
        val arrowPath = Path().apply {
            moveTo(10f, 0f)
            lineTo(-10f, -8f)
            lineTo(-10f, 8f)
            close()
        }
        val arrowManager = MilestoneManager(
            MilestonePixelDistanceLister(100.0, 100.0), // Aumenta distanza per non affollare
            MilestonePathDisplayer(0.0, true, arrowPath, arrowPaint)
        )

        // Applica le frecce.
        // **ATTENZIONE**: In osmdroid 6.1.x, i milestone vengono disegnati SOPRA le outlinePaintLists
        // della STESSA polyline, quindi questo approccio funzionerà.
        line.setMilestoneManagers(mutableListOf(arrowManager))
    }

// In MapUtils.kt

    /**
     * Calcola una lista di pendenze SMUSSATE (smoothed) per ogni punto di una Polyline,
     * usando una media mobile per ottenere valori più stabili e rappresentativi.
     *
     * @param line La Polyline contenente i GeoPoint con altitudine.
     * @param finestra La dimensione della finestra di punti da considerare (metà prima, metà dopo).
     *                 Un valore tipico è tra 10 e 20. Più è alto, più il risultato è "liscio".
     * @return Una MutableList<Float> con la pendenza smussata per ogni punto.
     */
    fun calcolaPendenzeSmussate(line: Polyline, finestra: Int = 10): MutableList<Float> {
        val punti = line.actualPoints
        if (punti.size < 2) return mutableListOf()

        val pendenzeNette = mutableListOf<Float>()
        // Calcola prima le pendenze nette punto-punto
        pendenzeNette.add(0f)
        for (i in 1 until punti.size) {
            val p1 = punti[i - 1]
            val p2 = punti[i]
            val distanza = p1.distanceToAsDouble(p2)
            val dislivello = p2.altitude - p1.altitude
            pendenzeNette.add(if (distanza > 0) (dislivello / distanza * 100).toFloat() else 0f)
        }

        // Ora calcola la media mobile (smoothing) sulle pendenze nette
        val pendenzeSmussate = mutableListOf<Float>()
        val mezzaFinestra = finestra / 2

        for (i in pendenzeNette.indices) {
            // Definisci i limiti della finestra di media, senza uscire dagli array bounds
            val start = maxOf(0, i - mezzaFinestra)
            val end = minOf(pendenzeNette.size - 1, i + mezzaFinestra)

            // Estrai la sotto-lista di pendenze da mediare
            val sottoLista = pendenzeNette.subList(start, end + 1)

            // Calcola la media e aggiungila al risultato finale
            if (sottoLista.isNotEmpty()) {
                pendenzeSmussate.add(sottoLista.average().toFloat())
            } else {
                pendenzeSmussate.add(0f)
            }
        }
        return pendenzeSmussate
    }


}