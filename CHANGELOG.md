# Changelog

## 0.9.0
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

## 0.8.0
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


## 0.7.0
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

## 0.6.0
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

