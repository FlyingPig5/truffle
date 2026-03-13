package com.piggytrade.piggytrade.protocol

object NetworkConfig {
    val NODES = mapOf(
        "Public1" to mapOf("url" to "https://ergo-node.eutxo.de"),
        "Public2" to mapOf("url" to "https://ergo-node-5.eutxo.de/"),
        "Public3" to mapOf("url" to "https://ergo1.oette.info"),
        "Public4" to mapOf("url" to "https://ergo2.oette.info"),
        "Public5" to mapOf("url" to "https://node.sigmaspace.io"),
        "Public6" to mapOf("url" to "https://node.ergo.watch"),
    )

    const val SPECTRUM_ADDRESS = "5vSUZRZbdVbnk4sJWjg2uhL94VZWRg4iatK9VgMChufzUgdihgvhR8yWSUEJKszzV7Vmi6K8hCyKTNhUaiP8p5ko6YEU9yfHpjVuXdQ4i5p4cRCzch6ZiqWrNukYjv7Vs5jvBwqg5hcEJ8u1eerr537YLWUoxxi1M4vQxuaCihzPKMt8NDXP4WcbN6mfNxxLZeGBvsHVvVmina5THaECosCWozKJFBnscjhpr3AJsdaL8evXAvPfEjGhVMoTKXAb2ZGGRmR8g1eZshaHmgTg2imSiaoXU5eiF3HvBnDuawaCtt674ikZ3oZdekqswcVPGMwqqUKVsGY4QuFeQoGwRkMqEYTdV2UDMMsfrjrBYQYKUBFMwsQGMNBL1VoY78aotXzdeqJCBVKbQdD3ZZWvukhSe4xrz8tcF3PoxpysDLt89boMqZJtGEHTV9UBTBEac6sDyQP693qT3nKaErN8TCXrJBUmHPqKozAg9bwxTqMYkpmb9iVKLSoJxG7MjAj72SRbcqQfNCVTztSwN3cRxSrVtz4p87jNFbVtFzhPg7UqDwNFTaasySCqM"
    const val SPECTRUM_TOKEN_ADDRESS = "3gb1RZucekcRdda82TSNS4FZSREhGLoi1FxGDmMZdVeLtYYixPRviEdYireoM9RqC6Jf4kx85Y1jmUg5XzGgqdjpkhHm7kJZdgUR3VBwuLZuyHVqdSNv3eanqpknYsXtUwvUA16HFwNa3HgVRAnGC8zj8U7kksrfjycAM1yb19BB4TYR2BKWN7mpvoeoTuAKcAFH26cM46CEYsDRDn832wVNTLAmzz4Q6FqE29H9euwYzKiebgxQbWUxtupvfSbKaHpQcZAo5Dhyc6PFPyGVFZVRGZZ4Kftgi1NMRnGwKG7NTtXsFMsJP6A7yvLy8UZaMPe69BUAkpbSJdcWem3WpPUE7UpXv4itDkS5KVVaFtVyfx8PQxzi2eotP2uXtfairHuKinbpSFTSFKW3GxmXaw7vQs1JuVd8NhNShX6hxSqCP6sxojrqBxA48T2KcxNrmE3uFk7Pt4vPPdMAS4PW6UU82UD9rfhe3SMytK6DkjCocuRwuNqFoy4k25TXbGauTNgKuPKY3CxgkTpw9WfWsmtei178tLefhUEGJueueXSZo7negPYtmcYpoMhCuv4G1JZc283Q7f3mNXS"

