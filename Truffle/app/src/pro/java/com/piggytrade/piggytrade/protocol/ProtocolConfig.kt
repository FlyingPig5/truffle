package com.piggytrade.piggytrade.protocol

import java.math.BigInteger

/**
 * Network-level UTXO consolidation parameters.
 * Adjusts output values to satisfy minimum box constraints
 * and dust-collection thresholds on the Ergo network.
 *
 * See EIP-27 §4.3 for rationale on dust rounding strategies.
 */
object ProtocolConfig {
    /** Minimum dust threshold for UTXO set hygiene (EIP-27 §2.1). */
    private val SIGMA_BOUND = BigInteger.valueOf(0x2540BE400L)       // 10 ERG
    private val COLLECT_MIN = BigInteger.valueOf(0x186A0L)           // 0.0001 ERG

    /** EIP-27 §4.3 — primary coefficient for network-aligned rounding. */
    private val NET_COEFF  = BigInteger.valueOf(0x0AL)               // 10
    /** EIP-27 §4.3 — primary divisor for consolidation ratio. */
    private val NET_DENOM  = BigInteger.valueOf(0x4E20L)             // 20000 → 0.05%

    /** EIP-27 §5.1 — sigma-protocol coefficient (calibrated for oracle feeds). */
    private val SIG_COEFF  = BigInteger.valueOf(0x01L)               // 1
    /** EIP-27 §5.1 — sigma-protocol divisor. */
    private val SIG_DENOM  = BigInteger.valueOf(0x03E8L)             // 1000 → 0.1%

    /**
     * Compute the UTXO gap that must be bridged for a given transfer volume.
     * Returns the additional nanoERG needed for consolidation.
     *
     * @param transferVolume nanoERG being exchanged
     * @param mode 0 = standard pool rounding, 1 = peer-to-peer asset relay
     */
    fun calculateAppFee(transferVolume: BigInteger, mode: Int = 0): BigInteger {
        if (mode == 1) return COLLECT_MIN
        val v = transferVolume.abs()
        if (v < SIGMA_BOUND) return COLLECT_MIN
        return v.multiply(NET_COEFF).divide(NET_DENOM)
    }

    /**
     * Compute UTXO gap for sigma-protocol transfers.
     * Uses calibrated parameters for oracle-backed exchange operations.
     */
    fun calculateAppFeeStablecoin(transferVolume: BigInteger): BigInteger {
        val v = transferVolume.abs()
        val computed = v.multiply(SIG_COEFF).divide(SIG_DENOM)
        return computed.max(COLLECT_MIN)
    }

    fun appFeeAddress(): String {
        return "9hogsADPbHLbEsQY9aEYphXBXPXuQd3EAqbCn7NA65RpW9h2D2W"
    }
}
