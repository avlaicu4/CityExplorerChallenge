package com.example.cityexplorerchallenge

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: ChallengeStorage
    private val placesClient = OverpassPlacesClient()
    private val challengeEngine = ChallengeEngine()

    private lateinit var tvChallengeTitle: TextView
    private lateinit var tvChallengeDescription: TextView
    private lateinit var tvChallengeCategory: TextView
    private lateinit var tvChallengeDistance: TextView
    private lateinit var tvChallengeStatus: TextView
    private lateinit var tvCompletedToday: TextView
    private lateinit var tvTotalCompleted: TextView
    private var pendingPreferredCategory: String? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                generateChallengeFromLocation(pendingPreferredCategory)
            } else {
                Toast.makeText(this, "Using demo location because GPS permission was denied.", Toast.LENGTH_LONG).show()
                generateChallenge(DEMO_LATITUDE, DEMO_LONGITUDE, true, pendingPreferredCategory)
            }
            pendingPreferredCategory = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        storage = ChallengeStorage(this)
        bindViews()
        bindActions()
        val expiredChallenge = storage.expireActiveChallengeIfNeeded()
        renderActiveChallenge()
        updateProgress()
        expiredChallenge?.let { showExpiredChallengeDialog(it) }

        if (storage.getActiveChallenge() == null && expiredChallenge == null) {
            ensureLocationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        val expiredChallenge = storage.expireActiveChallengeIfNeeded()
        renderActiveChallenge()
        updateProgress()
        expiredChallenge?.let { showExpiredChallengeDialog(it) }
    }

    private fun bindViews() {
        tvChallengeTitle = findViewById(R.id.tvChallengeTitle)
        tvChallengeDescription = findViewById(R.id.tvChallengeDescription)
        tvChallengeCategory = findViewById(R.id.tvChallengeCategory)
        tvChallengeDistance = findViewById(R.id.tvChallengeDistance)
        tvChallengeStatus = findViewById(R.id.tvChallengeStatus)
        tvCompletedToday = findViewById(R.id.tvCompletedToday)
        tvTotalCompleted = findViewById(R.id.tvTotalCompleted)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.btnOpenMap).setOnClickListener {
            if (storage.getActiveChallenge() == null) {
                Toast.makeText(this, "Generate a challenge first.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, MapActivity::class.java))
            }
        }

        findViewById<Button>(R.id.btnNewChallenge).setOnClickListener {
            handleNewChallengeRequest()
        }

        findViewById<Button>(R.id.btnDetails).setOnClickListener {
            startActivity(Intent(this, ChallengeDetailsActivity::class.java))
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<Button>(R.id.btnStatistics).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }
    }

    private fun handleNewChallengeRequest() {
        val skipCount = if (storage.getActiveChallenge() != null) {
            storage.recordChallengeSkipped()
        } else {
            0
        }

        if (skipCount >= SKIPS_BEFORE_CATEGORY_PROMPT) {
            storage.resetChallengeSkips()
            showCategoryPreferenceDialog()
        } else {
            ensureLocationPermission()
        }
    }

    private fun ensureLocationPermission(preferredCategory: String? = null) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            generateChallengeFromLocation(preferredCategory)
        } else {
            pendingPreferredCategory = preferredCategory
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @Suppress("MissingPermission")
    private fun generateChallengeFromLocation(preferredCategory: String? = null) {
        tvChallengeTitle.text = "Finding nearby places..."
        tvChallengeDescription.text = "The app is checking your location and OpenStreetMap data."
        tvChallengeStatus.text = "Status: Loading"

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    generateChallenge(location.latitude, location.longitude, false, preferredCategory)
                } else {
                    Toast.makeText(this, "No GPS fix yet. Using Krakow demo location.", Toast.LENGTH_LONG).show()
                    generateChallenge(DEMO_LATITUDE, DEMO_LONGITUDE, true, preferredCategory)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "GPS unavailable. Using Krakow demo location.", Toast.LENGTH_LONG).show()
                generateChallenge(DEMO_LATITUDE, DEMO_LONGITUDE, true, preferredCategory)
            }
    }

    private fun generateChallenge(
        latitude: Double,
        longitude: Double,
        demoLocation: Boolean,
        preferredCategory: String? = null
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val places = placesClient.fetchNearbyPlaces(latitude, longitude)
                    challengeEngine.generate(
                        latitude,
                        longitude,
                        places,
                        storage.getHistory(),
                        storage.getRecentSuggestedPlaceNames(),
                        preferredCategory
                    )
                }
            }

            val challenge = result.getOrNull()
                ?: challengeEngine.generate(
                    latitude,
                    longitude,
                    demoPlaces(latitude, longitude),
                    storage.getHistory(),
                    storage.getRecentSuggestedPlaceNames(),
                    preferredCategory
                )

            if (challenge == null) {
                Toast.makeText(this@MainActivity, "No places found nearby.", Toast.LENGTH_LONG).show()
                renderEmptyState()
                return@launch
            }

            storage.saveActiveChallenge(challenge)
            if (preferredCategory != null) {
                storage.resetChallengeSkips()
            }
            renderActiveChallenge()

            val message = if (preferredCategory != null) {
                "Challenge tailored for $preferredCategory."
            } else if (result.isSuccess && !demoLocation) {
                "New challenge generated from your location."
            } else if (result.isSuccess) {
                "New challenge generated with demo GPS coordinates."
            } else {
                "Network fallback used. Challenge is still generated automatically."
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderActiveChallenge() {
        val challenge = storage.getActiveChallenge()
        if (challenge == null) {
            renderEmptyState()
            return
        }

        tvChallengeTitle.text = challenge.title
        tvChallengeDescription.text = challenge.description
        tvChallengeCategory.text = "Category: ${challenge.category}"
        tvChallengeDistance.text = "Distance: ${formatDistance(challenge.distanceMeters)}"
        tvChallengeStatus.text = "Status: ${challenge.status}"
    }

    private fun renderEmptyState() {
        tvChallengeTitle.text = "No active challenge yet"
        tvChallengeDescription.text = "Tap New Challenge and the app will suggest a nearby place automatically."
        tvChallengeCategory.text = "Category: -"
        tvChallengeDistance.text = "Distance: -"
        tvChallengeStatus.text = "Status: Waiting"
    }

    private fun updateProgress() {
        tvCompletedToday.text = "Completed today: ${storage.completedTodayCount()}"
        tvTotalCompleted.text = "Total completed: ${storage.getHistory().size}"
    }

    private fun showExpiredChallengeDialog(challenge: Challenge) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_challenge_expired)
        dialog.setCancelable(false)

        dialog.findViewById<TextView>(R.id.tvExpiredMessage).text =
            "${challenge.targetPlaceName} expired after 24 hours. Let's find a fresh place nearby."

        dialog.findViewById<Button>(R.id.btnExpiredNewChallenge).setOnClickListener {
            dialog.dismiss()
            ensureLocationPermission()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showCategoryPreferenceDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_category_preference)
        dialog.setCancelable(true)
        dialog.setOnCancelListener {
            storage.resetChallengeSkips()
        }

        fun choose(category: String?) {
            dialog.dismiss()
            if (category == null) {
                storage.resetChallengeSkips()
                ensureLocationPermission()
            } else {
                ensureLocationPermission(category)
            }
        }

        dialog.findViewById<Button>(R.id.btnCategoryNature).setOnClickListener {
            choose("Nature / Sport")
        }
        dialog.findViewById<Button>(R.id.btnCategoryCulture).setOnClickListener {
            choose("Culture")
        }
        dialog.findViewById<Button>(R.id.btnCategoryFood).setOnClickListener {
            choose("Food")
        }
        dialog.findViewById<Button>(R.id.btnCategoryHistory).setOnClickListener {
            choose("History")
        }
        dialog.findViewById<Button>(R.id.btnCategorySurprise).setOnClickListener {
            choose(null)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun demoPlaces(latitude: Double, longitude: Double): List<Place> =
        listOf(
            demoPlace("Planty Park", "Nature / Sport", "park", 50.0618, 19.9354, latitude, longitude),
            demoPlace("Muzeum Krakowa", "Culture", "museum", 50.0616, 19.9385, latitude, longitude),
            demoPlace("St. Mary's Basilica", "History", "monument", 50.0617, 19.9394, latitude, longitude),
            demoPlace("Cafe Camelot", "Food", "cafe", 50.0632, 19.9382, latitude, longitude),
            demoPlace("Jagiellonian University", "Learning", "university", 50.0610, 19.9328, latitude, longitude),
            demoPlace("Cloth Hall", "Shopping", "marketplace", 50.0616, 19.9373, latitude, longitude)
        )

    private fun demoPlace(
        name: String,
        category: String,
        rawType: String,
        placeLatitude: Double,
        placeLongitude: Double,
        userLatitude: Double,
        userLongitude: Double
    ): Place =
        Place(
            id = "demo-$name",
            name = name,
            category = category,
            rawType = rawType,
            latitude = placeLatitude,
            longitude = placeLongitude,
            distanceMeters = distanceBetweenMeters(userLatitude, userLongitude, placeLatitude, placeLongitude)
        )

    companion object {
        private const val DEMO_LATITUDE = 50.06143
        private const val DEMO_LONGITUDE = 19.93658
        private const val SKIPS_BEFORE_CATEGORY_PROMPT = 3
    }
}