    val USE_CONFIG = mapOf(
        "id" to "a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669",
        "pid" to "4ecaa1aac9846b1454563ae51746db95a3a40ee9f8c5f5301afbe348ae803d41",
        "lp" to "804a66426283b8281240df8f9de783651986f20ad6391a71b26b9e7d6faad099",
        "fee" to 0.003,
        "R4" to "04ca0f",
        "dec" to 3,
        "pool_address" to "3W5ZTNTWAwgjcNhctkBccWeUVruJJVLATdYp1makMwoP78WiW2MDjMd2HKxZ2eUwtaSrhtRujuvi27k49msqFVAi7T2BsVHvMCHQ879nf5oJvuXjhEshf76EZgrijL3v3KcEA8CYi511YFtwN1b9u7ZUXeQSSUhqcMvyXMwaCZrpZsgCfbiLxk2DQMrngBMUh96vh7cBfPxZWhsZ9DGUtkGhiquqH3DcgFhpP33rRMjanCRXPAx9SbbphH3RBA2Z9K9j9TvWV6PnUafVGSpixUS8eawxUCiAuUAZHttXK9DjWqzeTDxDH9Tz1gSyjy7aKokwZyoAGTEafuiNQQrJ1UVfuVJCHPUD5v9eomJLmLVqdVDEUm7gj6Qj9a2cEKDfzedex977RkqXvuaeUdaumcikVCr9spzgmv7rhFCovdzAJscwTio98iRGS9rqcnUoTZFN6YmNJPXKe3krdQ7c9yvv74Ad7SBQmvNyuMkchFRnbPRozogKzV3xmTMxpLzagjQ1AdcP",
        "lp_swap_address" to "8W5UV9yEpKMQLuKzk7oDmaFEBGqeC1RGauuADViEfYJcs8x55ySXMKUUnSni3itEbscEo4qT8X2GuWY9zNdbYWWCqZmJjsFdynhWPc3FBtE45nrPgf4gqVzqN7RX9LpWJBTj97b4tkMxqMEL8QFDmLb8UzWKpp79MD94AziQvArc33KCQ9nYz3MafjrV3YACCxKcNbwgsKH1AuNUWoRLbFYVJvqzCJRiDHPboNcVSWTFotKkrm3yHZafyifT9BTD6Rs62V6UbiWHi2U4njP84wVyLFE5PvJemVJKy3Bc2MHwXBaoKVuLqZXJMu62nbjANBzHoZZ1cVmA4y",
        "lp_nft" to "ef461517a55b8bfcd30356f112928f3333b5b50faf472e8374081307a09110cf"
    )

    val DEXYGOLD_CONFIG = mapOf(
        "id" to "6122f7289e7bb2df2de273e09d4b2756cda6aeb0f40438dc9d257688f45183ad",
        "pid" to "905ecdef97381b92c2f0ea9b516f312bfb18082c61b24b40affa6a55555c77c7",
        "lp" to "cf74432b2d3ab8a1a934b6326a1004e1a19aec7b357c57209018c4aa35226246",
        "fee" to 0.003,
        "R4" to "04ca0f",
        "dec" to 0,
        "pool_address" to "3W5ZTNTWAwgjcNhctkBccWeUVruJJVLATdYp29mnGMCFZADaExRGC6PPrusg4wV6srzDrgkRHhzQWBsugmYxXRE54rsc41SRf87KKvE6NdPHmtYM3HWsE746kotBqQ1Nk1Mun3AHQUDEP3seLSa1DzWwuNx7HmBBn9ZxnbVCZy3UdX4PHmkbj9NtJkZH2Upz9o7S2txbaoSnSAA6zwUXoypxkRtAXvx9neoUhjng7EvyDtFJcyKbXFB8vDZNPvHd6yjL12JUZjxDVWAFgUhBecPjUM5LRYmsyHArunqSsEC9WRRuK3TGo9jJCbpEh527UyNkDvYnwhbJ9kwmSXEx69zNPez8tNn5hXZrqFa5BqrDqALYqkShBwmw1BmeZPoqHRWNANn72ZAMibrbz8if7gWNEJmYuA36bESriXiwUBVxkNVD79zSiyjkv8QTemdaTR6NvWAQEAdbhNn4eqvzEgAMnzbiWv6AMNAE36noWggRchCwnnvmnna7yRvjW5j5861w6dMU",
        "lp_swap_address" to "8W5UV9yEpKMQLuKzk7oDmaFEBGqeC1RGauuADViEfYJcs8x55ySXMKUUnSni3itEbscEo4qT8X2GuWY9zNdbYWWCqZmJjsFdynhWPc3FBtE45nrPgf4gqVzqN7RX9LpWJBTj97b4tkMxqMEL8QFDmLb8UzWKpp79MD94AziQvArc33KCQ9nYz3MafjrV3YACCxKcNbwgsKH1AuNUWoRLbFYVJvqzCJRiDHPboNcVSWTFotKkrm3yHZafyifT9BTD6Rs62V6UbiWHi2U4njP84wVyLFE5PvJemVJKy3Bc2MHwXBaoKVuLqZXJMu62nbjANBzHoZZ1cVmA4y",
        "lp_nft" to "ff7b7eff3c818f9dc573ca03a723a7f6ed1615bf27980ebd4a6c91986b26f801"
    )

