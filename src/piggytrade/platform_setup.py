# platform_setup.py

import os
import sys
import platform as _platform
import types
from pathlib import Path

# --- Platform Detection ---
IS_ANDROID = bool(
    os.environ.get('ANDROID_ROOT') or
    os.environ.get('ANDROID_DATA') or
    hasattr(sys, 'getandroidapilevel')
)

# PLATFORM: 'android' | 'windows' | 'linux' | 'darwin'
PLATFORM = "android" if IS_ANDROID else _platform.system().lower()

def apply_dbus_patch():
    """Apply the GTK DBus bypass patch to prevent Toga deadlocks on Linux."""
    try:
        import gi
        gi.require_version('Gtk', '3.0')
        from gi.repository import Gtk, Gio

        original_init = Gtk.Application.__init__

        def patched_init(self, *args, **kwargs):

            kwargs['flags'] = Gio.ApplicationFlags.NON_UNIQUE
            original_init(self, *args, **kwargs)

        Gtk.Application.__init__ = patched_init
    except (ImportError, ValueError):
        print("[piggytrade] GTK not found or not supported on this platform, skipping DBus patch.", flush=True)

def setup_java_bridge():
    """Setup the Java bridge depending on the running platform."""
    if IS_ANDROID:
        print("[piggytrade] Android: Shimming jnius via Chaquopy...", flush=True)
        try:
            from java import jclass
            mock_jnius = types.ModuleType('jnius')
            mock_jnius.autoclass = jclass
            

            def java_method(signature, name=None):
                def decorator(fn): return fn
                return decorator
                
            class PythonJavaClass:
                __javainterfaces__ = []
                __javacontext__ = 'app'
                def __init__(self, *args, **kwargs): pass
            
            mock_jnius.java_method = java_method
            mock_jnius.PythonJavaClass = PythonJavaClass
            mock_jnius.attach = lambda: None
            mock_jnius.detach = lambda: None
            mock_jnius.cast = lambda java_class, obj: obj # Android chaquopy fallback
            
            sys.modules['jnius'] = mock_jnius
            print("[piggytrade] Android: jnius shim (via Chaquopy) complete.", flush=True)
        except ImportError:
            print("[piggytrade] Not running on Chaquopy or bridge missing.", flush=True)
    else:
        # Desktop: Try real jnius first, then fallback to mock
        _jnius_real = False
        try:
            import jnius_config
            ergo_jar = Path(__file__).parent / "resources" / "ergo.jar"
            if not jnius_config.vm_running and ergo_jar.exists():
                jnius_config.add_classpath(str(ergo_jar))
                
            import jnius as _jnius_check
            _jnius_real = True
            print(f"[piggytrade] Desktop: Real pyjnius found (JAVA_HOME={os.environ.get('JAVA_HOME','not set')}).", flush=True)
        except ImportError:
            print("[piggytrade] Desktop: pyjnius not installed. ErgoPay signing will not work.", flush=True)
            print("[piggytrade] Desktop: To install: pip install pyjnius (requires JDK + JAVA_HOME set)", flush=True)
            # Create a LOCAL mock for UI only
            _local_jnius_mock = types.ModuleType('jnius')

            class PythonJavaClass:
                __javainterfaces__ = []
                __javacontext__ = 'app'
                def __init__(self, *args, **kwargs): pass

            def java_method(signature, name=None):
                def decorator(fn): return fn
                return decorator

            class MockObj:
                def __init__(self, name="MockObj"):
                    self._name = name
                def __getattr__(self, attr):
                    if attr.isupper(): return attr
                    return self
                def __call__(self, *a, **k): return self
                def __iter__(self): return iter([])
                def __getitem__(self, k): return self
                def __bool__(self): return True
                def toBytes(self): return b"MOCK_TX_BYTES_12345"
                def toString(self): return "MockObject"
                def __repr__(self): return f"<Mock:{self._name}>"

            _local_jnius_mock.autoclass = lambda name: MockObj(name)
            _local_jnius_mock.java_method = java_method
            _local_jnius_mock.PythonJavaClass = PythonJavaClass
            _local_jnius_mock.attach = lambda: None
            _local_jnius_mock.detach = lambda: None

            import sys as _sys
            _sys.modules['jnius'] = _local_jnius_mock
            _sys.modules['_jnius_is_mock'] = True  # sentinel

            mock_jconf = types.ModuleType('jnius_config')
            mock_jconf.vm_running = False
            mock_jconf.add_classpath = lambda x: None
            _sys.modules['jnius_config'] = mock_jconf
        except Exception as e:
            print(f"[piggytrade] Desktop: jnius error on load: {e}", flush=True)

def initialize_platform():
    """Runs all necessary platform setups."""
    apply_dbus_patch()
    setup_java_bridge()
