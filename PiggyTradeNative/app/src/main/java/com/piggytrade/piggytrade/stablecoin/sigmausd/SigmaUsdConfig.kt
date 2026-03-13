package com.piggytrade.piggytrade.stablecoin.sigmausd

/**
 * Configuration constants for the AgeUSD (SigmaUSD/SigmaRSV) protocol.
 * Values taken from the reference implementation and on-chain contract:
 * https://github.com/Emurgo/age-usd/blob/main/ageusd-smart-contracts/v0.4/AgeUSD.scala
 *
 * The AgeUSD bank box holds both SigUSD and SigRSV tokens plus an NFT identifier.
 * The oracle provides the ERG/USD exchange rate via R4.
 * Reserve ratio governs which operations are permitted.
 */
object SigmaUsdConfig {

    // ─── Token IDs ────────────────────────────────────────────────────────────

    /** NFT identifying the AgeUSD bank box. */
    const val BANK_NFT = "7d672d1def471720ca5782fd6473e47e796d9ac0c138d9911346f118b2f6d9d9"

    /** SigUSD (stable coin) token ID. */
    const val SIGUSD_TOKEN_ID = "03faf2cb329f2e90d6d23b58d91bbb6c046aa143261cc21f52fbe2824bfcbf04"

    /** SigRSV (reserve coin) token ID. */
    const val SIGRSV_TOKEN_ID = "003bd19d0187117f130b62e1bcab0939929ff5c7709f843c5c4dd158949285d0"

    /** Oracle pool NFT — provides the ERG/USD exchange rate in R4. */
    const val ORACLE_NFT = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f"

    // ─── Protocol Constants ───────────────────────────────────────────────────

    /** Default price of a reserve coin when equity is zero (1,000,000 nanoErg = 0.001 ERG). */
    const val RESERVE_COIN_DEFAULT_PRICE = 1_000_000L

    /** Minimum box value to keep the bank box spendable (10,000,000 nanoErg = 0.01 ERG). */
    const val MIN_BOX_VALUE = 10_000_000L

    /** On-chain protocol fee: 2%. The contract enforces this. */
    const val PROTOCOL_FEE_PERCENT = 2

    /** Minimum reserve ratio (percent) below which SigUSD minting is blocked. */
    const val MIN_RESERVE_RATIO = 400L

    /** Maximum reserve ratio (percent) above which SigRSV minting is blocked. */
    const val MAX_RESERVE_RATIO = 800L

    /** Mining fee for SigmaUSD transactions (0.002 ERG). */
    const val MINT_TX_FEE = 2_000_000L

    /**
     * Height after which the max reserve ratio cap takes effect.
     * Before this height, the max ratio was effectively infinite.
     */
    const val COOLING_OFF_HEIGHT = 460000

    /** SigUSD has 2 decimal places. */
    const val SIGUSD_DECIMALS = 2
    const val SIGUSD_FACTOR = 100L  // 10^2

    /** SigRSV has 0 decimal places. */
    const val SIGRSV_DECIMALS = 0
    const val SIGRSV_FACTOR = 1L    // 10^0
}
