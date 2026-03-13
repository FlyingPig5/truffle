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
 * SigUSD (stable coin) mint + redeem protocol.
 *
 * Ported from the reference sigma-usd wallet implementation.
 * Uses the AgeUSD bank contract (v0.4) with oracle data input.
 *
 * Transaction structure:
 *   Input 0:  Bank box (holds SigUSD + SigRSV + NFT)
 *   Inputs 1+: User UTXOs
 *   Data Input 0: Oracle box (R4 = ERG/USD rate)
 *   Output 0: Updated bank box (R4 = new circ stable, R5 = same circ reserve)
 *   Output 1: Receipt/user box (R4 = circDelta, R5 = bcReserveDelta per contract)
 *   Output 2: App fee box (obfuscated)
 */
class SigmaUsdMintProtocol : StablecoinProtocol {

    override val id = "sigusd_mint"
    override val displayName = "SigUSD"
    override val mintTokenId = SigmaUsdConfig.SIGUSD_TOKEN_ID
    override val mintTokenName = "SigUSD"
    override val mintTokenDecimals = SigmaUsdConfig.SIGUSD_DECIMALS
    override val supportsRedeem = true

    private val TAG = "SigUsdMint"

    // ─── Eligibility ──────────────────────────────────────────────────────────

    override suspend fun checkEligibility(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): EligibilityResult {
        return try {
            val state = fetchBankState(client, senderAddress, checkMempool)
            val bank = state.bank

            val ratio = bank.currentReserveRatio()
            val maxMintable = bank.numAbleToMintStableCoin()
            val canMintSigUsd = ratio >= SigmaUsdConfig.MIN_RESERVE_RATIO && maxMintable > 0
            // SigUSD redeem is ALWAYS allowed — no reserve ratio constraint per contract
            val canRedeemSigUsd = true

            val fields = buildList {
                add(StatusField(
                    "Reserve Ratio", "${ratio}%",
                    if (ratio >= SigmaUsdConfig.MIN_RESERVE_RATIO) StatusField.Status.OK else StatusField.Status.WARNING
                ))
                add(StatusField("ERG / SigUSD", "%.4f ERG".format(bank.ergPerSigUsd())))
                add(StatusField("SigUSD / ERG", "%.2f".format(bank.sigUsdPerErg())))
                add(StatusField(
                    "Mint (≥${SigmaUsdConfig.MIN_RESERVE_RATIO}%)",
                    if (canMintSigUsd) "Open ✓" else "Blocked ✗",
                    if (canMintSigUsd) StatusField.Status.OK else StatusField.Status.ERROR
                ))
                add(StatusField(
                    "Redeem",
                    "Always Open ✓",
                    StatusField.Status.OK
                ))
                add(StatusField("Circulating SigUSD", "%.2f".format(
                    state.circulatingStable.toDouble() / SigmaUsdConfig.SIGUSD_FACTOR
                )))
            }

            val reason = when {
                !canMintSigUsd && ratio < SigmaUsdConfig.MIN_RESERVE_RATIO ->
                    "SigUSD minting blocked (ratio ${ratio}% < ${SigmaUsdConfig.MIN_RESERVE_RATIO}%). Redeem is always available."
                !canMintSigUsd -> "No SigUSD available to mint. Redeem is always available."
                else -> null
            }

            EligibilityResult(
                canMint = canMintSigUsd,
                canRedeem = canRedeemSigUsd,
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
        val state = fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = (amount * SigmaUsdConfig.SIGUSD_FACTOR).toLong()

        val baseCost = state.bank.baseCostToMintStableCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseCost).toLong()

        val feeLessAmount = state.bank.stableCoinNominalPrice() * amountRaw
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
        val state = fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = (amount * SigmaUsdConfig.SIGUSD_FACTOR).toLong()

        val baseAmount = state.bank.baseAmountFromRedeemingStableCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseAmount).toLong()

        val feeLessAmount = state.bank.stableCoinNominalPrice() * amountRaw
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
        val state = fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = (amount * SigmaUsdConfig.SIGUSD_FACTOR).toLong()
        val height = client.getHeight()

        val bank = state.bank
        if (!bank.ableToMintStableCoin(amountRaw)) {
            throw Exception("SigUSD minting blocked: reserve ratio would drop below ${SigmaUsdConfig.MIN_RESERVE_RATIO}%")
        }

        val baseCost = bank.baseCostToMintStableCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseCost).toLong()

