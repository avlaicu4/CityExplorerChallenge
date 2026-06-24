package com.example.cityexplorerchallenge

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class OverpassPlacesClient {
    private val client = OkHttpClient()

    fun fetchNearbyPlaces(latitude: Double, longitude: Double): List<Place> {
        val query = """
            [out:json][timeout:25];
            (
              node(around:1600,$latitude,$longitude)["name"]["amenity"];
              node(around:1600,$latitude,$longitude)["name"]["tourism"];
              node(around:1600,$latitude,$longitude)["name"]["leisure"];
              node(around:1600,$latitude,$longitude)["name"]["historic"];
              node(around:1600,$latitude,$longitude)["name"]["shop"];
              way(around:1600,$latitude,$longitude)["name"]["amenity"];
              way(around:1600,$latitude,$longitude)["name"]["tourism"];
              way(around:1600,$latitude,$longitude)["name"]["leisure"];
              way(around:1600,$latitude,$longitude)["name"]["historic"];
              way(around:1600,$latitude,$longitude)["name"]["shop"];
            );
            out center 60;
        """.trimIndent()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter?data=$encodedQuery")
            .addHeader("User-Agent", "CityExplorerChallenge/1.0 student Android project")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Overpass error: HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            val places = mutableListOf<Place>()

            for (i in 0 until elements.length()) {
                val element = elements.optJSONObject(i) ?: continue
                val tags = element.optJSONObject("tags") ?: continue
                val name = tags.optString("name").takeIf { it.isNotBlank() } ?: continue
                val placeLatitude = element.optDoubleOrNull("lat")
                    ?: element.optJSONObject("center")?.optDoubleOrNull("lat")
                    ?: continue
                val placeLongitude = element.optDoubleOrNull("lon")
                    ?: element.optJSONObject("center")?.optDoubleOrNull("lon")
                    ?: continue
                val rawType = firstTagValue(tags) ?: continue
                val category = categoryFor(rawType)
                val distance = distanceBetweenMeters(latitude, longitude, placeLatitude, placeLongitude)

                places.add(
                    Place(
                        id = "${element.optString("type")}-${element.optLong("id")}",
                        name = name,
                        category = category,
                        rawType = rawType.replace("_", " "),
                        latitude = placeLatitude,
                        longitude = placeLongitude,
                        distanceMeters = distance
                    )
                )
            }

            return places
                .distinctBy { it.name.lowercase() to it.category }
                .sortedBy { it.distanceMeters }
        }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

    private fun firstTagValue(tags: JSONObject): String? {
        val keys = listOf("historic", "tourism", "leisure", "amenity", "shop")
        return keys.firstNotNullOfOrNull { key ->
            tags.optString(key).takeIf { it.isNotBlank() }
        }
    }

    private fun categoryFor(rawType: String): String {
        return when (rawType) {
            "cafe", "restaurant", "fast_food", "bar", "pub", "bakery", "confectionery" -> "Food"
            "park", "garden", "playground", "nature_reserve", "pitch", "sports_centre" -> "Nature / Sport"
            "museum", "gallery", "artwork", "attraction", "viewpoint", "theatre", "cinema" -> "Culture"
            "monument", "memorial", "castle", "archaeological_site", "ruins", "wayside_cross" -> "History"
            "books", "mall", "clothes", "supermarket", "marketplace", "gift" -> "Shopping"
            "library", "university", "college", "school" -> "Learning"
            else -> "Local discovery"
        }
    }
}
