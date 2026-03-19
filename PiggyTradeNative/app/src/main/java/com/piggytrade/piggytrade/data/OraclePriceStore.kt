package com.piggytrade.piggytrade.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.piggytrade.piggytrade.BuildConfig
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.network.NodePool
import com.piggytrade.piggytrade.stablecoin.VlqCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    data class VolumePoint(val height: Int, val ergDelta: Double)

    data class PriceHistory(
        val lastHeight: Int = 0,
        val prices: MutableList<PricePoint> = mutableListOf(),
        val volumes: MutableList<VolumePoint> = mutableListOf(),
        var syncComplete: Boolean = false,
        var lastScanOffset: Int = 0     // resume point for interrupted syncs
    )

    /** Market summary for one token, computed after sync */
    data class TokenMarketData(
        val name: String,
        val currentPriceErg: Double,
        val priceChange24h: Double,     // % change
        val priceChange7d: Double,      // % change
        val volume24hErg: Double,
        val volume7dErg: Double,
        val lastSynced: Long = 0L
    )

    // In-memory caches
    private var useOracle = PriceHistory()
    private var sigUsdOracle = PriceHistory()
    private var sigUsdDex = PriceHistory()
    internal var currentHeight = 0
        private set
    /** Arbitrary token DEX price histories keyed by token name */
    private val tokenDexData = mutableMapOf<String, PriceHistory>()
    /** Computed market data for all synced tokens */
    var allTokenMarketData by mutableStateOf<List<TokenMarketData>>(emptyList())
        private set
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
    /** All-token background sync progress */
    var allTokenSyncLabel by mutableStateOf("")
        private set
    var allTokenSyncProgress by mutableFloatStateOf(-1f)
        private set
    var allTokenSyncIndex by mutableStateOf(0)
        private set
    var allTokenSyncTotal by mutableStateOf(0)
        private set
    var isSyncingAllTokens by mutableStateOf(false)
        private set
    /** Timestamp when isSyncingAllTokens was last set to true — used to detect stale flags */
    private var syncingAllTokensStartMs = 0L

    /** Load all cached data from disk */
    fun loadAll() {
        useOracle = loadFromFile("oracle_use.json")
        sigUsdOracle = loadFromFile("oracle_sigusd.json")
        sigUsdDex = loadFromFile("dex_sigusd.json")
        isFirstSync = useOracle.prices.isEmpty() && sigUsdOracle.prices.isEmpty() && sigUsdDex.prices.isEmpty()

        if (BuildConfig.DEBUG) Log.d(TAG, "Loaded: USE=${useOracle.prices.size} SigUSD=${sigUsdOracle.prices.size} DEX=${sigUsdDex.prices.size} firstSync=$isFirstSync")
    }

    /**
     * Resilient API fetch — retries indefinitely with different nodes from the pool.
     * Only CancellationException breaks the loop. Uses exponential backoff (2s, 4s, 8s, max 30s).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> resilientFetch(
        nodePool: NodePool,
        label: String,
        block: suspend (NodeClient) -> T
    ): T {
        var attempt = 0
        while (true) {
            val client = nodePool.next()
            try {
                return block(client)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                val delay = minOf(2000L * (1L shl minOf(attempt - 1, 4)), 30_000L)
                if (BuildConfig.DEBUG) Log.d(TAG, "$label: attempt $attempt failed (${e.message}), retrying in ${delay}ms")
                kotlinx.coroutines.delay(delay)
            }
        }
    }

    /** Sync all sources from the node. Call on startup. */
    @Suppress("UNCHECKED_CAST")
    suspend fun syncAll(nodePool: NodePool) {
        try {
            // Show progress immediately so UI reacts right away
            syncProgressLabel = if (isFirstSync) "First sync — fetching 1 year of price data..." else "Checking for new price data..."
            syncProgressPercent = -1f  // indeterminate while connecting

            // Get current block height (resilient — retries indefinitely)
            val info = resilientFetch(nodePool, "height") { it.api.getNodeInfo() }
            currentHeight = (info["fullHeight"] as? Number)?.toInt() ?: 0
            if (currentHeight == 0) {
                syncProgressLabel = ""
                return
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Current height: $currentHeight")

            if (useOracle.syncComplete) {
                syncProgressLabel = "Updating USE oracle..."
            } else {
                val useResumeAt = if (useOracle.lastScanOffset > 0) " (resuming at ${useOracle.lastScanOffset})" else ""
                syncProgressLabel = "Syncing USE oracle...$useResumeAt"
            }
            syncProgressPercent = 0f
            if (BuildConfig.DEBUG) Log.d(TAG, "Starting USE sync: complete=${useOracle.syncComplete} offset=${useOracle.lastScanOffset} prices=${useOracle.prices.size}")
            syncOracle(nodePool, USE_ORACLE_NFT, useOracle, "oracle_use.json", "USE")

            if (sigUsdOracle.syncComplete) {
                syncProgressLabel = "Updating SigUSD oracle..."
            } else {
                val sigResumeAt = if (sigUsdOracle.lastScanOffset > 0) " (resuming at ${sigUsdOracle.lastScanOffset})" else ""
                syncProgressLabel = "Syncing SigUSD oracle...$sigResumeAt"
            }
            syncProgressPercent = 0f
            if (BuildConfig.DEBUG) Log.d(TAG, "Starting SigUSD sync: complete=${sigUsdOracle.syncComplete} offset=${sigUsdOracle.lastScanOffset} prices=${sigUsdOracle.prices.size}")
            syncOracle(nodePool, SIGUSD_ORACLE_NFT, sigUsdOracle, "oracle_sigusd.json", "SigUSD")

            if (sigUsdDex.syncComplete) {
                syncProgressLabel = "Updating SigUSD DEX..."
            } else {
                val dexResumeAt = if (sigUsdDex.lastScanOffset > 0) " (resuming at ${sigUsdDex.lastScanOffset})" else ""
                syncProgressLabel = "Syncing SigUSD DEX...$dexResumeAt"
            }
            syncProgressPercent = 0f
            if (BuildConfig.DEBUG) Log.d(TAG, "Starting SigUSD DEX sync: complete=${sigUsdDex.syncComplete} offset=${sigUsdDex.lastScanOffset} prices=${sigUsdDex.prices.size}")
            syncDexPool(nodePool, SIGUSD_DEX_POOL_NFT, sigUsdDex, "dex_sigusd.json")

            syncProgressLabel = ""
            syncProgressPercent = -1f

            // Verify all oracle syncs completed
            val incomplete = mutableListOf<String>()
            if (!useOracle.syncComplete) incomplete.add("USE")
            if (!sigUsdOracle.syncComplete) incomplete.add("SigUSD")
            if (!sigUsdDex.syncComplete) incomplete.add("SigUSD DEX")
            if (incomplete.isNotEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Oracle sync incomplete: ${incomplete.joinToString()}")
                throw Exception("Oracle sync incomplete: ${incomplete.joinToString()}")
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "All oracle syncs complete")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Sync failed: ${e.message}")
            syncProgressLabel = ""
            syncProgressPercent = -1f
            throw e  // re-throw so caller knows oracle sync didn't complete
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
        val updated = PriceHistory(newLast, history.prices, history.volumes, history.syncComplete, history.lastScanOffset)
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
        newPoints: List<PricePoint>, newVolumes: List<VolumePoint>, filename: String
    ): PriceHistory {
        val reversedPrices = newPoints.reversed()
        history.prices.addAll(reversedPrices)
        while (history.prices.size > MAX_POINTS) history.prices.removeAt(0)
        history.prices.sortBy { it.height }
        val dedupedPrices = history.prices.distinctBy { it.height }.toMutableList()
        history.prices.clear()
        history.prices.addAll(dedupedPrices)

        val reversedVolumes = newVolumes.reversed()
        history.volumes.addAll(reversedVolumes)
        while (history.volumes.size > MAX_POINTS) history.volumes.removeAt(0)
        history.volumes.sortBy { it.height }
        val dedupedVolumes = history.volumes.distinctBy { it.height }.toMutableList()
        history.volumes.clear()
        history.volumes.addAll(dedupedVolumes)

        val newLast = history.prices.maxOfOrNull { it.height } ?: history.lastHeight
        val updated = PriceHistory(newLast, history.prices, history.volumes, history.syncComplete, history.lastScanOffset)
        saveToFile(filename, updated)
        return updated
    }

    /** Sync a single oracle's price history */
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncOracle(
        nodePool: NodePool, nft: String, history: PriceHistory,
        filename: String, label: String
    ) {
        try {
            val newPoints = mutableListOf<PricePoint>()
            var offset = if (!history.syncComplete && history.lastScanOffset > 0) history.lastScanOffset else 0
            val pageSize = 100
            var done = false
            var totalBoxes = 0
            val breakAtHeight = history.lastHeight
            val canBreakAtCached = history.syncComplete
            val lowestCachedHeight = if (!canBreakAtCached && history.prices.isNotEmpty())
                history.prices.minOf { it.height } else 0
            val height24h = if (currentHeight > 720) currentHeight - 720 else 0
            var lastStoredHeight = Int.MAX_VALUE

            while (!done) {
                val resp = resilientFetch(nodePool, "$label@$offset") { client ->
                    client.api.getBoxesByTokenId(nft, offset, pageSize)
                }
                val items = resp["items"] as? List<Map<String, Any>> ?: break

                for (box in items) {
                    val h = (box["inclusionHeight"] as? Number)?.toInt() ?: continue
                    if (canBreakAtCached && h <= breakAtHeight) {
                        done = true
                        break
                    }
                    // Skip heights in the already-cached range, but keep scanning below it
                    if (!canBreakAtCached && h in lowestCachedHeight..breakAtHeight) continue
                    val minGap = if (h >= height24h) 8 else 180
                    if (lastStoredHeight != Int.MAX_VALUE && (lastStoredHeight - h) < minGap) continue

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
                if (canBreakAtCached) {
                    // Completed sync — just fetching recent updates, show simpler label
                    syncProgressPercent = -1f  // indeterminate since it'll finish fast
                    syncProgressLabel = "Updating $label (recent data) — ${history.prices.size + newPoints.size} stored"
                } else {
                    // First/incomplete sync — show full progress
                    val progressStr = if (totalBoxes > 0) "${String.format("%,d", offset)} / ${String.format("%,d", totalBoxes)}" else "${String.format("%,d", offset)}"
                    syncProgressPercent = if (totalBoxes > 0) (offset.toFloat() / totalBoxes.toFloat()).coerceAtMost(0.99f) else -1f
                    syncProgressLabel = "Syncing $label ($progressStr boxes) — ${history.prices.size + newPoints.size} stored"
                }

                // Always track current scan position
                history.lastScanOffset = offset

                // Checkpoint: save to disk every 500 boxes so progress survives app kills
                if (offset % 500 == 0 && (newPoints.isNotEmpty() || !canBreakAtCached)) {
                    if (newPoints.isNotEmpty()) {
                        mergeAndSave(nft, history, newPoints, filename)
                        newPoints.clear()
                    } else {
                        // Even with no new points, save the offset so we can resume
                        saveToFile(filename, history)
                    }
                }

                if (offset > 500_000) break
            }

            // Final save — include any remaining points and the current offset
            history.lastScanOffset = offset
            if (newPoints.isNotEmpty()) {
                mergeAndSave(nft, history, newPoints, filename)
            }

            // Mark sync as complete — next run can skip past cached data
            history.syncComplete = true
            history.lastScanOffset = 0  // reset offset for future syncs
            saveToFile(filename, history)

            if (BuildConfig.DEBUG) Log.d(TAG, "$label: total=${history.prices.size} complete")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "$label sync failed: ${e.message}")
        }
    }

    /** Sync SigUSD DEX pool price history (spot price from reserves) */
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncDexPool(
        nodePool: NodePool, poolNft: String, history: PriceHistory, filename: String
    ) {
        try {
            val newPoints = mutableListOf<PricePoint>()
            var offset = if (!history.syncComplete && history.lastScanOffset > 0) history.lastScanOffset else 0
            val pageSize = 500
            var done = false
            var totalBoxes = 0
            val breakAtHeight = history.lastHeight
            val canBreakAtCached = history.syncComplete
            val lowestCachedHeight = if (!canBreakAtCached && history.prices.isNotEmpty())
                history.prices.minOf { it.height } else 0
            val height24h = if (currentHeight > 720) currentHeight - 720 else 0
            var lastStoredHeight = Int.MAX_VALUE

            while (!done) {
                val resp = resilientFetch(nodePool, "SigUSD DEX@$offset") { client ->
                    client.api.getBoxesByTokenId(poolNft, offset, pageSize)
                }
                val items = resp["items"] as? List<Map<String, Any>> ?: break

                for (box in items) {
                    val h = (box["inclusionHeight"] as? Number)?.toInt() ?: continue
                    if (canBreakAtCached && h <= breakAtHeight) {
                        done = true
                        break
                    }
                    if (!canBreakAtCached && h in lowestCachedHeight..breakAtHeight) continue
                    val minGap = if (h >= height24h) 8 else 180
                    if (lastStoredHeight != Int.MAX_VALUE && (lastStoredHeight - h) < minGap) continue

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
                if (canBreakAtCached) {
                    syncProgressPercent = -1f
                    syncProgressLabel = "Updating SigUSD DEX (recent data) — ${history.prices.size + newPoints.size} stored"
                } else {
                    val progressStr = if (totalBoxes > 0) "${String.format("%,d", offset)} / ${String.format("%,d", totalBoxes)}" else "${String.format("%,d", offset)}"
                    syncProgressPercent = if (totalBoxes > 0) (offset.toFloat() / totalBoxes.toFloat()).coerceAtMost(0.99f) else -1f
                    syncProgressLabel = "Syncing SigUSD DEX ($progressStr boxes) — ${history.prices.size + newPoints.size} stored"
                }

                history.lastScanOffset = offset

                if (offset % 500 == 0 && (newPoints.isNotEmpty() || !canBreakAtCached)) {
                    if (newPoints.isNotEmpty()) {
                        mergeAndSave(SIGUSD_DEX_POOL_NFT, history, newPoints, filename)
                        newPoints.clear()
                    } else {
                        saveToFile(filename, history)
                    }
                }
                
                if (offset > 500_000) break
            }

            history.lastScanOffset = offset
            if (newPoints.isNotEmpty()) {
                mergeAndSave(SIGUSD_DEX_POOL_NFT, history, newPoints, filename)
            }

            history.syncComplete = true
            history.lastScanOffset = 0
            saveToFile(filename, history)

            if (BuildConfig.DEBUG) Log.d(TAG, "SigUSD DEX: total=${history.prices.size} complete")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SigUSD DEX sync failed: ${e.message}")
        }
    }

    /**
     * Sync price history for any token from its DEX pool.
     * Price is in ERG per token (how much ERG one token is worth).
     * Also tracks 24h and 7d ERG volume from pool box state changes.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun syncTokenDex(
        nodePool: NodePool, tokenName: String, poolNft: String, tokenDecimals: Int,
        onCheckpoint: (() -> Unit)? = null
    ) {
        isTokenSyncing = true
        try {
            val filename = "dex_${tokenName.lowercase().replace(" ", "_")}.json"
            val history = tokenDexData.getOrPut(tokenName) { loadFromFile(filename) }

            // Publish cached data immediately if available
            if (history.prices.isNotEmpty()) onCheckpoint?.invoke()

            // Skip sync entirely if synced very recently (within ~1 hour = 30 blocks)
            if (history.syncComplete && currentHeight > 0 && history.lastHeight > 0 &&
                (currentHeight - history.lastHeight) < 30) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Token $tokenName: skipping, synced recently (${currentHeight - history.lastHeight} blocks ago)")
                return
            }

            val newPoints = mutableListOf<PricePoint>()
            val newVolumes = mutableListOf<VolumePoint>()
            var offset = if (!history.syncComplete && history.lastScanOffset > 0) history.lastScanOffset else 0
            val pageSize = 500
            var done = false
            var totalBoxes = 0
            val breakAtHeight = history.lastHeight
            val canBreakAtCached = history.syncComplete
            val lowestCachedHeight = if (!canBreakAtCached && history.prices.isNotEmpty())
                history.prices.minOf { it.height } else 0
            var lastStoredHeight = Int.MAX_VALUE

            val height24h = if (currentHeight > 720) currentHeight - 720 else 0
            val height7d = if (currentHeight > 5040) currentHeight - 5040 else 0
            var prevErgReserve: Long? = null

            // Need volume backfill if we have no stored volume data within the 7d window
            val needsVolumeBackfill = canBreakAtCached && history.volumes.count { it.height >= height7d } < 5

            if (currentHeight == 0) {
                val info = resilientFetch(nodePool, "$tokenName height") { it.api.getNodeInfo() }
                currentHeight = (info["fullHeight"] as? Number)?.toInt() ?: 0
                if (BuildConfig.DEBUG) Log.d(TAG, "syncTokenDex: fetched height $currentHeight")
            }

            while (!done) {
                val resp = resilientFetch(nodePool, "$tokenName@$offset") { client ->
                    client.api.getBoxesByTokenId(poolNft, offset, pageSize)
                }
                val items = resp["items"] as? List<Map<String, Any>> ?: break

                for (box in items) {
                    val h = (box["inclusionHeight"] as? Number)?.toInt() ?: continue
                    val ergReserve = (box["value"] as? Number)?.toLong() ?: continue
                    val assets = box["assets"] as? List<Map<String, Any>> ?: continue
                    if (assets.size < 3) continue

                    // Store volume data point for each trade (within 7d window)
                    if (prevErgReserve != null && h >= height7d) {
                        val ergDelta = Math.abs(prevErgReserve - ergReserve) / 1_000_000_000.0
                        if (ergDelta > 0.001) {
                            newVolumes.add(VolumePoint(h, ergDelta))
                        }
                    }
                    prevErgReserve = ergReserve

                    // For completed syncs: stop at cached height for prices.
                    // But if we need volume backfill, keep scanning to 7d.
                    if (canBreakAtCached && h <= breakAtHeight) {
                        if (!needsVolumeBackfill || h < height7d) {
                            done = true
                            break
                        }
                        continue  // skip price storage but keep scanning for volume
                    }
                    if (!canBreakAtCached && h in lowestCachedHeight..breakAtHeight) continue
                    val minGap = if (h >= height24h) 8 else 180
                    if (lastStoredHeight != Int.MAX_VALUE && (lastStoredHeight - h) < minGap) continue

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
                if (canBreakAtCached) {
                    syncProgressPercent = -1f
                    syncProgressLabel = "Updating $tokenName — ${history.prices.size + newPoints.size} stored"
                } else {
                    val progressStr = if (totalBoxes > 0) "${String.format("%,d", offset)} / ${String.format("%,d", totalBoxes)}" else "${String.format("%,d", offset)}"
                    syncProgressPercent = if (totalBoxes > 0) (offset.toFloat() / totalBoxes.toFloat()).coerceAtMost(0.99f) else -1f
                    syncProgressLabel = "Syncing $tokenName ($progressStr boxes) — ${history.prices.size + newPoints.size} stored"
                }

                history.lastScanOffset = offset

                if (offset % 500 == 0 && (newPoints.isNotEmpty() || !canBreakAtCached)) {
                    if (newPoints.isNotEmpty()) {
                        val updated = mergeAndSaveToken(tokenName, history, newPoints, newVolumes, filename)
                        tokenDexData[tokenName] = updated
                        newPoints.clear()
                        newVolumes.clear()
                        onCheckpoint?.invoke()
                    } else {
                        saveToFile(filename, history)
                    }
                }
                
                if (offset > 500_000) break
            }

            history.lastScanOffset = offset
            // Merge new data (prices + volumes)
            if (newPoints.isNotEmpty() || newVolumes.isNotEmpty()) {
                val updated = mergeAndSaveToken(tokenName, history, newPoints, newVolumes, filename)
                tokenDexData[tokenName] = updated
            }

            // Prune volume points older than 7 days
            history.volumes.removeAll { it.height < height7d }

            // Mark sync complete
            history.syncComplete = true
            history.lastScanOffset = 0
            saveToFile(filename, history)

            if (BuildConfig.DEBUG) Log.d(TAG, "Token $tokenName: prices=${history.prices.size} volumes=${history.volumes.size} complete")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Token $tokenName sync failed: ${e.message}")
        } finally {
            isTokenSyncing = false
            syncProgressLabel = ""
            syncProgressPercent = -1f
        }
    }

    /**
     * Get volume data for a token (computed from stored VolumePoints).
     * Returns Pair(vol24h, vol7d) in ERG.
     */
    fun getTokenVolume(tokenName: String): Pair<Double, Double> {
        val history = tokenDexData[tokenName] ?: return Pair(0.0, 0.0)
        if (history.volumes.isEmpty() || currentHeight == 0) return Pair(0.0, 0.0)
        val height24h = currentHeight - 720
        val height7d = if (currentHeight > 5040) currentHeight - 5040 else 0
        val vol24h = history.volumes.filter { it.height >= height24h }.sumOf { it.ergDelta }
        val vol7d = history.volumes.filter { it.height >= height7d }.sumOf { it.ergDelta }
        return Pair(vol24h, vol7d)
    }

    /**
     * Compute market data for a single token from its price history + stored volume points.
     */
    fun computeMarketData(tokenName: String): TokenMarketData? {
        val history = tokenDexData[tokenName] ?: return null
        if (history.prices.isEmpty() || currentHeight == 0) return null

        val latestPrice = history.prices.lastOrNull()?.price ?: return null
        val height24h = currentHeight - 720
        val height7d = if (currentHeight > 5040) currentHeight - 5040 else 0

        // Find price closest to 24h ago
        val price24h = history.prices.lastOrNull { it.height <= height24h }?.price ?: latestPrice
        // Find price closest to 7d ago
        val price7d = history.prices.lastOrNull { it.height <= height7d }?.price ?: latestPrice

        val change24h = if (price24h > 0) ((latestPrice - price24h) / price24h) * 100.0 else 0.0
        val change7d = if (price7d > 0) ((latestPrice - price7d) / price7d) * 100.0 else 0.0

        // Compute volume from stored VolumePoints
        val vol24h = history.volumes.filter { it.height >= height24h }.sumOf { it.ergDelta }
        val vol7d = history.volumes.filter { it.height >= height7d }.sumOf { it.ergDelta }

        return TokenMarketData(
            name = tokenName,
            currentPriceErg = latestPrice,
            priceChange24h = change24h,
            priceChange7d = change7d,
            volume24hErg = vol24h,
            volume7dErg = vol7d,
            lastSynced = System.currentTimeMillis()
        )
    }

    /**
     * Rebuild market data from cached files on disk — NO network calls.
     * Used on startup to show existing market data before user triggers a manual sync.
     */
    fun rebuildMarketDataFromCache(tokens: List<Triple<String, String, Int>>) {
        for ((name, _, _) in tokens) {
            val filename = "dex_${name.lowercase().replace(" ", "_")}.json"
            val cached = loadFromFile(filename)
            if (cached.prices.isNotEmpty()) {
                tokenDexData[name] = cached
            }
        }
        allTokenMarketData = tokenDexData.keys.mapNotNull { computeMarketData(it) }
            .sortedByDescending { it.volume7dErg }
    }

    /**
     * Background sync ALL whitelisted tokens' price + volume data.
     * Uses multiple nodes in parallel for faster syncing.
     * tokens: List<Triple<name, poolNft, decimals>>
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun syncAllTokens(
        nodePool: NodePool,
        tokens: List<Triple<String, String, Int>>
    ) {
        // Guard: if the flag has been stuck for >10 min (stale from a previous ViewModel), reset it
        val nowMs = System.currentTimeMillis()
        if (isSyncingAllTokens) {
            if (nowMs - syncingAllTokensStartMs < 2 * 60 * 1000L) return
            // Flag is stale — force reset and continue
            if (BuildConfig.DEBUG) Log.d(TAG, "syncAllTokens: stale isSyncingAllTokens flag, resetting")
        }
        isSyncingAllTokens = true
        syncingAllTokensStartMs = nowMs
        try {
            if (currentHeight == 0) {
                val client = nodePool.next()
                val info = client.api.getNodeInfo()
                currentHeight = (info["fullHeight"] as? Number)?.toInt() ?: 0
            }

            val total = tokens.size
            allTokenSyncTotal = total

            // Resume from last completed token if app was interrupted
            val prefs = context.getSharedPreferences("oracle_sync", android.content.Context.MODE_PRIVATE)
            val resumeFrom = prefs.getInt("allTokenResumeIndex", 0)
            if (resumeFrom > 0 && BuildConfig.DEBUG) Log.d(TAG, "Resuming market sync from token $resumeFrom / $total")

            // Load cached data for already-synced tokens
            if (resumeFrom > 0) {
                for (i in 0 until resumeFrom) {
                    val (name, _, _) = tokens[i]
                    if (!tokenDexData.containsKey(name)) {
                        val filename = "dex_${name.lowercase().replace(" ", "_")}.json"
                        val loaded = loadFromFile(filename)
                        if (loaded.prices.isNotEmpty()) tokenDexData[name] = loaded
                    }
                }
                allTokenMarketData = tokenDexData.keys.mapNotNull { computeMarketData(it) }
                    .sortedByDescending { it.volume7dErg }
            }

            // Get remaining tokens to sync
            val remaining = tokens.drop(resumeFrom)
            if (remaining.isEmpty()) {
                prefs.edit().putInt("allTokenResumeIndex", 0).apply()
                return
            }

            // Split into parallel groups — one per available node (max 3)
            val parallelism = minOf(nodePool.size, 3).coerceAtLeast(1)
            val chunks = remaining.chunked((remaining.size + parallelism - 1) / parallelism)
            val completedCount = java.util.concurrent.atomic.AtomicInteger(resumeFrom)

            if (BuildConfig.DEBUG) Log.d(TAG, "Syncing ${remaining.size} tokens across $parallelism nodes")

            coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        for ((name, poolNft, decimals) in chunk) {
                            try {
                                syncTokenDex(nodePool, name, poolNft, decimals)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) Log.d(TAG, "syncAllTokens: $name failed: ${e.message}")
                            }

                            val done = completedCount.incrementAndGet()
                            // Save resume index
                            prefs.edit().putInt("allTokenResumeIndex", done).apply()

                            // Update progress UI
                            allTokenSyncIndex = done
                            allTokenSyncLabel = name
                            allTokenSyncProgress = (done.toFloat() / total.toFloat())

                            // Rebuild market data periodically
                            if (done % 5 == 0 || done == total) {
                                synchronized(tokenDexData) {
                                    allTokenMarketData = tokenDexData.keys.mapNotNull { computeMarketData(it) }
                                        .sortedByDescending { it.volume7dErg }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            // Final rebuild
            allTokenMarketData = tokenDexData.keys.mapNotNull { computeMarketData(it) }
                .sortedByDescending { it.volume7dErg }

            // All tokens done — reset resume index for next full cycle
            prefs.edit().putInt("allTokenResumeIndex", 0).apply()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "syncAllTokens failed: ${e.message}")
        } finally {
            isSyncingAllTokens = false
            allTokenSyncLabel = ""
            allTokenSyncProgress = -1f
            allTokenSyncIndex = 0
            allTokenSyncTotal = 0
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
        allTokenMarketData = emptyList()
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
            val volumes = mutableListOf<VolumePoint>()
            val volArr = json.optJSONArray("volumes")
            if (volArr != null) {
                for (i in 0 until volArr.length()) {
                    val obj = volArr.getJSONObject(i)
                    volumes.add(VolumePoint(obj.getInt("h"), obj.getDouble("v")))
                }
            }
            val complete = json.optBoolean("syncComplete", false)
            val scanOffset = json.optInt("lastScanOffset", 0)
            return PriceHistory(lastHeight, prices, volumes, complete, scanOffset)
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
            val volArr = JSONArray()
            for (v in history.volumes) {
                volArr.put(JSONObject().put("h", v.height).put("v", v.ergDelta))
            }
            val json = JSONObject()
                .put("lastHeight", history.lastHeight)
                .put("syncComplete", history.syncComplete)
                .put("lastScanOffset", history.lastScanOffset)
                .put("prices", arr)
                .put("volumes", volArr)
            File(context.filesDir, filename).writeText(json.toString())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Save $filename failed: ${e.message}")
        }
    }
}
