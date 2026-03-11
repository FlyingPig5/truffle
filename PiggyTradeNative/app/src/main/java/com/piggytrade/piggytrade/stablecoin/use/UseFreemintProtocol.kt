package com.piggytrade.piggytrade.stablecoin.use

import android.util.Log
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.stablecoin.EligibilityResult
import com.piggytrade.piggytrade.stablecoin.MintQuote
import com.piggytrade.piggytrade.stablecoin.StatusField
import com.piggytrade.piggytrade.stablecoin.StablecoinProtocol
import com.piggytrade.piggytrade.stablecoin.VlqCodec
import com.piggytrade.piggytrade.blockchain.ErgoSigner
import com.piggytrade.piggytrade.protocol.ProtocolConfig
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * USE Freemint Protocol implementation.
 *
 * Logic ported from PigTrade-Simple/use_freemint_v2.py and
 * PigTrade-Simple/functions/tx_assembler_USE.py (buy_freemint function).
 *
 * Per-cycle capacity is limited to 1% of the LP USE balance.
 * The cycle resets after [UseConfig.FREEMINT_CYCLE_BLOCKS] blocks.
 */
class UseFreemintProtocol : StablecoinProtocol {

    override val id = "use_freemint"
    override val displayName = "Freemint"
    override val mintTokenId = UseConfig.USE_TOKEN_ID
    override val mintTokenName = "USE"
    override val mintTokenDecimals = 3

    private val TAG = "UseFreemint"

    // ─── EligibilityResult ────────────────────────────────────────────────────

