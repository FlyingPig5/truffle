package com.piggytrade.piggytrade.stablecoin.use

/**
 * Configuration constants for the USE stablecoin protocol (Freemint + Arbmint).
 * Values taken directly from PigTrade-Simple/configurations/use_config.py
 *
 * Variable mapping:
 *   free_mint_token       → FREEMINT_NFT
 *   arbitrage_mint_token  → ARBMINT_NFT
 *   bank_nft              → BANK_NFT
 *   use_id                → USE_TOKEN_ID
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
object UseConfig {

    // ─── Token IDs ────────────────────────────────────────────────────────────

    /** NFT / token that identifies the Freemint contract box. (free_mint_token) */
    const val FREEMINT_NFT = "40db16e1ed50b16077b19102390f36b41ca35c64af87426d04af3b9340859051"

    /** NFT that identifies the Arbitrage Mint contract box. (arbitrage_mint_token) */
    const val ARBMINT_NFT = "c79bef6fe21c788546beab08c963999d5ef74151a9b7fd6c1843f626eea0ecf5"

    /** NFT identifying the Bank contract box. (bank_nft) */
    const val BANK_NFT = "78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"

    /** The USE stablecoin token. (use_id) */
    const val USE_TOKEN_ID = "a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669"

    /** Oracle price feed NFT. (use_oracle_nft) */
    const val ORACLE_NFT = "6a2b821b5727e85beb5e78b4efb9f0250d59cd48481d2ded2c23e91ba1d07c66"

    /** DORT Buyback contract NFT / token ID. (dort_buyback_id) */
    const val BUYBACK_NFT = "dcce07af04ea4f9b7979336476594dc16321547bcc9c6b95a67cb1a94192da4f"

    /** DORT reward token distributed by the buyback contract. (dort_reward_id) */
    const val BUYBACK_REWARD_NFT = "ae399fcb751e8e247d0da8179a2bcca2aa5119fff9c85721ffab9cdc9a3cb2dd"

    /** Tracker NFT used by the Arbmint oracle tracking box. (tracker_101_token) */
    const val TRACKER_NFT = "fec586b8d7b92b336a5fea060556cbb4ced15d5334dcb7ca9f9a7bb6ca866c42"

    /** Liquidity Pool token ID. (lp_id) */
    const val LP_TOKEN_ID = "4ecaa1aac9846b1454563ae51746db95a3a40ee9f8c5f5301afbe348ae803d41"

    // ─── Contract Addresses (ErgoScript P2S) ─────────────────────────────────

    /** Freemint contract P2S address. (free_mint_address) */
    const val FREEMINT_ADDRESS = "QKZSUxDcovcP6AijdheVE6TWrg2mSrUKc5P9J9N4Mq6fY22KriiPFPq8oXSaP6zdhrEmJwFNF4nKpSHvf2mhjUNeFCyoAME2k7TPsqSpi5M1SMj15d3NXSJZ1tmiC1ikyXeGxWzfdnNEDaEuwxVPkA7dNz3upUG7VaaHh4mKN7itU4MgPZ2JU2skEueU2bsehUCYoQMYHn3qN9vFmJih14VuyWuvShGSruNhKJ8jhsxxTyhJZgXmY2qEZeunhc1SaDTZ5LTG2c1so7bAEQDip6G7rNfMjkCjj1eAwFRPVwvLmk1moZmR3v53PKMusDqyExbLyE4PruJgLtDYekq5ZT2Eja28PrcXzbBNBa88sth4riJ4huKog1kT3hGq1128xmVghW9EiXDgSJagPVJT9o9YJQW1VgMyzSt7PyXvWodFCQBKa6fvE5wY4Cij1xCsCCatRr7pLMH6upTswV6AsS5bqDKmzMg9v7RF5nfmQt87SbQDJjzWaE3JeXpVPUmJQVWJGp8gDz2yTfy4Q8ykyvEnXcSyBxKPukvcuLLcXTMxVh3G4h4VbCRwCokDphF2cM3kkMfNWbtw7d311MpDQvkbYTkza5ZXVNqLZt5hkpKvQGUhb1dKHyWY6ah2mH1CVsBKBDDPPycTeJ6JWrr4rktQAzfyQDdbD1gvVMhtrrZWLxdPqurZzzYNThD2MfmEG5CQV1CRjP5wFpbCqqrmJmiuBMQJ2uFES3HJ5y551kN4unGNB3LtLd7E6eqnP3uV"

    /** Arbitrage Mint contract P2S address. (arbitrage_mint_address) */
    const val ARBMINT_ADDRESS = "CumBTcUofVg5SRyY9pi4CRuGfJtjiaQJkrPQhfQUQ4ndF4fMTD3XTUFkyg88NNC5xvXq6jCiL6aJPx43JTvyM76CJSV1WHnzKuNjzaMXi7QqaUMpKbfPc5vgLywaizFMacQ92N8gvXK6B2BJSsG1bGBtzZ6Z3WiFo31F6MX3P9ypeod7ogMMQnb4gCm5UBfjeuEtXtP19NSQj1SaGNkB46kd2JcZfx7PwAh13vCLuHbJiz6aFrpjVJ3NkaJhfyr7tEHDVKgfEAYtK2h2iGHyHpuKakMTdiZS55iWxr2LjnzjggFZiM6YhcWzcQopVPMgc32T1j9dWEFAupCnN5eQ7VchZKQXUX6ZfhQ1Cp6zxecqcSuWFwWQbpwLS8xenEKb7qS6aKpdLHPVKZLDiBbtre8g2SYm2peej4QrsNAsxkz172ztJSVxuWUzDqbsaqBGA4eZ595A6EroQC7oSiEqFbSan3pJBQ1g5a8Tt1X3sUCvaGW4owEaqmyHvL8UUtwXzkSDFhPDzsbm7KutXeB94porMoM4Zbh28hj9S7YxLytEDd75vf8YeiR2sRXsr4MSSmW7XWSmVKTYG3o67YVW1cEhzB3dUPhBFzw7LctGSHNbikhx5AVQLp6kG2DnxXyoXY68oJwcUqjA649xYMgdFykS5KwSDXySUL1kQr1kmXgCHwbtDgY3VhiTt8TRsXWsWPG3frYwrRSAhkznCNnTPbHaaU8nxy133x8micVCmHg9iapQA4WZTYDQne5n9eiSVqWbrDjtTfg4fsN8Ta7UwvaD2Cfu8iFeSRuVzS2EYbvkdo3TjhBmNaDsfzVinbN2omJuGyXNyrCFZpqN6nfLMsguCDZxgMQpctBougc1NskNXGerkjxnDjvRRBziAtCHRSZj3N"

    /** Bank contract P2S address. (bank_address) */
    const val BANK_ADDRESS = "L7ttnK2Comjkxxhyykdat7cCYLN7yrMJz6jCYmQGd5nu8Ma9mHi1JEiCNsxgmxAvDd5vuDMRkjiwQU11JHsizheespaEu4AaH41a2NzR2JbUsaTWVEg7jCBeMXCUbetnrsSLPCqZUb4PhnvE2sGV21E8LGyZyMjtWQqcauyB297d8d7aUCgKsbgZocqRsKZdeH185yxERavMEsb9R8ifqpbD4FVTNwWV6kixAQrMrwzp1wvheEk9t931iQXH9A2X4SJ4JR3eByqcHbWWAHoNs2gL2tpWa6fkVdCs2Kqgd7LgH7u9VFGEzACibuFzanQfNNZsic6Q1ndG97ebFoGVArfMNdvFMbxo1raYuqg4oFEeTY3aNXhhtgCfZWgt2AKz1mtKdZNLRBsWt83LKTiTQLrqBVNBurD2ojUnTV4r5deV"

    /** Buyback contract P2S address. (buyback_address) */
    const val BUYBACK_ADDRESS = "TBHrt7kYkmxqwSo2EEPDQkTn1R76iPw919ep4y1sZMqRN9SXLnm8FM2AvjVJ1mqLuD1Yuoaq9LVjrgwGNBoMyvyqqUuZpEY4rvy7oKEgyxjBU7oujwC3qVhTrSq733Yd2Fen6WGnDDePh9FfCyoDTnvFMcZWMZgR729VRnddfHHnYG7TUcjv5nmQHc2PeAFjfnGfb2MYdE31xfyJYNaf49ExmNJspee7sFt8NxPb742M3Nbr9ejZbcHGcWtiK78ck3aFwuJcdJvejk8zGWp3zmpEryryCAnAUnYrYEeatjQwn1Ke3T6g6NDK1Sy8dWCcTB2wzhZf8ZgRXAWGpfZMJdsCEQs5xaarkucSZKNhHoawJyU87s2fVhs7QfwnyvnT8s6vjWiWBsJLZ4AzBnrzNGmiH4nW6hJboe9dwan9AE55x48ZCxXErHxzkJf6dTmpa6YZ2u67gTcJF9LQg9nfx2sm1f7aQcnYWXiECayPHx6aC82bXxDhBiNLeWb6fXhJKjYuYcrEymvSohcbBfGiyNpZLuCQLsPcmu4MxdqtUTW7Y7QiuehW4c12Uvi7phfhpyDEVsyjDNM1URKFcaBDMdTzbLSi1cWg2afX9xSeQkg9VCQ1GghmBxsZWCDfwc82LYp5acDWqEMKg5m1xdxjaaudcyfGGz62T3DF3MHGzNBNNG3YVun1uAEfsijku3Lb1EG5AeKJodAJPCM2hoxPEXQ2nZkbtYhCF3Xib7yKfU1m2He4PVfPEaYaLa318CWSgMdEENJbCUVt19hkPJbgm7wHNfPGv9bfMekkrvmx8fHwWKAXuwQF2sivBJeRtMyVor5D7Fjw2mLJRnCKfJp1YDBS4XFLJQy5yzNpAUHxtpGjurwBeXJmWm6QADoVkMwWUfB1WyEckvJreA"

    // ─── Protocol Constants ───────────────────────────────────────────────────

    /** USE has 3 decimal places (denominator = 1000). */
    const val USE_DECIMALS = 1000L

    /** Mining fee in nanoErg (0.002 ERG). */
    const val DEFAULT_MINING_FEE_NANO = 2_000_000L

    /** Fee denominator for all protocol fees (FEE_DENOM). */
    const val FEE_DENOM = 1000L

    /** Bank fee numerator → 3/1000 = 0.3% (BANK_FEE_NUM). */
    const val BANK_FEE_NUM = 3L

    /** Buyback fee numerator → 2/1000 = 0.2% (BUYBACK_FEE_NUM). */
    const val BUYBACK_FEE_NUM = 2L

    /** Cycle length (offset) for Freemint reset height.
     *  Contract allows: successorR4 in [evalHEIGHT + T_free, evalHEIGHT + T_free + T_buffer]
     *  where T_free=360, T_buffer=5 (contract constants).
     *
     *  Using T_free + T_buffer = 365 (the maximum) maximises the window between when
     *  the tx is built (on the Review screen) and when the user actually signs it.
     *  The tx will succeed as long as signing happens within 5 blocks (~10 min) of build. */
    const val FREEMINT_CYCLE_BLOCKS = 365

    /** Minimum LP rate premium required for Arbmint: 101% (THRESHOLD_PERCENT). */
    const val THRESHOLD_PERCENT = 101L

    /** Minimum delay in blocks since tracker height for Arbmint (T_ARB). */
    const val T_ARB = 31

    /** Minimum nanoErg preserved in the Freemint contract box. */
    const val FREEMINT_BOX_VALUE_NANO = 1_000_000_000L // 1 ERG

    /** Minimum nanoErg preserved in the Arbmint contract box. */
    const val ARBMINT_BOX_VALUE_NANO = 1_000_000_000L // 1 ERG
}
