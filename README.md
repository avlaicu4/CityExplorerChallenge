# City Explorer Challenge

**City Explorer Challenge** is a native Android application (Kotlin) that turns walking through a city into a series of location-based challenges. The app reads the user's GPS location, downloads real nearby places from **OpenStreetMap (Overpass API)**, and uses a custom **scoring algorithm** to generate a personalised exploration challenge. The user then has to physically reach the destination; completion is verified through GPS.

Final project for the **Mobile App Development** course.

---

## Table of Contents

1. [Main Features](#main-features)
2. [Challenge Generation Algorithm](#challenge-generation-algorithm)
3. [Challenge Lifecycle](#challenge-lifecycle)
4. [Architecture](#architecture)
5. [Technologies and Android APIs](#technologies-and-android-apis)
6. [External Service: Overpass API](#external-service-overpass-api)
7. [Data Persistence](#data-persistence)
8. [Error Handling and Fallbacks](#error-handling-and-fallbacks)
9. [Build and Run](#build-and-run)
10. [Possible Improvements](#possible-improvements)

---

## Main Features

### Location
- Runtime permission handling for `ACCESS_FINE_LOCATION` using the Activity Result API.
- Current location obtained with the **Google Fused Location Provider** (`PRIORITY_HIGH_ACCURACY`).
- Fallback to Krakow demo coordinates when permission is denied or no GPS fix is available (useful on emulators).

### Automatic challenge generation
- The user never types a challenge: the app builds one from **real places around the user**.
- Places are fetched live from OpenStreetMap and mapped into **7 categories**: Food, Nature / Sport, Culture, History, Shopping, Learning, Local discovery.
- A **scoring engine** with 8 rules picks the most suitable place (see [algorithm](#challenge-generation-algorithm)).
- **Three difficulty levels** that scale with user progress: *Easy* → *Balanced* → *Explorer*.
- Each challenge stores the **reasons why it was chosen**, displayed on the Challenge Details screen (explainable recommendation).

### Adaptive user preference
- The app counts **consecutive skipped challenges** (user taps *New Challenge* while one is active).
- After **3 skips**, a custom dialog asks the user which category they would prefer (Nature, Culture, Food, History, or *Surprise me*), and the next challenge is tailored to that choice.

### Map and GPS completion check
- Interactive map built with **Leaflet.js + OpenStreetMap tiles** inside a `WebView`.
- Shows the user's position, the target, and a line between them; the map auto-fits both points.
- Live distance and coordinates displayed under the map, together with the location source (real GPS or demo).
- **Completion is verified by GPS**: the challenge is marked as completed only if the user is within **150 m** of the target. Distance is computed with the **Haversine formula**.

### Challenge expiration
- Every challenge expires **24 hours** after it was generated.
- Expired challenges are moved to history with the status *Expired*, and a dedicated dialog invites the user to generate a new one.

### History and statistics
- **History** screen listing completed and expired challenges with date, category and status (colour-coded cards).
- **Statistics** screen: total completed challenges, challenges completed today, total explored distance, breakdown per category, and most explored category.
- Home screen progress card: completed today and total.

### User interface
- **6 activities** (Welcome, Home, Map, Challenge Details, History, Statistics) and **3 custom dialogs** (Category Preference, Challenge Completed, Challenge Expired).
- Custom drawables (rounded cards, coloured buttons) and day/night themes.

---

## Challenge Generation Algorithm

Implemented in `ChallengeEngine.kt`. Every nearby place receives a numeric score, and the place with the **highest score** becomes the challenge (ties are broken by choosing the closer place).

| Rule | Score |
|---|---|
| Place is within the current difficulty range | **+35** (otherwise −20) |
| Place was not among the last 5 completed places | **+25** (otherwise −50) |
| Place was not suggested in the last 6 generations | **+22** (otherwise −80) |
| Category has never been completed before | **+30** (otherwise +8) |
| Category differs from the last completed category | **+18** (otherwise −15) |
| Category matches the current time of day | **+16** |
| Category matches the user's selected preference | **+120** |
| Distance ≤ 500 m / ≤ 1 km | **+18** / **+10** |

**Time-of-day preferences**

| Hours | Preferred categories |
|---|---|
| 06:00 – 10:59 | Food, Nature / Sport |
| 11:00 – 14:59 | Food, Culture |
| 15:00 – 18:59 | Nature / Sport, History, Culture |
| Evening / night | Culture, History, Local discovery |

**Difficulty progression**

| Completed challenges | Difficulty | Max preferred distance |
|---|---|---|
| 0 – 4 | Easy | 900 m |
| 5 – 9 | Balanced | 1.2 km |
| 10+ | Explorer | 1.6 km |

To avoid repetition, recently suggested places are first filtered out entirely, as long as at least 3 fresh places remain.

---

## Challenge Lifecycle

```
            New Challenge
                 │
                 ▼
   ┌──────────── ACTIVE ────────────┐
   │             │                  │
   │ user is ≤150 m from target     │ 24 h passed
   ▼             │                  ▼
COMPLETED        │               EXPIRED
(saved in        │               (saved in
 history)        │                history)
                 │
   user taps New Challenge again → skip counter +1
   3 skips → category preference dialog
```

---

## Architecture

```
app/src/main/java/com/example/cityexplorerchallenge/
├── WelcomeActivity.kt          Entry screen (launcher activity)
├── MainActivity.kt             Home: permissions, location, challenge generation, dialogs
├── MapActivity.kt              Leaflet map, live distance, GPS completion check
├── ChallengeDetailsActivity.kt Explains why the challenge was generated
├── HistoryActivity.kt          Completed / expired challenges
├── StatisticsActivity.kt       Aggregated statistics
├── OverpassPlacesClient.kt     Network layer: Overpass query, JSON parsing, category mapping
├── ChallengeEngine.kt          Business logic: scoring and challenge creation
├── ChallengeStorage.kt         Persistence layer (SharedPreferences + JSON)
└── ChallengeModels.kt          Data classes (Place, Challenge), JSON serialisation,
                                Haversine distance, formatting helpers
```

The code separates three responsibilities:
- **Network layer** – `OverpassPlacesClient` (no Android UI dependencies).
- **Logic layer** – `ChallengeEngine` (pure Kotlin, independent of Android, easy to test).
- **Data layer** – `ChallengeStorage` and the models in `ChallengeModels`.

Activities handle only UI and the user flow.

**Data flow when generating a challenge:**
`MainActivity` → Fused Location → `OverpassPlacesClient` (background, `Dispatchers.IO`) → `ChallengeEngine.generate()` (with history and recent suggestions from `ChallengeStorage`) → `ChallengeStorage.saveActiveChallenge()` → UI update.

---

## Technologies and Android APIs

| Area | Used |
|---|---|
| Language | Kotlin |
| Min / Target SDK | 34 / 36 |
| UI | XML layouts, ConstraintLayout, Material Components, custom drawables and dialogs |
| Location | Google Play Services – Fused Location Provider |
| Networking | OkHttp 4.12 |
| Concurrency | Kotlin Coroutines, `lifecycleScope`, `Dispatchers.IO` |
| Maps | Leaflet.js 1.9.4 + OpenStreetMap tiles in `WebView` (JavaScript enabled) |
| Persistence | SharedPreferences with manual JSON serialisation (`org.json`) |
| Permissions | Activity Result API (`RequestPermission`) |
| Navigation | Explicit `Intent`s between activities |

**Permissions:** `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`.

---

## External Service: Overpass API

Endpoint: `https://overpass-api.de/api/interpreter` (public, no API key required).

The app sends one Overpass QL query requesting **named nodes and ways** within **1600 m** of the user, for the OSM keys `amenity`, `tourism`, `leisure`, `historic` and `shop` (max. 60 results, `out center` for ways).

The JSON response is parsed manually:
- coordinates are taken from the node or from the way's centre;
- the most relevant tag (`historic` > `tourism` > `leisure` > `amenity` > `shop`) decides the place type;
- the type is mapped to one of the 7 app categories;
- duplicates are removed and places are sorted by distance.

---

## Data Persistence

All data is stored locally in `SharedPreferences` (`CityExplorerPrefs`) as JSON:

| Key | Content |
|---|---|
| `ACTIVE_CHALLENGE` | Current challenge (all fields + reasoning) |
| `CHALLENGE_HISTORY` | List of completed and expired challenges |
| `RECENT_SUGGESTIONS` | Last 6 suggested place names (anti-repetition) |
| `CONSECUTIVE_SKIPS` | Skip counter for the preference dialog |

Data survives app restarts; parsing errors are handled safely with `runCatching`.

---

## Error Handling and Fallbacks

The app is designed to always remain usable:

| Situation | Behaviour |
|---|---|
| Location permission denied | Uses Krakow demo coordinates |
| No GPS fix / location failure | Uses Krakow demo coordinates |
| Network or Overpass error | Generates the challenge from a built-in list of demo places |
| No places found | Shows an empty state with a clear message |
| Challenge older than 24 h | Moves it to history as *Expired* and shows a dialog |

Each case is communicated to the user through a Toast message.

---

## Build and Run

1. Clone the repository and open it in **Android Studio**.
2. Select an emulator or a physical device running **Android 14 (API 34)** or newer.
3. Run the `app` configuration.
4. Allow location permission when prompted.
5. Tap **New Challenge** to generate a challenge, then **Open Map** to navigate and check completion.

**Testing on an emulator:** set a custom location from *Extended Controls → Location*. To test completion, move the emulator location close to the target coordinates shown on the map screen. Without a GPS location, the app automatically uses Krakow demo coordinates.

---

## Possible Improvements

- Migrate to MVVM with `ViewModel` and a Room database.
- Unit tests for `ChallengeEngine` and the distance calculation.
- `RecyclerView` for the history list.
- Walking routes instead of a straight line on the map.
- Move all UI texts to `strings.xml` for localisation.
