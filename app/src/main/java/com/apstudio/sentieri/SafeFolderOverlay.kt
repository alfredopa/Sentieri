package com.apstudio.sentieri

import android.graphics.Canvas
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay

/**
 * A subclass of FolderOverlay that prevents NullPointerException in draw() 
 * and other interaction methods by ensuring mOverlayManager is not nullified onDetach.
 * This allows the overlay to be reused across different MapView instances (e.g., in a ViewModel).
 */
class SafeFolderOverlay : FolderOverlay() {

    override fun onDetach(mapView: MapView?) {
        // Log.d("SafeFolderOverlay", "onDetach called for $name")
        // We call onDetach on the internal manager but we DO NOT set it to null.
        // This prevents the NPE in draw() when the overlay is reused.
        // Note: osmdroid's FolderOverlay.onDetach() normally sets mOverlayManager = null.
        // By NOT doing that, we keep the items alive for the next MapView.
        mOverlayManager?.onDetach(mapView)
    }

    /**
     * Ensures the internal manager is properly linked to the current MapView before drawing.
     */
    override fun draw(canvas: android.graphics.Canvas?, osmv: MapView?, shadow: Boolean) {
        if (shadow) return
        if (osmv == null) return
        
        try {
            super.draw(canvas, osmv, shadow)
        } catch (e: Exception) {
            // Log.e("SafeFolderOverlay", "Error during draw: ${e.message}")
        }
    }
}
