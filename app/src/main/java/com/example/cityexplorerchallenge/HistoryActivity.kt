package com.example.cityexplorerchallenge

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val container = findViewById<LinearLayout>(R.id.historyContainer)
        val history = ChallengeStorage(this).getHistory()

        if (history.isEmpty()) {
            container.addView(historyCard("No completed challenges yet.\nOpen the map and complete your first target."))
            return
        }

        history.forEach { challenge ->
            container.addView(historyCard(formatChallenge(challenge), challenge.status))
        }
    }

    private fun formatChallenge(challenge: Challenge): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val statusLabel = if (challenge.status == Challenge.STATUS_EXPIRED) "Expired" else "Completed"
        val statusTime = when (challenge.status) {
            Challenge.STATUS_EXPIRED -> challenge.expiredAt
            else -> challenge.completedAt
        }?.let { dateFormat.format(Date(it)) } ?: "-"

        return """
            [${challenge.status}] ${challenge.title}
            Place: ${challenge.targetPlaceName}
            Category: ${challenge.category}
            Distance: ${formatDistance(challenge.distanceMeters)}
            $statusLabel: $statusTime
        """.trimIndent()
    }

    private fun historyCard(text: String, status: String = Challenge.STATUS_ACTIVE): TextView {
        val background = GradientDrawable().apply {
            cornerRadius = 18f
            setColor(
                when (status) {
                    Challenge.STATUS_COMPLETED -> Color.rgb(232, 249, 223)
                    Challenge.STATUS_EXPIRED -> Color.rgb(255, 222, 234)
                    else -> Color.rgb(255, 248, 221)
                }
            )
        }

        return TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT
            setTextColor(Color.rgb(35, 35, 35))
            setLineSpacing(4f, 1f)
            setPadding(18, 18, 18, 18)
            this.background = background
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 18
            }
        }
    }
}
