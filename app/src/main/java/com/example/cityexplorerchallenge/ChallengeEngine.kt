package com.example.cityexplorerchallenge

import java.util.Calendar

class ChallengeEngine {
    fun generate(
        userLatitude: Double,
        userLongitude: Double,
        places: List<Place>,
        history: List<Challenge>,
        recentSuggestedPlaceNames: Set<String> = emptySet(),
        preferredCategory: String? = null
    ): Challenge? {
        if (places.isEmpty()) return null

        val freshPlaces = places.filter { it.name.lowercase() !in recentSuggestedPlaceNames }
        val candidatePlaces = if (freshPlaces.size >= MIN_FRESH_PLACES_TO_AVOID_REPEATS) {
            freshPlaces
        } else {
            places
        }
        val completed = history.filter { it.status == Challenge.STATUS_COMPLETED }
        val recentlyCompletedNames = completed.take(5).map { it.targetPlaceName.lowercase() }.toSet()
        val completedCategories = completed.map { it.category }.toSet()
        val lastCategory = completed.firstOrNull()?.category
        val preferredTimeCategories = preferredCategoriesForCurrentTime()
        val completionCount = completed.size
        val maxDistance = when {
            completionCount >= 10 -> 1600
            completionCount >= 5 -> 1200
            else -> 900
        }

        val scoredPlaces = candidatePlaces.map { place ->
            val reasons = mutableListOf<String>()
            var score = 0

            if (place.distanceMeters <= maxDistance) {
                score += 35
                reasons.add("destination is within today's difficulty range (${formatDistance(maxDistance)})")
            } else {
                score -= 20
                reasons.add("destination is farther, so it receives lower priority")
            }

            if (place.name.lowercase() !in recentlyCompletedNames) {
                score += 25
                reasons.add("this exact place was not completed recently")
            } else {
                score -= 50
                reasons.add("recently completed places are avoided")
            }

            if (place.name.lowercase() !in recentSuggestedPlaceNames) {
                score += 22
                reasons.add("not suggested in the last few refreshes")
            } else {
                score -= 80
                reasons.add("recently suggested places are avoided")
            }

            if (place.category !in completedCategories) {
                score += 30
                reasons.add("category not explored before")
            } else {
                score += 8
                reasons.add("category was explored before, but nearby place is still relevant")
            }

            if (lastCategory == null || place.category != lastCategory) {
                score += 18
                reasons.add("avoids repeating the last completed category")
            } else {
                score -= 15
                reasons.add("same as the last category, so it is slightly deprioritized")
            }

            if (place.category in preferredTimeCategories) {
                score += 16
                reasons.add("category matches the current time of day")
            }

            if (preferredCategory != null && place.category == preferredCategory) {
                score += 120
                reasons.add("matches your selected category preference")
            }

            if (place.distanceMeters <= 500) {
                score += 18
                reasons.add("very close destination for a quick exploration")
            } else if (place.distanceMeters <= 1000) {
                score += 10
                reasons.add("nearby destination under 1 km")
            }

            ScoredPlace(place, score, reasons)
        }

        val selected = scoredPlaces.maxWithOrNull(
            compareBy<ScoredPlace> { it.score }.thenByDescending { -it.place.distanceMeters }
        ) ?: return null

        return selected.toChallenge(userLatitude, userLongitude, completed.size)
    }

    private fun preferredCategoriesForCurrentTime(): Set<String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..10 -> setOf("Food", "Nature / Sport")
            in 11..14 -> setOf("Food", "Culture")
            in 15..18 -> setOf("Nature / Sport", "History", "Culture")
            else -> setOf("Culture", "History", "Local discovery")
        }
    }

    companion object {
        private const val MIN_FRESH_PLACES_TO_AVOID_REPEATS = 3
    }

    private data class ScoredPlace(
        val place: Place,
        val score: Int,
        val reasons: List<String>
    ) {
        fun toChallenge(userLatitude: Double, userLongitude: Double, completedCount: Int): Challenge {
            val difficulty = when {
                completedCount >= 10 -> "Explorer"
                completedCount >= 5 -> "Balanced"
                else -> "Easy"
            }
            val title = when (place.category) {
                "Food" -> "Find a local food spot: ${place.name}"
                "Nature / Sport" -> "Reach a green or active place: ${place.name}"
                "Culture" -> "Discover a cultural place: ${place.name}"
                "History" -> "Visit a historical point: ${place.name}"
                "Shopping" -> "Explore a local shopping place: ${place.name}"
                "Learning" -> "Find a learning place: ${place.name}"
                else -> "Explore ${place.name}"
            }

            return Challenge(
                title = title,
                description = "$difficulty challenge: go to ${place.name}, about ${formatDistance(place.distanceMeters)} away.",
                category = place.category,
                targetPlaceName = place.name,
                rawPlaceType = place.rawType,
                targetLatitude = place.latitude,
                targetLongitude = place.longitude,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                distanceMeters = place.distanceMeters,
                reasoning = reasons.take(5)
            )
        }
    }
}
