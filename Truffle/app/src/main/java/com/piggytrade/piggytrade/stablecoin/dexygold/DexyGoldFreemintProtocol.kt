package com.piggytrade.piggytrade.stablecoin.dexygold

import android.util.Log
import com.piggytrade.piggytrade.BuildConfig
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.stablecoin.EligibilityResult
import com.piggytrade.piggytrade.stablecoin.MintQuote
import com.piggytrade.piggytrade.stablecoin.StatusField
import com.piggytrade.piggytrade.stablecoin.StablecoinProtocol
import com.piggytrade.piggytrade.stablecoin.VlqCodec
import com.piggytrade.piggytrade.blockchain.ErgoSigner
import com.piggytrade.piggytrade.protocol.ProtocolConfig

/**
 * DexyGold Freemint Protocol implementation.
 *
 * Same contract structure as USE Freemint but with different parameters:
 *  - Oracle divisor: 1_000_000 (vs USE's 1000)
 *  - Token decimals: 0 (vs USE's 3)
 *  - Freemint cycle: 362 blocks (vs USE's 364)
 *  - Different token IDs and contract addresses
 */
class DexyGoldFreemintProtocol : StablecoinProtocol {

    override val id = "dexygold_freemint"
    override val displayName = "Freemint"
    override val mintTokenId = DexyGoldConfig.DEXY_TOKEN_ID
    override val mintTokenName = "DexyGold"
    override val mintTokenDecimals = 0

    private val TAG = "DexyGoldFreemint"

    // ─── EligibilityResult ────────────────────────────────────────────────────

