package com.apstudio.sentieri.layer.placeholder

import java.util.ArrayList
import java.util.HashMap

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 *
 * TODO: Replace all uses of this class before publishing your app.
 */
object PlaceholderContent {

    /**
     * An array of sample (placeholder) items.
     */
    val ITEMS: MutableList<PlaceholderItem> = ArrayList()

    /**
     * A map of sample (placeholder) items, by ID.
     */
    val ITEM_MAP: MutableMap<String, PlaceholderItem> = HashMap()

    private const val COUNT = 25

    init {
        // Add some sample items.
        // For sample items, latitude and longitude will be null or default.
        for (i in 1..COUNT) {
            addItem(createPlaceholderItem(i))
        }
    }

    private fun addItem(item: PlaceholderItem) {
        ITEMS.add(item)
        //ITEM_MAP.put(item.id, item) // Assumes PlaceholderItem has an 'id' property if uncommented
    }

    // Adjusted to create items without real coordinates for the sample data
    private fun createPlaceholderItem(position: Int): PlaceholderItem {
        return PlaceholderItem("Item $position", makeDetails(position)) // lat/lon will be null by default
    }

    private fun makeDetails(position: Int): String {
        val builder = StringBuilder()
        builder.append("Details about Item: ").append(position)
        for (i in 0 until position) { // Corrected loop condition
            builder.append("\nMore details information here.")
        }
        return builder.toString()
    }

    /**
     * A placeholder item representing a piece of content.
     * Added latitude and longitude fields.
     */
    data class PlaceholderItem(
        val content: String,
        val details: String,
        val latitude: Double? = null, // Nullable Double for latitude
        val longitude: Double? = null // Nullable Double for longitude
    ) {
        override fun toString(): String = content
    }
}