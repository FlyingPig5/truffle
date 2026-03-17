# Changelog

## 0.8.0 — 2026-03-17
### Major Features
- **Ecosystem Activity Tab**: Entirely new "Ecosystem" tab added to the bottom navigation bar. Displays a live, paginated feed of all on-chain protocol activity: DEX swaps (Spectrum), LP swaps (USE LP, DexyGold LP), stablecoin mints/redeems (SigUSD, SigRSV, USE, DexyGold, FreeMint, ArbMint), and ERG stake / oracle actions. Transactions are sorted by recency with pull-to-refresh and infinite-scroll pagination.
- **Portfolio Overview Tab**: New "Portfolio" tab showing wallet token balances enriched with live DEX-derived USD values. Each token shows ERG price and calculated $USE value using the on-chain USE oracle rate.
- **ERG/USD Price Chart**: Swipeable chart card showing ERG price history using on-chain USE oracle price data, SigUSD oracle price, and SigUSD DEX price as three individually toggleable data series (SigUSD lines hidden by default; USE visible by default). Supports 24H, 7D, 30D, 3M, 6M, 1Y, 3Y, and MAX time ranges.
- **Token Pair Price Chart**: Selecting any DEX token from a dropdown in the chart card loads its pool's historical price (ERG per token) from pool state boxes. Chart auto-switches to the selected pair with a custom range-sampled line.
- **Latest Pool Trades**: When a token pair is selected in the chart, a new "Latest Trades" filter chip appears and is auto-selected. Shows the last ~15 trades derived from consecutive DEX pool state boxes — including BUY/SELL direction, ERG and token amounts, trader address (first 10 chars), relative time ("2m ago"), and a direct explorer link.
- **OraclePriceStore**: New dedicated store (`OraclePriceStore.kt`) that maintains in-memory price history for USE oracle, SigUSD oracle, and SigUSD DEX, serving chart data via configurable sampling.
- **NodePool**: New `NodePool.kt` multi-node manager that round-robins API calls across a pool of nodes, automatically skipping nodes that fail or time out.

### Enhancements
- **DEX Swap Color Coding**: Distinct colors for each swap type — Spectrum DEX Swap (cyan), USE LP Swap (light blue), DexyGold LP Swap (amber). Both EcosystemScreen and PortfolioScreen use the new palette.
- **Accurate LP Swap Labeling**: LP swap transactions now correctly identify USE vs DexyGold by inspecting pool NFT assets within the transaction, eliminating mislabeling.
- **Activity Filter Chips**: Ecosystem feed has filterable tabs — All, DEX Swaps, Stablecoins — plus a context-aware "Latest Trades" chip that appears only when a token pair is selected.
- **DEX Price in $USE**: Portfolio overview now shows each token's ERG price AND its calculated $USE value (e.g., "0.032 $USE"), rounded to 3 decimal places.
- **Chart Readability**: Improved chart date label and price tag font sizes. Added padding below the lowest price so the line doesn't hug the axis. Current-price tag uses a semi-transparent dark-blue background for legibility.
- **Bank Protocol Status UI**: Blocked operations now integrate their reason directly into an expandable "Protocol Status" section with an orange border and pulsing indicator. Auto-expands when mint/redeem is blocked. Header text changes to "MINT BLOCKED — TAP FOR DETAILS" or "REDEEM BLOCKED — TAP FOR DETAILS".
- **TVL Section**: Ecosystem tab pager includes a TVL (Total Value Locked) swipeable page alongside the price chart.
- **Icon Padding**: Improved centering of DEX bank icons with horizontal padding in both EcosystemScreen and PortfolioScreen transaction rows.
- **Node API Extension**: Added `GET /blockchain/transaction/byId/{txId}` endpoint to `NodeClient` for resolving trader addresses from pool swap transactions.

### Bug Fixes
- **Ecosystem Tx Loading State**: Loading indicator correctly distinguishes between initial load (full-screen spinner) and pagination (inline spinner at bottom of list).
- **Pool Trade Enrichment**: Trader addresses are resolved in a two-phase approach — pool-box deltas appear immediately, then tx-fetched addresses and real timestamps update the list asynchronously without blocking the UI.

