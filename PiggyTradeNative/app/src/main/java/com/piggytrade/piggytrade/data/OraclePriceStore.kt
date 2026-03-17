package com.piggytrade.piggytrade.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.piggytrade.piggytrade.BuildConfig
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.stablecoin.VlqCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists on-chain oracle price data locally so we only fetch deltas on startup.
 * Tracks USE oracle, SigUSD oracle, and SigUSD DEX pool prices.
 */
class OraclePriceStore(private val context: Context) {

    companion object {
        private const val TAG = "OraclePriceStore"
        // Oracle NFTs
        const val USE_ORACLE_NFT = "6a2b821b5727e85beb5e78b4efb9f0250d59cd48481d2ded2c23e91ba1d07c66"
        const val SIGUSD_ORACLE_NFT = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f"
        // SigUSD primary DEX pool NFT (from tokens.json)
        const val SIGUSD_DEX_POOL_NFT = "9916d75132593c8b07fe18bd8d583bda1652eed7565cf41a4738ddd90fc992ec"
        // Max data points to keep (~1 year at 17 min intervals)
        private const val MAX_POINTS = 32000
        // Blocks per minute estimate
        private const val BLOCK_TIME_MS = 120_000L // 2 minutes
    }

    data class PricePoint(val height: Int, val price: Double)

    data class PriceHistory(
        val lastHeight: Int = 0,
        val prices: MutableList<PricePoint> = mutableListOf()
    )

    // In-memory caches
    private var useOracle = PriceHistory()
    private var sigUsdOracle = PriceHistory()
    private var sigUsdDex = PriceHistory()
    private var currentHeight = 0
    /** Arbitrary token DEX price histories keyed by token name */
    private val tokenDexData = mutableMapOf<String, PriceHistory>()
    /** True when local data is empty (first run) */
    var isFirstSync = false
        private set
    /** True when a token sync is running */
    var isTokenSyncing by mutableStateOf(false)
        private set
    /** Human-readable label of what's currently syncing */
    var syncProgressLabel by mutableStateOf("")
        private set
    /** Progress 0.0–1.0 of current sync, -1 = indeterminate */
    var syncProgressPercent by mutableFloatStateOf(-1f)
        private set

    /** Load all cached data from disk */
    fun loadAll() {
        useOracle = loadFromFile("oracle_use.json")
        sigUsdOracle = loadFromFile("oracle_sigusd.json")
        sigUsdDex = loadFromFile("dex_sigusd.json")
        isFirstSync = useOracle.prices.isEmpty() && sigUsdOracle.prices.isEmpty() && sigUsdDex.prices.isEmpty()
        if (BuildConfig.DEBUG) Log.d(TAG, "Loaded: USE=${useOracle.prices.size} SigUSD=${sigUsdOracle.prices.size} DEX=${sigUsdDex.prices.size} firstSync=$isFirstSync")
    }

