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
import com.piggytrade.piggytrade.protocol.ProtocolConfig
import com.piggytrade.piggytrade.data.PreferenceManager
import com.piggytrade.piggytrade.data.TokenRepository
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.network.NodePool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Represents the connectivity state with the active Ergo node. */
sealed class NodeStatus {
    data class Trying(val url: String) : NodeStatus()
    data class Connected(val url: String) : NodeStatus()
    data class Failed(val url: String) : NodeStatus()
}

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
    val strictSubmitNode: Boolean = false,
    val walletFunctionalityEnabled: Boolean = false,
    val activeTab: String = "dex", // "dex", "wallet", "bank", "portfolio", "ecosystem"


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
    val cachedHeadersJson: String = "",

    // ─── SEND STATE ──────────────────────────────────────────────────────
    val sendRecipients: List<SendRecipientState> = listOf(SendRecipientState()),
    val sendMinerFee: Double = 0.0011,
    val isBuildingSendTx: Boolean = false,
    val sendError: String? = null,
    val sendReviewParams: SendReviewParams? = null,
    val sendPreparedTxData: Map<String, Any>? = null,
    val sendCachedHeadersJson: String = "",
    val ergoPayIncomingUrl: String = "",

    // ─── ADDRESS EXPLORER STATE ──────────────────────────────────────────
    val explorerAddress: String = "",
    val explorerErgBalance: Double = 0.0,
    val explorerTokens: Map<String, Long> = emptyMap(),
    val explorerTrades: List<NetworkTransaction> = emptyList(),
    val explorerHistoryOffset: Int = 0,
    val isLoadingExplorer: Boolean = false,
    val isLoadingExplorerHistory: Boolean = false,
    val savedExplorerAddresses: Map<String, String> = emptyMap(), // address -> label

    // ─── NODE STATUS ─────────────────────────────────────────────────────
    val nodeStatus: NodeStatus = NodeStatus.Trying(""),
    val deadNodes: Set<String> = emptySet()
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
    val traderAddress: String = "", // Abbreviated trader address
    val priceImpact: Double? = null  // % price impact; null when not computable
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

data class SendRecipientState(
    val address: String = "",
    val ergAmount: String = "",
    val tokens: List<SendTokenState> = emptyList()
)

data class SendTokenState(
    val tokenId: String = "",
    val amount: String = ""
)

data class SendReviewParams(
    val recipients: List<SendRecipientState>,
    val minerFee: Double,
    val totalErgOut: Double,
    val totalTokensOut: Map<String, Long>,
    val changeErg: Double,
    val changeTokens: Map<String, Long>,
    val isErgopay: Boolean,
    val ergopayUrl: String = ""
)

data class TokenMintInfo(
    val name: String,
    val description: String,
    val emissionAmount: Long,
    val decimals: Int,
    val mintAddress: String,
    val mintTxId: String,
    val mintBlockHeight: Int?,
    val mintTimestamp: Long?
)

data class HoldersCacheEntry(
    val holders: List<Pair<String, Long>>,
    val lastSyncedMs: Long
)

class SwapViewModel(application: Application) : AndroidViewModel(application) {

    private val topHoldersCache = mutableMapOf<String, HoldersCacheEntry>()

    private val TAG = "SwapViewModel"
    private val session = (application as com.piggytrade.piggytrade.TruffleApplication).sessionManager
    private val preferenceManager = session.preferenceManager
    private val tokenRepository = session.tokenRepository
    private val nodeManager = session.nodeManager
    val oraclePriceStore = session.oraclePriceStore
    private var quoteJob: kotlinx.coroutines.Job? = null

    private val _uiState: MutableStateFlow<SwapState> by lazy {
        if (BuildConfig.DEBUG) Log.d(TAG, "Initializing MutableStateFlow...")
        // Node list is owned by NodeManager — read from there to avoid duplication
        val nodesList = nodeManager.nodes.value
        val initialNodeIndex = nodeManager.selectedNodeIndex.value
        
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
                allowHttpNodes = (preferenceManager.loadSettings()["allow_http_nodes"] as? Boolean) ?: false,
                strictSubmitNode = (preferenceManager.loadSettings()["strict_submit_node"] as? Boolean) ?: false,
                walletFunctionalityEnabled = (preferenceManager.loadSettings()["wallet_functionality"] as? Boolean) ?: false,
                savedExplorerAddresses = preferenceManager.loadExplorerAddresses()
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
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                tokenRepository.tradeMapper
            }

            // Probe all pool nodes and initialize primary node client in parallel.
            // probeAll() marks dead nodes so that subsequent readNode() calls skip them.
            // initializeNodeClient() is independent — it only touches the primary node.
            kotlinx.coroutines.coroutineScope {
                val probeJob = async(kotlinx.coroutines.Dispatchers.IO) {
                    nodePool.probeAll(timeoutMs = 3000L)
                }
                val initJob = async {
                    initializeNodeClient()
                }
                probeJob.await()
                initJob.await()
                checkAndReplaceDeadPrimaryNode()
            }

            // 2. Fetch Wallet Balances (pool is now probed, primary node is ready)
            fetchWalletBalances()

            // First launch sync if needed
            if (!tokenRepository.hasTokenFiles()) {
                syncTokenList(isFirstLaunch = true)
            }
            
            // Mirror active read operations across all ViewModels to the Top Bar UI
            viewModelScope.launch {
                nodePool.activeNodeUrl.collect { url ->
                    if (url != null) {
                        _uiState.value = _uiState.value.copy(nodeStatus = NodeStatus.Trying(url))
                    } else {
                        val priUrl = _uiState.value.nodeUrl
                        if (priUrl.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(nodeStatus = NodeStatus.Connected(priUrl))
                        }
                    }
                }
            }
            
            // Mirror dead nodes for Settings UI dropdown indicators
            viewModelScope.launch {
                nodePool.deadNodeUrls.collect { dead ->
                    _uiState.value = _uiState.value.copy(deadNodes = dead)
                }
            }
            
