package com.meridian.compass.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.data.MarkerCategory
import com.meridian.compass.data.Waypoint
import java.io.File
import java.io.FileOutputStream

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddEditWaypointScreen(
    existing: Waypoint?,
    currentLat: Double?,
    currentLon: Double?,
    currentAltitude: Double?,
    onBack: () -> Unit,
    onSave: (name: String, lat: Double, lon: Double, altitude: Double?, category: MarkerCategory, photoPath: String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var latText by remember { mutableStateOf((existing?.latitude ?: currentLat ?: 0.0).toString()) }
    var lonText by remember { mutableStateOf((existing?.longitude ?: currentLon ?: 0.0).toString()) }
    var category by remember { mutableStateOf(MarkerCategory.fromName(existing?.category)) }
    var newPhotoPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val existingPhotoBitmap = remember(existing?.photoPath) {
        existing?.photoPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    var previewBitmap by remember { mutableStateOf(existingPhotoBitmap) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            previewBitmap = bitmap
            newPhotoPath = saveBitmapToInternalStorage(context, bitmap)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New Marker" else "Edit Marker", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Category", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MarkerCategory.entries.forEach { cat ->
                    CategoryChip(
                        category = cat,
                        selected = cat == category,
                        onClick = { category = cat }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Photo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            PhotoPicker(bitmap = previewBitmap, onTakePhoto = { cameraLauncher.launch() })

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Longitude") },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "Please enter a name."
                        lat == null || lat < -90 || lat > 90 -> error = "Latitude must be between -90 and 90."
                        lon == null || lon < -180 || lon > 180 -> error = "Longitude must be between -180 and 180."
                        else -> onSave(name.trim(), lat, lon, existing?.altitudeMeters ?: currentAltitude, category, newPhotoPath)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Marker")
            }
            if (existing == null && currentLat != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Prefilled with your current location, edit if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CategoryChip(category: MarkerCategory, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(MarkerCategory.icon(category), contentDescription = category.label, tint = fg)
        Spacer(Modifier.height(4.dp))
        Text(category.label, fontSize = 11.sp, color = fg)
    }
}

@Composable
private fun PhotoPicker(bitmap: Bitmap?, onTakePhoto: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { onTakePhoto() },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Marker photo", modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Take photo", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (bitmap != null) "Tap to retake" else "Tap to snap a photo (helps you recognize the spot later)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
    }
}

private fun saveBitmapToInternalStorage(context: android.content.Context, bitmap: Bitmap): String {
    val dir = File(context.filesDir, "marker_photos").apply { mkdirs() }
    val file = File(dir, "marker_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
    return file.absolutePath
}
