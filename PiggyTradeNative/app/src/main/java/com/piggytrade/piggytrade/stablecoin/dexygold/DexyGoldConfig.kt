package com.piggytrade.piggytrade.stablecoin.dexygold

/**
 * Configuration constants for the DexyGold stablecoin protocol (Freemint + Arbmint).
 * Values taken directly from PigTrade-Simple/configurations/dexygold_config.py
 *
 * Variable mapping:
 *   free_mint_token       → FREEMINT_NFT
 *   arbitrage_mint_token  → ARBMINT_NFT
 *   bank_nft              → BANK_NFT
 *   use_id                → DEXY_TOKEN_ID
 *   use_oracle_nft        → ORACLE_NFT
 *   dort_buyback_id       → BUYBACK_NFT
 *   dort_reward_id        → BUYBACK_REWARD_NFT
 *   tracker_101_token     → TRACKER_NFT
 *   lp_id                 → LP_TOKEN_ID
 *
 *   free_mint_address     → FREEMINT_ADDRESS
 *   arbitrage_mint_address→ ARBMINT_ADDRESS
 *   bank_address          → BANK_ADDRESS
 *   buyback_address       → BUYBACK_ADDRESS
 */
object DexyGoldConfig {

    // ─── Token IDs ────────────────────────────────────────────────────────────

    /** NFT that identifies the Freemint contract box. */
    const val FREEMINT_NFT = "74f906985e763192fc1d8d461e29406c75b7952da3a89dbc83fe1b889971e455"

    /** NFT that identifies the Arbitrage Mint contract box. */
    const val ARBMINT_NFT = "3fefa1e3fef4e7abbdc074a20bdf751675f058e4bcce5cef0b38bb9460be5c6a"

    /** NFT identifying the Bank contract box. */
    const val BANK_NFT = "75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f"

    /** The DexyGold token. */
    const val DEXY_TOKEN_ID = "6122f7289e7bb2df2de273e09d4b2756cda6aeb0f40438dc9d257688f45183ad"

    /** Oracle price feed NFT. */
    const val ORACLE_NFT = "3c45f29a5165b030fdb5eaf5d81f8108f9d8f507b31487dd51f4ae08fe07cf4a"

    /** DORT Buyback contract NFT / token ID. */
    const val BUYBACK_NFT = "610735cbf197f9de67b3628129feaa5a52403286859d140be719467c0fb94328"

    /** DORT reward token distributed by the buyback contract. */
    const val BUYBACK_REWARD_NFT = "7ba2a85fdb302a181578b1f64cb4a533d89b3f8de4159efece75da41041537f9"

    /** Tracker NFT used by the Arbmint oracle tracking box. */
    const val TRACKER_NFT = "4675c1819c3e22add72b73f4b7e83eb743d45013b4ee2d8a63e215de9bc6f57f"

    /** Liquidity Pool token ID. */
    const val LP_TOKEN_ID = "905ecdef97381b92c2f0ea9b516f312bfb18082c61b24b40affa6a55555c77c7"

    // ─── Contract Addresses (ErgoScript P2S) ─────────────────────────────────