    /** Sync all sources from the node. Call on startup. */
    @Suppress("UNCHECKED_CAST")
    suspend fun syncAll(client: NodeClient) {
        try {
            // Show progress immediately so UI reacts right away
            syncProgressLabel = if (isFirstSync) "First sync — fetching 1 year of price data..." else "Checking for new price data..."
            syncProgressPercent = -1f  // indeterminate while connecting

            // Get current block height
            val info = client.api.getNodeInfo()
            currentHeight = (info["fullHeight"] as? Number)?.toInt() ?: 0
            if (currentHeight == 0) {
                syncProgressLabel = ""
                return
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Current height: $currentHeight")

            syncProgressLabel = "Syncing USE oracle..."
            syncProgressPercent = 0f
            syncOracle(client, USE_ORACLE_NFT, useOracle, "oracle_use.json", "USE")
            syncProgressLabel = "Syncing SigUSD oracle..."
            syncProgressPercent = 0f
            syncOracle(client, SIGUSD_ORACLE_NFT, sigUsdOracle, "oracle_sigusd.json", "SigUSD")
            syncProgressLabel = "Syncing SigUSD DEX..."
            syncProgressPercent = 0f
            syncDexPool(client, SIGUSD_DEX_POOL_NFT, sigUsdDex, "dex_sigusd.json")
            syncProgressLabel = ""
            syncProgressPercent = -1f
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Sync failed: ${e.message}")
            syncProgressLabel = ""
            syncProgressPercent = -1f
        }
    }

    /** Merge new points into history for oracle sources */
    private fun mergeAndSave(
        nft: String, history: PriceHistory,
        newPoints: List<PricePoint>, filename: String
    ) {
        val reversed = newPoints.reversed()
        history.prices.addAll(reversed)
        while (history.prices.size > MAX_POINTS) history.prices.removeAt(0)
        history.prices.sortBy { it.height }
        val deduped = history.prices.distinctBy { it.height }.toMutableList()
        history.prices.clear()
        history.prices.addAll(deduped)

        val newLast = history.prices.maxOf { it.height }
        val updated = PriceHistory(newLast, history.prices)
        saveToFile(filename, updated)
        when (nft) {
            USE_ORACLE_NFT -> useOracle = updated
            SIGUSD_ORACLE_NFT -> sigUsdOracle = updated
            SIGUSD_DEX_POOL_NFT -> sigUsdDex = updated
        }
    }

    /** Merge new points into history for token DEX sources, returns the updated PriceHistory */
    private fun mergeAndSaveToken(
        tokenName: String, history: PriceHistory,
        newPoints: List<PricePoint>, filename: String
    ): PriceHistory {
        val reversed = newPoints.reversed()
        history.prices.addAll(reversed)
        while (history.prices.size > MAX_POINTS) history.prices.removeAt(0)
        history.prices.sortBy { it.height }
        val deduped = history.prices.distinctBy { it.height }.toMutableList()
        history.prices.clear()
        history.prices.addAll(deduped)

        val newLast = history.prices.maxOf { it.height }
        val updated = PriceHistory(newLast, history.prices)
        saveToFile(filename, updated)
        return updated
    }

    /** Sync a single oracle's price history */
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncOracle(
        client: NodeClient, nft: String, history: PriceHistory,
        filename: String, label: String
    ) {
        try {
            val newPoints = mutableListOf<PricePoint>()
            var offset = 0
            val pageSize = 100
            var done = false
            var totalBoxes = 0
            // Capture the stop-height BEFORE the loop so that incremental checkpoint saves
            // (which update lastHeight) don't affect the break condition.
            val breakAtHeight = history.lastHeight
            val MIN_HEIGHT_GAP = 180
            var lastStoredHeight = Int.MAX_VALUE

            while (!done) {
                val resp = client.api.getBoxesByTokenId(nft, offset, pageSize)
                val items = resp["items"] as? List<Map<String, Any>> ?: break

                for (box in items) {
                    val h = (box["inclusionHeight"] as? Number)?.toInt() ?: continue
                    if (h <= breakAtHeight) {
                        done = true
                        break
                    }
                    if (lastStoredHeight != Int.MAX_VALUE && (lastStoredHeight - h) < MIN_HEIGHT_GAP) continue

                    val regs = box["additionalRegisters"] as? Map<String, Any> ?: continue
                    val r4 = regs["R4"] as? String ?: continue
                    val rate = VlqCodec.decode(r4)
                    if (rate > 0) {
                        val price = 1_000_000_000.0 / rate.toDouble()
                        if (price < 50.0) {
                            newPoints.add(PricePoint(h, price))
                            lastStoredHeight = h
                        }
                    }
                }

                if (items.size < pageSize) break
                offset += pageSize
                if (totalBoxes == 0) totalBoxes = (resp["total"] as? Number)?.toInt() ?: 0
                val progressStr = if (totalBoxes > 0) "${String.format("%,d", offset)} / ${String.format("%,d", totalBoxes)}" else "${String.format("%,d", offset)}"
                syncProgressPercent = if (totalBoxes > 0) (offset.toFloat() / totalBoxes.toFloat()).coerceAtMost(0.99f) else -1f
                syncProgressLabel = "Syncing $label ($progressStr boxes) — ${newPoints.size} stored"

                // Checkpoint: save to disk every 2,000 boxes so progress survives app kills
                if (newPoints.size >= 50 && offset % 2000 == 0) {
                    mergeAndSave(nft, history, newPoints, filename)
                    newPoints.clear()
                }

                if (offset > 500_000) break
            }

            // Final save
            if (newPoints.isNotEmpty()) {
                mergeAndSave(nft, history, newPoints, filename)
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "$label: total=${history.prices.size}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "$label sync failed: ${e.message}")
        }
    }

    /** Sync SigUSD DEX pool price history (spot price from reserves) */
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncDexPool(
        client: NodeClient, poolNft: String, history: PriceHistory, filename: String
    ) {
        try {
            val newPoints = mutableListOf<PricePoint>()
            var offset = 0
            val pageSize = 500
            var done = false
            var totalBoxes = 0
            val breakAtHeight = history.lastHeight
            val MIN_HEIGHT_GAP = 180
            var lastStoredHeight = Int.MAX_VALUE

            while (!done) {
                val resp = client.api.getBoxesByTokenId(poolNft, offset, pageSize)
                val items = resp["items"] as? List<Map<String, Any>> ?: break

                for (box in items) {
                    val h = (box["inclusionHeight"] as? Number)?.toInt() ?: continue
                    if (h <= breakAtHeight) {
                        done = true
                        break
                    }
                    if (lastStoredHeight != Int.MAX_VALUE && (lastStoredHeight - h) < MIN_HEIGHT_GAP) continue

                    val ergReserve = (box["value"] as? Number)?.toLong() ?: continue
                    val assets = box["assets"] as? List<Map<String, Any>> ?: continue
                    if (assets.size < 3) continue
                    val sigUsdReserve = (assets[2]["amount"] as? Number)?.toLong() ?: continue
                    if (sigUsdReserve > 0 && ergReserve > 0) {
                        val usdPerErg = (sigUsdReserve.toDouble() / 100.0) / (ergReserve.toDouble() / 1_000_000_000.0)
                        newPoints.add(PricePoint(h, usdPerErg))
                        lastStoredHeight = h
                    }
                }

                if (items.size < pageSize) break
                offset += pageSize
                if (totalBoxes == 0) totalBoxes = (resp["total"] as? Number)?.toInt() ?: 0
                val progressStr = if (totalBoxes > 0) "${String.format("%,d", offset)} / ${String.format("%,d", totalBoxes)}" else "${String.format("%,d", offset)}"
                syncProgressPercent = if (totalBoxes > 0) (offset.toFloat() / totalBoxes.toFloat()).coerceAtMost(0.99f) else -1f
                syncProgressLabel = "Syncing SigUSD DEX ($progressStr boxes) — ${newPoints.size} stored"

                if (newPoints.size >= 50 && offset % 2000 == 0) {
                    mergeAndSave(SIGUSD_DEX_POOL_NFT, history, newPoints, filename)
                    newPoints.clear()
                }
                
                if (offset > 500_000) break
            }

            if (newPoints.isNotEmpty()) {
                mergeAndSave(SIGUSD_DEX_POOL_NFT, history, newPoints, filename)
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "SigUSD DEX: total=${history.prices.size}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SigUSD DEX sync failed: ${e.message}")
        }
    }

    /**
     * Sync price history for any token from its DEX pool.
     * Price is in ERG per token (how much ERG one token is worth).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun syncTokenDex(
        client: NodeClient, tokenName: String, poolNft: String, tokenDecimals: Int,
        onCheckpoint: (() -> Unit)? = null
    ) {
        isTokenSyncing = true
        try {
            val filename = "dex_${tokenName.lowercase().replace(" ", "_")}.json"
            val history = tokenDexData.getOrPut(tokenName) { loadFromFile(filename) }

            // Publish cached data immediately if available
            if (history.prices.isNotEmpty()) onCheckpoint?.invoke()

            val newPoints = mutableListOf<PricePoint>()
            var offset = 0
            val pageSize = 500
            var done = false
            var totalBoxes = 0
            val breakAtHeight = history.lastHeight
            val MIN_HEIGHT_GAP = 180
            var lastStoredHeight = Int.MAX_VALUE

            if (currentHeight == 0) {
                try {
                    val info = client.api.getNodeInfo()
                    currentHeight = (info["fullHeight"] as? Number)?.toInt() ?: 0
                    if (BuildConfig.DEBUG) Log.d(TAG, "syncTokenDex: fetched height $currentHeight")
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "syncTokenDex: failed to fetch height: ${e.message}")
                }
            }

            while (!done) {
                val resp = client.api.getBoxesByTokenId(poolNft, offset, pageSize)
                val items = resp["items"] as? List<Map<String, Any>> ?: break

                for (box in items) {
                    val h = (box["inclusionHeight"] as? Number)?.toInt() ?: continue
                    if (h <= breakAtHeight) {
                        done = true
                        break
                    }
                    if (lastStoredHeight != Int.MAX_VALUE && (lastStoredHeight - h) < MIN_HEIGHT_GAP) continue

                    val ergReserve = (box["value"] as? Number)?.toLong() ?: continue
                    val assets = box["assets"] as? List<Map<String, Any>> ?: continue
                    if (assets.size < 3) continue
                    val tokenReserve = (assets[2]["amount"] as? Number)?.toLong() ?: continue
                    if (tokenReserve > 0 && ergReserve > 0) {
                        val tokenDiv = Math.pow(10.0, tokenDecimals.toDouble())
                        val ergPerToken = (ergReserve.toDouble() / 1_000_000_000.0) / (tokenReserve.toDouble() / tokenDiv)
                        if (ergPerToken < 1_000_000.0) {
                            newPoints.add(PricePoint(h, ergPerToken))
                            lastStoredHeight = h
                        }
                    }
                }

                if (items.size < pageSize) break
                offset += pageSize
                if (totalBoxes == 0) totalBoxes = (resp["total"] as? Number)?.toInt() ?: 0
                val progressStr = if (totalBoxes > 0) "${String.format("%,d", offset)} / ${String.format("%,d", totalBoxes)}" else "${String.format("%,d", offset)}"
                syncProgressPercent = if (totalBoxes > 0) (offset.toFloat() / totalBoxes.toFloat()).coerceAtMost(0.99f) else -1f
                syncProgressLabel = "Syncing $tokenName ($progressStr boxes) — ${newPoints.size} stored"

                if (newPoints.size >= 50 && offset % 2000 == 0) {
                    val updated = mergeAndSaveToken(tokenName, history, newPoints, filename)
                    tokenDexData[tokenName] = updated
                    newPoints.clear()
                    onCheckpoint?.invoke()
                }
                
                if (offset > 500_000) break
            }

            if (newPoints.isNotEmpty()) {
                val updated = mergeAndSaveToken(tokenName, history, newPoints, filename)
                tokenDexData[tokenName] = updated
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "Token $tokenName: total=${history.prices.size}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Token $tokenName sync failed: ${e.message}")
        } finally {
            isTokenSyncing = false
            syncProgressLabel = ""
            syncProgressPercent = -1f
        }
    }

    /** Check if a token already has cached data */
    fun hasTokenData(tokenName: String): Boolean {
        val filename = "dex_${tokenName.lowercase().replace(" ", "_")}.json"
        if (tokenDexData.containsKey(tokenName)) return tokenDexData[tokenName]!!.prices.isNotEmpty()
        val loaded = loadFromFile(filename)
        if (loaded.prices.isNotEmpty()) {
            tokenDexData[tokenName] = loaded
            return true
        }
        return false
    }

    /**
     * Get price history for a given range, sampled for chart display.
     * Returns List<Pair<Long, Double>> where Long = estimated timestamp ms.
     */
    fun getHistory(source: String, range: String): List<Pair<Long, Double>> {
        val history = when (source) {
            "use" -> useOracle
            "sigusd_oracle" -> sigUsdOracle
            "sigusd_dex" -> sigUsdDex
            else -> return emptyList()
        }
        if (history.prices.isEmpty() || currentHeight == 0) return emptyList()

        val now = System.currentTimeMillis()
        val blocksBack = when (range) {
            "24H" -> 720
            "7D" -> 5040
            "30D" -> 21600
            "3M" -> 64800
            "6M" -> 129600
            "1Y" -> 262800
            "3Y" -> 788400
            "MAX" -> Int.MAX_VALUE
            else -> 5040
        }
        val targetPoints = when (range) {
            "24H" -> 80
            "7D" -> 50
            "30D" -> 40
            "3M" -> 60
            "6M" -> 80
            "1Y" -> 100
            "3Y" -> 120
            "MAX" -> 150
            else -> 50
        }

        val minHeight = if (range == "MAX") 0 else currentHeight - blocksBack
        var filtered = history.prices.filter { it.height >= minHeight && it.price < 50.0 }

        // If no data within the range window, anchor with the most recent valid price
        if (filtered.isEmpty()) {
            val anchor = history.prices.lastOrNull { it.price < 50.0 } ?: return emptyList()
            filtered = listOf(anchor)
        }

        // Downsample to targetPoints
        val step = maxOf(1, filtered.size / targetPoints)
        val sampled = filtered.filterIndexed { i, _ -> i % step == 0 || i == filtered.lastIndex }

        return buildList {
            addAll(sampled.map { point ->
                val estimatedTs = now - (currentHeight - point.height) * BLOCK_TIME_MS
                Pair(estimatedTs, point.price)
            })
            // For DEX source: extend line to 'now' using the most recent stored price
            // This prevents a gap at the right edge of the chart
            if (source == "sigusd_dex") {
                val latestPrice = filtered.lastOrNull()?.price
                if (latestPrice != null && (lastOrNull()?.first ?: 0L) < now - BLOCK_TIME_MS) {
                    add(Pair(now, latestPrice))
                }
            }
        }
    }

    /** Get the latest price from a source */
    fun getLatestPrice(source: String): Double? {
        val history = when (source) {
            "use" -> useOracle
            "sigusd_oracle" -> sigUsdOracle
            "sigusd_dex" -> sigUsdDex
            else -> return null
        }
        return history.prices.lastOrNull()?.price
    }

    /** Get price history for an arbitrary token (in ERG) */
    fun getTokenHistory(tokenName: String, range: String): List<Pair<Long, Double>> {
        val history = tokenDexData[tokenName] ?: return emptyList()
        if (history.prices.isEmpty() || currentHeight == 0) return emptyList()

        val now = System.currentTimeMillis()
        val blocksBack = when (range) {
            "24H" -> 720
            "7D" -> 5040
            "30D" -> 21600
            "3M" -> 64800
            "6M" -> 129600
            "1Y" -> 262800
            "3Y" -> 788400
            "MAX" -> Int.MAX_VALUE
            else -> 5040
        }
        val targetPoints = when (range) {
            "24H" -> 80; "7D" -> 50; "30D" -> 40; "3M" -> 60; "6M" -> 80
            "1Y" -> 100; "3Y" -> 120; "MAX" -> 150; else -> 50
        }

        val minHeight = if (range == "MAX") 0 else currentHeight - blocksBack
        var filtered = history.prices.filter { it.height >= minHeight }

        // If no trades happened within the range window, anchor with the most recent
        // trade before the window so we show a flat line at the current price.
        if (filtered.isEmpty()) {
            val anchor = history.prices.lastOrNull() ?: return emptyList()
            filtered = listOf(anchor)
        }

        val step = maxOf(1, filtered.size / targetPoints)
        val sampled = filtered.filterIndexed { i, _ -> i % step == 0 || i == filtered.lastIndex }

        return buildList {
            addAll(sampled.map { point ->
                val estimatedTs = now - (currentHeight - point.height) * BLOCK_TIME_MS
                Pair(estimatedTs, point.price)
            })
            // Extend line to 'now' using the most recent stored price as current price
            val latestPrice = filtered.lastOrNull()?.price
            if (latestPrice != null && (lastOrNull()?.first ?: 0L) < now - BLOCK_TIME_MS) {
                add(Pair(now, latestPrice))
            }
        }
    }

    /** Get latest price for an arbitrary token (in ERG) */
    fun getTokenLatestPrice(tokenName: String): Double? {
        return tokenDexData[tokenName]?.prices?.lastOrNull()?.price
    }

    /** Delete all cached oracle data files and reset in-memory state */
    fun clearAll() {
        listOf("oracle_use.json", "oracle_sigusd.json", "dex_sigusd.json").forEach { filename ->
            try { File(context.filesDir, filename).delete() } catch (_: Exception) {}
        }
        // Also delete any token dex files
        context.filesDir.listFiles()?.filter { it.name.startsWith("dex_") }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }
        useOracle = PriceHistory()
        sigUsdOracle = PriceHistory()
        sigUsdDex = PriceHistory()
        tokenDexData.clear()
        currentHeight = 0
        isFirstSync = true
        if (BuildConfig.DEBUG) Log.d(TAG, "Cleared all oracle data")
    }

    // ─── JSON Persistence ───────────────────────────────────────

    private fun loadFromFile(filename: String): PriceHistory {
        try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) return PriceHistory()
            val json = JSONObject(file.readText())
            val lastHeight = json.optInt("lastHeight", 0)
            val arr = json.optJSONArray("prices") ?: return PriceHistory()
            val prices = mutableListOf<PricePoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                prices.add(PricePoint(obj.getInt("h"), obj.getDouble("p")))
            }
            return PriceHistory(lastHeight, prices)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Load $filename failed: ${e.message}")
            return PriceHistory()
        }
    }

    private fun saveToFile(filename: String, history: PriceHistory) {
        try {
            val arr = JSONArray()
            for (p in history.prices) {
                arr.put(JSONObject().put("h", p.height).put("p", p.price))
            }
            val json = JSONObject()
                .put("lastHeight", history.lastHeight)
                .put("prices", arr)
            File(context.filesDir, filename).writeText(json.toString())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Save $filename failed: ${e.message}")
        }
    }
}
