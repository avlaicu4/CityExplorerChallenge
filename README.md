# City Explorer Challenge

Android application for the Mobile App Development final project. The app works as a small city exploration assistant: it reads the user's location, downloads nearby places from OpenStreetMap/Overpass, and generates a contextual challenge instead of asking the user to enter one manually.

## Features

- Runtime location permission handling.
- Current location lookup with a Krakow demo fallback for emulators without GPS.
- OpenStreetMap Overpass API integration, no private API key required.
- Dynamic challenge generation using multiple rules:
  - nearby places are preferred,
  - recently completed places are avoided,
  - unexplored categories receive higher priority,
  - the last completed category is not repeated immediately,
  - time of day influences category preference,
  - difficulty grows with the number of completed challenges.
- Active challenge screen with category, distance, status, and progress.
- Map screen with OpenStreetMap WebView, target coordinates, distance, and completion check.
- Local persistence with `SharedPreferences` for active challenge, history, and statistics.
- History and statistics screens generated from saved completed challenges.

## External Service

The application uses the public Overpass API:

`https://overpass-api.de/api/interpreter`

No API key is required.

## Build And Run

1. Open the project in Android Studio.
2. Select an emulator or physical Android device.
3. Run the `app` configuration.
4. Allow location permission when prompted.
5. Tap `New Challenge` to generate a challenge.

If the emulator does not provide a GPS location, the app uses Krakow demo coordinates so the full user flow can still be demonstrated in the screencast.

## Main Classes

- `MainActivity`: requests location and starts challenge generation.
- `OverpassPlacesClient`: downloads nearby places from OpenStreetMap.
- `ChallengeEngine`: scores places and creates the best challenge.
- `ChallengeStorage`: saves active challenge, history, and statistics locally.
- `MapActivity`: displays the destination and checks completion.
- `ChallengeDetailsActivity`: explains why the challenge was generated.
- `HistoryActivity`: shows completed challenges.
- `StatisticsActivity`: summarizes completed challenges by category and distance.
