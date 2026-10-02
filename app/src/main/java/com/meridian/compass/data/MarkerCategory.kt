package com.meridian.compass.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Water
import androidx.compose.ui.graphics.vector.ImageVector

enum class MarkerCategory(val label: String) {
    OUTDOOR("Outdoor"),
    VEHICLE("Vehicle"),
    FOOD("Food"),
    WATER("Water"),
    EVENT("Event"),
    PERSON("Person"),
    BOAT("Boat"),
    OTHER("Other");

    companion object {
        fun icon(category: MarkerCategory): ImageVector = when (category) {
            OUTDOOR -> Icons.Filled.Terrain
            VEHICLE -> Icons.Filled.DirectionsCar
            FOOD -> Icons.Filled.Fastfood
            WATER -> Icons.Filled.Water
            EVENT -> Icons.Filled.Celebration
            PERSON -> Icons.Filled.Person
            BOAT -> Icons.Filled.DirectionsBoat
            OTHER -> Icons.Filled.Place
        }

        fun fromName(name: String?): MarkerCategory =
            entries.find { it.name == name } ?: OTHER
    }
}