## 0.7.5
### Major Features
- **Multi-Address Wallet Support (EIP-3)**: Mnemonic wallets now automatically scan derivation paths on import, discovering all active addresses (gap limit = 5). A new "Addresses" tab in the wallet screen lets users toggle which addresses are active, set a change address, add new derivation indices, and remove unused ones. Balances and transaction history aggregate across all selected addresses.
- **Transaction Review Redesign**: The swap confirmation screen now dynamically parses the prepared transaction data and shows a per-address net-change breakdown. Each address is labeled as YOUR WALLET, CONTRACT, APP FEE, MINER FEE, or EXTERNAL, with green/red coloring for gains/losses.
- **Network Security Toggle**: Added a "Allow HTTP Nodes" setting under Settings → Network Security. HTTP nodes are blocked by default; enabling requires explicit user acknowledgment via a security warning dialog. Unencrypted traffic warnings are shown when enabled.

### Enhancements
- **Bank Miner Fee Slider**: The Bank (Stablecoins) order details now include a Slow ↔ Fast miner fee slider (0.0011–0.2 ERG range), matching the DEX order details. Adjusting it affects the actual transaction fee for SigUSD, SigRSV, USE, and DexyGold mint/redeem operations.
- **Wallet Token Display**: Removed the expand/collapse toggle for token lists in the wallet screen — all tokens now display directly without truncation.
- **Transaction Detail Collapse**: Individual transaction inputs and outputs with ≥5 tokens now have a collapsible toggle showing the first 4 tokens with a "+N more tokens" link.
- **Favorites Toggle**: Added a "Show Favorites" setting to hide/show the favorites bar on the DEX screen. Disabled by default, persisted across sessions. The swap arrow offset adjusts dynamically when hidden.
- **Compact Top Bar**: Redesigned the top bar to a slimmer layout with just the Piggy icon on the left and loading/settings on the right, freeing vertical screen space.
- **Compact Bottom Nav**: Reduced the vertical height of the bottom navigation bar while preserving icon sizes for more screen real estate.
- **Removed Experimental Banner**: Removed the red "Experimental" warning banner from the Bank screen.
- **Debug Mode Toggle Removed from DEX**: The "Check TX" / "LIVE" toggle buttons have been removed from the main DEX tab — simulation mode is now exclusively controlled via Settings → Advanced.

### Bug Fixes
- **Token Selector Pair Leak (Critical)**: Fixed T2T pair names (e.g., "GORT-SigUSD", "Erdoge-kushti", "DORT-GORT") appearing as individually selectable tokens in the swap FROM/TO selector. Root cause: `tokens.json` entries for T2T pairs only contain `pid` (no `id_in`/`id_out`), so they bypassed the T2T filter. Fixed with a three-layer approach:
  - `loadCombinedTokens()` now loads `tokens.json` first as an immutable foundation, then merges synced data alongside without overwriting official keys. Synced T2T data enriches official entries with `id_in`/`id_out` fields.
  - `isTokenToToken()` enhanced with a heuristic: if a hyphenated name has both halves existing as separate individual token entries, it's identified as a T2T pair.
  - `loadPoolMappings()` uses the enhanced check on both key and display name.
- **Unverified Wallet Token Leaking**: Wallet tokens that only exist in T2T discovered pools (not direct ERG pools) no longer appear as selectable swap assets.
- **Node List Updated**: Replaced HTTP-only public nodes with HTTPS alternatives. Added `ergo-node-5.eutxo.de` and `node.ergo.watch`. Removed deprecated Cornell nodes.
- **`loadCombinedTokens()` Rewrite**: Complete restructure of the token loading pipeline — official tokens are now an immutable foundation loaded first, synced data enriches but never overwrites official entries, and non-official synced entries that collide with official names are suffixed.


## 0.7.0
### Enhancements
- **Bank UI Overhaul**: Redesigned the Bank (Stablecoins) screen with a cleaner layout — user enters desired mint amount first, with cost summary below.
- **Experimental Banner**: Added a red warning banner at the top of the Bank screen reminding users to validate output values.
- **Token Logos**: USE and ERG logos now display from asset files in the mint input panel and cost summary, replacing plain text badges.
- **Smart Cost Summary**: Replaced the swap-style dual-panel layout with a clean informational cost breakdown (total cost, fee lines, mining fee, wallet balance).
- **Insufficient Balance Warning**: Cost summary now highlights in red when wallet balance is insufficient, with an explicit warning message.
- **Smart ERG Formatting**: ERG values now strip trailing zeros while maintaining at least 3 decimal places (e.g., `0.002` instead of `0.002000`).
- **Oracle Price Display**: Protocol status now shows prices as "USE / ERG" (how many USE per 1 ERG) instead of raw nanoERG values.
- **LP Price Fix**: Fixed arbmint LP price display — was showing raw nanoERG per unit (~3,123,193), now correctly shows human-readable USE/ERG rate.
- **Cycle Reset Label**: Arbmint delay field renamed from "Delay (X / Y blocks)" to "Cycle reset" for clarity.
- **Bank Nav Icon**: Bottom navigation bar icon for Stablecoins updated to a bank icon (`\uE84F`).
- **Check Tx Mode**: Added a "Check Tx" toggle under Settings → Advanced that enables simulation mode — transactions are validated by the node but not broadcast to the network.

