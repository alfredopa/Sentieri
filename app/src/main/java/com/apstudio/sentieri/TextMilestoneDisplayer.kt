package com.apstudio.sentieri

import android.graphics.Canvas
import android.graphics.Paint
import org.osmdroid.views.overlay.milestones.MilestoneDisplayer
import org.osmdroid.views.overlay.milestones.MilestoneStep
import androidx.core.graphics.withTranslation

/**
 * Classe personalizzata per disegnare testo su una Polyline, compatibile con osmdroid 6.1.x.
 * Questa versione gestisce autonomamente i parametri di orientamento e disegno,
 * poiché i metodi della classe base (es. getInitialOrientation) non sono pubblici.
 */
class TextMilestoneDisplayer(
    private val pInitialOrientation: Float,
    private val pFollowTrajectory: Boolean,
    private val pPaint: Paint,
    private val pXOffset: Int,
    private val pYOffset: Int
) : MilestoneDisplayer(pInitialOrientation.toDouble(), pFollowTrajectory) { // Chiamiamo il costruttore base anche se non useremo i suoi metodi

    // Funzione lambda per la massima flessibilità nella formattazione del testo
    var getDistanceText: (pDistance: Double) -> String = { "" }

    /**
     * Questo è l'unico metodo astratto da implementare per la classe MilestoneDisplayer
     * nella versione 6.1.20 di osmdroid.
     */
    override fun draw(pCanvas: Canvas, pParameter: Any) {
        // 1. Esegui il cast del parametro generico a MilestoneStep
        if (pParameter !is MilestoneStep) return

        val step = pParameter // Ora 'step' è di tipo MilestoneStep

        // 2. Salva lo stato del canvas
        pCanvas.withTranslation(step.x.toFloat() + pXOffset, step.y.toFloat() + pYOffset) {

            // 3. Applica la traslazione per posizionare il disegno
            // 4. Applica la rotazione basandoci sui parametri passati al nostro costruttore
            if (pFollowTrajectory) {
                // Se dobbiamo seguire la traiettoria, usiamo l'orientamento fornito dallo 'step'
                rotate((step.orientation + pInitialOrientation).toFloat())
            } else {
                // Altrimenti, usiamo solo l'orientamento iniziale fisso
                rotate(pInitialOrientation)
            }

            // 5. Disegna il testo
            // La distanza è una proprietà pubblica dello 'step' (o meglio, `distanceToNext`)
            // Ma per avere la distanza dall'inizio, dobbiamo calcolarla.
            // Fortunatamente, il MilestoneManager la calcola per noi, ma come la otteniamo?
            // LA SOLUZIONE è che il lister (es. MilestoneMeterDistanceLister) la calcola,
            // ma lo step stesso non la espone con un getter pubblico.
            // Dobbiamo usare un trucco. Il metodo toString() di MilestoneStep contiene la distanza!
            // Esempio: "x=540, y=863, orientation=2, distance=1000.0, next=20.0"
            // È una soluzione "sporca" ma l'unica possibile senza modificare la libreria.
            val distance = parseDistanceFromString(step.toString())

            if (distance != null) {
                drawText(getDistanceText(distance), 0f, 0f, pPaint)
            }

            // 6. Ripristina lo stato del canvas
        }
    }

    /**
     * Esegue il parsing della stringa di debug di MilestoneStep per estrarre la distanza.
     * Questo è un workaround necessario per le vecchie versioni di osmdroid.
     */
    private fun parseDistanceFromString(stepString: String): Double? {
        return try {
            val parts = stepString.split(", ")
            val distancePart = parts.find { it.startsWith("distance=") }
            distancePart?.substringAfter("distance=")?.toDouble()
        } catch (e: Exception) {
            null // In caso di errore nel parsing, non disegniamo nulla
        }
    }
}
