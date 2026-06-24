package com.example.cityexplorerchallenge

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(
    val id: String,
    val name: String,
    val category: String,
    val rawType: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int
)

data class Challenge(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val category: String,
    val targetPlaceName: String,
    val rawPlaceType: String,
    val targetLatitude: Double,
    val targetLongitude: Double,
    val userLatitude: Double,
    val userLongitude: Double,
    val distanceMeters: Int,
    val reasoning: List<String>,
    val generatedAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_ACTIVE,
    val completedAt: Long? = null,
    val expiredAt: Long? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("category", category)
        .put("targetPlaceName", targetPlaceName)
        .put("rawPlaceType", rawPlaceType)
        .put("targetLatitude", targetLatitude)
        .put("targetLongitude", targetLongitude)
        .put("userLatitude", userLatitude)
        .put("userLongitude", userLongitude)
        .put("distanceMeters", distanceMeters)
        .put("reasoning", JSONArray(reasoning))
        .put("generatedAt", generatedAt)
        .put("status", status)
        .put("completedAt", completedAt)
        .put("expiredAt", expiredAt)

    fun completed(now: Long = System.currentTimeMillis()): Challenge =
        copy(status = STATUS_COMPLETED, completedAt = now)

    fun expired(now: Long = System.currentTimeMillis()): Challenge =
        copy(status = STATUS_EXPIRED, expiredAt = now)

    companion object {
        const val STATUS_ACTIVE = "Active"
        const val STATUS_COMPLETED = "Completed"
        const val STATUS_EXPIRED = "Expired"

        fun fromJson(json: JSONObject): Challenge {
            val reasons = json.optJSONArray("reasoning")
            val reasonList = buildList {
                if (reasons != null) {
                    for (i in 0 until reasons.length()) {
                        add(reasons.optString(i))
                    }
                }
            }

            return Challenge(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title"),
                description = json.optString("description"),
                category = json.optString("category"),
                targetPlaceName = json.optString("targetPlaceName"),
                rawPlaceType = json.optString("rawPlaceType"),
                targetLatitude = json.optDouble("targetLatitude"),
                targetLongitude = json.optDouble("targetLongitude"),
                userLatitude = json.optDouble("userLatitude"),
                userLongitude = json.optDouble("userLongitude"),
                distanceMeters = json.optInt("distanceMeters"),
                reasoning = reasonList,
                generatedAt = json.optLong("generatedAt"),
                status = json.optString("status", STATUS_ACTIVE),
                completedAt = if (json.isNull("completedAt")) null else json.optLong("completedAt"),
                expiredAt = if (json.isNull("expiredAt")) null else json.optLong("expiredAt")
            )
        }
    }
}

fun distanceBetweenMeters(
    startLat: Double,
    startLon: Double,
    endLat: Double,
    endLon: Double
): Int {
    val earthRadiusMeters = 6371000.0
    val dLat = Math.toRadians(endLat - startLat)
    val dLon = Math.toRadians(endLon - startLon)
    val lat1 = Math.toRadians(startLat)
    val lat2 = Math.toRadians(endLat)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadiusMeters * c).roundToInt()
}

fun formatDistance(meters: Int): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "$meters m"
