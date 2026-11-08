# Badminton Scorekeeper

An Android scorekeeper for badminton singles and doubles. Keep score from the
phone or tablet, or use a paired Bluetooth remote that sends supported key
events.

## Features

### Scoring

- Choose a game target of 3, 7, 11, 15, or 21 points.
- Applies the win-by-two rule, with a hard cap of 30 points.
- Add or remove points from either side and undo the last scoring action.
- Tracks the server in singles and the serving team and player in doubles.
- Swap sides without losing the current score.
- Configure player and team names, and reset or start another game.

### Players and teams

- Save and reuse player names from the player roster.
- Select players for singles or doubles.
- Drag players between court positions, randomize teams, swap sides, or clear
  the court while setting up a match.
- Add players to or remove players from the roster.

### Sounds and speech

- Toggle score announcements and bonus sounds independently.
- Hear spoken scores after points, special score sounds, and a randomized
  sound when a game is won.
- Set the text-to-speech language/accent, speech rate, and pitch.
- Add custom pronunciations for player names, view saved pronunciations, and
  test the voice settings.

### Game history and statistics

- Completed games are saved in recent history, grouped by date, up to 150 games.
- Review game results and delete individual history records or clear recent
  history.
- View singles and doubles statistics for all time, the last 150 games, or the
  last 24 hours, including player and head-to-head records.
- Export all-time data as CSV files for overall statistics, head-to-head
  statistics, metadata, and game history.
- Clear all-time statistics separately from recent game history.

## Getting started

1. Install and open the app on an Android phone or tablet.
2. Set up player names and choose the game target.
3. Use the on-screen score controls to keep score. Enable doubles mode and
   choose four players for a doubles match.
4. To use a remote, pair it with Android first, then use its supported buttons
   to control scoring. The main screen displays the available key shortcuts.

## Requirements

- Android 7.0 (API 24) or later.
- Android Studio to build the project from source.

## Build from source

Open the project in Android Studio and run the `app` configuration, or build
from the repository root with the Gradle wrapper:

```shell
./gradlew assembleDebug
```

On Windows, use:

```powershell
.\gradlew.bat assembleDebug
```
