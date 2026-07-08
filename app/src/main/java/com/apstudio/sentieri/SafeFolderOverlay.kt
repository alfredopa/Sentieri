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
        // We call onDetach on the internal manager but we DO NOT set it to null.
        // This prevents the NPE in draw() when the overlay is reused.
        mOverlayManager?.onDetach(mapView)
    }

    override fun draw(canvas: Canvas?, osmv: MapView?, shadow: Boolean) {
        if (shadow) return
        if (mOverlayManager == null) return
        super.draw(canvas, osmv, shadow)
    }

    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        if (mOverlayManager == null) return false
        return super.onSingleTapConfirmed(e, mapView)
    }

    override fun onSingleTapUp(e: MotionEvent?, mapView: MapView?): Boolean {
        if (mOverlayManager == null) return false
        return super.onSingleTapUp(e, mapView)
    }

    override fun onLongPress(e: MotionEvent?, mapView: MapView?): Boolean {
        if (mOverlayManager == null) return false
        return super.onLongPress(e, mapView)
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        if (mOverlayManager == null) return false
        return super.onTouchEvent(event, mapView)
    }
}
