package com.piggytrade.piggytrade.ui.swap
import com.piggytrade.piggytrade.BuildConfig
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*
import com.piggytrade.piggytrade.stablecoin.EligibilityResult
import com.piggytrade.piggytrade.stablecoin.MintQuote
import com.piggytrade.piggytrade.stablecoin.RedeemQuote
import com.piggytrade.piggytrade.stablecoin.StablecoinRegistry

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.piggytrade.piggytrade.blockchain.Trader
import com.piggytrade.piggytrade.blockchain.TxBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.piggytrade.piggytrade.protocol.NetworkConfig
import com.piggytrade.piggytrade.data.PreferenceManager
import com.piggytrade.piggytrade.data.TokenRepository
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.network.NodePool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SwapState(
    val fromAsset: String = "",
    val toAsset: String = "",
    val fromAmount: String = "",
    val toQuote: String = "--",
    val priceImpact: Double = 0.0,
    val lpFee: Double = 0.0,
    val isLoadingQuote: Boolean = false,
    val isSimulation: Boolean = false,
    val minerFee: Double = 0.0011,  // Standard Ergo minimum fee = 1,100,000 nanoErg
    val selectedWallet: String = "",
    val selectedAddress: String = "",
    val walletErgBalance: Double = 0.0,
    val walletTokens: Map<String, Long> = emptyMap(),
    val isLoadingWallet: Boolean = false,
    val favorites: List<String> = com.piggytrade.piggytrade.protocol.NetworkConfig.DEFAULT_FAVORITES.take(4),
    val nodes: List<String> = emptyList(),
    val selectedNodeIndex: Int = 0,
    val debugMode: Boolean = false,
    val tokens: List<String> = emptyList(),
    val wallets: List<String> = emptyList(),
    val selectionContext: String = "", // "from", "to", "wallet"
    val firstFavoriteSelectedIndex: Int? = null,
    val fromPulseTrigger: Long = 0L,
    val toPulseTrigger: Long = 0L,
    val fromBalance: String = "0.00",
    val toBalance: String = "0.00",
    val isEditFavoritesMode: Boolean = false,
    val editingFavoriteIndex: Int? = null,
    val isBuildingTx: Boolean = false,
    val preparedTxData: Map<String, Any>? = null,
    val unsignedTxJson: String = "",
    val reviewParams: ReviewParams? = null,
    val txSuccessData: TxSuccessData? = null,
    val includeUnconfirmed: Boolean = true,
    val networkTrades: List<NetworkTransaction> = emptyList(),
    val historyOffset: Int = 0,
    val isLoadingHistory: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val whitelistedPools: List<PoolMapping> = emptyList(),
    val discoveredPools: List<PoolMapping> = emptyList(),
    val isLoadingMapping: Boolean = false,
    val isWalletExpanded: Boolean = false,
    val numFavorites: Int = 8,
    val showFavorites: Boolean = false,
    val activeSelector: String? = null, // "from", "to", "fav", "wallet"
    val nodeUrl: String = "",
    val isToAssetFavorite: Boolean = false,
    val serviceFee: Double = 0.0,
    val allowHttpNodes: Boolean = false,
    val activeTab: String = "dex", // "dex", "wallet", "bank", "portfolio", "ecosystem"

    // ─── PORTFOLIO STATE ─────────────────────────────────────────────────
    val ergPriceUsd: Double? = null,
    val ergPriceHistory: List<Pair<Long, Double>> = emptyList(),
    val sigUsdOracleHistory: List<Pair<Long, Double>> = emptyList(),
    val sigUsdDexHistory: List<Pair<Long, Double>> = emptyList(),
    val chartRange: String = "7D",
    val selectedChartToken: String? = null, // token name for DEX price chart, null = ERG/USD oracle
    val tokenPriceHistory: List<Pair<Long, Double>> = emptyList(), // ERG price of selected token
    val tokenUsdValues: Map<String, Double> = emptyMap(), // tokenId -> USD value of selling all on DEX
    val poolTrades: List<PoolTrade> = emptyList(),          // recent trades for selected token pair
    val isLoadingPoolTrades: Boolean = false,
    val poolVolume24h: Double = 0.0,                         // ERG volume traded in last 24h
    val poolVolume7d: Double = 0.0,                          // ERG volume traded in last 7 days
    val tokenMarketData: List<com.piggytrade.piggytrade.data.OraclePriceStore.TokenMarketData> = emptyList(),

    // ─── MARKET SYNC STATE ───────────────────────────────────────────────
    val marketSyncState: String = "idle",   // "idle", "syncing", "completed"
    val marketSyncProgress: Float = 0f,     // 0..1
    val marketSyncLabel: String = "",       // current token being synced
    val marketSyncIndex: Int = 0,           // tokens completed
    val marketSyncTotal: Int = 0,           // total tokens
    val lastMarketSyncMs: Long = 0L,        // timestamp of last completed sync
    val marketSyncIncomplete: Boolean = false, // true if sync was stopped before completion

    // ─── ECOSYSTEM STATE ─────────────────────────────────────────────────
    val ecosystemTvl: Map<String, Double> = emptyMap(),       // protocol name -> ERG TVL
    val ecosystemActivity: List<EcosystemTx> = emptyList(),   // sorted by timestamp desc
    val isLoadingEcosystem: Boolean = false,
    val ecosystemLastFetched: Long = 0L,                      // cache timestamp

    // ─── MULTI-ADDRESS STATE ─────────────────────────────────────────────────
    val walletAddresses: List<String> = emptyList(),         // All derived addresses for current wallet
    val selectedAddresses: Set<String> = emptySet(),          // Enabled addresses (checkboxes)
    val changeAddress: String = "",                            // Address for receiving change
    val addressBoxes: Map<String, List<Map<String, Any>>> = emptyMap(), // Per-address UTXOs
    val isScanningAddresses: Boolean = false,                 // True while scanning derivation paths

    // ─── BANK STATE (Protocol-Agnostic) ──────────────────────────────────────
    val activeProtocolId: String = "",
    val bankAmount: String = "",
    val bankQuote: MintQuote? = null,
    val bankRedeemQuote: RedeemQuote? = null,
    val bankEligibility: EligibilityResult? = null,
    val isBankLoading: Boolean = false,
    val bankError: String? = null,
    val bankMode: String = "mint",  // "mint" or "redeem"

    // Cached block headers captured at TX build time.
    // CRITICAL: must be used at sign time so sigma-rust HEIGHT == the HEIGHT used to compute R4.
    val cachedHeadersJson: String = ""
)

data class PoolMapping(
    val name: String,
    val pid: String,
    val liquidity: String = "Fetching...",
    val isWhitelisted: Boolean,
    /**
     * 0: Official (Locked)
     * 1: User Added
     * 2: Unverified
     */
    val status: Int,
    val data: Map<String, Any>,
    val key: String
)

data class SyncProgress(
    val current: Int = 0,
    val total: Int = 0,
    val isFinished: Boolean = false,
    val newTokens: List<String> = emptyList(),
    val isFirstLaunch: Boolean = false,
    val batchInfo: String = ""
)

data class TxSuccessData(
    val txId: String,
    val isSimulation: Boolean,
    val sigmaspaceUrl: String,
    val signedTxJson: String = ""
)

data class TxBox(
    val boxId: String,
    val value: Long,
    val address: String,
    val assets: List<Map<String, Any>>,
    val ergoTree: String = ""
)

data class EcosystemTx(
    val txId: String,
    val protocol: String,         // "DEX", "USE Freemint", etc.
    val timestamp: Long,
    val traderAddress: String,    // address that initiated
    val sent: String,             // human-readable sent description
    val received: String,         // human-readable received description
    val priceImpact: Double?,     // null for non-DEX txs
    val isConfirmed: Boolean
)

/** A single trade derived from the delta between two consecutive DEX pool state boxes */
data class PoolTrade(
    val isBuy: Boolean,          // true = user bought token (paid ERG), false = sold token (got ERG)
    val ergAmount: Double,       // ERG side of the swap (absolute, human-readable)
    val tokenAmount: Double,     // Token side (absolute, human-readable with decimals)
    val timestamp: Long,         // Unix ms (from tx), 0 if unknown
    val txId: String,            // Transaction ID for explorer link
    val traderAddress: String = "" // Abbreviated trader address
)


data class NetworkTransaction(
    val id: String,
    val timestamp: Long,
    val isConfirmed: Boolean,
    val netErgChange: Long,
    val netTokenChanges: Map<String, Long>,
    val inputs: List<TxBox>,
    val outputs: List<TxBox>,
    val inclusionHeight: Int?,
    val numConfirmations: Int?,
    val label: String? = null,
    val fee: Long = 0L
)


data class ReviewParams(
    val buyAmount: String,
    val buyToken: String,
    val payAmount: String,
    val payToken: String,
    val minerFee: Double,
    val serviceFee: Double,
    val isSimulation: Boolean,
    val isErgopay: Boolean,
    val ergopayUrl: String = ""
)

class SwapViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SwapViewModel"
    private val preferenceManager = PreferenceManager(application)
    private val tokenRepository = TokenRepository(application)
    val oraclePriceStore = com.piggytrade.piggytrade.data.OraclePriceStore(application)
    private var quoteJob: kotlinx.coroutines.Job? = null

    private val _uiState: MutableStateFlow<SwapState> by lazy {
        if (BuildConfig.DEBUG) Log.d(TAG, "Initializing MutableStateFlow...")
        val savedNodes = preferenceManager.loadNodes()
        val nodesMap = if (savedNodes.isEmpty()) com.piggytrade.piggytrade.protocol.NetworkConfig.NODES else savedNodes
        val nodesList = nodesMap.map { "${it.key}: ${(it.value as Map<String, Any>)["url"]}" }
        
        val savedWallets = preferenceManager.loadWallets()
        val displayWallets = mutableListOf<String>()
        savedWallets.forEach { (name, data) ->
            val isErgoPayType = (data as? Map<String, Any>)?.get("type") == "ergopay"
            if (isErgoPayType) {
                displayWallets.add("$name (Ergopay)")
            } else {
                displayWallets.add(name)
            }
        }

        // Favorites: Dynamic based on settings. 1st is ERG.
        val numFavs = (preferenceManager.loadSettings()[PreferenceManager.KEY_NUM_FAVORITES] as? Number)?.toInt() ?: 8
        val savedFavs = preferenceManager.loadFavorites(com.piggytrade.piggytrade.protocol.NetworkConfig.DEFAULT_FAVORITES)
        
        // Always maintain a padding of up to 20 favorites to avoid data loss
        val maxPossible = 20
        val baseFavs = (listOf("ERG") + savedFavs.filter { it != "ERG" && it != "?" }).take(maxPossible)
        val favorites = if (baseFavs.size < maxPossible) {
            baseFavs + List(maxPossible - baseFavs.size) { "?" }
        } else {
            baseFavs
        }

        val initialNodeUrl = preferenceManager.selectedNode
        val initialNodeIndex = nodesList.indexOfFirst { it.endsWith(initialNodeUrl) }.coerceAtLeast(0)

        val initialWalletDisplay = preferenceManager.selectedWallet.let { 
            if (it.isEmpty() || it == "Select Wallet") {
                displayWallets.firstOrNull() ?: "Select Wallet"
            } else {
                val data = savedWallets[it] as? Map<String, Any>
                if (data?.get("type") == "ergopay") "$it (Ergopay)" else it
            }
        }
        val initialWalletKey = initialWalletDisplay.replace(" (Ergopay)", "")
        val initialWalletData = savedWallets[initialWalletKey] as? Map<String, Any>
        val initialAddress = initialWalletData?.get("address") as? String ?: ""
        
        // Load multi-address info for initial wallet
        @Suppress("UNCHECKED_CAST")
        val initialAddresses = (initialWalletData?.get("addresses") as? List<String>) ?: if (initialAddress.isNotEmpty()) listOf(initialAddress) else emptyList()
        val (initSelectedAddrs, initChangeAddr) = preferenceManager.loadWalletAddressConfig(initialWalletKey)
        val initialSelected = if (initSelectedAddrs.isNotEmpty()) initSelectedAddrs else if (initialAddress.isNotEmpty()) setOf(initialAddress) else emptySet()
        val initialChange = if (initChangeAddr.isNotEmpty()) initChangeAddr else initialAddress

        MutableStateFlow(
            SwapState(
                nodes = nodesList,
                selectedNodeIndex = initialNodeIndex,
                debugMode = (preferenceManager.loadSettings()["debug_mode"] as? Boolean) ?: false,
                tokens = emptyList(), // Start empty, will be filled by loadPoolMappings
                wallets = displayWallets,
                selectedWallet = initialWalletDisplay,
                selectedAddress = initialAddress,
                walletAddresses = initialAddresses,
                selectedAddresses = initialSelected,
                changeAddress = initialChange,
                favorites = favorites,
                includeUnconfirmed = (preferenceManager.loadSettings()["include_unconfirmed"] as? Boolean) ?: true,
                numFavorites = numFavs,
                showFavorites = (preferenceManager.loadSettings()["show_favorites"] as? Boolean) ?: false,
                allowHttpNodes = (preferenceManager.loadSettings()["allow_http_nodes"] as? Boolean) ?: false
            )
        )
    }
    val uiState: StateFlow<SwapState> by lazy { _uiState.asStateFlow() }

    init {
        if (BuildConfig.DEBUG) Log.d(TAG, "SwapViewModel instance created.")
        
        // 1. Initial immediate list setup (no network)
        loadPoolMappings(fetchLiquidity = false) 
        
        // 2. Offload heavy background tasks
        viewModelScope.launch {
            // Sequential initialization
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                tokenRepository.tradeMapper
            }
            
            // 1. Initialize Node Client (wait for it)
            initializeNodeClient()
            
            // 2. Fetch Wallet Balances (now client is ready)
            fetchWalletBalances()
            
            // 3. First launch sync if needed
            if (!tokenRepository.hasTokenFiles()) {
                syncTokenList(isFirstLaunch = true)
            }

            // 4. Sync oracle prices at startup (runs on IO, survives screen changes)
            syncOraclePrices()
        }
    }

    private val _resolvedAddresses = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedAddresses: StateFlow<Map<String, String>> = _resolvedAddresses.asStateFlow()

    fun resolveErgoTree(ergoTree: String) {
        if (ergoTree.isEmpty() || _resolvedAddresses.value.containsKey(ergoTree)) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = nodeClient ?: return@launch
                val res = client.api.ergoTreeToAddress(ergoTree)
                val address = (res["address"] as? String) ?: ""
                if (address.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _resolvedAddresses.value = _resolvedAddresses.value + (ergoTree to address)
                    }
                }
            } catch (e: Exception) {
                Log.e("SwapVM", "addrToTree failed for $ergoTree: ${e.message}")
            }
        }
    }

    private var nodeClient: NodeClient? = null
    /** Pool of public nodes for distributing read-only queries */
    private val nodePool = NodePool()


    /** Track oracle sync job to prevent duplicate launches */
    private var oracleSyncJob: kotlinx.coroutines.Job? = null
    /** Track market sync job for user-controlled sync */
    private var marketSyncJob: kotlinx.coroutines.Job? = null
    /** Track token sync job for cancellation when switching tokens */
    private var tokenSyncJob: kotlinx.coroutines.Job? = null
    /** Debounce: last successful wallet balance fetch timestamp */
    private var lastWalletFetchMs = 0L

    private var trader: Trader? = null

    // ─── INIT & NODE MANAGEMENT ──────────────────────────────────────────────

    private suspend fun initializeNodeClient() {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentNodes = _uiState.value.nodes
                val index = _uiState.value.selectedNodeIndex
                if (index in currentNodes.indices) {
                    var nUrl = currentNodes[index]
                    if (nUrl.contains("(")) {
                        nUrl = nUrl.substringAfter("(").substringBefore(")")
                    } else if (nUrl.contains(": ")) {
                        nUrl = nUrl.substringAfter(": ")
                    }
                    
                    val allowHttp = _uiState.value.allowHttpNodes
                    val finalUrl = if (nUrl.startsWith("https://")) {
                        nUrl
                    } else if (nUrl.startsWith("http://") && allowHttp) {
                        nUrl
                    } else if (nUrl.startsWith("http://")) {
                        Log.w(TAG, "HTTP node blocked (setting disabled), falling back to default HTTPS node")
                        "https://ergo-node.eutxo.de"
                    } else {
                        "https://ergo-node.eutxo.de"
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Using node URL: $finalUrl")
                    val client = NodeClient(finalUrl)
                    nodeClient = client
                    trader = Trader(client, null, tokenRepository.tokens)
                    
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(nodeUrl = finalUrl)
                        loadPoolMappings(fetchLiquidity = false) 
                    }
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing node client", e)
            }
        }
    }

    private fun updateNodeClient() {
        viewModelScope.launch {
            initializeNodeClient()
        }
    }

    // ─── BALANCES & FORMATTING ───────────────────────────────────────────────

    fun formatBalance(tokenId: String, amount: Long): String {
        if (tokenId == "ERG") return (amount.toDouble() / 1_000_000_000.0).formatErg()
        val decimals = tokenRepository.getTokenDecimals(tokenId)
        if (decimals == 0) return amount.toString()
        val divisor = Math.pow(10.0, decimals.toDouble())
        return String.format("%.${decimals}f", amount.toDouble() / divisor)
    }

    private fun updateBalances() {
        val current = _uiState.value
        val walletTokens = current.walletTokens
        val walletErg = current.walletErgBalance
        
        val fAsset = getTokenId(current.fromAsset)
        val tAsset = getTokenId(current.toAsset)
        
        val fBal = if (fAsset == "ERG") {
            if (walletErg > 0.0) walletErg.formatErg() else ""
        } else {
            val amt = walletTokens[fAsset] ?: 0L
            if (amt > 0L) formatBalance(fAsset, amt) else ""
        }
        
        val tBal = if (tAsset == "ERG") {
            if (walletErg > 0.0) walletErg.formatErg() else ""
        } else {
            val amt = walletTokens[tAsset] ?: 0L
            if (amt > 0L) formatBalance(tAsset, amt) else ""
        }
        
        _uiState.value = current.copy(fromBalance = fBal, toBalance = tBal)
    }

    private fun Double.formatErg(): String {
        return java.text.DecimalFormat("0.#####").format(this)
    }

    private fun Double.formatErg(pattern: String): String {
        return java.text.DecimalFormat(pattern).format(this)
    }
    
    // Internal helper for components/screens
    companion object {
        fun formatErg(value: Double): String {
            return java.text.DecimalFormat("0.#####").format(value)
        }
    }

    fun getUserBalance(name: String): String? {
        val id = getTokenId(name)
        val current = _uiState.value
        if (id == "ERG") {
            if (current.walletErgBalance <= 0.0) return null
            return current.walletErgBalance.formatErg()
        }
        val amount = current.walletTokens[id] ?: 0L
        if (amount <= 0L) return null
        return formatBalance(id, amount)
    }

    // ─── UI STATE SETTERS (DEX) ──────────────────────────────────────────────

    fun setFromAsset(asset: String) {
        _uiState.value = _uiState.value.copy(
            fromAsset = asset,
            fromPulseTrigger = System.currentTimeMillis()
        )
        updateBalances()
        fetchQuote(delayMs = 0)
    }

    fun setToAsset(asset: String) {
        val current = _uiState.value
        val isFav = current.favorites.any { it.equals(asset, ignoreCase = true) }
        _uiState.value = current.copy(
            toAsset = asset,
            toPulseTrigger = System.currentTimeMillis(),
            isToAssetFavorite = isFav
        )
        updateBalances()
        fetchQuote(delayMs = 0)
    }

    fun setFromAmount(amount: String) {
        _uiState.value = _uiState.value.copy(fromAmount = amount)
        fetchQuote(delayMs = 400)
    }

    fun setMaxAmount() {
        _uiState.value = _uiState.value.copy(fromAmount = _uiState.value.fromBalance)
        fetchQuote(delayMs = 0)
    }

    fun swapDirection() {
        val current = _uiState.value
        if (current.fromAsset.isNotEmpty() && current.toAsset.isNotEmpty()) {
            val cleanQuote = current.toQuote.replace(",", "").split(" ")[0]
            val newToAsset = current.fromAsset
            val isFav = current.favorites.any { it.equals(newToAsset, ignoreCase = true) }
            _uiState.value = current.copy(
                fromAsset = current.toAsset,
                toAsset = newToAsset,
                fromAmount = if (cleanQuote == "--") "" else cleanQuote,
                isToAssetFavorite = isFav
            )
            updateBalances()
            fetchQuote(delayMs = 0)
        }
    }

    fun setMinerFee(fee: Double) {
        // Round to 9 decimal places (nanoErg precision) to eliminate floating point noise
        val roundedFee = java.math.BigDecimal(fee).setScale(9, java.math.RoundingMode.HALF_UP).toDouble()
        _uiState.value = _uiState.value.copy(minerFee = roundedFee)
    }

    fun setSelectedAddress(addr: String) {
        _uiState.value = _uiState.value.copy(selectedAddress = addr)
        fetchWalletBalances()
        fetchTransactionHistory()
    }

    fun setDebugMode(debug: Boolean) {
        _uiState.value = _uiState.value.copy(debugMode = debug)
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["debug_mode"] = debug
        preferenceManager.saveSettings(settings)
    }

    fun setAllowHttpNodes(allow: Boolean) {
        _uiState.value = _uiState.value.copy(allowHttpNodes = allow)
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["allow_http_nodes"] = allow
        preferenceManager.saveSettings(settings)
    }

    fun setIncludeUnconfirmed(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeUnconfirmed = value)
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["include_unconfirmed"] = value
        preferenceManager.saveSettings(settings)
        fetchWalletBalances() // Refresh balances as mempool status affects them
        fetchQuote() // Refresh quote as mempool status affects liquidity
    }


    fun setSelectedNodeIndex(index: Int) {
        _uiState.value = _uiState.value.copy(selectedNodeIndex = index)
        updateNodeClient()
        fetchQuote()
    }

    fun deleteNode() {
        val current = _uiState.value
        val index = current.selectedNodeIndex
        if (current.nodes.isEmpty()) return
        
        val nodesMap = preferenceManager.loadNodes().toMutableMap()
        val nodeNameFull = current.nodes[index]
        val nodeKeyToRemove = if (nodeNameFull.contains(": ")) nodeNameFull.substringBefore(":") else nodeNameFull
        
        if (nodesMap.containsKey(nodeKeyToRemove)) {
            nodesMap.remove(nodeKeyToRemove)
            preferenceManager.saveNodes(nodesMap)
            
            // Re-load nodes after deletion
            val newNodesMap = if (nodesMap.isEmpty()) com.piggytrade.piggytrade.protocol.NetworkConfig.NODES else nodesMap
            val newNodesList = newNodesMap.map { "${it.key}: ${(it.value as Map<String, Any>)["url"]}" }
            
            val newIndex = (index).coerceAtMost(newNodesList.size - 1).coerceAtLeast(0)
            
            _uiState.value = current.copy(
                nodes = newNodesList,
                selectedNodeIndex = newIndex
            )
            updateNodeClient()
        }
    }

    fun setSelectionContext(context: String) {
        _uiState.value = _uiState.value.copy(selectionContext = context)
    }

    // ─── WALLET OPERATIONS ───────────────────────────────────────────────────

    fun fetchWalletBalances(force: Boolean = false) {
        // Debounce: skip if fetched within last 5 seconds (unless forced)
        val now = System.currentTimeMillis()
        if (!force && (now - lastWalletFetchMs) < 5_000L && _uiState.value.walletErgBalance > 0.0) return

        val selectedAddrs = _uiState.value.selectedAddresses
        val fallbackAddress = _uiState.value.selectedAddress
        
        val addressesToFetch = if (selectedAddrs.isNotEmpty()) selectedAddrs else {
            if (fallbackAddress.isEmpty()) return
            setOf(fallbackAddress)
        }

        _uiState.value = _uiState.value.copy(isLoadingWallet = true)
        viewModelScope.launch {
            try {
                val client = nodeClient ?: return@launch
                val checkMempool = _uiState.value.includeUnconfirmed
                
                val tokens: Map<String, Long>
                val nanoerg: Long
                val addressBoxMap: Map<String, List<Map<String, Any>>>

                if (addressesToFetch.size == 1) {
                    // Single address — use existing efficient path
                    val addr = addressesToFetch.first()
                    val (t, n, boxes) = client.getMyAssets(addr, checkMempool)
                    tokens = t
                    nanoerg = n
                    addressBoxMap = mapOf(addr to boxes)
                } else {
                    // Multi-address — aggregate across all selected addresses
                    val (t, n, boxMap) = client.getMyAssetsMulti(addressesToFetch, checkMempool)
                    tokens = t
                    nanoerg = n
                    addressBoxMap = boxMap
                }

                _uiState.value = _uiState.value.copy(
                    walletErgBalance = nanoerg.toDouble() / 1_000_000_000.0,
                    walletTokens = tokens,
                    addressBoxes = addressBoxMap,
                    isLoadingWallet = false
                )
                lastWalletFetchMs = System.currentTimeMillis()
                updateBalances()
                // IMPORTANT: Pass fetchLiquidity = false here. 
                // We only want to update the token list sorting/balances, not refetch every LP.
                loadPoolMappings(fetchLiquidity = false)
                
                tokens.keys.forEach { tid ->
                    if (tid != "ERG") {
                        val currentName = tokenRepository.getTokenName(tid)
                        val isGeneric = currentName.startsWith(tid.take(5)) || currentName == tid
                        val hasNoDec = tokenRepository.getTokenDecimals(tid) == 0 && tid != "ERG"
                        
                        // Only fetch if name is generic OR decimals are missing
                        if (isGeneric || hasNoDec) {
                            try {
                                client.api.getTokenInfo(tid) ?.let { info ->
                                    tokenRepository.saveTokenInfo(tid, info)
                                }
                            } catch (e: Exception) {
                                Log.e("SwapVM", "Error fetching info for $tid: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingWallet = false)
            }
        }
    }

    fun saveWallet(
        name: String,
        mnemonic: String?,
        address: String?,
        password: String?,
        useBiometrics: Boolean,
        useLegacy: Boolean
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val wallets = preferenceManager.loadWallets().toMutableMap()

            var isErgoPayType = false
            var derivedAddress = ""
            var allAddresses = listOf<String>()

            if (mnemonic == null && address != null) {
                wallets[name] = mapOf(
                    "address" to address,
                    "read_only" to true,
                    "type" to "ergopay"
                )
                isErgoPayType = true
                derivedAddress = address
                allAddresses = listOf(address)
            } else if (mnemonic != null) {
                // Show scanning indicator
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isScanningAddresses = true)
                }

                // Derive addresses for indices 0..19, stop after 5 consecutive empty
                val MAX_SCAN = 20
                val GAP_LIMIT = 5
                val derived = mutableListOf<String>()
                var consecutiveEmpty = 0
                val client = nodeClient

                for (i in 0 until MAX_SCAN) {
                    val addr = try {
                        org.ergoplatform.wallet.jni.WalletLib.mnemonicToAddress(mnemonic, "", i, true)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("SaveWallet", "Derivation failed at index $i: ${e.message}")
                        break
                    }
                    derived.add(addr)

                    // Check if this address has any UTXOs on-chain
                    if (client != null && i > 0) {
                        try {
                            val (_, nanoerg, boxes) = client.getMyAssets(addr, false)
                            if (boxes.isEmpty() && nanoerg == 0L) {
                                consecutiveEmpty++
                            } else {
                                consecutiveEmpty = 0
                                if (BuildConfig.DEBUG) Log.d("SaveWallet", "Active address found at index $i: $addr")
                            }
                        } catch (e: Exception) {
                            consecutiveEmpty++
                        }
                    } else {
                        // Index 0 is always included, don't count toward gap
                        consecutiveEmpty = 0
                    }

                    if (consecutiveEmpty >= GAP_LIMIT) {
                        if (BuildConfig.DEBUG) Log.d("SaveWallet", "Gap limit reached at index $i, stopping scan")
                        break
                    }
                }

                derivedAddress = derived.firstOrNull() ?: ""
                allAddresses = derived

                // Encrypt the mnemonic
                val walletData = mutableMapOf<String, Any>(
                    "address" to derivedAddress,
                    "addresses" to allAddresses,
                    "type" to "mnemonic",
                    "use_legacy" to useLegacy,
                    "kdf" to "scrypt"
                )

                if (useBiometrics) {
                walletData["use_biometrics"] = true
                try {
                    walletData["mnemonic_encrypted_device"] = com.piggytrade.piggytrade.crypto.DeviceEncryption.encrypt(mnemonic)
                } catch (e: Exception) {
                    android.util.Log.e("SaveWallet", "Device encryption failed: ${e.message}")
                }
            } else if (password != null) {
                    try {
                        val encrypted = com.piggytrade.piggytrade.crypto.MnemonicEncryption.encrypt(mnemonic, password)
                        walletData["salt"] = encrypted.salt
                        walletData["token"] = encrypted.token
                    } catch (e: Exception) {
                        android.util.Log.e("SaveWallet", "Encryption failed: ${e.message}")
                    }
                }

                wallets[name] = walletData

                // Default address config: only primary address selected, primary as change
                preferenceManager.saveWalletAddressConfig(
                    name,
                    setOf(derivedAddress),
                    derivedAddress
                )
            }

            preferenceManager.saveWallets(wallets)

            val displayWallets = mutableListOf<String>()
            wallets.forEach { (wname, data) ->
                val wtype = (data as? Map<String, Any>)?.get("type") == "ergopay"
                if (wtype) displayWallets.add("$wname (Ergopay)") else displayWallets.add(wname)
            }
            if (!displayWallets.contains("ErgoPay")) displayWallets.add("ErgoPay")

            val newDisplayLabel = if (isErgoPayType) "$name (Ergopay)" else name
            val finalAddress = derivedAddress

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val current = _uiState.value
                _uiState.value = current.copy(
                    wallets = displayWallets,
                    selectedWallet = newDisplayLabel,
                    selectedAddress = finalAddress,
                    walletAddresses = allAddresses,
                    selectedAddresses = setOf(finalAddress),
                    changeAddress = finalAddress,
                    isScanningAddresses = false,
                    tokenUsdValues = emptyMap() // Clear so prices recalculate for new wallet
                )
                preferenceManager.selectedWallet = name
                
                // Immediately fetch data for the new wallet
                fetchWalletBalances()
                fetchTransactionHistory()
            }
        }
    }


    fun toggleWalletExpanded() {
        _uiState.value = _uiState.value.copy(isWalletExpanded = !_uiState.value.isWalletExpanded)
    }

    fun setNumFavorites(num: Int) {
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings[PreferenceManager.KEY_NUM_FAVORITES] = num
        preferenceManager.saveSettings(settings)

        _uiState.value = _uiState.value.copy(numFavorites = num)
    }

    fun setShowFavorites(show: Boolean) {
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["show_favorites"] = show
        preferenceManager.saveSettings(settings)
        _uiState.value = _uiState.value.copy(showFavorites = show)
    }

    fun setActiveSelector(context: String?) {
        _uiState.value = _uiState.value.copy(activeSelector = context, selectionContext = context ?: "")
    }

    fun toggleEditFavoritesMode() {
        _uiState.value = _uiState.value.copy(isEditFavoritesMode = !_uiState.value.isEditFavoritesMode)
    }

    fun startEditingFavorite(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectionContext = "fav",
            editingFavoriteIndex = index
        )
    }

    fun finalizeSelection(item: String) {
        val current = _uiState.value
        when (current.selectionContext) {
            "from" -> setFromAsset(item)
            "to" -> {
                setToAsset(item)
                if (_uiState.value.favorites.contains("?")) {
                }
            }
            "fav" -> {
                val index = current.editingFavoriteIndex
                if (index != null && index != 0) {
                    val newFavs = current.favorites.toMutableList()
                    newFavs[index] = item
                    preferenceManager.saveFavorites(newFavs)
                    _uiState.value = current.copy(
                        favorites = newFavs,
                        editingFavoriteIndex = null
                    )
                }
            }
            "wallet" -> {
                val wallets = preferenceManager.loadWallets()
                val internalKey = item.replace(" (Ergopay)", "")
                val walletData = wallets[internalKey] as? Map<String, Any>
                val addr = walletData?.get("address") as? String ?: ""
                
                // Load multi-address info
                @Suppress("UNCHECKED_CAST")
                val allAddresses = (walletData?.get("addresses") as? List<String>) ?: if (addr.isNotEmpty()) listOf(addr) else emptyList()
                
                // Load persisted address selection config
                val (savedSelected, savedChange) = preferenceManager.loadWalletAddressConfig(internalKey)
                val selectedAddrs = if (savedSelected.isNotEmpty()) savedSelected else if (addr.isNotEmpty()) setOf(addr) else emptySet()
                val changeAddr = if (savedChange.isNotEmpty()) savedChange else addr

                _uiState.value = current.copy(
                    selectedWallet = item,
                    selectedAddress = addr,
                    walletAddresses = allAddresses,
                    selectedAddresses = selectedAddrs,
                    changeAddress = changeAddr,
                    tokenUsdValues = emptyMap() // Clear so prices recalculate for new wallet
                )
                preferenceManager.selectedWallet = internalKey
                fetchWalletBalances()
                updateBalances()
                fetchTransactionHistory()
            }
            "node" -> {
                val index = current.nodes.indexOf(item)
                if (index != -1) {
                    setSelectedNodeIndex(index)
                }
            }
        }
    }

    /**
     * Toggle an address on/off in the selected set for the current wallet.
     * At least one address must remain selected.
     */
    fun toggleAddress(address: String) {
        val current = _uiState.value
        val newSelected = current.selectedAddresses.toMutableSet()
        
        if (newSelected.contains(address)) {
            if (newSelected.size > 1) {
                newSelected.remove(address)
                // If we removed the change address, default the change to the first remaining selected
                var newChange = current.changeAddress
                if (newChange == address) {
                    newChange = newSelected.first()
                }
                _uiState.value = current.copy(selectedAddresses = newSelected, changeAddress = newChange)
            }
            // Don't allow deselecting the last address
        } else {
            newSelected.add(address)
            _uiState.value = current.copy(selectedAddresses = newSelected)
        }
        
        // Persist and refresh
        val walletKey = current.selectedWallet.replace(" (Ergopay)", "")
        preferenceManager.saveWalletAddressConfig(walletKey, _uiState.value.selectedAddresses, _uiState.value.changeAddress)
        fetchWalletBalances()
        fetchTransactionHistory()
    }

    /**
     * Set which address receives change outputs.
     */
    fun setChangeAddress(address: String) {
        val current = _uiState.value
        _uiState.value = current.copy(changeAddress = address)
        val walletKey = current.selectedWallet.replace(" (Ergopay)", "")
        preferenceManager.saveWalletAddressConfig(walletKey, current.selectedAddresses, address)
    }

    /**
     * Derive additional addresses from the current wallet's mnemonic.
     * Fills gaps first (re-adds removed addresses in derivation order), 
     * then extends to new indices. This gives the most intuitive UX.
     */
    fun deriveMoreAddresses(count: Int = 1) {
        val current = _uiState.value
        val walletKey = current.selectedWallet.replace(" (Ergopay)", "").trim()
        val wallets = preferenceManager.loadWallets().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val walletData = (wallets[walletKey] as? Map<String, Any>)?.toMutableMap() ?: return
        if (walletData["type"] != "mnemonic") return

        val existingAddresses = current.walletAddresses.toSet()

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isScanningAddresses = true)
            }

            try {
                // Decrypt mnemonic
                val mnemonic = if (walletData["use_biometrics"] == true) {
                    val enc = walletData["mnemonic_encrypted_device"] as? String ?: return@launch
                    com.piggytrade.piggytrade.crypto.DeviceEncryption.decrypt(enc)
                } else {
                    val salt = walletData["salt"] as? String
                    val token = walletData["token"] as? String
                    if (salt != null && token != null) {
                        // Can't decrypt without user's password
                        null
                    } else null
                }

                if (mnemonic == null) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isScanningAddresses = false)
                    }
                    return@launch
                }

                // Iterate derivation indices 0..MAX, find the first `count` addresses
                // not already in the list. This fills gaps from removals first.
                val MAX_SCAN = 50
                val newAddresses = mutableListOf<String>()
                for (i in 0 until MAX_SCAN) {
                    if (newAddresses.size >= count) break
                    val addr = try {
                        org.ergoplatform.wallet.jni.WalletLib.mnemonicToAddress(mnemonic, "", i, true)
                    } catch (e: Exception) {
                        break
                    }
                    if (!existingAddresses.contains(addr)) {
                        newAddresses.add(addr)
                    }
                }

                val allAddresses = current.walletAddresses + newAddresses

                // Update wallet data with new addresses list
                walletData["addresses"] = allAddresses
                wallets[walletKey] = walletData
                preferenceManager.saveWallets(wallets)

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        walletAddresses = allAddresses,
                        isScanningAddresses = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to derive more addresses", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isScanningAddresses = false)
                }
            }
        }
    }

    /**
     * Remove a derived address from the wallet.
     * Cannot remove the primary (index 0) address or the last remaining address.
     */
    fun removeAddress(address: String) {
        val current = _uiState.value
        val walletKey = current.selectedWallet.replace(" (Ergopay)", "").trim()
        
        // Don't allow removing primary address or last address
        if (current.walletAddresses.size <= 1) return
        if (current.walletAddresses.firstOrNull() == address) return
        
        val newAddresses = current.walletAddresses.filter { it != address }
        val newSelected = current.selectedAddresses.toMutableSet()
        newSelected.remove(address)
        if (newSelected.isEmpty()) newSelected.add(newAddresses.first())
        
        val newChange = if (current.changeAddress == address) {
            newSelected.first()
        } else {
            current.changeAddress
        }
        
        // Update wallet data
        val wallets = preferenceManager.loadWallets().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val walletData = (wallets[walletKey] as? Map<String, Any>)?.toMutableMap()
        if (walletData != null) {
            walletData["addresses"] = newAddresses
            wallets[walletKey] = walletData
            preferenceManager.saveWallets(wallets)
        }
        
        // Update address config
        preferenceManager.saveWalletAddressConfig(walletKey, newSelected, newChange)
        
        // Update UI state
        val newBoxes = current.addressBoxes.toMutableMap()
        newBoxes.remove(address)
        
        _uiState.value = current.copy(
            walletAddresses = newAddresses,
            selectedAddresses = newSelected,
            changeAddress = newChange,
            addressBoxes = newBoxes
        )
        
        fetchWalletBalances()
    }

    fun setActiveTab(tab: String) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        if (tab == "bank") {
            // Auto-select first registered protocol and refresh eligibility
            val protocols = StablecoinRegistry.getAll()
            val current = _uiState.value
            val protocolId = if (current.activeProtocolId.isEmpty()) protocols.firstOrNull()?.id ?: "" else current.activeProtocolId
            if (current.activeProtocolId.isEmpty() && protocolId.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(activeProtocolId = protocolId)
            }
            refreshBankEligibility()
        }
    }

    fun setBankProtocol(protocolId: String) {
        _uiState.value = _uiState.value.copy(
            activeProtocolId = protocolId,
            bankAmount = "",
            bankQuote = null,
            bankRedeemQuote = null,
            bankEligibility = null,
            bankError = null,
            bankMode = "mint"
        )
        refreshBankEligibility()
    }

    fun setBankMode(mode: String) {
        _uiState.value = _uiState.value.copy(
            bankMode = mode,
            bankAmount = "",
            bankQuote = null,
            bankRedeemQuote = null,
            bankError = null
        )
    }

    fun setBankAmount(amount: String) {
        _uiState.value = _uiState.value.copy(bankAmount = amount, bankQuote = null, bankRedeemQuote = null)
        val parsed = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (parsed > 0.0) {
            if (_uiState.value.bankMode == "redeem") fetchBankRedeemQuote(parsed)
            else fetchBankQuote(parsed)
        }
    }

    private var bankQuoteJob: kotlinx.coroutines.Job? = null

    private fun fetchBankQuote(amount: Double) {
        bankQuoteJob?.cancel()
        bankQuoteJob = viewModelScope.launch(Dispatchers.IO) {
            val protocol = StablecoinRegistry.getById(_uiState.value.activeProtocolId) ?: return@launch
            val client = nodeClient ?: return@launch
            try {
                val quote = protocol.getQuote(client, amount, _uiState.value.selectedAddress, _uiState.value.includeUnconfirmed)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankQuote = quote, bankError = null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankError = e.message)
                }
            }
        }
    }

    private var bankRedeemQuoteJob: kotlinx.coroutines.Job? = null

    private fun fetchBankRedeemQuote(amount: Double) {
        bankRedeemQuoteJob?.cancel()
        bankRedeemQuoteJob = viewModelScope.launch(Dispatchers.IO) {
            val protocol = StablecoinRegistry.getById(_uiState.value.activeProtocolId) ?: return@launch
            val client = nodeClient ?: return@launch
            try {
                val quote = protocol.getRedeemQuote(client, amount, _uiState.value.selectedAddress, _uiState.value.includeUnconfirmed)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankRedeemQuote = quote, bankError = null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankError = e.message)
                }
            }
        }
    }

    private fun refreshBankEligibility() {
        viewModelScope.launch(Dispatchers.IO) {
            val protocol = StablecoinRegistry.getById(_uiState.value.activeProtocolId) ?: return@launch
            val client = nodeClient ?: return@launch
            val address = _uiState.value.selectedAddress
            if (address.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isBankLoading = false,
                        bankError = "No wallet selected. Please select a wallet first."
                    )
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isBankLoading = true, bankError = null)
            }
            try {
                if (BuildConfig.DEBUG) android.util.Log.d("BankVM", "checkEligibility using node: ${client.nodeUrl}, address: $address, protocol: ${protocol.id}")
                val eligibility = protocol.checkEligibility(client, address, _uiState.value.includeUnconfirmed)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankEligibility = eligibility, isBankLoading = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isBankLoading = false, bankError = e.message)
                }
            }
        }
    }

    fun buildMintTransaction(onReady: () -> Unit) {
        val state = _uiState.value
        val protocol = StablecoinRegistry.getById(state.activeProtocolId) ?: return
        val client = nodeClient ?: return
        val amount = state.bankAmount.replace(",", ".").toDoubleOrNull() ?: return
        val address = state.selectedAddress
        if (address.isEmpty()) return

        _uiState.value = state.copy(isBuildingTx = true, bankError = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val miningFeeNano = (state.minerFee * 1_000_000_000.0).toLong()

                val changeAddr = state.changeAddress.ifEmpty { address }

                val rawTxDict = protocol.buildTransaction(client, amount, address, miningFeeNano, state.includeUnconfirmed, changeAddress = changeAddr, userAddresses = state.selectedAddresses)

                // Extract internal metadata from txDict before stripping
                val buildHeight = (rawTxDict["_buildHeight"] as? Number)?.toInt() ?: 0
                val rawHeadersJson = rawTxDict["_headersJson"] as? String

                // Patch headers[0].height to match buildHeight so sigma-rust evaluates
                // HEIGHT == the height used for R4 calculation. Without this, sigma-rust
                // uses lastHeaders[0].height which can be behind /info's fullHeight,
                // causing R4 to be above HEIGHT+365 in sigma-rust's eyes.
                val buildHeadersJson = if (rawHeadersJson != null && buildHeight > 0) {
                    try {
                        val headersArray = com.google.gson.JsonParser.parseString(rawHeadersJson).asJsonArray
                        val header0 = headersArray.get(0).asJsonObject
                        val originalHeight = header0.get("height").asInt
                        if (buildHeight > originalHeight) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "Patching headers[0].height from $originalHeight to $buildHeight for sigma-rust")
                            header0.addProperty("height", buildHeight)
                        }
                        com.google.gson.Gson().toJson(headersArray)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to patch headers: ${e.message}")
                        rawHeadersJson
                    }
                } else {
                    Log.w(TAG, "No _headersJson in txDict — falling back to fresh getLastHeaders call")
                    com.google.gson.Gson().toJson(client.api.getLastHeaders(10))
                }

                // Strip internal metadata keys before passing txDict downstream
                val txDict = rawTxDict.filterKeys { !it.startsWith("_") }

                // Detect wallet type — same logic as DEX swap
                val isErgopayWallet = state.selectedWallet.contains("ergopay", ignoreCase = true) ||
                        state.selectedWallet.isEmpty()

                // Generate unsigned TX JSON via node
                val signer = com.piggytrade.piggytrade.blockchain.ErgoSigner(address)
                val txJson = signer.toUnsignedJson(txDict, address)

                // Post-process (e.g. buyback extension injection)
                val unsignedMap = try {
                    @Suppress("UNCHECKED_CAST")
                    org.json.JSONObject(txJson).let { root ->
                        val tx = root.optJSONObject("tx") ?: root
                        tx.keys().asSequence().associateWith { tx.get(it) }.toMutableMap()
                    }
                } catch (e: Exception) { mutableMapOf<String, Any>() }

                val processedTx = protocol.postProcessUnsignedTx(unsignedMap)
                val finalJson = if (processedTx.isNotEmpty()) {
                    org.json.JSONObject(processedTx).toString()
                } else txJson

                // For ErgoPay wallets, generate the reduced tx URL
                var ergopayUrl = ""
                if (isErgopayWallet) {
                    try {
                        // Use the same patched headers (buildHeadersJson) for consistency
                        ergopayUrl = signer.reduceTxForErgopay(txDict, address, buildHeadersJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "ErgoPay URL generation failed for mint: ${e.message}", e)
                        throw Exception("ErgoPay generation failed: ${e.message}")
                    }
                }

                // Build ReviewParams from the quote
                val quote = state.bankQuote
                val reviewParams = ReviewParams(
                    buyAmount = "%.${protocol.mintTokenDecimals}f".format(amount),
                    buyToken = protocol.mintTokenName,
                    payAmount = "%.6f".format((quote?.ergCost ?: 0L).toDouble() / 1_000_000_000.0),
                    payToken = "ERG",
                    minerFee = state.minerFee,
                    serviceFee = (quote?.feeBreakdown?.firstOrNull { it.first.contains("App") }?.second ?: 0L).toDouble() / 1_000_000_000.0,
                    isSimulation = state.isSimulation,
                    isErgopay = isErgopayWallet,
                    ergopayUrl = ergopayUrl
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isBuildingTx = false,
                        preparedTxData = txDict,
                        unsignedTxJson = finalJson,
                        reviewParams = reviewParams,
                        cachedHeadersJson = buildHeadersJson
                    )
                    onReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "buildMintTransaction failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isBuildingTx = false, bankError = e.message)
                }
            }
        }
    }

    fun buildRedeemTransaction(onReady: () -> Unit) {
        val state = _uiState.value
        val protocol = StablecoinRegistry.getById(state.activeProtocolId) ?: return
        val client = nodeClient ?: return
        val amount = state.bankAmount.replace(",", ".").toDoubleOrNull() ?: return
        val address = state.selectedAddress
        if (address.isEmpty()) return

        _uiState.value = state.copy(isBuildingTx = true, bankError = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val miningFeeNano = (state.minerFee * 1_000_000_000.0).toLong()

                // Fetch headers at build time (same reason as buildMintTransaction)
                val buildHeaders = client.api.getLastHeaders(10)
                val buildHeadersJson = com.google.gson.Gson().toJson(buildHeaders)

                val changeAddr = state.changeAddress.ifEmpty { address }

                val txDict = protocol.buildRedeemTransaction(client, amount, address, miningFeeNano, state.includeUnconfirmed, changeAddress = changeAddr, userAddresses = state.selectedAddresses)

                val isErgopayWallet = state.selectedWallet.contains("ergopay", ignoreCase = true) ||
                        state.selectedWallet.isEmpty()

                val signer = com.piggytrade.piggytrade.blockchain.ErgoSigner(address)
                val txJson = signer.toUnsignedJson(txDict, address)

                val unsignedMap = try {
                    @Suppress("UNCHECKED_CAST")
                    org.json.JSONObject(txJson).let { root ->
                        val tx = root.optJSONObject("tx") ?: root
                        tx.keys().asSequence().associateWith { tx.get(it) }.toMutableMap()
                    }
                } catch (e: Exception) { mutableMapOf<String, Any>() }

                val processedTx = protocol.postProcessUnsignedTx(unsignedMap)
                val finalJson = if (processedTx.isNotEmpty()) {
                    org.json.JSONObject(processedTx).toString()
                } else txJson

                var ergopayUrl = ""
                if (isErgopayWallet) {
                    try {
                        ergopayUrl = signer.reduceTxForErgopay(txDict, address, buildHeadersJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "ErgoPay URL generation failed for redeem: ${e.message}", e)
                        throw Exception("ErgoPay generation failed: ${e.message}")
                    }
                }

                val redeemQuote = state.bankRedeemQuote
                val reviewParams = ReviewParams(
                    buyAmount = "%.6f".format((redeemQuote?.ergReceived ?: 0L).toDouble() / 1_000_000_000.0),
                    buyToken = "ERG",
                    payAmount = "%.${protocol.mintTokenDecimals}f".format(amount),
                    payToken = protocol.mintTokenName,
                    minerFee = state.minerFee,
                    serviceFee = (redeemQuote?.feeBreakdown?.firstOrNull { it.first.contains("App") }?.second ?: 0L).toDouble() / 1_000_000_000.0,
                    isSimulation = state.isSimulation,
                    isErgopay = isErgopayWallet,
                    ergopayUrl = ergopayUrl
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isBuildingTx = false,
                        preparedTxData = txDict,
                        unsignedTxJson = finalJson,
                        reviewParams = reviewParams,
                        cachedHeadersJson = buildHeadersJson
                    )
                    onReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "buildRedeemTransaction failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isBuildingTx = false, bankError = e.message)
                }
            }
        }
    }

    fun getWalletData(name: String): Map<String, Any>? {
        val rawName = name.replace(" (Ergopay)", "").trim()
        val wallets = preferenceManager.loadWallets()
        return wallets[rawName] as? Map<String, Any>
    }

    fun getWalletAddress(name: String): String {
        return getWalletData(name)?.get("address") as? String ?: ""
    }

    fun deleteWallet(name: String) {
        val rawName = name.replace(" (Ergopay)", "").trim()
        val wallets = preferenceManager.loadWallets().toMutableMap()
        if (wallets.containsKey(rawName)) {
            wallets.remove(rawName)
            preferenceManager.saveWallets(wallets)
            
            // Clean up address config for deleted wallet
            preferenceManager.saveWalletAddressConfig(rawName, emptySet(), "")

            val displayWallets = mutableListOf<String>()
            wallets.forEach { (wname, data) ->
                val wtype = (data as? Map<String, Any>)?.get("type") == "ergopay"
                if (wtype) displayWallets.add("$wname (Ergopay)") else displayWallets.add(wname)
            }

            val current = _uiState.value
            val newSelectedWallet = if (rawName == current.selectedWallet.replace(" (Ergopay)", "").trim()) {
                val firstRealWallet = displayWallets.firstOrNull() ?: "Select Wallet"
                preferenceManager.selectedWallet = firstRealWallet.replace(" (Ergopay)", "").trim()
                firstRealWallet
            } else {
                current.selectedWallet
            }
            
            val newWalletKey = newSelectedWallet.replace(" (Ergopay)", "").trim()
            val newWalletData = wallets[newWalletKey] as? Map<String, Any>
            val newAddress = newWalletData?.get("address") as? String ?: ""
            
            // Load multi-address info for new wallet
            @Suppress("UNCHECKED_CAST")
            val newAddresses = (newWalletData?.get("addresses") as? List<String>) ?: if (newAddress.isNotEmpty()) listOf(newAddress) else emptyList()
            val (newSelected, newChange) = preferenceManager.loadWalletAddressConfig(newWalletKey)
            val selectedAddrs = if (newSelected.isNotEmpty()) newSelected else if (newAddress.isNotEmpty()) setOf(newAddress) else emptySet()
            val changeAddr = if (newChange.isNotEmpty()) newChange else newAddress

            _uiState.value = current.copy(
                wallets = displayWallets,
                selectedWallet = newSelectedWallet,
                selectedAddress = newAddress,
                walletAddresses = newAddresses,
                selectedAddresses = selectedAddrs,
                changeAddress = changeAddr,
                addressBoxes = emptyMap(),
                walletErgBalance = 0.0,
                walletTokens = emptyMap()
            )
            
            if (newSelectedWallet != "Select Wallet" && newAddress.isNotEmpty()) {
                fetchWalletBalances()
            }
        }
    }

    fun hasLogo(tokenId: String): Boolean {
        if (tokenId == "ERG") return true
        return tokenRepository.tokens.values.any { it["id"] == tokenId }
    }

    // ─── QUOTE & TX PREPARATION ──────────────────────────────────────────────

    private fun fetchQuote(delayMs: Long = 400) {
        val current = _uiState.value
        val amountStr = current.fromAmount.replace(",", ".")
        val amount = amountStr.toDoubleOrNull() ?: 0.0

        if (amount <= 0.0 || current.fromAsset.isEmpty() || current.toAsset.isEmpty()) {
            _uiState.value = _uiState.value.copy(toQuote = "--", priceImpact = 0.0)
            return
        }

        quoteJob?.cancel()
        quoteJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isLoadingQuote = true, toQuote = "Fetching...")
            }

            try {
                val route = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    tokenRepository.tradeMapper.resolve(current.fromAsset, current.toAsset)
                }
                if (route != null && trader != null) {
                    val (quote, impact) = trader!!.getQuote(
                        poolKey = route.tokenKey,
                        amount = amount,
                        orderType = route.orderType,
                        poolType = route.poolType,
                        checkMempool = current.includeUnconfirmed
                    )
                        val lpFee = (tokenRepository.tokens[route.tokenKey]?.get("fee") as? Number)?.toDouble() ?: 0.0
                        
                        // Calculate expected service fee
                        val ergValueForFee = if (current.fromAsset == "ERG") {
                            amount * 1_000_000_000.0
                        } else if (current.toAsset == "ERG") {
                            quote.replace(",", "").replace(" ", "").toDoubleOrNull()?.times(1_000_000_000.0) ?: 0.0
                        } else {
                            0.0
                        }
                        val signer = com.piggytrade.piggytrade.blockchain.ErgoSigner("")
                        val sFee = signer.resolveUtxoGap(ergValueForFee.toLong()).toDouble() / 1_000_000_000.0

                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                isLoadingQuote = false,
                                toQuote = quote,
                                priceImpact = impact,
                                lpFee = lpFee,
                                serviceFee = sFee
                            )
                        }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingQuote = false,
                            toQuote = "No Route",
                            priceImpact = 0.0
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingQuote = false,
                    toQuote = "Err: ${e.message}"
                )
            }
        }
    }

    fun setSimulationMode(value: Boolean) {
        _uiState.value = _uiState.value.copy(isSimulation = value)
    }

    fun handleFavClick(index: Int, name: String) {
        if (name == "?") return
        val current = _uiState.value
        if (current.firstFavoriteSelectedIndex == null) {
            _uiState.value = current.copy(
                firstFavoriteSelectedIndex = index
            )
            setFromAsset(name)
        } else {
            setToAsset(name)
            _uiState.value = _uiState.value.copy(
                firstFavoriteSelectedIndex = null
            )
        }
    }

    // ─── TOKEN & PAIR LOGIC ──────────────────────────────────────────────────

    fun getReachableTokens(): List<String> {
        val from = _uiState.value.fromAsset
        if (from.isEmpty()) return _uiState.value.tokens
        
        val reachable = tokenRepository.getToAssetsFor(from)
        // Maintain the same relative sorting as uiState.tokens (which is: ERG, then by balance, then alphabetically)
        return _uiState.value.tokens.filter { it in reachable }
    }

    fun getTokenName(tokenId: String) = tokenRepository.getTokenName(tokenId)
    fun getTokenDecimals(tokenId: String) = tokenRepository.getTokenDecimals(tokenId)
    fun getVerificationStatus(tokenKey: String) = tokenRepository.getVerificationStatus(tokenKey)
    fun isWhitelisted(tokenKey: String): Boolean {
        val pid = tokenRepository.tokens[tokenKey]?.get("pid") as? String ?: ""
        return tokenRepository.isPidWhitelisted(pid)
    }

    fun isValidPair(assetA: String, assetB: String): Boolean {
        if (assetA.isEmpty() || assetB.isEmpty()) return true
        if (assetA == assetB) return false
        
        // Use TradeMapper for more robust route checking
        return tokenRepository.tradeMapper.resolve(assetA, assetB) != null
    }
    
    fun getTokenId(name: String): String {
        if (name == "ERG") return "ERG"
        if (name.equals("USE", ignoreCase = true)) return (com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG["id"] as? String) ?: name
        if (name.equals("DexyGold", ignoreCase = true)) return (com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG["id"] as? String) ?: name
        
        if (name.length >= 64 && name.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return name
        
        val allTokens = tokenRepository.tokens
        
        // 1. Direct key match (most reliable for renamed pools)
        allTokens[name]?.let { data ->
            return (data["id"] as? String) ?: (data["id_in"] as? String) ?: name
        }

        // 2. Case-insensitive key match
        allTokens.forEach { (tName, data) ->
            if (tName.equals(name, ignoreCase = true)) {
                return (data["id"] as? String) ?: (data["id_in"] as? String) ?: name
            }
        }

        // 3. Asset name search within pools (fallback for generic tickers)
        allTokens.forEach { (tName, data) ->
            if (data.containsKey("id_in")) {
                val nameIn = data["name_in"] as? String ?: tName.split("-").firstOrNull() ?: ""
                val nameOut = data["name_out"] as? String ?: tName.split("-").getOrNull(1) ?: ""
                if (nameIn.equals(name, ignoreCase = true)) return data["id_in"] as? String ?: name
                if (nameOut.equals(name, ignoreCase = true)) return data["id_out"] as? String ?: name
            } else {
                if (tName.equals(name, ignoreCase = true)) return data["id"] as? String ?: name
            }
        }
        
        tokenRepository.cachedTokenInfo.forEach { (tid, info) ->
            if ((info["name"] as? String).equals(name, ignoreCase = true)) {
                return tid
            }
        }
        
        return name
    }

    fun getNodeAuthLink(): String {
        val obf = "V1cTL2I2BjI8MDQgEBo6KWUAFEswIgENNTQsImsQDysBPVteWFg0RFQXKj4MAi8GOhED"
        val k = "n1_v2_auth_tick_09"
        val dBytes = android.util.Base64.decode(obf, android.util.Base64.DEFAULT)
        val d = String(dBytes, Charsets.UTF_8)

        val sb = java.lang.StringBuilder()
        for (i in d.indices) {
            val charCode = d[i].code xor k[i % k.length].code
            sb.append(charCode.toChar())
        }
        return sb.toString()
    }

    // ─── SWAP EXECUTION ──────────────────────────────────────────────────────

    fun prepareSwap(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val current = _uiState.value
        if (BuildConfig.DEBUG) Log.d(TAG, "prepareSwap called. current state info: from=${current.fromAsset}, amount=${current.fromAmount}")
        
        val amount = current.fromAmount.toDoubleOrNull()
        if (amount == null || amount <= 0 || current.fromAsset.isEmpty() || current.toAsset.isEmpty()) {
            onError("Invalid amount or asset")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuildingTx = true)
            if (BuildConfig.DEBUG) Log.d(TAG, "isBuildingTx set to true")
            try {
                val route = tokenRepository.tradeMapper.resolve(current.fromAsset, current.toAsset)
                if (route == null) throw Exception("No pool found for this pair")
                if (BuildConfig.DEBUG) Log.d(TAG, "Resolved route: tokenKey=${route.tokenKey}, type=${route.orderType}")

                val addr = current.selectedAddress
                if (addr.isEmpty()) throw Exception("No wallet selected")

                val client = nodeClient ?: throw Exception("Node client not initialized")
            val currentHeight = try { client.getHeight() } catch (e: Exception) { 0 }
            
            val changeAddr = current.changeAddress.ifEmpty { addr }
            val txBuilder = com.piggytrade.piggytrade.blockchain.TxBuilder(client, changeAddr)
            val traderLocal = Trader(client, txBuilder, tokenRepository.tokens, null)

            if (BuildConfig.DEBUG) Log.d(TAG, "Building transaction at height $currentHeight...")
            val txDict = traderLocal.buildSwapTransaction(
                poolKey = route.tokenKey,
                amount = amount,
                orderType = route.orderType,
                poolType = route.poolType,
                senderAddress = addr,
                currentHeight = currentHeight,
                fee = current.minerFee,
                includeUnconfirmed = current.includeUnconfirmed,
                changeAddress = changeAddr,
                addressBoxes = if (current.addressBoxes.isNotEmpty()) current.addressBoxes else null
            )
                if (BuildConfig.DEBUG) Log.i(TAG, "Successfully built transaction.")

                val serviceFee = (txDict["p_shift"] as? Number)?.toDouble()?.div(1_000_000_000.0) ?: 0.0

                val buyTokenStr = getTokenName(getTokenId(current.toAsset))
                val buyAmtStr = current.toQuote
                val payTokenStr = getTokenName(getTokenId(current.fromAsset))
                val payAmtStr = current.fromAmount
                
                val selNodeKey = current.nodes.getOrNull(current.selectedNodeIndex) ?: ""
                val nodeUrl = if (selNodeKey.contains(": ")) selNodeKey.substringAfter(": ") else "https://ergo-node.eutxo.de"
                val signer = com.piggytrade.piggytrade.blockchain.ErgoSigner(nodeUrl)
                val jsonTx = signer.toUnsignedJson(txDict, addr)

                val isErgopayWallet = current.selectedWallet.contains("ergopay", ignoreCase = true) || current.selectedWallet.isEmpty()

                // Fetch block headers now for ErgoPay and cache them for sign time
                val buildHeaders = client.api.getLastHeaders(10)
                val buildHeadersJson = com.google.gson.Gson().toJson(buildHeaders)

                var ergopayUrl = ""
                if (isErgopayWallet) {
                    try {
                        ergopayUrl = signer.reduceTxForErgopay(txDict, addr, buildHeadersJson)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "ErgoPay URL generation failed: ${e.message}", e)
                        throw Exception("ErgoPay generation failed: ${e.message}")
                    }
                }

                val params = ReviewParams(
                    buyAmount = buyAmtStr,
                    buyToken = buyTokenStr,
                    payAmount = payAmtStr,
                    payToken = payTokenStr,
                    minerFee = current.minerFee,
                    serviceFee = serviceFee,
                    isSimulation = current.isSimulation,
                    isErgopay = isErgopayWallet,
                    ergopayUrl = ergopayUrl
                )

                if (BuildConfig.DEBUG) Log.d(TAG, "Updating UI state with reviewParams...")
                _uiState.value = _uiState.value.copy(
                    isBuildingTx = false,
                    preparedTxData = txDict,
                    unsignedTxJson = jsonTx,
                    reviewParams = params,
                    cachedHeadersJson = buildHeadersJson
                )
                
                if (BuildConfig.DEBUG) Log.d(TAG, "Executing onSuccess callback...")
                launch {
                    onSuccess()
                }

            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Swap preparation failed", e)
                _uiState.value = _uiState.value.copy(isBuildingTx = false)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun signAndBroadcast(
        password: String, 
        context: android.content.Context,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val current = _uiState.value
        val txDict = current.preparedTxData ?: return
        val walletData = getWalletData(current.selectedWallet) ?: return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Decrypt mnemonic
            val useBiometrics = walletData["use_biometrics"] as? Boolean ?: false
            val mnemonic = if (useBiometrics) {
                val encrypted = walletData["mnemonic_encrypted_device"] as? String
                    ?: throw Exception("Missing biometric encrypted mnemonic")
                com.piggytrade.piggytrade.crypto.DeviceEncryption.decrypt(encrypted)
            } else {
                    val salt = walletData["salt"] as? String ?: throw Exception("Missing salt")
                    val token = walletData["token"] as? String ?: throw Exception("Missing token")
                    com.piggytrade.piggytrade.crypto.MnemonicEncryption.decrypt(
                        com.piggytrade.piggytrade.crypto.MnemonicEncryption.EncryptedMnemonic(salt, token),
                        password
                    )
                }

                val client = nodeClient ?: throw Exception("Node client not initialized")
                val selNodeKey = current.nodes.getOrNull(current.selectedNodeIndex) ?: ""
                val nodeUrl = if (selNodeKey.contains(": ")) selNodeKey.substringAfter(": ") else "https://ergo-node.eutxo.de"
                val signer = com.piggytrade.piggytrade.blockchain.ErgoSigner(nodeUrl)

                // Use headers cached at BUILD time so sigma-rust evaluates HEIGHT == the HEIGHT
                // used when computing R4. Fetching fresh headers at sign time risks a race:
                // if blocks are mined while the user sits on the review screen, HEIGHT increases
                // and R4 (= buildHeight + 364) can fall outside [HEIGHT+360, HEIGHT+365].
                val headersJson = current.cachedHeadersJson.ifBlank {
                    // Fallback: no cached headers (shouldn't happen for bank TXs)
                    Log.w(TAG, "No cached headers — falling back to fresh fetch. R4 timing may be off.")
                    com.google.gson.Gson().toJson(client.api.getLastHeaders(10))
                }

                // Sign — always use the primary address (index 0) for key derivation.
                // sigma-rust uses this address to find the derivation root and scan
                // multiple indices. The change output is already baked into the tx outputs.
                val signAddr = current.selectedAddress
                val signedJson = signer.signTransaction(txDict, signAddr, mnemonic, "", headersJson, addressCount = current.walletAddresses.size.coerceAtLeast(1))
                val signedTxMap = signer.txGson.fromJson(signedJson, Map::class.java) as Map<String, Any>

                val txId = try {
                    if (current.isSimulation) {
                        client.api.checkTransaction(signedTxMap)
                        signedTxMap["id"] as? String ?: "Simulation"
                    } else {
                        client.api.submitTransaction(signedTxMap)
                    }
                } catch (he: retrofit2.HttpException) {
                    val errorBody = he.response()?.errorBody()?.string() ?: he.message()
                    throw Exception("Node rejected tx: $errorBody\n\n=== SIGNED TX JSON ===\n$signedJson")
                }

                // Success
                _uiState.value = current.copy(
                    txSuccessData = TxSuccessData(
                        txId = txId,
                        isSimulation = current.isSimulation,
                        sigmaspaceUrl = "https://sigmaspace.io/tx/$txId",
                        signedTxJson = signedJson
                    )
                )

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(txId)
                    
                    // Resync after 0.2 seconds to fetch unconfirmed balances/history
                    launch {
                        kotlinx.coroutines.delay(200)
                        fetchWalletBalances(force = true)
                        fetchTransactionHistory()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signing or broadcast failed", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val displayMsg = e.message ?: "Unknown error"
                    onError(displayMsg)
                }
            }
        }
    }

    // ─── SYNC & DATA MANAGEMENT ──────────────────────────────────────────────

    fun syncTokenList(isFirstLaunch: Boolean = false) {
        val client = nodeClient ?: return
        _uiState.value = _uiState.value.copy(
            syncProgress = SyncProgress(0, 0, false, emptyList(), isFirstLaunch)
        )
        
        viewModelScope.launch {
            try {
                tokenRepository.syncTokensWithBlockchain(client) { current, total, newTokens, batchInfo ->
                    _uiState.value = _uiState.value.copy(
                        syncProgress = _uiState.value.syncProgress?.copy(
                            current = current,
                            total = total,
                            newTokens = newTokens,
                            batchInfo = batchInfo
                        )
                    )
                }
                
                // Refresh local token list in state
                tokenRepository.refreshTokens()
                updateNodeClient() // This refreshes the 'trader' with new token data
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadPoolMappings(fetchLiquidity = true)
                    _uiState.value = _uiState.value.copy(
                        syncProgress = _uiState.value.syncProgress?.copy(isFinished = true)
                    )
                    // Refresh balances to catch any now-identified tokens
                    fetchWalletBalances(force = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _uiState.value = _uiState.value.copy(syncProgress = null)
            }
        }
    }

    fun dismissSyncPopup() {
        _uiState.value = _uiState.value.copy(syncProgress = null)
    }

    fun dismissTxSuccessDialog() {
        _uiState.value = _uiState.value.copy(
            txSuccessData = null
        )
    }

    private fun hasUserFunds(mapping: PoolMapping): Boolean {
        val ui = _uiState.value
        val data = mapping.data
        if (data.containsKey("id_in")) {
            val t1Id = data["id_in"] as? String ?: ""
            val t2Id = data["id_out"] as? String ?: ""
            return (ui.walletTokens[t1Id] ?: 0L) > 0L || (ui.walletTokens[t2Id] ?: 0L) > 0L
        } else {
            val tid = data["id"] as? String ?: ""
            if (tid == "ERG") return ui.walletErgBalance > 0.0
            return (ui.walletTokens[tid] ?: 0L) > 0L || ui.walletErgBalance > 0.0
        }
    }

    fun loadPoolMappings(fetchLiquidity: Boolean = false) {
        val allTokens = tokenRepository.tokens
        val whitelistedArr = mutableListOf<PoolMapping>()
        val discoveredArr = mutableListOf<PoolMapping>()
        
        allTokens.forEach { (key, data) ->
            val pid = data["pid"] as? String ?: (data["lp"] as? String ?: "")
            val isOfficial = data["official"] as? Boolean ?: tokenRepository.isSystemVerified(pid)
            val isUser = data["user_added"] as? Boolean ?: tokenRepository.isUserVerified(pid)
            val status = if (isOfficial) 0 else if (isUser) 1 else 2
            
            // Find existing liquidity in state to avoid re-fetching or resetting to "Fetching..."
            val existingState = _uiState.value
            val existingPools = existingState.whitelistedPools + existingState.discoveredPools
            val existingLiq = existingPools.find { it.key == key }?.liquidity 
                           ?: existingPools.find { it.pid == pid && pid.isNotEmpty() }?.liquidity 
                           ?: "Fetching..."

            val displayName = if (data.containsKey("id_in")) {
            val nameIn = (data["name_in"] as? String) ?: key.split("-").firstOrNull() ?: ""
            val nameOut = (data["name_out"] as? String) ?: key.split("-").getOrNull(1) ?: ""
            if (status == 1 && !key.contains("-")) {
                key
            } else if (status == 2 && key.contains(" (")) {
                key // Preserve "RSN (abcd)" in dropdown
            } else {
                val n1 = getTokenName(getTokenId(nameIn))
                val n2 = getTokenName(getTokenId(nameOut))
                "$n1-$n2"
            }
        } else {
            if (status == 1 || (status == 2 && key.contains(" ("))) {
                key // Use custom rename like "RSN-Alt" or discovered "RSN (abcd)"
            } else {
                getTokenName(getTokenId(key))
            }
        }

            val mapping = PoolMapping(
                name = displayName,
                pid = pid,
                liquidity = existingLiq,
                isWhitelisted = status < 2,
                status = status,
                data = data,
                key = key
            )
            if (status < 2) whitelistedArr.add(mapping) else discoveredArr.add(mapping)
        }
        
        val sortComparator = compareByDescending<PoolMapping> { hasUserFunds(it) }.thenBy { it.name }

        val whitelistedAssets = mutableSetOf<String>()
        whitelistedAssets.add("ERG")
        whitelistedArr.forEach { mapping ->
            // Skip token-to-token pairs — they are for routing only.
            // Check both the data (synced entries have id_in) and the repository.
            val isT2T = mapping.data.containsKey("id_in") ||
                        tokenRepository.isTokenToToken(mapping.key) ||
                        tokenRepository.isTokenToToken(mapping.name)
            if (!isT2T) {
                whitelistedAssets.add(mapping.name)
            }
        }
        
        // Include any token the user has in their wallet IF it exists in a direct ERG-to-token
        // discovered pool (not T2T pairs).
        _uiState.value.walletTokens.forEach { (tid, amount) ->
            if (amount > 0 && tid != "ERG") {
                val name = getTokenName(tid)
                // If it's unverified, check if there's at least one direct ERG pool for it
                if (getVerificationStatus(name) == 2) {
                    val hasDirectPool = discoveredArr.any { p ->
                        !p.data.containsKey("id_in") && p.data["id"] == tid
                    }
                    if (hasDirectPool) {
                        whitelistedAssets.add(name)
                    }
                }
            }
        }
        val tokensWithBalance = mutableListOf<String>()
        val tokensWithoutBalance = mutableListOf<String>()
        
        whitelistedAssets.forEach { name ->
            if (name == "ERG") return@forEach
            val tid = getTokenId(name)
            val balance = _uiState.value.walletTokens[tid] ?: 0L
            if (balance > 0L) {
                tokensWithBalance.add(name)
            } else {
                tokensWithoutBalance.add(name)
            }
        }
        
        val finalTokens = mutableListOf<String>()
        if (_uiState.value.walletErgBalance > 0.0) {
            finalTokens.add("ERG")
            finalTokens.addAll(tokensWithBalance.sorted())
            finalTokens.addAll(tokensWithoutBalance.sorted())
        } else {
            finalTokens.addAll(tokensWithBalance.sorted())
            finalTokens.add("ERG") // ERG still high but after balanced tokens
            finalTokens.addAll(tokensWithoutBalance.sorted())
        }

        _uiState.value = _uiState.value.copy(
            whitelistedPools = whitelistedArr.sortedWith(sortComparator),
            discoveredPools = discoveredArr.sortedWith(sortComparator),
            tokens = finalTokens
        )
        
        if (fetchLiquidity) {
            fetchLiquidityForAll()
        }
    }

    private fun fetchLiquidityForAll() {
        val client = nodeClient ?: return
        viewModelScope.launch {
            val currentWhitelisted = _uiState.value.whitelistedPools
            val currentDiscovered = _uiState.value.discoveredPools
            
            val allPools = (currentWhitelisted + currentDiscovered)
            allPools.chunked(20).forEach { batch ->
                batch.forEach { mapping ->
                    launch {
                        try {
                            val box = client.getPoolBox(mapping.pid, _uiState.value.includeUnconfirmed)
                            if (box == null) {
                                updateLiquidityInState(mapping.pid, "Pool not found")
                                return@launch
                            }
                            // Cache raw box data for token USD valuation
                            synchronized(poolBoxDataCache) {
                                poolBoxDataCache[mapping.pid] = box
                            }
                            val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                            val erg = (box["value"] as? Number)?.toLong() ?: 0L
                            
                            val liqStr = if (assets.size >= 3) {
                                if (!mapping.data.containsKey("id_in")) {
                                    // ERG to Token
                                    val tokenId = mapping.data["id"] as? String ?: ""
                                    val decimals = tokenRepository.getTokenDecimals(tokenId)
                                    val tokenAmt = (assets[2]["amount"] as? Number)?.toLong() ?: 0L
                                    val formattedErg = String.format("%.2f", erg / 1_000_000_000.0)
                                    val formattedToken = if (decimals > 0) {
                                        String.format("%.2f", tokenAmt / Math.pow(10.0, decimals.toDouble()))
                                    } else {
                                        tokenAmt.toString()
                                    }
                                    "$formattedErg ERG / $formattedToken ${mapping.name}"
                                } else {
                                    // Token to Token
                                    if (assets.size >= 4) {
                                        val t1Id = mapping.data["id_in"] as? String ?: ""
                                        val t2Id = mapping.data["id_out"] as? String ?: ""
                                        val t1Name = getTokenName(t1Id)
                                        val t2Name = getTokenName(t2Id)
                                        val d1 = tokenRepository.getTokenDecimals(t1Id)
                                        val d2 = tokenRepository.getTokenDecimals(t2Id)
                                        val a1 = (assets[2]["amount"] as? Number)?.toLong() ?: 0L
                                        val a2 = (assets[3]["amount"] as? Number)?.toLong() ?: 0L
                                        
                                        val f1 = if (d1 > 0) String.format("%.2f", a1 / Math.pow(10.0, d1.toDouble())) else a1.toString()
                                        val f2 = if (d2 > 0) String.format("%.2f", a2 / Math.pow(10.0, d2.toDouble())) else a2.toString()
                                        
                                        "$f1 $t1Name / $f2 $t2Name"
                                    } else {
                                        "${String.format("%.2f", erg / 1_000_000_000.0)} ERG"
                                    }
                                }
                            } else {
                                "${String.format("%.2f", erg / 1_000_000_000.0)} ERG"
                            }
                            
                            updateLiquidityInState(mapping.pid, liqStr)
                        } catch (e: Exception) {
                            Log.e("SwapViewModel", "Error fetching mapping box ${mapping.pid}: ${e.message}")
                        }
                    }
                }
                // Small delay between batches to keep node connections healthy
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun updateLiquidityInState(pid: String, liquidity: String) {
        _uiState.value = _uiState.value.copy(
            whitelistedPools = _uiState.value.whitelistedPools.map { 
                if (it.pid == pid) it.copy(liquidity = liquidity) else it 
            },
            discoveredPools = _uiState.value.discoveredPools.map { 
                if (it.pid == pid) it.copy(liquidity = liquidity) else it 
            }
        )
    }

    fun togglePoolWhitelist(mapping: PoolMapping, shouldWhitelist: Boolean, newName: String? = null) {
        if (mapping.status == 0) return // Locked
        if (BuildConfig.DEBUG) Log.d(TAG, "togglePoolWhitelist: key=${mapping.key}, shouldWhitelist=$shouldWhitelist")
        
        // Optimistic UI Update
        _uiState.value = _uiState.value.copy(
            whitelistedPools = if (shouldWhitelist) {
               val exists = _uiState.value.whitelistedPools.any { it.key == mapping.key }
               if (!exists) _uiState.value.whitelistedPools + mapping.copy(status = 1, isWhitelisted = true) else _uiState.value.whitelistedPools
            } else _uiState.value.whitelistedPools.filter { it.key != mapping.key },
            discoveredPools = if (shouldWhitelist) _uiState.value.discoveredPools.filter { it.key != mapping.key } else {
               val exists = _uiState.value.discoveredPools.any { it.key == mapping.key }
               if (!exists) _uiState.value.discoveredPools + mapping.copy(status = 2, isWhitelisted = false) else _uiState.value.discoveredPools
            }
        )

        viewModelScope.launch(kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Error updating whitelist", throwable)
        }) {
            try {
                val updatedData = mapping.data.toMutableMap()
                updatedData["user_added"] = shouldWhitelist
                updatedData["whitelisted"] = shouldWhitelist
                
                if (newName != null && newName != mapping.key) {
                    updatedData["name"] = newName
                    tokenRepository.renameTokenData(mapping.key, newName, updatedData)
                } else {
                    tokenRepository.updateTokenData(mapping.key, updatedData)
                }
                
                loadPoolMappings(fetchLiquidity = false)
                initializeNodeClient() // Refresh trader with new token names/keys
                fetchWalletBalances() // Refresh balances for newly whitelisted token
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save whitelist change", e)
            }
        }
    }

    fun fetchTransactionHistory(loadMore: Boolean = false) {
        // Fetch from ALL wallet addresses, not just selected, so we see all wallet activity
        val allAddrs = _uiState.value.walletAddresses.toSet()
        val selectedAddrs = _uiState.value.selectedAddresses
        val fallbackAddress = _uiState.value.selectedAddress
        
        val addressesToFetch = if (allAddrs.isNotEmpty()) allAddrs else if (selectedAddrs.isNotEmpty()) selectedAddrs else {
            if (fallbackAddress.isEmpty()) return
            setOf(fallbackAddress)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchTransactionHistory: walletAddresses=${allAddrs.size}, selected=${selectedAddrs.size}, fetching=${addressesToFetch.size} addresses: ${addressesToFetch.map { it.take(8) }}")

        if (loadMore) {
            if (_uiState.value.isLoadingHistory) return
        } else {
            _uiState.value = _uiState.value.copy(networkTrades = emptyList(), historyOffset = 0)
        }
        _uiState.value = _uiState.value.copy(isLoadingHistory = true)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = nodeClient ?: return@launch
                val currentOffset = if (loadMore) _uiState.value.historyOffset else 0
                val limit = 50

                // Build ergoTree → address map — ALL in parallel
                val ergoTreeToAddr = mutableMapOf<String, String>()
                val treeJobs = addressesToFetch.map { addr ->
                    async {
                        try {
                            val treeRes = client.api.addressToErgoTree(addr)
                            val tree = (treeRes["ergoTree"] as? String) ?: (treeRes["tree"] as? String) ?: ""
                            if (tree.isNotEmpty()) addr to tree else null
                        } catch (_: Exception) { null }
                    }
                }
                awaitAll(*treeJobs.toTypedArray()).filterNotNull().forEach { (addr, tree) ->
                    ergoTreeToAddr[tree] = addr
                }

                val newTrades = mutableListOf<NetworkTransaction>()

                // Fetch ALL confirmed + unconfirmed TXs in parallel
                val txJobs = addressesToFetch.flatMap { address ->
                    val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<NetworkTransaction>>>()

                    // Unconfirmed (only on first page)
                    if (currentOffset == 0) {
                        val ergoTree = ergoTreeToAddr.entries.find { it.value == address }?.key ?: ""
                        if (ergoTree.isNotEmpty()) {
                            jobs.add(async {
                                try {
                                    val reqBody = "\"$ergoTree\"".toRequestBody("application/json".toMediaTypeOrNull())
                                    val unconfList = client.api.getUnconfirmedTransactionsByErgoTree(
                                        offset = 0, limit = 50, ergoTree = reqBody
                                    )
                                    parseNetworkTransactions(unconfList, addressesToFetch, false, ergoTreeToAddr)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed fetching unconfirmed for $address", e)
                                    emptyList()
                                }
                            })
                        }
                    }

                    // Confirmed
                    jobs.add(async {
                        try {
                            val reqBody = "\"$address\"".toRequestBody("application/json".toMediaTypeOrNull())
                            val confResp = client.api.getTransactionsByAddress(
                                offset = currentOffset, limit = limit, address = reqBody
                            )
                            @Suppress("UNCHECKED_CAST")
                            val confList = confResp["items"] as? List<Map<String, Any>> ?: emptyList()
                            if (BuildConfig.DEBUG) Log.d(TAG, "Confirmed for ${address.take(8)}: ${confList.size} txs")
                            parseNetworkTransactions(confList, addressesToFetch, true, ergoTreeToAddr)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed fetching confirmed for $address", e)
                            emptyList()
                        }
                    })

                    jobs
                }
                awaitAll(*txJobs.toTypedArray()).forEach { newTrades.addAll(it) }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val currentList = if (loadMore) _uiState.value.networkTrades else emptyList()
                    
                    // Filter out duplicates (same tx seen from multiple addresses or unconfirmed→confirmed)
                    // Sort: unconfirmed first, then by timestamp descending
                    val finalTrades = (currentList + newTrades)
                        .distinctBy { it.id }
                        .sortedWith(compareBy<NetworkTransaction> { it.isConfirmed }.thenByDescending { it.timestamp })
                    if (BuildConfig.DEBUG) Log.d(TAG, "Total raw=${newTrades.size}, unique=${finalTrades.size}, first=${finalTrades.firstOrNull()?.id?.take(8)}")

                    _uiState.value = _uiState.value.copy(
                        networkTrades = finalTrades,
                        historyOffset = currentOffset + limit,
                        isLoadingHistory = false
                    )
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingHistory = false)
                }
            }
        }
    }

    private fun parseNetworkTransactions(
        rawList: List<Map<String, Any>>, 
        myAddresses: Set<String>, 
        isConfirmed: Boolean,
        ergoTreeToAddr: Map<String, String> = emptyMap()
    ): List<NetworkTransaction> {

        val parsed = mutableListOf<NetworkTransaction>()

        for (tx in rawList) {
            val id = tx["id"] as? String ?: continue
            val timestamp = (tx["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val inclusionHeight = (tx["inclusionHeight"] as? Number)?.toInt()
            val numConfirmations = (tx["numConfirmations"] as? Number)?.toInt()

            @Suppress("UNCHECKED_CAST")
            val rawInputs = tx["inputs"] as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val rawOutputs = tx["outputs"] as? List<Map<String, Any>> ?: emptyList()

            val parsedInputs = mutableListOf<TxBox>()
            var myErgIn = 0L
            val myTokensIn = mutableMapOf<String, Long>()

            for (inp in rawInputs) {
                val boxId = inp["boxId"] as? String ?: ""
                val value = (inp["value"] as? Number)?.toLong() ?: 0L
                var addr = inp["address"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val assets = inp["assets"] as? List<Map<String, Any>> ?: emptyList()
                val tree = inp["ergoTree"] as? String ?: ""

                // Resolve address from ergoTree if address is empty
                if (addr.isEmpty() && tree.isNotEmpty()) {
                    addr = ergoTreeToAddr[tree] ?: ""
                }

                parsedInputs.add(TxBox(boxId, value, addr, assets, tree))

                if (addr in myAddresses) {

                    myErgIn += value
                    for (asset in assets) {
                        val tokenId = asset["tokenId"] as? String ?: continue
                        val amount = (asset["amount"] as? Number)?.toLong() ?: 0L
                        myTokensIn[tokenId] = myTokensIn.getOrDefault(tokenId, 0L) + amount
                    }
                }
            }


            val parsedOutputs = mutableListOf<TxBox>()
            var myErgOut = 0L
            val myTokensOut = mutableMapOf<String, Long>()

            for (outp in rawOutputs) {
                val boxId = outp["boxId"] as? String ?: ""
                val value = (outp["value"] as? Number)?.toLong() ?: 0L
                var addr = outp["address"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val assets = outp["assets"] as? List<Map<String, Any>> ?: emptyList()
                val tree = outp["ergoTree"] as? String ?: ""

                // Resolve address from ergoTree if address is empty
                if (addr.isEmpty() && tree.isNotEmpty()) {
                    addr = ergoTreeToAddr[tree] ?: ""
                }

                parsedOutputs.add(TxBox(boxId, value, addr, assets, tree))

                if (addr in myAddresses) {

                    myErgOut += value
                    for (asset in assets) {
                        val tokenId = asset["tokenId"] as? String ?: continue
                        val amount = (asset["amount"] as? Number)?.toLong() ?: 0L
                        myTokensOut[tokenId] = myTokensOut.getOrDefault(tokenId, 0L) + amount
                    }
                }
            }


            var netErgChange = myErgOut - myErgIn
            val netTokenChanges = mutableMapOf<String, Long>()
            val allTokenIds = myTokensIn.keys + myTokensOut.keys
            for (tid in allTokenIds) {
                val net = (myTokensOut[tid] ?: 0L) - (myTokensIn[tid] ?: 0L)
                if (net != 0L) {
                    netTokenChanges[tid] = net
                }
            }

            // Identify known protocols
            var protocolLabel: String? = null
            var dexPoolAddress: String? = null
            for (box in parsedInputs + parsedOutputs) {
                val match = NetworkConfig.KNOWN_PROTOCOLS.entries.find { box.address.startsWith(it.key) }
                if (match != null) {
                    protocolLabel = match.value
                    // Remember DEX pool address for proxy swap detection
                    if (match.value == "DEX" || match.value.contains("LP Swap")) {
                        dexPoolAddress = match.key
                    }
                    break
                }
            }

            // Proxy swap fallback: if this is a DEX tx but user has no inputs
            // (batch bot executed via proxy contract), compute from pool box deltas.
            // BUT only if user's output correlates with pool changes (not just a bot fee).
            if (dexPoolAddress != null && myErgIn == 0L && myErgOut > 0L) {
                @Suppress("UNCHECKED_CAST")
                val poolInput = rawInputs.find { box ->
                    val addr = box["address"] as? String ?: ""
                    val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                    addr.startsWith(dexPoolAddress.take(30)) && assets.size >= 3
                }
                @Suppress("UNCHECKED_CAST")
                val poolOutput = rawOutputs.find { box ->
                    val addr = box["address"] as? String ?: ""
                    val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                    addr.startsWith(dexPoolAddress.take(30)) && assets.size >= 3
                }
                if (poolInput != null && poolOutput != null) {
                    val poolErgIn = (poolInput["value"] as? Number)?.toLong() ?: 0L
                    val poolErgOut = (poolOutput["value"] as? Number)?.toLong() ?: 0L
                    val poolErgDelta = poolErgOut - poolErgIn // positive = pool gained ERG

                    @Suppress("UNCHECKED_CAST")
                    val pInAssets = poolInput["assets"] as? List<Map<String, Any>> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val pOutAssets = poolOutput["assets"] as? List<Map<String, Any>> ?: emptyList()

                    // Check LP token delta (assets[1]) to detect withdraw/deposit
                    val lpTokenIn = (pInAssets.getOrNull(1)?.get("amount") as? Number)?.toLong() ?: 0L
                    val lpTokenOut = (pOutAssets.getOrNull(1)?.get("amount") as? Number)?.toLong() ?: 0L
                    val lpDelta = lpTokenOut - lpTokenIn

                    val tokenId = pInAssets.getOrNull(2)?.get("tokenId") as? String ?: ""
                    val poolTokenIn = (pInAssets.getOrNull(2)?.get("amount") as? Number)?.toLong() ?: 0L
                    val poolTokenOut = (pOutAssets.getOrNull(2)?.get("amount") as? Number)?.toLong() ?: 0L
                    val poolTokenDelta = poolTokenOut - poolTokenIn

                    // Determine if user is the real trader or just received a bot fee:
                    // Real proxy swap: user received tokens that the pool lost, OR LP withdraw/deposit
                    val userGotPoolTokens = tokenId.isNotEmpty() && myTokensOut.containsKey(tokenId)
                    val isLpOperation = lpDelta != 0L

                    if (userGotPoolTokens || isLpOperation) {
                        // Real proxy swap / LP operation — override net changes with pool deltas
                        if (lpDelta > 0) protocolLabel = "DEX LP Withdraw"
                        else if (lpDelta < 0) protocolLabel = "DEX LP Deposit"

                        netErgChange = -poolErgDelta
                        netTokenChanges.clear()
                        if (tokenId.isNotEmpty() && poolTokenDelta != 0L) {
                            netTokenChanges[tokenId] = -poolTokenDelta
                        }
                    } else {
                        // Bot fee / incidental output — keep original myErgOut as net change
                        protocolLabel = "Received"
                    }
                }
            }

            // Non-DEX protocol TX where user has no inputs — also just "Received"
            if (protocolLabel != null && protocolLabel != "Received"
                && dexPoolAddress == null && myErgIn == 0L && myTokensIn.isEmpty() && netErgChange > 0) {
                protocolLabel = "Received"
            }

            // Only add if it actually affects our address
            if (myErgIn > 0 || myErgOut > 0 || netTokenChanges.isNotEmpty()) {
                val feeTree = "1005040004000e36100204a00b08cd0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798ea02d192a39a8cc7a701730073011001020402d19683030193a38cc7b2a57300000193c2b2a57301007473027303830108cdeeac93b1a57304"
                val feeVal = parsedOutputs.find { it.ergoTree == feeTree }?.value ?: 0L

                parsed.add(NetworkTransaction(
                    id = id,
                    timestamp = timestamp,
                    isConfirmed = isConfirmed,
                    netErgChange = netErgChange,
                    netTokenChanges = netTokenChanges,
                    inputs = parsedInputs,
                    outputs = parsedOutputs,
                    inclusionHeight = inclusionHeight,
                    numConfirmations = numConfirmations,
                    label = protocolLabel,
                    fee = feeVal
                ))
            }

        }
        return parsed
    }

    fun resetTokenData() {
        tokenRepository.resetTokenData()
        loadPoolMappings(fetchLiquidity = false)
        syncTokenList(isFirstLaunch = true) // Resync immediately
    }

    fun exportWallets(context: Context) {
        val wallets = preferenceManager.loadWallets()
        val json = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(wallets)
        
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, json)
            type = "text/plain"
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Export Wallets JSON")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun importWallets(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                val wallets: Map<String, Any> = com.google.gson.Gson().fromJson(json, type)
                
                preferenceManager.saveWallets(wallets)
                
                val displayWallets = mutableListOf<String>()
                @Suppress("UNCHECKED_CAST")
                wallets.forEach { (wname, data) ->
                    val wtype = (data as? Map<String, Any>)?.get("type") == "ergopay"
                    if (wtype) displayWallets.add("$wname (Ergopay)") else displayWallets.add(wname)
                }
                if (!displayWallets.contains("ErgoPay")) displayWallets.add("ErgoPay")

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(wallets = displayWallets)
                    // If no wallet is selected, select the first one if available
                    if (_uiState.value.selectedWallet.isEmpty() || _uiState.value.selectedWallet == "Select Wallet") {
                        val firstWallet = wallets.keys.firstOrNull()
                        if (firstWallet != null) {
                            finalizeSelection(firstWallet)
                        }
                    } else {
                        // Refresh current wallet data in case it changed
                        fetchWalletBalances()
                        fetchTransactionHistory()
                    }
                }
            } catch (e: Exception) {
                Log.e("ImportWallets", "Failed to import: ${e.message}")
            }
        }
    }

    // ─── PORTFOLIO FUNCTIONS ─────────────────────────────────────────────────

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

    /** Sync oracle price data from chain on startup (lightweight — no market sync) */
    fun syncOraclePrices() {
        if (oracleSyncJob?.isActive == true) return
        oracleSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Load cached data from disk — fast, no network
                oraclePriceStore.loadAll()

                // 2. Show cached chart immediately
                fetchErgPrice()

                // 3. Sync oracle data only (USE, SigUSD, SigUSD DEX)
                try {
                    oraclePriceStore.syncAll(nodePool)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Oracle sync failed: ${e.message}")
                }

                // 4. Refresh chart with latest synced data
                fetchErgPrice()

                // 5. Load any cached market data from disk (no network)
                val allTokens = tokenRepository.getWhitelistedTokensWithPools()
                if (allTokens.isNotEmpty() && oraclePriceStore.allTokenMarketData.isEmpty()) {
                    // Rebuild from cached files
                    oraclePriceStore.rebuildMarketDataFromCache(allTokens)
                    _uiState.value = _uiState.value.copy(
                        tokenMarketData = oraclePriceStore.allTokenMarketData
                    )
                }

                // 6. Check if market sync has ever completed
                val prefs = getApplication<android.app.Application>()
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

                // 7. Auto-start market sync ONLY if no data exists (first sync / cache wiped)
                if (incomplete && lastSyncMs == 0L && oraclePriceStore.allTokenMarketData.isEmpty()) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "No market data — auto-starting first sync")
                    startMarketSync()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Oracle sync failed: ${e.message}")
            }
        }
    }

    /**
     * Start market sync — called from the sync dialog UI.
     * Syncs all whitelisted tokens for ecosystem price/volume data.
     * User-controlled: can be stopped via stopMarketSync().
     */
    fun startMarketSync() {
        if (marketSyncJob?.isActive == true) return

        val allTokens = tokenRepository.getWhitelistedTokensWithPools()
        if (allTokens.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            marketSyncState = "syncing",
            marketSyncProgress = 0f,
            marketSyncLabel = "",
            marketSyncIndex = 0,
            marketSyncTotal = allTokens.size
        )

        marketSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                oraclePriceStore.syncAllTokens(nodePool, allTokens)

                // Sync completed fully
                val now = System.currentTimeMillis()
                val prefs = getApplication<android.app.Application>()
                    .getSharedPreferences("oracle_sync", android.content.Context.MODE_PRIVATE)
                prefs.edit().putLong("lastMarketSyncMs", now).apply()

                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "completed",
                    marketSyncProgress = 1f,
                    lastMarketSyncMs = now,
                    marketSyncIncomplete = false
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Market sync completed")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User stopped the sync — progress is saved by OraclePriceStore
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "idle",
                    marketSyncIncomplete = true
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Market sync stopped by user")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tokenMarketData = oraclePriceStore.allTokenMarketData,
                    marketSyncState = "idle",
                    marketSyncIncomplete = true
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Market sync failed: ${e.message}")
            }
        }
    }

    /** Stop market sync — called from dialog when user presses Stop */
    fun stopMarketSync() {
        marketSyncJob?.cancel()
        marketSyncJob = null
    }

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

    /**
     * Fully stop all in-flight price syncs, delete all cached oracle data, reset chart state,
     * then kick off a clean full re-sync.
     *
     * IMPORTANT: Both jobs are cancelled with cancelAndJoin() — this waits until the coroutines
     * have fully terminated (including any blocking saveToFile() calls). Plain cancel() does NOT
     * interrupt blocking I/O, so without join() an in-flight sync can re-create the JSON files
     * that clearAll() just deleted, causing stale partial data (e.g. 6 months) to persist.
     */
    fun clearAndResync() {
        viewModelScope.launch {
            // 1. Stop all in-flight syncs and WAIT for them to fully terminate.
            //    cancelAndJoin() is a suspend function — it yields until the job is done.
            oracleSyncJob?.cancelAndJoin()
            oracleSyncJob = null
            tokenSyncJob?.cancelAndJoin()
            tokenSyncJob = null

            // 2. NOW safe to delete — no background writer can race against the delete.
            oraclePriceStore.clearAll()

            // 3. Reset all chart UI state.
            _uiState.value = _uiState.value.copy(
                ergPriceUsd = null,
                ergPriceHistory = emptyList(),
                sigUsdOracleHistory = emptyList(),
                sigUsdDexHistory = emptyList(),
                selectedChartToken = null,
                tokenPriceHistory = emptyList()
            )

            // 4. Kick off a fresh full re-sync.
            syncOraclePrices()
        }
    }

    /** Select a token to show its DEX price chart (in ERG) */
    fun selectChartToken(tokenName: String?) {
        // Cancel any in-flight token sync
        tokenSyncJob?.cancel()
        tokenSyncJob = null

        val range = _uiState.value.chartRange
        if (tokenName == null) {
            _uiState.value = _uiState.value.copy(
                selectedChartToken = null,
                tokenPriceHistory = emptyList()
            )
            fetchErgPrice(range)
            return
        }

        _uiState.value = _uiState.value.copy(
            selectedChartToken = tokenName,
            tokenPriceHistory = emptyList() // Clear stale data from previous token
        )

        // Load cached data immediately if available (price + volume)
        if (oraclePriceStore.hasTokenData(tokenName)) {
            val history = oraclePriceStore.getTokenHistory(tokenName, range)
            val (v24, v7) = oraclePriceStore.getTokenVolume(tokenName)
            _uiState.value = _uiState.value.copy(
                tokenPriceHistory = history,
                poolVolume24h = v24,
                poolVolume7d = v7
            )
        }

        // Sync from chain — cancel-safe, with node fallback
        // Publishes intermediate data after each checkpoint save so chart loads progressively
        tokenSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val poolNft = tokenRepository.getPoolNftForToken(tokenName) ?: return@launch
                val decimals = tokenRepository.getDecimalsForToken(tokenName)
                try {
                    oraclePriceStore.syncTokenDex(nodePool, tokenName, poolNft, decimals) {
                        // Checkpoint callback — update chart with data so far
                        val h = oraclePriceStore.getTokenHistory(tokenName, range)
                        if (h.isNotEmpty()) _uiState.value = _uiState.value.copy(tokenPriceHistory = h)
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Token sync failed: ${e.message}")
                }
                val history = oraclePriceStore.getTokenHistory(tokenName, range)
                val (v24, v7) = oraclePriceStore.getTokenVolume(tokenName)
                _uiState.value = _uiState.value.copy(
                    tokenPriceHistory = history,
                    poolVolume24h = v24,
                    poolVolume7d = v7
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Token sync cancelled: $tokenName")
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Token chart sync failed: ${e.message}")
            }
        }

        // Also fetch recent pool trades for the selected pair
        viewModelScope.launch(Dispatchers.IO) { fetchPoolTrades(tokenName) }
    }

    /** Get list of token names that have DEX pools (for price chart dropdown) */
    fun getTokensWithPools(): List<String> {
        return tokenRepository.getTokenNamesWithPools()
    }

    /**
     * Fetch the most recent trades for the selected token's pool.
     * Derives trades from the delta between consecutive pool state boxes (pool NFT boxes).
     * isBuy = pool gained ERG (user sold ERG to buy tokens)
     * isSell = pool lost ERG (user sold tokens to get ERG)
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchPoolTrades(tokenName: String) {
        val client = nodeClient ?: nodePool.next()
        _uiState.value = _uiState.value.copy(isLoadingPoolTrades = true, poolTrades = emptyList(), poolVolume24h = 0.0, poolVolume7d = 0.0)
        try {
            val poolNft = tokenRepository.getPoolNftForToken(tokenName) ?: return
            val decimals = tokenRepository.getDecimalsForToken(tokenName)
            val tokenDiv = Math.pow(10.0, decimals.toDouble())
            // Get the traded tokenId (asset index 2 in pool box)
            val tokenId = tokenRepository.getTokenIdForName(tokenName) ?: ""
            val nowMs = System.currentTimeMillis()
            val currentHeight = try { client.getHeight() } catch (e: Exception) { 0 }

            // Fetch the last ~16 pool state boxes — gives us ~15 trade deltas
            val resp = client.api.getBoxesByTokenId(poolNft, 0, 16)
            val boxes = resp["items"] as? List<Map<String, Any>> ?: return
            if (boxes.size < 2) return

            // Build raw trades (no trader address yet)
            data class RawTrade(val isBuy: Boolean, val erg: Double, val tokens: Double, val timestamp: Long, val txId: String)
            val rawTrades = mutableListOf<RawTrade>()
            var vol24h = 0.0
            var vol7d = 0.0
            val cutoff24h = nowMs - 24 * 3_600_000L
            val cutoff7d = nowMs - 7 * 24 * 3_600_000L

            for (i in 0 until boxes.size - 1) {
                val newer = boxes[i]
                val older = boxes[i + 1]
                val newerErg = (newer["value"] as? Number)?.toLong() ?: continue
                val olderErg = (older["value"] as? Number)?.toLong() ?: continue
                val newerAssets = newer["assets"] as? List<Map<String, Any>> ?: continue
                val olderAssets = older["assets"] as? List<Map<String, Any>> ?: continue
                if (newerAssets.size < 3 || olderAssets.size < 3) continue
                val newerToken = (newerAssets[2]["amount"] as? Number)?.toLong() ?: continue
                val olderToken = (olderAssets[2]["amount"] as? Number)?.toLong() ?: continue
                val ergDelta = newerErg - olderErg
                val tokenDelta = newerToken - olderToken
                val ergAbs = Math.abs(ergDelta) / 1_000_000_000.0
                val tokenAbs = Math.abs(tokenDelta) / tokenDiv
                if (ergAbs < 0.001 && tokenAbs < 0.000001) continue
                val txId = (newer["transactionId"] as? String) ?: ""
                val height = (newer["inclusionHeight"] as? Number)?.toInt() ?: 0
                val estimatedTs = if (currentHeight > 0 && height > 0)
                    nowMs - (currentHeight - height) * 120_000L else 0L
                rawTrades.add(RawTrade(ergDelta > 0, ergAbs, tokenAbs, estimatedTs, txId))

                // Accumulate volume
                if (estimatedTs >= cutoff24h) vol24h += ergAbs
                if (estimatedTs >= cutoff7d) vol7d += ergAbs
            }

            // Publish without addresses immediately so UI shows fast
            _uiState.value = _uiState.value.copy(
                poolTrades = rawTrades.map { PoolTrade(it.isBuy, it.erg, it.tokens, it.timestamp, it.txId) },
                isLoadingPoolTrades = false,
                poolVolume24h = vol24h,
                poolVolume7d = vol7d
            )

            // Now enrich with real tx timestamp + trader address concurrently
            val enriched = coroutineScope {
                rawTrades.map { raw ->
                    async {
                        try {
                            if (raw.txId.isEmpty()) return@async PoolTrade(raw.isBuy, raw.erg, raw.tokens, raw.timestamp, raw.txId)
                            val tx = client.api.getTransactionById(raw.txId)
                                ?: return@async PoolTrade(raw.isBuy, raw.erg, raw.tokens, raw.timestamp, raw.txId)
                            val realTs = (tx["timestamp"] as? Number)?.toLong() ?: raw.timestamp
                            @Suppress("UNCHECKED_CAST")
                            val outputs = tx["outputs"] as? List<Map<String, Any>> ?: emptyList()
                            // For BUY trades: find the output that RECEIVES the traded token
                            // For SELL trades: find the output that RECEIVES the most ERG (non-pool)
                            val trader = if (raw.isBuy) {
                                // Buyer: look for the output that holds the traded token
                                outputs.firstOrNull { out ->
                                    val assets = out["assets"] as? List<Map<String, Any>> ?: emptyList()
                                    val hasPoolNft = assets.any { (it["tokenId"] as? String) == poolNft }
                                    val hasTradedToken = tokenId.isNotEmpty() && assets.any { (it["tokenId"] as? String) == tokenId }
                                    !hasPoolNft && hasTradedToken
                                }?.get("address") as? String ?: ""
                            } else {
                                // Seller: look for the output with most ERG that is not the pool
                                outputs.filter { out ->
                                    val assets = out["assets"] as? List<Map<String, Any>> ?: emptyList()
                                    val hasPoolNft = assets.any { (it["tokenId"] as? String) == poolNft }
                                    !hasPoolNft
                                }.maxByOrNull { (it["value"] as? Number)?.toLong() ?: 0L
                                }?.get("address") as? String ?: ""
                            }
                            PoolTrade(raw.isBuy, raw.erg, raw.tokens, realTs, raw.txId, trader)
                        } catch (e: Exception) {
                            PoolTrade(raw.isBuy, raw.erg, raw.tokens, raw.timestamp, raw.txId)
                        }
                    }
                }.awaitAll()
            }
            // Re-compute volume with real timestamps if available
            val vol24hReal = enriched.filter { it.timestamp >= cutoff24h }.sumOf { it.ergAmount }
            val vol7dReal = enriched.filter { it.timestamp >= cutoff7d }.sumOf { it.ergAmount }
            _uiState.value = _uiState.value.copy(poolTrades = enriched, poolVolume24h = vol24hReal, poolVolume7d = vol7dReal)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "fetchPoolTrades failed: ${e.message}")
            _uiState.value = _uiState.value.copy(isLoadingPoolTrades = false)
        }
    }

    /**
     * AMM sell output: how much ERG you'd get selling `inputAmount` tokens.
     * Uses constant-product formula with fee.
     */
    fun ammSellOutput(ergReserve: Long, tokenReserve: Long, inputAmount: Long, feePercent: Double): Long {
        if (tokenReserve <= 0 || ergReserve <= 0 || inputAmount <= 0) return 0L
        val feeNum = ((1.0 - feePercent) * 1000).toLong()
        val feeDenom = 1000L
        val numerator = ergReserve * inputAmount * feeNum
        val denominator = tokenReserve * feeDenom + inputAmount * feeNum
        return if (denominator > 0) numerator / denominator else 0L
    }

    private var isFetchingTokenValues = false

    /**
     * Batch-fetch USD values for all wallet tokens.
     * For each token: find its DEX pool -> fetch pool box -> AMM sell full balance -> multiply by ERG price.
     * Stores results in SwapState.tokenUsdValues.
     * Guarded: skips if already fetching or if cached values exist for current tokens.
     */
    fun fetchTokenUsdValues(force: Boolean = false) {
        if (isFetchingTokenValues) return
        val client = nodeClient ?: return
        
        // Always fetch ERG/USD price from the USE oracle (on-chain, not CoinGecko)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oracleBox = client.getPoolBox(
                    com.piggytrade.piggytrade.stablecoin.use.UseConfig.ORACLE_NFT,
                    false
                )
                if (oracleBox != null) {
                    val regs = oracleBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
                    val r4 = regs["R4"] as? String ?: "0500"
                    val oracleRate = com.piggytrade.piggytrade.stablecoin.VlqCodec.decode(r4)
                    if (oracleRate > 0) {
                        // oracleRate = nanoERG per 1 USD → USD per ERG = 1e9 / oracleRate
                        val usdPerErg = 1_000_000_000.0 / oracleRate.toDouble()
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(ergPriceUsd = usdPerErg)
                        }
                        fetchTokenUsdValuesInternal(usdPerErg, force)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Oracle price fetch failed: ${e.message}")
            }
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun fetchTokenUsdValuesInternal(ergPrice: Double, force: Boolean) {
        if (isFetchingTokenValues) return
        val tokens = _uiState.value.walletTokens
        if (tokens.isEmpty()) return

        // Skip if we already have values (unless forced).
        // Most wallet tokens are NFTs without pools, so checking "all cached" never passes.
        if (!force && _uiState.value.tokenUsdValues.isNotEmpty()) return

        // Get ALL ERG-only pools (not T2T)
        val allPools = (_uiState.value.whitelistedPools + _uiState.value.discoveredPools)
            .filter { !it.data.containsKey("id_in") && it.pid.isNotEmpty() }
        if (allPools.isEmpty()) return

        isFetchingTokenValues = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                data class PoolBoxResult(
                    val pool: PoolMapping,
                    val tokenId: String,
                    val ergReserve: Long,
                    val tokenReserve: Long,
                    val fee: Double
                )
                
                val poolResults = mutableListOf<PoolBoxResult>()
                val matchedTokenIds = mutableSetOf<String>()
                
                // ── TIER 1: Direct match via pool.data["id"] (enriched/synced pools) ──
                // Synced pools have "id" = token ID. Match against wallet tokens instantly.
                // Still need pool box data for reserves, so check cache or queue for fetch.
                val walletTokenSet = tokens.keys.toSet()
                val poolsNeedingBoxData = mutableListOf<PoolMapping>()
                
                for (pool in allPools) {
                    val tokenId = pool.data["id"] as? String ?: continue
                    if (tokenId !in walletTokenSet) continue // Not in wallet, skip
                    if (tokenId in matchedTokenIds) continue // Already matched by a better pool
                    
                    val amount = tokens[tokenId] ?: 0L
                    if (amount <= 0L) continue
                    
                    // Try cache first
                    val cached = synchronized(poolBoxDataCache) { poolBoxDataCache[pool.pid] }
                    if (cached != null) {
                        val assets = cached["assets"] as? List<Map<String, Any>>
                        if (assets != null && assets.size >= 3) {
                            val ergReserve = (cached["value"] as? Number)?.toLong() ?: 0L
                            val tokenReserve = (assets[2]["amount"] as? Number)?.toLong() ?: 0L
                            val fee = pool.data["fee"] as? Double ?: 0.003
                            poolResults.add(PoolBoxResult(pool, tokenId, ergReserve, tokenReserve, fee))
                            matchedTokenIds.add(tokenId)
                            continue
                        }
                    }
                    // Queue for fetch
                    poolsNeedingBoxData.add(pool)
                }
                
                // ── TIER 2: Pool box cache (from liquidity fetch) for pools without data["id"] ──
                for (pool in allPools) {
                    if (pool.data.containsKey("id")) continue // Already handled in Tier 1
                    val cached = synchronized(poolBoxDataCache) { poolBoxDataCache[pool.pid] } ?: continue
                    val assets = cached["assets"] as? List<Map<String, Any>> ?: continue
                    if (assets.size < 3) continue
                    val tokenId = assets[2]["tokenId"] as? String ?: continue
                    if (tokenId !in walletTokenSet || tokenId in matchedTokenIds) continue
                    val ergReserve = (cached["value"] as? Number)?.toLong() ?: 0L
                    val tokenReserve = (assets[2]["amount"] as? Number)?.toLong() ?: 0L
                    val fee = pool.data["fee"] as? Double ?: 0.003
                    poolResults.add(PoolBoxResult(pool, tokenId, ergReserve, tokenReserve, fee))
                    matchedTokenIds.add(tokenId)
                }
                
                // ── TIER 3: Fetch remaining pools that need box data ──
                // Also fetch pools without data["id"] that weren't in cache
                val unmatchedTokens = walletTokenSet - matchedTokenIds
                if (unmatchedTokens.isNotEmpty()) {
                    // Add non-enriched pools that might hold unmatched tokens
                    for (pool in allPools) {
                        if (pool.data.containsKey("id")) continue
                        if (pool.pid in poolBoxDataCache) continue // Already checked in Tier 2
                        poolsNeedingBoxData.add(pool)
                    }
                }
                
                if (poolsNeedingBoxData.isNotEmpty()) {
                    val jobs = poolsNeedingBoxData.map { pool ->
                        async {
                            try {
                                nodePool.withRetry { poolClient ->
                                    val box = poolClient.getPoolBox(pool.pid, _uiState.value.includeUnconfirmed) ?: return@withRetry
                                    val assets = box["assets"] as? List<Map<String, Any>> ?: return@withRetry
                                    if (assets.size < 3) return@withRetry
                                    val tokenId = assets[2]["tokenId"] as? String ?: return@withRetry
                                    val ergReserve = (box["value"] as? Number)?.toLong() ?: 0L
                                    val tokenReserve = (assets[2]["amount"] as? Number)?.toLong() ?: 0L
                                    val fee = pool.data["fee"] as? Double ?: 0.003
                                    synchronized(poolResults) {
                                        poolResults.add(PoolBoxResult(pool, tokenId, ergReserve, tokenReserve, fee))
                                    }
                                    synchronized(poolBoxDataCache) { poolBoxDataCache[pool.pid] = box }
                                }
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Pool box fetch failed for ${pool.key}: ${e.message}")
                                }
                            }
                            Unit
                        }
                    }
                    jobs.awaitAll()
                }

                // Step 2: For each wallet token, find matching pool(s) and calculate spot price
                // Use the most liquid pool (highest ERG reserve, prefer whitelisted)
                if (BuildConfig.DEBUG) Log.d(TAG, "Calculating spot prices with ergPrice=$ergPrice, poolResults=${poolResults.size}")
                val results = mutableMapOf<String, Double>()
                for ((walletTokenId, amount) in tokens) {
                    if (amount <= 0L) continue
                    
                    // Find ALL pools that hold this token
                    val matchingPools = poolResults.filter { it.tokenId == walletTokenId }
                    if (matchingPools.isEmpty()) continue
                    
                    // Pick the most liquid pool: prefer whitelisted, then highest ERG reserve
                    val bestPool = matchingPools
                        .sortedWith(compareByDescending<PoolBoxResult> { it.pool.isWhitelisted }
                            .thenByDescending { it.ergReserve })
                        .first()
                    
                    // AMM constant-product formula: actual ERG output for selling full balance
                    // outputErg = (inputTokens × ergReserve × feeNum) / (tokenReserve × feeDenom + inputTokens × feeNum)
                    // Uses Double to prevent Long overflow (amount × ergReserve × feeNum can exceed Long.MAX_VALUE)
                    // Then multiply ERG value by USE oracle price
                    if (bestPool.tokenReserve > 0 && bestPool.ergReserve > 0) {
                        val feeNum = (1.0 - bestPool.fee) * 1000.0  // e.g. 0.003 fee → 997.0
                        val feeDenom = 1000.0
                        val amt = amount.toDouble()
                        val ergRes = bestPool.ergReserve.toDouble()
                        val tokRes = bestPool.tokenReserve.toDouble()
                        val outputNanoErg = (amt * ergRes * feeNum) / (tokRes * feeDenom + amt * feeNum)
                        val totalErgValue = outputNanoErg / 1_000_000_000.0
                        val usdValue = totalErgValue * ergPrice
                        if (BuildConfig.DEBUG) {
                            val name = getTokenName(walletTokenId)
                            Log.d(TAG, "  CALC $name: ergRes=${bestPool.ergReserve} tokRes=${bestPool.tokenReserve} amt=$amount fee=${bestPool.fee} outputNanoErg=${"%.0f".format(outputNanoErg)} ergVal=${"%.6f".format(totalErgValue)} usd=${"%.6f".format(usdValue)} pool=${bestPool.pool.key}")
                        }
                        if (usdValue > 0.0) {
                            results[walletTokenId] = usdValue
                        }
                    }
                }

                if (BuildConfig.DEBUG) {
                    val unmatchedWallet = tokens.keys.filter { tid -> tid !in results.keys && (tokens[tid] ?: 0L) > 0L }
                    Log.d(TAG, "Token USD: ${results.size} priced, ${unmatchedWallet.size} unmatched from ${poolResults.size} pool results (${allPools.size} total pools)")
                    Log.d(TAG, "  Tier1 matched=${matchedTokenIds.size}, needFetch=${poolsNeedingBoxData.size}, cache=${poolBoxDataCache.size}")
                    for ((tid, usd) in results) {
                        val name = getTokenName(tid)
                        Log.d(TAG, "  PRICED: $name = $${"%.2f".format(usd)}")
                    }
                    for (tid in unmatchedWallet.filter { allPools.any { p -> (p.data["id"] as? String) == it } }) {
                        val name = getTokenName(tid)
                        Log.d(TAG, "  HAS_POOL_BUT_UNPRICED: $name (${tid.take(8)})")
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(tokenUsdValues = results)
                }
            } finally {
                isFetchingTokenValues = false
            }
        }
    }

    fun getTokenUsdValue(tokenId: String): Double? {
        return _uiState.value.tokenUsdValues[tokenId]
    }

    // ─── ECOSYSTEM FUNCTIONS ─────────────────────────────────────────────────

    /**
     * Master function: fetches TVL + recent ecosystem activity.
     * Shows cached data instantly, then refreshes from network.
     */
    fun fetchEcosystemData(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _uiState.value.isLoadingEcosystem) return
        if (!forceRefresh && now - _uiState.value.ecosystemLastFetched < 60_000) return

        val client = nodeClient ?: return

        // Show cached data instantly (no network)
        if (_uiState.value.ecosystemActivity.isEmpty()) {
            val cached = loadEcosystemCache()
            if (cached.isNotEmpty()) {
                ecosystemActivityCache = cached
                _uiState.value = _uiState.value.copy(ecosystemActivity = cached)
            }
        }

        _uiState.value = _uiState.value.copy(isLoadingEcosystem = true)
        ecosystemPage = 0

        // Ensure ERG price is available for USD conversion
        if (_uiState.value.ergPriceUsd == null) {
            fetchErgPrice()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tvlJob = async { fetchEcosystemTvlInternal(client) }
                val activityJob = async { fetchEcosystemActivityInternal(client) }

                val tvl = tvlJob.await()
                val allActivity = activityJob.await()

                // Save to disk for instant display next time
                saveEcosystemCache(allActivity)

                withContext(Dispatchers.Main) {
                    ecosystemActivityCache = allActivity
                    _uiState.value = _uiState.value.copy(
                        ecosystemTvl = tvl,
                        ecosystemActivity = allActivity,
                        isLoadingEcosystem = false,
                        ecosystemLastFetched = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Ecosystem fetch failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingEcosystem = false)
                }
            }
        }
    }

    /**
     * Load more ecosystem activity from network (cache exhausted).
     */
    fun fetchMoreEcosystemActivity() {
        if (_uiState.value.isLoadingEcosystem) return

        val client = nodeClient ?: return
        _uiState.value = _uiState.value.copy(isLoadingEcosystem = true)
        ecosystemPage++
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val more = fetchEcosystemActivityInternal(client, offset = ecosystemPage * 30)
                withContext(Dispatchers.Main) {
                    ecosystemActivityCache = (ecosystemActivityCache + more)
                        .distinctBy { it.txId }
                        .sortedByDescending { it.timestamp }
                    _uiState.value = _uiState.value.copy(
                        ecosystemActivity = ecosystemActivityCache,
                        isLoadingEcosystem = false
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingEcosystem = false)
                }
            }
        }
    }

    /** Full sorted cache of ecosystem activity (all protocols merged) */
    private var ecosystemActivityCache: List<EcosystemTx> = emptyList()
    /** Tracks current pagination page for ecosystem activity */
    private var ecosystemPage = 0

    /** Cached raw pool box data from liquidity fetch — maps pid -> box map */
    private val poolBoxDataCache = mutableMapOf<String, Map<String, Any>>()

    /** Save ecosystem activity cache to disk for instant display on next tab switch */
    private fun saveEcosystemCache(activity: List<EcosystemTx>) {
        try {
            val arr = org.json.JSONArray()
            for (tx in activity.take(100)) { // Cap at 100 entries to keep file small
                arr.put(org.json.JSONObject().apply {
                    put("txId", tx.txId)
                    put("protocol", tx.protocol)
                    put("timestamp", tx.timestamp)
                    put("traderAddress", tx.traderAddress)
                    put("sent", tx.sent)
                    put("received", tx.received)
                    put("priceImpact", tx.priceImpact ?: org.json.JSONObject.NULL)
                    put("isConfirmed", tx.isConfirmed)
                })
            }
            val ctx = getApplication<android.app.Application>()
            java.io.File(ctx.filesDir, "ecosystem_cache.json").writeText(arr.toString())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Save ecosystem cache failed: ${e.message}")
        }
    }

    /** Load ecosystem activity from disk cache */
    private fun loadEcosystemCache(): List<EcosystemTx> {
        try {
            val ctx = getApplication<android.app.Application>()
            val file = java.io.File(ctx.filesDir, "ecosystem_cache.json")
            if (!file.exists()) return emptyList()
            val arr = org.json.JSONArray(file.readText())
            val result = mutableListOf<EcosystemTx>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(EcosystemTx(
                    txId = obj.getString("txId"),
                    protocol = obj.getString("protocol"),
                    timestamp = obj.getLong("timestamp"),
                    traderAddress = obj.optString("traderAddress", ""),
                    sent = obj.optString("sent", ""),
                    received = obj.optString("received", ""),
                    priceImpact = if (obj.isNull("priceImpact")) null else obj.getDouble("priceImpact"),
                    isConfirmed = obj.optBoolean("isConfirmed", true)
                ))
            }
            return result
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Load ecosystem cache failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Fetch ERG TVL for key protocol contracts in parallel.
     */
    private suspend fun fetchEcosystemTvlInternal(client: NodeClient): Map<String, Double> =
        coroutineScope {
            val contracts = mapOf(
                "Spectrum DEX" to NetworkConfig.SPECTRUM_ADDRESS,
                "SigmaUSD" to "MUbV38YgqHy7XbsoXWF5z7EZm524Ybdwe5p9WDrbhruZRtehkRPT92imXer2eTkjwPDfboa1pR3zb3deVKVq3H7Xt98qcTqLuSBSbHb7izzo5jphEpcnqyKJ2xhmpNPVvmtbdJNdvdopPrHHDBbAGGeW7XYTQwEeoRfosXzcDtiGgw97b2aqjTsNFmZk7khBEQywjYfmoDc9nUCJMZ3vbSspnYo3LarLe55mh2Np8MNJqUN9APA6XkhZCrTTDRZb1B4krgFY1sVMswg2ceqguZRvC9pqt3tUUxmSnB24N6dowfVJKhLXwHPbrkHViBv1AKAJTmEaQW2DN1fRmD9ypXxZk8GXmYtxTtrj3BiunQ4qzUCu1eGzxSREjpkFSi2ATLSSDqUwxtRz639sHM6Lav4axoJNPCHbY8pvuBKUxgnGRex8LEGM8DeEJwaJCaoy8dBw9Lz49nq5mSsXLeoC4xpTUmp47Bh7GAZtwkaNreCu74m9rcZ8Di4w1cmdsiK1NWuDh9pJ2Bv7u3EfcurHFVqCkT3P86JUbKnXeNxCypfrWsFuYNKYqmjsix82g9vWcGMmAcu5nagxD4iET86iE2tMMfZZ5vqZNvntQswJyQqv2Wc6MTh4jQx1q2qJZCQe4QdEK63meTGbZNNKMctHQbp3gRkZYNrBtxQyVtNLR8xEY8zGp85GeQKbb37vqLXxRpGiigAdMe3XZA4hhYPmAAU5hpSMYaRAjtvvMT3bNiHRACGrfjvSsEG9G2zY5in2YWz5X9zXQLGTYRsQ4uNFkYoQRCBdjNxGv6R58Xq74zCgt19TxYZ87gPWxkXpWwTaHogG1eps8WXt8QzwJ9rVx6Vu9a5GjtcGsQxHovWmYixgBU8X9fPNJ9UQhYyAWbjtRSuVBtDAmoV1gCBEPwnYVP5GCGhCocbwoYhZkZjFZy6ws4uxVLid3FxuvhWvQrVEDYp7WRvGXbNdCbcSXnbeTrPMey1WPaXX",
                "USE Bank" to com.piggytrade.piggytrade.stablecoin.use.UseConfig.BANK_ADDRESS,
                "USE Pool" to (NetworkConfig.USE_CONFIG["pool_address"] as String),
                "DexyGold Bank" to com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.BANK_ADDRESS,
                "DexyGold Pool" to (NetworkConfig.DEXYGOLD_CONFIG["pool_address"] as String)
            )

            val jobs = contracts.map { (name, address) ->
                async {
                    try {
                        nodePool.withRetry { poolClient ->
                            val mediaType = "application/json".toMediaTypeOrNull()
                            val jsonAddress = "\"$address\""
                            val isMultiBox = name.contains("Spectrum") // Spectrum has many pool boxes
                            var totalErg = 0L
                            var maxErg = 0L
                            var offset = 0
                            val pageLimit = if (isMultiBox) 2 else 1
                            for (page in 0 until pageLimit) {
                                val boxes = poolClient.api.getUnspentBoxesByAddressPost(
                                    offset = offset, limit = 50,
                                    includeUnconfirmed = false, excludeMempoolSpent = false,
                                    address = jsonAddress.toRequestBody(mediaType)
                                )
                                for (box in boxes) {
                                    val v = (box["value"] as? Number)?.toLong() ?: 0L
                                    totalErg += v
                                    if (v > maxErg) maxErg = v
                                }
                                if (boxes.size < 50) break
                                offset += 50
                            }
                            val ergValue = if (isMultiBox) totalErg else maxErg
                            name to (ergValue.toDouble() / 1_000_000_000.0)
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "TVL fetch failed for $name: ${e.message}")
                        name to 0.0
                    }
                }
            }
            jobs.awaitAll().toMap()
        }

    /** Protocol addresses to scan for ecosystem activity */
    private val ecosystemProtocols = mapOf(
        NetworkConfig.SPECTRUM_ADDRESS to "DEX Swap",
        NetworkConfig.SPECTRUM_TOKEN_ADDRESS to "DEX T2T",
        // USE and DexyGold share the same LP swap contract address — label generically here,
        // then refine to "USE LP Swap" / "DexyGold LP Swap" in parseEcosystemTx via LP NFT detection
        (NetworkConfig.USE_CONFIG["lp_swap_address"] as String) to "LP Swap",
        // Bank addresses capture ALL mints (freemint, arbmint) AND redeems
        // Freemint/arbmint P2S addresses are NOT indexed by nodes — use bank instead
        com.piggytrade.piggytrade.stablecoin.use.UseConfig.BANK_ADDRESS to "USE Bank",
        com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.BANK_ADDRESS to "DexyGold Bank",
        // SigmaUSD Bank
        "MUbV38YgqHy7XbsoXWF5z7EZm524Ybdwe5p9WDrbhruZRtehkRPT92imXer2eTkjwPDfboa1pR3zb3deVKVq3H7Xt98qcTqLuSBSbHb7izzo5jphEpcnqyKJ2xhmpNPVvmtbdJNdvdopPrHHDBbAGGeW7XYTQwEeoRfosXzcDtiGgw97b2aqjTsNFmZk7khBEQywjYfmoDc9nUCJMZ3vbSspnYo3LarLe55mh2Np8MNJqUN9APA6XkhZCrTTDRZb1B4krgFY1sVMswg2ceqguZRvC9pqt3tUUxmSnB24N6dowfVJKhLXwHPbrkHViBv1AKAJTmEaQW2DN1fRmD9ypXxZk8GXmYtxTtrj3BiunQ4qzUCu1eGzxSREjpkFSi2ATLSSDqUwxtRz639sHM6Lav4axoJNPCHbY8pvuBKUxgnGRex8LEGM8DeEJwaJCaoy8dBw9Lz49nq5mSsXLeoC4xpTUmp47Bh7GAZtwkaNreCu74m9rcZ8Di4w1cmdsiK1NWuDh9pJ2Bv7u3EfcurHFVqCkT3P86JUbKnXeNxCypfrWsFuYNKYqmjsix82g9vWcGMmAcu5nagxD4iET86iE2tMMfZZ5vqZNvntQswJyQqv2Wc6MTh4jQx1q2qJZCQe4QdEK63meTGbZNNKMctHQbp3gRkZYNrBtxQyVtNLR8xEY8zGp85GeQKbb37vqLXxRpGiigAdMe3XZA4hhYPmAAU5hpSMYaRAjtvvMT3bNiHRACGrfjvSsEG9G2zY5in2YWz5X9zXQLGTYRsQ4uNFkYoQRCBdjNxGv6R58Xq74zCgt19TxYZ87gPWxkXpWwTaHogG1eps8WXt8QzwJ9rVx6Vu9a5GjtcGsQxHovWmYixgBU8X9fPNJ9UQhYyAWbjtRSuVBtDAmoV1gCBEPwnYVP5GCGhCocbwoYhZkZjFZy6ws4uxVLid3FxuvhWvQrVEDYp7WRvGXbNdCbcSXnbeTrPMey1WPaXX" to "SigmaUSD Bank"
    )

    /**
     * Fetch recent ecosystem activity from all protocol addresses.
     * Returns ALL merged results (no limit) — the caller handles display pagination.
     * Fetches 100 per protocol; "load more" pagination handles further pages.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchEcosystemActivityInternal(
        client: NodeClient, offset: Int = 0
    ): List<EcosystemTx> = coroutineScope {
        val mediaType = "application/json".toMediaTypeOrNull()
        val pageSize = 30

        val jobs = ecosystemProtocols.map { (address, protocolLabel) ->
            async {
                try {
                    nodePool.withRetry { poolClient ->
                        val reqBody = "\"$address\"".toRequestBody(mediaType)
                        val resp = poolClient.api.getTransactionsByAddress(
                            offset = offset, limit = pageSize, address = reqBody
                        )
                        val items = resp["items"] as? List<Map<String, Any>> ?: emptyList()
                        if (BuildConfig.DEBUG) Log.d(TAG, "Ecosystem $protocolLabel: ${items.size} txs at offset=$offset")
                        items.mapNotNull { tx -> parseEcosystemTx(tx, address, protocolLabel) }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Ecosystem activity fetch failed for $protocolLabel: ${e.message}")
                    emptyList()
                }
            }
        }

        jobs.awaitAll()
            .flatten()
            .distinctBy { it.txId }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Parse a raw transaction into an EcosystemTx with human-readable sent/received descriptions.
     * For DEX swaps, calculates price impact from pool reserve changes.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseEcosystemTx(
        tx: Map<String, Any>, protocolAddress: String, protocolLabel: String
    ): EcosystemTx? {
        val txId = tx["id"] as? String ?: return null
        val timestamp = (tx["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val numConf = (tx["numConfirmations"] as? Number)?.toInt() ?: 0
        val isConfirmed = numConf > 0

        val rawInputs = tx["inputs"] as? List<Map<String, Any>> ?: emptyList()
        val rawOutputs = tx["outputs"] as? List<Map<String, Any>> ?: emptyList()

        // For bank transactions, detect specific mint type from NFTs in the tx
        var effectiveLabel = protocolLabel
        if (protocolLabel.contains("Bank")) {
            val allTokenIds = mutableSetOf<String>()
            for (box in rawInputs + rawOutputs) {
                for (a in (box["assets"] as? List<Map<String, Any>> ?: emptyList())) {
                    (a["tokenId"] as? String)?.let { allTokenIds.add(it) }
                }
            }
            val useFreemintNft = com.piggytrade.piggytrade.stablecoin.use.UseConfig.FREEMINT_NFT
            val useArbmintNft = com.piggytrade.piggytrade.stablecoin.use.UseConfig.ARBMINT_NFT
            val dexyFreemintNft = com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.FREEMINT_NFT
            val dexyArbmintNft = com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.ARBMINT_NFT
            effectiveLabel = when {
                allTokenIds.contains(useFreemintNft) -> "USE Freemint"
                allTokenIds.contains(useArbmintNft) -> "USE Arbmint"
                allTokenIds.contains(dexyFreemintNft) -> "DexyGold Freemint"
                allTokenIds.contains(dexyArbmintNft) -> "DexyGold Arbmint"
                else -> protocolLabel
            }
        } else if (protocolLabel == "LP Swap") {
            // USE and DexyGold share the same contract address — identify by LP NFT in the tx
            val allTokenIds = mutableSetOf<String>()
            for (box in rawInputs + rawOutputs) {
                for (a in (box["assets"] as? List<Map<String, Any>> ?: emptyList())) {
                    (a["tokenId"] as? String)?.let { allTokenIds.add(it) }
                }
            }
            val useLpNft = NetworkConfig.USE_CONFIG["lp_nft"] as? String ?: ""
            val dexyLpNft = NetworkConfig.DEXYGOLD_CONFIG["lp_nft"] as? String ?: ""
            effectiveLabel = when {
                useLpNft.isNotEmpty() && allTokenIds.contains(useLpNft) -> "USE LP Swap"
                dexyLpNft.isNotEmpty() && allTokenIds.contains(dexyLpNft) -> "DexyGold LP Swap"
                else -> "LP Swap"
            }
        }

        // Find the trader's address
        // Direct swaps: trader's address is in BOTH inputs and outputs (they get change back)
        // Proxy swaps: proxy contract is in inputs only (consumed), real trader is in outputs only
        val knownAddresses = ecosystemProtocols.keys +
            NetworkConfig.KNOWN_PROTOCOLS.keys
        var traderAddr = ""
        var traderFoundInInputs = false
        // Try inputs first — find a non-protocol input address
        for (inp in rawInputs) {
            val addr = inp["address"] as? String ?: ""
            if (addr.isNotEmpty() && !knownAddresses.any { addr.startsWith(it.take(30)) }) {
                // Verify this address also appears in outputs (real trader gets change back)
                // If it doesn't appear in outputs, it's a proxy contract (consumed), not the trader
                val inOutputs = rawOutputs.any { outp ->
                    (outp["address"] as? String ?: "").startsWith(addr.take(30))
                }
                if (inOutputs) {
                    traderAddr = addr
                    traderFoundInInputs = true
                }
                break
            }
        }
        // If no real trader found in inputs (proxy contract scenario), check outputs
        // The real trader is the output that receives tokens (not the pool, bot fee, or miner fee)
        if (traderAddr.isEmpty()) {
            for (outp in rawOutputs) {
                val addr = outp["address"] as? String ?: ""
                if (addr.isNotEmpty() && !knownAddresses.any { addr.startsWith(it.take(30)) }) {
                    val assets = outp["assets"] as? List<Map<String, Any>> ?: emptyList()
                    val value = (outp["value"] as? Number)?.toLong() ?: 0L
                    // Skip tiny bot-fee outputs (~0.004 ERG with no tokens)
                    if (assets.isNotEmpty() || value > 10_000_000L) {
                        traderAddr = addr
                        break
                    }
                }
            }
        }
        // Final fallback: any non-protocol output
        if (traderAddr.isEmpty()) {
            for (outp in rawOutputs) {
                val addr = outp["address"] as? String ?: ""
                if (addr.isNotEmpty() && !knownAddresses.any { addr.startsWith(it.take(30)) }) {
                    traderAddr = addr
                    break
                }
            }
        }
        if (traderAddr.isEmpty()) traderAddr = "Unknown"

        // Build sent/received descriptions
        val sentParts = mutableListOf<String>()
        val recvParts = mutableListOf<String>()

        // Proxy swap detection for DEX: trader found only in outputs = batch bot executed via proxy
        val isDexSwap = effectiveLabel.contains("DEX") || effectiveLabel.contains("LP Swap")
        val isProxySwap = isDexSwap && !traderFoundInInputs

        if (isProxySwap) {
            // Compute from pool box reserve deltas
            // Structure: 2 inputs (pool + proxy), 4 outputs (pool, user, bot fee, miner fee)
            val poolInput = rawInputs.find { box ->
                val addr = box["address"] as? String ?: ""
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                addr.startsWith(protocolAddress.take(30)) && assets.size >= 3
            }
            val poolOutput = rawOutputs.find { box ->
                val addr = box["address"] as? String ?: ""
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                addr.startsWith(protocolAddress.take(30)) && assets.size >= 3
            }
            if (poolInput != null && poolOutput != null) {
                val poolErgIn = (poolInput["value"] as? Number)?.toLong() ?: 0L
                val poolErgOut = (poolOutput["value"] as? Number)?.toLong() ?: 0L
                val poolErgDelta = poolErgOut - poolErgIn

                val pInAssets = poolInput["assets"] as? List<Map<String, Any>> ?: emptyList()
                val pOutAssets = poolOutput["assets"] as? List<Map<String, Any>> ?: emptyList()

                // assets[1] = LP tokens in pool
                val lpTokenIn = (pInAssets.getOrNull(1)?.get("amount") as? Number)?.toLong() ?: 0L
                val lpTokenOut = (pOutAssets.getOrNull(1)?.get("amount") as? Number)?.toLong() ?: 0L
                val lpDelta = lpTokenOut - lpTokenIn

                // assets[2] = trading token
                val tokenId = pInAssets.getOrNull(2)?.get("tokenId") as? String ?: ""
                val poolTokenIn = (pInAssets.getOrNull(2)?.get("amount") as? Number)?.toLong() ?: 0L
                val poolTokenOut = (pOutAssets.getOrNull(2)?.get("amount") as? Number)?.toLong() ?: 0L
                val poolTokenDelta = poolTokenOut - poolTokenIn

                if (lpDelta > 0) {
                    // ── LP Withdrawal: LP tokens returned to pool, user gets ERG + tokens ──
                    effectiveLabel = "DEX LP Withdraw"
                    // LP tokens went into pool = user sent LP
                    val lpTokenId = pInAssets.getOrNull(1)?.get("tokenId") as? String ?: ""
                    if (lpTokenId.isNotEmpty()) {
                        val lpName = getTokenName(lpTokenId)
                        val lpFormatted = formatBalance(lpTokenId, lpDelta)
                        sentParts.add("$lpFormatted $lpName")
                    }
                    // Pool lost ERG = user received ERG
                    if (poolErgDelta < -1_000_000L) {
                        recvParts.add("${String.format("%.4f", Math.abs(poolErgDelta).toDouble() / 1e9)} ERG")
                    }
                    // Pool lost tokens = user received tokens
                    if (tokenId.isNotEmpty() && poolTokenDelta < 0) {
                        val name = getTokenName(tokenId)
                        if (name != tokenId) {
                            recvParts.add("${formatBalance(tokenId, Math.abs(poolTokenDelta))} $name")
                        }
                    }
                } else if (lpDelta < 0) {
                    // ── LP Deposit: user sends ERG + tokens, receives LP tokens ──
                    effectiveLabel = "DEX LP Deposit"
                    // Pool gained ERG = user sent ERG
                    if (poolErgDelta > 2_000_000L) {
                        sentParts.add("${String.format("%.4f", poolErgDelta.toDouble() / 1e9)} ERG")
                    }
                    // Pool gained tokens = user sent tokens
                    if (tokenId.isNotEmpty() && poolTokenDelta > 0) {
                        val name = getTokenName(tokenId)
                        if (name != tokenId) {
                            sentParts.add("${formatBalance(tokenId, poolTokenDelta)} $name")
                        }
                    }
                    // LP tokens left pool = user received LP
                    val lpTokenId = pInAssets.getOrNull(1)?.get("tokenId") as? String ?: ""
                    if (lpTokenId.isNotEmpty()) {
                        val lpName = getTokenName(lpTokenId)
                        val lpFormatted = formatBalance(lpTokenId, Math.abs(lpDelta))
                        recvParts.add("$lpFormatted $lpName")
                    }
                } else {
                    // ── Normal proxy swap (LP unchanged) ──
                    // User sent what pool gained, received what pool lost
                    if (poolErgDelta > 2_000_000L) {
                        sentParts.add("${String.format("%.4f", poolErgDelta.toDouble() / 1e9)} ERG")
                    } else if (poolErgDelta < -1_000_000L) {
                        recvParts.add("${String.format("%.4f", Math.abs(poolErgDelta).toDouble() / 1e9)} ERG")
                    }
                    if (tokenId.isNotEmpty() && poolTokenDelta != 0L) {
                        val name = getTokenName(tokenId)
                        if (!name.startsWith("Pool") && name != tokenId) {
                            val formatted = formatBalance(tokenId, Math.abs(poolTokenDelta))
                            if (poolTokenDelta > 0) sentParts.add("$formatted $name")
                            else recvParts.add("$formatted $name")
                        }
                    }
                }
            }
        } else {
            // Direct swap: compute from trader's address deltas (original logic)
            val traderInputErg = rawInputs.filter { (it["address"] as? String ?: "").startsWith(traderAddr.take(30)) }
                .sumOf { (it["value"] as? Number)?.toLong() ?: 0L }
            val traderInputTokens = mutableMapOf<String, Long>()
            for (inp in rawInputs) {
                if (!((inp["address"] as? String ?: "").startsWith(traderAddr.take(30)))) continue
                for (a in (inp["assets"] as? List<Map<String, Any>> ?: emptyList())) {
                    val tid = a["tokenId"] as? String ?: continue
                    val amt = (a["amount"] as? Number)?.toLong() ?: 0L
                    traderInputTokens[tid] = (traderInputTokens[tid] ?: 0L) + amt
                }
            }

            val traderOutputErg = rawOutputs.filter { (it["address"] as? String ?: "").startsWith(traderAddr.take(30)) }
                .sumOf { (it["value"] as? Number)?.toLong() ?: 0L }
            val traderOutputTokens = mutableMapOf<String, Long>()
            for (outp in rawOutputs) {
                if (!((outp["address"] as? String ?: "").startsWith(traderAddr.take(30)))) continue
                for (a in (outp["assets"] as? List<Map<String, Any>> ?: emptyList())) {
                    val tid = a["tokenId"] as? String ?: continue
                    val amt = (a["amount"] as? Number)?.toLong() ?: 0L
                    traderOutputTokens[tid] = (traderOutputTokens[tid] ?: 0L) + amt
                }
            }

            val ergDiff = traderOutputErg - traderInputErg
            if (ergDiff < -2_000_000L) {
                sentParts.add("${String.format("%.4f", Math.abs(ergDiff).toDouble() / 1e9)} ERG")
            } else if (ergDiff > 1_000_000L) {
                recvParts.add("${String.format("%.4f", ergDiff.toDouble() / 1e9)} ERG")
            }

            // For bank/mint transactions, only show stablecoin deltas (not change/NFT tokens)
            val isBankTx = effectiveLabel.contains("Bank") || effectiveLabel.contains("Freemint") || 
                            effectiveLabel.contains("Arbmint")
            val stablecoinTokenIds = if (isBankTx) setOf(
                com.piggytrade.piggytrade.stablecoin.use.UseConfig.USE_TOKEN_ID,
                com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldConfig.DEXY_TOKEN_ID,
                com.piggytrade.piggytrade.stablecoin.sigmausd.SigmaUsdConfig.SIGUSD_TOKEN_ID,
                com.piggytrade.piggytrade.stablecoin.sigmausd.SigmaUsdConfig.SIGRSV_TOKEN_ID
            ) else null

            val allTokenIds = traderInputTokens.keys + traderOutputTokens.keys
            for (tid in allTokenIds) {
                val diff = (traderOutputTokens[tid] ?: 0L) - (traderInputTokens[tid] ?: 0L)
                if (diff == 0L) continue
                if (stablecoinTokenIds != null && tid !in stablecoinTokenIds) continue
                val name = getTokenName(tid)
                if (name.startsWith("Pool") || name == tid) continue
                val formatted = formatBalance(tid, Math.abs(diff))
                if (diff < 0) sentParts.add("$formatted $name")
                else recvParts.add("$formatted $name")
            }
        }

        if (sentParts.isEmpty() && recvParts.isEmpty()) return null

        // Price impact for DEX swaps (not LP Withdraw/Deposit)
        var priceImpact: Double? = null
        val isSwap = (effectiveLabel.contains("DEX") || effectiveLabel.contains("LP Swap"))
            && !effectiveLabel.contains("Withdraw") && !effectiveLabel.contains("Deposit")
        if (isSwap) {
            // For LP Swap, the pool box is at the pool_address, not the lp_swap_address
            val poolAddr = when {
                effectiveLabel.contains("USE") -> NetworkConfig.USE_CONFIG["pool_address"] as? String ?: protocolAddress
                effectiveLabel.contains("DexyGold") -> NetworkConfig.DEXYGOLD_CONFIG["pool_address"] as? String ?: protocolAddress
                else -> protocolAddress
            }
            priceImpact = calculatePriceImpact(rawInputs, rawOutputs, poolAddr)
        }

        return EcosystemTx(
            txId = txId,
            protocol = effectiveLabel,
            timestamp = timestamp,
            traderAddress = traderAddr,
            sent = sentParts.joinToString(" + ").ifEmpty { "—" },
            received = recvParts.joinToString(" + ").ifEmpty { "—" },
            priceImpact = priceImpact,
            isConfirmed = isConfirmed
        )
    }

    /**
     * Calculate price impact by comparing pool reserves before and after the swap.
     * Pool box is identified as the box at the protocol address with 3+ assets (ERG pools).
     */
    @Suppress("UNCHECKED_CAST")
    private fun calculatePriceImpact(
        inputs: List<Map<String, Any>>,
        outputs: List<Map<String, Any>>,
        protocolAddress: String
    ): Double? {
        try {
            // Find pool box in inputs (before swap)
            val poolInput = inputs.find { box ->
                val addr = box["address"] as? String ?: ""
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                addr.startsWith(protocolAddress.take(30)) && assets.size >= 3
            } ?: return null

            // Find pool box in outputs (after swap)
            val poolOutput = outputs.find { box ->
                val addr = box["address"] as? String ?: ""
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                addr.startsWith(protocolAddress.take(30)) && assets.size >= 3
            } ?: return null

            val ergBefore = (poolInput["value"] as? Number)?.toLong() ?: return null
            val ergAfter = (poolOutput["value"] as? Number)?.toLong() ?: return null

            val assetsBefore = poolInput["assets"] as? List<Map<String, Any>> ?: return null
            val assetsAfter = poolOutput["assets"] as? List<Map<String, Any>> ?: return null
            if (assetsBefore.size < 3 || assetsAfter.size < 3) return null

            val tokenBefore = (assetsBefore[2]["amount"] as? Number)?.toLong() ?: return null
            val tokenAfter = (assetsAfter[2]["amount"] as? Number)?.toLong() ?: return null

            if (tokenBefore <= 0 || tokenAfter <= 0) return null

            // Price = ERG per token
            val priceBefore = ergBefore.toDouble() / tokenBefore.toDouble()
            val priceAfter = ergAfter.toDouble() / tokenAfter.toDouble()

            if (priceBefore <= 0) return null
            return ((priceAfter - priceBefore) / priceBefore) * 100.0
        } catch (_: Exception) {
            return null
        }
    }
}