    override suspend fun checkEligibility(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): EligibilityResult {
        return try {
            val state = fetchProtocolState(client, setOf(senderAddress), checkMempool)
            val height = client.getHeight()

            val isReset = height > state.resetHeight
            val lpRate = if (state.lpDexyAmount > 0) state.lpNanoErg / state.lpDexyAmount else 0L
            val contractOracleRate = state.oracleRate / DexyGoldConfig.ORACLE_DIVISOR
            val validRate = lpRate * 100 > contractOracleRate * 98
            val rateRatioPct = if (contractOracleRate > 0) "%.1f".format(lpRate.toDouble() / contractOracleRate.toDouble() * 100) else "?"

            val availableNow = if (isReset) {
                state.lpDexyAmount / 100
            } else {
                state.availableToMint
            }

            val canMint = validRate && availableNow > 0
            val reason = when {
                !validRate -> "LP rate is ${rateRatioPct}% of oracle (needs ≥98%)"
                availableNow <= 0 -> "No freemint capacity"
                else -> null
            }

            val ergPerDexy = contractOracleRate.toDouble() / 1_000_000_000.0
            val dexyPerErg = if (ergPerDexy > 0) 1.0 / ergPerDexy else 0.0
            val lpErgPerDexy = if (state.lpDexyAmount > 0) state.lpNanoErg.toDouble() / state.lpDexyAmount.toDouble() / 1_000_000_000.0 else 0.0
            val dexyPerErgLp = if (lpErgPerDexy > 0) 1.0 / lpErgPerDexy else 0.0

            val fields = buildList {
                add(StatusField(
                    "Available to mint", "%.0f DexyGold".format(availableNow.toDouble() / DexyGoldConfig.DEXY_DECIMALS),
                    if (availableNow > 0) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField("Oracle price", "%.4f DexyGold / ERG".format(dexyPerErg)))
                add(StatusField("LP price", "%.4f DexyGold / ERG".format(dexyPerErgLp)))
                add(StatusField(
                    "Rate check (≥98%)", if (validRate) "Passed ✓ ($rateRatioPct%)" else "Failed ($rateRatioPct% < 98%)",
                    if (validRate) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField(
                    "Cycle reset",
                    if (isReset) "Ready ✓" else "Resets at block ${state.resetHeight}",
                    if (isReset) StatusField.Status.OK else StatusField.Status.NEUTRAL
                ))
            }

            EligibilityResult(
                canMint = canMint,
                reason = reason,
                availableCapacity = availableNow,
                statusFields = fields
            )
        } catch (e: Exception) {
            val detail = if (e is retrofit2.HttpException) {
                val body = e.response()?.errorBody()?.string() ?: ""
                "HTTP ${e.code()} — $body"
            } else e.message ?: "Unknown error"
            Log.e(TAG, "checkEligibility failed: $detail", e)
            EligibilityResult(
                canMint = false,
                reason = "Protocol state error: $detail",
                statusFields = listOf(StatusField("Error", detail, StatusField.Status.ERROR))
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
        val state = fetchProtocolState(client, setOf(senderAddress), checkMempool)
        val amountDexyInt = (amount * DexyGoldConfig.DEXY_DECIMALS).toLong()

        val oracleRate = state.oracleRate / DexyGoldConfig.ORACLE_DIVISOR
        val bankErgs = amountDexyInt * oracleRate * (DexyGoldConfig.BANK_FEE_NUM + DexyGoldConfig.FEE_DENOM) / DexyGoldConfig.FEE_DENOM
        val buybackErgs = amountDexyInt * oracleRate * DexyGoldConfig.BUYBACK_FEE_NUM / DexyGoldConfig.FEE_DENOM

        val signer = ErgoSigner("")
        val utxoDust = signer.calculateAppFeeStablecoin(bankErgs).toLong()

        val baseCost = amountDexyInt * oracleRate
        val bankFeeDisplay = bankErgs - baseCost
        val buybackFeeDisplay = buybackErgs

        val breakdown = buildList {
            add("Bank Fee (0.3%)" to bankFeeDisplay)
            add("Buyback Fee (0.2%)" to buybackFeeDisplay)
            if (utxoDust > 0L) add("App Fee" to utxoDust)
        }

        return MintQuote(
            tokenReceived = mintTokenName,
            amountReceived = amountDexyInt,
            tokenDecimals = mintTokenDecimals,
            ergCost = bankErgs + buybackErgs + utxoDust,
            feeBreakdown = breakdown,
            miningFee = DexyGoldConfig.DEFAULT_MINING_FEE_NANO
        )
    }

    // ─── buildTransaction ─────────────────────────────────────────────────────

    override suspend fun buildTransaction(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        miningFee: Long,
        checkMempool: Boolean,
        changeAddress: String,
        userAddresses: Set<String>
    ): Map<String, Any> {
        val addrs = if (userAddresses.isNotEmpty()) userAddresses else setOf(senderAddress)
        val state = fetchProtocolState(client, addrs, checkMempool)
        val amountDexyInt = (amount * DexyGoldConfig.DEXY_DECIMALS).toLong()

        // Height fix: use max(fullHeight, lastHeaders[0]) + embed headers for ViewModel patching
        val fullHeight = client.getHeight()
        val lastHeaders = client.api.getLastHeaders(10)
        val lastHeaderHeight = (lastHeaders.firstOrNull()?.get("height") as? Number)?.toInt() ?: 0
        val height = maxOf(fullHeight, lastHeaderHeight)
        if (BuildConfig.DEBUG) Log.d(TAG, "buildTransaction: fullHeight=$fullHeight, lastHeaders[0]=$lastHeaderHeight, using=$height")

        // ── Pre-flight contract validation ────────────────────────────────────
        val contractOracleRate = state.oracleRate / DexyGoldConfig.ORACLE_DIVISOR
        val lpRate = if (state.lpDexyAmount > 0) state.lpNanoErg / state.lpDexyAmount else 0L
        if (lpRate * 100 <= contractOracleRate * 98) {
            throw Exception(
                "Freemint blocked: LP rate ($lpRate) has deviated >2% from oracle rate ($contractOracleRate). " +
                "LP rate * 100 = ${lpRate * 100}, oracle rate * 98 = ${contractOracleRate * 98}. Try again later."
            )
        }

        // ── Fees ──────────────────────────────────────────────────────────────
        val oracleRate = contractOracleRate
        val baseBankErgs = amountDexyInt * oracleRate * (DexyGoldConfig.BANK_FEE_NUM + DexyGoldConfig.FEE_DENOM) / DexyGoldConfig.FEE_DENOM
        val bankErgsToAdd = baseBankErgs + baseBankErgs / 1000   // +0.1% buffer
        val baseBuybackErgs = amountDexyInt * oracleRate * DexyGoldConfig.BUYBACK_FEE_NUM / DexyGoldConfig.FEE_DENOM
        val buybackErgsToAdd = baseBuybackErgs + baseBuybackErgs / 1000  // +0.1% buffer

        val signer = ErgoSigner("")
        val utxoDust = signer.calculateAppFeeStablecoin(bankErgsToAdd).toLong()

        // ── Reset logic ───────────────────────────────────────────────────────
        val (_, resetHeight) = resolveCapacity(state, height)
        val (resetHeightVlq, newAvailableVlq) = buildRegisterValues(state, height, amountDexyInt)

        // ── Fetch box bytes ───────────────────────────────────────────────────
        val inputBoxIds = listOf(state.freemintBoxId, state.bankBoxId, state.buybackBoxId) + state.userBoxIds
        val dataInputIds = listOf(state.oracleBoxId, state.lpBoxId)

        val inputsRaw = client.getBoxBytes(inputBoxIds)
        val dataInputsRaw = client.getBoxBytes(dataInputIds)

        // Total ERG the user needs to spend
        val userChangeValue = state.userNanoErg - bankErgsToAdd - buybackErgsToAdd - miningFee - utxoDust
        if (userChangeValue < 0) {
            throw Exception("Insufficient ERG: need ${(bankErgsToAdd + buybackErgsToAdd + miningFee + utxoDust) / 1_000_000_000.0} ERG, have ${state.userNanoErg / 1_000_000_000.0} ERG")
        }

        // ── Build the requests list ───────────────────────────────────────────
        val requestsList = mutableListOf(
            // Output 0: Freemint box (preserved, updated registers)
            mapOf(
                "address" to DexyGoldConfig.FREEMINT_ADDRESS,
                "value" to state.freemintNanoErg,
                "assets" to listOf(mapOf("tokenId" to DexyGoldConfig.FREEMINT_NFT, "amount" to 1L)),
                "registers" to mapOf("R4" to resetHeightVlq, "R5" to newAvailableVlq),
                "creationHeight" to height
            ),
            // Output 1: Bank box
            mapOf(
                "address" to DexyGoldConfig.BANK_ADDRESS,
                "value" to state.bankNanoErg + bankErgsToAdd,
                "assets" to listOf(
                    mapOf("tokenId" to DexyGoldConfig.BANK_NFT, "amount" to 1L),
                    mapOf("tokenId" to DexyGoldConfig.DEXY_TOKEN_ID, "amount" to state.bankDexyAmount - amountDexyInt)
                ),
                "registers" to emptyMap<String, Any>(),
                "creationHeight" to height
            ),
            // Output 2: Buyback box
            mapOf(
                "address" to DexyGoldConfig.BUYBACK_ADDRESS,
                "value" to state.buybackNanoErg + buybackErgsToAdd,
                "assets" to listOf(
                    mapOf("tokenId" to DexyGoldConfig.BUYBACK_NFT, "amount" to 1L),
                    mapOf("tokenId" to DexyGoldConfig.BUYBACK_REWARD_NFT, "amount" to state.buybackDortAmount)
                ),
                "registers" to mapOf("R4" to "0e20${state.buybackBoxId}"),
                "creationHeight" to height
            ),
            // Output 3: User change
            run {
                val userTokens = mutableMapOf<String, Long>()
                for (box in state.userBoxMaps) {
                    val assets = (box["assets"] as? List<Map<String, Any>>) ?: continue
                    for (asset in assets) {
                        val tid = asset["tokenId"] as? String ?: continue
                        val amt = (asset["amount"] as? Number)?.toLong() ?: continue
                        userTokens[tid] = (userTokens[tid] ?: 0L) + amt
                    }
                }
                userTokens[DexyGoldConfig.DEXY_TOKEN_ID] = (userTokens[DexyGoldConfig.DEXY_TOKEN_ID] ?: 0L) + amountDexyInt

                val userChangeAssets = userTokens.map { (tid, amt) ->
                    mapOf("tokenId" to tid, "amount" to amt)
                }

                mapOf(
                    "address" to changeAddress,
                    "value" to userChangeValue,
                    "assets" to userChangeAssets,
                    "registers" to emptyMap<String, Any>(),
                    "creationHeight" to height
                )
            }
        )

        // UTXO dust consolidation output
        val sinkAddr = ProtocolConfig.appFeeAddress()
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
            "current_height" to height,
            "_buildHeight" to height,
            "_headersJson" to com.google.gson.Gson().toJson(lastHeaders)
        )
    }

    // ─── postProcessUnsignedTx ───────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override fun postProcessUnsignedTx(
        unsignedTxDict: MutableMap<String, Any>
    ): Map<String, Any> {
        val inputs = unsignedTxDict["inputs"] as? List<MutableMap<String, Any>> ?: return unsignedTxDict
        for (input in inputs) {
            val boxId = input["boxId"] as? String ?: continue
            if (boxId.length == 64) {
                val assets = input["assets"] as? List<Map<String, Any>> ?: continue
                val hasBuybackNft = assets.any { (it["tokenId"] as? String) == DexyGoldConfig.BUYBACK_NFT }
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
        val bankDexyAmount: Long,
        val oracleBoxId: String,
        val lpBoxId: String,
        val lpNanoErg: Long,
        val lpDexyAmount: Long,
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

    private fun resolveCapacity(state: ProtocolState, height: Int): Pair<Long, Long> {
        val isReset = height > state.resetHeight
        val availableNow = if (isReset) {
            state.lpDexyAmount / 100
        } else {
            state.availableToMint
        }
        val newResetHeight = if (isReset) (height + DexyGoldConfig.FREEMINT_CYCLE_BLOCKS).toLong() else state.resetHeight
        return Pair(availableNow, newResetHeight)
    }

    private fun buildRegisterValues(state: ProtocolState, height: Int, amountDexyInt: Long): Pair<String, String> {
        val isReset = height > state.resetHeight
        val newResetVal = if (isReset) height + DexyGoldConfig.FREEMINT_CYCLE_BLOCKS else state.resetHeight.toInt()
        val availableNow = if (isReset) state.lpDexyAmount / 100 else state.availableToMint
        val newAvailable = (availableNow - amountDexyInt).coerceAtLeast(0L)

        val resetHeightVlq = VlqCodec.encode(newResetVal.toLong(), "04")
        val newAvailableVlq = VlqCodec.encode(newAvailable, "05")
        return Pair(resetHeightVlq, newAvailableVlq)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchProtocolState(
        client: NodeClient,
        addresses: Set<String>,
        checkMempool: Boolean
    ): ProtocolState {
        // Freemint box
        val freemintBox = client.getPoolBox(DexyGoldConfig.FREEMINT_NFT, checkMempool)
            ?: throw Exception("DexyGold Freemint box not found")
        val freemintBoxId = freemintBox["boxId"] as? String ?: ""
        val freemintNanoErg = (freemintBox["value"] as? Number)?.toLong() ?: 0L
        val freemintRegisters = freemintBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val resetHeightVlq = freemintRegisters["R4"] as? String ?: "04"
        val availableToMintVlq = freemintRegisters["R5"] as? String ?: "0500"
        val resetHeight = VlqCodec.decode(resetHeightVlq)
        val availableToMint = VlqCodec.decode(availableToMintVlq)

        // Bank box
        val bankBox = client.getPoolBox(DexyGoldConfig.BANK_NFT, checkMempool, DexyGoldConfig.BANK_ADDRESS)
            ?: throw Exception("DexyGold Bank box not found")
        val bankBoxId = bankBox["boxId"] as? String ?: ""
        val bankNanoErg = (bankBox["value"] as? Number)?.toLong() ?: 0L
        val bankAssets = bankBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val bankDexyAmount = bankAssets.firstOrNull { it["tokenId"] == DexyGoldConfig.DEXY_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L

        // Oracle box
        val oracleBox = client.getPoolBox(DexyGoldConfig.ORACLE_NFT, checkMempool)
            ?: throw Exception("DexyGold Oracle box not found")
        val oracleBoxId = oracleBox["boxId"] as? String ?: ""
        val oracleRegisters = oracleBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val oracleRateVlq = oracleRegisters["R4"] as? String ?: "0500"
        val oracleRate = VlqCodec.decode(oracleRateVlq)

        // LP box
        val lpBox = client.getPoolBox(DexyGoldConfig.LP_TOKEN_ID, checkMempool)
            ?: throw Exception("DexyGold LP box not found")
        val lpBoxId = lpBox["boxId"] as? String ?: ""
        val lpNanoErg = (lpBox["value"] as? Number)?.toLong() ?: 0L
        val lpAssets = lpBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val lpDexyAmount = lpAssets.firstOrNull { it["tokenId"] == DexyGoldConfig.DEXY_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L

        // Buyback box
        val buybackBox = client.getPoolBox(DexyGoldConfig.BUYBACK_NFT, checkMempool, DexyGoldConfig.BUYBACK_ADDRESS)
            ?: throw Exception("DexyGold Buyback box not found")
        val buybackBoxId = buybackBox["boxId"] as? String ?: ""
        val buybackNanoErg = (buybackBox["value"] as? Number)?.toLong() ?: 0L
        val buybackAssets = buybackBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val buybackDortAmount = buybackAssets.firstOrNull { it["tokenId"] == DexyGoldConfig.BUYBACK_REWARD_NFT }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 1L

        // User boxes — fetch from all selected addresses
        var userNanoErg = 0L
        val userBoxMaps = mutableListOf<Map<String, Any>>()
        for (addr in addresses) {
            val (_, n, boxes) = client.getMyAssets(addr, checkMempool)
            userNanoErg += n
            userBoxMaps.addAll(boxes)
        }
        val userBoxIds = userBoxMaps.mapNotNull { it["boxId"] as? String }

        return ProtocolState(
            oracleRate = oracleRate,
            freemintBoxId = freemintBoxId,
            freemintNanoErg = freemintNanoErg,
            resetHeight = resetHeight,
            availableToMint = availableToMint,
            bankBoxId = bankBoxId,
            bankNanoErg = bankNanoErg,
            bankDexyAmount = bankDexyAmount,
            oracleBoxId = oracleBoxId,
            lpBoxId = lpBoxId,
            lpNanoErg = lpNanoErg,
            lpDexyAmount = lpDexyAmount,
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
}
