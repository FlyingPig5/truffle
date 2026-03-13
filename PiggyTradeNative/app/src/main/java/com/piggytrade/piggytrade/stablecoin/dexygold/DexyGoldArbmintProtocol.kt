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
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * DexyGold Arbitrage Mint Protocol implementation.
 *
 * Same contract structure as USE Arbmint but with different parameters:
 *  - Oracle divisor: 1_000_000 (vs USE's 1000)
 *  - Token decimals: 0 (vs USE's 3)
 *  - Different token IDs and contract addresses
 */
class DexyGoldArbmintProtocol : StablecoinProtocol {

    override val id = "dexygold_arbmint"
    override val displayName = "Arbmint"
    override val mintTokenId = DexyGoldConfig.DEXY_TOKEN_ID
    override val mintTokenName = "DexyGold"
    override val mintTokenDecimals = 0

    private val TAG = "DexyGoldArbmint"

    // ─── EligibilityResult ────────────────────────────────────────────────────

    override suspend fun checkEligibility(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): EligibilityResult {
        return try {
            val state = fetchProtocolState(client, senderAddress, checkMempool)
            val height = client.getHeight()

            val rates = calculateRates(state.oracleRateWhole)
            val lpRate = if (state.lpDexyAmount > 0)
                BigDecimal(state.lpReservesErg).divide(BigDecimal(state.lpDexyAmount), 18, RoundingMode.HALF_UP)
            else BigDecimal.ZERO

            val thresholdTarget = BigDecimal(DexyGoldConfig.THRESHOLD_PERCENT)
                .multiply(BigDecimal(rates.rateWithFee))
                .divide(BigDecimal(100), 18, RoundingMode.HALF_UP)
            val isThresholdMet = lpRate > thresholdTarget

            val isReset = height > state.resetHeight
            val blocksSinceTracking = height - state.trackingHeight
            val isDelayMet = blocksSinceTracking > DexyGoldConfig.T_ARB
            val blocksUntilReady = if (isDelayMet) 0 else (DexyGoldConfig.T_ARB - blocksSinceTracking + 1)

            val availableNow = if (isReset) {
                val raw = if (rates.rateWithFee > 0)
                    (state.lpReservesErg - rates.rateWithFee * state.lpDexyAmount) / rates.rateWithFee
                else 0L
                raw.coerceAtLeast(0L)
            } else {
                state.availableToMint.coerceAtLeast(0L)
            }

            // Oracle price: oracleRateWhole = raw R4
            // ERG per 1 DexyGold = R4 / 1e9  (since DEXY_DECIMALS=1, no scaling needed)
            val ergPerDexy = state.oracleRateWhole.toDouble() / 1_000_000_000.0
            val dexyPerErg = if (ergPerDexy > 0) 1.0 / ergPerDexy else 0.0

            // LP price: lpRate is BigDecimal (nanoERG per raw unit)
            // ERG per 1 DexyGold = lpRate * DEXY_DECIMALS / 1e9
            val lpErgPerDexy = lpRate.toDouble() * DexyGoldConfig.DEXY_DECIMALS / 1_000_000_000.0
            val dexyPerErgLp = if (lpErgPerDexy > 0) 1.0 / lpErgPerDexy else 0.0
            val lpPriceDisplay = "%.4f DexyGold / ERG".format(dexyPerErgLp)
            val thresholdDisplay = "%.8f ERG".format(thresholdTarget.toDouble())

            val canMint = isThresholdMet && isDelayMet && availableNow > 0
            val reason = when {
                !isThresholdMet -> "LP price below required threshold"
                !isDelayMet -> "Wait $blocksUntilReady more blocks"
                availableNow <= 0 -> "No arbitrage capacity available"
                else -> null
            }

            val fields = buildList {
                add(StatusField(
                    "Available to mint", "%.0f DexyGold".format(availableNow.toDouble() / DexyGoldConfig.DEXY_DECIMALS),
                    if (availableNow > 0) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField("Oracle price", "%.4f DexyGold / ERG".format(dexyPerErg)))
                add(StatusField("LP price", lpPriceDisplay))
                add(StatusField(
                    "Threshold (101%)", if (isThresholdMet) "Met ✓" else "Not met",
                    if (isThresholdMet) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField(
                    "Cycle reset",
                    if (isDelayMet) "Ready ✓" else "$blocksUntilReady blocks remaining",
                    if (isDelayMet) StatusField.Status.OK else StatusField.Status.WARNING
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
        val state = fetchProtocolState(client, senderAddress, checkMempool)
        val amountDexyInt = (amount * DexyGoldConfig.DEXY_DECIMALS).toLong()
        val rates = calculateRates(state.oracleRateWhole)

        val oracleUnit = rates.oracleRateUnit
        val buybackFee = amountDexyInt * oracleUnit * DexyGoldConfig.BUYBACK_FEE_NUM / DexyGoldConfig.FEE_DENOM
        val bankFee    = amountDexyInt * oracleUnit * (DexyGoldConfig.BANK_FEE_NUM + DexyGoldConfig.FEE_DENOM) / DexyGoldConfig.FEE_DENOM
        val ergToSpend = buybackFee + bankFee

        val signer = ErgoSigner("")
        val utxoDust = signer.resolveUtxoGap(ergToSpend).toLong()

        val breakdown = buildList {
            add("Bank Fee (rateWithFee × amount)" to bankFee)
            add("Buyback Fee (buybackRate × amount)" to buybackFee)
            if (utxoDust > 0L) add("App Fee" to utxoDust)
        }

        return MintQuote(
            tokenReceived = mintTokenName,
            amountReceived = amountDexyInt,
            tokenDecimals = mintTokenDecimals,
            ergCost = ergToSpend + utxoDust,
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
        checkMempool: Boolean
    ): Map<String, Any> {
        val state = fetchProtocolState(client, senderAddress, checkMempool)
        val amountDexyInt = (amount * DexyGoldConfig.DEXY_DECIMALS).toLong()

        // Height fix: use max(fullHeight, lastHeaders[0]) + embed headers for ViewModel patching
        val fullHeight = client.getHeight()
        val lastHeaders = client.api.getLastHeaders(10)
        val lastHeaderHeight = (lastHeaders.firstOrNull()?.get("height") as? Number)?.toInt() ?: 0
        val height = maxOf(fullHeight, lastHeaderHeight)
        if (BuildConfig.DEBUG) Log.d(TAG, "buildTransaction: fullHeight=$fullHeight, lastHeaders[0]=$lastHeaderHeight, using=$height")

        val rates = calculateRates(state.oracleRateWhole)

        // ── Fees ──────────────────────────────────────────────────────────────
        val oracleUnit = rates.oracleRateUnit
        val baseBuybackFee = amountDexyInt * oracleUnit * DexyGoldConfig.BUYBACK_FEE_NUM / DexyGoldConfig.FEE_DENOM
        val buybackFee = baseBuybackFee + baseBuybackFee / 1000
        val baseBankFee = amountDexyInt * oracleUnit * (DexyGoldConfig.BANK_FEE_NUM + DexyGoldConfig.FEE_DENOM) / DexyGoldConfig.FEE_DENOM
        val bankFee    = baseBankFee + baseBankFee / 1000
        val ergToSpend = buybackFee + bankFee

        val signer = ErgoSigner("")
        val utxoDust = signer.resolveUtxoGap(ergToSpend).toLong()

        // ── Reset / register values ───────────────────────────────────────────
        val isReset = height > state.resetHeight
        val newResetVal = if (isReset) height + DexyGoldConfig.T_ARB else state.resetHeight.toInt()
        val availableNow = if (isReset) {
            if (rates.rateWithFee > 0)
                (state.lpReservesErg - rates.rateWithFee * state.lpDexyAmount) / rates.rateWithFee
            else 0L
        } else {
            state.availableToMint
        }
        val newAvailable = (availableNow - amountDexyInt).coerceAtLeast(0L)

        val resetHeightVlq  = VlqCodec.encode(newResetVal.toLong(), "04")
        val newAvailableVlq = VlqCodec.encode(newAvailable, "05")

        // ── Box bytes ─────────────────────────────────────────────────────────
        val inputBoxIds = listOf(state.arbBoxId, state.bankBoxId, state.buybackBoxId) + state.userBoxIds
        val dataInputIds = listOf(state.oracleBoxId, state.lpBoxId, state.trackerBoxId)
        val inputsRaw    = client.getBoxBytes(inputBoxIds)
        val dataInputsRaw = client.getBoxBytes(dataInputIds)

        // ── User change ───────────────────────────────────────────────────────
        val userChangeValue = state.userNanoErg - ergToSpend - miningFee - utxoDust
        if (userChangeValue < 0) {
            throw Exception("Insufficient ERG: need ${(ergToSpend + miningFee + utxoDust) / 1_000_000_000.0} ERG, have ${state.userNanoErg / 1_000_000_000.0} ERG")
        }

        // ── Requests ──────────────────────────────────────────────────────────
        val requestsList = mutableListOf(
            // Output 0: Arbmint box (preserved, updated registers)
            mapOf(
                "address" to DexyGoldConfig.ARBMINT_ADDRESS,
                "value" to DexyGoldConfig.ARBMINT_BOX_VALUE_NANO,
                "assets" to listOf(mapOf("tokenId" to DexyGoldConfig.ARBMINT_NFT, "amount" to 1L)),
                "registers" to mapOf("R4" to resetHeightVlq, "R5" to newAvailableVlq),
                "creationHeight" to height
            ),
            // Output 1: Bank box — NOTE: no erg_to_spend added here (different from Freemint!)
            mapOf(
                "address" to DexyGoldConfig.BANK_ADDRESS,
                "value" to state.bankNanoErg + bankFee,
                "assets" to listOf(
                    mapOf("tokenId" to DexyGoldConfig.BANK_NFT, "amount" to 1L),
                    mapOf("tokenId" to DexyGoldConfig.DEXY_TOKEN_ID, "amount" to state.bankDexyAmount - amountDexyInt)
                ),
                "registers" to emptyMap<String, Any>(),
                "creationHeight" to height
            ),
            // Output 2: Buyback box (preserves ALL DORT tokens)
            mapOf(
                "address" to DexyGoldConfig.BUYBACK_ADDRESS,
                "value" to state.buybackNanoErg + buybackFee,
                "assets" to listOf(
                    mapOf("tokenId" to DexyGoldConfig.BUYBACK_NFT, "amount" to 1L),
                    mapOf("tokenId" to DexyGoldConfig.BUYBACK_REWARD_NFT, "amount" to state.buybackDortAmount)
                ),
                "registers" to mapOf("R4" to "0e20${state.buybackBoxId}"),
                "creationHeight" to height
            ),
            // Output 3: User change (receives minted DexyGold + ALL existing user tokens)
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
        val inputBoxes = listOf(state.arbBoxMap, state.bankBoxMap, state.buybackBoxMap) + state.userBoxMaps

        return mapOf(
            "requests" to requests,
            "fee" to miningFee,
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to dataInputIds,
            "input_boxes" to inputBoxes,
            "data_input_boxes" to listOf(state.oracleBoxMap, state.lpBoxMap, state.trackerBoxMap),
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
            val assets = input["assets"] as? List<Map<String, Any>> ?: continue
            val hasBuybackNft = assets.any { (it["tokenId"] as? String) == DexyGoldConfig.BUYBACK_NFT }
            if (hasBuybackNft) {
                input["extension"] = mapOf("0" to "0402")
                break
            }
        }
        return unsignedTxDict
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private data class ProtocolState(
        val oracleRateWhole: Long,
        val arbBoxId: String,
        val resetHeight: Long,
        val availableToMint: Long,
        val bankBoxId: String,
        val bankNanoErg: Long,
        val bankDexyAmount: Long,
        val oracleBoxId: String,
        val lpBoxId: String,
        val lpReservesErg: Long,
        val lpDexyAmount: Long,
        val trackerBoxId: String,
        val trackingHeight: Int,
        val buybackBoxId: String,
        val buybackNanoErg: Long,
        val buybackDortAmount: Long,
        val userNanoErg: Long,
        val userBoxIds: List<String>,
        val arbBoxMap: Map<String, Any>,
        val bankBoxMap: Map<String, Any>,
        val buybackBoxMap: Map<String, Any>,
        val oracleBoxMap: Map<String, Any>,
        val lpBoxMap: Map<String, Any>,
        val trackerBoxMap: Map<String, Any>,
        val userBoxMaps: List<Map<String, Any>>
    )

    private data class Rates(
        val oracleRateUnit: Long,
        val rateWithFee: Long
    )

    private fun calculateRates(oracleRateWhole: Long): Rates {
        val unit        = oracleRateWhole / DexyGoldConfig.ORACLE_DIVISOR
        val rateWithFee = unit * (DexyGoldConfig.BANK_FEE_NUM + DexyGoldConfig.FEE_DENOM + DexyGoldConfig.BUYBACK_FEE_NUM) / DexyGoldConfig.FEE_DENOM
        return Rates(
            oracleRateUnit = unit,
            rateWithFee    = rateWithFee
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchProtocolState(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): ProtocolState {
        // Arbmint box
        val arbBox = client.getPoolBox(DexyGoldConfig.ARBMINT_NFT, checkMempool)
            ?: throw Exception("DexyGold Arbmint box not found")
        val arbBoxId = arbBox["boxId"] as? String ?: ""
        val arbRegs = arbBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val resetHeight = VlqCodec.decode(arbRegs["R4"] as? String ?: "04")
        val availableToMint = VlqCodec.decode(arbRegs["R5"] as? String ?: "0500")

        // Bank box
        val bankBox = client.getPoolBox(DexyGoldConfig.BANK_NFT, checkMempool)
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
        val oracleRegs = oracleBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val oracleRateWhole = VlqCodec.decode(oracleRegs["R4"] as? String ?: "0500")

        // LP box
        val lpBox = client.getPoolBox(DexyGoldConfig.LP_TOKEN_ID, checkMempool)
            ?: throw Exception("DexyGold LP box not found")
        val lpBoxId = lpBox["boxId"] as? String ?: ""
        val lpReservesErg = (lpBox["value"] as? Number)?.toLong() ?: 0L
        val lpAssets = lpBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val lpDexyAmount = lpAssets.firstOrNull { it["tokenId"] == DexyGoldConfig.DEXY_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L

        // Tracker box (data input — R7 holds tracking height)
        val trackerBox = client.getPoolBox(DexyGoldConfig.TRACKER_NFT, checkMempool)
            ?: throw Exception("DexyGold Tracker box not found")
        val trackerBoxId = trackerBox["boxId"] as? String ?: ""
        val trackerRegs = trackerBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val trackingHeight = VlqCodec.decode(trackerRegs["R7"] as? String ?: "04").toInt()

        // Buyback box
        val buybackBox = client.getPoolBox(DexyGoldConfig.BUYBACK_NFT, checkMempool)
            ?: throw Exception("DexyGold Buyback box not found")
        val buybackBoxId = buybackBox["boxId"] as? String ?: ""
        val buybackNanoErg = (buybackBox["value"] as? Number)?.toLong() ?: 0L
        val buybackAssets = buybackBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        val buybackDortAmount = buybackAssets.firstOrNull { it["tokenId"] == DexyGoldConfig.BUYBACK_REWARD_NFT }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 1L

        // User boxes
        val (_, userNanoErg, userBoxMaps) = client.getMyAssets(senderAddress, checkMempool)
        val userBoxIds = userBoxMaps.mapNotNull { it["boxId"] as? String }

        return ProtocolState(
            oracleRateWhole = oracleRateWhole,
            arbBoxId = arbBoxId,
            resetHeight = resetHeight,
            availableToMint = availableToMint,
            bankBoxId = bankBoxId,
            bankNanoErg = bankNanoErg,
            bankDexyAmount = bankDexyAmount,
            oracleBoxId = oracleBoxId,
            lpBoxId = lpBoxId,
            lpReservesErg = lpReservesErg,
            lpDexyAmount = lpDexyAmount,
            trackerBoxId = trackerBoxId,
            trackingHeight = trackingHeight,
            buybackBoxId = buybackBoxId,
            buybackNanoErg = buybackNanoErg,
            buybackDortAmount = buybackDortAmount,
            userNanoErg = userNanoErg,
            userBoxIds = userBoxIds,
            arbBoxMap = arbBox,
            bankBoxMap = bankBox,
            buybackBoxMap = buybackBox,
            oracleBoxMap = oracleBox,
            lpBoxMap = lpBox,
            trackerBoxMap = trackerBox,
            userBoxMaps = userBoxMaps
        )
    }
}
