package com.piggytrade.piggytrade.stablecoin

import com.piggytrade.piggytrade.stablecoin.use.UseArbmintProtocol
import com.piggytrade.piggytrade.stablecoin.use.UseFreemintProtocol
import com.piggytrade.piggytrade.stablecoin.sigmausd.SigmaUsdMintProtocol
import com.piggytrade.piggytrade.stablecoin.sigmausd.SigmaRsvMintProtocol
import com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldFreemintProtocol
import com.piggytrade.piggytrade.stablecoin.dexygold.DexyGoldArbmintProtocol

/**
 * Central registry for all [StablecoinProtocol] implementations.
 *
 * Protocols are registered once at app startup via [initialize].
 * The BANK tab UI calls [getAll] to build its protocol selector and
 * [getById] to dispatch user actions.
 *
 * To add a future stablecoin:
 *  1. Create a new sub-package (e.g. stablecoin/dexygold/)
 *  2. Implement [StablecoinProtocol]
 *  3. Register it here – no other changes needed.
 */
object StablecoinRegistry {

    private val protocols = mutableListOf<StablecoinProtocol>()

    fun register(protocol: StablecoinProtocol) {
        protocols.add(protocol)
    }

    /** Returns all registered protocols in registration order. */
    fun getAll(): List<StablecoinProtocol> = protocols.toList()

    /** Looks up a protocol by its [StablecoinProtocol.id]. */
    fun getById(id: String): StablecoinProtocol? = protocols.find { it.id == id }

    /** Returns all protocols that mint the given token name (case-insensitive). */
    fun getForToken(tokenName: String): List<StablecoinProtocol> =
        protocols.filter { it.mintTokenName.equals(tokenName, ignoreCase = true) }

    /**
     * Register all built-in protocols.
     * Called once from [com.piggytrade.piggytrade.PiggyApplication.onCreate].
     */
    fun initialize() {
        register(UseFreemintProtocol())
        register(UseArbmintProtocol())
        register(DexyGoldFreemintProtocol())
        register(DexyGoldArbmintProtocol())
        register(SigmaUsdMintProtocol())
        register(SigmaRsvMintProtocol())
    }
}
