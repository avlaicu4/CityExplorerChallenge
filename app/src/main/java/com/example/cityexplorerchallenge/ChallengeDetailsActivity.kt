package com.example.cityexplorerchallenge

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChallengeDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_details)

        findViewById<Button>(R.id.btnBackToHome).setOnClickListener {
            finish()
        }

        val challenge = ChallengeStorage(this).getActiveChallenge()
        if (challenge == null) {
            findViewById<TextView>(R.id.tvDetailsTitle).text = "No active challenge"
            findViewById<TextView>(R.id.tvDetailsReason).text = "Generate a challenge from the main screen first."
            findViewById<TextView>(R.id.tvDetailsPlaceInfo).text = "-"
            return
        }

        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(challenge.generatedAt))

        findViewById<TextView>(R.id.tvDetailsTitle).text = "Challenge: ${challenge.title}"
        findViewById<TextView>(R.id.tvDetailsReason).text =
            challenge.reasoning.joinToString(separator = "\n") { "- $it" }
        findViewById<TextView>(R.id.tvDetailsPlaceInfo).text = """
            Name: ${challenge.targetPlaceName}
            Category: ${challenge.category}
            OSM type: ${challenge.rawPlaceType}
            Distance: ${formatDistance(challenge.distanceMeters)}
            Generated: $generatedAt
        """.trimIndent()
    }
}
