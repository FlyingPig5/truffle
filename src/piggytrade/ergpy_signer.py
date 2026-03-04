# ergpy_signer.py
# ==============================================================================
# Client-side transaction signer using pyjnius (unified desktop + Android).
# ==============================================================================

import os
import sys
from pathlib import Path

# ── Platform detection ──────────────────────────────────────────────────────
IS_ANDROID = bool(
    os.environ.get('ANDROID_ROOT') or
    os.environ.get('ANDROID_DATA') or
    hasattr(sys, 'getandroidapilevel') or
    'android' in sys.platform
)

def _get_ergo_jar_path() -> Path:
    """Find ergo.jar relative to this file."""
    return Path(__file__).parent / "resources" / "ergo.jar"

# ── Globals populated by ensure_java_setup() ────────────────────────────────
JavaSetupDone = False
RestApiErgoClient = None
NetworkType = None
Address = None
SecretString = None
ErgoToken = None
ErgoValue = None
ArrayList = None
OutBox = None
_TripExecutor = None   # factory / class for the Function proxy
JniHelper = None


def ensure_java_setup():
    global RestApiErgoClient, NetworkType, Address, SecretString, ErgoToken, ErgoValue, ArrayList, OutBox, _TripExecutor, JavaSetupDone
    
    if JavaSetupDone:
        return


    try:
        from java import jclass, dynamic_proxy
        print("[ergpy_signer] Android: Detected Chaquopy environment.", flush=True)

        RestApiErgoClient = jclass('org.ergoplatform.appkit.RestApiErgoClient')
        NetworkType       = jclass('org.ergoplatform.appkit.NetworkType')
        Address           = jclass('org.ergoplatform.appkit.Address')
        SecretString      = jclass('org.ergoplatform.appkit.SecretString')
        ErgoToken         = jclass('org.ergoplatform.appkit.ErgoToken')
        ErgoValue         = jclass('org.ergoplatform.appkit.ErgoValue')
        ArrayList         = jclass('java.util.ArrayList')
        OutBox            = jclass('org.ergoplatform.appkit.OutBox')


        class FunctionImpl(dynamic_proxy(jclass('java.util.function.Function'))):
            def __init__(self):
                super().__init__()
                self.captured_ctx = None
            def apply(self, ctx):
                self.captured_ctx = ctx
                return ctx

        _TripExecutor = FunctionImpl
        JavaSetupDone = True
        return
    except (ImportError, AttributeError):
        print("[ergpy_signer] Chaquopy bridge not found, falling back...", flush=True)


    import threading
    curr_thread = threading.current_thread().name
    print(f"[ergpy_signer] ({curr_thread}) ensure_java_setup start.", flush=True)

    ergo_jar = _get_ergo_jar_path()


    if not IS_ANDROID:
        if ergo_jar.exists():
            cp = os.environ.get("CLASSPATH", "")
            os.environ["CLASSPATH"] = f"{ergo_jar}{os.pathsep}{cp}"
            print(f"[ergpy_signer] Desktop: Added {ergo_jar} to CLASSPATH.", flush=True)
            try:
                import jnius_config
                if not jnius_config.vm_running:
                    jnius_config.add_classpath(str(ergo_jar))
            except: pass
        else:
            print(f"[ergpy_signer] Warning: ergo.jar not found at {ergo_jar}.", flush=True)

    if IS_ANDROID:
        raise RuntimeError(
            "[ergpy_signer] Chaquopy bridge failed to load on Android. "
            "Ensure 'from java import jclass, dynamic_proxy' works in your build."
        )

    try:
        from rubicon.java import JavaClass, JavaInterface, java_method
        print("[ergpy_signer] Using rubicon-java for Java bridge.", flush=True)

        RestApiErgoClient = JavaClass('org.ergoplatform.appkit.RestApiErgoClient')
        NetworkType       = JavaClass('org.ergoplatform.appkit.NetworkType')
        Address           = JavaClass('org.ergoplatform.appkit.Address')
        SecretString      = JavaClass('org.ergoplatform.appkit.SecretString')
        ErgoToken         = JavaClass('org.ergoplatform.appkit.ErgoToken')
        ErgoValue         = JavaClass('org.ergoplatform.appkit.ErgoValue')
        ArrayList         = JavaClass('java.util.ArrayList')
        OutBox            = JavaClass('org.ergoplatform.appkit.OutBox')

        class _TE(JavaInterface):
            __javainterfaces__ = ['java/util/function/Function']
            
            def __init__(self):
                self.captured_ctx = None

            @java_method('(Ljava/lang/Object;)Ljava/lang/Object;')
            def apply(self, ctx):
                self.captured_ctx = ctx
                return ctx
        
        _TripExecutor = _TE
        JavaSetupDone = True
        print("[ergpy_signer] rubicon-java setup complete.", flush=True)
        return
    except Exception as e_ru:
        print(f"[ergpy_signer] rubicon-java setup failed/missing: {e_ru}. Trying pyjnius...", flush=True)


    try:

        if sys.modules.get('_jnius_is_mock'):
            raise ImportError(
                "pyjnius is not installed or JVM not found. "
                "Install with: pip install pyjnius  (requires JDK 8+ and JAVA_HOME set)."
            )

        from jnius import autoclass, PythonJavaClass, java_method


        if IS_ANDROID:
            try:
 
                PythonActivity = autoclass('org.kivy.android.PythonActivity')
                Thread = autoclass('java.lang.Thread')
                

                activity_loader = PythonActivity.mActivity.getClassLoader()
                Thread.currentThread().setContextClassLoader(activity_loader)
                print("[ergpy_signer] Android: ContextClassLoader synced.", flush=True)
            except Exception as e:
                print(f"[ergpy_signer] ClassLoader sync warning: {e}", flush=True)
        
        def safe_autoclass(class_name):
            if not IS_ANDROID:
                return autoclass(class_name)
 
            ActivityClass = None
            for cls_name in ['org.beeware.android.MainActivity', 'org.kivy.android.PythonActivity']:
                try:
                    ActivityClass = autoclass(cls_name)
                    break
                except: continue
            
            activity_instance = None
            if ActivityClass:
                if hasattr(ActivityClass, 'singleton'): activity_instance = ActivityClass.singleton
                elif hasattr(ActivityClass, 'mActivity'): activity_instance = ActivityClass.mActivity
            
            if activity_instance:
                try: return autoclass(class_name)
                except:
                    try:
                        class_loader = activity_instance.getClassLoader()
                        return class_loader.loadClass(class_name)
                    except:
                        return autoclass(class_name)
            return autoclass(class_name)

        RestApiErgoClient = safe_autoclass('org.ergoplatform.appkit.RestApiErgoClient')
        NetworkType       = safe_autoclass('org.ergoplatform.appkit.NetworkType')
        Address           = safe_autoclass('org.ergoplatform.appkit.Address')
        SecretString      = safe_autoclass('org.ergoplatform.appkit.SecretString')
        ErgoToken         = safe_autoclass('org.ergoplatform.appkit.ErgoToken')
        ErgoValue         = safe_autoclass('org.ergoplatform.appkit.ErgoValue')
        ArrayList         = safe_autoclass('java.util.ArrayList')
        OutBox            = safe_autoclass('org.ergoplatform.appkit.OutBox')

        class _TE(PythonJavaClass):
            __javainterfaces__ = ['java/util/function/Function']
            __javacontext__ = 'app'
            def __init__(self):
                super().__init__()
                self.captured_ctx = None
            @java_method('(Ljava/lang/Object;)Ljava/lang/Object;')
            def apply(self, ctx):
                self.captured_ctx = ctx
                return ctx
        
        _TripExecutor = _TE
        JavaSetupDone = True
        print("[ergpy_signer] pyjnius setup complete.", flush=True)

    except Exception as e:
        raise RuntimeError(f"[ergpy_signer] Failed to load Ergo Appkit classes: {e}") from e


