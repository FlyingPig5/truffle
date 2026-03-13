# PiggyTrade — Security Audit

**Date:** March 2026  
**Scope:** Full static source code analysis of the PiggyTradeNative Android application  

---

## Overall Verdict: ✅ Safe to Use

| Category | Rating | Summary |
|---|---|---|
| **Mnemonic/Key Security** | 🟢 Strong | Industry-standard SCrypt + AES-CBC + HMAC, or Android Keystore AES/GCM |
| **No Secret Exfiltration** | 🟢 Clean | Mnemonics never leave the device; signing is local-only; zero analytics/telemetry |
| **Transaction Integrity** | 🟢 Sound | Standard constant-product AMM math; all data fetched from chain; no manipulation |
| **Developer Fee** | 🟢 Disclosed | Small app fee labeled **"App Fee"** in UI before every transaction |
| **Network Security** | 🟢 Strong | HTTPS enforced by default; cleartext HTTP requires explicit opt-in via Advanced Settings |
| **Debug Logging** | 🟢 Resolved | All debug logs guarded with `BuildConfig.DEBUG` — silent in release builds |
| **Backup Configuration** | 🟡 Acceptable | `allowBackup=true` but encrypted with device-bound Keystore keys |

> **The application does NOT steal funds, exfiltrate secrets, or contain malicious transaction manipulation.** All value flows are mathematically correct and verifiable on-chain. The user reviews every transaction before signing.

---

## 1. How Your Mnemonic Is Protected

PiggyTrade supports two wallet security modes:

### Password-Protected Wallets
- **Key Derivation:** SCrypt (N=32768, r=8, p=1) — computationally expensive, resists brute-force
- **Encryption:** AES-128-CBC with HMAC-SHA256 authentication (Fernet format)
- **HMAC verification** uses constant-time comparison to prevent timing attacks
- **Unique salt and IV** generated via `SecureRandom` for every wallet

### Biometric Wallets
- **Key Storage:** Android Keystore (hardware-backed on supported devices) — keys are non-extractable
- **Encryption:** AES/GCM/NoPadding — authenticated encryption
- **Biometric gate** requires fingerprint or face authentication before any key usage

### Key Security Properties
- ✅ Mnemonic is **never stored in plaintext** at rest
- ✅ Mnemonic is only decrypted **immediately before signing**, as a local variable
- ✅ Signing is performed **entirely locally** via native JNI library
- ✅ After signing, the mnemonic goes out of scope (eligible for garbage collection)
- ✅ ErgoPay (external wallet) is offered as an alternative — **no mnemonic required**
- ✅ All preferences stored via `EncryptedSharedPreferences` (AES-256)

---

## 2. Transaction Integrity

### AMM Math
The app uses the standard **x · y = k** constant product formula with `BigDecimal` precision 100 and `BigInteger` arithmetic — **no floating-point truncation or overflow risk**.

### Data Sourcing
- **All pool and oracle data** is fetched live from the Ergo blockchain via the user's selected node
- Pool boxes are identified by their **immutable NFT token IDs**
- There is **no external backend, API, or centralized service** in any transaction path

### Stablecoin Protocols
All protocol interactions (USE Freemint, USE Arbmint, SigmaUSD, DexyGold) have been independently verified to match their published on-chain contract semantics, including fee calculations, register encoding, and height constraints.

### Transaction Signing
- Signing is performed entirely locally via JNI (`WalletLib.signTransactionJson`)
- ErgoPay transactions construct a reduced transaction and open an `ergopay:` URI for external wallet review
- **Mnemonic is never stored by the signer** — it is received only as a function parameter

---

## 3. Developer Fee

| Aspect | Assessment |
|---|---|
| Is user money stolen? | ✅ **No** — fee is a known output, visible on-chain, sent to a fixed address |
| Is the fee disclosed? | ✅ **Yes** — shown as "App Fee" in the fee breakdown before every transaction |
| Is the fee reasonable? | ✅ **Yes** — 0.05% for swaps, 0.1% for bank operations (industry: 0.3%–1.0%) |
| Can the user see the fee before confirming? | ✅ **Yes** — displayed on the review screen before signing |
| Does the fee address receive only ERG? | ✅ **Yes** — user tokens are never diverted |
| Could the fee change without an update? | ✅ **No** — embedded in the binary; requires an APK update |

---

## 4. Network & Privacy

### Zero Telemetry
A thorough search of all source files confirms:
- ✅ No analytics SDKs (Firebase, Mixpanel, etc.)
- ✅ No crash reporting services (Crashlytics, Sentry, etc.)
- ✅ No external API calls other than user-selected Ergo nodes
- ✅ No WebView, data collection, or advertising libraries

**The only network calls the app makes are to your chosen Ergo node.**

### Network Security
- All default nodes use HTTPS
- Cleartext HTTP is available only via an explicit **Advanced Settings** toggle with a clear warning — designed for users running a local Ergo node on their home network
- No mnemonics or private keys are ever transmitted over the network

### Permissions
The app requests only `INTERNET` and `USE_BIOMETRIC` — the minimum necessary.

---

## 5. Threat Model

| Threat | Mitigated? | How |
|---|---|---|
| Mnemonic theft via app compromise | ✅ | AES encryption + Android Keystore; never in plaintext at rest |
| Network eavesdropping (MITM) | ✅ | HTTPS by default; HTTP requires explicit opt-in with warning |
| Transaction manipulation by app | ✅ | Standard AMM math; all outputs verifiable on-chain; user reviews before signing |
| Physical device access | ✅ | Biometric gate or password required for every transaction |
| Fund theft by developer | ✅ | Impossible — developer never has access to your mnemonic; signing is local-only |
| Rogue node returning fake data | ⚠️ Partial | User chooses node; pool boxes verified by NFT ID; node could return stale data |
| Clipboard snooping | ⚠️ Standard | App allows paste for mnemonic entry; Android 13+ shows clipboard access notifications |

---

## 6. Recommendations for Users

1. **Use biometric authentication** where available — keys are hardware-protected
2. **Use ErgoPay** (external wallet) when possible — no mnemonic stored in the app
3. **Choose a strong password** (8+ characters) if using password-protected wallets
4. **Use HTTPS nodes** (the default) — only enable HTTP nodes if connecting to your own local node
5. **Keep Android updated** to benefit from the latest Keystore security improvements

---

## Conclusion

PiggyTrade demonstrates **solid security engineering** for a self-custody crypto wallet:

- ✅ Mnemonics are properly encrypted using industry-standard primitives
- ✅ No secrets ever leave the device — all signing is local via native JNI
- ✅ Zero telemetry, analytics, or external APIs — purely client-to-node
- ✅ Transaction math is verifiable and follows standard AMM formulas
- ✅ Developer fee is disclosed, transparent, and competitive with industry rates
- ✅ Every transaction is reviewable before signing

**You can confidently use PiggyTrade** knowing that your seed phrases are securely encrypted, your funds are protected by standard cryptographic primitives, and all transactions are transparent and verifiable on-chain.
