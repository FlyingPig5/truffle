import traceback as _tb
import sys as _sys

try:
    from piggytrade.app import main

    if __name__ == "__main__":
        main().main_loop()
except Exception as _e:
    print(f"[piggytrade] FATAL STARTUP CRASH: {type(_e).__name__}: {_e}", flush=True)
    _tb.print_exc()
    _sys.exit(1)