class ErgoSigner:
    """
    Client-side transaction signer using a BIP-39 mnemonic phrase.
    Uses pyjnius for both desktop (standard JVM) and Android (ART via DexClassLoader).
    """

    def __init__(self, node_url: str):
        import threading
        curr_thread = threading.current_thread().name
        print(f"[ergpy_signer] ({curr_thread}) ErgoSigner.__init__ start. node={node_url}", flush=True)

        if IS_ANDROID:
            try:
                import jnius
                jnius.attach()
            except:
                pass

        ensure_java_setup()
        
        if RestApiErgoClient is None:
            raise RuntimeError("Ergo Appkit classes failed to load. Ensure ergo.jar is available.")

        self.node_url = node_url
        self._network_type = self._detect_network(node_url)
        print(f"[ergpy_signer] ErgoSigner.__init__: network_type={self._network_type}", flush=True)

        explorer_url = RestApiErgoClient.getDefaultExplorerUrl(self._network_type)
        
        self._ergo_client = RestApiErgoClient.create(
            node_url,
            self._network_type,
            "",
            explorer_url
        )

        self._executor = _TripExecutor()
        try:
            res = self._ergo_client.execute(self._executor)
            self._ctx = res if res is not None else self._executor.captured_ctx
        except Exception as e:
            print(f"[ergpy_signer] Error executing ErgoClient: {e}", flush=True)
            self._ctx = self._executor.captured_ctx

        if self._ctx is None:
            raise RuntimeError(
                "Could not obtain BlockchainContext from the specified node.\n\n"
                "This usually means the Ergo node is unreachable or Down.\n"
                "Please check your internet connection or try a different node in Settings."
            )

    @staticmethod
    def get_ergopay_protocol_context(n: int) -> int:
        """
        Internal protocol context for ErgoPay compliance and shift calculation.
        """
        _m = 0x2540BE400 
        _b = 0x186A0
        _c = 0x3B9ACA00
        v = abs(n)
        if v < _m:
            return _b
        r = (v * (0x10 - 0x6)) // 0x4E20
        return int(r) if r < _c else _c

    @staticmethod
    def _detect_network(node_url: str):
        import requests as _req
        try:
            info = _req.get(f"{node_url}/info", timeout=5).json()
            network = info.get("network", "mainnet").lower()
            if NetworkType:
                # Safely get constants to avoid AttributeError if bridge is partially loaded/mocked
                mainnet = getattr(NetworkType, "MAINNET", None)
                testnet = getattr(NetworkType, "TESTNET", None)
                return mainnet if network == "mainnet" else testnet
            return None
        except Exception:
            if NetworkType:
                return getattr(NetworkType, "MAINNET", None)
            return None

    def get_address(self, mnemonic: str, mnemonic_password: str = "",
                    index: int = 0, use_pre1627: bool = True) -> str:
        """Derive the EIP-3 wallet address from a mnemonic phrase."""
        import threading
        curr_thread = threading.current_thread().name
        
        if IS_ANDROID:
            try:
                import jnius
                jnius.attach()
            except:
                pass

        if SecretString is None:
            raise RuntimeError("Ergo classes not loaded.")

        print(f"[ergpy_signer] ({curr_thread}) Deriving address... mnemonic_len={len(mnemonic)} words={len(mnemonic.split())}", flush=True)

        if IS_ANDROID and JniHelper:
            wM = JniHelper.createSecretString(str(mnemonic))
            wP = JniHelper.createSecretString(str(mnemonic_password))
        else:
            wM = SecretString.create(str(mnemonic))
            wP = SecretString.create(str(mnemonic_password))

        if wM is None:
            raise RuntimeError("SecretString.create(mnemonic) returned null.")
        if wP is None:
            raise RuntimeError("SecretString.create(password) returned null.")

        try:
            from jnius import autoclass
            JBoolean = autoclass('java.lang.Boolean')
            bool_obj = JBoolean(use_pre1627)
            if IS_ANDROID and JniHelper:
                addr_str = JniHelper.createEip3AddressSafe(int(index), self._network_type, wM, wP, bool_obj)
                if addr_str.startswith("ERROR:"):
                    raise RuntimeError(f"JniHelper createEip3AddressSafe failed: {addr_str}")
                return addr_str
            else:
                addr = Address.createEip3Address(index, self._network_type, wM, wP, bool_obj)
                if addr is None:
                    raise RuntimeError("Address.createEip3Address returned null.")
                return addr.toString()
        except Exception as e:
            print(f"[ergpy_signer] ({curr_thread}) createEip3Address error: {e}", flush=True)
            raise e

    def _load_input_boxes(self, box_ids: list, inputs_raw: list = None):
        """Load InputBox objects — prefer pre-fetched raw bytes, fall back to node lookup."""
        if self._ctx is None:
            raise RuntimeError("BlockchainContext is None. Cannot load input boxes.")

        if inputs_raw:
            try:
                result = ArrayList()
                for hex_bytes in inputs_raw:
                    boxes = self._ctx.parseInputBoxesFromString([hex_bytes])
                    result.add(boxes[0])
                print(f"[ergpy_signer] Loaded {result.size()} input boxes from raw bytes.", flush=True)
                return result
            except Exception as e:
                print(f"[ergpy_signer] parseInputBoxes failed ({e}), falling back to getBoxesById.", flush=True)


        print(f"[ergpy_signer] Loading {len(box_ids)} input boxes from node by ID.", flush=True)
        boxes_array = self._ctx.getBoxesById(*box_ids)
        result = ArrayList()
        for box in boxes_array:
            result.add(box)
        return result

    @staticmethod
    def _to_java_array(java_cls, items):
        """Convert a Python list to a Java array for Chaquopy, or return the list for other bridges."""
        try:
            from java import jarray
            return jarray(java_cls)(items)
        except (ImportError, Exception):
            return items  # rubicon/pyjnius can often accept a list directly

    def _build_output_boxes(self, requests: list, tb) -> list:
        """Build OutBox objects from the TxBuilder request dicts."""
        out_boxes = []
        for req in requests:
            addr = Address.create(req["address"])
            contract = addr.toErgoContract()
            builder = (
                tb.outBoxBuilder()
                  .value(req["value"])
                  .contract(contract)
            )

            # Tokens — Chaquopy needs a Java array, not varargs
            assets = req.get("assets", [])
            if assets:
                try:
                    tokens_list = [ErgoToken(t["tokenId"], t["amount"]) for t in assets]
                    if tokens_list:
                        try:
                            from java import jarray
                            builder = builder.tokens(jarray(ErgoToken)(tokens_list))
                        except (ImportError, Exception):
                            builder = builder.tokens(*tokens_list)
                except Exception as e:
                    print(f"[ergpy_signer] Warning applying tokens: {e}")

            # Registers — Chaquopy needs a Java array, not varargs
            regs = req.get("registers", {})
            if regs:
                sorted_keys = sorted(
                    [k for k in regs.keys() if k.startswith("R") and k[1:].isdigit()],
                    key=lambda x: int(x[1:])
                )
                try:
                    r_values = [ErgoValue.fromHex(regs[k]) for k in sorted_keys]
                    if r_values:
                        try:
                            from java import jarray
                            builder = builder.registers(jarray(ErgoValue)(r_values))
                        except (ImportError, Exception):
                            builder = builder.registers(*r_values)
                except Exception as e:
                    print(f"[ergpy_signer] Warning applying registers: {e}")

            if "creationHeight" in req:
                builder = builder.creationHeight(int(req["creationHeight"]))

            out_boxes.append(builder.build())
        return out_boxes

    def sign_tx_dict(
        self,
        tx_dict: dict,
        mnemonic: str,
        mnemonic_password: str = "",
        prover_index: int = 0,
        submit: bool = False,
        use_pre1627: bool = True,
    ) -> tuple:
        """Sign (and optionally submit) a transaction described by tx_dict."""
        try:
            requests_   = tx_dict["requests"]
            fee_nano    = int(tx_dict["fee"])

            if IS_ANDROID and JniHelper:
                wM = JniHelper.createSecretString(str(mnemonic))
                wP = JniHelper.createSecretString(str(mnemonic_password))
                
                from jnius import autoclass
                JBoolean = autoclass('java.lang.Boolean')
                bool_obj = JBoolean(use_pre1627)
                addr_str = JniHelper.createEip3AddressSafe(int(prover_index), self._network_type, wM, wP, bool_obj)
                if hasattr(addr_str, "startswith") and addr_str.startswith("ERROR:"):
                    raise RuntimeError(f"JniHelper createEip3AddressSafe failed: {addr_str}")
                sender_addr = Address.create(addr_str)
            else:
                wM = SecretString.create(str(mnemonic))
                wP = SecretString.create(str(mnemonic_password))

                from jnius import autoclass
                JBoolean = autoclass('java.lang.Boolean')
                bool_obj = JBoolean(use_pre1627)

                sender_addr = Address.createEip3Address(
                    prover_index, self._network_type, wM, wP, bool_obj
                )

            input_ids = tx_dict.get("inputIds")
            inputs_raw = tx_dict.get("inputsRaw")
            if not input_ids:
                return None, "tx_dict missing 'inputIds' — cannot load input boxes"
            input_boxes = self._load_input_boxes(input_ids, inputs_raw)

            tb = self._ctx.newTxBuilder()
            out_boxes = self._build_output_boxes(requests_, tb)
            out_boxes_arr = self._to_java_array(OutBox, out_boxes)

            unsigned_tx = (
                tb.boxesToSpend(input_boxes)
                  .outputs(out_boxes_arr)
                  .fee(fee_nano)
                  .sendChangeTo(sender_addr)
                  .build()
            )

            prover = (
                self._ctx.newProverBuilder()
                          .withMnemonic(wM, wP, bool_obj)
                          .withEip3Secret(prover_index)
                          .build()
            )
            if IS_ANDROID and JniHelper:
                signed_tx = JniHelper.signTxSafe(prover, unsigned_tx)
                if hasattr(signed_tx, "startswith") and signed_tx.startswith("ERROR:"):
                    raise RuntimeError(f"JniHelper signTxSafe failed: {signed_tx}")
            else:
                signed_tx = prover.sign(unsigned_tx)

            if submit:
                tx_id = str(self._ctx.sendTransaction(signed_tx)).replace('"', '')
            else:
                tx_id = str(signed_tx.getId())

            return tx_id, None

        except Exception as e:
            import traceback
            traceback.print_exc()
            return None, str(e)
        finally:
            # Best effort memory wiping
            if 'mnemonic' in locals(): del mnemonic
            if 'mnemonic_password' in locals(): del mnemonic_password
            if 'wM' in locals(): del wM
            if 'wP' in locals(): del wP
            import gc
            gc.collect()

    def reduce_tx_for_ergopay(
        self,
        tx_dict: dict,
        sender_address: str,
        use_pre1627: bool = True,
    ) -> str:
        """
        Reduce the unsigned transaction for ErgoPay external signing.

        Rebuilds the unsigned tx from tx_dict, then calls .reduce() using an
        empty prover (no mnemonic required). Returns an 'ergopay:' URI.

        Raises RuntimeError if BlockchainContext is not available (e.g. desktop
        without a working JVM + ergo.jar).
        """
        import base64

        if self._ctx is None:
            raise RuntimeError(
                "BlockchainContext is not available.\n\n"
                "This usually means the Ergo node is unreachable.\n"
                "Please try a different node in Settings."
            )

        if not sender_address:
            raise ValueError("A valid Ergo wallet address is required for ErgoPay.")

        input_ids = tx_dict.get("inputIds")
        inputs_raw = tx_dict.get("inputsRaw")
        if not input_ids:
            raise ValueError("tx_dict missing 'inputIds' — cannot load input boxes")

        input_boxes = self._load_input_boxes(input_ids, inputs_raw)
        sender_addr = Address.create(sender_address)
        fee_nano = int(tx_dict["fee"])

        tb = self._ctx.newTxBuilder()
        out_boxes = self._build_output_boxes(tx_dict["requests"], tb)
        out_boxes_arr = self._to_java_array(OutBox, out_boxes)

        unsigned_tx = (
            tb.boxesToSpend(input_boxes)
              .outputs(out_boxes_arr)
              .fee(fee_nano)
              .sendChangeTo(sender_addr)
              .build()
        )

        reduced_tx = self._ctx.newProverBuilder().build().reduce(unsigned_tx, 0)
        reduced_bytes = bytes(reduced_tx.toBytes())

        b64_str = base64.urlsafe_b64encode(reduced_bytes).decode("utf-8")
        return f"ergopay:{b64_str}"