### Bug Fixes
- **Oracle Price Calculation**: Fixed a 1000x error in oracle price display caused by an unnecessary division by `USE_DECIMALS` before conversion.
- **Order Details Font**: Increased total cost font size in the collapsible Order Details panel for better readability.

## 0.6.0
### Enhancements
- **PID-Centric Logic**: Successfully migrated all trading and whitelisting logic to use unique Pool IDs (PIDs) as the primary identifier, enabling robust support for multiple pools of the same asset.
- **Dynamic Pool Renaming**: Users can now rename duplicate pools to avoid confusion (e.g., RSN vs RSN-Alt), with custom names preserved throughout the trading interface.
- **Advanced Verification States**: Refined the token selector to clearly distinguish between **Official**, **User Added**, and **Unverified** pools using visual labels and colors.
- **Transparent Fee Structure**: Redesigned the Swap Order Details into an expandable panel, providing a granular breakdown of LP Fees, Service Fees, and a user-adjustable Miner Fee via a new slider.
- **Smart Quote Engine**: Introduced debounced price fetching and a short-term pool cache in the Trader to significantly reduce network load and improve UI responsiveness.
- **Enhanced History Parsing**: Transaction history now attempts to resolve sender/receiver addresses from ErgoTrees and displays per-transaction service fees for better accounting.
- **High-Precision Balances**: Increased ERG balance visibility to 5 decimal places across the wallet and swap screens for better tracking of small amounts.

### Bug Fixes
- **Logo Resolution**: Fixed an issue where renamed pools would fail to show their official token logo; the resolution logic now correctly maps custom pool keys back to their underlying assets.
- **Selector Consistency**: Corrected behavior where the swap panel would roll back custom-renamed tokens to their official tickers.
- **Address Resolution**: Improved the history engine to correctly identify "Self" transfers and properly associate inputs/outputs with the active wallet even when address strings are missing from on-chain data.


## 0.5.5
### Enhancements
- **On-Chain Authority**: Restructured token whitelisting to use `tokens.json` as a PID registry. The app now pulls all live parameters (fees, decimals, token IDs) directly from the blockchain, ensuring immunity to static configuration errors.
- **Aesthetic Refinement**: Increased swap screen typography for better readability. Redesigned the Settings UI with a modern, soft-white branding section and integrated social links.
- **Transparency**: Added a dedicated LP Fee percentage display in the Swap Order Details for better cost visibility.
- **Global Feedback**: Promoted the synchronization progress UI to a global state, ensuring visibility across all app screens during data updates.
- **Optimization**: Refined the token discovery engine to deduplicate assets across multiple boxes and accurately report only truly new trading pairs.

### Bug Fixes
- **Token Management**: Fixed a critical bug in the drag-and-drop functionality when whitelisting newly discovered tokens.
- **Discovery Accuracy**: Resolved an issue where official tokens were occasionally reported as "new" during synchronization.
- **Sync Logic**: Fixed a crash related to unbalanced data structures during deep-scan analyst mode.

## 0.5.1
### Enhancements
- **AMM Accuracy**: Refined Automated Market Maker calculations to align perfectly with the original Python implementation.
- **Security**: Improved token synchronization priority—the built-in `tokens.json` now acts as a source of truth, overriding cached data to prevent spoofing of official tokens like DexyGold and USE.
- **Robustness**: Added comprehensive zero-division guards across all trading paths to prevent crashes on low-liquidity pools or small trade amounts.
- **Sync UI**: Added a spinning wheel and clearer batch progress indicators to the token synchronization screen.

### bug Fixes
- **Transaction Building**: Fixed "Not enough coins" error during DexyGold/USE swaps by correctly fetching and including the LP Swap box in transactions.
- **Fee Logic**: Corrected fee routing for special pools to ensure accurate price quotes and successful ledger balancing.
- **Performance**: Removed redundant network requests for special tokens already defined in system configuration.

## 0.5.0
* Initial release of PiggyTrade.