    override suspend fun checkEligibility(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): EligibilityResult {
        return try {
            val state = fetchProtocolState(client, senderAddress, checkMempool)
            val height = client.getHeight()

            val (available, resetHeight) = resolveCapacity(state, height)
            val blocksRemaining = (resetHeight - height).coerceAtLeast(0)
            val availableDisplay = "%.3f USE".format(available.toDouble() / UseConfig.USE_DECIMALS)
            // Oracle: state.oracleRate = raw R4 (nanoERG per 1 USD = per 1 human USE)
            // ERG per 1 USE = R4 / 1e9
            val contractOracleRate = state.oracleRate / UseConfig.USE_DECIMALS  // nanoERG per USE base unit (for contract math)
            val ergPerUse = state.oracleRate.toDouble() / 1_000_000_000.0       // ERG per 1 human USE (for display)
            val usePerErg = if (ergPerUse > 0) 1.0 / ergPerUse else 0.0

            val lpRate = if (state.lpUseAmount > 0) state.lpNanoErg / state.lpUseAmount else 0L
            val rateCheckPasses = lpRate * 100 > contractOracleRate * 98

            val fields = buildList {
                add(StatusField(
                    "Available this cycle", availableDisplay,
                    if (available > 0) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField("Cycle resets in", "$blocksRemaining blocks"))
                add(StatusField("Oracle price", "%.3f USE / ERG".format(usePerErg)))
                add(StatusField(
                    "LP rate check",
                    if (rateCheckPasses) "OK (LP rate within 2% of oracle)" else "BLOCKED (LP rate too far from oracle)",
                    if (rateCheckPasses) StatusField.Status.OK else StatusField.Status.ERROR
                ))
            }

            val canMint = available > 0 && rateCheckPasses
            val reason = when {
                !rateCheckPasses -> "Freemint blocked: LP rate (${lpRate}) has deviated >2% from oracle rate (${contractOracleRate}). Try again later."
                available <= 0 -> "No capacity available this cycle"
                else -> null
            }

            EligibilityResult(
                canMint = canMint,
                reason = reason,
                availableCapacity = available,
                statusFields = fields
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkEligibility failed: ${e.message}", e)
            EligibilityResult(
                canMint = false,
                reason = "Could not fetch protocol state: ${e.message}",
                statusFields = listOf(StatusField("Error", e.message ?: "Unknown", StatusField.Status.ERROR))
            )
        }
    }

    // ─── MintQuote ────────────────────────────────────────────────────────────

    override suspend fun getQuote(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        checkMempool: Boolean
    ): MintQuote {
        val state = fetchProtocolState(client, senderAddress, checkMempool)
        val amountUseInt = (amount * UseConfig.USE_DECIMALS).toLong()

        // state.oracleRate is raw R4 (nanoErgs per USD).
        // Contract: oracleRate = R4 / 1000 (nanoErgs per USE base unit)
        val oracleRate = state.oracleRate / UseConfig.USE_DECIMALS
        val bankErgs = amountUseInt * oracleRate * (UseConfig.BANK_FEE_NUM + UseConfig.FEE_DENOM) / UseConfig.FEE_DENOM
        val buybackErgs = amountUseInt * oracleRate * UseConfig.BUYBACK_FEE_NUM / UseConfig.FEE_DENOM

        val signer = ErgoSigner("")
        val utxoDust = signer.resolveUtxoGap(bankErgs).toLong()

        // Fee breakdown for display (in nanoErgs)
        val baseCost = amountUseInt * oracleRate
        val bankFeeDisplay = bankErgs - baseCost
        val buybackFeeDisplay = buybackErgs

        val breakdown = buildList {
            add("Bank Fee (0.3%)" to bankFeeDisplay)
            add("Buyback Fee (0.2%)" to buybackFeeDisplay)
            if (utxoDust > 0L) add("App Fee" to utxoDust)
        }

        return MintQuote(
            tokenReceived = mintTokenName,
            amountReceived = amountUseInt,
            tokenDecimals = mintTokenDecimals,
            ergCost = bankErgs + buybackErgs + utxoDust,
            feeBreakdown = breakdown,
            miningFee = UseConfig.DEFAULT_MINING_FEE_NANO
        )
    }

    // ─── buildTransaction ─────────────────────────────────────────────────────

    override suspend fun buildTransaction(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        miningFee: Long,
        checkMempool: Boolean
    ): Map<String, Any> {
        val state = fetchProtocolState(client, senderAddress, checkMempool)
        val amountUseInt = (amount * UseConfig.USE_DECIMALS).toLong()
        val height = client.getHeight()

        // ── Pre-flight contract validation ────────────────────────────────────
        // Contract: validRateFreeMint = lpRate * 100 > oracleRate * 98
        val contractOracleRate = state.oracleRate / UseConfig.USE_DECIMALS
        val lpRate = if (state.lpUseAmount > 0) state.lpNanoErg / state.lpUseAmount else 0L
        if (lpRate * 100 <= contractOracleRate * 98) {
            throw Exception(
                "Freemint blocked: LP rate ($lpRate) has deviated >2% from oracle rate ($contractOracleRate). " +
                "LP rate * 100 = ${lpRate * 100}, oracle rate * 98 = ${contractOracleRate * 98}. Try again later."
            )
        }

        // ── Fees (must match ErgoScript contract math exactly) ────────────────
        // state.oracleRate is raw R4 (nanoErgs per USD)
        // Contract: val oracleRate = oracleBox.R4[Long].get / 1000L
        // Contract: bankRate = oracleRate * 1003 / 1000
        // Contract: ergsAdded >= dexyMinted * bankRate
        val oracleRate = contractOracleRate
        // Contract evaluates: (v4 * v16 * 1003) / 1000  — multiply FIRST, divide LAST
        // The oracle can update its box between tx-build and tx-eval.
        // Add a 0.1% buffer on the base cost to survive oracle price swings.
        val baseBankErgs = amountUseInt * oracleRate * (UseConfig.BANK_FEE_NUM + UseConfig.FEE_DENOM) / UseConfig.FEE_DENOM
        val bankErgsToAdd = baseBankErgs + baseBankErgs / 1000   // +0.1% buffer
        val baseBuybackErgs = amountUseInt * oracleRate * UseConfig.BUYBACK_FEE_NUM / UseConfig.FEE_DENOM
        val buybackErgsToAdd = baseBuybackErgs + baseBuybackErgs / 1000  // +0.1% buffer

        val signer = ErgoSigner("")
        val utxoDust = signer.resolveUtxoGap(bankErgsToAdd).toLong()

        // ── Reset logic ───────────────────────────────────────────────────────
        val (_, resetHeight) = resolveCapacity(state, height)
        val (resetHeightVlq, newAvailableVlq) = buildRegisterValues(state, height, amountUseInt)

        // ── Fetch box bytes ───────────────────────────────────────────────────
        val inputBoxIds = listOf(state.freemintBoxId, state.bankBoxId, state.buybackBoxId) + state.userBoxIds
        val dataInputIds = listOf(state.oracleBoxId, state.lpBoxId)

        val inputsRaw = client.getBoxBytes(inputBoxIds)
        val dataInputsRaw = client.getBoxBytes(dataInputIds)

        // Total ERG the user needs to spend (bank delta + buyback delta + mining fee + app fee)
        val userChangeValue = state.userNanoErg - bankErgsToAdd - buybackErgsToAdd - miningFee - utxoDust
        if (userChangeValue < 0) {
            throw Exception("Insufficient ERG: need ${(bankErgsToAdd + buybackErgsToAdd + miningFee + utxoDust) / 1_000_000_000.0} ERG, have ${state.userNanoErg / 1_000_000_000.0} ERG")
        }

        // ── Build the requests list ───────────────────────────────────────────
        val requestsList = mutableListOf(
            // Output 0: Freemint box (preserved, updated registers)
            mapOf(
                "address" to UseConfig.FREEMINT_ADDRESS,
                "value" to UseConfig.FREEMINT_BOX_VALUE_NANO,
                "assets" to listOf(mapOf("tokenId" to UseConfig.FREEMINT_NFT, "amount" to 1L)),
                "registers" to mapOf("R4" to resetHeightVlq, "R5" to newAvailableVlq),
                "creationHeight" to height
            ),
            // Output 1: Bank box (receives ERG = dexyMinted * bankRate)
            mapOf(
                "address" to UseConfig.BANK_ADDRESS,
                "value" to state.bankNanoErg + bankErgsToAdd,
                "assets" to listOf(
                    mapOf("tokenId" to UseConfig.BANK_NFT, "amount" to 1L),
                    mapOf("tokenId" to UseConfig.USE_TOKEN_ID, "amount" to state.bankUseAmount - amountUseInt)
                ),
                "registers" to emptyMap<String, Any>(),
                "creationHeight" to height
            ),
            // Output 2: Buyback box (contract requires selfOut.tokens == SELF.tokens)
            mapOf(
                "address" to UseConfig.BUYBACK_ADDRESS,
                "value" to state.buybackNanoErg + buybackErgsToAdd,
                "assets" to listOf(
                    mapOf("tokenId" to UseConfig.BUYBACK_NFT, "amount" to 1L),
                    mapOf("tokenId" to UseConfig.BUYBACK_REWARD_NFT, "amount" to state.buybackDortAmount)
                ),
                "registers" to mapOf("R4" to "0e20${state.buybackBoxId}"),
                "creationHeight" to height
            ),
            // Output 3: User change (receives minted USE, returns leftover ERG + ALL user tokens)
            run {
                // Collect all tokens from user's input boxes to carry them through
                val userTokens = mutableMapOf<String, Long>()
                for (box in state.userBoxMaps) {
                    val assets = (box["assets"] as? List<Map<String, Any>>) ?: continue
                    for (asset in assets) {
                        val tid = asset["tokenId"] as? String ?: continue
                        val amt = (asset["amount"] as? Number)?.toLong() ?: continue
                        userTokens[tid] = (userTokens[tid] ?: 0L) + amt
                    }
                }
                // Add minted USE tokens (user receives these from the bank)
                userTokens[UseConfig.USE_TOKEN_ID] = (userTokens[UseConfig.USE_TOKEN_ID] ?: 0L) + amountUseInt

                val userChangeAssets = userTokens.map { (tid, amt) ->
                    mapOf("tokenId" to tid, "amount" to amt)
                }

                mapOf(
                    "address" to senderAddress,
                    "value" to userChangeValue,
                    "assets" to userChangeAssets,
                    "registers" to emptyMap<String, Any>(),
                    "creationHeight" to height
                )
            }
        )

        // UTXO dust consolidation output
        val sinkAddr = ProtocolConfig.consolidationSink()
        if (utxoDust > 0L && sinkAddr.isNotEmpty()) {
            requestsList.add(mapOf(
                "address" to sinkAddr,
                "value" to utxoDust,
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, Any>()
            ))
        }

        val requests = requestsList.toList()

        val inputBoxes = listOf(state.freemintBoxMap, state.bankBoxMap, state.buybackBoxMap) + state.userBoxMaps

        return mapOf(
            "requests" to requests,
            "fee" to miningFee,
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to dataInputIds,
            "input_boxes" to inputBoxes,
            "data_input_boxes" to listOf(state.oracleBoxMap, state.lpBoxMap),
            "context_extensions" to mapOf("2" to mapOf("0" to "0402")),
            "current_height" to height
        )
    }

    // ─── postProcessUnsignedTx ───────────────────────────────────────────────

    /**
     * Injects the {"0": "0402"} extension on the buyback box input.
     * This is required by the DORT buyback ErgoScript contract.
     */
    @Suppress("UNCHECKED_CAST")
    override fun postProcessUnsignedTx(
        unsignedTxDict: MutableMap<String, Any>
    ): Map<String, Any> {
        val inputs = unsignedTxDict["inputs"] as? List<MutableMap<String, Any>> ?: return unsignedTxDict
        for (input in inputs) {
            val boxId = input["boxId"] as? String ?: continue
            // We stored the buyback box ID in the context; match by NFT presence or simple scan
            // The buyback box holds BUYBACK_NFT – after tx generation the input list is ordered
            // by how we placed them; buyback is index 2. Matching by boxId is the safest approach.
            if (boxId.length == 64) { // valid ErgoBox ID length
                val assets = input["assets"] as? List<Map<String, Any>> ?: continue
                val hasBuybackNft = assets.any { (it["tokenId"] as? String) == UseConfig.BUYBACK_NFT }
                if (hasBuybackNft) {
                    input["extension"] = mapOf("0" to "0402")
                    break
                }
            }
        }
        return unsignedTxDict
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private data class ProtocolState(
        val oracleRate: Long,
        val freemintBoxId: String,
        val freemintNanoErg: Long,
        val resetHeight: Long,
        val availableToMint: Long,
        val bankBoxId: String,
        val bankNanoErg: Long,
        val bankUseAmount: Long,
        val oracleBoxId: String,
        val lpBoxId: String,
        val lpNanoErg: Long,
        val lpUseAmount: Long,
        val buybackBoxId: String,
        val buybackNanoErg: Long,
        val buybackDortAmount: Long,
        val userNanoErg: Long,
        val userBoxIds: List<String>,
        val freemintBoxMap: Map<String, Any>,
        val bankBoxMap: Map<String, Any>,
        val buybackBoxMap: Map<String, Any>,
        val oracleBoxMap: Map<String, Any>,
        val lpBoxMap: Map<String, Any>,
        val userBoxMaps: List<Map<String, Any>>
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchProtocolState(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): ProtocolState {
        // Freemint box
        val freemintBox = client.getPoolBox(UseConfig.FREEMINT_NFT, checkMempool)
            ?: throw Exception("Freemint box not found")
        val freemintBoxId = freemintBox["boxId"] as? String ?: ""
        val freemintNanoErg = (freemintBox["value"] as? Number)?.toLong() ?: 0L
        val freemintRegisters = freemintBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val resetHeightVlq = freemintRegisters["R4"] as? String ?: "04"
        val availableToMintVlq = freemintRegisters["R5"] as? String ?: "0500"
        val resetHeight = VlqCodec.decode(resetHeightVlq)
        val availableToMint = VlqCodec.decode(availableToMintVlq)

        // Bank box
        val bankBox = client.getPoolBox(UseConfig.BANK_NFT, checkMempool)
            ?: throw Exception("Bank box not found")
        val bankBoxId = bankBox["boxId"] as? String ?: ""
        val bankNanoErg = (bankBox["value"] as? Number)?.toLong() ?: 0L
        val bankAssets = bankBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val bankUseAmount = bankAssets.firstOrNull { it["tokenId"] == UseConfig.USE_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L

        // Oracle box (data input)
        val oracleBox = client.getPoolBox(UseConfig.ORACLE_NFT, checkMempool)
            ?: throw Exception("Oracle box not found")
        val oracleBoxId = oracleBox["boxId"] as? String ?: ""
        val oracleRegisters = oracleBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val oracleRateVlq = oracleRegisters["R4"] as? String ?: "0500"
        val oracleRate = VlqCodec.decode(oracleRateVlq)

        // LP box (data input, needed for reset calculation)
        val lpBox = client.getPoolBox(UseConfig.LP_TOKEN_ID, checkMempool)
            ?: throw Exception("LP box not found")
        val lpBoxId = lpBox["boxId"] as? String ?: ""
        val lpNanoErg = (lpBox["value"] as? Number)?.toLong() ?: 0L
        val lpAssets = lpBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val lpUseAmount = lpAssets.firstOrNull { it["tokenId"] == UseConfig.USE_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L

        // Buyback box (input)
        val buybackBox = client.getPoolBox(UseConfig.BUYBACK_NFT, checkMempool)
            ?: throw Exception("Buyback box not found")
        val buybackBoxId = buybackBox["boxId"] as? String ?: ""
        val buybackNanoErg = (buybackBox["value"] as? Number)?.toLong() ?: 0L
        val buybackAssets = buybackBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val buybackDortAmount = buybackAssets.firstOrNull { it["tokenId"] == UseConfig.BUYBACK_REWARD_NFT }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 1L

        // User boxes — extract string IDs from the box maps
        val (_, userNanoErg, userBoxMaps) = client.getMyAssets(senderAddress, checkMempool)
        val userBoxIds = userBoxMaps.mapNotNull { it["boxId"] as? String }

        return ProtocolState(
            oracleRate = oracleRate,
            freemintBoxId = freemintBoxId,
            freemintNanoErg = freemintNanoErg,
            resetHeight = resetHeight,
            availableToMint = availableToMint,
            bankBoxId = bankBoxId,
            bankNanoErg = bankNanoErg,
            bankUseAmount = bankUseAmount,
            oracleBoxId = oracleBoxId,
            lpBoxId = lpBoxId,
            lpNanoErg = lpNanoErg,
            lpUseAmount = lpUseAmount,
            buybackBoxId = buybackBoxId,
            buybackNanoErg = buybackNanoErg,
            buybackDortAmount = buybackDortAmount,
            userNanoErg = userNanoErg,
            userBoxIds = userBoxIds,
            freemintBoxMap = freemintBox,
            bankBoxMap = bankBox,
            buybackBoxMap = buybackBox,
            oracleBoxMap = oracleBox,
            lpBoxMap = lpBox,
            userBoxMaps = userBoxMaps
        )
    }

    /**
     * Resolve current available capacity, accounting for the cycle reset.
     * Returns Pair(available: Long, effectiveResetHeight: Int)
     */
    private fun resolveCapacity(state: ProtocolState, currentHeight: Int): Pair<Long, Int> {
        return if (currentHeight > state.resetHeight) {
            // Cycle reset: 1% of LP USE balance becomes the new limit
            val maxMint = state.lpUseAmount / 100
            maxMint to (currentHeight + UseConfig.FREEMINT_CYCLE_BLOCKS)
        } else {
            state.availableToMint to state.resetHeight.toInt()
        }
    }

    /**
     * Build VLQ-encoded register values for the outgoing Freemint box.
     * Returns Pair(resetHeightVlq, newAvailableVlq)
     */
    private fun buildRegisterValues(
        state: ProtocolState,
        currentHeight: Int,
        amountUseInt: Long
    ): Pair<String, String> {
        val newResetHeight: Int
        val newAvailable: Long

        if (currentHeight > state.resetHeight) {
            newResetHeight = currentHeight + UseConfig.FREEMINT_CYCLE_BLOCKS
            val maxMint = state.lpUseAmount / 100
            newAvailable = maxMint - amountUseInt
        } else {
            newResetHeight = state.resetHeight.toInt()
            newAvailable = state.availableToMint - amountUseInt
        }

        return VlqCodec.encode(newResetHeight.toLong(), "04") to
               VlqCodec.encode(newAvailable, "05")
    }

    /**
     * Calculate buyback and bank fees.
     * Mirrors: buyback_fee = ((rate * 2) // 1000) * amount // 1000
     *          bank_fee    = ((rate * 3) // 1000) * amount // 1000
     */
    private fun calculateFees(oracleRate: Long, amountUseInt: Long): Pair<Long, Long> {
        val buybackFee = ((oracleRate * UseConfig.BUYBACK_FEE_NUM) / UseConfig.FEE_DENOM) * amountUseInt / UseConfig.FEE_DENOM
        val bankFee    = ((oracleRate * UseConfig.BANK_FEE_NUM)    / UseConfig.FEE_DENOM) * amountUseInt / UseConfig.FEE_DENOM
        return buybackFee to bankFee
    }
}
