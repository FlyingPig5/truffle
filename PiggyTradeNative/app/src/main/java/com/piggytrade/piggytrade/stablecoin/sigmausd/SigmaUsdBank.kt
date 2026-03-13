package com.piggytrade.piggytrade.stablecoin.sigmausd

/**
 * AgeUSD protocol economics calculator.
 *
 * Pure computation class — no network calls. Takes on-chain state as constructor
 * parameters and provides all pricing, fee, and eligibility calculations.
 *
 * Arithmetic uses Long (nanoErg precision) to exactly mirror the on-chain
 * ErgoScript contract logic from AgeUSD.scala v0.4.
 *
 * Contract reference:
 *   rate = oracleR4 / 100  (nanoErg per "cent" → nanoErg per USD)
 *   fee = brDeltaExpected * 2 / 100  (2% protocol fee, always positive)
 *   reserveRatio = bcReserve * 100 / (scCirc * rate)
 */
class SigmaUsdBank(
    /** Bank box ERG value in nanoErg. */
    private val bankValue: Long,
    /** Number of SigUSD tokens currently circulating (from bank R4). */
    private val circulatingStableCoins: Long,
    /** Number of SigRSV tokens currently circulating (from bank R5). */
    private val circulatingReserveCoins: Long,
    /** Number of SigUSD tokens still in the bank box (tokens(0) amount). */
    private val bankStableTokens: Long,
    /** Number of SigRSV tokens still in the bank box (tokens(1) amount). */
    private val bankReserveTokens: Long,
    /** Oracle rate: raw R4 value / 100 (nanoErg per USD). */
    private val oracleRateNanoErgPerUsd: Long
) {

    // ─── Base Calculations ────────────────────────────────────────────────────

    /** Bank ERG reserves, clamped to MIN_BOX_VALUE. */
    fun baseReserve(): Long {
        return if (bankValue < SigmaUsdConfig.MIN_BOX_VALUE) 0L else bankValue
    }

    /** Total ERG needed to back all circulating stable coins at current oracle rate. */
    fun liabilities(): Long {
        if (circulatingStableCoins == 0L) return 0L
        val baseReservesNeeded = circulatingStableCoins * oracleRateNanoErgPerUsd
        val br = baseReserve()
        return if (baseReservesNeeded < br) baseReservesNeeded else br
    }

    /** ERG surplus beyond liabilities — belongs to reserve coin holders. */
    fun equity(): Long {
        val br = baseReserve()
        val liab = liabilities()
        return if (liab < br) br - liab else 0L
    }

    /**
     * Current reserve ratio as a percentage.
     * E.g. 450 means 450% (4.5x overcollateralised).
     */
    fun currentReserveRatio(): Long {
        val br = baseReserve()
        if (br == 0L || oracleRateNanoErgPerUsd == 0L) return 0L
        if (circulatingStableCoins == 0L) {
            // No stable coins → ratio is "(baseReserve * 100) / oracleRate"
            // This matches the reference TS code
            return br * 100 / oracleRateNanoErgPerUsd
        }
        val perStableCoinRate = br * 100 / circulatingStableCoins
        return perStableCoinRate / oracleRateNanoErgPerUsd
    }

    // ─── Nominal Prices ───────────────────────────────────────────────────────

    /**
     * Price of one SigUSD in nanoErg.
     * Contract: min(rate, liabilities / circulatingStable)
     */
    fun stableCoinNominalPrice(): Long {
        val liab = liabilities()
        val numSc = circulatingStableCoins
        if (numSc == 0L) return oracleRateNanoErgPerUsd
        val liableRate = liab / numSc
        return if (oracleRateNanoErgPerUsd < liableRate) oracleRateNanoErgPerUsd else liableRate
    }

    /**
     * Price of one SigRSV in nanoErg.
     * Contract: equity / circulatingReserve, default 0.001 ERG if zero equity.
     */
    fun reserveCoinNominalPrice(): Long {
        val eq = equity()
        val numRc = circulatingReserveCoins
        if (numRc <= 1L || eq == 0L) return SigmaUsdConfig.RESERVE_COIN_DEFAULT_PRICE
        return eq / numRc
    }

    // ─── Minting Costs (ERG in, tokens out) ───────────────────────────────────

    /** Base ERG cost to mint [amount] SigUSD (including 2% protocol fee). */
    fun baseCostToMintStableCoin(amount: Long): Long {
        val feeLessAmount = stableCoinNominalPrice() * amount
        val protocolFee = feeLessAmount * SigmaUsdConfig.PROTOCOL_FEE_PERCENT / 100
        return feeLessAmount + protocolFee
    }

    /** Base ERG cost to mint [amount] SigRSV (including 2% protocol fee). */
    fun baseCostToMintReserveCoin(amount: Long): Long {
        val feeLessAmount = reserveCoinNominalPrice() * amount
        val protocolFee = feeLessAmount * SigmaUsdConfig.PROTOCOL_FEE_PERCENT / 100
        return feeLessAmount + protocolFee
    }

    // ─── Redeem Amounts (tokens in, ERG out) ──────────────────────────────────

    /** Base ERG returned from redeeming [amount] SigUSD (after 2% protocol fee deduction). */
    fun baseAmountFromRedeemingStableCoin(amount: Long): Long {
        val feeLessAmount = stableCoinNominalPrice() * amount
        val protocolFee = feeLessAmount * SigmaUsdConfig.PROTOCOL_FEE_PERCENT / 100
        return feeLessAmount - protocolFee
    }

    /** Base ERG returned from redeeming [amount] SigRSV (after 2% protocol fee deduction). */
    fun baseAmountFromRedeemingReserveCoin(amount: Long): Long {
        val feeLessAmount = reserveCoinNominalPrice() * amount
        val protocolFee = feeLessAmount * SigmaUsdConfig.PROTOCOL_FEE_PERCENT / 100
        return feeLessAmount - protocolFee
    }

    // ─── Eligibility Checks ───────────────────────────────────────────────────

    /**
     * Reserve ratio after minting [amount] SigUSD.
     * Must be >= MIN_RESERVE_RATIO (400%) for mint to be allowed.
     */
    fun mintStableCoinReserveRatio(amount: Long): Long {
        val newBaseReserve = baseReserve() + baseCostToMintStableCoin(amount)
        val newCirculating = circulatingStableCoins + amount
        if (newCirculating == 0L) return SigmaUsdConfig.MAX_RESERVE_RATIO
        val bcReserveNeeded = newCirculating * oracleRateNanoErgPerUsd
        return if (bcReserveNeeded == 0L) SigmaUsdConfig.MAX_RESERVE_RATIO
               else newBaseReserve * 100 / bcReserveNeeded
    }

    /** Whether minting [amount] SigUSD is allowed (ratio stays >= 400%). */
    fun ableToMintStableCoin(amount: Long): Boolean {
        return mintStableCoinReserveRatio(amount) >= SigmaUsdConfig.MIN_RESERVE_RATIO
    }

    /**
     * Reserve ratio after minting [amount] SigRSV.
     * Must be <= MAX_RESERVE_RATIO (800%) for mint to be allowed.
     */
    fun mintReserveCoinReserveRatio(amount: Long): Long {
        val newBaseReserve = baseReserve() + baseCostToMintReserveCoin(amount)
        val bcReserveNeeded = circulatingStableCoins * oracleRateNanoErgPerUsd
        return if (bcReserveNeeded == 0L) SigmaUsdConfig.MAX_RESERVE_RATIO
               else newBaseReserve * 100 / bcReserveNeeded
    }

    /** Whether minting [amount] SigRSV is allowed (ratio stays <= 800%). */
    fun ableToMintReserveCoin(amount: Long): Boolean {
        return mintReserveCoinReserveRatio(amount) <= SigmaUsdConfig.MAX_RESERVE_RATIO
    }

    /**
     * Reserve ratio after redeeming [amount] SigRSV.
     * Must be >= MIN_RESERVE_RATIO (400%) for redeem to be allowed.
     */
    fun redeemReserveCoinReserveRatio(amount: Long): Long {
        val redeemErg = baseCostToMintReserveCoin(amount) // uses same pricing
        val br = baseReserve()
        val newBaseReserve = if (redeemErg < br) br - redeemErg else 0L
        val bcReserveNeeded = circulatingStableCoins * oracleRateNanoErgPerUsd
        return if (bcReserveNeeded == 0L) SigmaUsdConfig.MAX_RESERVE_RATIO
               else newBaseReserve * 100 / bcReserveNeeded
    }

    /** Whether redeeming [amount] SigRSV is allowed (ratio stays >= 400%). */
    fun ableToRedeemReserveCoin(amount: Long): Boolean {
        return redeemReserveCoinReserveRatio(amount) >= SigmaUsdConfig.MIN_RESERVE_RATIO
    }

    // ─── Maximum Amounts (Binary Search) ──────────────────────────────────────

    /** Max SigUSD that can be minted while keeping ratio >= 400%. */
    fun numAbleToMintStableCoin(): Long {
        if (!ableToMintStableCoin(1L)) return 0L
        var low = equity() / oracleRateNanoErgPerUsd / 4  // rough lower bound
        var high = bankStableTokens
        while (low <= high) {
            val mid = low + (high - low) / 2
            val ratio = mintStableCoinReserveRatio(mid)
            when {
                ratio == SigmaUsdConfig.MIN_RESERVE_RATIO -> return mid
                ratio < SigmaUsdConfig.MIN_RESERVE_RATIO -> high = mid - 1
                else -> low = mid + 1
            }
        }
        // low went past the boundary; the last valid was low-1
        return (low - 1).coerceAtLeast(0L)
    }

    /** Max SigRSV that can be minted while keeping ratio <= 800%. */
    fun numAbleToMintReserveCoin(): Long {
        if (!ableToMintReserveCoin(1L)) return 0L
        var low = 0L
        var high = bankReserveTokens
        while (low <= high) {
            val mid = low + (high - low) / 2
            val ratio = mintReserveCoinReserveRatio(mid)
            when {
                ratio == SigmaUsdConfig.MAX_RESERVE_RATIO -> return mid
                ratio > SigmaUsdConfig.MAX_RESERVE_RATIO -> high = mid - 1
                else -> low = mid + 1
            }
        }
        return (low - 1).coerceAtLeast(0L)
    }

    /** Max SigRSV that can be redeemed while keeping ratio >= 400%. */
    fun numAbleToRedeemReserveCoin(): Long {
        if (!ableToRedeemReserveCoin(1L)) return 0L
        var low = 0L
        var high = circulatingReserveCoins
        while (low <= high) {
            val mid = low + (high - low) / 2
            val ratio = redeemReserveCoinReserveRatio(mid)
            when {
                ratio == SigmaUsdConfig.MIN_RESERVE_RATIO -> return mid
                ratio < SigmaUsdConfig.MIN_RESERVE_RATIO -> high = mid - 1
                else -> low = mid + 1
            }
        }
        return (low - 1).coerceAtLeast(0L)
    }

    // ─── Display Helpers ──────────────────────────────────────────────────────

    /** ERG per 1 whole SigUSD (human-readable). stableCoinNominalPrice is nanoErg per cent. */
    fun ergPerSigUsd(): Double =
        stableCoinNominalPrice().toDouble() * SigmaUsdConfig.SIGUSD_FACTOR / 1_000_000_000.0

    /** Whole SigUSD per ERG (human-readable). */
    fun sigUsdPerErg(): Double {
        val pricePerWhole = stableCoinNominalPrice() * SigmaUsdConfig.SIGUSD_FACTOR
        return if (pricePerWhole > 0) 1_000_000_000.0 / pricePerWhole.toDouble() else 0.0
    }

    /** ERG per SigRSV (human-readable). */
    fun ergPerSigRsv(): Double = reserveCoinNominalPrice().toDouble() / 1_000_000_000.0

    /** SigRSV per ERG (human-readable). */
    fun sigRsvPerErg(): Double {
        val price = reserveCoinNominalPrice()
        return if (price > 0) 1_000_000_000.0 / price.toDouble() else 0.0
    }
}