    /** Freemint contract P2S address. */
    const val FREEMINT_ADDRESS = "2mvvBUNih47dAVouDAzQTao62nFGoB7Mb7NQ582TYRDjipgtUM6QmkqAfY687pCSiFk3B2NxqHrTwLWBFgX3s15q8eVwCB3MSuSsZq56gcujhK3Gy9Y4TpHo1RkYFDrThrrFeQqFFjVYp9Fv4a5RGGrCnHLS9ovTmKiehrPo4HQ6qL5H9pkUj5477fwbpervhffPqwjSirGHNNxSd6JxMhv4askmTKKQw5RfnxywnvCKm923V9brZMS2VaJR3Zek5AaQHDfHQ94jLpvanKrwjN7GooUndABj9HDk2azPveUpcA99wUow3Nytws4cvDHogA9YPfHDc2wTPKpfoyTkY3GVM7JxTrFp6yrrRniXGwRTrtgLdFGsDSC3GocvTyUn1itrvL66gmznXKubcimrK77Vens6dphBtHrRE3F8XqhdYwwonKepPyyTHqEqVid7Kh54Je6upWQXM4RZ144WdRMfYDmWrVmy8kppbPHZ23irCJoSdTzu1mDZnZscDSuwS3iR4F18zQm8m43k5a8kKp8b1rQGrpcg8QNfwqV77DtExdEzovrwb6Ak2heQ3iNYjWeKvpTJrhGqiNPMpRjuurX2cZPEvNZUg1gUGpJSMMGszFV3XfiefQdGRfvqqr8Y2yz1JzqsatFoq2xpVjW9m6mYfSMsCkMc3pHMmxcGcGi6X91vqWMrsVFAARVgcexDxZcKwVUGST7pYUQMEe8HaezuK4mNTQxwQBSVdjbigREVU4B8VFt6oXhFWkTXFEjui9"

    /** Arbitrage Mint contract P2S address. */
    const val ARBMINT_ADDRESS = "uZt1ATbixuZUbFETdSVaYJsRchfjGZdVSNg4wXgex88vDTAPdrzVgQaeuJF5VsjCdsXtSoGtVdhELFYhjvVaw7ek4sp61vC8YVQjCqFvByJaFWxX28S4PpZR7zjyZf7DqYsCnN5gJku871FwDnQGSqSHn2Z7HJxSDkipHgEBvPLDKqsTBDf6yU3YchFA2GGFNBkHKJxA2sCoJYJCVyAhDg8oBeAYzRNXtJNsdVu2o2T3DEZZBRrdzBMnHd4uhUCDUv5jQfhKLafBzv6F8zHn1ezwdGn5QfWVVr7tGmYg5sjijJNBAeTu6vy45k3Azjp4Wgbk77oQLTdjdeSfZXFmodywTGejPR5WJe3sDgXvXqyYgZexZqmmvcqzp22PG2LEfsJrvkcg9hwU6UX17Y7HrcTnJuox3M7d2BEpBMwCaPZFwtiqCtr6XSmWyEr6Bx3jog931yRgPwCAtJvEfJTAk1ptDzhyUqZZvWr9zh1Z7khuCWF4GUKw2enWLcJRRze8qdzoGd5GzYb8QN75F2GreGHc42551DzzBqzLqcPYJad498eWGjFh9p8LkQywhqyZCbjmT4fY4DJefFp91sLApCAiBrTfjrzFVi3ekSCn7kFGxmX3L3BK23P1YzoXNxCdeZHAnF3Y8Dom5xEc3qtB4VcH9dE9GoKFja4EUWGjySBqw8LifWoVZroZT5zbMkz9zFHeNwknNbaBxSo97Ef8LMPeMteu3RzSXoahPgsCwo7i8SfYhbDLB5MDmadRmeBpC77dbZcTyXksg4yH6HG75QoBZ9gFmDgjFGexSHNW1RxB1ey1mak9rbByhPAfgJH8fay1t5Gfn11hcCUZ88k5HMRjTT7pJ8atsVVrE94UALBwVnVCE7cqCiBtgdLmNDybuiBMtHZ"

    /** Bank contract P2S address. */
    const val BANK_ADDRESS = "L7ttnK2Comjkxxhyykdat7cCYTJkNHjG7AxiKggp3AFxCkBDbi5o29egZacptbLXNXGQAKucR7kuifFVTeNBsFmnngcz9vqcoCC3xNZ6N8ZxRZYxQxQCa1mRdP7agaXy86QLVZVw5cYdWv8j4ZdfLHdtEUAbvSzZ8Q5MfAi7LdohcDZbPyv7krHJtRQsVWMEVJ5rGb5BesuhEi8ThqLFCuDwYDsHrPFfiSTZNoiAeDMAq5LbCp3JbrJrWMyCaM6avLAZw4hekfhGNN1dN1k1SZNJNc13qvDkjr2aE5JC78U9Ynre45GdQjF9H3dniDqCzBTY8dhrkQdh7haBcKTRe5uEcLpS5u4cRENNGf6fjt1uvyxdiLervnZq5RKQ9wcjwYDGSRjX4s3tu9czYY4X5LCxT5w7PxwWCmhSaw6fSGN5"