        // Bank box output: receives ERG, releases SigUSD tokens
        val newBankValue = state.bankNanoErg + baseCost
        val newBankStable = state.bankStableTokens - amountRaw
        val newCircStable = state.circulatingStable + amountRaw

        // User change
        val userChangeValue = state.userNanoErg - baseCost - miningFee - appFee
        if (userChangeValue < 0) {
            val totalNeeded = baseCost + miningFee + appFee
            throw Exception("Insufficient ERG: need ${totalNeeded / 1_000_000_000.0} ERG, have ${state.userNanoErg / 1_000_000_000.0} ERG")
        }

        // Collect all user tokens to carry through
        val userTokens = collectUserTokens(state.userBoxMaps)
        userTokens[SigmaUsdConfig.SIGUSD_TOKEN_ID] =
            (userTokens[SigmaUsdConfig.SIGUSD_TOKEN_ID] ?: 0L) + amountRaw

        val userChangeAssets = userTokens.map { (tid, amt) -> mapOf("tokenId" to tid, "amount" to amt) }

        // Build requests
        val requestsList = mutableListOf(
            // Output 0: Updated bank box
            buildBankOutput(state, newBankValue, newBankStable, state.bankReserveTokens, newCircStable, state.circulatingReserve, height),
            // Output 1: Receipt/user box (R4=circDelta, R5=bcReserveDelta per contract)
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

        // App fee output
        val sinkAddr = ProtocolConfig.consolidationSink()
        if (appFee > 0L && sinkAddr.isNotEmpty()) {
            requestsList.add(mapOf(
                "address" to sinkAddr,
                "value" to appFee,
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, Any>()
            ))
        }

        // Input / data input boxes
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
        val state = fetchBankState(client, senderAddress, checkMempool)
        val amountRaw = (amount * SigmaUsdConfig.SIGUSD_FACTOR).toLong()
        val height = client.getHeight()

        val bank = state.bank
        val baseAmountErg = bank.baseAmountFromRedeemingStableCoin(amountRaw)
        val signer = ErgoSigner("")
        val appFee = signer.resolveUtxoGapSigma(baseAmountErg).toLong()

        // Bank box output: releases ERG, receives SigUSD tokens back
        val newBankValue = state.bankNanoErg - baseAmountErg
        if (newBankValue < SigmaUsdConfig.MIN_BOX_VALUE) {
            throw Exception("Bank reserves too low to redeem this amount")
        }
        val newBankStable = state.bankStableTokens + amountRaw
        val newCircStable = state.circulatingStable - amountRaw

        // User receives ERG from the bank, minus fees
        val userTokens = collectUserTokens(state.userBoxMaps)
        // User must have SigUSD tokens to redeem
        val userSigUsd = userTokens[SigmaUsdConfig.SIGUSD_TOKEN_ID] ?: 0L
        if (userSigUsd < amountRaw) {
            throw Exception("Insufficient SigUSD: need ${amountRaw.toDouble() / SigmaUsdConfig.SIGUSD_FACTOR}, have ${userSigUsd.toDouble() / SigmaUsdConfig.SIGUSD_FACTOR}")
        }
        userTokens[SigmaUsdConfig.SIGUSD_TOKEN_ID] = userSigUsd - amountRaw
        // Remove zero-balance tokens from output
        val userChangeAssets = userTokens.filter { it.value > 0 }.map { (tid, amt) ->
            mapOf("tokenId" to tid, "amount" to amt)
        }

        val userChangeValue = state.userNanoErg + baseAmountErg - miningFee - appFee
        if (userChangeValue < SigmaUsdConfig.MIN_BOX_VALUE) {
            throw Exception("Insufficient ERG for transaction fees")
        }

        // Build requests
        val requestsList = mutableListOf(
            // Output 0: Updated bank box
            buildBankOutput(state, newBankValue, newBankStable, state.bankReserveTokens, newCircStable, state.circulatingReserve, height),
            // Output 1: Receipt/user box (R4=circDelta negative, R5=bcReserveDelta negative per contract)
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

        // App fee output
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

    internal data class BankState(
        val bank: SigmaUsdBank,
        val bankBoxId: String,
        val bankNanoErg: Long,
        val bankStableTokens: Long,
        val bankReserveTokens: Long,
        val circulatingStable: Long,
        val circulatingReserve: Long,
        val oracleBoxId: String,
        val oracleRate: Long,             // raw R4 value
        val userNanoErg: Long,
        val userBoxIds: List<String>,
        val bankBoxMap: Map<String, Any>,
        val oracleBoxMap: Map<String, Any>,
        val userBoxMaps: List<Map<String, Any>>
    )

    @Suppress("UNCHECKED_CAST")
    internal suspend fun fetchBankState(
        client: NodeClient,
        senderAddress: String,
        checkMempool: Boolean
    ): BankState {
        // Bank box
        val bankBox = client.getPoolBox(SigmaUsdConfig.BANK_NFT, checkMempool)
            ?: throw Exception("AgeUSD bank box not found")
        val bankBoxId = bankBox["boxId"] as? String ?: ""
        val bankNanoErg = (bankBox["value"] as? Number)?.toLong() ?: 0L
        val bankRegs = bankBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val circulatingStable = VlqCodec.decode(bankRegs["R4"] as? String ?: "0500")
        val circulatingReserve = VlqCodec.decode(bankRegs["R5"] as? String ?: "0500")

        val bankAssets = bankBox["assets"] as? List<Map<String, Any>> ?: emptyList()
        // Token ordering per contract: tokens(0) = SigUSD, tokens(1) = SigRSV, tokens(2) = NFT
        val bankStableTokens = bankAssets.firstOrNull { it["tokenId"] == SigmaUsdConfig.SIGUSD_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L
        val bankReserveTokens = bankAssets.firstOrNull { it["tokenId"] == SigmaUsdConfig.SIGRSV_TOKEN_ID }
            ?.let { (it["amount"] as? Number)?.toLong() } ?: 0L

        // Oracle box
        val oracleBox = client.getPoolBox(SigmaUsdConfig.ORACLE_NFT, checkMempool)
            ?: throw Exception("Oracle box not found")
        val oracleBoxId = oracleBox["boxId"] as? String ?: ""
        val oracleRegs = oracleBox["additionalRegisters"] as? Map<String, Any> ?: emptyMap()
        val oracleRateRaw = VlqCodec.decode(oracleRegs["R4"] as? String ?: "0500")
        // Contract: rate = R4 / 100 (converts from per-cent to per-dollar)
        val oracleRate = oracleRateRaw / 100

        // User boxes
        val (_, userNanoErg, userBoxMaps) = client.getMyAssets(senderAddress, checkMempool)
        val userBoxIds = userBoxMaps.mapNotNull { it["boxId"] as? String }

        val bank = SigmaUsdBank(
            bankValue = bankNanoErg,
            circulatingStableCoins = circulatingStable,
            circulatingReserveCoins = circulatingReserve,
            bankStableTokens = bankStableTokens,
            bankReserveTokens = bankReserveTokens,
            oracleRateNanoErgPerUsd = oracleRate
        )

        return BankState(
            bank = bank,
            bankBoxId = bankBoxId,
            bankNanoErg = bankNanoErg,
            bankStableTokens = bankStableTokens,
            bankReserveTokens = bankReserveTokens,
            circulatingStable = circulatingStable,
            circulatingReserve = circulatingReserve,
            oracleBoxId = oracleBoxId,
            oracleRate = oracleRateRaw,
            userNanoErg = userNanoErg,
            userBoxIds = userBoxIds,
            bankBoxMap = bankBox,
            oracleBoxMap = oracleBox,
            userBoxMaps = userBoxMaps
        )
    }

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

    /**
     * Build the bank output box with updated token counts and registers.
     * The bank box preserves its ergoTree (address) and token IDs per contract rules.
     */
    private fun buildBankOutput(
        state: BankState,
        newValue: Long,
        newStableTokens: Long,
        newReserveTokens: Long,
        newCircStable: Long,
        newCircReserve: Long,
        height: Int
    ): Map<String, Any> {
        // Reconstruct the bank address from the bank box's ergoTree
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

        // Use address if available, otherwise fall back to ergoTree
        if (bankAddress.isNotEmpty()) {
            bankOutput["address"] = bankAddress
        } else if (bankErgoTree.isNotEmpty()) {
            bankOutput["ergoTree"] = bankErgoTree
        }

        return bankOutput
    }
}

/**
 * Extension on ErgoSigner to compute the SigmaUSD-specific app fee.
 * Uses 0.1% rate (separate from the 0.05% DEX/USE rate).
 */
fun ErgoSigner.resolveUtxoGapSigma(n: Long): java.math.BigInteger {
    return ProtocolConfig.resolveUtxoGapSigma(java.math.BigInteger.valueOf(n))
}