            // Mirror node list from NodeManager to prevent state drift
            viewModelScope.launch {
                nodeManager.nodes.collect { nodeList ->
                    _uiState.value = _uiState.value.copy(nodes = nodeList)
                }
            }
        }
    }
    
    fun reprobeNodes() {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                nodePool.probeAll()
                checkAndReplaceDeadPrimaryNode()
            }
        }
    }

    private suspend fun checkAndReplaceDeadPrimaryNode() {
        val deadUrls = nodePool.deadNodeUrls.value
        val primaryUrl = nodeManager.nodeUrl.value
        if (primaryUrl.isNotEmpty() && deadUrls.any { it.contains(primaryUrl) || primaryUrl.contains(it) }) {
            val allNodes = nodeManager.nodes.value
            val liveNodeIndices = allNodes.indices.filter { idx ->
                val nodeUrl = allNodes[idx].substringAfter(": ")
                !deadUrls.any { it.contains(nodeUrl) || nodeUrl.contains(it) }
            }
            if (liveNodeIndices.isNotEmpty()) {
                val randomIdx = liveNodeIndices.random()
                nodeManager.setSelectedNodeIndex(randomIdx)
                if (BuildConfig.DEBUG) android.util.Log.i(TAG, "Primary node $primaryUrl is dead. Auto-switching to live node index $randomIdx")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    initializeNodeClient()
                }
            }
        }
    }

    private val _resolvedAddresses = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedAddresses: StateFlow<Map<String, String>> = _resolvedAddresses.asStateFlow()

    fun resolveErgoTree(ergoTree: String) {
        if (ergoTree.isEmpty() || _resolvedAddresses.value.containsKey(ergoTree)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = readNode { it.api.ergoTreeToAddress(ergoTree) }
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

    private var nodeClient: NodeClient?
        get() = nodeManager.nodeClient.value
        private set(value) { nodeManager.setNodeClientDirect(value) }
    /** Pool of public nodes for distributing read-only queries */
    private val nodePool = session.nodePool

    /** Cached raw pool box data from liquidity fetch — maps pid -> box map */
    private val poolBoxDataCache = mutableMapOf<String, Map<String, Any>>()



    /** Debounce: last successful wallet balance fetch timestamp */
    private var lastWalletFetchMs = 0L
    /** Cached address → ergoTree mapping (never changes) */
    private val ergoTreeCache = mutableMapOf<String, String>()

    private var trader: Trader? = null

    // ─── INIT & NODE MANAGEMENT ──────────────────────────────────────────────

    private suspend fun initializeNodeClient() {
        val allowHttp = _uiState.value.allowHttpNodes
        val attemptUrl = run {
            val nodes = _uiState.value.nodes
            val idx = _uiState.value.selectedNodeIndex
            val raw = nodes.getOrNull(idx) ?: ""
            when {
                raw.contains(": ") -> raw.substringAfter(": ")
                raw.contains("(") -> raw.substringAfter("(").substringBefore(")")
                else -> raw
            }
        }
        if (attemptUrl.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(nodeStatus = NodeStatus.Trying(attemptUrl))
        }
        try {
            nodeManager.initializeNodeClient(allowHttp,
                onClientReady = { client, finalUrl ->
                    // Runs on Main — update state and rebuild pool mappings
                    trader = Trader(client, null, tokenRepository.tokens)
                    _uiState.value = _uiState.value.copy(
                        nodeUrl = finalUrl,
                        nodes = nodeManager.nodes.value,
                        selectedNodeIndex = nodeManager.selectedNodeIndex.value,
                        nodeStatus = NodeStatus.Connected(finalUrl)
                    )
                    loadPoolMappings(fetchLiquidity = false)
                },
                onFailed = { failedUrl ->
                    _uiState.value = _uiState.value.copy(
                        nodeStatus = NodeStatus.Failed(failedUrl)
                    )
                }
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                nodeStatus = NodeStatus.Failed(attemptUrl.ifEmpty { "unknown" })
            )
        }
    }

    private fun updateNodeClient() {
        viewModelScope.launch {
            initializeNodeClient()
        }
    }

    fun addNode(name: String, url: String) {
        val currentNodesMap = preferenceManager.loadNodes().toMutableMap()
        val key = name.ifEmpty { "Custom${currentNodesMap.size + 1}" }
        currentNodesMap[key] = mapOf("url" to url)
        preferenceManager.saveNodes(currentNodesMap)

        nodeManager.reloadNodes()
        val newNodes = nodeManager.nodes.value
        val newIndex = newNodes.indexOfFirst { it.startsWith("$key:") }.coerceAtLeast(0)

        // Update UI state with new list & selection, then trigger client rebuild
        _uiState.value = _uiState.value.copy(
            nodes = newNodes,
            selectedNodeIndex = newIndex
        )
        nodeManager.setSelectedNodeIndex(newIndex)
        updateNodeClient()
    }

    /**
     * Execute a read-only node call through the rotating NodePool with automatic retry.
     * Updates [nodeStatus] as it cycles through nodes:
     *   Trying(url) → Connected(url) on success, or Failed(url) if all retries fail.
     *
     * Use this for ALL read ops (balances, quotes, history, etc.).
     * TX building and submission use [nodeClient] directly for consistency.
     */
    private suspend fun <T> readNode(block: suspend (NodeClient) -> T): T {
        return nodePool.withRetryTracked(
            maxRetries = nodePool.size.coerceAtLeast(1),
            onTrying = { url ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(nodeStatus = NodeStatus.Trying(url))
                }
            }
        ) { client ->
            val result = block(client)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(nodeStatus = NodeStatus.Connected(client.nodeUrl))
            }
            result
        }
    }

    /**
     * Converts raw network/SSL exception messages into user-friendly strings.
     * Prevents technical jargon like "Unable to parse TLS packet header" from
     * appearing directly in the UI.
     */
    private fun sanitizeNodeError(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("tls") || msg.contains("ssl") || msg.contains("handshake") ||
            msg.contains("certificate") || msg.contains("socket") || msg.contains("tsl") ->
                "Node unreachable — retrying"
            msg.contains("timeout") || msg.contains("timed out") ->
                "Node timed out — retrying"
            msg.contains("connect") || msg.contains("connection refused") ||
            msg.contains("failed to connect") ->
                "Cannot connect to node — retrying"
            msg.contains("all nodes failed") ->
                "All nodes unreachable — check connection"
            msg.contains("unknown host") || msg.contains("unable to resolve") ->
                "Node address invalid"
            else -> e.message ?: "Network error"
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
        fetchWalletBalances(force = true)
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

    fun setStrictSubmitNode(strict: Boolean) {
        _uiState.value = _uiState.value.copy(strictSubmitNode = strict)
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["strict_submit_node"] = strict
        preferenceManager.saveSettings(settings)
    }

    fun setWalletFunctionalityEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(walletFunctionalityEnabled = enabled)
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["wallet_functionality"] = enabled
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
        nodeManager.setSelectedNodeIndex(index)
        _uiState.value = _uiState.value.copy(
            selectedNodeIndex = index,
            nodes = nodeManager.nodes.value
        )
        updateNodeClient()
        fetchQuote()
    }

    fun deleteNode() {
        if (_uiState.value.nodes.isEmpty()) return
        // Delegate to NodeManager — it handles preference persistence and list rebuild
        val newIndex = nodeManager.deleteSelectedNode()
        _uiState.value = _uiState.value.copy(
            nodes = nodeManager.nodes.value,
            selectedNodeIndex = newIndex
        )
        updateNodeClient()
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
                val checkMempool = _uiState.value.includeUnconfirmed

                val tokens: Map<String, Long>
                val nanoerg: Long
                val addressBoxMap: Map<String, List<Map<String, Any>>>

                if (addressesToFetch.size == 1) {
                    val addr = addressesToFetch.first()
                    val (t, n, boxes) = readNode { it.getMyAssets(addr, checkMempool) }
                    tokens = t
                    nanoerg = n
                    addressBoxMap = mapOf(addr to boxes)
                } else {
                    val (t, n, boxMap) = readNode { it.getMyAssetsMulti(addressesToFetch, checkMempool) }
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
                loadPoolMappings(fetchLiquidity = false)

                // Fetch token metadata for unknown tokens (reuse whichever pool node answered)
                tokens.keys.forEach { tid ->
                    if (tid != "ERG") {
                        val currentName = tokenRepository.getTokenName(tid)
                        val isGeneric = currentName.startsWith(tid.take(5)) || currentName == tid
                        val hasNoDec = tokenRepository.getTokenDecimals(tid) == 0 && tid != "ERG"
                        if (isGeneric || hasNoDec) {
                            try {
                                readNode { it.api.getTokenInfo(tid) }?.let { info ->
                                    tokenRepository.saveTokenInfo(tid, info)
                                }
                            } catch (e: Exception) {
                                Log.e("SwapVM", "Error fetching info for $tid: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingWallet = false,
                    nodeStatus = NodeStatus.Failed(
                        (_uiState.value.nodeStatus as? NodeStatus.Trying)?.url
                            ?: _uiState.value.nodeUrl.ifEmpty { "unknown" }
                    )
                )
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

                for (i in 0 until MAX_SCAN) {
                    val addr = try {
                        org.ergoplatform.wallet.jni.WalletLib.mnemonicToAddress(mnemonic, "", i, true)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("SaveWallet", "Derivation failed at index $i: ${e.message}")
                        break
                    }
                    derived.add(addr)

                    // Check if this address has any UTXOs on-chain (use pool node for reads)
                    if (i > 0) {
                        try {
                            val (_, nanoerg, boxes) = readNode { it.getMyAssets(addr, false) }
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

                )
                preferenceManager.selectedWallet = name
                
                // Immediately fetch data for the new wallet
                fetchWalletBalances(force = true)
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
                    walletTokens = emptyMap(),
                    walletErgBalance = 0.0,
                    networkTrades = emptyList()
                )
                preferenceManager.selectedWallet = internalKey
                fetchWalletBalances(force = true)
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
            try {
                val quote = readNode { client ->
                    protocol.getQuote(client, amount, _uiState.value.selectedAddress, _uiState.value.includeUnconfirmed)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankQuote = quote, bankError = null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankError = sanitizeNodeError(e))
                }
            }
        }
    }

    private var bankRedeemQuoteJob: kotlinx.coroutines.Job? = null

    private fun fetchBankRedeemQuote(amount: Double) {
        bankRedeemQuoteJob?.cancel()
        bankRedeemQuoteJob = viewModelScope.launch(Dispatchers.IO) {
            val protocol = StablecoinRegistry.getById(_uiState.value.activeProtocolId) ?: return@launch
            try {
                val quote = readNode { client ->
                    protocol.getRedeemQuote(client, amount, _uiState.value.selectedAddress, _uiState.value.includeUnconfirmed)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankRedeemQuote = quote, bankError = null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankError = sanitizeNodeError(e))
                }
            }
        }
    }

    private fun refreshBankEligibility() {
        viewModelScope.launch(Dispatchers.IO) {
            val protocol = StablecoinRegistry.getById(_uiState.value.activeProtocolId) ?: return@launch
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
                val eligibility = readNode { client ->
                    if (BuildConfig.DEBUG) android.util.Log.d("BankVM", "checkEligibility using node: ${client.nodeUrl}, address: $address, protocol: ${protocol.id}")
                    protocol.checkEligibility(client, address, _uiState.value.includeUnconfirmed)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(bankEligibility = eligibility, isBankLoading = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isBankLoading = false, bankError = sanitizeNodeError(e))
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

    /** Return sorted list of signable wallet names (excludes ErgoPay-only wallets). */
    fun getSignableWalletNames(): List<String> {
        val wallets = preferenceManager.loadWallets()
        return wallets.keys.filter { name ->
            val data = wallets[name] as? Map<*, *> ?: return@filter false
            // A wallet is signable if it has either encrypted mnemonic or device-encrypted mnemonic
            data.containsKey("token") || data.containsKey("mnemonic_encrypted_device")
        }.sorted()
    }

    fun getWalletAddress(name: String): String {
        return getWalletData(name)?.get("address") as? String ?: ""
    }

    /** Fetch a box from the blockchain by its box ID. Returns the box data map or null. */
    suspend fun fetchBoxById(boxId: String): Map<String, Any>? {
        return try {
            nodeClient?.api?.getBoxById(boxId)
        } catch (e: Exception) {
            android.util.Log.w("SwapViewModel", "fetchBoxById($boxId) failed: ${e.message}")
            null
        }
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
                walletTokens = emptyMap(),
                networkTrades = emptyList()
            )
            
            if (newSelectedWallet != "Select Wallet" && newAddress.isNotEmpty()) {
                fetchWalletBalances()
            }
        }
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
                if (route != null) {
                    val (quote, impact) = readNode { poolClient ->
                        val tempTrader = com.piggytrade.piggytrade.blockchain.Trader(poolClient, null, tokenRepository.tokens)
                        tempTrader.getQuote(
                            poolKey = route.tokenKey,
                            amount = amount,
                            orderType = route.orderType,
                            poolType = route.poolType,
                            checkMempool = current.includeUnconfirmed
                        )
                    }
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
                        val sFee = signer.calculateAppFee(ergValueForFee.toLong(), if (route.poolType == "token") 1 else 0).toDouble() / 1_000_000_000.0

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
                    toQuote = sanitizeNodeError(e)
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

    /**
     * Fetch full minting info for a token by chaining three API calls:
     * 1. /blockchain/token/byId/{tokenId}   → name, description, emissionAmount, decimals
     * 2. /blockchain/box/byId/{tokenId}      → minting address, transactionId
     * 3. /blockchain/transaction/byId/{txId} → block height, timestamp
     */
    suspend fun fetchTokenMintInfo(tokenId: String): TokenMintInfo {
        // 1. Token metadata
        val tokenInfo = readNode { it.api.getTokenInfo(tokenId) }
            ?: throw Exception("Token not found")
        val name = tokenInfo["name"] as? String ?: "Unknown"
        val description = tokenInfo["description"] as? String ?: ""
        val emissionAmount = (tokenInfo["emissionAmount"] as? Number)?.toLong() ?: 0L
        val decimals = (tokenInfo["decimals"] as? Number)?.toInt() ?: 0

        // 2. Minting box (box ID == token ID for the first issuance box)
        val boxInfo = readNode { it.api.getBoxById(tokenId) }
        val mintAddress = boxInfo["address"] as? String ?: "Unknown"
        val mintTxId = boxInfo["transactionId"] as? String ?: ""

        // 3. Minting transaction → timestamp & block height
        var mintBlockHeight: Int? = null
        var mintTimestamp: Long? = null
        if (mintTxId.isNotEmpty()) {
            val txInfo = readNode { it.api.getTransactionById(mintTxId) }
            mintBlockHeight = (txInfo?.get("inclusionHeight") as? Number)?.toInt()
            mintTimestamp = (txInfo?.get("timestamp") as? Number)?.toLong()
        }

        return TokenMintInfo(
            name = name,
            description = description,
            emissionAmount = emissionAmount,
            decimals = decimals,
            mintAddress = mintAddress,
            mintTxId = mintTxId,
            mintBlockHeight = mintBlockHeight,
            mintTimestamp = mintTimestamp
        )
    }

    /**
     * Fetch all unspent boxes holding [tokenId], aggregate token amounts by address,
     * and return the top 100 holders sorted by balance descending.
     *
     * @param onProgress called with (boxesFetchedSoFar, isStillFetching) so the UI
     *                   can display a live progress indicator.
     * @return list of (address, totalAmount) pairs, top 100 by balance.
     */
    private fun loadTopHoldersCache(tokenId: String): HoldersCacheEntry? {
        try {
            val file = java.io.File(getApplication<Application>().filesDir, "holders_$tokenId.json")
            if (!file.exists()) return null
            val json = org.json.JSONObject(file.readText())
            val lastSyncedMs = json.getLong("lastSyncedMs")
            val arr = json.getJSONArray("holders")
            val list = mutableListOf<Pair<String, Long>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(obj.getString("a") to obj.getLong("v"))
            }
            return HoldersCacheEntry(list, lastSyncedMs)
        } catch (e: Exception) { return null }
    }

    private fun saveTopHoldersCache(tokenId: String, entry: HoldersCacheEntry) {
        try {
            val arr = org.json.JSONArray()
            for ((a, v) in entry.holders) {
                arr.put(org.json.JSONObject().put("a", a).put("v", v))
            }
            val json = org.json.JSONObject().put("lastSyncedMs", entry.lastSyncedMs).put("holders", arr)
            java.io.File(getApplication<Application>().filesDir, "holders_$tokenId.json").writeText(json.toString())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to save top holders cache: ${e.message}")
        }
    }

    suspend fun fetchTopHolders(
        tokenId: String,
        decimals: Int,
        forceRefresh: Boolean = false,
        onProgress: suspend (fetched: Int) -> Unit = {}
    ): HoldersCacheEntry {
        if (!forceRefresh) {
            topHoldersCache[tokenId]?.let { return it }
            val diskCache = loadTopHoldersCache(tokenId)
            if (diskCache != null) {
                topHoldersCache[tokenId] = diskCache
                return diskCache
            }
        }

        val limit = 500
        val offsetCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val isFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        val allBoxes = java.util.concurrent.ConcurrentLinkedQueue<Map<String, Any>>()
        val totalFetchedCounter = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            val workers = List(4) {
                async(Dispatchers.IO) {
                    while (!isFinished.get()) {
                        val currentOffset = offsetCounter.getAndAdd(limit)
                        try {
                            val boxes = readNode { it.api.getUnspentBoxesByTokenId(
                                tokenId = tokenId,
                                offset = currentOffset,
                                limit = limit,
                                sortDirection = "desc",
                                includeUnconfirmed = false
                            ) }

                            if (boxes.isNotEmpty()) {
                                allBoxes.addAll(boxes)
                                val currentTotal = totalFetchedCounter.addAndGet(boxes.size)
                                onProgress(currentTotal)
                            }

                            if (boxes.size < limit || boxes.isEmpty()) {
                                isFinished.set(true)
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "Worker failed at offset $currentOffset: ${e.message}")
                            isFinished.set(true)
                        }
                    }
                }
            }
            workers.awaitAll()
        }

        val addressBalances = mutableMapOf<String, Long>()
        val uniqueBoxes = allBoxes.distinctBy { it["boxId"] as? String ?: it.hashCode().toString() }

        for (box in uniqueBoxes) {
            val address = box["address"] as? String ?: continue
            @Suppress("UNCHECKED_CAST")
            val assets = box["assets"] as? List<Map<String, Any>> ?: continue
            for (asset in assets) {
                if ((asset["tokenId"] as? String) == tokenId) {
                    val amount = (asset["amount"] as? Number)?.toLong() ?: 0L
                    addressBalances[address] = (addressBalances[address] ?: 0L) + amount
                }
            }
        }

        val sortedList = addressBalances.entries
            .sortedByDescending { it.value }
            .take(100)
            .map { it.key to it.value }

        val entry = HoldersCacheEntry(sortedList, System.currentTimeMillis())
        topHoldersCache[tokenId] = entry
        saveTopHoldersCache(tokenId, entry)
        return entry
    }

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
        return ProtocolConfig.appFeeAddress()
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

                val serviceFee = (txDict["appFee"] as? Number)?.toDouble()?.div(1_000_000_000.0) ?: 0.0

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
                // Decrypt mnemonic — biometric gate enforced
            val useBiometrics = walletData["use_biometrics"] as? Boolean ?: false
            val mnemonic = if (useBiometrics) {
                if (!_biometricVerified) {
                    throw Exception("Biometric authentication required")
                }
                _biometricVerified = false // One-time use — must re-verify for next TX
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
                        if (current.strictSubmitNode) {
                            client.api.checkTransaction(signedTxMap)
                        } else {
                            nodePool.withRetryTracked { it.api.checkTransaction(signedTxMap) }
                        }
                        signedTxMap["id"] as? String ?: "Simulation"
                    } else {
                        if (current.strictSubmitNode) {
                            client.api.submitTransaction(signedTxMap)
                        } else {
                            nodePool.withRetryTracked { it.api.submitTransaction(signedTxMap) }
                        }
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
                        sigmaspaceUrl = "https://sigmaspace.io/en/transaction/$txId",
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

    // ─── SEND OPERATIONS ─────────────────────────────────────────────────

    fun addSendRecipient() {
        val current = _uiState.value
        _uiState.value = current.copy(
            sendRecipients = current.sendRecipients + SendRecipientState()
        )
    }

    fun removeSendRecipient(index: Int) {
        val current = _uiState.value
        if (current.sendRecipients.size <= 1) return
        _uiState.value = current.copy(
            sendRecipients = current.sendRecipients.toMutableList().apply { removeAt(index) }
        )
    }

    fun setSendRecipientAddress(index: Int, address: String) {
        val current = _uiState.value
        val recipients = current.sendRecipients.toMutableList()
        if (index !in recipients.indices) return
        recipients[index] = recipients[index].copy(address = address)
        _uiState.value = current.copy(sendRecipients = recipients)
    }

    fun setSendRecipientAmount(index: Int, amount: String) {
        val current = _uiState.value
        val recipients = current.sendRecipients.toMutableList()
        if (index !in recipients.indices) return
        recipients[index] = recipients[index].copy(ergAmount = amount)
        _uiState.value = current.copy(sendRecipients = recipients)
    }

    fun setSendMaxErg(index: Int) {
        val current = _uiState.value
        val totalAvailableErg = current.walletErgBalance
        // Subtract fee + other recipients' amounts + dust for change
        var otherErg = current.sendMinerFee
        for ((i, r) in current.sendRecipients.withIndex()) {
            if (i != index) {
                otherErg += r.ergAmount.toDoubleOrNull() ?: 0.0
            }
        }
        val max = (totalAvailableErg - otherErg - 0.001).coerceAtLeast(0.0) // Leave 0.001 for dust
        val recipients = current.sendRecipients.toMutableList()
        if (index !in recipients.indices) return
        recipients[index] = recipients[index].copy(ergAmount = formatErg(max))
        _uiState.value = current.copy(sendRecipients = recipients)
    }

    fun addSendToken(recipientIndex: Int, tokenId: String) {
        val current = _uiState.value
        val recipients = current.sendRecipients.toMutableList()
        if (recipientIndex !in recipients.indices) return
        val r = recipients[recipientIndex]
        // Don't add duplicate token
        if (r.tokens.any { it.tokenId == tokenId }) return
        recipients[recipientIndex] = r.copy(tokens = r.tokens + SendTokenState(tokenId = tokenId))
        _uiState.value = current.copy(sendRecipients = recipients)
    }

    fun removeSendToken(recipientIndex: Int, tokenIndex: Int) {
        val current = _uiState.value
        val recipients = current.sendRecipients.toMutableList()
        if (recipientIndex !in recipients.indices) return
        val r = recipients[recipientIndex]
        if (tokenIndex !in r.tokens.indices) return
        recipients[recipientIndex] = r.copy(
            tokens = r.tokens.toMutableList().apply { removeAt(tokenIndex) }
        )
        _uiState.value = current.copy(sendRecipients = recipients)
    }

    fun setSendTokenAmount(recipientIndex: Int, tokenIndex: Int, amount: String) {
        val current = _uiState.value
        val recipients = current.sendRecipients.toMutableList()
        if (recipientIndex !in recipients.indices) return
        val r = recipients[recipientIndex]
        if (tokenIndex !in r.tokens.indices) return
        val tokens = r.tokens.toMutableList()
        tokens[tokenIndex] = tokens[tokenIndex].copy(amount = amount)
        recipients[recipientIndex] = r.copy(tokens = tokens)
        _uiState.value = current.copy(sendRecipients = recipients)
    }

    fun setSendMaxToken(recipientIndex: Int, tokenIndex: Int) {
        val current = _uiState.value
        val recipients = current.sendRecipients
        if (recipientIndex !in recipients.indices) return
        val r = recipients[recipientIndex]
        if (tokenIndex !in r.tokens.indices) return
        val tokenId = r.tokens[tokenIndex].tokenId
        val totalAvailable = current.walletTokens[tokenId] ?: 0L
        // Subtract amounts allocated to other recipients for the same token
        var otherAllocated = 0L
        for ((i, rec) in recipients.withIndex()) {
            for ((j, tok) in rec.tokens.withIndex()) {
                if (tok.tokenId == tokenId && !(i == recipientIndex && j == tokenIndex)) {
                    val dec = tokenRepository.getTokenDecimals(tokenId)
                    val amt = tok.amount.toDoubleOrNull() ?: 0.0
                    otherAllocated += (amt * Math.pow(10.0, dec.toDouble())).toLong()
                }
            }
        }
        val maxRaw = (totalAvailable - otherAllocated).coerceAtLeast(0L)
        val display = formatBalance(tokenId, maxRaw)
        val newRecipients = recipients.toMutableList()
        val tokens = r.tokens.toMutableList()
        tokens[tokenIndex] = tokens[tokenIndex].copy(amount = display)
        newRecipients[recipientIndex] = r.copy(tokens = tokens)
        _uiState.value = current.copy(sendRecipients = newRecipients)
    }

    fun setSendMinerFee(fee: Double) {
        val roundedFee = java.math.BigDecimal(fee).setScale(9, java.math.RoundingMode.HALF_UP).toDouble()
        _uiState.value = _uiState.value.copy(sendMinerFee = roundedFee)
    }

    fun prepareSendTx(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val current = _uiState.value
        val addr = current.selectedAddress
        if (addr.isEmpty()) {
            onError("No wallet selected")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuildingSendTx = true, sendError = null)
            try {
                val client = nodeClient ?: throw Exception("Node client not initialized")
                val currentHeight = try { client.getHeight() } catch (e: Exception) { 0 }
                val feeNano = (current.sendMinerFee * 1_000_000_000).toLong()
                val changeAddr = current.changeAddress.ifEmpty { addr }

                // Convert UI state to SendTxBuilder recipients
                val recipients = current.sendRecipients.map { r ->
                    val ergAmount = r.ergAmount.toDoubleOrNull()
                        ?: throw Exception("Invalid ERG amount for ${r.address.take(8)}...")
                    val nanoErg = (ergAmount * 1_000_000_000).toLong()
                    val tokens = r.tokens.map { t ->
                        val dec = tokenRepository.getTokenDecimals(t.tokenId)
                        val rawAmt = (t.amount.toDoubleOrNull()
                            ?: throw Exception("Invalid amount for token ${getTokenName(t.tokenId)}"))
                        val amount = (rawAmt * Math.pow(10.0, dec.toDouble())).toLong()
                        com.piggytrade.piggytrade.blockchain.SendTxBuilder.TokenAmount(t.tokenId, amount)
                    }
                    com.piggytrade.piggytrade.blockchain.SendTxBuilder.SendRecipient(
                        address = r.address,
                        nanoErg = nanoErg,
                        tokens = tokens
                    )
                }

                val sendBuilder = com.piggytrade.piggytrade.blockchain.SendTxBuilder(client)
                val addressBoxMap = if (current.addressBoxes.isNotEmpty()) {
                    current.addressBoxes
                } else {
                    val (_, _, boxes) = client.getMyAssets(addr, current.includeUnconfirmed)
                    mapOf(addr to boxes)
                }

                val txDict = sendBuilder.buildSendTx(
                    recipients = recipients,
                    addressBoxes = addressBoxMap,
                    changeAddress = changeAddr,
                    feeNano = feeNano,
                    currentHeight = currentHeight
                )

                // Calculate totals for review
                var totalErgOut = 0.0
                val totalTokensOut = mutableMapOf<String, Long>()
                for (r in recipients) {
                    totalErgOut += r.nanoErg.toDouble() / 1_000_000_000.0
                    for (t in r.tokens) {
                        totalTokensOut[t.tokenId] = (totalTokensOut[t.tokenId] ?: 0L) + t.amount
                    }
                }

                // Calculate change from the txDict
                val requests = txDict["requests"] as List<Map<String, Any>>
                val changeRequest = requests.lastOrNull { (it["address"] as? String) == changeAddr }
                val changeErg = ((changeRequest?.get("value") as? Number)?.toLong() ?: 0L).toDouble() / 1_000_000_000.0
                val changeTokens = mutableMapOf<String, Long>()
                val changeAssets = changeRequest?.get("assets") as? List<Map<String, Any>> ?: emptyList()
                for (asset in changeAssets) {
                    val tid = asset["tokenId"] as String
                    val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                    changeTokens[tid] = amt
                }

                val isErgopayWallet = current.selectedWallet.contains("ergopay", ignoreCase = true) || current.selectedWallet.isEmpty()

                // Fetch block headers
                val buildHeaders = client.api.getLastHeaders(10)
                val buildHeadersJson = com.google.gson.Gson().toJson(buildHeaders)

                var ergopayUrl = ""
                if (isErgopayWallet) {
                    try {
                        val selNodeKey = current.nodes.getOrNull(current.selectedNodeIndex) ?: ""
                        val nodeUrl = if (selNodeKey.contains(": ")) selNodeKey.substringAfter(": ") else "https://ergo-node.eutxo.de"
                        val signer = com.piggytrade.piggytrade.blockchain.ErgoSigner(nodeUrl)
                        ergopayUrl = signer.reduceTxForErgopay(txDict, changeAddr, buildHeadersJson)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "ErgoPay URL generation failed: ${e.message}", e)
                        throw Exception("ErgoPay generation failed: ${e.message}")
                    }
                }

                val reviewParams = SendReviewParams(
                    recipients = current.sendRecipients,
                    minerFee = current.sendMinerFee,
                    totalErgOut = totalErgOut,
                    totalTokensOut = totalTokensOut,
                    changeErg = changeErg,
                    changeTokens = changeTokens,
                    isErgopay = isErgopayWallet,
                    ergopayUrl = ergopayUrl
                )

                _uiState.value = _uiState.value.copy(
                    isBuildingSendTx = false,
                    sendPreparedTxData = txDict,
                    sendReviewParams = reviewParams,
                    sendCachedHeadersJson = buildHeadersJson
                )

                launch { onSuccess() }
            } catch (e: Exception) {
                Log.e(TAG, "Send TX preparation failed", e)
                _uiState.value = _uiState.value.copy(
                    isBuildingSendTx = false,
                    sendError = e.message ?: "Unknown error"
                )
                onError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Mark biometric authentication as verified. Called ONLY from UI after biometric success.
     * Must be set before signAndBroadcastSend / signAndBroadcast for biometric wallets.
     */
    fun setBiometricVerified(verified: Boolean) {
        _biometricVerified = verified
    }
    @Volatile
    private var _biometricVerified = false

    fun signAndBroadcastSend(
        password: String,
        context: android.content.Context,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val current = _uiState.value
        val txDict = current.sendPreparedTxData ?: return
        val walletData = getWalletData(current.selectedWallet) ?: return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Decrypt mnemonic — biometric gate enforced
                val useBiometrics = walletData["use_biometrics"] as? Boolean ?: false
                val mnemonic = if (useBiometrics) {
                    if (!_biometricVerified) {
                        throw Exception("Biometric authentication required")
                    }
                    _biometricVerified = false // One-time use — must re-verify for next TX
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

                val headersJson = current.sendCachedHeadersJson.ifBlank {
                    Log.w(TAG, "No cached headers for send — falling back to fresh fetch.")
                    com.google.gson.Gson().toJson(client.api.getLastHeaders(10))
                }

                val signAddr = current.selectedAddress
                val signedJson = signer.signTransaction(
                    txDict, signAddr, mnemonic, "", headersJson,
                    addressCount = current.walletAddresses.size.coerceAtLeast(1)
                )
                val signedTxMap = signer.txGson.fromJson(signedJson, Map::class.java) as Map<String, Any>

                val txId = try {
                    if (current.isSimulation) {
                        if (current.strictSubmitNode) {
                            client.api.checkTransaction(signedTxMap)
                        } else {
                            nodePool.withRetryTracked { it.api.checkTransaction(signedTxMap) }
                        }
                        signedTxMap["id"] as? String ?: "Simulation"
                    } else {
                        if (current.strictSubmitNode) {
                            client.api.submitTransaction(signedTxMap)
                        } else {
                            nodePool.withRetryTracked { it.api.submitTransaction(signedTxMap) }
                        }
                    }
                } catch (he: retrofit2.HttpException) {
                    val errorBody = he.response()?.errorBody()?.string() ?: he.message()
                    throw Exception("Node rejected tx: $errorBody\n\n=== SIGNED TX JSON ===\n$signedJson")
                }

                _uiState.value = current.copy(
                    txSuccessData = TxSuccessData(
                        txId = txId,
                        isSimulation = current.isSimulation,
                        sigmaspaceUrl = "https://sigmaspace.io/en/transaction/$txId",
                        signedTxJson = signedJson
                    )
                )

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(txId)
                    launch {
                        kotlinx.coroutines.delay(200)
                        fetchWalletBalances(force = true)
                        fetchTransactionHistory()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send signing or broadcast failed", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun handleErgoPayUrl(url: String) {
        _uiState.value = _uiState.value.copy(ergoPayIncomingUrl = url)
    }

    /**
     * Sign an incoming ErgoPay reduced transaction and broadcast it.
     * Uses WalletLib.signReducedTxBytes (Rust JNI).
     */
    fun signAndBroadcastErgoPay(
        reducedTxBase64: String,
        password: String,
        context: android.content.Context,
        signingWallet: String = _uiState.value.selectedWallet,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val current = _uiState.value
        val walletData = getWalletData(signingWallet) ?: run {
            onError("Wallet '$signingWallet' not found")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Decrypt mnemonic — biometric gate enforced
                val useBiometrics = walletData["use_biometrics"] as? Boolean ?: false
                val mnemonic = if (useBiometrics) {
                    if (!_biometricVerified) {
                        throw Exception("Biometric authentication required")
                    }
                    _biometricVerified = false
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

                // Sign the reduced transaction
                val signedJson = org.ergoplatform.wallet.jni.WalletLib.signReducedTxBytes(
                    reducedTxBase64,
                    mnemonic,
                    "", // mnemonic password
                    current.walletAddresses.size.coerceAtLeast(1)
                )

                // Parse signed TX and submit
                val signedTxMap = com.google.gson.Gson().fromJson(signedJson, Map::class.java) as Map<String, Any>
                val client = nodeClient ?: throw Exception("Node client not initialized")

                val txId = try {
                    if (current.strictSubmitNode) {
                        client.api.submitTransaction(signedTxMap)
                    } else {
                        nodePool.withRetryTracked { it.api.submitTransaction(signedTxMap) }
                    }
                } catch (he: retrofit2.HttpException) {
                    val errorBody = he.response()?.errorBody()?.string() ?: he.message()
                    throw Exception("Node rejected tx: $errorBody")
                }

                _uiState.value = current.copy(
                    txSuccessData = TxSuccessData(
                        txId = txId,
                        isSimulation = false,
                        sigmaspaceUrl = "https://sigmaspace.io/en/transaction/$txId",
                        signedTxJson = signedJson
                    )
                )

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(txId)
                    launch {
                        kotlinx.coroutines.delay(200)
                        fetchWalletBalances(force = true)
                        fetchTransactionHistory()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ErgoPay signing or broadcast failed", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun clearSendState() {
        _uiState.value = _uiState.value.copy(
            sendRecipients = listOf(SendRecipientState()),
            sendMinerFee = 0.0011,
            isBuildingSendTx = false,
            sendError = null,
            sendReviewParams = null,
            sendPreparedTxData = null,
            sendCachedHeadersJson = "",
            ergoPayIncomingUrl = ""
        )
    }

    // ─── SYNC & DATA MANAGEMENT ──────────────────────────────────────────────

    fun syncTokenList(isFirstLaunch: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            syncProgress = SyncProgress(0, 0, false, emptyList(), isFirstLaunch)
        )

        viewModelScope.launch {
            try {
                // Use a single pool node for the full sync so pagination is consistent
                readNode { client ->
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

        // Market sync is now managed by MarketViewModel — no pause needed

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pageSize = 50
                val currentLimit = if (loadMore) _uiState.value.historyOffset + pageSize else pageSize
                val limit = currentLimit
                val currentOffset = 0

                // Build ergoTree → address map — use cache to skip API calls
                val ergoTreeToAddr = mutableMapOf<String, String>()
                val uncachedAddrs = mutableListOf<String>()
                for (addr in addressesToFetch) {
                    val cached = ergoTreeCache[addr]
                    if (cached != null) {
                        ergoTreeToAddr[cached] = addr
                    } else {
                        uncachedAddrs.add(addr)
                    }
                }
                if (uncachedAddrs.isNotEmpty()) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "ErgoTree cache miss for ${uncachedAddrs.size} addrs, fetching...")
                    val treeJobs = uncachedAddrs.map { addr ->
                        async {
                            try {
                                val treeRes = readNode { it.api.addressToErgoTree(addr) }
                                val tree = (treeRes["ergoTree"] as? String) ?: (treeRes["tree"] as? String) ?: ""
                                if (tree.isNotEmpty()) addr to tree else null
                            } catch (_: Exception) { null }
                        }
                    }
                    awaitAll(*treeJobs.toTypedArray()).filterNotNull().forEach { (addr, tree) ->
                        ergoTreeToAddr[tree] = addr
                        ergoTreeCache[addr] = tree  // Cache for next time
                    }
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
                                    val unconfList = readNode { it.api.getUnconfirmedTransactionsByErgoTree(
                                        offset = 0, limit = 50, ergoTree = reqBody
                                    )}
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
                            val confResp = readNode { it.api.getTransactionsByAddress(
                                offset = currentOffset, limit = limit, address = reqBody
                            )}
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
                    // We re-fetch from offset 0, so we just use the new trades, sort globally,
                    // and strictly cut off at currentLimit to perfectly paginate across independent streams.
                    val finalTrades = newTrades
                        .distinctBy { it.id }
                        .sortedWith(compareBy<NetworkTransaction> { it.isConfirmed }.thenByDescending { it.timestamp })
                        .take(currentLimit)

                    if (BuildConfig.DEBUG) Log.d(TAG, "Total raw=${newTrades.size}, unique=${finalTrades.size}, first=${finalTrades.firstOrNull()?.id?.take(8)}")

                    _uiState.value = _uiState.value.copy(
                        networkTrades = finalTrades,
                        historyOffset = currentLimit,
                        isLoadingHistory = false
                    )
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingHistory = false)
                }
            } finally {
                // Market sync (if it was running) is now managed by MarketViewModel — no resume needed
            }
        }
    }

    // ─── ADDRESS EXPLORER ─────────────────────────────────────────────────

    fun openAddressExplorer(address: String) {
        _uiState.value = _uiState.value.copy(
            explorerAddress = address,
            explorerErgBalance = 0.0,
            explorerTokens = emptyMap(),
            explorerTrades = emptyList(),
            explorerHistoryOffset = 0,
            isLoadingExplorer = true,
            isLoadingExplorerHistory = false
        )
        fetchExplorerBalances()
        fetchExplorerTransactionHistory()
    }

    private fun fetchExplorerBalances() {
        val address = _uiState.value.explorerAddress
        if (address.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (tokens, nanoerg, _) = readNode { it.getMyAssets(address, false) }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        explorerErgBalance = nanoerg.toDouble() / 1_000_000_000.0,
                        explorerTokens = tokens,
                        isLoadingExplorer = false
                    )
                }
                // Fetch token info for any tokens with generic names
                tokens.keys.forEach { tid ->
                    if (tid != "ERG") {
                        val currentName = tokenRepository.getTokenName(tid)
                        val isGeneric = currentName.startsWith(tid.take(5)) || currentName == tid
                        val hasNoDec = tokenRepository.getTokenDecimals(tid) == 0
                        if (isGeneric || hasNoDec) {
                            try {
                                readNode { it.api.getTokenInfo(tid) }?.let { info ->
                                    tokenRepository.saveTokenInfo(tid, info)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingExplorer = false)
                }
            }
        }
    }

    fun fetchExplorerTransactionHistory(loadMore: Boolean = false) {
        val address = _uiState.value.explorerAddress
        if (address.isEmpty()) return
        if (loadMore && _uiState.value.isLoadingExplorerHistory) return
        if (!loadMore) {
            _uiState.value = _uiState.value.copy(explorerTrades = emptyList(), explorerHistoryOffset = 0)
        }
        _uiState.value = _uiState.value.copy(isLoadingExplorerHistory = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentOffset = if (loadMore) _uiState.value.explorerHistoryOffset else 0
                val limit = 50
                val addressSet = setOf(address)

                // Build ergoTree map
                val ergoTreeToAddr = mutableMapOf<String, String>()
                val cached = ergoTreeCache[address]
                if (cached != null) {
                    ergoTreeToAddr[cached] = address
                } else {
                    try {
                        val treeRes = readNode { it.api.addressToErgoTree(address) }
                        val tree = (treeRes["ergoTree"] as? String) ?: (treeRes["tree"] as? String) ?: ""
                        if (tree.isNotEmpty()) {
                            ergoTreeToAddr[tree] = address
                            ergoTreeCache[address] = tree
                        }
                    } catch (_: Exception) {}
                }

                val newTrades = mutableListOf<NetworkTransaction>()

                // Unconfirmed (only on first page)
                if (currentOffset == 0) {
                    val ergoTree = ergoTreeToAddr.entries.firstOrNull()?.key ?: ""
                    if (ergoTree.isNotEmpty()) {
                        try {
                            val reqBody = "\"$ergoTree\"".toRequestBody("application/json".toMediaTypeOrNull())
                            val unconfList = readNode { it.api.getUnconfirmedTransactionsByErgoTree(
                                offset = 0, limit = 50, ergoTree = reqBody
                            )}
                            newTrades.addAll(parseNetworkTransactions(unconfList, addressSet, false, ergoTreeToAddr))
                        } catch (_: Exception) {}
                    }
                }

                // Confirmed
                try {
                    val reqBody = "\"$address\"".toRequestBody("application/json".toMediaTypeOrNull())
                    val confResp = readNode { it.api.getTransactionsByAddress(
                        offset = currentOffset, limit = limit, address = reqBody
                    )}
                    @Suppress("UNCHECKED_CAST")
                    val confList = confResp["items"] as? List<Map<String, Any>> ?: emptyList()
                    newTrades.addAll(parseNetworkTransactions(confList, addressSet, true, ergoTreeToAddr))
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    val currentList = if (loadMore) _uiState.value.explorerTrades else emptyList()
                    val finalTrades = (currentList + newTrades)
                        .distinctBy { it.id }
                        .sortedWith(compareBy<NetworkTransaction> { it.isConfirmed }.thenByDescending { it.timestamp })
                    _uiState.value = _uiState.value.copy(
                        explorerTrades = finalTrades,
                        explorerHistoryOffset = currentOffset + limit,
                        isLoadingExplorerHistory = false
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingExplorerHistory = false)
                }
            }
        }
    }

    fun clearExplorerState() {
        _uiState.value = _uiState.value.copy(
            explorerAddress = "",
            explorerErgBalance = 0.0,
            explorerTokens = emptyMap(),
            explorerTrades = emptyList(),
            explorerHistoryOffset = 0,
            isLoadingExplorer = false,
            isLoadingExplorerHistory = false
        )
    }

    fun saveExplorerAddress(address: String, label: String) {
        val current = _uiState.value.savedExplorerAddresses.toMutableMap()
        current[address] = label
        _uiState.value = _uiState.value.copy(savedExplorerAddresses = current)
        preferenceManager.saveExplorerAddresses(current)
    }

    fun removeExplorerAddress(address: String) {
        val current = _uiState.value.savedExplorerAddresses.toMutableMap()
        current.remove(address)
        _uiState.value = _uiState.value.copy(savedExplorerAddresses = current)
        preferenceManager.saveExplorerAddresses(current)
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
}

