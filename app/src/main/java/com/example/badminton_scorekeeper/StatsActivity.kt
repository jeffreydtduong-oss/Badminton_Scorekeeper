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
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import android.net.Uri
import kotlin.math.abs

class StatsActivity : AppCompatActivity() {

    private lateinit var statsData: StatsData
    private lateinit var singlesContainer: LinearLayout
    private lateinit var doublesContainer: LinearLayout
    private lateinit var lastUpdatedText: TextView
    private lateinit var currentFilterText: TextView
    private lateinit var exportDataButton: Button
    private lateinit var clearAllTimeStatsButton: Button

    private var currentFilter: String = "all"
    private var allGameHistory: List<GameHistory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        singlesContainer = findViewById(R.id.singlesStatsContainer)
        doublesContainer = findViewById(R.id.doublesStatsContainer)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)
        currentFilterText = findViewById(R.id.currentFilterText)
        exportDataButton = findViewById(R.id.exportDataButton)
        clearAllTimeStatsButton = findViewById(R.id.clearAllTimeStatsButton)

        loadAllGameHistory()
        setupFilterButtons()
        setupExportButton()
        setupClearAllTimeStatsButton()
        setupBackButton()
        applyFilter("all")
    }

    private fun setupExportButton() {
        exportDataButton.setOnClickListener {
            if (currentFilter == "alltime") {
                exportAllTimeData()
            }
        }
    }

    private fun setupClearAllTimeStatsButton() {
        clearAllTimeStatsButton.setOnClickListener {
            showDeleteCumulativeStatsConfirmation()
        }
    }

    private fun exportAllTimeData() {
        val cumulativeStats = loadCumulativeStats()

        if (cumulativeStats.totalGamesProcessed == 0) {
            Toast.makeText(this, "No all-time data to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseFileName = "badminton_stats_$timestamp"

            // Create separate CSV files
            val filesCreated = mutableListOf<String>()

            // 1. Metadata CSV
            val metadataFile = createCsvFile("${baseFileName}_metadata.csv", buildMetadataCsv(cumulativeStats, timestamp))
            filesCreated.add(metadataFile)

            // 2. Overall Statistics CSV
            val overallStatsFile = createCsvFile("${baseFileName}_overall_stats.csv", buildOverallStatsCsv(cumulativeStats))
            filesCreated.add(overallStatsFile)

            // 3. Head-to-Head Statistics CSV
            val headToHeadFile = createCsvFile("${baseFileName}_head_to_head.csv", buildHeadToHeadCsv(cumulativeStats))
            filesCreated.add(headToHeadFile)

            // 4. Game History CSV (if available)
            if (allGameHistory.isNotEmpty()) {
                val gameHistoryFile = createCsvFile("${baseFileName}_game_history.csv", buildGameHistoryCsv())
                filesCreated.add(gameHistoryFile)
            }

            // Share all files
            shareMultipleFiles(filesCreated, baseFileName)

            Toast.makeText(this,
                "✅ ${filesCreated.size} CSV files exported successfully!\n\nCheck your file manager in:\nAndroid/data/com.example.badmintonscorekeeper/files/",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("StatsActivity", "Error exporting data: ${e.message}", e)
            Toast.makeText(this, "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createCsvFile(fileName: String, csvContent: String): String {
        val file = File(getExternalFilesDir(null), fileName)
        FileWriter(file).use { writer ->
            writer.write(csvContent)
        }
        return file.absolutePath
    }

    private fun buildMetadataCsv(cumulativeStats: CumulativeStats, timestamp: String): String {
        val sb = StringBuilder()
        sb.append("Key,Value\n")
        sb.append("ExportDate,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("ExportId,$timestamp\n")
        sb.append("TotalGamesProcessed,${cumulativeStats.totalGamesProcessed}\n")
        sb.append("FirstGameTimestamp,${cumulativeStats.firstGameTimestamp}\n")
        sb.append("FirstGameDate,${formatTimestamp(cumulativeStats.firstGameTimestamp)}\n")
        sb.append("LastGameTimestamp,${cumulativeStats.lastGameTimestamp}\n")
        sb.append("LastGameDate,${formatTimestamp(cumulativeStats.lastGameTimestamp)}\n")
        sb.append("DataScope,All-Time Cumulative Statistics\n")
        sb.append("AppVersion,Badminton Scorekeeper\n")
        return sb.toString()
    }

    private fun buildOverallStatsCsv(cumulativeStats: CumulativeStats): String {
        val sb = StringBuilder()
        sb.append("PlayerTeam,GamesPlayed,Wins,Losses,WinRate,GameType\n")

        cumulativeStats.playerStats.values.sortedByDescending { it.totalGamesPlayed }.forEach { player ->
            val gameType = if (player.name.contains("/")) "Doubles" else "Singles"
            sb.append("\"${player.name}\",")
            sb.append("${player.totalGamesPlayed},")
            sb.append("${player.totalWins},")
            sb.append("${player.totalLosses},")
            sb.append("${"%.3f".format(player.totalWinRate)},") // Decimal format for Power BI
            sb.append("$gameType\n")
        }

        return sb.toString()
    }

    private fun buildHeadToHeadCsv(cumulativeStats: CumulativeStats): String {
        val sb = StringBuilder()
        sb.append("Player,Opponent,Wins,Losses,WinRate,TotalMatches,GameType\n")

        cumulativeStats.playerStats.values.forEach { player ->
            val playerGameType = if (player.name.contains("/")) "Doubles" else "Singles"
            player.opponentStats.values.sortedByDescending { it.wins + it.losses }.forEach { opponent ->
                val totalMatches = opponent.wins + opponent.losses
                sb.append("\"${player.name}\",")
                sb.append("\"${opponent.opponentName}\",")
                sb.append("${opponent.wins},")
                sb.append("${opponent.losses},")
                sb.append("${"%.3f".format(opponent.winRate)},")
                sb.append("$totalMatches,")
                sb.append("$playerGameType\n")
            }
        }

        return sb.toString()
    }

    private fun buildGameHistoryCsv(): String {
        val sb = StringBuilder()
        sb.append("Timestamp,DateTime,GameType,Team1Player1,Team1Player2,Team2Player1,Team2Player2,Team1Score,Team2Score,Winner,WinningTeam,GameDurationSeconds,WinningPoints,ScoreDifference\n")

        if (allGameHistory.isNotEmpty()) {
            allGameHistory.reversed().forEach { game ->
                val timestamp = game.timestamp
                val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                val gameType = if (game.playerNames.isDoubles) "Doubles" else "Singles"
                val durationSeconds = (game.endTime - game.startTime) / 1000
                val winningTeam = if (game.player1Score > game.player2Score) "Team1" else "Team2"
                val scoreDifference = abs(game.player1Score - game.player2Score)

                sb.append("$timestamp,")
                sb.append("\"$dateTime\",")
                sb.append("\"$gameType\",")
                sb.append("\"${game.playerNames.team1Player1}\",")
                sb.append("\"${if (game.playerNames.isDoubles) game.playerNames.team1Player2 else ""}\",")
                sb.append("\"${game.playerNames.team2Player1}\",")
                sb.append("\"${if (game.playerNames.isDoubles) game.playerNames.team2Player2 else ""}\",")
                sb.append("${game.player1Score},")
                sb.append("${game.player2Score},")
                sb.append("\"${game.winner}\",")
                sb.append("\"$winningTeam\",")
                sb.append("$durationSeconds,")
                sb.append("${game.winningPoints},")
                sb.append("$scoreDifference\n")
            }
        }

        return sb.toString()
    }

    private fun shareMultipleFiles(filePaths: List<String>, baseFileName: String) {
        try {
            val uris = ArrayList<Uri>()

            filePaths.forEach { filePath ->
                val file = File(filePath)
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                uris.add(uri)
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Badminton Statistics Export - $baseFileName")
                putExtra(Intent.EXTRA_TEXT, "Badminton Scorekeeper all-time statistics export\n\nIncludes:\n- Metadata\n- Overall Statistics\n- Head-to-Head Statistics\n- Game History")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Grant permissions to receiving apps
            val resInfoList = packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                uris.forEach { uri ->
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            startActivity(Intent.createChooser(shareIntent, "Export Statistics Files"))

        } catch (e: Exception) {
            Log.e("StatsActivity", "Error sharing multiple files: ${e.message}", e)
            // Files are still created locally even if sharing fails
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return if (timestamp == Long.MAX_VALUE || timestamp == 0L) {
            "N/A"
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        }
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

    // Update the applyFilter method to show/hide export button
    private fun applyFilter(filterType: String) {
        currentFilter = filterType
        when (filterType) {
            "alltime" -> {
                val cumulativeStats = loadCumulativeStats()
                statsData = convertCumulativeToStatsData(cumulativeStats)
                // Show both buttons for all-time filter
                exportDataButton.visibility = View.VISIBLE
                clearAllTimeStatsButton.visibility = View.VISIBLE
            }
            else -> {
                val filteredHistory = filterGameHistory(allGameHistory, filterType)
                statsData = calculateStatsFromHistory(filteredHistory)
                // Hide both buttons for other filters
                exportDataButton.visibility = View.GONE
                clearAllTimeStatsButton.visibility = View.GONE
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
        val headers = arrayOf("Player(s)", "Games", "W", "L", "Win %")
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
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
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

    // NEW: Add the delete confirmation dialog (moved from MainActivity)
    private fun showDeleteCumulativeStatsConfirmation() {
        val cumulativeStats = loadCumulativeStats()
        val totalGames = cumulativeStats.totalGamesProcessed

        if (totalGames == 0) {
            Toast.makeText(this, "No cumulative stats to delete", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Delete All-Time Stats")
            .setMessage("Are you sure you want to delete ALL lifetime statistics?\n\n" +
                    "This will permanently delete:\n" +
                    "• $totalGames all-time games\n" +
                    "• All player win/loss records\n" +
                    "• All head-to-head statistics\n\n" +
                    "This action cannot be undone!")
            .setPositiveButton("DELETE ALL STATS") { dialog, which ->
                deleteCumulativeStats()
                // Refresh the display after deletion
                applyFilter("alltime")
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    val positiveButton = getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    val negativeButton = getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

                    // Make "DELETE ALL STATS" red and bold
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

    // NEW: Method to delete cumulative stats
    private fun deleteCumulativeStats() {
        val prefs = getSharedPreferences("BadmintonScorePrefs", MODE_PRIVATE)
        prefs.edit().remove("cumulativeStats").apply()
        Toast.makeText(this, "All-time statistics cleared", Toast.LENGTH_SHORT).show()
    }
}