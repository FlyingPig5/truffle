<p align="center">
  <img src="branding.png" width="600">
</p>

Crypto trading application on the Ergo blockchain for Android, built using the best programming language ever—**Python**!

## Development

### Build
The compiled APK can be downloaded from the [GitHub Releases](https://github.com/FlyingPig5/piggytrade/releases), but for total security, we recommend you compile your own using the provided script:
```bash
./build_android.sh
```

## Overview

PiggyTrade is a high-performance mobile trading client for the Ergo blockchain. It is designed for traders who prioritize **speed and efficiency** over a vast feature set. By focusing on the most liquid pairs and providing a streamlined interface, PiggyTrade allows you to execute swaps in seconds.

> [!WARNING]
> **BETA SOFTWARE**: PiggyTrade is currently in beta. Users are expected to verify all transaction details and output amounts before confirming. Use at your own risk!!!

---

## User Guide

### 1. Setting Up Your Wallet
PiggyTrade offers two ways to interact with your funds:
*   **ErgoPay (Recommended):** Best for security. You only provide your public address. When you initiate a trade, the app generates a request that you sign using your preferred wallet (for example the official [Ergo Mobile Wallet](https://github.com/ergoplatform/ergo-wallet-app)) via a deep link. This means **your mnemonic never enters this app.**
*   **Mnemonic (Hot Wallet):** For maximum speed, you can import your 12/15/18-word mnemonic. It is encrypted locally with a password of your choice. This allows you to sign transactions directly within PiggyTrade.

### 2. Favorites Grid
The main screen features a "Favorites" grid for one-tap asset selection.
*   **Toggle Edit:** Turn on the "Edit" switch to change which assets appear in your grid.
*   **Quick Swap:** Tap a favorite to immediately set it as the "To" asset.

### 3. Execution Modes
PiggyTrade includes a **Debug Mode** (toggleable in Settings) which reveals:
*   **Check TX (Safe):** Simulates the transaction without submitting it to the blockchain.
*   **LIVE:** Submits the transaction directly.
*   **Mempool/LP Toggles:** Advanced controls to include or exclude unconfirmed balances and liquidity.

### 4. Node Configuration
The app comes prepopulated with several public Ergo nodes. However, for maximum reliability and speed, you can add your own custom node URL in the **Settings** menu.
---

### 5. Fees
The app uses a fee of 0.0001 ERG for each trade under 10 ERG. 
For trades over 10 ERG, the fee is 0.05% up to max 1 ERG (yeah, it's CHEAP!!)
Token to Token trades are free!
---

## Philosophy: Speed vs. Features

PiggyTrade is built for **Speed**. To achieve this, we make the following trade-offs:
*   **Hardcoded Metadata:** To avoid slow blockchain scans at startup, token metadata and liquidity pool locations are cached/hardcoded.
*   **Token Name Caching:** Token names and IDs are cached locally on your device. If you have a wallet with hundreds of tokens, the very first load might take a few seconds as it builds the cache, but every subsequent load will be significantly faster.
*   **Connectivity Dependent:** App performance (balance fetching, quote retrieval) is directly tied to your node connection. Using a local node or a high-performance private node will result in the best experience.
*   **Manual Updates:** While the app doesn't "watch" for every new pool automatically, you can always check for and import the latest verified token list in the **Settings > Token List Management** menu.
*   **Minimalist UI:** We provide exactly what you need to trade, keeping the interface snappy even on older hardware. It won't win any awards for design, but it should be good enough.
---

## Folder Structure
- `src/piggytrade`: Core Python source code.
- `resources/`: High-res icons, splash screens, and generated Android variants.
- `src/piggytrade/resources/`: Runtime assets (fonts, token logos, runtime icon).