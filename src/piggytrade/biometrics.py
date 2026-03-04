# biometrics.py - Chaquopy static_proxy module for biometric authentication
#
# IMPORTANT: Chaquopy's static proxy generator requires:
# 1. Java classes imported via the import hook (NOT jclass)
# 2. @Override decorators (NOT @java_method)
# 3. Proxy classes unconditionally at module top level
# 4. No relative package syntax in this file
#
import asyncio
import sys
import os

# Android detection without relative imports (static proxy generator limitation)
IS_ANDROID = (
    hasattr(sys, 'getandroidapilevel') or
    bool(os.environ.get('ANDROID_DATA'))
)

# Desktop: inject mock Java modules so the import-hook imports below succeed.
# The build-time scanner does static AST analysis and ignores this block entirely.
if not IS_ANDROID:
    import types as _types

    class _MockJavaClass:
        """Mock that allows arbitrary attribute access for nested Java classes."""
        def __init__(self, name="Mock"):
            self._name = name
        def __getattr__(self, attr):
            return _MockJavaClass(f"{self._name}.{attr}")
        def __call__(self, *a, **k):
            return self
        def __repr__(self):
            return f"<MockJava:{self._name}>"

    if 'java' not in sys.modules:
        _java = _types.ModuleType('java')
        _java.static_proxy = lambda *args, **kw: (lambda cls: cls)
        _java.dynamic_proxy = lambda *args, **kw: (lambda cls: cls)
        _java.Override = lambda *args, **kw: (lambda fn: fn)
        _java.jvoid = _MockJavaClass("jvoid")
        _java.jint = _MockJavaClass("jint")
        _java.jclass = lambda name: _MockJavaClass(name)
        sys.modules['java'] = _java

    _jl = _types.ModuleType('java.lang')
    _jl.CharSequence = _MockJavaClass("CharSequence")
    _jl.Runnable = _MockJavaClass("Runnable")
    sys.modules['java.lang'] = _jl

    sys.modules['androidx'] = _types.ModuleType('androidx')
    _ab = _types.ModuleType('androidx.biometric')
    _ab.BiometricPrompt = _MockJavaClass("BiometricPrompt")
    sys.modules['androidx.biometric'] = _ab

    sys.modules['androidx.core'] = _types.ModuleType('androidx.core')
    _acc = _types.ModuleType('androidx.core.content')
    _acc.ContextCompat = _MockJavaClass("ContextCompat")
    sys.modules['androidx.core.content'] = _acc


# === Chaquopy import-hook imports (REQUIRED for static_proxy) ===
from java import static_proxy, dynamic_proxy, jvoid, jint, Override
from androidx.biometric import BiometricPrompt
from java.lang import CharSequence, Runnable
from androidx.core.content import ContextCompat


# === Static proxy class — must be unconditional at module top level ===
class AuthCallback(static_proxy(BiometricPrompt.AuthenticationCallback)):
    def __init__(self, loop, future):
        super().__init__()
        self.loop = loop
        self.future = future

    @Override(jvoid, [BiometricPrompt.AuthenticationResult])
    def onAuthenticationSucceeded(self, result):
        try:
            crypto = result.getCryptoObject()
            if crypto:
                self.loop.call_soon_threadsafe(self.future.set_result, (True, crypto))
            else:
                self.loop.call_soon_threadsafe(self.future.set_result, (True, "Success"))
        except Exception as res_err:
            self.loop.call_soon_threadsafe(
                self.future.set_result,
                (False, f"Result processing error: {res_err}"),
            )

    @Override(jvoid, [jint, CharSequence])
    def onAuthenticationError(self, errorCode, errString):
        self.loop.call_soon_threadsafe(self.future.set_result, (False, str(errString)))

    @Override(jvoid, [])
    def onAuthenticationFailed(self):
        print("[piggytrade] Biometric: Auth failed (try again)", flush=True)


# === Dynamic proxy (runtime-generated, no build-time scanning needed) ===
class JavaRunnable(dynamic_proxy(Runnable)):
    def __init__(self, func):
        super().__init__()
        self.func = func

    def run(self):
        self.func()


# === Runtime helper classes (can use jclass freely inside methods) ===

