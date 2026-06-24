package com.example.cityexplorerchallenge

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatisticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        val storage = ChallengeStorage(this)
        val history = storage.getHistory()
        val completed = history.filter { it.status == Challenge.STATUS_COMPLETED }
        val categoryCounts = storage.categoryCounts()
        val favorite = categoryCounts.maxByOrNull { it.value }?.key ?: "-"
        val categoriesText = if (categoryCounts.isEmpty()) {
            "- No categories completed yet"
        } else {
            categoryCounts.entries
                .sortedByDescending { it.value }
                .joinToString("\n") { "- ${it.key}: ${it.value}" }
        }

        findViewById<TextView>(R.id.tvStatsTotal).text = """
            Total completed challenges: ${completed.size}
            Completed today: ${storage.completedTodayCount()}
            Total explored distance: ${formatDistance(storage.totalExploredMeters())}
        """.trimIndent()

        findViewById<TextView>(R.id.tvStatsCategories).text = categoriesText
        findViewById<TextView>(R.id.tvStatsFavorite).text = "Most explored category: $favorite"
    }
}