    /** Buyback contract P2S address. */
    const val BUYBACK_ADDRESS = "43xhWcMKGeNA8eJ1oFuJGzcCyiBH84t5ViuMLx5BddqmAew1JjhEXkiL5bJsdBktBhvFkpyFb5WJ5m24jT1Kz978h8Mb9Z61feYbCZjLTuF2skSCfQWvsrXpcsTGY2pnVpZn4fe32CijGymD2M4UKdc1YMU8fh6TRJJUfHCra9xkX98cajwsrWUE6aFC7Ck7y4rKA9vNSNsBr1sCFt2i5dj7Cci5Ez3F8QZ8xMkZioAGh6MCvupHuPefnkPXtqBtZxwZ6ve5Nk24oudcRagnepk3ipTMRyxA57sfaNwXAij98doHk2mBU7Li51TD3REpjifjUPmA6DX1U3UMCnuDgAZzdSkdgxanPWzRbxbxEoDyxrieN7TQyirHK9dBpp6iJjaE6ave1Y8TZWCizBvWUsXcEuLWGZczDumdstzDs6zvLk4f8MWTVRSkqorwFfvDRSYK26TbsUbD5dLUw4QW8QCYVzER542syvMKp1MNUyM8bo9kRD2Vtb75V3eZV1Camr1h46nMQLLw3bUq2T9P1wRqCHb7kfqKpR1hDLMqmpEeNNig4WJ6BVnBqzfT7MVesYinRco881NDvJnVi22JNx7CXdFKjWm88fG3UcjmBpG8LWedKiybSv3GxZdf9reZrigTZpMyBgwc5YWN4uF9pKWVr25ZHMnqZK8r5FdbrW5x4k8bW1hoLCe5aXLKXzBYyBNt1wZk33qCE4gJ5qu4dyD9eR1F32vY787vBYbSXrsNiF5iN1MhSJysFy4pQVvePMrxePQTEuqYytJCDGFxbaMiU3mai1GwJVTgnQysQWceSE959HgYBxALsTGisWGCjjfYN7QxnjPbbD36ybsei9VhYXu6"

    // ─── Protocol Constants ───────────────────────────────────────────────────

    /** DexyGold has 0 decimal places (denominator = 1). */
    const val DEXY_DECIMALS = 1L

    /** Oracle rate divisor: oracle R4 is divided by 1_000_000 (vs USE's 1000). */
    const val ORACLE_DIVISOR = 1_000_000L

    /** Mining fee in nanoErg (0.0011 ERG). */
    const val DEFAULT_MINING_FEE_NANO = 1_100_000L

    /** Fee denominator for all protocol fees. */
    const val FEE_DENOM = 1000L

    /** Bank fee numerator → 3/1000 = 0.3%. */
    const val BANK_FEE_NUM = 3L

    /** Buyback fee numerator → 2/1000 = 0.2%. */
    const val BUYBACK_FEE_NUM = 2L

    /** Cycle length for Freemint reset height (362 blocks, ~12 hours). */
    const val FREEMINT_CYCLE_BLOCKS = 362

    /** Minimum LP rate premium required for Arbmint: 101%. */
    const val THRESHOLD_PERCENT = 101L

    /** Minimum delay in blocks since tracker height for Arbmint. */
    const val T_ARB = 31

    /** Minimum nanoErg preserved in the Freemint contract box (1 ERG). */
    const val FREEMINT_BOX_VALUE_NANO = 1_000_000_000L

    /** Minimum nanoErg preserved in the Arbmint contract box (1 ERG). */
    const val ARBMINT_BOX_VALUE_NANO = 1_000_000_000L
}