class BiometricHelper:
    def __init__(self):
        self.available = False
        self._can_auth_status = -1
        self.activity = None
        if IS_ANDROID:
            self._init_android()

    async def wait_for_ready(self, timeout=2):
        """Wait for the system to report biometric status correctly."""
        start = asyncio.get_event_loop().time()
        while asyncio.get_event_loop().time() - start < timeout:
            self._init_android()
            if self.available:
                return True
            await asyncio.sleep(0.5)
        return self.available

    def _init_android(self):
        try:
            from java import jclass

            BiometricMgr = jclass("androidx.biometric.BiometricManager")

            activity = jclass("org.beeware.android.MainActivity").singletonThis
            if not activity:
                print("[piggytrade] Biometrics: MainActivity not ready yet.", flush=True)
                return

            self.activity = activity
            self.manager = getattr(BiometricMgr, "from")(activity)

            # Bitmasks
            STRONG = 0xF
            WEAK = 0xFF
            CRED = 0x8000

            # Query multiple combinations
            s_status = self.manager.canAuthenticate(STRONG)
            w_status = self.manager.canAuthenticate(WEAK)
            c_status = self.manager.canAuthenticate(STRONG | CRED)
            wc_status = self.manager.canAuthenticate(WEAK | CRED)

            # 0=SUCCESS, 11=NONE_ENROLLED, 1=HW_UNAVAILABLE, 12=SECURITY_UPDATE_REQUIRED
            self.available = any(s == 0 for s in [s_status, w_status, c_status, wc_status])
            self.has_hardware = any(s in [0, 11, 12] for s in [s_status, w_status])

            print(
                f"[piggytrade] Biometrics Init: Strong={s_status}, Weak={w_status}, "
                f"Cred={c_status}, W+C={wc_status}",
                flush=True,
            )
            if self.available:
                print("[piggytrade] Biometrics detected as AVAILABLE", flush=True)

        except Exception as e:
            print(f"[piggytrade] Biometric init crash: {e}", flush=True)
            self.available = False

    async def authenticate(
        self,
        title="Biometric Authentication",
        subtitle="Confirm transaction",
        cipher=None,
    ):
        """Triggers the Android BiometricPrompt and returns (success, result_or_err)."""
        if not IS_ANDROID:
            return True, "Desktop: Biometrics skipped"

        if not self.available:
            return False, "Biometric authentication not available"

        try:
            from java import jclass

            activity = self.activity
            if not activity:
                return False, "Android Activity not ready. Please try again in a moment."

            executor = ContextCompat.getMainExecutor(activity)

            loop = asyncio.get_event_loop()
            future = loop.create_future()

            callback = AuthCallback(loop, future)
            prompt = BiometricPrompt(activity, executor, callback)

            info_builder = BiometricPrompt.PromptInfo.Builder()
            info_builder.setTitle(title)
            info_builder.setSubtitle(subtitle)
            info_builder.setNegativeButtonText("Cancel")

            # Crypto-based auth requires STRONG (Class 3) biometrics only.
            # Plain auth can use STRONG | WEAK (0xFF).
            if cipher:
                info_builder.setAllowedAuthenticators(0xF)  # STRONG only
            else:
                info_builder.setAllowedAuthenticators(0xFF)  # STRONG | WEAK
            prompt_info = info_builder.build()

            # Create CryptoObject if cipher provided
            crypto_obj = None
            if cipher:
                crypto_obj = BiometricPrompt.CryptoObject(cipher)

            # Show the prompt on the main thread
            def do_auth():
                if crypto_obj:
                    prompt.authenticate(prompt_info, crypto_obj)
                else:
                    prompt.authenticate(prompt_info)

            activity.runOnUiThread(JavaRunnable(do_auth))

            return await future

        except Exception as e:
            import traceback
            traceback.print_exc()
            return False, f"Biometric error: {e}"


class KeystoreHelper:
    ALIAS = "piggy_master_key"

    def __init__(self):
        if not IS_ANDROID:
            return
        try:
            from java import jclass

            self.KeyStore = jclass("java.security.KeyStore")
            self.ks = self.KeyStore.getInstance("AndroidKeyStore")
            self.ks.load(None)

            self.Cipher = jclass("javax.crypto.Cipher")
            self.KeyGenerator = jclass("javax.crypto.KeyGenerator")
            self.KeyGenParameterSpec = jclass(
                "android.security.keystore.KeyGenParameterSpec$Builder"
            )
            self.KeyProperties = jclass("android.security.keystore.KeyProperties")
        except Exception as e:
            print(f"[piggytrade] Keystore init error: {e}", flush=True)

    def _get_or_create_key(self, require_auth=True):
        if not self.ks.containsAlias(self.ALIAS):
            gen = self.KeyGenerator.getInstance(
                self.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            purpose = (
                self.KeyProperties.PURPOSE_ENCRYPT | self.KeyProperties.PURPOSE_DECRYPT
            )
            builder = self.KeyGenParameterSpec(self.ALIAS, purpose)
            builder.setBlockModes(self.KeyProperties.BLOCK_MODE_GCM)
            builder.setEncryptionPaddings(self.KeyProperties.ENCRYPTION_PADDING_NONE)
            builder.setKeySize(256)
            if require_auth:
                builder.setUserAuthenticationRequired(True)
                try:
                    builder.setUserAuthenticationValidityDurationSeconds(-1)
                except Exception:
                    pass

            gen.init(builder.build())
            return gen.generateKey()
        return self.ks.getKey(self.ALIAS, None)

    def encrypt_data(self, plaintext_str):
        """Encrypts a string and returns (iv_b64, ciphertext_b64)."""
        try:
            key = self._get_or_create_key(require_auth=False)
            cipher = self.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(self.Cipher.ENCRYPT_MODE, key)

            iv = cipher.getIV()
            ciphertext = cipher.doFinal(plaintext_str.encode("utf-8"))

            import base64
            return base64.b64encode(iv).decode(), base64.b64encode(ciphertext).decode()
        except Exception as e:
            print(f"[piggytrade] Encryption error: {e}", flush=True)
            return None, None

    def get_decryption_cipher(self, iv_b64):
        """Returns an initialized Cipher for decryption. Pass to BiometricPrompt."""
        try:
            import base64
            from javax.crypto.spec import GCMParameterSpec

            iv = base64.b64decode(iv_b64)

            key = self._get_or_create_key(require_auth=True)
            cipher = self.Cipher.getInstance("AES/GCM/NoPadding")
            spec = GCMParameterSpec(128, iv)
            cipher.init(self.Cipher.DECRYPT_MODE, key, spec)
            return cipher
        except Exception as e:
            print(f"[piggytrade] Cipher init error: {e}", flush=True)
            return None
