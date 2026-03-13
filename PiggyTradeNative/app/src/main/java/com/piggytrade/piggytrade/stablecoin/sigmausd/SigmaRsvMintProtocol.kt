package com.piggytrade.piggytrade.stablecoin.sigmausd

import android.util.Log
import com.piggytrade.piggytrade.network.NodeClient
import com.piggytrade.piggytrade.stablecoin.EligibilityResult
import com.piggytrade.piggytrade.stablecoin.MintQuote
import com.piggytrade.piggytrade.stablecoin.RedeemQuote
import com.piggytrade.piggytrade.stablecoin.StatusField
import com.piggytrade.piggytrade.stablecoin.StablecoinProtocol
import com.piggytrade.piggytrade.stablecoin.VlqCodec
import com.piggytrade.piggytrade.blockchain.ErgoSigner
import com.piggytrade.piggytrade.protocol.ProtocolConfig

/**
 * SigRSV (reserve coin) mint + redeem protocol.
 *
 * Key differences from SigUSD:
 *   - Mint is blocked when reserve ratio > 800% (too overcolllateralised)
 *   - Redeem is blocked when reserve ratio < 400% (would under-collateralise)
 *   - SigRSV has 0 decimal places (integer amounts)
 *   - Price is based on equity / circulating reserve coins
 *
 * Shares the same bank box, oracle, and BankState with SigmaUsdMintProtocol.
 */
class SigmaRsvMintProtocol : StablecoinProtocol {

    override val id = "sigrsv_mint"
    override val displayName = "SigRSV"
    override val mintTokenId = SigmaUsdConfig.SIGRSV_TOKEN_ID
    override val mintTokenName = "SigRSV"
    override val mintTokenDecimals = SigmaUsdConfig.SIGRSV_DECIMALS
    override val supportsRedeem = true

    private val TAG = "SigRsvMint"

    // Delegate to SigmaUsdMintProtocol for shared bank state fetching
    private val sigmaUsdHelper = SigmaUsdMintProtocol()

    // ─── Eligibility ──────────────────────────────────────────────────────────

    override suspend fun checkEligibility(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): EligibilityResult {
        return try {
            val state = sigmaUsdHelper.fetchBankState(client, senderAddress, checkMempool)
            val bank = state.bank

            val ratio = bank.currentReserveRatio()
            val maxMintable = bank.numAbleToMintReserveCoin()
            val maxRedeemable = bank.numAbleToRedeemReserveCoin()

            val canMintRsv = ratio <= SigmaUsdConfig.MAX_RESERVE_RATIO && maxMintable > 0
            val canRedeemRsv = ratio >= SigmaUsdConfig.MIN_RESERVE_RATIO && maxRedeemable > 0

            val fields = buildList {
                add(StatusField(
                    "Reserve Ratio", "${ratio}%",
                    when {
                        ratio < SigmaUsdConfig.MIN_RESERVE_RATIO -> StatusField.Status.ERROR
                        ratio > SigmaUsdConfig.MAX_RESERVE_RATIO -> StatusField.Status.WARNING
                        else -> StatusField.Status.OK
                    }
                ))
                add(StatusField("ERG / SigRSV", "%.6f ERG".format(bank.ergPerSigRsv())))
                add(StatusField("SigRSV / ERG", "%.0f".format(bank.sigRsvPerErg())))
                add(StatusField(
                    "Mint (≤${SigmaUsdConfig.MAX_RESERVE_RATIO}%)",
                    if (canMintRsv) "Open ✓" else "Blocked ✗",
                    if (canMintRsv) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField(
                    "Redeem (≥${SigmaUsdConfig.MIN_RESERVE_RATIO}%)",
                    if (canRedeemRsv) "Open ✓" else "Blocked ✗",
                    if (canRedeemRsv) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField("Circulating SigRSV", "${state.circulatingReserve}"))
            }

            val reason = when {
                !canMintRsv && !canRedeemRsv ->
                    "SigRSV operations blocked: ratio ${ratio}% — mint needs ≤${SigmaUsdConfig.MAX_RESERVE_RATIO}%, redeem needs ≥${SigmaUsdConfig.MIN_RESERVE_RATIO}%"
                !canMintRsv ->
                    "SigRSV minting blocked (ratio ${ratio}% > ${SigmaUsdConfig.MAX_RESERVE_RATIO}%). Redeem is available."
                !canRedeemRsv ->
                    "SigRSV redeem blocked (ratio ${ratio}% < ${SigmaUsdConfig.MIN_RESERVE_RATIO}%). Minting is available."
                else -> null
            }

            EligibilityResult(
                canMint = canMintRsv,
                canRedeem = canRedeemRsv,
                reason = reason,
                availableCapacity = maxMintable,
                statusFields = fields
            )
        } catch (e: Exception) {
            val detail = if (e is retrofit2.HttpException) {
                "HTTP ${e.code()} — ${e.response()?.errorBody()?.string() ?: ""}"
            } else e.message ?: "Unknown error"
            Log.e(TAG, "checkEligibility failed: $detail", e)
            EligibilityResult(
                canMint = false,
                reason = "Protocol state error: $detail",
                statusFields = listOf(StatusField("Error", detail, StatusField.Status.ERROR))
            )
        }
    }

    // ─── Mint Quote ───────────────────────────────────────────────────────────

    override suspend fun getQuote(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        checkMempool: Boolean
    ): MintQuote {
        val state = sigmaUsdHelper.fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = amount.toLong()  // SigRSV has 0 decimals

        val baseCost = state.bank.baseCostToMintReserveCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseCost).toLong()

        val feeLessAmount = state.bank.reserveCoinNominalPrice() * amountRaw
        val protocolFee = baseCost - feeLessAmount

        val breakdown = buildList {
            add("Protocol Fee (2%)" to protocolFee)
            if (appFee > 0L) add("App Fee" to appFee)
        }

        return MintQuote(
            tokenReceived = mintTokenName,
            amountReceived = amountRaw,
            tokenDecimals = mintTokenDecimals,
            ergCost = baseCost + appFee,
            feeBreakdown = breakdown,
            miningFee = SigmaUsdConfig.MINT_TX_FEE
        )
    }

    // ─── Redeem Quote ─────────────────────────────────────────────────────────

    override suspend fun getRedeemQuote(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        checkMempool: Boolean
    ): RedeemQuote {
        val state = sigmaUsdHelper.fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = amount.toLong()

        val baseAmount = state.bank.baseAmountFromRedeemingReserveCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseAmount).toLong()

        val feeLessAmount = state.bank.reserveCoinNominalPrice() * amountRaw
        val protocolFee = feeLessAmount - baseAmount

        val breakdown = buildList {
            add("Protocol Fee (2%)" to protocolFee)
            if (appFee > 0L) add("App Fee" to appFee)
        }

        return RedeemQuote(
            tokenRedeemed = mintTokenName,
            amountRedeemed = amountRaw,
            tokenDecimals = mintTokenDecimals,
            ergReceived = baseAmount - appFee - SigmaUsdConfig.MINT_TX_FEE,
            feeBreakdown = breakdown,
            miningFee = SigmaUsdConfig.MINT_TX_FEE
        )
    }

