package com.apstudio.sentieri

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class RectangleSearchOverlay(private val onAreaSelected: (BoundingBox) -> Unit) : Overlay() {
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private var isDrawing = false
    var isActive = false

    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (isActive) {
            // Log periodico rimosso per evitare spam, ma utile se necessario
            if (isDrawing && startPoint != null && endPoint != null) {
                val projection = mapView.projection
                val p1 = projection.toPixels(startPoint, null)
                val p2 = projection.toPixels(endPoint, null)
                val rect = RectF(
                    minOf(p1.x.toFloat(), p2.x.toFloat()),
                    minOf(p1.y.toFloat(), p2.y.toFloat()),
                    maxOf(p1.x.toFloat(), p2.x.toFloat()),
                    maxOf(p1.y.toFloat(), p2.y.toFloat())
                )
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        // LOG FONDAMENTALE: Riceviamo il tocco?
        //Log.d("SearchOverlay", "onTouchEvent: action=${event.action}, isActive=$isActive")

        if (!isActive) return false

        if (event.action == MotionEvent.ACTION_DOWN) {
            //Log.d("SearchOverlay", "ACTION_DOWN intercettato, richiedo focus")
            mapView.requestFocus()
            mapView.parent?.requestDisallowInterceptTouchEvent(true)
        }

        val projection = mapView.projection
        val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startPoint = geoPoint
                endPoint = geoPoint
                isDrawing = true
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    endPoint = geoPoint
                    mapView.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                //Log.d("SearchOverlay", "ACTION_UP: isDrawing=$isDrawing, start=$startPoint, end=$endPoint")
                if (isDrawing && startPoint != null && endPoint != null) {
                    val minLat = minOf(startPoint!!.latitude, endPoint!!.latitude)
                    val maxLat = maxOf(startPoint!!.latitude, endPoint!!.latitude)
                    val minLon = minOf(startPoint!!.longitude, endPoint!!.longitude)
                    val maxLon = maxOf(startPoint!!.longitude, endPoint!!.longitude)
                    
                    if (maxLat - minLat > 0.00001 && maxLon - minLon > 0.00001) {
                        //Log.d("SearchOverlay", "Area valida! Chiamo onAreaSelected")
                        onAreaSelected(BoundingBox(maxLat, maxLon, minLat, minLon))
                    } else {
                        Log.w("SearchOverlay", "Area troppo piccola, ricerca ignorata")
                    }
                }
                resetState(mapView)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                //Log.d("SearchOverlay", "ACTION_CANCEL")
                resetState(mapView)
                return true
            }
        }
        return false
    }

    private fun resetState(mapView: MapView) {
        isDrawing = false
        startPoint = null
        endPoint = null
        mapView.invalidate()
    }
}
