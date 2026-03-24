package com.piggytrade.piggytrade.ui.market

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.piggytrade.piggytrade.BuildConfig
import com.piggytrade.piggytrade.TruffleApplication
import com.piggytrade.piggytrade.data.OraclePriceStore
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.protocol.NetworkConfig
import com.piggytrade.piggytrade.ui.swap.EcosystemTx
import com.piggytrade.piggytrade.ui.swap.PoolMapping
import com.piggytrade.piggytrade.ui.swap.PoolTrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class MarketState(
    // ─── ERG price / chart ────────────────────────────────────────────────
    val ergPriceUsd: Double? = null,
    val ergPriceHistory: List<Pair<Long, Double>> = emptyList(),
    val sigUsdOracleHistory: List<Pair<Long, Double>> = emptyList(),
    val sigUsdDexHistory: List<Pair<Long, Double>> = emptyList(),
    val chartRange: String = "7D",
    val selectedChartToken: String? = null,
    val tokenPriceHistory: List<Pair<Long, Double>> = emptyList(),
    val tokenUsdValues: Map<String, Double> = emptyMap(),

    // ─── Pool trades ─────────────────────────────────────────────────────
    val poolTrades: List<PoolTrade> = emptyList(),
    val isLoadingPoolTrades: Boolean = false,
    val poolVolume24h: Double = 0.0,
    val poolVolume7d: Double = 0.0,
    val tokenMarketData: List<OraclePriceStore.TokenMarketData> = emptyList(),

    // ─── Market sync ─────────────────────────────────────────────────────
    val marketSyncState: String = "idle",
    val marketSyncProgress: Float = 0f,
    val marketSyncLabel: String = "",
    val marketSyncIndex: Int = 0,
    val marketSyncTotal: Int = 0,
    val lastMarketSyncMs: Long = 0L,
    val marketSyncIncomplete: Boolean = false,
    val syncFailedCount: Int = 0,

    // ─── Ecosystem ───────────────────────────────────────────────────────
    val ecosystemTvl: Map<String, Double> = emptyMap(),
    val ecosystemActivity: List<EcosystemTx> = emptyList(),
    val isLoadingEcosystem: Boolean = false,
    val ecosystemLastFetched: Long = 0L
)

class MarketViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MarketViewModel"
    private val session = (application as TruffleApplication).sessionManager
    private val tokenRepository = session.tokenRepository
    val oraclePriceStore = session.oraclePriceStore
    private val nodePool = session.nodePool

    private val nodeClient: NodeClient? get() = session.nodeManager.nodeClient.value

    private val _uiState = MutableStateFlow(MarketState())
    val uiState: StateFlow<MarketState> = _uiState.asStateFlow()

    private var oracleSyncJob: kotlinx.coroutines.Job? = null
    private var marketSyncJob: kotlinx.coroutines.Job? = null
    private var tokenSyncJob: kotlinx.coroutines.Job? = null
    private var isFetchingTokenValues = false
    private var ecosystemActivityCache: List<EcosystemTx> = emptyList()
    private var ecosystemPage = 0
    private val poolBoxDataCache = mutableMapOf<String, Map<String, Any>>()

    // ─── ERG price / chart ────────────────────────────────────────────────

    fun fetchErgPrice(range: String = _uiState.value.chartRange) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val useHistory = oraclePriceStore.getHistory("use", range)
                val sigOracleHistory = oraclePriceStore.getHistory("sigusd_oracle", range)
                val sigDexHistory = oraclePriceStore.getHistory("sigusd_dex", range)
                val currentPrice = oraclePriceStore.getLatestPrice("use")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        ergPriceUsd = currentPrice,
                        ergPriceHistory = useHistory,
                        sigUsdOracleHistory = sigOracleHistory,
                        sigUsdDexHistory = sigDexHistory,
                        chartRange = range
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Oracle price fetch failed: ${e.message}")
            }
        }
    }

    fun syncOraclePrices() {
        if (oracleSyncJob?.isActive == true) return
        oracleSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                oraclePriceStore.loadAll()
                fetchErgPrice()
                try { oraclePriceStore.syncAll(nodePool) } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Oracle sync failed: ${e.message}")
                }
                fetchErgPrice()
                val allTokens = tokenRepository.getWhitelistedTokensWithPools()
                if (allTokens.isNotEmpty() && oraclePriceStore.allTokenMarketData.isEmpty()) {
                    oraclePriceStore.rebuildMarketDataFromCache(allTokens)
                    _uiState.value = _uiState.value.copy(tokenMarketData = oraclePriceStore.allTokenMarketData)
                }
                val prefs = getApplication<Application>()
                    .getSharedPreferences("oracle_sync", android.content.Context.MODE_PRIVATE)
                val lastSyncMs = prefs.getLong("lastMarketSyncMs", 0L)
                val resumeIdx = prefs.getInt("allTokenResumeIndex", 0)
                val totalTokens = allTokens.size
                val incomplete = totalTokens > 0 && (lastSyncMs == 0L || resumeIdx in 1 until totalTokens)
                _uiState.value = _uiState.value.copy(
                    lastMarketSyncMs = lastSyncMs,
                    marketSyncIncomplete = incomplete,
                    marketSyncTotal = totalTokens
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Oracle sync failed: ${e.message}")
            }
        }
    }

    // ─── Market sync ─────────────────────────────────────────────────────

    fun startMarketSync() {
        if (marketSyncJob?.isActive == true) return
        val allTokens = tokenRepository.getWhitelistedTokensWithPools()
        if (allTokens.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            marketSyncState = "syncing", marketSyncProgress = 0f,
            marketSyncLabel = "", marketSyncIndex = 0, marketSyncTotal = allTokens.size
        )
        marketSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                oraclePriceStore.syncAllTokens(nodePool, allTokens)
                val now = System.currentTimeMillis()
                getApplication<Application>().getSharedPreferences("oracle_sync", android.content.Context.MODE_PRIVATE)
                    .edit().putLong("lastMarketSyncMs", now).apply()
                val failedCount = oraclePriceStore.lastSyncFailedTokens.size
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "completed", marketSyncProgress = 1f,
                    lastMarketSyncMs = now, marketSyncIncomplete = false, syncFailedCount = failedCount
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "idle", marketSyncIncomplete = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "idle", marketSyncIncomplete = true
                )
            }
        }
    }

    fun stopMarketSync() {
        marketSyncJob?.cancel()
        marketSyncJob = null
    }

    fun retryFailedSync() {
        val failedTokens = oraclePriceStore.lastSyncFailedTokens
        if (failedTokens.isEmpty() || marketSyncJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            marketSyncState = "syncing", marketSyncProgress = 0f,
            marketSyncLabel = "", marketSyncIndex = 0,
            marketSyncTotal = failedTokens.size, syncFailedCount = 0
        )
        marketSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                oraclePriceStore.syncAllTokens(nodePool, failedTokens)
                val now = System.currentTimeMillis()
                val retryFailedCount = oraclePriceStore.lastSyncFailedTokens.size
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "completed", marketSyncProgress = 1f,
                    lastMarketSyncMs = now, syncFailedCount = retryFailedCount
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "idle", marketSyncIncomplete = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "idle"
                )
            }
        }
    }

    // ─── Chart / token selection ──────────────────────────────────────────

    fun setChartRange(range: String) {
        _uiState.value = _uiState.value.copy(chartRange = range)
        val token = _uiState.value.selectedChartToken
        if (token != null) {
            val history = oraclePriceStore.getTokenHistory(token, range)
            _uiState.value = _uiState.value.copy(tokenPriceHistory = history)
        } else {
            fetchErgPrice(range)
        }
    }

    fun clearAndResync() {
        viewModelScope.launch {
            oracleSyncJob?.cancelAndJoin(); oracleSyncJob = null
            tokenSyncJob?.cancelAndJoin(); tokenSyncJob = null
            oraclePriceStore.clearAll()
            _uiState.value = _uiState.value.copy(
                ergPriceUsd = null, ergPriceHistory = emptyList(),
                sigUsdOracleHistory = emptyList(), sigUsdDexHistory = emptyList(),
                selectedChartToken = null, tokenPriceHistory = emptyList()
            )
            syncOraclePrices()
        }
    }

    fun selectChartToken(tokenName: String?) {
        tokenSyncJob?.cancel(); tokenSyncJob = null
        val range = _uiState.value.chartRange
        if (tokenName == null) {
            _uiState.value = _uiState.value.copy(selectedChartToken = null, tokenPriceHistory = emptyList())
            fetchErgPrice(range); return
        }
        _uiState.value = _uiState.value.copy(selectedChartToken = tokenName, tokenPriceHistory = emptyList())
        if (oraclePriceStore.hasTokenData(tokenName)) {
            val history = oraclePriceStore.getTokenHistory(tokenName, range)
            val (v24, v7) = oraclePriceStore.getTokenVolume(tokenName)
            _uiState.value = _uiState.value.copy(tokenPriceHistory = history, poolVolume24h = v24, poolVolume7d = v7)
        }
        tokenSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val poolNft = tokenRepository.getPoolNftForToken(tokenName) ?: return@launch
                val decimals = tokenRepository.getDecimalsForToken(tokenName)
                try {
                    oraclePriceStore.syncTokenDex(nodePool, tokenName, poolNft, decimals) {
                        val h = oraclePriceStore.getTokenHistory(tokenName, range)
                        if (h.isNotEmpty()) _uiState.value = _uiState.value.copy(tokenPriceHistory = h)
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Token sync failed: ${e.message}")
                }
                val history = oraclePriceStore.getTokenHistory(tokenName, range)
                val (v24, v7) = oraclePriceStore.getTokenVolume(tokenName)
                _uiState.value = _uiState.value.copy(tokenPriceHistory = history, poolVolume24h = v24, poolVolume7d = v7)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Token chart sync failed: ${e.message}")
            }
        }
        viewModelScope.launch(Dispatchers.IO) { fetchPoolTrades(tokenName) }
    }

    fun getTokensWithPools(): List<String> = tokenRepository.getTokenNamesWithPools()

    // ─── Pool trades ─────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchPoolTrades(tokenName: String) {
        val client = nodeClient ?: nodePool.next()
        _uiState.value = _uiState.value.copy(isLoadingPoolTrades = true, poolTrades = emptyList(), poolVolume24h = 0.0, poolVolume7d = 0.0)
        try {
            val poolNft = tokenRepository.getPoolNftForToken(tokenName) ?: return
            val decimals = tokenRepository.getDecimalsForToken(tokenName)
            val tokenDiv = Math.pow(10.0, decimals.toDouble())
            val tokenId = tokenRepository.getTokenIdForName(tokenName) ?: ""
            val nowMs = System.currentTimeMillis()
            val currentHeight = try { client.getHeight() } catch (e: Exception) { 0 }
            val resp = client.api.getBoxesByTokenId(poolNft, 0, 16)
            val boxes = resp["items"] as? List<Map<String, Any>> ?: return
            if (boxes.size < 2) return

            data class RawTrade(val isBuy: Boolean, val erg: Double, val tokens: Double, val timestamp: Long, val txId: String, val priceImpact: Double?)
            val rawTrades = mutableListOf<RawTrade>()
            var vol24h = 0.0; var vol7d = 0.0
            val cutoff24h = nowMs - 24 * 3_600_000L
            val cutoff7d = nowMs - 7 * 24 * 3_600_000L

            for (i in 0 until boxes.size - 1) {
                val newer = boxes[i]; val older = boxes[i + 1]
                val newerErg = (newer["value"] as? Number)?.toLong() ?: continue
                val olderErg = (older["value"] as? Number)?.toLong() ?: continue
                val newerAssets = newer["assets"] as? List<Map<String, Any>> ?: continue
                val olderAssets = older["assets"] as? List<Map<String, Any>> ?: continue
                if (newerAssets.size < 3 || olderAssets.size < 3) continue
                val newerToken = (newerAssets[2]["amount"] as? Number)?.toLong() ?: continue
                val olderToken = (olderAssets[2]["amount"] as? Number)?.toLong() ?: continue
                val ergDelta = newerErg - olderErg; val tokenDelta = newerToken - olderToken
                val ergAbs = Math.abs(ergDelta) / 1_000_000_000.0
                val tokenAbs = Math.abs(tokenDelta) / tokenDiv
                if (ergAbs < 0.001 && tokenAbs < 0.000001) continue
                val txId = (newer["transactionId"] as? String) ?: ""
                val height = (newer["inclusionHeight"] as? Number)?.toInt() ?: 0
                val estimatedTs = if (currentHeight > 0 && height > 0)
                    nowMs - (currentHeight - height) * 120_000L else 0L
                // Price impact: (price_after - price_before) / price_before * 100
                val priceImpact = if (olderErg > 0 && olderToken > 0 && newerErg > 0 && newerToken > 0) {
                    val priceBefore = olderErg.toDouble() / olderToken.toDouble()
                    val priceAfter  = newerErg.toDouble() / newerToken.toDouble()
                    ((priceAfter - priceBefore) / priceBefore) * 100.0
                } else null
                rawTrades.add(RawTrade(ergDelta > 0, ergAbs, tokenAbs, estimatedTs, txId, priceImpact))
                if (estimatedTs >= cutoff24h) vol24h += ergAbs
                if (estimatedTs >= cutoff7d) vol7d += ergAbs
            }

            _uiState.value = _uiState.value.copy(
                poolTrades = rawTrades.map { PoolTrade(it.isBuy, it.erg, it.tokens, it.timestamp, it.txId, priceImpact = it.priceImpact) },
                isLoadingPoolTrades = false, poolVolume24h = vol24h, poolVolume7d = vol7d
            )

            val enriched = coroutineScope {
                rawTrades.map { raw ->
                    async {
                        try {
                            if (raw.txId.isEmpty()) return@async PoolTrade(raw.isBuy, raw.erg, raw.tokens, raw.timestamp, raw.txId, priceImpact = raw.priceImpact)
                            val tx = client.api.getTransactionById(raw.txId)
                                ?: return@async PoolTrade(raw.isBuy, raw.erg, raw.tokens, raw.timestamp, raw.txId, priceImpact = raw.priceImpact)
                            val realTs = (tx["timestamp"] as? Number)?.toLong() ?: raw.timestamp
                            @Suppress("UNCHECKED_CAST")
                            val outputs = tx["outputs"] as? List<Map<String, Any>> ?: emptyList()
                            val trader = if (raw.isBuy) {
                                outputs.firstOrNull { out ->
                                    val assets = out["assets"] as? List<Map<String, Any>> ?: emptyList()
                                    val hasPoolNft = assets.any { (it["tokenId"] as? String) == poolNft }
                                    val hasTradedToken = tokenId.isNotEmpty() && assets.any { (it["tokenId"] as? String) == tokenId }
                                    !hasPoolNft && hasTradedToken
                                }?.get("address") as? String ?: ""
                            } else {
                                outputs.filter { out ->
                                    val assets = out["assets"] as? List<Map<String, Any>> ?: emptyList()
                                    !assets.any { (it["tokenId"] as? String) == poolNft }
                                }.maxByOrNull { (it["value"] as? Number)?.toLong() ?: 0L }?.get("address") as? String ?: ""
                            }
                            PoolTrade(raw.isBuy, raw.erg, raw.tokens, realTs, raw.txId, trader, priceImpact = raw.priceImpact)
                        } catch (e: Exception) { PoolTrade(raw.isBuy, raw.erg, raw.tokens, raw.timestamp, raw.txId, priceImpact = raw.priceImpact) }
                    }
                }.awaitAll()
            }
            val vol24hReal = enriched.filter { it.timestamp >= cutoff24h }.sumOf { it.ergAmount }
            val vol7dReal = enriched.filter { it.timestamp >= cutoff7d }.sumOf { it.ergAmount }
            _uiState.value = _uiState.value.copy(poolTrades = enriched, poolVolume24h = vol24hReal, poolVolume7d = vol7dReal)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "fetchPoolTrades failed: ${e.message}")
            _uiState.value = _uiState.value.copy(isLoadingPoolTrades = false)
        }
    }

    // ─── Token USD values ─────────────────────────────────────────────────

    fun fetchTokenUsdValues(
        walletTokens: Map<String, Long>,
        whitelistedPools: List<PoolMapping>,
        discoveredPools: List<PoolMapping>,
        includeUnconfirmed: Boolean,
        force: Boolean = false
    ) {
        if (isFetchingTokenValues) return
        val client = nodeClient ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oracleBox = client.getPoolBox(
                    com.piggytrade.piggytrade.stablecoin.use.UseConfig.ORACLE_NFT, false
                )
                if (oracleBox != null) {
                    @Suppress("UNCHECKED_CAST")
                    val regs = oracleBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
                    val r4 = regs["R4"] as? String ?: "0500"
                    val oracleRate = com.piggytrade.piggytrade.stablecoin.VlqCodec.decode(r4)
                    if (oracleRate > 0) {
                        val usdPerErg = 1_000_000_000.0 / oracleRate.toDouble()
                        withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(ergPriceUsd = usdPerErg) }
                        fetchTokenUsdValuesInternal(usdPerErg, walletTokens, whitelistedPools, discoveredPools, includeUnconfirmed, force)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Oracle price fetch failed: ${e.message}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchTokenUsdValuesInternal(
        ergPrice: Double,
        walletTokens: Map<String, Long>,
        whitelistedPools: List<PoolMapping>,
        discoveredPools: List<PoolMapping>,
        includeUnconfirmed: Boolean,
        force: Boolean
    ) {
        if (isFetchingTokenValues) return
        if (walletTokens.isEmpty()) return
        if (!force && _uiState.value.tokenUsdValues.isNotEmpty()) return
        val allPools = (whitelistedPools + discoveredPools).filter { !it.data.containsKey("id_in") && it.pid.isNotEmpty() }
        if (allPools.isEmpty()) return
        isFetchingTokenValues = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                data class PoolBoxResult(val pool: PoolMapping, val tokenId: String, val ergReserve: Long, val tokenReserve: Long, val fee: Double)
                val poolResults = mutableListOf<PoolBoxResult>()
                val matchedTokenIds = mutableSetOf<String>()
                val walletTokenSet = walletTokens.keys.toSet()
                val poolsNeedingBoxData = mutableListOf<PoolMapping>()

                for (pool in allPools) {
                    val tokenId = pool.data["id"] as? String ?: continue
                    if (tokenId !in walletTokenSet || tokenId in matchedTokenIds) continue
                    val amount = walletTokens[tokenId] ?: 0L
                    if (amount <= 0L) continue
                    val cached = synchronized(poolBoxDataCache) { poolBoxDataCache[pool.pid] }
                    if (cached != null) {
                        val assets = cached["assets"] as? List<Map<String, Any>>
                        if (assets != null && assets.size >= 3) {
                            poolResults.add(PoolBoxResult(pool, tokenId, (cached["value"] as? Number)?.toLong() ?: 0L, (assets[2]["amount"] as? Number)?.toLong() ?: 0L, pool.data["fee"] as? Double ?: 0.003))
                            matchedTokenIds.add(tokenId); continue
                        }
                    }
                    poolsNeedingBoxData.add(pool)
                }
                for (pool in allPools) {
                    if (pool.data.containsKey("id")) continue
                    val cached = synchronized(poolBoxDataCache) { poolBoxDataCache[pool.pid] } ?: continue
                    val assets = cached["assets"] as? List<Map<String, Any>> ?: continue
                    if (assets.size < 3) continue
                    val tokenId = assets[2]["tokenId"] as? String ?: continue
                    if (tokenId !in walletTokenSet || tokenId in matchedTokenIds) continue
                    poolResults.add(PoolBoxResult(pool, tokenId, (cached["value"] as? Number)?.toLong() ?: 0L, (assets[2]["amount"] as? Number)?.toLong() ?: 0L, pool.data["fee"] as? Double ?: 0.003))
                    matchedTokenIds.add(tokenId)
                }
                val unmatchedTokens = walletTokenSet - matchedTokenIds
                if (unmatchedTokens.isNotEmpty()) {
                    for (pool in allPools) {
                        if (pool.data.containsKey("id")) continue
                        if (pool.pid in poolBoxDataCache) continue
                        poolsNeedingBoxData.add(pool)
                    }
                }
                if (poolsNeedingBoxData.isNotEmpty()) {
                    coroutineScope {
                        poolsNeedingBoxData.map { pool ->
                            async {
                                try {
                                    nodePool.withRetry { poolClient ->
                                        val box = poolClient.getPoolBox(pool.pid, includeUnconfirmed) ?: return@withRetry
                                        val assets = box["assets"] as? List<Map<String, Any>> ?: return@withRetry
                                        if (assets.size < 3) return@withRetry
                                        val tokenId = assets[2]["tokenId"] as? String ?: return@withRetry
                                        synchronized(poolResults) {
                                            poolResults.add(PoolBoxResult(pool, tokenId, (box["value"] as? Number)?.toLong() ?: 0L, (assets[2]["amount"] as? Number)?.toLong() ?: 0L, pool.data["fee"] as? Double ?: 0.003))
                                        }
                                        synchronized(poolBoxDataCache) { poolBoxDataCache[pool.pid] = box }
                                    }
                                } catch (e: Exception) { /* skip */ }
                            }
                        }.awaitAll()
                    }
                }
                val results = mutableMapOf<String, Double>()
                for ((walletTokenId, amount) in walletTokens) {
                    if (amount <= 0L) continue
                    val matchingPools = poolResults.filter { it.tokenId == walletTokenId }
                    if (matchingPools.isEmpty()) continue
                    val bestPool = matchingPools.sortedWith(compareByDescending<PoolBoxResult> { it.pool.isWhitelisted }.thenByDescending { it.ergReserve }).first()
                    if (bestPool.tokenReserve > 0 && bestPool.ergReserve > 0) {
                        val feeNum = (1.0 - bestPool.fee) * 1000.0
                        val outputNanoErg = (amount.toDouble() * bestPool.ergReserve.toDouble() * feeNum) / (bestPool.tokenReserve.toDouble() * 1000.0 + amount.toDouble() * feeNum)
                        val usdValue = (outputNanoErg / 1_000_000_000.0) * ergPrice
                        if (usdValue > 0.0) results[walletTokenId] = usdValue
                    }
                }
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(tokenUsdValues = results) }
            } finally { isFetchingTokenValues = false }
        }
    }

    fun getTokenUsdValue(tokenId: String): Double? = _uiState.value.tokenUsdValues[tokenId]

    // ─── Ecosystem ───────────────────────────────────────────────────────

    fun fetchEcosystemData(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _uiState.value.isLoadingEcosystem) return
        if (!forceRefresh && now - _uiState.value.ecosystemLastFetched < 60_000) return
        val client = nodeClient ?: return
        if (_uiState.value.ecosystemActivity.isEmpty()) {
            val cached = loadEcosystemCache()
            if (cached.isNotEmpty()) { ecosystemActivityCache = cached; _uiState.value = _uiState.value.copy(ecosystemActivity = cached) }
        }
        _uiState.value = _uiState.value.copy(isLoadingEcosystem = true)
        ecosystemPage = 0
        if (_uiState.value.ergPriceUsd == null) fetchErgPrice()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tvlJob = async { fetchEcosystemTvlInternal(client) }
                val activityJob = async { fetchEcosystemActivityInternal(client) }
                val tvl = tvlJob.await(); val allActivity = activityJob.await()
                saveEcosystemCache(allActivity)
                withContext(Dispatchers.Main) {
                    ecosystemActivityCache = allActivity
                    _uiState.value = _uiState.value.copy(ecosystemTvl = tvl, ecosystemActivity = allActivity, isLoadingEcosystem = false, ecosystemLastFetched = System.currentTimeMillis())
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Ecosystem fetch failed: ${e.message}")
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(isLoadingEcosystem = false) }
            }
        }
    }

    fun fetchMoreEcosystemActivity() {
        if (_uiState.value.isLoadingEcosystem) return
        val client = nodeClient ?: return
        _uiState.value = _uiState.value.copy(isLoadingEcosystem = true)
        ecosystemPage++
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val more = fetchEcosystemActivityInternal(client, offset = ecosystemPage * 30)
                withContext(Dispatchers.Main) {
                    ecosystemActivityCache = (ecosystemActivityCache + more).distinctBy { it.txId }.sortedByDescending { it.timestamp }
                    _uiState.value = _uiState.value.copy(ecosystemActivity = ecosystemActivityCache, isLoadingEcosystem = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(isLoadingEcosystem = false) }
            }
        }
    }

    private fun saveEcosystemCache(activity: List<EcosystemTx>) {
        try {
            val arr = org.json.JSONArray()
            for (tx in activity.take(100)) {
                arr.put(org.json.JSONObject().apply {
                    put("txId", tx.txId); put("protocol", tx.protocol); put("timestamp", tx.timestamp)
                    put("traderAddress", tx.traderAddress); put("sent", tx.sent); put("received", tx.received)
                    put("priceImpact", tx.priceImpact ?: org.json.JSONObject.NULL); put("isConfirmed", tx.isConfirmed)
                })
            }
            java.io.File(getApplication<Application>().filesDir, "ecosystem_cache.json").writeText(arr.toString())
        } catch (e: Exception) { if (BuildConfig.DEBUG) Log.d(TAG, "Save ecosystem cache failed: ${e.message}") }
    }

    private fun loadEcosystemCache(): List<EcosystemTx> {
        return try {
            val file = java.io.File(getApplication<Application>().filesDir, "ecosystem_cache.json")
            if (!file.exists()) return emptyList()
            val arr = org.json.JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                EcosystemTx(txId = obj.getString("txId"), protocol = obj.getString("protocol"), timestamp = obj.getLong("timestamp"),
                    traderAddress = obj.optString("traderAddress", ""), sent = obj.optString("sent", ""),
                    received = obj.optString("received", ""),
                    priceImpact = if (obj.isNull("priceImpact")) null else obj.getDouble("priceImpact"),
                    isConfirmed = obj.optBoolean("isConfirmed", true))
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun fetchEcosystemTvlInternal(client: NodeClient): Map<String, Double> = coroutineScope {
        val mediaType = "application/json".toMediaTypeOrNull()
        val contracts = mapOf(
            "Spectrum DEX" to NetworkConfig.SPECTRUM_ADDRESS,
            "USE Bank" to com.piggytrade.piggytrade.stablecoin.use.UseConfig.BANK_ADDRESS,
            "USE Pool" to (NetworkConfig.USE_CONFIG["pool_address"] as String),
            "DexyGold Bank" to com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.BANK_ADDRESS,
            "DexyGold Pool" to (NetworkConfig.DEXYGOLD_CONFIG["pool_address"] as String)
        )
        contracts.map { (name, address) ->
            async {
                try {
                    nodePool.withRetry { poolClient ->
                        val jsonAddress = "\"$address\""
                        val isMultiBox = name.contains("Spectrum")
                        var totalErg = 0L; var maxErg = 0L; var offset = 0
                        val pageLimit = if (isMultiBox) 2 else 1
                        for (page in 0 until pageLimit) {
                            val boxes = poolClient.api.getUnspentBoxesByAddressPost(
                                offset = offset, limit = 50, includeUnconfirmed = false, excludeMempoolSpent = false,
                                address = jsonAddress.toRequestBody(mediaType)
                            )
                            for (box in boxes) {
                                val v = (box["value"] as? Number)?.toLong() ?: 0L
                                totalErg += v; if (v > maxErg) maxErg = v
                            }
                            if (boxes.size < 50) break; offset += 50
                        }
                        name to ((if (isMultiBox) totalErg else maxErg).toDouble() / 1_000_000_000.0)
                    }
                } catch (e: Exception) { name to 0.0 }
            }
        }.awaitAll().toMap()
    }

    private val ecosystemProtocols = mapOf(
        NetworkConfig.SPECTRUM_ADDRESS to "DEX Swap",
        NetworkConfig.SPECTRUM_TOKEN_ADDRESS to "DEX T2T",
        (NetworkConfig.USE_CONFIG["lp_swap_address"] as String) to "LP Swap",
        com.piggytrade.piggytrade.stablecoin.use.UseConfig.BANK_ADDRESS to "USE Bank",
        com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.BANK_ADDRESS to "DexyGold Bank",
        "MUbV38YgqHy7XbsoXWF5z7EZm524Ybdwe5p9WDrbhruZRtehkRPT92imXer2eTkjwPDfboa1pR3zb3deVKVq3H7Xt98qcTqLuSBSbHb7izzo5jphEpcnqyKJ2xhmpNPVvmtbdJNdvdopPrHHDBbAGGeW7XYTQwEeoRfosXzcDtiGgw97b2aqjTsNFmZk7khBEQywjYfmoDc9nUCJMZ3vbSspnYo3LarLe55mh2Np8MNJqUN9APA6XkhZCrTTDRZb1B4krgFY1sVMswg2ceqguZRvC9pqt3tUUxmSnB24N6dowfVJKhLXwHPbrkHViBv1AKAJTmEaQW2DN1fRmD9ypXxZk8GXmYtxTtrj3BiunQ4qzUCu1eGzxSREjpkFSi2ATLSSDqUwxtRz639sHM6Lav4axoJNPCHbY8pvuBKUxgnGRex8LEGM8DeEJwaJCaoy8dBw9Lz49nq5mSsXLeoC4xpTUmp47Bh7GAZtwkaNreCu74m9rcZ8Di4w1cmdsiK1NWuDh9pJ2Bv7u3EfcurHFVqCkT3P86JUbKnXeNxCypfrWsFuYNKYqmjsix82g9vWcGMmAcu5nagxD4iET86iE2tMMfZZ5vqZNvntQswJyQqv2Wc6MTh4jQx1q2qJZCQe4QdEK63meTGbZNNKMctHQbp3gRkZYNrBtxQyVtNLR8xEY8zGp85GeQKbb37vqLXxRpGiigAdMe3XZA4hhYPmAAU5hpSMYaRAjtvvMT3bNiHRACGrfjvSsEG9G2zY5in2YWz5X9zXQLGTYRsQ4uNFkYoQRCBdjNxGv6R58Xq74zCgt19TxYZ87gPWxkXpWwTaHogG1eps8WXt8QzwJ9rVx6Vu9a5GjtcGsQxHovWmYixgBU8X9fPNJ9UQhYyAWbjtRSuVBtDAmoV1gCBEPwnYVP5GCGhCocbwoYhZkZjFZy6ws4uxVLid3FxuvhWvQrVEDYp7WRvGXbNdCbcSXnbeTrPMey1WPaXX" to "SigmaUSD Bank"
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchEcosystemActivityInternal(client: NodeClient, offset: Int = 0): List<EcosystemTx> = coroutineScope {
        val mediaType = "application/json".toMediaTypeOrNull()
        ecosystemProtocols.map { (address, protocolLabel) ->
            async {
                try {
                    nodePool.withRetry { poolClient ->
                        val resp = poolClient.api.getTransactionsByAddress(offset = offset, limit = 30, address = "\"$address\"".toRequestBody(mediaType))
                        val items = resp["items"] as? List<Map<String, Any>> ?: emptyList()
                        items.mapNotNull { tx -> parseEcosystemTx(tx, address, protocolLabel) }
                    }
                } catch (e: Exception) { emptyList() }
            }
        }.awaitAll().flatten().distinctBy { it.txId }.sortedByDescending { it.timestamp }
    }

    private fun getTokenName(tokenId: String): String = tokenRepository.getTokenName(tokenId)
    private fun formatBalance(tokenId: String, amount: Long): String {
        if (tokenId == "ERG") return (amount.toDouble() / 1_000_000_000.0).let { java.text.DecimalFormat("0.#####").format(it) }
        val decimals = tokenRepository.getTokenDecimals(tokenId)
        if (decimals == 0) return amount.toString()
        return String.format("%.${decimals}f", amount.toDouble() / Math.pow(10.0, decimals.toDouble()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEcosystemTx(tx: Map<String, Any>, protocolAddress: String, protocolLabel: String): EcosystemTx? {
        val txId = tx["id"] as? String ?: return null
        val timestamp = (tx["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val numConf = (tx["numConfirmations"] as? Number)?.toInt() ?: 0
        val isConfirmed = numConf > 0
        val rawInputs = tx["inputs"] as? List<Map<String, Any>> ?: emptyList()
        val rawOutputs = tx["outputs"] as? List<Map<String, Any>> ?: emptyList()

        var effectiveLabel = protocolLabel
        if (protocolLabel.contains("Bank")) {
            val allTokenIds = mutableSetOf<String>()
            for (box in rawInputs + rawOutputs) for (a in (box["assets"] as? List<Map<String, Any>> ?: emptyList())) (a["tokenId"] as? String)?.let { allTokenIds.add(it) }
            effectiveLabel = when {
                allTokenIds.contains(com.piggytrade.piggytrade.stablecoin.use.UseConfig.FREEMINT_NFT) -> "USE Freemint"
                allTokenIds.contains(com.piggytrade.piggytrade.stablecoin.use.UseConfig.ARBMINT_NFT) -> "USE Arbmint"
                allTokenIds.contains(com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.FREEMINT_NFT) -> "DexyGold Freemint"
                allTokenIds.contains(com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.ARBMINT_NFT) -> "DexyGold Arbmint"
                else -> protocolLabel
            }
        } else if (protocolLabel == "LP Swap") {
            val allTokenIds = mutableSetOf<String>()
            for (box in rawInputs + rawOutputs) for (a in (box["assets"] as? List<Map<String, Any>> ?: emptyList())) (a["tokenId"] as? String)?.let { allTokenIds.add(it) }
            val useLpNft = NetworkConfig.USE_CONFIG["lp_nft"] as? String ?: ""
            val dexyLpNft = NetworkConfig.DEXYGOLD_CONFIG["lp_nft"] as? String ?: ""
            effectiveLabel = when {
                useLpNft.isNotEmpty() && allTokenIds.contains(useLpNft) -> "USE LP Swap"
                dexyLpNft.isNotEmpty() && allTokenIds.contains(dexyLpNft) -> "DexyGold LP Swap"
                else -> "LP Swap"
            }
        }

        val knownAddresses = ecosystemProtocols.keys + NetworkConfig.KNOWN_PROTOCOLS.keys
        var traderAddr = ""; var traderFoundInInputs = false
        for (inp in rawInputs) {
            val addr = inp["address"] as? String ?: ""
            if (addr.isNotEmpty() && !knownAddresses.any { addr.startsWith(it.take(30)) }) {
                val inOutputs = rawOutputs.any { (it["address"] as? String ?: "").startsWith(addr.take(30)) }
                if (inOutputs) { traderAddr = addr; traderFoundInInputs = true }
                break
            }
        }
        if (traderAddr.isEmpty()) {
            for (outp in rawOutputs) {
                val addr = outp["address"] as? String ?: ""
                if (addr.isNotEmpty() && !knownAddresses.any { addr.startsWith(it.take(30)) }) {
                    val assets = outp["assets"] as? List<Map<String, Any>> ?: emptyList()
                    val value = (outp["value"] as? Number)?.toLong() ?: 0L
                    if (assets.isNotEmpty() || value > 10_000_000L) { traderAddr = addr; break }
                }
            }
        }
        if (traderAddr.isEmpty()) for (outp in rawOutputs) {
            val addr = outp["address"] as? String ?: ""
            if (addr.isNotEmpty() && !knownAddresses.any { addr.startsWith(it.take(30)) }) { traderAddr = addr; break }
        }
        if (traderAddr.isEmpty()) traderAddr = "Unknown"

        val sentParts = mutableListOf<String>(); val recvParts = mutableListOf<String>()
        val isDexSwap = effectiveLabel.contains("DEX") || effectiveLabel.contains("LP Swap")
        val isProxySwap = isDexSwap && !traderFoundInInputs

        if (isProxySwap) {
            val poolInput = rawInputs.find { box -> (box["address"] as? String ?: "").startsWith(protocolAddress.take(30)) && (box["assets"] as? List<*> ?: emptyList<Any>()).size >= 3 }
            val poolOutput = rawOutputs.find { box -> (box["address"] as? String ?: "").startsWith(protocolAddress.take(30)) && (box["assets"] as? List<*> ?: emptyList<Any>()).size >= 3 }
            if (poolInput != null && poolOutput != null) {
                val poolErgIn = (poolInput["value"] as? Number)?.toLong() ?: 0L
                val poolErgOut = (poolOutput["value"] as? Number)?.toLong() ?: 0L
                val poolErgDelta = poolErgOut - poolErgIn
                val pInAssets = poolInput["assets"] as? List<Map<String, Any>> ?: emptyList()
                val pOutAssets = poolOutput["assets"] as? List<Map<String, Any>> ?: emptyList()
                val lpTokenIn = (pInAssets.getOrNull(1)?.get("amount") as? Number)?.toLong() ?: 0L
                val lpTokenOut = (pOutAssets.getOrNull(1)?.get("amount") as? Number)?.toLong() ?: 0L
                val lpDelta = lpTokenOut - lpTokenIn
                val tokenId = pInAssets.getOrNull(2)?.get("tokenId") as? String ?: ""
                val poolTokenIn = (pInAssets.getOrNull(2)?.get("amount") as? Number)?.toLong() ?: 0L
                val poolTokenOut = (pOutAssets.getOrNull(2)?.get("amount") as? Number)?.toLong() ?: 0L
                val poolTokenDelta = poolTokenOut - poolTokenIn
                when {
                    lpDelta > 0 -> {
                        effectiveLabel = "DEX LP Withdraw"
                        val lpTokenId = pInAssets.getOrNull(1)?.get("tokenId") as? String ?: ""
                        if (lpTokenId.isNotEmpty()) sentParts.add("${formatBalance(lpTokenId, lpDelta)} ${getTokenName(lpTokenId)}")
                        if (poolErgDelta < -1_000_000L) recvParts.add("${String.format("%.4f", Math.abs(poolErgDelta).toDouble() / 1e9)} ERG")
                        if (tokenId.isNotEmpty() && poolTokenDelta < 0) { val name = getTokenName(tokenId); if (name != tokenId) recvParts.add("${formatBalance(tokenId, Math.abs(poolTokenDelta))} $name") }
                    }
                    lpDelta < 0 -> {
                        effectiveLabel = "DEX LP Deposit"
                        if (poolErgDelta > 2_000_000L) sentParts.add("${String.format("%.4f", poolErgDelta.toDouble() / 1e9)} ERG")
                        if (tokenId.isNotEmpty() && poolTokenDelta > 0) { val name = getTokenName(tokenId); if (name != tokenId) sentParts.add("${formatBalance(tokenId, poolTokenDelta)} $name") }
                        val lpTokenId = pInAssets.getOrNull(1)?.get("tokenId") as? String ?: ""
                        if (lpTokenId.isNotEmpty()) recvParts.add("${formatBalance(lpTokenId, Math.abs(lpDelta))} ${getTokenName(lpTokenId)}")
                    }
                    else -> {
                        if (poolErgDelta > 2_000_000L) sentParts.add("${String.format("%.4f", poolErgDelta.toDouble() / 1e9)} ERG")
                        else if (poolErgDelta < -1_000_000L) recvParts.add("${String.format("%.4f", Math.abs(poolErgDelta).toDouble() / 1e9)} ERG")
                        if (tokenId.isNotEmpty() && poolTokenDelta != 0L) {
                            val name = getTokenName(tokenId)
                            if (!name.startsWith("Pool") && name != tokenId) {
                                val formatted = formatBalance(tokenId, Math.abs(poolTokenDelta))
                                if (poolTokenDelta > 0) sentParts.add("$formatted $name") else recvParts.add("$formatted $name")
                            }
                        }
                    }
                }
            }
        } else {
            val traderInputErg = rawInputs.filter { (it["address"] as? String ?: "").startsWith(traderAddr.take(30)) }.sumOf { (it["value"] as? Number)?.toLong() ?: 0L }
            val traderInputTokens = mutableMapOf<String, Long>()
            for (inp in rawInputs) { if (!((inp["address"] as? String ?: "").startsWith(traderAddr.take(30)))) continue; for (a in (inp["assets"] as? List<Map<String, Any>> ?: emptyList())) { val tid = a["tokenId"] as? String ?: continue; traderInputTokens[tid] = (traderInputTokens[tid] ?: 0L) + ((a["amount"] as? Number)?.toLong() ?: 0L) } }
            val traderOutputErg = rawOutputs.filter { (it["address"] as? String ?: "").startsWith(traderAddr.take(30)) }.sumOf { (it["value"] as? Number)?.toLong() ?: 0L }
            val traderOutputTokens = mutableMapOf<String, Long>()
            for (outp in rawOutputs) { if (!((outp["address"] as? String ?: "").startsWith(traderAddr.take(30)))) continue; for (a in (outp["assets"] as? List<Map<String, Any>> ?: emptyList())) { val tid = a["tokenId"] as? String ?: continue; traderOutputTokens[tid] = (traderOutputTokens[tid] ?: 0L) + ((a["amount"] as? Number)?.toLong() ?: 0L) } }
            val ergDiff = traderOutputErg - traderInputErg
            if (ergDiff < -2_000_000L) sentParts.add("${String.format("%.4f", Math.abs(ergDiff).toDouble() / 1e9)} ERG")
            else if (ergDiff > 1_000_000L) recvParts.add("${String.format("%.4f", ergDiff.toDouble() / 1e9)} ERG")
            val isBankTx = effectiveLabel.contains("Bank") || effectiveLabel.contains("Freemint") || effectiveLabel.contains("Arbmint")
            val stablecoinTokenIds = if (isBankTx) setOf(
                com.piggytrade.piggytrade.stablecoin.use.UseConfig.USE_TOKEN_ID,
                com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.DEXY_TOKEN_ID,
                com.piggytrade.piggytrade.stablecoin.sigmausd.SigmaUsdConfig.SIGUSD_TOKEN_ID,
                com.piggytrade.piggytrade.stablecoin.sigmausd.SigmaUsdConfig.SIGRSV_TOKEN_ID
            ) else null
            for (tid in traderInputTokens.keys + traderOutputTokens.keys) {
                val diff = (traderOutputTokens[tid] ?: 0L) - (traderInputTokens[tid] ?: 0L)
                if (diff == 0L) continue
                if (stablecoinTokenIds != null && tid !in stablecoinTokenIds) continue
                val name = getTokenName(tid)
                if (name.startsWith("Pool") || name == tid) continue
                val formatted = formatBalance(tid, Math.abs(diff))
                if (diff < 0) sentParts.add("$formatted $name") else recvParts.add("$formatted $name")
            }
        }

        if (sentParts.isEmpty() && recvParts.isEmpty()) return null

        var priceImpact: Double? = null
        val isSwap = (effectiveLabel.contains("DEX") || effectiveLabel.contains("LP Swap")) && !effectiveLabel.contains("Withdraw") && !effectiveLabel.contains("Deposit")
        if (isSwap) {
            val poolAddr = when {
                effectiveLabel.contains("USE") -> NetworkConfig.USE_CONFIG["pool_address"] as? String ?: protocolAddress
                effectiveLabel.contains("DexyGold") -> NetworkConfig.DEXYGOLD_CONFIG["pool_address"] as? String ?: protocolAddress
                else -> protocolAddress
            }
            priceImpact = calculatePriceImpact(rawInputs, rawOutputs, poolAddr)
        }

        return EcosystemTx(txId = txId, protocol = effectiveLabel, timestamp = timestamp, traderAddress = traderAddr,
            sent = sentParts.joinToString(" + ").ifEmpty { "—" }, received = recvParts.joinToString(" + ").ifEmpty { "—" },
            priceImpact = priceImpact, isConfirmed = isConfirmed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun calculatePriceImpact(inputs: List<Map<String, Any>>, outputs: List<Map<String, Any>>, protocolAddress: String): Double? {
        return try {
            val poolInput = inputs.find { (it["address"] as? String ?: "").startsWith(protocolAddress.take(30)) && (it["assets"] as? List<*> ?: emptyList<Any>()).size >= 3 } ?: return null
            val poolOutput = outputs.find { (it["address"] as? String ?: "").startsWith(protocolAddress.take(30)) && (it["assets"] as? List<*> ?: emptyList<Any>()).size >= 3 } ?: return null
            val ergBefore = (poolInput["value"] as? Number)?.toLong() ?: return null
            val ergAfter = (poolOutput["value"] as? Number)?.toLong() ?: return null
            val assetsBefore = poolInput["assets"] as? List<Map<String, Any>> ?: return null
            val assetsAfter = poolOutput["assets"] as? List<Map<String, Any>> ?: return null
            if (assetsBefore.size < 3 || assetsAfter.size < 3) return null
            val tokenBefore = (assetsBefore[2]["amount"] as? Number)?.toLong() ?: return null
            val tokenAfter = (assetsAfter[2]["amount"] as? Number)?.toLong() ?: return null
            if (tokenBefore <= 0 || tokenAfter <= 0 || ergBefore <= 0) return null
            val priceBefore = ergBefore.toDouble() / tokenBefore.toDouble()
            val priceAfter = ergAfter.toDouble() / tokenAfter.toDouble()
            ((priceAfter - priceBefore) / priceBefore) * 100.0
        } catch (_: Exception) { null }
    }
}
