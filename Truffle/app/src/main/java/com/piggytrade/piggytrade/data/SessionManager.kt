package com.piggytrade.piggytrade.data

import android.app.Application
import com.piggytrade.piggytrade.network.NodeManager
import com.piggytrade.piggytrade.network.NodePool

/**
 * Application-scoped container for shared services and state.
 *
 * All ViewModels read from the same [SessionManager] instance (provided via
 * [com.piggytrade.piggytrade.TruffleApplication]), which means:
 *  - WalletViewModel can update balances and BankViewModel/SwapViewModel see them
 *  - NodeClient is created once, not per-ViewModel
 *  - TokenRepository and OraclePriceStore are shared singletons
 *
 * This is the foundation layer for the ViewModel decomposition.
 */
class SessionManager(application: Application) {

    // ─── Shared services (stateless singletons) ─────────────────────────
    val preferenceManager = PreferenceManager(application)
    val tokenRepository = TokenRepository(application)
    val oraclePriceStore = OraclePriceStore(application)
    val nodePool = NodePool()

    // ─── Node management ─────────────────────────────────────────────────
    /** Owns node list, selected index, active NodeClient, and URL. */
    val nodeManager = NodeManager(preferenceManager)

    // Convenience accessors — delegate through NodeManager
    val nodeClient get() = nodeManager.nodeClient
    fun setNodeClient(client: com.piggytrade.piggytrade.network.NodeClient?) =
        nodeManager.setNodeClientDirect(client)
}
