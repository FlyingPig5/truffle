package com.piggytrade.piggytrade.stablecoin

import com.piggytrade.piggytrade.network.NodeClient

// ─── Shared Display / Result Types ───────────────────────────────────────────

/**
 * A single labelled field to be displayed in the Protocol Status card.
 * Each [StablecoinProtocol.checkEligibility] call returns a list of these so
 * the UI can render them without knowing any protocol specifics.
 */
data class StatusField(
    val label: String,
    val value: String,
    val status: Status = Status.NEUTRAL
) {
    enum class Status { NEUTRAL, OK, WARNING, ERROR }
}

/**
 * Result of a pre-flight eligibility check.
 * [canMint] drives the enabled state of the Mint button.
 * [statusFields] are rendered row-by-row in the Protocol Status card.
 */
data class EligibilityResult(
    val canMint: Boolean,
    val canRedeem: Boolean = true,       // Whether redeem is allowed (defaults true for backward compat)
    val reason: String? = null,          // Human-readable reason when something is blocked
    val availableCapacity: Long = 0L,    // raw token units
    val statusFields: List<StatusField> = emptyList()
)

/**
 * Contains everything the Review screen and Order Details need to display.
 * [feeBreakdown] is rendered as a dynamic list – no hard-coded fee labels.
 */
data class MintQuote(
    val tokenReceived: String,                    // e.g. "USE"
    val amountReceived: Long,                     // raw units
    val tokenDecimals: Int,                       // for display formatting
    val ergCost: Long,                            // total nanoErg from wallet (excl. mining fee)
    val feeBreakdown: List<Pair<String, Long>>,   // e.g. [("Bank Fee 0.3%", 370_000L), ...]
    val miningFee: Long
)

/**
 * Result of a redeem (sell-back) operation.
 * [ergReceived] is the nanoErg the user gets back after fees.
 */
data class RedeemQuote(
    val tokenRedeemed: String,                    // e.g. "SigUSD"
    val amountRedeemed: Long,                     // raw units being returned
    val tokenDecimals: Int,
    val ergReceived: Long,                        // nanoErg returned to user (after all fees)
    val feeBreakdown: List<Pair<String, Long>>,
    val miningFee: Long
)

// ─── Protocol Interface ───────────────────────────────────────────────────────

/**
 * Implement this interface to add a new stablecoin to the BANK tab.
 *
 * The UI, ViewModel and transaction flow are fully protocol-agnostic:
 *  - [checkEligibility] drives the status card and button enabled state.
 *  - [getQuote]        drives the order details panel.
 *  - [buildTransaction] produces the same Map<String,Any> shape as TxBuilder.
 *  - [postProcessUnsignedTx] is an optional hook for context-variable injection.
 *
 * Protocols that support redeem (selling tokens back) override [supportsRedeem],
 * [getRedeemQuote], and [buildRedeemTransaction]. Default implementations throw
 * so existing mint-only protocols (USE) don't need changes.
 *
 * Register new protocols via [StablecoinRegistry.register] at app startup.
 */
interface StablecoinProtocol {
    /** Unique identifier, e.g. "use_freemint", "use_arbmint". */
    val id: String

    /** Label shown in the protocol-selector chip row, e.g. "Freemint". */
    val displayName: String

    val mintTokenId: String
    val mintTokenName: String
    val mintTokenDecimals: Int

    /** Whether this protocol supports redeem (selling tokens back to the bank). */
    val supportsRedeem: Boolean get() = false

    /**
     * Fetch on-chain state and return the current mint availability + status
     * fields for the UI card. Called whenever the BANK tab is focused.
     */
    suspend fun checkEligibility(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): EligibilityResult

    /**
     * Calculate price, total ERG cost, and fee breakdown for [amount] tokens.
     * Called whenever the user changes the amount field.
     */
    suspend fun getQuote(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        checkMempool: Boolean
    ): MintQuote

    /**
     * Build and return the full unsigned transaction dictionary.
     * Shape must match [TxBuilder] output:
     *   { "requests", "fee", "inputsRaw", "dataInputsRaw", "current_height" }
     */
    suspend fun buildTransaction(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        miningFee: Long,
        checkMempool: Boolean
    ): Map<String, Any>

    /**
     * Optional hook called after the node generates the unsigned tx JSON.
     * Override to inject context variables (e.g. the buyback "0402" extension).
     * Default implementation is a no-op.
     */
    fun postProcessUnsignedTx(
        unsignedTxDict: MutableMap<String, Any>
    ): Map<String, Any> = unsignedTxDict

    // ─── Redeem Support (optional) ────────────────────────────────────────────

    /**
     * Calculate ERG received and fee breakdown for redeeming [amount] tokens.
     * Only called when [supportsRedeem] is true.
     */
    suspend fun getRedeemQuote(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        checkMempool: Boolean
    ): RedeemQuote = throw UnsupportedOperationException("Redeem not supported by $id")

    /**
     * Build the unsigned redeem transaction.
     * Only called when [supportsRedeem] is true.
     */
    suspend fun buildRedeemTransaction(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        miningFee: Long,
        checkMempool: Boolean
    ): Map<String, Any> = throw UnsupportedOperationException("Redeem not supported by $id")
}
