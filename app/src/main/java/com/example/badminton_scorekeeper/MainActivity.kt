package com.example.badmintonscorekeeper

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import android.Manifest
import android.annotation.SuppressLint
import androidx.core.app.ActivityCompat
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.media.MediaPlayer
import android.media.AudioAttributes
import androidx.appcompat.widget.SwitchCompat
import android.speech.tts.TextToSpeech
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Calendar
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.text.TextUtils

data class GameState(
    val scorePlayer1: Int,
    val scorePlayer2: Int,
    val isPlayer1Serving: Boolean,
    val serveHistory: List<Boolean> = emptyList()
)

data class GameHistory(
    val player1Name: String,
    val player2Name: String,
    val player1Score: Int,
    val player2Score: Int,
    val winningPoints: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val winner: String,
    val startTime: Long,
    val endTime: Long
)
class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var player1ScoreText: TextView
    private lateinit var player2ScoreText: TextView
    private lateinit var serveIndicator1: ImageView
    private lateinit var serveIndicator2: ImageView
    private lateinit var player1Button: Button
    private lateinit var player2Button: Button
    private lateinit var player1RemoveButton: Button
    private lateinit var player2RemoveButton: Button
    private lateinit var resetButton: Button
    private lateinit var player1Name: TextView
    private lateinit var player2Name: TextView
    private lateinit var player1NameInput: EditText
    private lateinit var player2NameInput: EditText
    private lateinit var saveNamesButton: Button

    // Game state variables
    private var scorePlayer1 = 0
    private var scorePlayer2 = 0
    private var isPlayer1Serving = true
    private var gameStarted = false
    private var gameOver = false

    // Track serve history for proper undo functionality
    private val serveHistory = mutableListOf<Boolean>()

    // SharedPreferences keys
    private val prefsName = "BadmintonScorePrefs"
    private val keyPlayer1Name = "player1Name"
    private val keyPlayer2Name = "player2Name"
    private val keyBonusSoundEnabled = "bonusSoundEnabled"

    private val undoStack = mutableListOf<GameState>()

    private val REQUEST_BLUETOOTH_PERMISSIONS = 100

    // Number pad key mapping
    private val keyPlayer1Score = KeyEvent.KEYCODE_MEDIA_PREVIOUS
    private val keyPlayer2Score = KeyEvent.KEYCODE_MEDIA_NEXT
    private val keyPlayer1Remove = KeyEvent.KEYCODE_ZOOM_IN
    private val keyPlayer2Remove = KeyEvent.KEYCODE_ZOOM_OUT
    private val keyResetGame = KeyEvent.KEYCODE_5
    private val keySwapServe = KeyEvent.KEYCODE_VOLUME_UP
    private val keyUndoLast = KeyEvent.KEYCODE_7

    // For swiping detection
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private val minSwipeDistance = 100f // Minimum distance to consider it a swipe

    // Sound variables
    private lateinit var scoreSoundPlayer: MediaPlayer
    private var soundEnabled = true // Optional: allow users to disable sound
    private var bonusSoundEnabled = true // NEW: Control for bonus sounds
    private lateinit var soundToggle: SwitchCompat
    private lateinit var bonusSoundToggle: SwitchCompat // NEW: Bonus sound toggle
    private val soundResources = listOf(
        R.raw.score_sound_1,
        R.raw.score_sound_2,
        R.raw.score_sound_3
    )
    // Text-to-Speech variables
    private lateinit var textToSpeech: TextToSpeech
    private var ttsInitialized = false

    // Game configuration variables
    private lateinit var winningPointsButton: Button

    private var winningPoints = 21
    private val maxPoints = 30

    // SharedPreferences key
    private val keyWinningPoints = "winningPoints"

    // Game history variables
    private val gameHistory = mutableListOf<GameHistory>()
    private val prefsHistoryKey = "gameHistory"
    private val maxHistorySize = 150 // Keep last 150 games

    private lateinit var historyButton: Button

    private lateinit var swapNamesButton: Button

    // NEW: Game timing variables
    private var gameStartTime: Long = 0
    private var gameEndTime: Long = 0
    private var isNewGameJustStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Simple immersive mode
        hideSystemUI()

        // Initialize UI elements
        initializeViews()

        // Load saved names
        loadSavedNames()

        // Set up button click listeners
        setupButtonListeners()

        // Update the display with initial values
        updateScoreDisplay()

        // Setup keyboard listeners - call this AFTER everything else
        setupKeyboardFocus()

        // Add swipe detection
        setupSwipeDetection()

        // Initialize sound - add this line
        initializeSound()

        // Initialize Text-to-Speech
        initializeTextToSpeech()

        // Load game history
        loadHistoryFromPrefs()

        // Check and request Bluetooth permissions
        checkAndRequestBluetoothPermissions()

        // NEW: Check if we should start timing a new game (app just launched with 0-0)
        checkAndStartGameTimer()
    }

    // NEW: Check if we should start timing a new game
    private fun checkAndStartGameTimer() {
        if (scorePlayer1 == 0 && scorePlayer2 == 0 && !gameStarted && !gameOver) {
            startGameTimer()
            isNewGameJustStarted = true
        }
    }

    // NEW: Start the game timer
    private fun startGameTimer() {
        gameStartTime = System.currentTimeMillis()
        Log.d("GameTimer", "Game timer started at: $gameStartTime")
    }

    // NEW: Stop the game timer and return duration
    private fun stopGameTimer(): Long {
        gameEndTime = System.currentTimeMillis()
        val duration = gameEndTime - gameStartTime
        Log.d("GameTimer", "Game timer stopped. Duration: $duration ms")
        return duration
    }

    // NEW: Format duration to human readable format
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> {
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) {
                    "$hours hour${if (hours > 1) "s" else ""} and $remainingMinutes min${if (remainingMinutes > 1) "s" else ""}"
                } else {
                    "$hours hour${if (hours > 1) "s" else ""}"
                }
            }
            minutes > 0 -> {
                val remainingSeconds = seconds % 60
                if (remainingSeconds > 0) {
                    "$minutes min${if (minutes > 1) "s" else ""} and $remainingSeconds sec${if (remainingSeconds > 1) "s" else ""}"
                } else {
                    "$minutes min${if (minutes > 1) "s" else ""}"
                }
            }
            else -> {
                "$seconds sec${if (seconds > 1) "s" else ""}"
            }
        }
    }

    private fun hideSystemUI() {
        // Enables regular immersive mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    private fun loadSavedNames() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val savedPlayer1Name = prefs.getString(keyPlayer1Name, "Player 1")
        val savedPlayer2Name = prefs.getString(keyPlayer2Name, "Player 2")

        // Load winning points with 21 as default
        winningPoints = prefs.getInt(keyWinningPoints, 21)

        // NEW: Load bonus sound setting with true as default
        bonusSoundEnabled = prefs.getBoolean(keyBonusSoundEnabled, true)

        player1Name.text = savedPlayer1Name
        player2Name.text = savedPlayer2Name
        player1NameInput.setText(savedPlayer1Name)
        player2NameInput.setText(savedPlayer2Name)

        // Update the button text and toggle states
        winningPointsButton?.text = "Game to ${winningPoints} points"
        soundToggle?.isChecked = soundEnabled
        bonusSoundToggle?.isChecked = bonusSoundEnabled // NEW: Set the toggle state
    }

    private fun saveGameToHistory() {
        if (scorePlayer1 == 0 && scorePlayer2 == 0) return // Don't save empty games

        val winner = if (scorePlayer1 > scorePlayer2) player1Name.text.toString() else player2Name.text.toString()

        // NEW: Stop timer and calculate duration
        val duration = stopGameTimer()

        val completedGame = GameHistory(
            player1Name = player1Name.text.toString(),
            player2Name = player2Name.text.toString(),
            player1Score = scorePlayer1,
            player2Score = scorePlayer2,
            winningPoints = winningPoints,
            winner = winner,
            startTime = gameStartTime,
            endTime = gameEndTime
        )

        gameHistory.add(completedGame)

        // Limit history size
        if (gameHistory.size > maxHistorySize) {
            gameHistory.removeAt(0)
        }

        // Save to SharedPreferences
        saveHistoryToPrefs()
    }

    private fun saveHistoryToPrefs() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = Gson().toJson(gameHistory)
        prefs.edit().putString(prefsHistoryKey, json).apply()
    }

    private fun loadHistoryFromPrefs() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = prefs.getString(prefsHistoryKey, null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<GameHistory>>() {}.type
                val loadedHistory = Gson().fromJson<List<GameHistory>>(json, type)
                gameHistory.clear()
                gameHistory.addAll(loadedHistory)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading game history: ${e.message}")
            }
        }
    }

    private fun showGameHistory() {
        if (gameHistory.isEmpty()) {
            Toast.makeText(this, "No game history yet", Toast.LENGTH_SHORT).show()
            return
        }

        // Group games by date
        val groupedHistory = gameHistory.reversed().groupBy { game ->
            val calendar = Calendar.getInstance().apply { timeInMillis = game.timestamp }
            calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
        }

        val dates = groupedHistory.keys.toList().sortedWith(compareByDescending { it.first * 1000 + it.second })

        val dateItems = dates.map { dateKey ->
            val games = groupedHistory[dateKey]!!
            val firstGame = games.first()
            val calendar = Calendar.getInstance().apply { timeInMillis = firstGame.timestamp }
            val dateString = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(firstGame.timestamp))
            "$dateString (${games.size} game${if (games.size > 1) "s" else ""})"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Game History - ${getHistorySummary()}")
            .setItems(dateItems) { dialog, which ->
                val selectedDateKey = dates[which]
                val gamesForDate = groupedHistory[selectedDateKey]!!
                showGamesForDate(gamesForDate, dateItems[which])
            }
            .setPositiveButton("Clear History") { dialog, which ->
                clearGameHistory()
            }
            .setNegativeButton("Close", null)
            .create()

        // Apply button colors
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Make "Clear History" red (warning color)
            positiveButton.setTextColor(Color.RED)
            positiveButton.setBackgroundColor(Color.TRANSPARENT) // Remove default background if needed
            positiveButton.setTypeface(null, Typeface.BOLD)

            // Make "Close" dark gray
            negativeButton.setTextColor(Color.DKGRAY)
            negativeButton.setBackgroundColor(Color.TRANSPARENT) // Remove default background if needed
            negativeButton.setTypeface(null, Typeface.BOLD)
        }

        dialog.show()
    }

    private fun showGamesForDate(games: List<GameHistory>, dateTitle: String) {
        // Create a custom dialog with table layout
        val dialog = AlertDialog.Builder(this)
            .setTitle(dateTitle)
            .setPositiveButton("Back to Dates") { dialog, which ->
                showGameHistory()
            }
            .setNegativeButton("Close", null)
            .create()

        // Create a scroll view to handle many games
        val scrollView = ScrollView(this)
        val tableLayout = TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            isShrinkAllColumns = true
            isStretchAllColumns = true
        }

        // Add header row
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, com.google.android.material.R.color.design_default_color_surface))
        }

        // Header cells - added "Delete" column
        val headers = arrayOf("Time Completed", "Player 1", "Score", "Player 2", "Duration", "Delete")
        headers.forEach { headerText ->
            val textView = TextView(this).apply {
                text = headerText
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12)
            }
            headerRow.addView(textView)
        }
        tableLayout.addView(headerRow)

        // Add data rows
        games.forEachIndexed { index, game ->
            val row = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                // Alternate row colors for better readability
                if (index % 2 == 0) {
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, com.google.android.material.R.color.design_default_color_surface))
                } else {
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, com.google.android.material.R.color.design_default_color_background))
                }
            }

            // Time column
            val timeTextView = TextView(this).apply {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(game.timestamp))
                text = time
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                setTextColor(Color.BLACK)

                // Make the time clickable to view details
                setOnClickListener {
                    showGameDetails(game)
                    dialog.dismiss()
                }
            }

            // Player 1 column - highlight if winner
            val player1TextView = TextView(this).apply {
                text = game.player1Name
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                setTextColor(if (game.winner == game.player1Name) Color.BLUE else Color.BLACK)
                setTypeface(typeface, if (game.winner == game.player1Name) Typeface.BOLD else Typeface.NORMAL)

                // Make clickable to view details
                setOnClickListener {
                    showGameDetails(game)
                    dialog.dismiss()
                }
            }

            // Score column
            val scoreTextView = TextView(this).apply {
                text = "${game.player1Score} - ${game.player2Score}"
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                maxLines = 1
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.BLACK)

                // Make clickable to view details
                setOnClickListener {
                    showGameDetails(game)
                    dialog.dismiss()
                }
            }

            // Player 2 column - highlight if winner
            val player2TextView = TextView(this).apply {
                text = game.player2Name
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                setTextColor(if (game.winner == game.player2Name) Color.BLUE else Color.BLACK)
                setTypeface(typeface, if (game.winner == game.player2Name) Typeface.BOLD else Typeface.NORMAL)

                // Make clickable to view details
                setOnClickListener {
                    showGameDetails(game)
                    dialog.dismiss()
                }
            }

            // Duration column
            val durationTextView = TextView(this).apply {
                val duration = formatDuration(game.endTime - game.startTime)
                text = duration
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                setTextColor(Color.BLACK)

                // Make clickable to view details
                setOnClickListener {
                    showGameDetails(game)
                    dialog.dismiss()
                }
            }

            // Delete button column - Icon only
            val deleteButton = TextView(this).apply {
                text = "🗑️" // Trash icon
                // Or use: text = "✕" for X icon
                // Or use: text = "×" for multiplication X

                textSize = 10f
                setTextColor(Color.RED)
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)

                // Add a subtle background on press
                setBackgroundResource(android.R.drawable.list_selector_background)

                setOnClickListener {
                    showDeleteConfirmation(game) {
                        // Refresh the dialog with updated games list
                        dialog.dismiss()

                        // Check if there are remaining games for this date
                        val remainingGames = getRemainingGamesForDate(game)
                        if (remainingGames.isEmpty()) {
                            // If no games left for this date, go back to dates view
                            showGameHistory()
                        } else {
                            // If there are remaining games, refresh the current table view
                            showGamesForDate(remainingGames, dateTitle)
                        }
                    }
                }
            }

            // Add all cells to row
            row.addView(timeTextView)
            row.addView(player1TextView)
            row.addView(scoreTextView)
            row.addView(player2TextView)
            row.addView(durationTextView)
            row.addView(deleteButton)

            tableLayout.addView(row)
        }

        scrollView.addView(tableLayout)
        dialog.setView(scrollView)

        // Apply button colors
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(Color.DKGRAY)
            positiveButton.setBackgroundColor(Color.TRANSPARENT)
            negativeButton.setTextColor(Color.DKGRAY)
            negativeButton.setBackgroundColor(Color.TRANSPARENT)
            positiveButton.setTypeface(null, Typeface.BOLD)
            negativeButton.setTypeface(null, Typeface.BOLD)
        }

        dialog.show()
    }

    private fun showGameDetails(game: GameHistory) {
        val dateTime = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(game.timestamp))
        val duration = formatDuration(game.endTime - game.startTime)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Game Details")
            .setMessage(
                "${game.player1Name} vs ${game.player2Name}\n\n" +
                        "Final Score: ${game.player1Score} - ${game.player2Score}\n" +
                        "Game to: ${game.winningPoints} points\n" +
                        "Winner: ${game.winner}\n" +
                        "Duration: $duration\n" +
                        "Date & Time Completed: $dateTime"
            )
            .setPositiveButton("Back") { dialog, which ->
                showGamesForDateForGame(game)
            }
            .setNeutralButton("Delete") { dialog, which ->
                showDeleteConfirmation(game) {
                    // After deletion, check if we should go back to table view or date view
                    val remainingGamesForDate = getRemainingGamesForDate(game)
                    if (remainingGamesForDate.isEmpty()) {
                        // If no games left for this date, go back to dates view
                        showGameHistory()
                    } else {
                        // If there are remaining games, go back to the table view for this date
                        showGamesForDate(remainingGamesForDate, getDateTitle(remainingGamesForDate.first()))
                    }
                }
            }
            .setNegativeButton("Close", null)
            .create()

        // Apply button colors
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Make "Back" dark gray
            positiveButton.setTextColor(Color.DKGRAY)
            positiveButton.setBackgroundColor(Color.TRANSPARENT)
            positiveButton.setTypeface(null, Typeface.BOLD)

            // Make "Delete" red
            neutralButton.setTextColor(Color.RED)
            neutralButton.setBackgroundColor(Color.TRANSPARENT)
            neutralButton.setTypeface(null, Typeface.BOLD)

            // Make "Close" dark gray
            negativeButton.setTextColor(Color.DKGRAY)
            negativeButton.setBackgroundColor(Color.TRANSPARENT)
            negativeButton.setTypeface(null, Typeface.BOLD)
        }

        dialog.show()
    }

    private fun getRemainingGamesForDate(deletedGame: GameHistory): List<GameHistory> {
        // Get the date key for the deleted game
        val deletedGameCalendar = Calendar.getInstance().apply { timeInMillis = deletedGame.timestamp }
        val deletedGameDateKey = deletedGameCalendar.get(Calendar.YEAR) to deletedGameCalendar.get(Calendar.DAY_OF_YEAR)

        // Filter the current gameHistory to find remaining games for the same date
        return gameHistory.filter { game ->
            val gameCalendar = Calendar.getInstance().apply { timeInMillis = game.timestamp }
            val gameDateKey = gameCalendar.get(Calendar.YEAR) to gameCalendar.get(Calendar.DAY_OF_YEAR)
            gameDateKey == deletedGameDateKey && game.timestamp != deletedGame.timestamp
        }.sortedByDescending { it.timestamp } // Add this line to sort by newest first
    }

    private fun getDateTitle(game: GameHistory): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = game.timestamp }
        val dateString = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(game.timestamp))

        // Count games for this specific date from the current gameHistory
        val gameDateKey = calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
        val gamesCount = gameHistory.count { gameItem ->
            val itemCalendar = Calendar.getInstance().apply { timeInMillis = gameItem.timestamp }
            val itemDateKey = itemCalendar.get(Calendar.YEAR) to itemCalendar.get(Calendar.DAY_OF_YEAR)
            itemDateKey == gameDateKey
        }

        return "$dateString ($gamesCount game${if (gamesCount > 1) "s" else ""})"
    }

    private fun showDeleteConfirmation(
        game: GameHistory,
        onDelete: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("Delete Game")
            .setMessage("Are you sure you want to delete this game?\n\n" +
                    "${game.player1Name} vs ${game.player2Name}\n" +
                    "Score: ${game.player1Score} - ${game.player2Score}")
            .setPositiveButton("Delete") { dialog, which ->
                // Remove the game from history
                val index = gameHistory.indexOfFirst { it.timestamp == game.timestamp }
                if (index != -1) {
                    gameHistory.removeAt(index)
                    saveHistoryToPrefs()
                    Toast.makeText(this, "Game deleted", Toast.LENGTH_SHORT).show()
                    onDelete() // Call the callback after successful deletion
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    val positiveButton = getButton(AlertDialog.BUTTON_POSITIVE)
                    val negativeButton = getButton(AlertDialog.BUTTON_NEGATIVE)

                    // Make "Delete" red
                    positiveButton.setTextColor(Color.RED)
                    positiveButton.setBackgroundColor(Color.TRANSPARENT)
                    positiveButton.setTypeface(null, Typeface.BOLD)

                    // Make "Cancel" dark gray
                    negativeButton.setTextColor(Color.DKGRAY)
                    negativeButton.setBackgroundColor(Color.TRANSPARENT)
                    negativeButton.setTypeface(null, Typeface.BOLD)
                }
            }
            .show()
    }


    private fun showGamesForDateForGame(game: GameHistory) {
        // Group games by date to find which date this game belongs to
        val groupedHistory = gameHistory.reversed().groupBy { gameItem ->
            val calendar = Calendar.getInstance().apply { timeInMillis = gameItem.timestamp }
            calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
        }

        // Find the date key for this specific game
        val gameDateKey = groupedHistory.entries.find { entry ->
            entry.value.any { it.timestamp == game.timestamp }
        }?.key

        if (gameDateKey != null) {
            val gamesForDate = groupedHistory[gameDateKey]!!
            val firstGame = gamesForDate.first()
            val dateString = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(firstGame.timestamp))
            showGamesForDate(gamesForDate, dateString)
        } else {
            // Fallback: just show the full history
            showGameHistory()
        }
    }

    private fun getHistorySummary(): String {
        if (gameHistory.isEmpty()) return "No games played yet"

        val totalGames = gameHistory.size
        val player1Wins = gameHistory.count { it.winner == it.player1Name }
        val player2Wins = gameHistory.count { it.winner == it.player2Name }

        val latestGame = gameHistory.last()
        val latestDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(latestGame.timestamp))

        return "$totalGames total games • Last: $latestDate"
    }

    private fun clearGameHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to clear all game history?")
            .setPositiveButton("Clear") { dialog, which ->
                gameHistory.clear()
                saveHistoryToPrefs()
                Toast.makeText(this, "Game history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Alternative initialization
    private fun initializeSound() {
        try {
            scoreSoundPlayer = MediaPlayer()

            // Configure audio attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                scoreSoundPlayer.setAudioAttributes(audioAttributes)
            }

            scoreSoundPlayer.setOnCompletionListener {
                it.reset() // Reset for next sound loading
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing sound player: ${e.message}")
            soundEnabled = false
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                    ttsInitialized = false
                } else {
                    ttsInitialized = true
                    Log.d("TTS", "Text-to-Speech initialized successfully")
                }
            } else {
                Log.e("TTS", "Text-to-Speech initialization failed")
                ttsInitialized = false
            }
        }

        // Set speech rate and pitch if needed
        textToSpeech.setSpeechRate(0.7f)
        textToSpeech.setPitch(0.9f)
    }

    private fun isGamePointForScoringPlayer(isPlayer1Scored: Boolean): Boolean {
        if (gameOver) return false

        val scoringPlayerScore = if (isPlayer1Scored) scorePlayer1 else scorePlayer2
        val opponentScore = if (isPlayer1Scored) scorePlayer2 else scorePlayer1

        // Max points approach (29 points)
        if (scoringPlayerScore == 29) return true

        // Check if the scoring player can win with the next point
        return wouldNextPointWin(scoringPlayerScore, opponentScore)
    }

    private fun wouldNextPointWin(playerScore: Int, opponentScore: Int): Boolean {
        val newPlayerScore = playerScore + 1

        // Case 1: Reaching 30 points always wins
        if (newPlayerScore == 30) return true

        // Case 2: Regular win - reaching winningPoints with at least 2-point lead
        if (newPlayerScore >= winningPoints && newPlayerScore - opponentScore >= 2) {
            return true
        }

        // Case 3: Extended game win - both players at high scores, getting 2-point lead
        val bothAtHighScores = playerScore >= winningPoints - 1 && opponentScore >= winningPoints - 1
        if (bothAtHighScores && newPlayerScore - opponentScore >= 2) {
            return true
        }

        return false
    }

    private fun checkIfGameWon(): Boolean {
        // Check if a player has won according to badminton rules
        if (scorePlayer1 >= winningPoints || scorePlayer2 >= winningPoints) {
            // Must win by 2 points, unless someone reaches 30 points first
            val pointDifference = Math.abs(scorePlayer1 - scorePlayer2)

            if (pointDifference >= 2) {
                if (!gameOver) { // Only end game if it's not already over
                    endGame()
                }
                return true
            } else if (scorePlayer1 == maxPoints || scorePlayer2 == maxPoints) {
                // Special case: games go to 30 points maximum (win immediately at 30)
                if (!gameOver) { // Only end game if it's not already over
                    endGame()
                }
                return true
            }
        }
        return false
    }

    // Alternative play method
    private fun playScoreSound() {
        if (!soundEnabled) return

        try {
            // Select random sound resource
            val randomSoundRes = soundResources.random()

            // Reset player and set new data source
            scoreSoundPlayer.reset()
            val assetFileDescriptor = resources.openRawResourceFd(randomSoundRes)
            scoreSoundPlayer.setDataSource(
                assetFileDescriptor.fileDescriptor,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.length
            )
            assetFileDescriptor.close()

            // Prepare and play
            scoreSoundPlayer.prepare()
            scoreSoundPlayer.start()

            Log.d("Sound", "Playing sound resource: $randomSoundRes")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing random sound: ${e.message}")
        }
    }

    private fun playSixtyNineSound() {
        try {
            // First speak "sixty-nine" via TTS
            textToSpeech.speak("sixty-nine", TextToSpeech.QUEUE_FLUSH, null, null)

            // Then play the custom MP3 sound
            if (soundEnabled) {
                val sixtyNineSoundPlayer = MediaPlayer()

                // Configure audio attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    sixtyNineSoundPlayer.setAudioAttributes(audioAttributes)
                }

                // Load and play the custom sound
                val assetFileDescriptor = resources.openRawResourceFd(R.raw.quagmire_delay)
                sixtyNineSoundPlayer.setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()

                sixtyNineSoundPlayer.prepare()
                sixtyNineSoundPlayer.start()

                // Release the MediaPlayer when done
                sixtyNineSoundPlayer.setOnCompletionListener {
                    it.release()
                }

                Log.d("Sound", "Playing sixty-nine special sound")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing sixty-nine sound: ${e.message}")
            // Fallback to regular TTS if sound fails
            textToSpeech.speak("sixty-nine", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun playNineElevenSound() {
        try {
            // First speak "sixty-nine" via TTS
            textToSpeech.speak("nine-eleven", TextToSpeech.QUEUE_FLUSH, null, null)

            // Then play the custom MP3 sound
            if (soundEnabled) {
                val NineElevenSoundPlayer = MediaPlayer()

                // Configure audio attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    NineElevenSoundPlayer.setAudioAttributes(audioAttributes)
                }

                // Load and play the custom sound
                val assetFileDescriptor = resources.openRawResourceFd(R.raw.nine_eleven_sound)
                NineElevenSoundPlayer.setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()

                NineElevenSoundPlayer.prepare()
                NineElevenSoundPlayer.start()

                // Release the MediaPlayer when done
                NineElevenSoundPlayer.setOnCompletionListener {
                    it.release()
                }

                Log.d("Sound", "Playing nine-eleven special sound")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing nine-eleven sound: ${e.message}")
            // Fallback to regular TTS if sound fails
            textToSpeech.speak("nine-eleven", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun announceScore(isPlayer1Scored: Boolean) {
        if (!soundEnabled || !ttsInitialized) return

        try {
            val servingScore: Int
            val receivingScore: Int
            val servingPlayerName: String
            val receivingPlayerName: String

            if (isPlayer1Scored) {
                servingScore = scorePlayer1
                receivingScore = scorePlayer2
                servingPlayerName = player1Name.text.toString()
                receivingPlayerName = player2Name.text.toString()
            } else {
                servingScore = scorePlayer2
                receivingScore = scorePlayer1
                servingPlayerName = player2Name.text.toString()
                receivingPlayerName = player1Name.text.toString()
            }

            // Check for special "69" scores - ONLY when bonus sound is enabled
            if (bonusSoundEnabled && isSixtyNineScore(servingScore, receivingScore)) {
                playSixtyNineSound()
                return
            }

            // Check for special "9-11" score - ONLY when bonus sound is enabled
            if (bonusSoundEnabled && isNineElevenScore(servingScore, receivingScore)) {
                playNineElevenSound()
                return
            }

            val announcement = if (isGamePointForScoringPlayer(isPlayer1Scored)) {
                // Game point announcement
                "Game point, $servingScore serving $receivingScore"
            } else {
                // Regular announcement
                "$servingScore serving $receivingScore"
            }

            textToSpeech.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d("TTS", "Announcing: $announcement")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error announcing score: ${e.message}")
        }
    }

    private fun isSixtyNineScore(servingScore: Int, receivingScore: Int): Boolean {
        return (servingScore == 6 && receivingScore == 9) ||
                (servingScore == 6 && receivingScore == 19) ||
                (servingScore == 16 && receivingScore == 9) ||
                (servingScore == 16 && receivingScore == 19)
    }

    private fun isNineElevenScore(servingScore: Int, receivingScore: Int): Boolean {
        return (servingScore == 9 && receivingScore == 11) ||
                (servingScore == 11 && receivingScore == 9)
    }

    private fun showSystemUI() {
        // Shows the system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires runtime permissions for Bluetooth
            val permissionsNeeded = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }

            if (permissionsNeeded.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsNeeded.toTypedArray(),
                    REQUEST_BLUETOOTH_PERMISSIONS
                )
            } else {
                // Permissions already granted, setup Bluetooth
                setupBluetooth()
            }
        } else {
            // For older Android versions, just setup Bluetooth
            setupBluetooth()
        }
    }

    private fun setupBluetooth() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            showNoBluetoothWarning()
        } else {
            setupBluetoothMonitoring()
        }
    }

    private fun showNoBluetoothWarning() {
        Toast.makeText(
            this,
            "This device doesn't support Bluetooth. App will work in manual mode only.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, setup Bluetooth
                    setupBluetooth()
                    Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
                } else {
                    // Permissions denied
                    Toast.makeText(
                        this,
                        "Bluetooth permissions denied. App will work in manual mode only.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupKeyboardFocus() {
        // Initially hide the EditText fields and show the TextView names
        showNameDisplayMode()

        window.decorView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyPress(keyCode)
                }
                true
            }
        }
    }

    private fun showNameDisplayMode() {
        // Show the TextView names, hide the EditText inputs
        player1Name.visibility = View.VISIBLE
        player2Name.visibility = View.VISIBLE
        player1NameInput.visibility = View.GONE
        player2NameInput.visibility = View.GONE
        saveNamesButton.visibility = View.GONE
    }

    private fun showNameEditMode() {
        // Show the EditText inputs for editing, hide the TextView names
        player1Name.visibility = View.GONE
        player2Name.visibility = View.GONE
        player1NameInput.visibility = View.VISIBLE
        player2NameInput.visibility = View.VISIBLE
        saveNamesButton.visibility = View.VISIBLE
    }

    private fun clearEditTextFocus() {
        player1NameInput.clearFocus()
        player2NameInput.clearFocus()
    }

    // Helper function to identify game control keys
    private fun isGameControlKey(keyCode: Int): Boolean {
        return when (keyCode) {
            keyPlayer1Score, keyPlayer2Score, keyPlayer1Remove, keyPlayer2Remove,
            keyResetGame, keySwapServe, keyUndoLast,
            KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_NUMPAD_7 -> true

            else -> false
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        handleKeyPress(keyCode)
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeDetection() {
        // Get the root content view
        val contentView = window.decorView.findViewById<View>(android.R.id.content)

        // List of ALL possible views that could receive touches
        val viewsToListen = arrayOf(
            contentView,
            findViewById<View>(R.id.scoreContainer),
            findViewById<View>(R.id.topControls),
            findViewById<View>(R.id.bottomControls),
            findViewById<View>(R.id.serveIndicator1),
            findViewById<View>(R.id.serveIndicator2)
        )

        val swipeListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Record start position
                    swipeStartX = event.x
                    swipeStartY = event.y
                    Log.d("SWIPE", "Swipe started at X: $swipeStartX")
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Calculate swipe distance and direction
                    val endX = event.x
                    val endY = event.y
                    var deltaX = endX - swipeStartX
                    var deltaY = swipeStartY - endY

                    Log.d("SWIPE", "Swipe ended. DeltaX: $deltaX, DeltaY: $deltaY")

                    // Get orientation and rotation
                    val orientation = resources.configuration.orientation
                    val rotation = windowManager.defaultDisplay.rotation

                    // Adjust deltas for landscape mode based on actual rotation
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        when (rotation) {
                            Surface.ROTATION_90 -> {
                                Log.d("SWIPE_DEBUG", "ROTATION_90 transform - Before: deltaX=$deltaX, deltaY=$deltaY")
                                val temp = deltaY
                                deltaY = -deltaX
                                deltaX = temp
                                Log.d("SWIPE_DEBUG", "ROTATION_90 transform - After: deltaX=$deltaX, deltaY=$deltaY")
                            }
                            Surface.ROTATION_270 -> {
                                Log.d("SWIPE_DEBUG", "ROTATION_270 transform - Before: deltaX=$deltaX, deltaY=$deltaY")
                                val temp = deltaY
                                deltaY = deltaX
                                deltaX = -temp
                                Log.d("SWIPE_DEBUG", "ROTATION_270 transform - After: deltaX=$deltaX, deltaY=$deltaY")
                            }
                        }
                    }

                    Log.d("SWIPE_DEBUG", "AFTER TRANSFORM - DeltaX: $deltaX, DeltaY: $deltaY")

                    // Check if it's a significant horizontal swipe
                    if (Math.abs(deltaX) > minSwipeDistance && Math.abs(deltaX) > Math.abs(deltaY)) {
                        // It's a horizontal swipe
                        if (deltaX > 0) {
                            // Swipe RIGHT → Player 1 scores
                            Log.d("SWIPE", "Right swipe detected - Player 1 scores")
                            handleKeyPress(keyPlayer1Score)
                        } else {
                            // Swipe LEFT → Player 2 scores
                            Log.d("SWIPE", "Left swipe detected - Player 2 scores")
                            handleKeyPress(keyPlayer2Score)
                        }
                    } else if (Math.abs(deltaY) > minSwipeDistance && Math.abs(deltaY) > Math.abs(deltaX)) {
                        // It's a vertical swipe - map to other functions
                        if (deltaY > 0) {
                            // Swipe UP → Undo last action
                            Log.d("SWIPE", "Up swipe detected - Undo last action")
                            handleKeyPress(keyUndoLast)
                        } else {
                            // Swipe Down → New Game
                            Log.d("SWIPE", "Down swipe detected - New Game")
                            handleKeyPress(keyResetGame)
                        }
                    }
                    return@OnTouchListener true // Consume the event
                }

                else -> return@OnTouchListener false
            }
        }

        // Apply the listener to all views
        viewsToListen.forEach { view ->
            view.setOnTouchListener(swipeListener)
        }
    }

    private fun handleKeyPress(keyCode: Int) {
        when (keyCode) {
            keyPlayer1Score -> {
                if (!gameOver) {
                    // NEW: Start timer on first score if not already started
                    if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                        startGameTimer()
                    }
                    // Save current state to undo stack
                    saveCurrentState()

                    gameStarted = true
                    serveHistory.add(isPlayer1Serving)
                    scorePlayer1++

                    // Check if this point wins the game BEFORE updating serve status
                    val wasGameWon = checkIfGameWon()

                    updateServeStatus(true)
                    updateScoreDisplay()
                    showKeyPressFeedback("${player1Name.text} scored!")

                    // Only announce score if game didn't just end
                    if (!wasGameWon) {
                        announceScore(true)  // For player 1
                    }
                }
            }
            keyPlayer2Score -> {
                if (!gameOver) {
                    // NEW: Start timer on first score if not already started
                    if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                        startGameTimer()
                    }
                    // Save current state to undo stack
                    saveCurrentState()

                    gameStarted = true
                    serveHistory.add(isPlayer1Serving)
                    scorePlayer2++

                    // Check if this point wins the game BEFORE updating serve status
                    val wasGameWon = checkIfGameWon()

                    updateServeStatus(false)
                    updateScoreDisplay()
                    showKeyPressFeedback("${player2Name.text} scored!")

                    // Only announce score if game didn't just end
                    if (!wasGameWon) {
                        announceScore(false) // For player 2
                    }
                }
            }
            keyPlayer1Remove -> {
                if (!gameOver && scorePlayer1 > 0) {
                    // Save current state to undo stack
                    saveCurrentState()

                    scorePlayer1--
                    if (serveHistory.isNotEmpty()) {
                        isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                    }
                    updateScoreDisplay()
                    if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                        gameStarted = false
                    }
                    showKeyPressFeedback("${player1Name.text} point removed")
                }
            }
            keyPlayer2Remove -> {
                if (!gameOver && scorePlayer2 > 0) {
                    // Save current state to undo stack
                    saveCurrentState()

                    scorePlayer2--
                    if (serveHistory.isNotEmpty()) {
                        isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                    }
                    updateScoreDisplay()
                    if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                        gameStarted = false
                    }
                    showKeyPressFeedback("${player2Name.text} point removed")
                }
            }
            keyResetGame -> {
                // Save current state to undo stack
                saveCurrentState()

                resetGame()
                showKeyPressFeedback("Game reset!")
            }
            keySwapServe -> {
                // Allow serve swapping even when game hasn't started (0-0 score)
                if (!gameOver) {
                    isPlayer1Serving = !isPlayer1Serving
                    updateScoreDisplay()
                    val serverName = if (isPlayer1Serving) player1Name.text else player2Name.text
                    showKeyPressFeedback("Serve: $serverName")
                    true
                } else false
            }
            keyUndoLast -> {
                if (undoStack.isNotEmpty()) {
                    // Pop the last state from the stack
                    val previousState = undoStack.removeAt(undoStack.size - 1)

                    // Restore the previous state
                    scorePlayer1 = previousState.scorePlayer1
                    scorePlayer2 = previousState.scorePlayer2
                    isPlayer1Serving = previousState.isPlayer1Serving
                    serveHistory.clear()
                    serveHistory.addAll(previousState.serveHistory)

                    // If we're undoing back to a non-game-over state, reset gameOver flag
                    gameOver = false

                    // Re-enable buttons since game is no longer over
                    player1Button.isEnabled = true
                    player2Button.isEnabled = true
                    player1RemoveButton.isEnabled = true
                    player2RemoveButton.isEnabled = true
                    player1Button.text = "+1"
                    player2Button.text = "+1"

                    updateScoreDisplay()
                    showKeyPressFeedback("Undo last action")
                } else if (undoStack.isEmpty()) {
                    showKeyPressFeedback("Nothing to undo")
                }
            }
            // Also support numpad keys (different keycodes)
            KeyEvent.KEYCODE_NUMPAD_1 -> handleKeyPress(keyPlayer1Score)
            KeyEvent.KEYCODE_NUMPAD_2 -> handleKeyPress(keyPlayer2Score)
            KeyEvent.KEYCODE_NUMPAD_3 -> handleKeyPress(keyPlayer1Remove)
            KeyEvent.KEYCODE_NUMPAD_4 -> handleKeyPress(keyPlayer2Remove)
            KeyEvent.KEYCODE_NUMPAD_5 -> handleKeyPress(keyResetGame)
            KeyEvent.KEYCODE_NUMPAD_6 -> handleKeyPress(keySwapServe)
            KeyEvent.KEYCODE_NUMPAD_7 -> handleKeyPress(keyUndoLast)
        }
    }

    private fun saveCurrentState() {
        undoStack.add(GameState(scorePlayer1, scorePlayer2, isPlayer1Serving, ArrayList(serveHistory)))
        // Limit undo stack size to prevent memory issues (e.g., last 50 moves)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    private fun showKeyPressFeedback(message: String) {
        // Use Toast for feedback instead of TextView to avoid layout issues
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Vibrate for tactile feedback
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(50)
            }
        }
    }

    private fun setupBluetoothMonitoring() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            registerReceiver(bluetoothReceiver, filter)
        } catch (e: Exception) {
            Log.e("MainActivity", "Bluetooth setup failed: ${e.message}")
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-request focus when activity resumes
        window.decorView.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver was not registered, ignore
        }

        // Release MediaPlayer resources - ADD THIS
        try {
            if (::scoreSoundPlayer.isInitialized) {
                scoreSoundPlayer.release()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error releasing sound player: ${e.message}")
        }

        // Clean up Text-to-Speech
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    private fun toggleSound() {
        soundEnabled = !soundEnabled
        Toast.makeText(this, "Sound ${if (soundEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
    }

    private fun initializeViews() {
        player1ScoreText = findViewById(R.id.player1Score)
        player2ScoreText = findViewById(R.id.player2Score)
        serveIndicator1 = findViewById(R.id.serveIndicator1)
        serveIndicator2 = findViewById(R.id.serveIndicator2)
        player1Button = findViewById(R.id.player1Button)
        player2Button = findViewById(R.id.player2Button)
        player1RemoveButton = findViewById(R.id.player1RemoveButton)
        player2RemoveButton = findViewById(R.id.player2RemoveButton)
        resetButton = findViewById(R.id.resetButton)
        player1Name = findViewById(R.id.player1Name)
        player2Name = findViewById(R.id.player2Name)
        player1NameInput = findViewById(R.id.player1NameInput)
        player2NameInput = findViewById(R.id.player2NameInput)
        saveNamesButton = findViewById(R.id.saveNamesButton)
        soundToggle = findViewById(R.id.soundToggle)
        bonusSoundToggle = findViewById(R.id.bonusSoundToggle)
        winningPointsButton = findViewById(R.id.winningPointsButton)
        historyButton = findViewById(R.id.historyButton)
        swapNamesButton = findViewById(R.id.swapNamesButton)
    }


    private fun saveNames() {
        val player1 = player1NameInput.text.toString().trim()
        val player2 = player2NameInput.text.toString().trim()

        if (player1.isEmpty() || player2.isEmpty()) {
            Toast.makeText(this, "Please enter names for both players", Toast.LENGTH_SHORT).show()
            return
        }

        if (player1 == player2) {
            Toast.makeText(this, "Player names must be different", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(keyPlayer1Name, player1)
        editor.putString(keyPlayer2Name, player2)
        editor.apply()

        player1Name.text = player1
        player2Name.text = player2

        // Switch back to display mode
        showNameDisplayMode()
        window.decorView.requestFocus()

        Toast.makeText(this, "Names saved!", Toast.LENGTH_SHORT).show()
    }

    private fun swapPlayerNames() {
        // Save current state if game is in progress
        if (gameStarted && !gameOver) {
            saveCurrentState()
        }

        // Swap the displayed names
        val tempName = player1Name.text.toString()
        player1Name.text = player2Name.text.toString()
        player2Name.text = tempName

        // Swap the input field names (for when editing)
        val tempInput = player1NameInput.text.toString()
        player1NameInput.setText(player2NameInput.text.toString())
        player2NameInput.setText(tempInput)

        // Also swap the scores and serve status to maintain game state
        val tempScore = scorePlayer1
        scorePlayer1 = scorePlayer2
        scorePlayer2 = tempScore

        // Swap serve status
        isPlayer1Serving = !isPlayer1Serving

        // Update the display
        updateScoreDisplay()

        // Save the swapped names to SharedPreferences
        saveSwappedNamesToPrefs()

        Toast.makeText(this, "Player names swapped!", Toast.LENGTH_SHORT).show()
    }

    private fun saveSwappedNamesToPrefs() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(keyPlayer1Name, player1Name.text.toString())
        editor.putString(keyPlayer2Name, player2Name.text.toString())
        editor.apply()
    }

    private fun showWinningPointsDialog() {
        val pointsOptions = arrayOf("3", "7", "11", "15", "21")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Winning Points")
        builder.setItems(pointsOptions) { dialog, which ->
            val selectedPoints = pointsOptions[which].toInt()
            setWinningPoints(selectedPoints)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun setWinningPoints(points: Int) {
        if (points == winningPoints) return

        // Save current state if game is in progress
        if (gameStarted && !gameOver) {
            saveCurrentState()
        }

        winningPoints = points
        winningPointsButton?.text = "Game to $points points"

        // Save to SharedPreferences
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(keyWinningPoints, points)
        editor.apply()

        // Check if current score meets new winning condition
        if (gameStarted && !gameOver) {
            checkIfGameWon()  // Use the updated method
        }

        Toast.makeText(this, "Game to $points points", Toast.LENGTH_SHORT).show()
    }

    private fun setupButtonListeners() {
        // Save names button
        saveNamesButton.setOnClickListener {
            saveNames()
        }

        // Player 1 scores a point
        player1Button.setOnClickListener {
            if (!gameOver) {
                // NEW: Start timer on first score if not already started
                if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                    startGameTimer()
                }
                gameStarted = true
                // Save current serve state before updating
                serveHistory.add(isPlayer1Serving)
                scorePlayer1++

                // Check if this point wins the game BEFORE updating serve status
                val wasGameWon = checkIfGameWon()

                updateServeStatus(true)
                updateScoreDisplay()

                // Only announce score if game didn't just end
                if (!wasGameWon) {
                    announceScore(true)  // For player 1
                }
            }
        }

        // Player 2 scores a point
        player2Button.setOnClickListener {
            if (!gameOver) {
                // NEW: Start timer on first score if not already started
                if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                    startGameTimer()
                }
                gameStarted = true
                // Save current serve state before updating
                serveHistory.add(isPlayer1Serving)
                scorePlayer2++

                // Check if this point wins the game BEFORE updating serve status
                val wasGameWon = checkIfGameWon()

                updateServeStatus(false)
                updateScoreDisplay()

                // Only announce score if game didn't just end
                if (!wasGameWon) {
                    announceScore(false) // For player 2
                }
            }
        }

        // Player 1 removes a point
        player1RemoveButton.setOnClickListener {
            if (!gameOver && scorePlayer1 > 0) {
                // Only remove point if score is above 0
                scorePlayer1--

                // Restore previous serve state if available
                if (serveHistory.isNotEmpty()) {
                    isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                }

                // If no history, just update display
                updateScoreDisplay()

                // If both scores are 0, reset game started state
                if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                    gameStarted = false
                }
            } else if (scorePlayer1 == 0) {
                Toast.makeText(this, "Score cannot go below 0", Toast.LENGTH_SHORT).show()
            }
        }

        // Player 2 removes a point
        player2RemoveButton.setOnClickListener {
            if (!gameOver && scorePlayer2 > 0) {
                // Only remove point if score is above 0
                scorePlayer2--

                // Restore previous serve state if available
                if (serveHistory.isNotEmpty()) {
                    isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                }

                // If no history, just update display
                updateScoreDisplay()

                // If both scores are 0, reset game started state
                if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                    gameStarted = false
                }
            } else if (scorePlayer2 == 0) {
                Toast.makeText(this, "Score cannot go below 0", Toast.LENGTH_SHORT).show()
            }
        }

        // Reset the game
        resetButton.setOnClickListener {
            resetGame()
        }

        // Add text watchers to enable/disable save button
        player1NameInput.addTextChangedListener {
            validateNameInputs()
        }

        player2NameInput.addTextChangedListener {
            validateNameInputs()
        }

        player1Name.setOnClickListener {
            showNameEditMode()
            player1NameInput.requestFocus()
        }

        player2Name.setOnClickListener {
            showNameEditMode()
            player2NameInput.requestFocus()
        }

        // Winning points button
        winningPointsButton.setOnClickListener {
            showWinningPointsDialog()
        }

        // Also make the score area clickable to return to game mode
        findViewById<View>(R.id.scoreContainer).setOnClickListener {
            showNameDisplayMode()
            window.decorView.requestFocus()
        }

        // Sound toggle listener
        soundToggle.setOnCheckedChangeListener { _, isChecked ->
            soundEnabled = isChecked
            val message = if (isChecked) "Sound enabled" else "Sound disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Optional: Play a test sound when enabling
            if (isChecked) {
                playScoreSound()
            }
        }

        // ADD THIS: Bonus sound toggle listener
        bonusSoundToggle.setOnCheckedChangeListener { _, isChecked ->
            bonusSoundEnabled = isChecked
            val message = if (isChecked) "Bonus sounds enabled" else "Bonus sounds disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Save to SharedPreferences
            val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putBoolean(keyBonusSoundEnabled, isChecked)
            editor.apply()
        }

        historyButton.setOnClickListener {
            showGameHistory()
        }

        // Swap names button
        swapNamesButton.setOnClickListener {
            swapPlayerNames()
        }
    }

    private fun validateNameInputs() {
        val player1 = player1NameInput.text.toString().trim()
        val player2 = player2NameInput.text.toString().trim()

        saveNamesButton.isEnabled = player1.isNotEmpty() &&
                player2.isNotEmpty() &&
                player1 != player2
    }

    private fun updateServeStatus(pointScoredByPlayer1: Boolean) {
        // In badminton, service changes when the server loses a point
        if (pointScoredByPlayer1 != isPlayer1Serving) {
            // The non-serving player scored, so service changes
            isPlayer1Serving = !isPlayer1Serving
        }
        // If the server scored, service continues with the same player
    }

    private fun updateScoreDisplay() {
        // Update score text
        player1ScoreText.text = scorePlayer1.toString()
        player2ScoreText.text = scorePlayer2.toString()

        // Update serve indicators
        serveIndicator1.visibility = if (isPlayer1Serving) View.VISIBLE else View.INVISIBLE
        serveIndicator2.visibility = if (isPlayer1Serving) View.INVISIBLE else View.VISIBLE

        // Update remove buttons state
        player1RemoveButton.isEnabled = scorePlayer1 > 0 && !gameOver
        player2RemoveButton.isEnabled = scorePlayer2 > 0 && !gameOver
    }

    private fun endGame() {
        gameOver = true
        player1Button.isEnabled = false
        player2Button.isEnabled = false
        player1RemoveButton.isEnabled = false
        player2RemoveButton.isEnabled = false

        val winner = if (scorePlayer1 > scorePlayer2) player1Name.text else player2Name.text
        Toast.makeText(this, "$winner Wins! (First to $winningPoints)", Toast.LENGTH_LONG).show()

        // Save game to history
        saveGameToHistory()

        // Play victory sound when game is won
        playScoreSound()

        // Update button text to indicate game over
        player1Button.text = "Game Over"
        player2Button.text = "Game Over"
    }

    private fun resetGame() {
        // Save current state before reset
        if (gameStarted || scorePlayer1 > 0 || scorePlayer2 > 0) {
            saveCurrentState()
        }

        // Reset all game state
        scorePlayer1 = 0
        scorePlayer2 = 0
        isPlayer1Serving = true
        gameStarted = false
        gameOver = false
        serveHistory.clear()

        // Re-enable buttons and reset text
        player1Button.isEnabled = true
        player2Button.isEnabled = true
        player1RemoveButton.isEnabled = true
        player2RemoveButton.isEnabled = true
        player1Button.text = "+1"
        player2Button.text = "+1"

        // NEW: Start timer for new game
        startGameTimer()
        isNewGameJustStarted = true

        // Update display
        updateScoreDisplay()

        Toast.makeText(this, "New game to $winningPoints points!", Toast.LENGTH_SHORT).show()
    }
}