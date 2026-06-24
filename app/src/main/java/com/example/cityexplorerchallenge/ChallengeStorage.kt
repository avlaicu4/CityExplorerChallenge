package com.example.cityexplorerchallenge

import android.content.Context
import org.json.JSONArray

class ChallengeStorage(context: Context) {
    private val prefs = context.getSharedPreferences("CityExplorerPrefs", Context.MODE_PRIVATE)

    fun saveActiveChallenge(challenge: Challenge) {
        prefs.edit()
            .putString(KEY_ACTIVE_CHALLENGE, challenge.toJson().toString())
            .apply()
    }

    fun getActiveChallenge(): Challenge? {
        val raw = prefs.getString(KEY_ACTIVE_CHALLENGE, null) ?: return null
        return runCatching { Challenge.fromJson(org.json.JSONObject(raw)) }.getOrNull()
    }

    fun completeActiveChallenge(): Challenge? {
        val completed = getActiveChallenge()?.completed() ?: return null
        val history = getHistory().toMutableList()
        if (history.none { it.id == completed.id }) {
            history.add(0, completed)
        }
        prefs.edit()
            .putString(KEY_ACTIVE_CHALLENGE, completed.toJson().toString())
            .putString(KEY_HISTORY, JSONArray(history.map { it.toJson() }).toString())
            .apply()
        return completed
    }

    fun expireActiveChallengeIfNeeded(now: Long = System.currentTimeMillis()): Challenge? {
        val active = getActiveChallenge() ?: return null
        if (active.status != Challenge.STATUS_ACTIVE) return null
        if (now - active.generatedAt < CHALLENGE_EXPIRATION_MILLIS) return null

        val expired = active.expired(now)
        val history = getHistory().toMutableList()
        if (history.none { it.id == expired.id }) {
            history.add(0, expired)
        }

        prefs.edit()
            .remove(KEY_ACTIVE_CHALLENGE)
            .putString(KEY_HISTORY, JSONArray(history.map { it.toJson() }).toString())
            .apply()
        return expired
    }

    fun getHistory(): List<Challenge> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                runCatching { Challenge.fromJson(item) }.getOrNull()?.let(::add)
            }
        }
    }

    fun completedTodayCount(): Int {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val currentDay = today.format(java.util.Date())
        return getHistory().count { challenge ->
            challenge.status == Challenge.STATUS_COMPLETED &&
                challenge.completedAt != null &&
                today.format(java.util.Date(challenge.completedAt)) == currentDay
        }
    }

    fun totalExploredMeters(): Int =
        getHistory()
            .filter { it.status == Challenge.STATUS_COMPLETED }
            .sumOf { it.distanceMeters }

    fun categoryCounts(): Map<String, Int> =
        getHistory()
            .filter { it.status == Challenge.STATUS_COMPLETED }
            .groupingBy { it.category }
            .eachCount()

    companion object {
        private const val KEY_ACTIVE_CHALLENGE = "ACTIVE_CHALLENGE"
        private const val KEY_HISTORY = "CHALLENGE_HISTORY"
        private const val CHALLENGE_EXPIRATION_MILLIS = 24L * 60L * 60L * 1000L
    }
}
