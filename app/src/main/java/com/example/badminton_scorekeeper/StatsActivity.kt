package com.example.badmintonscorekeeper

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.util.*
import java.util.concurrent.TimeUnit

class StatsActivity : AppCompatActivity() {

    private lateinit var statsData: StatsData
    private lateinit var singlesContainer: LinearLayout
    private lateinit var doublesContainer: LinearLayout
    private lateinit var lastUpdatedText: TextView
    private lateinit var currentFilterText: TextView

    private var currentFilter: String = "all"
    private var allGameHistory: List<GameHistory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        singlesContainer = findViewById(R.id.singlesStatsContainer)
        doublesContainer = findViewById(R.id.doublesStatsContainer)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)
        currentFilterText = findViewById(R.id.currentFilterText)

        loadAllGameHistory()
        setupFilterButtons()
        setupBackButton()
        applyFilter("all")
    }

    private fun loadAllGameHistory() {
        val prefs = getSharedPreferences("BadmintonScorePrefs", MODE_PRIVATE)
        val historyJson = prefs.getString("gameHistory", null)
        allGameHistory = if (!historyJson.isNullOrEmpty()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<GameHistory>>() {}.type
                Gson().fromJson<List<GameHistory>>(historyJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun loadCumulativeStats(): CumulativeStats {
        val prefs = getSharedPreferences("BadmintonScorePrefs", MODE_PRIVATE)
        val json = prefs.getString("cumulativeStats", null)
        return if (!json.isNullOrEmpty()) {
            try {
                Gson().fromJson(json, CumulativeStats::class.java)
            } catch (e: Exception) {
                CumulativeStats()
            }
        } else {
            CumulativeStats()
        }
    }

    private fun setupFilterButtons() {
        findViewById<Button>(R.id.filterAllButton).setOnClickListener {
            applyFilter("all")
        }

        findViewById<Button>(R.id.filter24HoursButton).setOnClickListener {
            applyFilter("24hours")
        }

        findViewById<Button>(R.id.filterAllTimeButton).setOnClickListener {
            applyFilter("alltime")
        }
    }

    // Update applyFilter to handle all-time
    private fun applyFilter(filterType: String) {
        currentFilter = filterType
        when (filterType) {
            "alltime" -> {
                val cumulativeStats = loadCumulativeStats()
                statsData = convertCumulativeToStatsData(cumulativeStats)
            }
            else -> {
                val filteredHistory = filterGameHistory(allGameHistory, filterType)
                statsData = calculateStatsFromHistory(filteredHistory)
            }
        }
        updateFilterDisplay(filterType)
        displayStats()
    }

    // Helper method to convert cumulative stats to StatsData
    private fun convertCumulativeToStatsData(cumulativeStats: CumulativeStats): StatsData {
        val singlesStats = mutableListOf<PlayerStats>()
        val doublesStats = mutableListOf<PlayerStats>()

        cumulativeStats.playerStats.values.forEach { cumulativeStat ->
            val displayStat = PlayerStats(
                name = cumulativeStat.name,
                wins = cumulativeStat.totalWins,
                losses = cumulativeStat.totalLosses,
                gamesPlayed = cumulativeStat.totalGamesPlayed,
                opponentStats = cumulativeStat.opponentStats
            )

            // Simple heuristic: if name contains "/" it's probably a doubles team
            if (cumulativeStat.name.contains("/")) {
                doublesStats.add(displayStat)
            } else {
                singlesStats.add(displayStat)
            }
        }

        return StatsData(
            singlesStats = singlesStats.sortedByDescending { it.gamesPlayed },
            doublesStats = doublesStats.sortedByDescending { it.gamesPlayed },
            lastUpdated = cumulativeStats.lastGameTimestamp
        )
    }

    private fun filterGameHistory(history: List<GameHistory>, filterType: String): List<GameHistory> {
        if (history.isEmpty()) return emptyList()

        return when (filterType) {
            "24hours" -> {
                val twentyFourHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
                history.filter { game ->
                    game.timestamp >= twentyFourHoursAgo
                }
            }
            else -> history // "all" - no filter
        }
    }

    // Update updateFilterDisplay for three buttons
    private fun updateFilterDisplay(filterType: String) {
        val filterText = when (filterType) {
            "all" -> "Recent Games (max 150)"
            "alltime" -> "All Time (lifetime stats)"
            "24hours" -> "Last 24 Hours"
            else -> "Recent Games (max 150)"
        }
        currentFilterText.text = "Filter: $filterText"

        // Update button colors
        val allButton = findViewById<Button>(R.id.filterAllButton)
        val hoursButton = findViewById<Button>(R.id.filter24HoursButton)
        val allTimeButton = findViewById<Button>(R.id.filterAllTimeButton)

        val primaryColor = ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_primary)

        // Reset all buttons first
        allButton.setBackgroundColor(Color.TRANSPARENT)
        allButton.setTextColor(primaryColor)
        hoursButton.setBackgroundColor(Color.TRANSPARENT)
        hoursButton.setTextColor(primaryColor)
        allTimeButton.setBackgroundColor(Color.TRANSPARENT)
        allTimeButton.setTextColor(primaryColor)

        // Set active button
        when (filterType) {
            "all" -> {
                allButton.setBackgroundColor(primaryColor)
                allButton.setTextColor(Color.WHITE)
            }
            "24hours" -> {
                hoursButton.setBackgroundColor(primaryColor)
                hoursButton.setTextColor(Color.WHITE)
            }
            "alltime" -> {
                allTimeButton.setBackgroundColor(primaryColor)
                allTimeButton.setTextColor(Color.WHITE)
            }
        }
    }

    private fun calculateStatsFromHistory(history: List<GameHistory>): StatsData {
        val singlesStats = mutableMapOf<String, PlayerStats>()
        val doublesStats = mutableMapOf<String, PlayerStats>()

        history.forEach { game ->
            if (game.playerNames.isDoubles) {
                processDoublesGame(game, doublesStats)
            } else {
                processSinglesGame(game, singlesStats)
            }
        }

        return StatsData(
            singlesStats = singlesStats.values.sortedByDescending { it.gamesPlayed },
            doublesStats = doublesStats.values.sortedByDescending { it.gamesPlayed },
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun processSinglesGame(game: GameHistory, statsMap: MutableMap<String, PlayerStats>) {
        val player1 = normalizeName(game.playerNames.team1Player1)
        val player2 = normalizeName(game.playerNames.team2Player1)
        val player1Won = game.player1Score > game.player2Score

        val player1Stats = statsMap.getOrPut(player1) { PlayerStats(player1) }
        player1Stats.gamesPlayed++
        if (player1Won) player1Stats.wins++ else player1Stats.losses++

        val player2Stats = statsMap.getOrPut(player2) { PlayerStats(player2) }
        player2Stats.gamesPlayed++
        if (player1Won) player2Stats.losses++ else player2Stats.wins++

        updateHeadToHeadStats(player1Stats, player2, player1Won)
        updateHeadToHeadStats(player2Stats, player1, !player1Won)
    }

    private fun processDoublesGame(game: GameHistory, statsMap: MutableMap<String, PlayerStats>) {
        val team1Players = listOf(
            normalizeName(game.playerNames.team1Player1),
            normalizeName(game.playerNames.team1Player2)
        )
        val team2Players = listOf(
            normalizeName(game.playerNames.team2Player1),
            normalizeName(game.playerNames.team2Player2)
        )

        val team1 = getCanonicalTeamName(team1Players)
        val team2 = getCanonicalTeamName(team2Players)
        val team1Won = game.player1Score > game.player2Score

        val team1Stats = statsMap.getOrPut(team1) { PlayerStats(team1) }
        team1Stats.gamesPlayed++
        if (team1Won) team1Stats.wins++ else team1Stats.losses++

        val team2Stats = statsMap.getOrPut(team2) { PlayerStats(team2) }
        team2Stats.gamesPlayed++
        if (team1Won) team2Stats.losses++ else team2Stats.wins++

        updateHeadToHeadStats(team1Stats, team2, team1Won)
        updateHeadToHeadStats(team2Stats, team1, !team1Won)
    }

    private fun normalizeName(name: String): String {
        return name.trim()
    }

    private fun getCanonicalTeamName(players: List<String>): String {
        return players.map { normalizeName(it) }.sorted().joinToString("/")
    }

    private fun updateHeadToHeadStats(playerStats: PlayerStats, opponent: String, won: Boolean) {
        val opponentStats = playerStats.opponentStats.getOrPut(opponent) {
            OpponentStats(opponent)
        }
        if (won) opponentStats.wins++ else opponentStats.losses++
    }

    private fun setupBackButton() {
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun displayStats() {
        // Update last updated time
        val lastUpdated = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(statsData.lastUpdated))

        val singlesGamesInStats = statsData.singlesStats.sumOf { it.gamesPlayed }
        val doublesGamesInStats = statsData.doublesStats.sumOf { it.gamesPlayed }

        val displayText = when (currentFilter) {
            "alltime" -> {
                val cumulativeStats = loadCumulativeStats()
                "Last updated: $lastUpdated\n" +
                        "All-time statistics\n" +
                        "${cumulativeStats.totalGamesProcessed} total games recorded\n"
            }
            else -> {
                val filteredHistory = filterGameHistory(allGameHistory, currentFilter)
                val gamesInCurrentFilter = filteredHistory.size
                val singlesGamesCount = filteredHistory.count { !it.playerNames.isDoubles }
                val doublesGamesCount = filteredHistory.count { it.playerNames.isDoubles }

                val filterDesc = when (currentFilter) {
                    "all" -> "${allGameHistory.size} games"
                    "24hours" -> "$gamesInCurrentFilter games (last 24 hours)"
                    else -> "${allGameHistory.size} games"
                }

                "Last updated: $lastUpdated\n" +
                        "Filter: $filterDesc\n" +
                        "Games: $singlesGamesCount singles, $doublesGamesCount doubles"
            }
        }

        lastUpdatedText.text = displayText

        // Display singles stats
        displayStatsSection("Singles", statsData.singlesStats, singlesContainer)

        // Display doubles stats
        displayStatsSection("Doubles", statsData.doublesStats, doublesContainer)

        // Show message if no stats available
        if (statsData.singlesStats.isEmpty() && statsData.doublesStats.isEmpty()) {
            findViewById<TextView>(R.id.noStatsText).visibility = View.VISIBLE
        } else {
            findViewById<TextView>(R.id.noStatsText).visibility = View.GONE
        }
    }

    private fun displayStatsSection(title: String, stats: List<PlayerStats>, container: LinearLayout) {
        // Clear existing views
        container.removeAllViews()

        if (stats.isEmpty()) {
            val noDataText = TextView(this).apply {
                text = "No $title games in selected filter"
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            container.addView(noDataText)
            return
        }

        // Add section header
        val header = TextView(this).apply {
            text = title
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 20f
            setPadding(0, 16, 0, 16)
        }
        container.addView(header)

        // Create table for better alignment
        val tableLayout = TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            isStretchAllColumns = true
        }

        // Add column headers
        val headers = arrayOf("Player/Team", "Games", "W", "L", "Win %")
        val headerRow = createTableRow(headers, isHeader = true)
        tableLayout.addView(headerRow)

        // Add player/team rows
        stats.forEach { playerStats ->
            val rowData = arrayOf(
                playerStats.name,
                playerStats.gamesPlayed.toString(),
                playerStats.wins.toString(),
                playerStats.losses.toString(),
                "%.1f%%".format(playerStats.winRate * 100)
            )
            val row = createTableRow(rowData, isHeader = false)

            // Make row clickable to show detailed stats
            row.setOnClickListener {
                showPlayerDetails(playerStats, title)
            }

            // Alternate row colors for better readability
            if (stats.indexOf(playerStats) % 2 == 0) {
                row.setBackgroundColor(Color.parseColor("#f0f0f0"))
            } else {
                row.setBackgroundColor(Color.WHITE)
            }

            tableLayout.addView(row)
        }

        container.addView(tableLayout)
    }

    private fun createTableRow(data: Array<String>, isHeader: Boolean): TableRow {
        val row = TableRow(this)
        val layoutParams = TableRow.LayoutParams(
            TableRow.LayoutParams.MATCH_PARENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )
        row.layoutParams = layoutParams

        data.forEachIndexed { index, text ->
            val textView = TextView(this).apply {
                this.text = text
                gravity = when (index) {
                    0 -> Gravity.START
                    else -> Gravity.CENTER
                }
                setPadding(8, 12, 8, 12)

                if (isHeader) {
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundColor(ContextCompat.getColor(this@StatsActivity, com.google.android.material.R.color.design_default_color_primary))
                } else {
                    setTextColor(Color.DKGRAY)
                    if (index == 0) {
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.BLACK)
                    }
                }
            }

            val cellLayoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            textView.layoutParams = cellLayoutParams
            row.addView(textView)
        }

        return row
    }

    private fun showPlayerDetails(playerStats: PlayerStats, gameType: String) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("${playerStats.name} - $gameType")
            .setMessage(buildPlayerDetailsMessage(playerStats))
            .setPositiveButton("Close", null)
            .create()

        dialog.show()
    }

    private fun buildPlayerDetailsMessage(playerStats: PlayerStats): String {
        val sb = StringBuilder()
        sb.append("Overall Stats:\n")
        sb.append("Games: ${playerStats.gamesPlayed}\n")
        sb.append("Wins: ${playerStats.wins}\n")
        sb.append("Losses: ${playerStats.losses}\n")
        sb.append("Win Rate: ${"%.1f".format(playerStats.winRate * 100)}%\n\n")

        if (playerStats.opponentStats.isNotEmpty()) {
            sb.append("Head-to-Head:\n")
            playerStats.opponentStats.values.sortedByDescending { it.wins + it.losses }
                .forEach { opponent ->
                    sb.append("vs ${opponent.opponentName}: ")
                    sb.append("${opponent.wins}-${opponent.losses} ")
                    sb.append("(${"%.1f".format(opponent.winRate * 100)}%)\n")
                }
        } else {
            sb.append("No head-to-head data available")
        }

        return sb.toString()
    }
}