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
import android.view.LayoutInflater

data class GameState(
    val scorePlayer1: Int,
    val scorePlayer2: Int,
    val isPlayer1Serving: Boolean,
    val serveHistory: List<Boolean> = emptyList(),
    val team1ServePosition: Int = 0,
    val team2ServePosition: Int = 0,
    val isDoublesMode: Boolean = false,
    val team1CurrentEvenPlayer: Int = 0,  // Add this
    val team2CurrentEvenPlayer: Int = 0   // Add this
)

data class PlayerNames(
    val team1Player1: String = "Player 1",
    val team1Player2: String = "Player 2",
    val team2Player1: String = "Player 3",
    val team2Player2: String = "Player 4",
    val isDoubles: Boolean = false,
    // Simplified serving configuration
    val team1EvenPlayer: Int = 0, // 0 for Player1, 1 for Player2
    val team2EvenPlayer: Int = 0, // 0 for Player1, 1 for Player2
    val firstServeTeam: Int = 1   // 1 for Team 1, 2 for Team 2
)

data class GameHistory(
    val playerNames: PlayerNames,
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
    private lateinit var soundToggle: SwitchCompat
    private lateinit var bonusSoundToggle: SwitchCompat
    private lateinit var winningPointsButton: Button
    private lateinit var historyButton: Button
    private lateinit var swapNamesButton: Button
    private lateinit var doublesModeToggle: SwitchCompat

    // Game state variables
    private var scorePlayer1 = 0
    private var scorePlayer2 = 0
    private var isPlayer1Serving = true
    private var gameStarted = false
    private var gameOver = false
    private var isDoublesMode = false
    private var team1ServePosition = 0
    private var team2ServePosition = 0

    // Track serve history for proper undo functionality
    private val serveHistory = mutableListOf<Boolean>()

    // SharedPreferences keys
    private val prefsName = "BadmintonScorePrefs"
    private val keyPlayer1Name = "player1Name"
    private val keyPlayer2Name = "player2Name"
    private val keyBonusSoundEnabled = "bonusSoundEnabled"
    private val keyWinningPoints = "winningPoints"

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
    private val minSwipeDistance = 100f

    // Sound variables
    private lateinit var scoreSoundPlayer: MediaPlayer
    private var soundEnabled = true
    private var bonusSoundEnabled = true
    private val soundResources = listOf(
        R.raw.score_sound_1,
        R.raw.score_sound_2,
        R.raw.score_sound_3
    )

    // Text-to-Speech variables
    private lateinit var textToSpeech: TextToSpeech
    private var ttsInitialized = false

    // Game configuration variables
    private var winningPoints = 21
    private val maxPoints = 30

    // Game history variables
    private val gameHistory = mutableListOf<GameHistory>()
    private val prefsHistoryKey = "gameHistory"
    private val maxHistorySize = 150

    // Game timing variables
    private var gameStartTime: Long = 0
    private var gameEndTime: Long = 0
    private var isNewGameJustStarted = false

    private var playerNames = PlayerNames()

    private var currentLayout: View? = null

    private lateinit var nameInputLayout: LinearLayout
    private lateinit var teamNamesLayout: LinearLayout
    private lateinit var player1Header: LinearLayout
    private lateinit var player2Header: LinearLayout
    private lateinit var team1Header: LinearLayout
    private lateinit var team2Header: LinearLayout
    private lateinit var team1ServeIndicator: TextView
    private lateinit var team2ServeIndicator: TextView
    private lateinit var team1ServeIcon: ImageView
    private lateinit var team2ServeIcon: ImageView
    private lateinit var team1Name: TextView
    private lateinit var team2Name: TextView
    private var team1CurrentEvenPlayer: Int = 0 // Tracks who is CURRENTLY on even side
    private var team2CurrentEvenPlayer: Int = 0 // Tracks who is CURRENTLY on even side

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Bluetooth event handling
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        hideSystemUI()
        initializeViews()
        loadSavedNames()
        setupButtonListeners()
        updateScoreDisplay()
        setupKeyboardFocus()
        setupSwipeDetection()
        initializeSound()
        initializeTextToSpeech()
        loadHistoryFromPrefs()
        checkAndRequestBluetoothPermissions()
        checkAndStartGameTimer()
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
        doublesModeToggle = findViewById(R.id.doublesModeToggle)

        // NEW: Doubles mode views
        nameInputLayout = findViewById(R.id.nameInputLayout)
        teamNamesLayout = findViewById(R.id.teamNamesLayout)
        player1Header = findViewById(R.id.player1Header)
        player2Header = findViewById(R.id.player2Header)
        team1Header = findViewById(R.id.team1Header)
        team2Header = findViewById(R.id.team2Header)
        team1ServeIndicator = findViewById(R.id.team1ServeIndicator)
        team2ServeIndicator = findViewById(R.id.team2ServeIndicator)
        team1ServeIcon = findViewById(R.id.team1ServeIcon)
        team2ServeIcon = findViewById(R.id.team2ServeIcon)
        team1Name = findViewById(R.id.team1Name)
        team2Name = findViewById(R.id.team2Name)
    }

    private fun setupButtonListeners() {
        saveNamesButton.setOnClickListener {
            saveNames()
        }

        doublesModeToggle.setOnCheckedChangeListener { _, isChecked ->
            isDoublesMode = isChecked
            updateLayoutForMode()

            Toast.makeText(this, "Doubles mode ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        player1Button.setOnClickListener {
            if (!gameOver) {
                if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                    startGameTimer()
                }
                saveCurrentState()
                gameStarted = true
                serveHistory.add(isPlayer1Serving)
                scorePlayer1++
                val wasGameWon = checkIfGameWon()
                updateServeStatus(true)
                updateScoreDisplay()
                if (!wasGameWon) {
                    announceScore(true)
                }
            }
        }

        player2Button.setOnClickListener {
            if (!gameOver) {
                if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                    startGameTimer()
                }
                saveCurrentState()
                gameStarted = true
                serveHistory.add(isPlayer1Serving)
                scorePlayer2++
                val wasGameWon = checkIfGameWon()
                updateServeStatus(false)
                updateScoreDisplay()
                if (!wasGameWon) {
                    announceScore(false)
                }
            }
        }

        player1RemoveButton.setOnClickListener {
            if (!gameOver && scorePlayer1 > 0) {
                saveCurrentState()
                scorePlayer1--
                if (serveHistory.isNotEmpty()) {
                    isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                }
                updateScoreDisplay()
                if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                    gameStarted = false
                }
            }
        }

        player2RemoveButton.setOnClickListener {
            if (!gameOver && scorePlayer2 > 0) {
                saveCurrentState()
                scorePlayer2--
                if (serveHistory.isNotEmpty()) {
                    isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                }
                updateScoreDisplay()
                if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                    gameStarted = false
                }
            }
        }

        resetButton.setOnClickListener {
            resetGame()
        }

        player1NameInput.addTextChangedListener {
            validateNameInputs()
        }

        player2NameInput.addTextChangedListener {
            validateNameInputs()
        }

        player1Name.setOnClickListener {
            if (isDoublesMode) {
                showNameEntryDialog()
            } else {
                showNameEditMode()
                player1NameInput.requestFocus()
            }
        }

        player2Name.setOnClickListener {
            if (isDoublesMode) {
                showNameEntryDialog()
            } else {
                showNameEditMode()
                player2NameInput.requestFocus()
            }
        }

        // Also update the team name click listeners
        team1Header.setOnClickListener {
            if (isDoublesMode) {
                showNameEntryDialog()
            }
        }

        team2Header.setOnClickListener {
            if (isDoublesMode) {
                showNameEntryDialog()
            }
        }

        // Make sure the team names themselves are also clickable
        team1Name.setOnClickListener {
            if (isDoublesMode) {
                showNameEntryDialog()
            }
        }

        team2Name.setOnClickListener {
            if (isDoublesMode) {
                showNameEntryDialog()
            }
        }

        winningPointsButton.setOnClickListener {
            showWinningPointsDialog()
        }

        findViewById<View>(R.id.scoreContainer).setOnClickListener {
            showNameDisplayMode()
            window.decorView.requestFocus()
        }

        soundToggle.setOnCheckedChangeListener { _, isChecked ->
            soundEnabled = isChecked
            Toast.makeText(this, "Sound ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            if (isChecked) {
                playScoreSound()
            }
        }

        bonusSoundToggle.setOnCheckedChangeListener { _, isChecked ->
            bonusSoundEnabled = isChecked
            Toast.makeText(this, "Bonus sounds ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
            prefs.edit().putBoolean(keyBonusSoundEnabled, isChecked).apply()
        }

        historyButton.setOnClickListener {
            showGameHistory()
        }

        swapNamesButton.setOnClickListener {
            swapPlayerNames()
        }
    }

    private fun updateLayoutForMode() {
        if (isDoublesMode) {
            // Show doubles layout
            nameInputLayout.visibility = View.GONE
            teamNamesLayout.visibility = View.VISIBLE
            player1Header.visibility = View.GONE
            player2Header.visibility = View.GONE
            team1Header.visibility = View.VISIBLE
            team2Header.visibility = View.VISIBLE

            // Update the team names display at the top
            val teamNamesDisplay1 = findViewById<TextView>(R.id.teamNamesDisplay1)
            val teamNamesDisplay2 = findViewById<TextView>(R.id.teamNamesDisplay2)
            teamNamesDisplay1.text = "${playerNames.team1Player1} / ${playerNames.team1Player2}"
            teamNamesDisplay2.text = "${playerNames.team2Player1} / ${playerNames.team2Player2}"

            // Update swap button text
            swapNamesButton.text = "Swap Teams"

            // Update help text
            val keyHelpText = findViewById<TextView>(R.id.keyHelpText)
            keyHelpText.text = "Left=Team1+1, Right=Team2+1, Up=NG, Down=Undo, +=Team1-1, -=Team2-1, R-Hold=Swap Serve"

            // Set default team names if not already set
            if (playerNames.team1Player2 == "Player 2" && playerNames.team2Player2 == "Player 4") {
                // This means we just switched to doubles and need default team names
                playerNames = playerNames.copy(
                    team1Player1 = playerNames.team1Player1,
                    team1Player2 = "Player 2",
                    team2Player1 = playerNames.team2Player1,
                    team2Player2 = "Player 4",
                    isDoubles = true
                )
            }
        } else {
            // Show singles layout
            nameInputLayout.visibility = View.VISIBLE
            teamNamesLayout.visibility = View.GONE
            player1Header.visibility = View.VISIBLE
            player2Header.visibility = View.VISIBLE
            team1Header.visibility = View.GONE
            team2Header.visibility = View.GONE

            // Update swap button text
            swapNamesButton.text = "Swap Names"

            // Update help text
            val keyHelpText = findViewById<TextView>(R.id.keyHelpText)
            keyHelpText.text = "Left=P1+1, Right=P2+1, Up=NG, Down=Undo, +=P1-1, -=P2-1, R-Hold=Swap Serve"

            // Convert back to singles names
            playerNames = playerNames.copy(
                team1Player1 = playerNames.team1Player1,
                team2Player1 = playerNames.team2Player1,
                isDoubles = false
            )
        }

        updatePlayerDisplay()
    }

    // Core game methods
    private fun updateServeStatus(pointScoredByPlayer1: Boolean) {
        if (!isDoublesMode) {
            // Singles mode logic (existing)
            if (pointScoredByPlayer1 != isPlayer1Serving) {
                isPlayer1Serving = !isPlayer1Serving
            }
            return
        }

        // Doubles mode logic - WITH POSITION TRACKING
        if (pointScoredByPlayer1) {
            // Team 1 scored
            if (isPlayer1Serving) {
                // Team 1 was serving and scored - players swap sides
                team1CurrentEvenPlayer = (team1CurrentEvenPlayer + 1) % 2
            } else {
                // Team 1 was receiving and scored - they win serve back
                isPlayer1Serving = true
                // Team 1 serves from the side based on their score
                team1ServePosition = if (scorePlayer1 % 2 == 0) {
                    team1CurrentEvenPlayer  // Even side player serves
                } else {
                    (team1CurrentEvenPlayer + 1) % 2  // Odd side player serves
                }
            }
        } else {
            // Team 2 scored
            if (!isPlayer1Serving) {
                // Team 2 was serving and scored - players swap sides
                team2CurrentEvenPlayer = (team2CurrentEvenPlayer + 1) % 2
            } else {
                // Team 2 was receiving and scored - they win serve back
                isPlayer1Serving = false
                // Team 2 serves from the side based on their score
                team2ServePosition = if (scorePlayer2 % 2 == 0) {
                    team2CurrentEvenPlayer  // Even side player serves
                } else {
                    (team2CurrentEvenPlayer + 1) % 2  // Odd side player serves
                }
            }
        }
    }

    private fun getCurrentServerName(): String {
        if (!isDoublesMode) {
            return if (isPlayer1Serving) playerNames.team1Player1 else playerNames.team2Player1
        }

        return if (isPlayer1Serving) {
            if (team1ServePosition == 0) playerNames.team1Player1 else playerNames.team1Player2
        } else {
            if (team2ServePosition == 0) playerNames.team2Player1 else playerNames.team2Player2
        }
    }

    private fun updateScoreDisplay() {
        player1ScoreText.text = scorePlayer1.toString()
        player2ScoreText.text = scorePlayer2.toString()

        // Update the serve indicators
        updatePlayerDisplay() // ADD THIS LINE

        player1RemoveButton.isEnabled = scorePlayer1 > 0 && !gameOver
        player2RemoveButton.isEnabled = scorePlayer2 > 0 && !gameOver
    }

    private fun updatePlayerDisplay() {
        if (isDoublesMode) {
            // For doubles mode
            team1Name.text = "${playerNames.team1Player1} / ${playerNames.team1Player2}"
            team2Name.text = "${playerNames.team2Player1} / ${playerNames.team2Player2}"

            val servingPlayer = getCurrentServerName()
            val receivingPlayer = getCurrentReceiverName()

            if (isPlayer1Serving) {
                team1ServeIndicator.text = "Serving: $servingPlayer"
                team2ServeIndicator.text = "Receiving: $receivingPlayer"
                team1ServeIcon.visibility = View.VISIBLE
                team2ServeIcon.visibility = View.INVISIBLE
            } else {
                team1ServeIndicator.text = "Receiving: $receivingPlayer"
                team2ServeIndicator.text = "Serving: $servingPlayer"
                team1ServeIcon.visibility = View.INVISIBLE
                team2ServeIcon.visibility = View.VISIBLE
            }
        } else {
            // Singles mode
            player1Name.text = playerNames.team1Player1.trim()
            player2Name.text = playerNames.team2Player1.trim()

            // Clear doubles serving indicators
            team1ServeIndicator.text = ""
            team2ServeIndicator.text = ""
            team1ServeIcon.visibility = View.INVISIBLE
            team2ServeIcon.visibility = View.INVISIBLE

            serveIndicator1.visibility = if (isPlayer1Serving) View.VISIBLE else View.INVISIBLE
            serveIndicator2.visibility = if (isPlayer1Serving) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun getCurrentReceiverName(): String {
        if (!isDoublesMode) {
            return if (isPlayer1Serving) playerNames.team2Player1 else playerNames.team1Player1
        }

        // Receiver is always diagonal to server - determined by serving team's score
        // Same logic as serving: even score = even side, odd score = odd side
        return if (isPlayer1Serving) {
            // Team 1 is serving - receiver is on Team 2
            if (scorePlayer1 % 2 == 0) {
                // Team 1 has even score - server is on even side, so receiver is on even side
                if (team2CurrentEvenPlayer == 0) playerNames.team2Player1 else playerNames.team2Player2
            } else {
                // Team 1 has odd score - server is on odd side, so receiver is on odd side
                if (team2CurrentEvenPlayer == 0) playerNames.team2Player2 else playerNames.team2Player1
            }
        } else {
            // Team 2 is serving - receiver is on Team 1
            if (scorePlayer2 % 2 == 0) {
                // Team 2 has even score - server is on even side, so receiver is on even side
                if (team1CurrentEvenPlayer == 0) playerNames.team1Player1 else playerNames.team1Player2
            } else {
                // Team 2 has odd score - server is on odd side, so receiver is on odd side
                if (team1CurrentEvenPlayer == 0) playerNames.team1Player2 else playerNames.team1Player1
            }
        }
    }

    private fun checkIfGameWon(): Boolean {
        if (scorePlayer1 >= winningPoints || scorePlayer2 >= winningPoints) {
            val pointDifference = Math.abs(scorePlayer1 - scorePlayer2)
            if (pointDifference >= 2) {
                if (!gameOver) {
                    endGame()
                }
                return true
            } else if (scorePlayer1 == maxPoints || scorePlayer2 == maxPoints) {
                if (!gameOver) {
                    endGame()
                }
                return true
            }
        }
        return false
    }

    private fun endGame() {
        gameOver = true
        player1Button.isEnabled = false
        player2Button.isEnabled = false
        player1RemoveButton.isEnabled = false
        player2RemoveButton.isEnabled = false

        // FIXED: Use correct team name in doubles mode
        val winner = if (scorePlayer1 > scorePlayer2) {
            if (isDoublesMode) "${playerNames.team1Player1}/${playerNames.team1Player2}" else player1Name.text
        } else {
            if (isDoublesMode) "${playerNames.team2Player1}/${playerNames.team2Player2}" else player2Name.text
        }

        Toast.makeText(this, "$winner Wins! (First to $winningPoints)", Toast.LENGTH_LONG).show()

        saveGameToHistory()
        playScoreSound()

        player1Button.text = "Game Over"
        player2Button.text = "Game Over"
    }

    private fun resetGame() {
        if (gameStarted || scorePlayer1 > 0 || scorePlayer2 > 0) {
            saveCurrentState()
        }

        scorePlayer1 = 0
        scorePlayer2 = 0
        gameStarted = false
        gameOver = false
        serveHistory.clear()

        // Reset serve positions based on doubles mode
        if (isDoublesMode) {
            // Use the configured serve setup from playerNames
            isPlayer1Serving = (playerNames.firstServeTeam == 1)

            // Reset current positions based on who is configured as even side
            team1CurrentEvenPlayer = playerNames.team1EvenPlayer
            team2CurrentEvenPlayer = playerNames.team2EvenPlayer

            if (isPlayer1Serving) {
                // Team 1 serves first - even side player serves
                team1ServePosition = playerNames.team1EvenPlayer
                team2ServePosition = 0 // Team 2 not serving yet
            } else {
                // Team 2 serves first - even side player serves
                team2ServePosition = playerNames.team2EvenPlayer
                team1ServePosition = 0 // Team 1 not serving yet
            }
        } else {
            // Singles mode - default to Team 1 serving
            isPlayer1Serving = true
            team1ServePosition = 0
            team2ServePosition = 0
            team1CurrentEvenPlayer = 0
            team2CurrentEvenPlayer = 0
        }

        player1Button.isEnabled = true
        player2Button.isEnabled = true
        player1RemoveButton.isEnabled = true
        player2RemoveButton.isEnabled = true
        player1Button.text = "+1"
        player2Button.text = "+1"

        startGameTimer()
        isNewGameJustStarted = true
        updateScoreDisplay()

        Toast.makeText(this, "New game to $winningPoints points!", Toast.LENGTH_SHORT).show()
    }

    // Name management methods
    private fun showNameDisplayMode() {
        player1Name.visibility = View.VISIBLE
        player2Name.visibility = View.VISIBLE
        player1NameInput.visibility = View.GONE
        player2NameInput.visibility = View.GONE
        saveNamesButton.visibility = View.GONE
    }

    private fun showNameEditMode() {
        player1Name.visibility = View.GONE
        player2Name.visibility = View.GONE
        player1NameInput.visibility = View.VISIBLE
        player2NameInput.visibility = View.VISIBLE
        saveNamesButton.visibility = View.VISIBLE
    }

    private fun validateNameInputs() {
        val player1 = player1NameInput.text.toString().trim()
        val player2 = player2NameInput.text.toString().trim()
        saveNamesButton.isEnabled = player1.isNotEmpty() && player2.isNotEmpty() && player1 != player2
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
        prefs.edit().apply {
            putString(keyPlayer1Name, player1)
            putString(keyPlayer2Name, player2)
            apply()
        }

        playerNames = PlayerNames(
            team1Player1 = player1,
            team2Player1 = player2,
            isDoubles = false
        )

        player1Name.text = player1
        player2Name.text = player2

        showNameDisplayMode()
        window.decorView.requestFocus()
        Toast.makeText(this, "Names saved!", Toast.LENGTH_SHORT).show()
    }

    // Doubles name management
    private fun showNameEntryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.player_names_dialog, null)

        // Initialize name inputs (existing code)
        val team1Player1Input = dialogView.findViewById<EditText>(R.id.team1Player1Input)
        val team1Player2Input = dialogView.findViewById<EditText>(R.id.team1Player2Input)
        val team2Player1Input = dialogView.findViewById<EditText>(R.id.team2Player1Input)
        val team2Player2Input = dialogView.findViewById<EditText>(R.id.team2Player2Input)

        team1Player1Input.setText(playerNames.team1Player1)
        team1Player2Input.setText(playerNames.team1Player2)
        team2Player1Input.setText(playerNames.team2Player1)
        team2Player2Input.setText(playerNames.team2Player2)

        // Initialize serving configuration
        val team1Player1Even = dialogView.findViewById<RadioButton>(R.id.team1Player1Even)
        val team1Player2Even = dialogView.findViewById<RadioButton>(R.id.team1Player2Even)
        val team2Player1Even = dialogView.findViewById<RadioButton>(R.id.team2Player1Even)
        val team2Player2Even = dialogView.findViewById<RadioButton>(R.id.team2Player2Even)
        val team1FirstServe = dialogView.findViewById<RadioButton>(R.id.team1FirstServe)
        val team2FirstServe = dialogView.findViewById<RadioButton>(R.id.team2FirstServe)

        // Set current values
        if (playerNames.team1EvenPlayer == 0) {
            team1Player1Even.isChecked = true
        } else {
            team1Player2Even.isChecked = true
        }

        if (playerNames.team2EvenPlayer == 0) {
            team2Player1Even.isChecked = true
        } else {
            team2Player2Even.isChecked = true
        }

        if (playerNames.firstServeTeam == 1) {
            team1FirstServe.isChecked = true
        } else {
            team2FirstServe.isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Doubles Team Names")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, which ->
                // Save names
                playerNames = PlayerNames(
                    team1Player1 = team1Player1Input.text.toString().trim(),
                    team1Player2 = team1Player2Input.text.toString().trim(),
                    team2Player1 = team2Player1Input.text.toString().trim(),
                    team2Player2 = team2Player2Input.text.toString().trim(),
                    isDoubles = true,
                    team1EvenPlayer = if (team1Player1Even.isChecked) 0 else 1,
                    team2EvenPlayer = if (team2Player1Even.isChecked) 0 else 1,
                    firstServeTeam = if (team1FirstServe.isChecked) 1 else 2
                )

                // Set default names if empty
                if (playerNames.team1Player1.isEmpty()) playerNames = playerNames.copy(team1Player1 = "P1")
                if (playerNames.team1Player2.isEmpty()) playerNames = playerNames.copy(team1Player2 = "P2")
                if (playerNames.team2Player1.isEmpty()) playerNames = playerNames.copy(team2Player1 = "P3")
                if (playerNames.team2Player2.isEmpty()) playerNames = playerNames.copy(team2Player2 = "P4")

                savePlayerNamesToPrefs()
                initializeServingState()
                updatePlayerDisplay()
                Toast.makeText(this, "Doubles names and serve setup saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun initializeServingState() {
        if (!isDoublesMode) return

        // Determine which team serves first
        isPlayer1Serving = (playerNames.firstServeTeam == 1)

        // Initialize current positions based on who started as even side
        team1CurrentEvenPlayer = playerNames.team1EvenPlayer
        team2CurrentEvenPlayer = playerNames.team2EvenPlayer

        if (isPlayer1Serving) {
            team1ServePosition = team1CurrentEvenPlayer  // Even side serves first
            team2ServePosition = 0
        } else {
            team2ServePosition = team2CurrentEvenPlayer  // Even side serves first
            team1ServePosition = 0
        }
    }

    private fun savePlayerNamesToPrefs() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = Gson().toJson(playerNames)
        prefs.edit().putString("playerNames", json).apply()
    }

    private fun loadPlayerNamesFromPrefs() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = prefs.getString("playerNames", null)
        if (!json.isNullOrEmpty()) {
            try {
                playerNames = Gson().fromJson(json, PlayerNames::class.java)
                isDoublesMode = playerNames.isDoubles
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading player names: ${e.message}")
                playerNames = PlayerNames()
            }
        }
        updatePlayerDisplay()
    }

    // Swap names functionality
    private fun swapPlayerNames() {
        if (gameStarted && !gameOver) {
            saveCurrentState()
        }

        if (isDoublesMode) {
            // Swap team names and all serve configurations
            playerNames = playerNames.copy(
                team1Player1 = playerNames.team2Player1,
                team1Player2 = playerNames.team2Player2,
                team2Player1 = playerNames.team1Player1,
                team2Player2 = playerNames.team1Player2,
                // Swap even side player assignments
                team1EvenPlayer = playerNames.team2EvenPlayer,
                team2EvenPlayer = playerNames.team1EvenPlayer,
                // Swap first serve team
                firstServeTeam = if (playerNames.firstServeTeam == 1) 2 else 1
            )

            // Swap serving team and positions
            isPlayer1Serving = !isPlayer1Serving

            // Swap serve positions
            val tempPosition = team1ServePosition
            team1ServePosition = team2ServePosition
            team2ServePosition = tempPosition

            // Swap current even players
            val tempCurrentEven = team1CurrentEvenPlayer
            team1CurrentEvenPlayer = team2CurrentEvenPlayer
            team2CurrentEvenPlayer = tempCurrentEven
        } else {
            // Singles mode - swap player names only
            playerNames = playerNames.copy(
                team1Player1 = playerNames.team2Player1,
                team2Player1 = playerNames.team1Player1
            )

            // Swap serving
            isPlayer1Serving = !isPlayer1Serving
        }

        updatePlayerDisplay()
        savePlayerNamesToPrefs()
        Toast.makeText(this, "Teams swapped!", Toast.LENGTH_SHORT).show()
    }

    // Winning points methods
    private fun showWinningPointsDialog() {
        val pointsOptions = arrayOf("3", "7", "11", "15", "21")
        AlertDialog.Builder(this)
            .setTitle("Select Winning Points")
            .setItems(pointsOptions) { dialog, which ->
                setWinningPoints(pointsOptions[which].toInt())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setWinningPoints(points: Int) {
        if (points == winningPoints) return

        if (gameStarted && !gameOver) {
            saveCurrentState()
        }

        winningPoints = points
        winningPointsButton.text = "Game to $points points"

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit().putInt(keyWinningPoints, points).apply()

        if (gameStarted && !gameOver) {
            checkIfGameWon()
        }

        Toast.makeText(this, "Game to $points points", Toast.LENGTH_SHORT).show()
    }

    // Undo functionality
    private fun saveCurrentState() {
        undoStack.add(GameState(scorePlayer1, scorePlayer2, isPlayer1Serving,
            ArrayList(serveHistory), team1ServePosition, team2ServePosition, isDoublesMode,
            team1CurrentEvenPlayer, team2CurrentEvenPlayer))  // Add these
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    // Keyboard and swipe handlers
    private fun setupKeyboardFocus() {
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

    private fun clearEditTextFocus() {
        player1NameInput.clearFocus()
        player2NameInput.clearFocus()
    }

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
        val contentView = window.decorView.findViewById<View>(android.R.id.content)
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
                    swipeStartX = event.x
                    swipeStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val endX = event.x
                    val endY = event.y
                    var deltaX = endX - swipeStartX
                    var deltaY = swipeStartY - endY

                    val orientation = resources.configuration.orientation
                    val rotation = windowManager.defaultDisplay.rotation

                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        when (rotation) {
                            Surface.ROTATION_90 -> {
                                val temp = deltaY
                                deltaY = -deltaX
                                deltaX = temp
                            }
                            Surface.ROTATION_270 -> {
                                val temp = deltaY
                                deltaY = deltaX
                                deltaX = -temp
                            }
                        }
                    }

                    if (Math.abs(deltaX) > minSwipeDistance && Math.abs(deltaX) > Math.abs(deltaY)) {
                        if (deltaX > 0) {
                            handleKeyPress(keyPlayer1Score)
                        } else {
                            handleKeyPress(keyPlayer2Score)
                        }
                    } else if (Math.abs(deltaY) > minSwipeDistance && Math.abs(deltaY) > Math.abs(deltaX)) {
                        if (deltaY > 0) {
                            handleKeyPress(keyUndoLast)
                        } else {
                            handleKeyPress(keyResetGame)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        viewsToListen.forEach { view ->
            view.setOnTouchListener(swipeListener)
        }
    }

    private fun handleKeyPress(keyCode: Int) {
        when (keyCode) {
            keyPlayer1Score -> {
                if (!gameOver) {
                    if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                        startGameTimer()
                    }
                    saveCurrentState()
                    gameStarted = true
                    serveHistory.add(isPlayer1Serving)
                    scorePlayer1++
                    val wasGameWon = checkIfGameWon()
                    updateServeStatus(true)
                    updateScoreDisplay()

                    // FIXED: Use correct team name in doubles mode
                    val teamName = if (isDoublesMode) {
                        "${playerNames.team1Player1}/${playerNames.team1Player2}"
                    } else {
                        player1Name.text.toString()
                    }
                    showKeyPressFeedback("$teamName scored!")

                    if (!wasGameWon) {
                        announceScore(true)
                    }
                }
            }
            keyPlayer2Score -> {
                if (!gameOver) {
                    if (!gameStarted && scorePlayer1 == 0 && scorePlayer2 == 0) {
                        startGameTimer()
                    }
                    saveCurrentState()
                    gameStarted = true
                    serveHistory.add(isPlayer1Serving)
                    scorePlayer2++
                    val wasGameWon = checkIfGameWon()
                    updateServeStatus(false)
                    updateScoreDisplay()

                    // FIXED: Use correct team name in doubles mode
                    val teamName = if (isDoublesMode) {
                        "${playerNames.team2Player1}/${playerNames.team2Player2}"
                    } else {
                        player2Name.text.toString()
                    }
                    showKeyPressFeedback("$teamName scored!")

                    if (!wasGameWon) {
                        announceScore(false)
                    }
                }
            }
            keyPlayer1Remove -> {
                if (!gameOver && scorePlayer1 > 0) {
                    saveCurrentState()
                    scorePlayer1--
                    if (serveHistory.isNotEmpty()) {
                        isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                    }
                    updateScoreDisplay()
                    if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                        gameStarted = false
                    }

                    // FIXED: Use correct team name in doubles mode
                    val teamName = if (isDoublesMode) {
                        "${playerNames.team1Player1}/${playerNames.team1Player2}"
                    } else {
                        player1Name.text.toString()
                    }
                    showKeyPressFeedback("$teamName point removed")

                }
            }
            keyPlayer2Remove -> {
                if (!gameOver && scorePlayer2 > 0) {
                    saveCurrentState()
                    scorePlayer2--
                    if (serveHistory.isNotEmpty()) {
                        isPlayer1Serving = serveHistory.removeAt(serveHistory.size - 1)
                    }
                    updateScoreDisplay()
                    if (scorePlayer1 == 0 && scorePlayer2 == 0) {
                        gameStarted = false
                    }

                    // FIXED: Use correct team name in doubles mode
                    val teamName = if (isDoublesMode) {
                        "${playerNames.team2Player1}/${playerNames.team2Player2}"
                    } else {
                        player2Name.text.toString()
                    }
                    showKeyPressFeedback("$teamName point removed")

                }
            }
            keyResetGame -> {
                saveCurrentState()
                resetGame()
                showKeyPressFeedback("Game reset!")
            }
            keySwapServe -> {
                if (!gameOver && isDoublesMode) {
                    // In doubles mode, swap serve between teams
                    isPlayer1Serving = !isPlayer1Serving
                    // Reset serve positions when swapping teams
                    team1ServePosition = 0
                    team2ServePosition = 0
                    updateScoreDisplay()

                    // FIXED: Use correct team name in doubles mode
                    val servingTeam = if (isPlayer1Serving) {
                        "${playerNames.team1Player1}/${playerNames.team1Player2}"
                    } else {
                        "${playerNames.team2Player1}/${playerNames.team2Player2}"
                    }
                    showKeyPressFeedback("Serve: $servingTeam")
                } else if (!gameOver) {
                    // Singles mode logic
                    isPlayer1Serving = !isPlayer1Serving
                    updateScoreDisplay()
                    val serverName = if (isPlayer1Serving) player1Name.text else player2Name.text
                    showKeyPressFeedback("Serve: $serverName")
                }
            }
            keyUndoLast -> {
                if (undoStack.isNotEmpty()) {
                    val previousState = undoStack.removeAt(undoStack.size - 1)
                    scorePlayer1 = previousState.scorePlayer1
                    scorePlayer2 = previousState.scorePlayer2
                    isPlayer1Serving = previousState.isPlayer1Serving
                    serveHistory.clear()
                    serveHistory.addAll(previousState.serveHistory)
                    team1ServePosition = previousState.team1ServePosition
                    team2ServePosition = previousState.team2ServePosition
                    isDoublesMode = previousState.isDoublesMode
                    team1CurrentEvenPlayer = previousState.team1CurrentEvenPlayer
                    team2CurrentEvenPlayer = previousState.team2CurrentEvenPlayer

                    gameOver = false
                    player1Button.isEnabled = true
                    player2Button.isEnabled = true
                    player1RemoveButton.isEnabled = true
                    player2RemoveButton.isEnabled = true
                    player1Button.text = "+1"
                    player2Button.text = "+1"

                    updateScoreDisplay()
                    showKeyPressFeedback("Undo last action")
                } else {
                    showKeyPressFeedback("Nothing to undo")
                }
            }
            KeyEvent.KEYCODE_NUMPAD_1 -> handleKeyPress(keyPlayer1Score)
            KeyEvent.KEYCODE_NUMPAD_2 -> handleKeyPress(keyPlayer2Score)
            KeyEvent.KEYCODE_NUMPAD_3 -> handleKeyPress(keyPlayer1Remove)
            KeyEvent.KEYCODE_NUMPAD_4 -> handleKeyPress(keyPlayer2Remove)
            KeyEvent.KEYCODE_NUMPAD_5 -> handleKeyPress(keyResetGame)
            KeyEvent.KEYCODE_NUMPAD_6 -> handleKeyPress(keySwapServe)
            KeyEvent.KEYCODE_NUMPAD_7 -> handleKeyPress(keyUndoLast)
        }
    }

    private fun showKeyPressFeedback(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(50)
            }
        }
    }

    // Sound and TTS methods
    private fun initializeSound() {
        try {
            scoreSoundPlayer = MediaPlayer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                scoreSoundPlayer.setAudioAttributes(audioAttributes)
            }
            scoreSoundPlayer.setOnCompletionListener {
                it.reset()
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
                }
            } else {
                Log.e("TTS", "Text-to-Speech initialization failed")
                ttsInitialized = false
            }
        }
        textToSpeech.setSpeechRate(0.7f)
        textToSpeech.setPitch(0.9f)
    }

    private fun playScoreSound() {
        if (!soundEnabled) return
        try {
            val randomSoundRes = soundResources.random()
            scoreSoundPlayer.reset()
            val assetFileDescriptor = resources.openRawResourceFd(randomSoundRes)
            scoreSoundPlayer.setDataSource(
                assetFileDescriptor.fileDescriptor,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.length
            )
            assetFileDescriptor.close()
            scoreSoundPlayer.prepare()
            scoreSoundPlayer.start()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing random sound: ${e.message}")
        }
    }

    private fun playSixtyNineSound() {
        try {
            textToSpeech.speak("sixty-nine", TextToSpeech.QUEUE_FLUSH, null, null)
            if (soundEnabled) {
                val sixtyNineSoundPlayer = MediaPlayer()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    sixtyNineSoundPlayer.setAudioAttributes(audioAttributes)
                }
                val assetFileDescriptor = resources.openRawResourceFd(R.raw.quagmire_delay)
                sixtyNineSoundPlayer.setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()
                sixtyNineSoundPlayer.prepare()
                sixtyNineSoundPlayer.start()
                sixtyNineSoundPlayer.setOnCompletionListener {
                    it.release()
                }
            }
        } catch (e: Exception) {
            textToSpeech.speak("sixty-nine", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun playNineElevenSound() {
        try {
            textToSpeech.speak("nine-eleven", TextToSpeech.QUEUE_FLUSH, null, null)
            if (soundEnabled) {
                val nineElevenSoundPlayer = MediaPlayer()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    nineElevenSoundPlayer.setAudioAttributes(audioAttributes)
                }
                val assetFileDescriptor = resources.openRawResourceFd(R.raw.nine_eleven_sound)
                nineElevenSoundPlayer.setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()
                nineElevenSoundPlayer.prepare()
                nineElevenSoundPlayer.start()
                nineElevenSoundPlayer.setOnCompletionListener {
                    it.release()
                }
            }
        } catch (e: Exception) {
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
                servingPlayerName = getCurrentServerName()
                receivingPlayerName = getCurrentReceiverName()
            } else {
                servingScore = scorePlayer2
                receivingScore = scorePlayer1
                servingPlayerName = getCurrentServerName()
                receivingPlayerName = getCurrentReceiverName()
            }

            if (bonusSoundEnabled && isSixtyNineScore(servingScore, receivingScore)) {
                playSixtyNineSound()
                return
            }

            if (bonusSoundEnabled && isNineElevenScore(servingScore, receivingScore)) {
                playNineElevenSound()
                return
            }

            val announcement = if (isGamePointForScoringPlayer(isPlayer1Scored)) {
                "Game point, $servingScore - $receivingScore, $servingPlayerName serving $receivingPlayerName"
            } else {
                "$servingScore - $receivingScore, $servingPlayerName serving $receivingPlayerName"
            }

            textToSpeech.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
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

    private fun isGamePointForScoringPlayer(isPlayer1Scored: Boolean): Boolean {
        if (gameOver) return false
        val scoringPlayerScore = if (isPlayer1Scored) scorePlayer1 else scorePlayer2
        val opponentScore = if (isPlayer1Scored) scorePlayer2 else scorePlayer1
        if (scoringPlayerScore == 29) return true
        return wouldNextPointWin(scoringPlayerScore, opponentScore)
    }

    private fun wouldNextPointWin(playerScore: Int, opponentScore: Int): Boolean {
        val newPlayerScore = playerScore + 1
        if (newPlayerScore == 30) return true
        if (newPlayerScore >= winningPoints && newPlayerScore - opponentScore >= 2) {
            return true
        }
        val bothAtHighScores = playerScore >= winningPoints - 1 && opponentScore >= winningPoints - 1
        if (bothAtHighScores && newPlayerScore - opponentScore >= 2) {
            return true
        }
        return false
    }

    // Game history methods
    private fun saveGameToHistory() {
        if (scorePlayer1 == 0 && scorePlayer2 == 0) return

        val winner = if (scorePlayer1 > scorePlayer2) {
            if (isDoublesMode) "${playerNames.team1Player1}/${playerNames.team1Player2}" else playerNames.team1Player1
        } else {
            if (isDoublesMode) "${playerNames.team2Player1}/${playerNames.team2Player2}" else playerNames.team2Player1
        }

        val duration = stopGameTimer()

        val completedGame = GameHistory(
            playerNames = playerNames,
            player1Score = scorePlayer1,
            player2Score = scorePlayer2,
            winningPoints = winningPoints,
            winner = winner,
            startTime = gameStartTime,
            endTime = gameEndTime
        )

        gameHistory.add(completedGame)
        if (gameHistory.size > maxHistorySize) {
            gameHistory.removeAt(0)
        }
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

    // Utility methods
    private fun hideSystemUI() {
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

    private fun showSystemUI() {
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

    private fun loadSavedNames() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        winningPoints = prefs.getInt(keyWinningPoints, 21)
        bonusSoundEnabled = prefs.getBoolean(keyBonusSoundEnabled, true)

        loadPlayerNamesFromPrefs()

        winningPointsButton.text = "Game to ${winningPoints} points"
        soundToggle.isChecked = soundEnabled
        bonusSoundToggle.isChecked = bonusSoundEnabled
        doublesModeToggle.isChecked = isDoublesMode

        // Set the initial layout based on the loaded mode
        updateLayoutForMode()
    }

    private fun checkAndStartGameTimer() {
        if (scorePlayer1 == 0 && scorePlayer2 == 0 && !gameStarted && !gameOver) {
            startGameTimer()
            isNewGameJustStarted = true
        }
    }

    private fun startGameTimer() {
        gameStartTime = System.currentTimeMillis()
    }

    private fun stopGameTimer(): Long {
        gameEndTime = System.currentTimeMillis()
        return gameEndTime - gameStartTime
    }

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

    // Bluetooth methods
    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                setupBluetooth()
            }
        } else {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    setupBluetooth()
                    Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth permissions denied. App will work in manual mode only.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
        }
        try {
            if (::scoreSoundPlayer.isInitialized) {
                scoreSoundPlayer.release()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error releasing sound player: ${e.message}")
        }
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
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

            // Player 1 column - UPDATED: Use PlayerNames
            val player1TextView = TextView(this).apply {
                // Format player 1 name based on whether it was a doubles game
                val player1Text = if (game.playerNames.isDoubles) {
                    "${game.playerNames.team1Player1}/${game.playerNames.team1Player2}"
                } else {
                    game.playerNames.team1Player1
                }
                text = player1Text
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1

                // UPDATED: Check if player 1 team won
                val isPlayer1Winner = if (game.playerNames.isDoubles) {
                    game.winner.contains(game.playerNames.team1Player1) || game.winner.contains(game.playerNames.team1Player2)
                } else {
                    game.winner == game.playerNames.team1Player1
                }

                setTextColor(if (isPlayer1Winner) Color.BLUE else Color.BLACK)
                setTypeface(typeface, if (isPlayer1Winner) Typeface.BOLD else Typeface.NORMAL)
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

            // Player 2 column - UPDATED: Use PlayerNames
            val player2TextView = TextView(this).apply {
                // Format player 2 name based on whether it was a doubles game
                val player2Text = if (game.playerNames.isDoubles) {
                    "${game.playerNames.team2Player1}/${game.playerNames.team2Player2}"
                } else {
                    game.playerNames.team2Player1
                }
                text = player2Text
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1

                // UPDATED: Check if player 2 team won
                val isPlayer2Winner = if (game.playerNames.isDoubles) {
                    game.winner.contains(game.playerNames.team2Player1) || game.winner.contains(game.playerNames.team2Player2)
                } else {
                    game.winner == game.playerNames.team2Player1
                }

                setTextColor(if (isPlayer2Winner) Color.BLUE else Color.BLACK)
                setTypeface(typeface, if (isPlayer2Winner) Typeface.BOLD else Typeface.NORMAL)
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

        // Format player names based on whether it was a doubles game
        val player1Display = if (game.playerNames.isDoubles) {
            "${game.playerNames.team1Player1}/${game.playerNames.team1Player2}"
        } else {
            game.playerNames.team1Player1
        }

        val player2Display = if (game.playerNames.isDoubles) {
            "${game.playerNames.team2Player1}/${game.playerNames.team2Player2}"
        } else {
            game.playerNames.team2Player1
        }

        // ADD THIS: Game type indicator
        val gameType = if (game.playerNames.isDoubles) "Doubles" else "Singles"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Game Details")
            .setMessage(
                "$player1Display vs $player2Display\n\n" +
                        "Game Type: $gameType\n" +  // ADD THIS LINE
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
        // Format player names for display
        val player1Display = if (game.playerNames.isDoubles) {
            "${game.playerNames.team1Player1}/${game.playerNames.team1Player2}"
        } else {
            game.playerNames.team1Player1
        }

        val player2Display = if (game.playerNames.isDoubles) {
            "${game.playerNames.team2Player1}/${game.playerNames.team2Player2}"
        } else {
            game.playerNames.team2Player1
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Game")
            .setMessage("Are you sure you want to delete this game?\n\n" +
                    "$player1Display vs $player2Display\n" +
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
        val player1Wins = gameHistory.count { game ->
            if (game.playerNames.isDoubles) {
                game.winner.contains(game.playerNames.team1Player1) || game.winner.contains(game.playerNames.team1Player2)
            } else {
                game.winner == game.playerNames.team1Player1
            }
        }
        val player2Wins = gameHistory.count { game ->
            if (game.playerNames.isDoubles) {
                game.winner.contains(game.playerNames.team2Player1) || game.winner.contains(game.playerNames.team2Player2)
            } else {
                game.winner == game.playerNames.team2Player1
            }
        }

        val latestGame = gameHistory.last()
        val latestDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(latestGame.timestamp))

        return "$totalGames total games • $player1Wins - $player2Wins • Last: $latestDate"
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
}