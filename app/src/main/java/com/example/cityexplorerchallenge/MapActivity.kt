package com.example.cityexplorerchallenge

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Window
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class MapActivity : AppCompatActivity() {
    private lateinit var storage: ChallengeStorage
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapView: WebView
    private lateinit var tvMapDistance: TextView
    private lateinit var tvMapCoordinates: TextView
    private var activeChallenge: Challenge? = null
    private var currentDistanceMeters: Int = 0
    private var currentLocationSource: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        storage = ChallengeStorage(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        storage.expireActiveChallengeIfNeeded()?.let {
            showExpiredDialogAndFinish(it)
            return
        }
        activeChallenge = storage.getActiveChallenge()

        val challenge = activeChallenge
        if (challenge == null) {
            Toast.makeText(this, "No active challenge found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mapView = findViewById(R.id.mapWebView)
        tvMapDistance = findViewById(R.id.tvMapDistance)
        tvMapCoordinates = findViewById(R.id.tvMapCoordinates)

        mapView.settings.javaScriptEnabled = true
        mapView.settings.domStorageEnabled = true

        findViewById<TextView>(R.id.tvMapTarget).text = "Target: ${challenge.targetPlaceName}"
        updateMapWithBestLocation(challenge)

        findViewById<Button>(R.id.btnCheckCompletion).setOnClickListener {
            checkCompletion(challenge)
        }
    }

    @Suppress("MissingPermission")
    private fun updateMapWithBestLocation(challenge: Challenge) {
        if (!hasLocationPermission()) {
            currentLocationSource = locationSourceFor(challenge.userLatitude, challenge.userLongitude)
            renderMap(challenge, challenge.userLatitude, challenge.userLongitude, challenge.distanceMeters)
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val distance = distanceBetweenMeters(
                        location.latitude,
                        location.longitude,
                        challenge.targetLatitude,
                        challenge.targetLongitude
                    )
                    currentLocationSource = locationSourceFor(location.latitude, location.longitude)
                    renderMap(challenge, location.latitude, location.longitude, distance)
                } else {
                    currentLocationSource = locationSourceFor(challenge.userLatitude, challenge.userLongitude)
                    renderMap(challenge, challenge.userLatitude, challenge.userLongitude, challenge.distanceMeters)
                }
            }
            .addOnFailureListener {
                currentLocationSource = locationSourceFor(challenge.userLatitude, challenge.userLongitude)
                renderMap(challenge, challenge.userLatitude, challenge.userLongitude, challenge.distanceMeters)
            }
    }

    @Suppress("MissingPermission")
    private fun checkCompletion(challenge: Challenge) {
        storage.expireActiveChallengeIfNeeded()?.let {
            showExpiredDialogAndFinish(it)
            return
        }

        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission is needed to check completion.", Toast.LENGTH_LONG).show()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                val distance = if (location != null) {
                    distanceBetweenMeters(
                        location.latitude,
                        location.longitude,
                        challenge.targetLatitude,
                        challenge.targetLongitude
                    )
                } else {
                    currentDistanceMeters
                }

                val userLatitude = location?.latitude ?: challenge.userLatitude
                val userLongitude = location?.longitude ?: challenge.userLongitude
                renderMap(challenge, userLatitude, userLongitude, distance)

                if (distance <= COMPLETION_RADIUS_METERS) {
                    val completed = storage.completeActiveChallenge()
                    showCompletionDialog(completed ?: challenge)
                } else {
                    Toast.makeText(
                        this,
                        "Not yet. Current distance: ${formatDistance(distance)}.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not read current location.", Toast.LENGTH_LONG).show()
            }
    }

    private fun showCompletionDialog(challenge: Challenge) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_challenge_completed)
        dialog.setCancelable(false)

        dialog.findViewById<TextView>(R.id.tvCompletedMessage).text =
            "You completed ${challenge.targetPlaceName}."

        dialog.findViewById<Button>(R.id.btnContinueHome).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showExpiredDialogAndFinish(challenge: Challenge) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_challenge_expired)
        dialog.setCancelable(false)

        dialog.findViewById<TextView>(R.id.tvExpiredMessage).text =
            "${challenge.targetPlaceName} expired after 24 hours. Try a fresh challenge from Home."

        dialog.findViewById<Button>(R.id.btnExpiredNewChallenge).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun renderMap(
        challenge: Challenge,
        userLatitude: Double,
        userLongitude: Double,
        distanceMeters: Int
    ) {
        currentDistanceMeters = distanceMeters
        tvMapDistance.text = "Current distance: ${formatDistance(distanceMeters)}"
        tvMapCoordinates.text =
            "Location source: %s\nUser: %.5f, %.5f\nDestination: %.5f, %.5f".format(
                Locale.US,
                currentLocationSource.ifBlank { locationSourceFor(userLatitude, userLongitude) },
                userLatitude,
                userLongitude,
                challenge.targetLatitude,
                challenge.targetLongitude
            )
        mapView.loadDataWithBaseURL(
            "https://city-explorer.local/",
            buildLeafletMapHtml(challenge, userLatitude, userLongitude),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun locationSourceFor(latitude: Double, longitude: Double): String {
        val isKrakowDemo = kotlin.math.abs(latitude - DEMO_LATITUDE) < 0.0002 &&
            kotlin.math.abs(longitude - DEMO_LONGITUDE) < 0.0002
        return if (isKrakowDemo) "Demo/emulator location: Krakow" else "Device/emulator GPS"
    }

    private fun buildLeafletMapHtml(
        challenge: Challenge,
        userLatitude: Double,
        userLongitude: Double
    ): String {
        val targetName = challenge.targetPlaceName.escapeForJavaScript()
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body, #map {
                        height: 100%;
                        width: 100%;
                        margin: 0;
                        padding: 0;
                    }
                    .route-label {
                        font-family: sans-serif;
                        font-size: 13px;
                        font-weight: 700;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    const user = [$userLatitude, $userLongitude];
                    const target = [${challenge.targetLatitude}, ${challenge.targetLongitude}];
                    const map = L.map('map');

                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '&copy; OpenStreetMap contributors'
                    }).addTo(map);

                    L.marker(user).addTo(map).bindPopup('Your location');
                    L.marker(target).addTo(map).bindPopup('$targetName');

                    const bounds = L.latLngBounds([user, target]).pad(0.35);
                    map.fitBounds(bounds);

                    L.polyline([user, target], {
                        color: '#D50000',
                        weight: 5,
                        opacity: 0.9
                    }).addTo(map);
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun String.escapeForJavaScript(): String =
        replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ")

    companion object {
        private const val COMPLETION_RADIUS_METERS = 150
        private const val DEMO_LATITUDE = 50.06143
        private const val DEMO_LONGITUDE = 19.93658
    }
}
