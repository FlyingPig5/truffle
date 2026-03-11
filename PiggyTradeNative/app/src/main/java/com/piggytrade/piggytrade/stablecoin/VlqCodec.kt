package com.piggytrade.piggytrade.stablecoin

/**
 * VLQ (Variable-Length Quantity) codec with ZigZag encoding,
 * matching the Python `vlq.py` reference implementation exactly.
 *
 * ErgoScript register values are encoded as:
 *   [type-prefix byte(s)] + [ZigZag-VLQ encoded Int/Long]
 *
 * The prefix for a register holding a Long (type 0x05) is "05".
 * The prefix for a register holding an Int  (type 0x04) is "04".
 *
 * Example round-trip:
 *   encode(1L, "05") == "0502"
 *   decode("0502")   == 1L
 */
object VlqCodec {

    /**
     * Decode a hex string produced by ErgoScript register serialisation.
     * Strips the 2-character type prefix, then applies VLQ+ZigZag decoding.
     * Mirrors Python `vlq.decode(encoded_string)`.
     */
    fun decode(hexWithPrefix: String): Long {
        if (hexWithPrefix.length < 2) return 0L
        val hex = hexWithPrefix.substring(2) // strip type prefix
        if (hex.isEmpty()) return 0L

        val bytes = hex.chunked(2).map { it.toInt(16) }

        // VLQ decode → zigzag accumulator
        var z = 0L
        var shift = 0
        for (b in bytes) {
            z = z or ((b.toLong() and 0x7FL) shl shift)
            shift += 7
            if (b and 0x80 == 0) break
        }

        // ZigZag decode: (z >>> 1) XOR -(z AND 1)
        return (z ushr 1) xor -(z and 1L)
    }

    /**
     * Encode a Long value to a hex string with the given type prefix.
     * Prefix "05" for Long registers, "04" for Int/Height registers.
     * Mirrors Python `vlq.encode(value, prefix)`.
     *
     * @param value  The signed integer value to encode.
     * @param prefix Two-hex-char type prefix, e.g. "04" or "05".
     */
    fun encode(value: Long, prefix: String): String {
        // ZigZag encode: (value << 1) XOR (value >> 31)
        var z = (value shl 1) xor (value shr 31)

        val bytes = mutableListOf<Int>()
        while (z >= 0x80L) {
            bytes.add(((z and 0x7FL) or 0x80L).toInt())
            z = z ushr 7
        }
        bytes.add(z.toInt())

        val encoded = bytes.joinToString("") { it.toString(16).padStart(2, '0') }
        return prefix + encoded
    }
}