    val KNOWN_PROTOCOLS = mapOf(
        SPECTRUM_ADDRESS to "DEX",
        SPECTRUM_TOKEN_ADDRESS to "DEX",
        (USE_CONFIG["pool_address"] as String) to "USE Pool",
        (DEXYGOLD_CONFIG["pool_address"] as String) to "DexyGold Pool",
        "MUbV38YgqHy7XbsoXWF5z7EZm524Ybdwe5p9WDrbhruZRtehkRPT92imXer2eTkjwPDfboa1pR3zb3deVKVq3H7Xt98qcTqLuSBSbHb7izzo5jphEpcnqyKJ2xhmpNPVvmtbdJNdvdopPrHHDBbAGGeW7XYTQwEeoRfosXzcDtiGgw97b2aqjTsNFmZk7khBEQywjYfmoDc9nUCJMZ3vbSspnYo3LarLe55mh2Np8MNJqUN9APA6XkhZCrTTDRZb1B4krgFY1sVMswg2ceqguZRvC9pqt3tUUxmSnB24N6dowfVJKhLXwHPbrkHViBv1AKAJTmEaQW2DN1fRmD9ypXxZk8GXmYtxTtrj3BiunQ4qzUCu1eGzxSREjpkFSi2ATLSSDqUwxtRz639sHM6Lav4axoJNPCHbY8pvuBKUxgnGRex8LEGM8DeEJwaJCaoy8dBw9Lz49nq5mSsXLeoC4xpTUmp47Bh7GAZtwkaNreCu74m9rcZ8Di4w1cmdsiK1NWuDh9pJ2Bv7u3EfcurHFVqCkT3P86JUbKnXeNxCypfrWsFuYNKYqmjsix82g9vWcGMmAcu5nagxD4iET86iE2tMMfZZ5vqZNvntQswJyQqv2Wc6MTh4jQx1q2qJZCQe4QdEK63meTGbZNNKMctHQbp3gRkZYNrBtxQyVtNLR8xEY8zGp85GeQKbb37vqLXxRpGiigAdMe3XZA4hhYPmAAU5hpSMYaRAjtvvMT3bNiHRACGrfjvSsEG9G2zY5in2YWz5X9zXQLGTYRsQ4uNFkYoQRCBdjNxGv6R58Xq74zCgt19TxYZ87gPWxkXpWwTaHogG1eps8WXt8QzwJ9rVx6Vu9a5GjtcGsQxHovWmYixgBU8X9fPNJ9UQhYyAWbjtRSuVBtDAmoV1gCBEPwnYVP5GCGhCocbwoYhZkZjFZy6ws4uxVLid3FxuvhWvQrVEDYp7WRvGXbNdCbcSXnbeTrPMey1WPaXX" to "SigmaUSD Bank",
        "L7ttnK2Comjkxxhyykdat7cCYLN7yrMJz6jCYmQGd5nu8Ma9mHi1JEiCNsxgmxAvDd5vuDMRkjiwQU11JHsizheespaEu4AaH41a2NzR2JbUsaTWVEg7jCBeMXCUbetnrsSLPCqZUb4PhnvE2sGV21E8LGyZyMjtWQqcauyB297d8d7aUCgKsbgZocqRsKZdeH185yxERavMEsb9R8ifqpbD4FVTNwWV6kixAQrMrwzp1wvheEk9t931iQXH9A2X4SJ4JR3eByqcHbWWAHoNs2gL2tpWa6fkVdCs2Kqgd7LgH7u9VFGEzACibuFzanQfNNZsic6Q1ndG97ebFoGVArfMNdvFMbxo1raYuqg4oFEeTY3aNXhhtgCfZWgt2AKz1mtKdZNLRBsWt83LKTiTQLrqBVNBurD2ojUnTV4r5deV" to "USE Bank",
        "4EQTxw3MSn9fDbvzUN4qmbe6zFBp6M" to "Duckpools"

    )

    val DEFAULT_FAVORITES = listOf("ERG", "SigUSD", "USE", "DEXYGOLD", "rsADA", "kushti", "RSN","SigRSV")
}
