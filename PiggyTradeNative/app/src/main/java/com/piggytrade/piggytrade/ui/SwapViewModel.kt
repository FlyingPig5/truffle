package com.piggytrade.piggytrade.ui

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.piggytrade.piggytrade.blockchain.Trader
import com.piggytrade.piggytrade.blockchain.TxBuilder
import com.piggytrade.piggytrade.data.PreferenceManager
import com.piggytrade.piggytrade.data.TokenRepository
import com.piggytrade.piggytrade.network.NodeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    val isLoadingQuote: Boolean = false,
    val isSimulation: Boolean = false,
    val minerFee: Double = 0.001,
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
    val trades: List<TradeHistoryItem> = emptyList(),
    val syncProgress: SyncProgress? = null,
    val whitelistedPools: List<PoolMapping> = emptyList(),
    val discoveredPools: List<PoolMapping> = emptyList(),
    val isLoadingMapping: Boolean = false,
    val isWalletExpanded: Boolean = false,
    val numFavorites: Int = 8,
    val activeSelector: String? = null, // "from", "to", "fav", "wallet"
    val nodeUrl: String = "",
    val isToAssetFavorite: Boolean = false,
    val activeTab: String = "dex" // "dex", "wallet"
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
    val sigmaspaceUrl: String
)

data class TradeHistoryItem(
    val timestamp: String,
    val buy: String,
    val pay: String,
    val txId: String,
    val isSimulation: Boolean,
    val address: String
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

    private val _uiState: MutableStateFlow<SwapState> by lazy {
        Log.d(TAG, "Initializing MutableStateFlow...")
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
        val initialAddress = (savedWallets[initialWalletKey] as? Map<String, Any>)?.get("address") as? String ?: ""

        MutableStateFlow(
            SwapState(
                nodes = nodesList,
                selectedNodeIndex = initialNodeIndex,
                debugMode = (preferenceManager.loadSettings()["debug_mode"] as? Boolean) ?: false,
                tokens = emptyList(), // Start empty, will be filled by loadPoolMappings
                wallets = displayWallets,
                selectedWallet = initialWalletDisplay,
                selectedAddress = initialAddress,
                favorites = favorites,
                includeUnconfirmed = (preferenceManager.loadSettings()["include_unconfirmed"] as? Boolean) ?: true,
                numFavorites = numFavs,
                trades = preferenceManager.loadTrades().map {
                    TradeHistoryItem(
                        timestamp = it["timestamp"] as? String ?: "",
                        buy = it["buy"] as? String ?: "",
                        pay = it["pay"] as? String ?: "",
                        txId = it["txId"] as? String ?: "",
                        isSimulation = it["isSimulation"] as? Boolean ?: false,
                        address = it["address"] as? String ?: ""
                    )
                }
            )
        )
    }
    val uiState: StateFlow<SwapState> by lazy { _uiState.asStateFlow() }

    init {
        Log.d(TAG, "SwapViewModel instance created.")
        
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
        }
    }
    private var nodeClient: NodeClient? = null
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
                    
                    val finalUrl = if (nUrl.startsWith("http")) nUrl else "https://ergo-node.eutxo.de"
                    Log.d(TAG, "Using node URL: $finalUrl")
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
        if (tokenId == "ERG") return String.format("%.4f", amount.toDouble() / 1_000_000_000.0)
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
        
        val fBal = if (fAsset == "ERG") String.format("%.4f", walletErg) 
                   else formatBalance(fAsset, walletTokens[fAsset] ?: 0L)
        
        val tBal = if (tAsset == "ERG") String.format("%.4f", walletErg)
                   else formatBalance(tAsset, walletTokens[tAsset] ?: 0L)
        
        _uiState.value = current.copy(fromBalance = fBal, toBalance = tBal)
    }

    fun getUserBalance(name: String): String? {
        val id = getTokenId(name)
        val current = _uiState.value
        if (id == "ERG") {
            if (current.walletErgBalance > 0) return String.format("%.4f", current.walletErgBalance)
            return null
        }
        val amount = current.walletTokens[id] ?: return null
        return formatBalance(id, amount)
    }

    // ─── UI STATE SETTERS (DEX) ──────────────────────────────────────────────

    fun setFromAsset(asset: String) {
        _uiState.value = _uiState.value.copy(
            fromAsset = asset,
            fromPulseTrigger = System.currentTimeMillis()
        )
        updateBalances()
        fetchQuote()
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
        fetchQuote()
    }

    fun setFromAmount(amount: String) {
        _uiState.value = _uiState.value.copy(fromAmount = amount)
        fetchQuote()
    }

    fun setMaxAmount() {
        _uiState.value = _uiState.value.copy(fromAmount = _uiState.value.fromBalance)
        fetchQuote()
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
            fetchQuote()
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
    }

    fun setDebugMode(debug: Boolean) {
        _uiState.value = _uiState.value.copy(debugMode = debug)
        val settings = preferenceManager.loadSettings().toMutableMap()
        settings["debug_mode"] = debug
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

    fun fetchWalletBalances() {
        val address = _uiState.value.selectedAddress
        if (address.isEmpty()) return

        _uiState.value = _uiState.value.copy(isLoadingWallet = true)
        viewModelScope.launch {
            try {
                val client = nodeClient ?: return@launch
                val (tokens, nanoerg, _) = client.getMyAssets(address, checkMempool = _uiState.value.includeUnconfirmed)
                _uiState.value = _uiState.value.copy(
                    walletErgBalance = nanoerg.toDouble() / 1_000_000_000.0,
                    walletTokens = tokens,
                    isLoadingWallet = false
                )
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

            if (mnemonic == null && address != null) {
                wallets[name] = mapOf(
                    "address" to address,
                    "read_only" to true,
                    "type" to "ergopay"
                )
                isErgoPayType = true
                derivedAddress = address
            } else if (mnemonic != null) {
                // Derive the real address using ergo-lib-jni (BIP44 EIP-3 path)
                derivedAddress = try {
                    org.ergoplatform.wallet.jni.WalletLib.mnemonicToAddress(
                        mnemonic, "", 0, true
                    )
                } catch (e: Exception) {
                    android.util.Log.e("SaveWallet", "Address derivation failed: ${e.message}")
                    ""
                }

                // Encrypt the mnemonic
                val walletData = mutableMapOf<String, Any>(
                    "address" to derivedAddress,
                    "type" to "mnemonic",
                    "use_legacy" to useLegacy,
                    "kdf" to "scrypt"
                )

                if (useBiometrics) {
                    walletData["use_biometrics"] = true
                    // Store plaintext temporarily - biometric encryption happens at sign time
                    walletData["mnemonic_plain"] = mnemonic
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
                    selectedAddress = finalAddress
                )
                preferenceManager.selectedWallet = name
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
                val addr = (wallets[internalKey] as? Map<String, Any>)?.get("address") as? String ?: ""
                _uiState.value = current.copy(
                    selectedWallet = item,
                    selectedAddress = addr
                )
                preferenceManager.selectedWallet = internalKey
                fetchWalletBalances()
                updateBalances()
            }
            "node" -> {
                val index = current.nodes.indexOf(item)
                if (index != -1) {
                    setSelectedNodeIndex(index)
                }
            }
        }
    }

    fun setActiveTab(tab: String) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
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
            
            val newAddress = if (newSelectedWallet != "Select Wallet") {
                val data = wallets[newSelectedWallet.replace(" (Ergopay)", "").trim()] as? Map<String, Any>
                data?.get("address") as? String ?: ""
            } else ""

            _uiState.value = current.copy(
                wallets = displayWallets,
                selectedWallet = newSelectedWallet,
                selectedAddress = newAddress,
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

    private fun fetchQuote() {
        val current = _uiState.value
        val amount = current.fromAmount.toDoubleOrNull() ?: 0.0

        if (amount <= 0.0 || current.fromAsset.isEmpty() || current.toAsset.isEmpty()) {
            _uiState.value = _uiState.value.copy(toQuote = "--", priceImpact = 0.0)
            return
        }

        _uiState.value = _uiState.value.copy(isLoadingQuote = true, toQuote = "Fetching...")

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val route = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    tokenRepository.tradeMapper.resolve(current.fromAsset, current.toAsset)
                }
                if (route != null && trader != null) {
                    val (quote, impact) = trader!!.getQuote(
                        tokenName = route.tokenKey,
                        amount = amount,
                        orderType = route.orderType,
                        poolType = route.poolType,
                        checkMempool = current.includeUnconfirmed
                    )
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingQuote = false,
                            toQuote = quote,
                            priceImpact = impact
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
    fun getVerificationStatus(tokenKey: String) = tokenRepository.getVerificationStatus(tokenKey)
    fun isWhitelisted(tokenKey: String): Boolean {
        val pid = tokenRepository.tokens[tokenKey]?.get("pid") as? String ?: ""
        return tokenRepository.isPidWhitelisted(pid)
    }

    fun isValidPair(assetA: String, assetB: String): Boolean {
        if (assetA.isEmpty() || assetB.isEmpty()) return true
        if (assetA == assetB) return false
        
        // ERG case
        if (assetA == "ERG" || assetB == "ERG") {
            val tokenName = if (assetA == "ERG") assetB else assetA
            return tokenRepository.tokens.containsKey(tokenName)
        }
        
        // Token to Token case
        val pairName1 = "$assetA-$assetB"
        val pairName2 = "$assetB-$assetA"
        return tokenRepository.tokens.containsKey(pairName1) || tokenRepository.tokens.containsKey(pairName2)
    }

    fun getTokenId(name: String): String {
        if (name == "ERG") return "ERG"
        if (name.length >= 64 && name.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return name
        
        tokenRepository.tokens.forEach { (tName, data) ->
            if (data.containsKey("id_in")) {
                val nameIn = data["name_in"] as? String ?: tName.split("-").firstOrNull() ?: ""
                val nameOut = data["name_out"] as? String ?: tName.split("-").takeIf { it.size > 1 }?.get(1) ?: ""
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
        Log.d(TAG, "prepareSwap called. current state info: from=${current.fromAsset}, amount=${current.fromAmount}")
        
        val amount = current.fromAmount.toDoubleOrNull()
        if (amount == null || amount <= 0 || current.fromAsset.isEmpty() || current.toAsset.isEmpty()) {
            onError("Invalid amount or asset")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuildingTx = true)
            Log.d(TAG, "isBuildingTx set to true")
            try {
                val route = tokenRepository.tradeMapper.resolve(current.fromAsset, current.toAsset)
                if (route == null) throw Exception("No pool found for this pair")
                Log.d(TAG, "Resolved route: tokenKey=${route.tokenKey}, type=${route.orderType}")

                val addr = current.selectedAddress
                if (addr.isEmpty()) throw Exception("No wallet selected")

                val client = nodeClient ?: throw Exception("Node client not initialized")
            val currentHeight = try { client.getHeight() } catch (e: Exception) { 0 }
            
            val txBuilder = com.piggytrade.piggytrade.blockchain.TxBuilder(client, addr)
            val traderLocal = Trader(client, txBuilder, tokenRepository.tokens, null)

            Log.d(TAG, "Building transaction at height $currentHeight...")
            val txDict = traderLocal.buildSwapTransaction(
                tokenName = route.tokenKey,
                amount = amount,
                orderType = route.orderType,
                poolType = route.poolType,
                senderAddress = addr,
                currentHeight = currentHeight,
                fee = current.minerFee,
                includeUnconfirmed = current.includeUnconfirmed
            )
                Log.i(TAG, "Successfully built transaction.")

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
                var ergopayUrl = ""
                if (isErgopayWallet) {
                    try {
                        val headers = client.api.getLastHeaders(10)
                        val headersJson = com.google.gson.Gson().toJson(headers)
                        ergopayUrl = signer.reduceTxForErgopay(txDict, addr, headersJson)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "ErgoPay URL generation failed: ${e.message}")
                        ergopayUrl = signer.reduceTxForErgopayLegacy(txDict, addr)
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

                Log.d(TAG, "Updating UI state with reviewParams...")
                _uiState.value = _uiState.value.copy(
                    isBuildingTx = false,
                    preparedTxData = txDict,
                    unsignedTxJson = jsonTx,
                    reviewParams = params
                )
                
                Log.d(TAG, "Executing onSuccess callback...")
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
                    walletData["mnemonic_plain"] as? String 
                        ?: throw Exception("Biometric signing without plain mnemonic currently unsupported")
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

                val headers = client.api.getLastHeaders(10)
                val headersJson = com.google.gson.Gson().toJson(headers)

                // Sign
                val signedJson = signer.signTransaction(txDict, current.selectedAddress, mnemonic, "", headersJson)
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
                    throw Exception("Node rejected tx: $errorBody")
                }

                // Success
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timestamp = sdf.format(Date())
                
                val newTrade = TradeHistoryItem(
                    timestamp = timestamp,
                    buy = "${current.reviewParams?.buyAmount} ${current.reviewParams?.buyToken}",
                    pay = "${current.reviewParams?.payAmount} ${current.reviewParams?.payToken}",
                    txId = txId,
                    isSimulation = current.isSimulation,
                    address = current.selectedAddress
                )
                
                val updatedTrades = current.trades + newTrade
                _uiState.value = current.copy(
                    trades = updatedTrades,
                    txSuccessData = TxSuccessData(
                        txId = txId,
                        isSimulation = current.isSimulation,
                        sigmaspaceUrl = "https://sigmaspace.io/tx/$txId"
                    )
                )
                
                // Persist trades
                preferenceManager.saveTrades(updatedTrades.map {
                    mapOf(
                        "timestamp" to it.timestamp,
                        "buy" to it.buy,
                        "pay" to it.pay,
                        "txId" to it.txId,
                        "isSimulation" to it.isSimulation,
                        "address" to it.address
                    )
                })

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(txId)
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
                    loadPoolMappings()
                    _uiState.value = _uiState.value.copy(
                        syncProgress = _uiState.value.syncProgress?.copy(isFinished = true)
                    )
                    // Refresh balances to catch any now-identified tokens
                    fetchWalletBalances()
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

    fun exportTokens(context: android.content.Context) {
        val ergToToken = tokenRepository.getErgToTokenJson()
        val tokenToToken = tokenRepository.getTokenToTokenJson()
        
        val combined = "--- ERG to Token ---\n$ergToToken\n\n--- Token to Token ---\n$tokenToToken"
        
        val sendIntent: android.content.Intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, combined)
            type = "text/plain"
        }
        
        val shareIntent = android.content.Intent.createChooser(sendIntent, "Export Tokens JSON")
        shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
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
            val status = getVerificationStatus(key)
            
            // Find existing liquidity in state to avoid re-fetching or resetting to "Fetching..."
            val existingState = _uiState.value
            val existingLiq = (existingState.whitelistedPools + existingState.discoveredPools)
                .find { it.pid == pid }?.liquidity ?: "Fetching..."

            val displayName = if (data.containsKey("id_in")) {
                val nameIn = data["name_in"] as? String ?: key.split("-").firstOrNull() ?: ""
                val nameOut = data["name_out"] as? String ?: key.split("-").takeIf { it.size > 1 }?.get(1) ?: ""
                val n1 = getTokenName(getTokenId(nameIn))
                val n2 = getTokenName(getTokenId(nameOut))
                "$n1-$n2"
            } else {
                getTokenName(getTokenId(key))
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
            val name = mapping.name
            if (mapping.data.containsKey("id_in")) {
                val parts = name.split("-")
                whitelistedAssets.add(parts[0])
                whitelistedAssets.add(parts[1])
            } else {
                whitelistedAssets.add(name)
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
        finalTokens.add("ERG")
        finalTokens.addAll(tokensWithBalance.sorted())
        finalTokens.addAll(tokensWithoutBalance.sorted())

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
            
            (currentWhitelisted + currentDiscovered).forEach { mapping ->
                launch {
                    try {
                        val box = client.getPoolBox(mapping.pid, _uiState.value.includeUnconfirmed)
                        if (box == null) {
                            updateLiquidityInState(mapping.pid, "Pool not found")
                            return@launch
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
        Log.d(TAG, "togglePoolWhitelist: key=${mapping.key}, shouldWhitelist=$shouldWhitelist")
        
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save whitelist change", e)
            }
        }
    }

    fun resetTokenData() {
        tokenRepository.resetTokenData()
        loadPoolMappings()
    }
}
