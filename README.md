<p align="center">
  <img src="branding/branding_landscape.jpeg" width="700">
</p>

Open-source crypto trading app for the **Ergo blockchain** on Android — swap tokens, mint stablecoins, track your portfolio, and follow live on-chain activity, all from your pocket.

> [!WARNING]
> **BETA SOFTWARE**: Truffle is currently in beta. Always verify all transaction details and output amounts before confirming. Use at your own risk!
>
> As an **Open Source** project, the community is invited to audit the code for vulnerabilities. Transparency is key to building a secure tool for everyone.
>
> 🔍 The codebase has been passed through **Claude Opus**, one of the most capable AI coding models available, for a comprehensive security assessment. Read the full report: [Security_Audit.md](Security_Audit.md)

---

## Get the App

Download the latest compiled APK from [GitHub Releases](https://github.com/FlyingPig5/truffle/releases), or build it yourself for maximum trust.


---

## What is Truffle?

Truffle is a fast, self-custodial mobile trading client for the Ergo blockchain. It's built for traders who want to **act quickly without giving up control** — you stay on-chain, you hold your keys, and every swap lands directly in a DEX pool with no middlemen.

It supports **Spectrum/ErgoDEX** liquidity pools for token swaps, and the **USE**, **SigUSD/SigRSV** and **DexyGold** stablecoin protocols for minting and redeeming. Transactions can be signed securely via **ErgoPay** (recommended), or you can import a mnemonic and sign directly in the app.

---

## Features at a Glance

### 💱 DEX Trading
Swap ERG ↔ Token, Token ↔ ERG, and Token ↔ Token across all Spectrum pools. The app has a pre-approve whitelist, but you can discover and add new pools using user whitelist. All data is fetched from the blockchain. Price quotes update as you type, including **price impact** and **LP fee** breakdowns. A customizable **miner fee slider** gives you full control over transaction speed.

### 📊 Portfolio Overview
See your full wallet balance enriched with **live DEX prices** — each token shows its current ERG price and its equivalent **$USE value** so you always know what your holdings are worth in real terms.

### 📈 Price Charts
A swipeable chart card shows **ERG/USD price history** from multiple on-chain sources: the USE oracle, the SigUSD oracle, and the SigUSD DEX pool. You can also select any individual token from the dropdown to view its **historical ERG price** from pool state data. Time ranges from 24H all the way to MAX.

### 🔄 Latest Trades
When you select a token pair in the chart, a **Latest Trades** view appears — showing the last ~15 real on-chain swaps with direction (buy/sell), amounts, trader address, time ago, and a direct explorer link.

### 🌍 Ecosystem Activity
A live feed of **everything happening on-chain** across the Ergo DeFi ecosystem — DEX swaps, stablecoin mints and redeems (SigUSD, SigRSV, USE, DexyGold), LP actions, and oracle updates. Filter by category, pull to refresh, and scroll infinitely.

### 🏦 Stablecoins (Bank Tab)
Mint and redeem **SigUSD**, **SigRSV**, **USE**, and **DexyGold** directly from the app. The Bank tab shows live protocol status, reserve ratios, available capacity, and fee breakdowns. Blocked operations show an expandable explanation so you always know why something isn't available right now.

### 📤 Send ERG & Tokens
Send ERG and tokens to one or multiple recipients in a single transaction. The Send screen supports **QR code scanning** for addresses, a **token multiselect** showing all tokens in your wallet, and a **miner fee slider**. A full review screen shows you exactly what's going out before you sign.

### 📱 ErgoPay Deep Linking
Truffle handles `ergopay:` URIs natively — scan a QR code from any dApp or click an ErgoPay link and the app opens the transaction for review. A detailed **per-address breakdown** shows net changes for your wallet, contracts, fees, and external addresses before you confirm.

### 👛 Multi-Address Wallet Support
Import a mnemonic and the app automatically scans your derivation path to find **all your active addresses**. You can pick which addresses to trade from, set a change address, and see balances aggregated across all of them. Transaction history covers every address in your wallet.

### 🔐 Wallet Security
- **ErgoPay (Recommended):** Add your public address only. Trades generate a signing request for your external wallet — your mnemonic never enters this app.
- **Mnemonic (Hot Wallet):** Import your seed phrase and encrypt it with a password or with **biometrics** (face/fingerprint). Transactions are signed locally on your device.

---

## Fees

**DEX Swaps (Spectrum pools)**

| Trade Size | App Fee |
|---|---|
| Under 10 ERG | 0.0001 ERG flat |
| Over 10 ERG | 0.05% of trade value |
| Token ↔ Token | **Free!** |

> The Spectrum LP fee (0.3%) is separate and goes directly to liquidity providers.

**Stablecoin Mints & Redeems (Bank)**

| Protocol | Fee |
|---|---|
| App fee | 0.1% of transaction value |

---

## User Guide

### Setting Up Your Wallet
Go to the **Wallet tab** and tap the `+` button. Choose ErgoPay (address-only, safest) or import a mnemonic. On mnemonic import, the app scans the blockchain to find all your active addresses automatically.

### Favorites Grid
The DEX screen has a quick-pick **Favorites grid** — tap any asset to instantly set it as your swap target. Tap the Edit toggle to customise which tokens appear. You can configure how many favorites are shown in Settings.

### Sending ERG & Tokens
Tap **Send** from the Wallet tab. Enter a recipient address (or scan a QR code), set the ERG amount, and optionally add tokens from your balance. Add multiple recipients if needed. Review the transaction summary, then sign with your password or biometrics.

### Node Configuration
Truffle ships with a set of public Ergo nodes and automatically distributes requests across them for speed and resilience. You can add your own node URL in **Settings → Network** for maximum reliability.

### Check TX Mode
In **Settings → Advanced**, you can enable **Check TX mode** — transactions are validated by the node but never broadcast. Useful for verifying amounts before going live.

---

## Philosophy: Speed First

Truffle is built to be **fast and direct**:
- Pool data and token metadata are cached locally so the app is ready instantly on launch.
- The node pool distributes read requests across multiple nodes in parallel for snappy quotes and balance fetches.
- The UI updates progressively as data arrives — you see prices and balances as soon as they're ready, not after a full sync completes.
- You can always refresh the token list manually in **Settings → Token List** to pick up newly added pools.

---

## Credits
Built on the [Ergo blockchain](https://ergoplatform.org). Transaction signing powered by [sigma-rust](https://github.com/ergoplatform/sigma-rust). DEX contracts by [Spectrum Finance](https://spectrum.fi).