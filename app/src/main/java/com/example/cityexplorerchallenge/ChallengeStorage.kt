package com.example.cityexplorerchallenge

import android.content.Context
import org.json.JSONArray

class ChallengeStorage(context: Context) {
    private val prefs = context.getSharedPreferences("CityExplorerPrefs", Context.MODE_PRIVATE)

    fun saveActiveChallenge(challenge: Challenge) {
        val recentNames = getRecentSuggestedPlaceNames()
            .filterNot { it.equals(challenge.targetPlaceName, ignoreCase = true) }
            .toMutableList()
        recentNames.add(0, challenge.targetPlaceName)

        prefs.edit()
            .putString(KEY_ACTIVE_CHALLENGE, challenge.toJson().toString())
            .putString(KEY_RECENT_SUGGESTIONS, JSONArray(recentNames.take(MAX_RECENT_SUGGESTIONS)).toString())
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
            .putInt(KEY_CONSECUTIVE_SKIPS, 0)
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

    fun getRecentSuggestedPlaceNames(): Set<String> {
        val raw = prefs.getString(KEY_RECENT_SUGGESTIONS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildSet {
            for (i in 0 until array.length()) {
                val name = array.optString(i).takeIf { it.isNotBlank() } ?: continue
                add(name.lowercase())
            }
        }
    }

    fun recordChallengeSkipped(): Int {
        val nextCount = prefs.getInt(KEY_CONSECUTIVE_SKIPS, 0) + 1
        prefs.edit()
            .putInt(KEY_CONSECUTIVE_SKIPS, nextCount)
            .apply()
        return nextCount
    }

    fun resetChallengeSkips() {
        prefs.edit()
            .putInt(KEY_CONSECUTIVE_SKIPS, 0)
            .apply()
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
        private const val KEY_RECENT_SUGGESTIONS = "RECENT_SUGGESTIONS"
        private const val KEY_CONSECUTIVE_SKIPS = "CONSECUTIVE_SKIPS"
        private const val MAX_RECENT_SUGGESTIONS = 6
        private const val CHALLENGE_EXPIRATION_MILLIS = 24L * 60L * 60L * 1000L
    }
}