    // ─── Build Mint Transaction ───────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun buildTransaction(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        miningFee: Long,
        checkMempool: Boolean
    ): Map<String, Any> {
        val state = sigmaUsdHelper.fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = amount.toLong()
        val height = client.getHeight()

        val bank = state.bank
        if (!bank.ableToMintReserveCoin(amountRaw)) {
            throw Exception("SigRSV minting blocked: reserve ratio would exceed ${SigmaUsdConfig.MAX_RESERVE_RATIO}%")
        }

        val baseCost = bank.baseCostToMintReserveCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseCost).toLong()

        val newBankValue = state.bankNanoErg + baseCost
        val newBankReserve = state.bankReserveTokens - amountRaw
        val newCircReserve = state.circulatingReserve + amountRaw

        val userChangeValue = state.userNanoErg - baseCost - miningFee - appFee
        if (userChangeValue < 0) {
            val totalNeeded = baseCost + miningFee + appFee
            throw Exception("Insufficient ERG: need ${totalNeeded / 1_000_000_000.0} ERG, have ${state.userNanoErg / 1_000_000_000.0} ERG")
        }

        val userTokens = collectUserTokens(state.userBoxMaps)
        userTokens[SigmaUsdConfig.SIGRSV_TOKEN_ID] =
            (userTokens[SigmaUsdConfig.SIGRSV_TOKEN_ID] ?: 0L) + amountRaw

        val userChangeAssets = userTokens.map { (tid, amt) -> mapOf("tokenId" to tid, "amount" to amt) }

        val requestsList = mutableListOf(
            buildBankOutput(state, newBankValue, state.bankStableTokens, newBankReserve, state.circulatingStable, newCircReserve, height),
            mapOf(
                "address" to senderAddress,
                "value" to userChangeValue,
                "assets" to userChangeAssets,
                "registers" to mapOf(
                    "R4" to VlqCodec.encode(amountRaw, "05"),
                    "R5" to VlqCodec.encode(baseCost, "05")
                ),
                "creationHeight" to height
            )
        )

        val sinkAddr = ProtocolConfig.consolidationSink()
        if (appFee > 0L && sinkAddr.isNotEmpty()) {
            requestsList.add(mapOf(
                "address" to sinkAddr,
                "value" to appFee,
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, Any>()
            ))
        }

        val inputBoxIds = listOf(state.bankBoxId) + state.userBoxIds
        val inputsRaw = client.getBoxBytes(inputBoxIds)
        val inputBoxes = listOf(state.bankBoxMap) + state.userBoxMaps

        return mapOf(
            "requests" to requestsList.toList(),
            "fee" to miningFee,
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to listOf(state.oracleBoxId),
            "input_boxes" to inputBoxes,
            "data_input_boxes" to listOf(state.oracleBoxMap),
            "context_extensions" to emptyMap<String, Any>(),
            "current_height" to height
        )
    }

    // ─── Build Redeem Transaction ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun buildRedeemTransaction(
        client: NodeClient,
        amount: Double,
        senderAddress: String,
        miningFee: Long,
        checkMempool: Boolean
    ): Map<String, Any> {
        val state = sigmaUsdHelper.fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = amount.toLong()
        val height = client.getHeight()

        val bank = state.bank
        if (!bank.ableToRedeemReserveCoin(amountRaw)) {
            throw Exception("SigRSV redeem blocked: reserve ratio would drop below ${SigmaUsdConfig.MIN_RESERVE_RATIO}%")
        }

        val baseAmountErg = bank.baseAmountFromRedeemingReserveCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseAmountErg).toLong()

        val newBankValue = state.bankNanoErg - baseAmountErg
        if (newBankValue < SigmaUsdConfig.MIN_BOX_VALUE) {
            throw Exception("Bank reserves too low to redeem this amount")
        }
        val newBankReserve = state.bankReserveTokens + amountRaw
        val newCircReserve = state.circulatingReserve - amountRaw

        val userTokens = collectUserTokens(state.userBoxMaps)
        val userSigRsv = userTokens[SigmaUsdConfig.SIGRSV_TOKEN_ID] ?: 0L
        if (userSigRsv < amountRaw) {
            throw Exception("Insufficient SigRSV: need $amountRaw, have $userSigRsv")
        }
        userTokens[SigmaUsdConfig.SIGRSV_TOKEN_ID] = userSigRsv - amountRaw
        val userChangeAssets = userTokens.filter { it.value > 0 }.map { (tid, amt) ->
            mapOf("tokenId" to tid, "amount" to amt)
        }

        val userChangeValue = state.userNanoErg + baseAmountErg - miningFee - appFee
        if (userChangeValue < SigmaUsdConfig.MIN_BOX_VALUE) {
            throw Exception("Insufficient ERG for transaction fees")
        }

        val requestsList = mutableListOf(
            buildBankOutput(state, newBankValue, state.bankStableTokens, newBankReserve, state.circulatingStable, newCircReserve, height),
            mapOf(
                "address" to senderAddress,
                "value" to userChangeValue,
                "assets" to userChangeAssets,
                "registers" to mapOf(
                    "R4" to VlqCodec.encode(-amountRaw, "05"),
                    "R5" to VlqCodec.encode(-baseAmountErg, "05")
                ),
                "creationHeight" to height
            )
        )

        val sinkAddr = ProtocolConfig.consolidationSink()
        if (appFee > 0L && sinkAddr.isNotEmpty()) {
            requestsList.add(mapOf(
                "address" to sinkAddr,
                "value" to appFee,
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, Any>()
            ))
        }

        val inputBoxIds = listOf(state.bankBoxId) + state.userBoxIds
        val inputsRaw = client.getBoxBytes(inputBoxIds)
        val inputBoxes = listOf(state.bankBoxMap) + state.userBoxMaps

        return mapOf(
            "requests" to requestsList.toList(),
            "fee" to miningFee,
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to listOf(state.oracleBoxId),
            "input_boxes" to inputBoxes,
            "data_input_boxes" to listOf(state.oracleBoxMap),
            "context_extensions" to emptyMap<String, Any>(),
            "current_height" to height
        )
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun collectUserTokens(userBoxMaps: List<Map<String, Any>>): MutableMap<String, Long> {
        val userTokens = mutableMapOf<String, Long>()
        for (box in userBoxMaps) {
            val assets = (box["assets"] as? List<Map<String, Any>>) ?: continue
            for (asset in assets) {
                val tid = asset["tokenId"] as? String ?: continue
                val amt = (asset["amount"] as? Number)?.toLong() ?: continue
                userTokens[tid] = (userTokens[tid] ?: 0L) + amt
            }
        }
        return userTokens
    }

    private fun buildBankOutput(
        state: SigmaUsdMintProtocol.BankState,
        newValue: Long,
        newStableTokens: Long,
        newReserveTokens: Long,
        newCircStable: Long,
        newCircReserve: Long,
        height: Int
    ): Map<String, Any> {
        val bankAddress = state.bankBoxMap["address"] as? String ?: ""
        val bankErgoTree = state.bankBoxMap["ergoTree"] as? String ?: ""

        val bankOutput = mutableMapOf<String, Any>(
            "value" to newValue,
            "assets" to listOf(
                mapOf("tokenId" to SigmaUsdConfig.SIGUSD_TOKEN_ID, "amount" to newStableTokens),
                mapOf("tokenId" to SigmaUsdConfig.SIGRSV_TOKEN_ID, "amount" to newReserveTokens),
                mapOf("tokenId" to SigmaUsdConfig.BANK_NFT, "amount" to 1L)
            ),
            "registers" to mapOf(
                "R4" to VlqCodec.encode(newCircStable, "05"),
                "R5" to VlqCodec.encode(newCircReserve, "05")
            ),
            "creationHeight" to height
        )

        if (bankAddress.isNotEmpty()) {
            bankOutput["address"] = bankAddress
        } else if (bankErgoTree.isNotEmpty()) {
            bankOutput["ergoTree"] = bankErgoTree
        }

        return bankOutput
    }
}
