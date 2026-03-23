package com.piggytrade.piggytrade.crypto

import android.content.Context
import java.security.MessageDigest

/**
 * BIP39 mnemonic validation.
 *
 * Validates that:
 *  1. Each word is in the BIP39 English dictionary (2048 words).
 *  2. The word count is one of the valid BIP39 lengths (12, 15, 18, 21, 24).
 *  3. The BIP39 checksum is correct.
 */
object MnemonicValidator {

    private val VALID_LENGTHS = setOf(12, 15, 18, 21, 24)

    /** Load the BIP39 English wordlist from assets. Call once and cache. */
    fun loadWordList(context: Context): Set<String> {
        return context.assets.open("bip39_english.txt")
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Full list (ordered) needed for checksum calculation. */
    fun loadWordListOrdered(context: Context): List<String> {
        return context.assets.open("bip39_english.txt")
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    data class MnemonicValidationResult(
        /** Indices (0-based) of words that are NOT in the BIP39 dictionary. */
        val invalidWordIndices: Set<Int>,
        /** True if word count is 12 / 15 / 18 / 21 / 24. */
        val isValidWordCount: Boolean,
        /** True if the BIP39 checksum passes (only meaningful when all words are valid). */
        val checksumValid: Boolean
    ) {
        val isFullyValid: Boolean
            get() = invalidWordIndices.isEmpty() && isValidWordCount && checksumValid
    }

    /**
     * Validate a mnemonic phrase.
     *
     * @param phrase  The raw mnemonic string (space-separated words).
     * @param wordSet The set returned by [loadWordList].
     * @param orderedList The ordered list returned by [loadWordListOrdered] (needed for checksum).
     */
    fun validate(
        phrase: String,
        wordSet: Set<String>,
        orderedList: List<String>
    ): MnemonicValidationResult {
        val words = phrase.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (words.isEmpty()) {
            return MnemonicValidationResult(
                invalidWordIndices = emptySet(),
                isValidWordCount = false,
                checksumValid = false
            )
        }

        // 1. Word-by-word dictionary check.
        val invalidIndices = words.indices
            .filter { words[it].lowercase() !in wordSet }
            .toSet()

        // 2. Word count check.
        val validCount = words.size in VALID_LENGTHS

        // 3. Checksum — only attempt when all words are known and count is valid.
        val checksumValid = if (invalidIndices.isEmpty() && validCount) {
            verifyChecksum(words.map { it.lowercase() }, orderedList)
        } else {
            false
        }

        return MnemonicValidationResult(
            invalidWordIndices = invalidIndices,
            isValidWordCount = validCount,
            checksumValid = checksumValid
        )
    }

    /**
     * BIP39 checksum verification.
     *
     * Each word maps to an 11-bit index. The concatenated bits are:
     *   [entropy bits][checksum bits]
     * where checksumBits = entropyBits / 32.
     * The checksum is the first `checksumBits` of SHA-256(entropy).
     */
    private fun verifyChecksum(words: List<String>, orderedList: List<String>): Boolean {
        val wordIndexMap = orderedList.mapIndexed { i, w -> w to i }.toMap()

        // Convert each word to its 11-bit index.
        val indices = words.map { wordIndexMap[it] ?: return false }

        // Concatenate all bits.
        val totalBits = indices.size * 11
        val entropyBitsCount = (totalBits * 32) / 33   // = N * 32 / 33 (must be multiple of 32)
        val checksumBitsCount = totalBits - entropyBitsCount

        // Build a BitArray from the indices.
        val bits = BooleanArray(totalBits)
        for ((wordPos, idx) in indices.withIndex()) {
            for (bit in 0 until 11) {
                bits[wordPos * 11 + bit] = (idx shr (10 - bit)) and 1 == 1
            }
        }

        // Extract entropy bytes.
        val entropyBytes = ByteArray(entropyBitsCount / 8)
        for (byteIndex in entropyBytes.indices) {
            var b = 0
            for (bitInByte in 0 until 8) {
                if (bits[byteIndex * 8 + bitInByte]) b = b or (1 shl (7 - bitInByte))
            }
            entropyBytes[byteIndex] = b.toByte()
        }

        // Compute SHA-256 of entropy.
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(entropyBytes)

        // Extract the checksum bits from the hash.
        val computedChecksum = BooleanArray(checksumBitsCount) { i ->
            (hash[i / 8].toInt() ushr (7 - i % 8)) and 1 == 1
        }

        // Extract the actual checksum bits from the mnemonic bits.
        val mnemonicChecksum = BooleanArray(checksumBitsCount) { i ->
            bits[entropyBitsCount + i]
        }

        return computedChecksum.contentEquals(mnemonicChecksum)
    }
}
