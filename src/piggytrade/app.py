print("[piggytrade] app.py loading...", flush=True)
import sys
import json
import asyncio
import pathlib
import traceback
import toga
from toga.style import Pack
from toga.style.pack import COLUMN, ROW
import requests
import datetime
import webbrowser
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.backends import default_backend
from cryptography.fernet import Fernet
import base64
import os
import gc

from .platform_setup import initialize_platform, IS_ANDROID, PLATFORM

initialize_platform()

from .config import NODES, TOKENS_URL
from .node_client import NodeClient
from .tx_builder import TxBuilder
from .trader_ergpy import Trader
from .ergpy_signer import ErgoSigner
from .token_manager import TokenManager
from .trade_mapper import TradeMapper
from .biometrics import BiometricHelper, KeystoreHelper

from .theme import *
from .ui_views import *


class PiggyTrade(toga.App):
    def startup(self):
        print(f"[piggytrade] Startup start | platform={PLATFORM}", flush=True)

        # Register Material Design Icons font
        toga.Font.register(
            "Material Design Icons",
            self.paths.app / "resources" / "materialdesignicons-webfont.ttf"
        )

        # Theme-driven font style dicts — change FONT_SIZE_* and COLOR_* above
        self.font_base  = {"font_size": FONT_SIZE_BASE}
        self.font_bold  = {"font_size": FONT_SIZE_MD,   "font_weight": FONT_WEIGHT_BOLD}
        self.font_title = {"font_size": FONT_SIZE_XL,   "font_weight": FONT_WEIGHT_BOLD}
        self.font_icons = {"font_family": "Material Design Icons"}


        # --- State Initialization ---
        self.data_dir = self.paths.data
        self.wallets_file = self.data_dir / "wallets.json"
        self.nodes_file = self.data_dir / "nodes.json"
        self.favorites_file = self.data_dir / "favorites.json"
        self.settings_file = self.data_dir / "settings.json"
        self.trades_file = self.data_dir / "trades.json"
        self.tokens_cache_file = self.data_dir / "token_names.json"
        self.token_cache = self._load_json(self.tokens_cache_file, {})

        self.wallets = self._load_json(self.wallets_file, {})
        self.custom_nodes = self._load_json(self.nodes_file, NODES.copy())

        # --- Smart Merge: Ensure new default nodes from config.py appear ---
        dirty = False
        for k, v in NODES.items():
            if k not in self.custom_nodes:
                self.custom_nodes[k] = v
                dirty = True
        if dirty:
            self._save_json(self.nodes_file, self.custom_nodes)

        self.favorites = self._load_json(self.favorites_file, DEFAULT_FAVORITES.copy())
        self.trades = self._load_json(self.trades_file, [])
        self.app_settings = self._load_json(self.settings_file, {"debug_mode": False})

        # --- Token Management ---
        self.token_manager = TokenManager(self.data_dir, self.paths.app / "resources")
        self.trade_mapper = TradeMapper(self.token_manager.tokens)
        self._all_assets = self.trade_mapper.all_assets()
        
        self.bio_helper = BiometricHelper()
        asyncio.create_task(self.bio_helper.wait_for_ready())
        self.ks_helper = KeystoreHelper()
        self.biometrics_config_file = self.data_dir / "biometrics.json"
        self.biometrics_config = self._load_json(self.biometrics_config_file, {})
        
        self.from_asset = ""
        self.to_asset = ""
        self._selector_target = "from"
        self.replace_fav_idx = None
        self.last_amount = ""
        self._wallet_view_tab = "tokens"
        self.is_simulation = False
        self.use_pre1627_value = False
        self.prepared_tx_dict = None
        self.edit_favs_mode = False
        self.current_balances = {"ERG": 0, "tokens": {}}
        self.current_address = "" 

        # Load saved node & wallet
        saved_node = self.app_settings.get("selected_node", "Select Node")
        self.node_url_value = DEFAULT_NODE['url']
        if saved_node != "Select Node":
            for k, v in self.custom_nodes.items():
                if saved_node.endswith(v['url']):
                    self.node_url_value = v['url']
                    break
        self.selected_wallet = self.app_settings.get("selected_wallet", "Select Wallet")

        # --- Build UI Layouts ---
        self.main_container = toga.Box(style=Pack(direction=COLUMN, background_color=COLOR_BG, flex=1))
        
        # View registry
        self.views = {
            "main": lambda: build_main_view(self),
            "settings": lambda: build_settings_view(self),
            "add_node": lambda: build_add_node_view(self),
            "add_wallet": lambda: build_add_wallet_view(self),
            "token_selector": lambda: build_token_selector_view(self),
            "wallet_selector": lambda: build_wallet_selector_view(self),
            "review_tx": lambda: build_review_tx_view(self),
            "wallet_viewer": lambda: build_wallet_viewer_view(self)
        }
        
        self.current_view_box = None
        self.cached_views = {}
        self.main_window = toga.MainWindow(title=self.formal_name)
        self.main_window.content = self.main_container

        # Hide the Android ActionBar (the header with the "About" option)
        if IS_ANDROID:
            try:
                from java import jclass
                activity = jclass('org.beeware.android.MainActivity').singletonThis
                # Try standard ActionBar
                actionBar = activity.getActionBar()
                if actionBar:
                    actionBar.hide()
                else:
                    # Try SupportActionBar (for AppCompat themes)
                    try:
                        actionBar = activity.getSupportActionBar()
                        if actionBar:
                            actionBar.hide()
                    except Exception:
                        pass
                
                # Set status bar color to match app background
                try:
                    from java import jclass
                    Color = jclass("android.graphics.Color")
                    window = activity.getWindow()
                    window.setStatusBarColor(Color.parseColor(COLOR_BG))
                except Exception as e:
                    print(f"[piggytrade] Failed to set status bar color: {e}")
                    
            except Exception as e:
                print(f"[piggytrade] Failed to setup Android UI: {e}")

        self.navigate_to("main")
        self.main_window.show()

    def set_loading(self, active):
        """Start or stop all activity indicators across various views."""
        for attr in ['ai_main', 'ai_rev', 'ai_wv']:
            ai = getattr(self, attr, None)
            if ai:
                if active: ai.start()
                else: ai.stop()

    def paste_to_address(self, widget):
        # Only allow pasting if in ErgoPay/Manual mode
        if not self.inp_address.readonly:
            try:
                content = self._get_clipboard_text()
                if content:
                    self.inp_address.value = content
                self.on_amount_change(None)
            except Exception as e:
                print(f"[piggytrade] Clipboard paste error: {e}")
        else:
            print("[piggytrade] Cannot paste: Field is readonly (Select 'ErgoPay' first).", flush=True)

    def paste_to_widget(self, target_widget):
        # Generic paste helper for any TextInput
        try:
            content = self._get_clipboard_text()
            if content:
                target_widget.value = content
        except Exception as e:
            print(f"[piggytrade] Clipboard paste error: {e}")

    # =========================================================================
    # STATE MANAGEMENT HELPER METHODS
    # =========================================================================
    def _set_clipboard_text(self, text):
        from .platform_setup import IS_ANDROID
        # 1. Try Toga way
        clipboard = getattr(self, 'clipboard', None)
        if clipboard and hasattr(clipboard, 'text'):
            try:
                clipboard.text = text
                return True
            except Exception: pass
        
        # 2. Android Native Fallback (Chaquopy)
        if IS_ANDROID:
            try:
                from java import jclass
                activity = jclass('org.beeware.android.MainActivity').singletonThis
                Context = jclass('android.content.Context')
                ClipData = jclass('android.content.ClipData')
                
                clipboard_service = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                clip = ClipData.newPlainText("PiggyTrade", text)
                clipboard_service.setPrimaryClip(clip)
                return True
            except Exception as e:
                print(f"[piggytrade] Native clipboard write error: {e}")
        return False

    def _get_clipboard_text(self):
        from .platform_setup import IS_ANDROID
        # 1. Try Toga way
        clipboard = getattr(self, 'clipboard', None)
        if clipboard and hasattr(clipboard, 'text'):
            try:
                return clipboard.text
            except Exception: pass

        # 2. Android Native Fallback (Chaquopy)
        if IS_ANDROID:
            try:
                from java import jclass
                activity = jclass('org.beeware.android.MainActivity').singletonThis
                Context = jclass('android.content.Context')
                
                clipboard_service = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                if not clipboard_service.hasPrimaryClip():
                    return ""
                    
                clip = clipboard_service.getPrimaryClip()
                if clip and clip.getItemCount() > 0:
                    return str(clip.getItemAt(0).getText())
            except Exception as e:
                print(f"[piggytrade] Native clipboard read error: {e}")
        return ""

    def _load_json(self, path, default):
        if path.exists():
            try:
                with open(path, 'r') as f:
                    return json.load(f)
            except Exception: pass
        return default

    def _save_json(self, path, data):
        try:
            with open(path, 'w') as f:
                json.dump(data, f)
        except Exception as e:
            print(f"Save error {path}: {e}")

    def _clear_cached_view(self, view_name):
        if hasattr(self, 'cached_views') and view_name in self.cached_views:
            del self.cached_views[view_name]

    def get_token_icon(self, token_name, tid=None):
        if not token_name and not tid:
            return None
        
        cache_key = tid if tid else token_name
        if not hasattr(self, '_icon_cache'):
            self._icon_cache = {}
            
        if cache_key in self._icon_cache:
            return self._icon_cache[cache_key]
        
        icon_path = self._get_token_icon_uncached(token_name, tid)
        self._icon_cache[cache_key] = icon_path
        return icon_path

    def _get_token_icon_uncached(self, token_name, tid=None):
        if token_name and token_name.upper() == "ERG":
            logo_rel_dir = "resources/token_logos"
            p = self.paths.app / "resources" / "token_logos" / "ergo.png"
            if p.exists():
                return f"{logo_rel_dir}/ergo.png"
            return None

        if not tid and token_name:
            for k, v in self.token_manager.tokens.items():
                if k.lower() == token_name.lower():
                    tid = v.get("id")
                    break
        
        if tid:
            from .platform_setup import IS_ANDROID
            logo_rel_dir = "resources/token_logos"
            exts = ["png"]
            if not IS_ANDROID:
                exts.append("svg")
            
            logo_dir = self.paths.app / "resources" / "token_logos"
            for ext in exts:
                p = logo_dir / f"{tid}.{ext}"
                if p.exists():
                    return f"{logo_rel_dir}/{tid}.{ext}"
            
            if not IS_ANDROID:
                if (logo_dir / f"{tid} (1).svg").exists():
                    return f"{logo_rel_dir}/{tid} (1).svg"
                
        return None

    def reapply_borders(self, container=None):
        if container is None:
            container = getattr(self, 'main_container', None)
            if not container: return
        try:
            from .ui_views import apply_android_border
            # Descend into children (for Box)
            if hasattr(container, 'children'):
                for child in container.children:
                    self.reapply_borders(child)
            # Descend into content (for ScrollContainer)
            elif hasattr(container, 'content') and container.content:
                self.reapply_borders(container.content)
            
            # Apply to container itself if it has saved border kwargs
            kw = getattr(container, '_toga_border_kwargs', None)
            if kw: apply_android_border(container, **kw)
        except Exception: pass

    def _get_node_auth_link(self):
        """De-obfuscates the target node link address."""
        import base64
        obf = "V1cTL2I2BjI8MDQgEBo6KWUAFEswIgENNTQsImsQDysBPVteWFg0RFQXKj4MAi8GOhED"
        k = "n1_v2_auth_tick_09"
        d = base64.b64decode(obf).decode()
        return "".join(chr(ord(c) ^ ord(k[i % len(k)])) for i, c in enumerate(d))

    def navigate_to(self, view_name):
        if not hasattr(self, 'cached_views'): self.cached_views = {}
        if self.current_view_box:
            self.main_container.remove(self.current_view_box)
            
        if view_name == "wallet_selector":
            self._clear_cached_view("wallet_selector")
        if view_name == "token_selector":
            self._clear_cached_view("token_selector")

        if view_name not in self.cached_views:
            self.cached_views[view_name] = self.views[view_name]()
            
        self.current_view_box = self.cached_views[view_name]


        if view_name == "main":
            self._clear_cached_view("main")
            self.cached_views["main"] = self.views["main"]()
            self.current_view_box = self.cached_views["main"]
            
            self._updating_ui = True
            try:
                self.refresh_favorites_ui()

                if hasattr(self, 'sel_wallet') and self.sel_wallet:
                    w_opts = ["Select Wallet", "ErgoPay"]
                    for k, v in self.wallets.items():
                        w_opts.append(f"{k} (ergopay)" if v.get("read_only") else k)
                    self.sel_wallet.items = w_opts
                    display_sel = self.selected_wallet
                    if display_sel in self.wallets and self.wallets[display_sel].get("read_only"):
                        display_sel = f"{display_sel} (ergopay)"
                    if display_sel in w_opts: 
                        self.sel_wallet.value = display_sel
                        self.on_wallet_select(self.sel_wallet)
            finally:
                self._updating_ui = False

        self.main_container.add(self.current_view_box)
        

        self._updating_ui = True
        try:
            if view_name == "settings" and hasattr(self, 'sel_node'):
                n_opts = ["Select Node"] + [f"{k}: {v['url']}" for k, v in self.custom_nodes.items()]
                self.sel_node.items = n_opts
                if self.app_settings.get("selected_node") in n_opts:
                    self.sel_node.value = self.app_settings.get("selected_node")
            elif view_name == "wallet_viewer":
                self.populate_wallet_view_ui()
            elif view_name == "add_wallet" and hasattr(self, 'sw_w_readonly'):


                self.sw_w_readonly.value = False
                self.on_readonly_toggle(self.sw_w_readonly)
                if hasattr(self, 'inp_w_name'): self.inp_w_name.value = ""
                if hasattr(self, 'inp_w_mnem'): self.inp_w_mnem.value = ""
                if hasattr(self, 'inp_w_pass'): self.inp_w_pass.value = ""
                if hasattr(self, 'inp_w_pass2'): self.inp_w_pass2.value = ""
                if hasattr(self, 'inp_w_addr'): self.inp_w_addr.value = ""
        finally:
            self._updating_ui = False
            
        self.reapply_borders()

    def update_widget_color(self, widget, background_color, text_color=None, border_color="#535C6E", border_width=1):
        if text_color: widget.style.color = text_color
        from .ui_views import apply_android_border
        from .platform_setup import IS_ANDROID
        if IS_ANDROID:
            kwargs = getattr(widget, '_toga_border_kwargs', {}).copy()
            kwargs.update(dict(bg_color=background_color, border_color=border_color, border_width=border_width))
            apply_android_border(widget, **kwargs)
        else:
            widget.style.background_color = background_color


    async def fade_widget_background(self, widget, start_hex, end_hex, duration=0.4, steps=10):
        """Simple property animator for color fading."""
        import asyncio
        from .theme import COLOR_BG
        
        def hex_to_rgb(h):
            h = h.lstrip('#')
            if h == 'transparent': return (0, 0, 0)
            return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
        
        def rgb_to_hex(rgb):
            return '#{:02x}{:02x}{:02x}'.format(int(rgb[0]), int(rgb[1]), int(rgb[2]))

        try:
            c1 = hex_to_rgb(start_hex if start_hex != 'transparent' else COLOR_INPUT_BG)
            c2 = hex_to_rgb(end_hex if end_hex != 'transparent' else COLOR_INPUT_BG)
        except: return
        
        for i in range(steps + 1):
            alpha = i / steps
            curr = tuple(c1[j] * (1 - alpha) + c2[j] * alpha for j in range(3))
            self.update_widget_color(widget, background_color=rgb_to_hex(curr))
            await asyncio.sleep(duration / steps)

    async def pulse_widget(self, widget, color=COLOR_ACCENT, duration=0.2):
        """Fade to a color and back."""
        await self.fade_widget_background(widget, COLOR_INPUT_BG, color, duration=duration, steps=6)
        await self.fade_widget_background(widget, color, COLOR_INPUT_BG, duration=duration, steps=6)
        self.update_widget_color(widget, background_color=COLOR_INPUT_BG, border_color="#535C6E")

    # =========================================================================
    # EVENT HANDLERS & LOGIC
    # =========================================================================
    def refresh_favorites_ui(self):
        if not hasattr(self, 'fav_box'): return
        self.fav_box.clear()
        row1 = toga.Box(style=Pack(direction=ROW, margin_bottom=5, justify_content=CENTER))
        row2 = toga.Box(style=Pack(direction=ROW, justify_content=CENTER))
        

        from .ui_views import apply_android_border
        from .theme import COLOR_BLUE, FONT_SIZE_SM, FONT_SIZE_BASE
        for i, fav in enumerate(self.favorites):
            if i == 0:
                btn_text = ""
                btn_val = "ERG"
                bg = COLOR_INPUT_BG
            else:
                btn_text = fav[:5] + ".." if len(fav) > 5 else fav
                btn_val = fav
                bg = COLOR_INPUT_BG
            
            ico = self.get_token_icon(btn_val)
            border = "#535C6E"

            btn_font_size = 14 if i == 0 else 10

            btn_inner = toga.Box(style=Pack(direction=ROW, align_items=CENTER, width=88, height=58, margin_left=2, margin_right=2))
            
            content_box = toga.Box(style=Pack(direction=ROW, align_items=CENTER, flex=1, justify_content=CENTER))
            if ico:
                ico_size = 36 if i == 0 else 16
                ico_margin = 0 if not btn_text else 4
                content_box.add(toga.ImageView(image=ico, style=Pack(width=ico_size, height=ico_size, margin_right=ico_margin)))
            
            if btn_text:
                lbl = toga.Label(btn_text, style=Pack(color="#FFFFFF", background_color="transparent", font_size=btn_font_size))
                content_box.add(lbl)
            btn_inner.add(content_box)

            from .ui_views import apply_android_border, apply_android_click
            btn = apply_android_border(btn_inner, bg_color=bg, border_color=border, v_padding=2, h_padding=2, radius=10)
            btn._fav_index = i
            btn._fav_name = btn_val
            apply_android_click(btn, self.on_fav_click)
            
            if i < 4: row1.add(btn)
            elif i < 8: row2.add(btn)
            
        self.fav_box.add(row1); self.fav_box.add(row2)

    def toggle_edit_favs(self, widget):
        self.edit_favs_mode = widget.value

    async def on_fav_click(self, widget):
        index = widget._fav_index
        fav = widget._fav_name
        if self.edit_favs_mode:
            if fav.upper() != "ERG":
                self._selector_target = "fav"
                self.replace_fav_idx = index
                self.navigate_to("token_selector")
            return
        if fav == "?": return
        
        target_box = None
        if getattr(self, '_fav_first_idx', None) is None:
            # --- FIRST SELECTION (FROM) ---
            target_box = getattr(self, 'from_top_row', None)
            if target_box: asyncio.create_task(self.pulse_widget(target_box, color="#1B4D3E"))
            
            self._fav_first_idx = index
            # Visual Feedback: Fade to green on click
            asyncio.create_task(self.fade_widget_background(widget, COLOR_INPUT_BG, COLOR_ACCENT, duration=0.2))
            
            self.set_from_asset(fav)
        else:
            # --- SECOND SELECTION (TO) ---
            target_box = getattr(self, 'to_top_row', None)
            if target_box: asyncio.create_task(self.pulse_widget(target_box, color="#1B4D3E"))
            
            self.set_to_asset(fav)
            self._fav_first_idx = None
            
            # Visual Feedback: Fade the 2nd favorite to green and back
            await self.fade_widget_background(widget, COLOR_INPUT_BG, COLOR_ACCENT, duration=0.2, steps=6)
            await self.fade_widget_background(widget, COLOR_ACCENT, COLOR_INPUT_BG, duration=0.2, steps=6)

            # Visual Feedback: Clear outlines on ALL favorite buttons
            self.refresh_favorites_ui()

    def set_from_asset(self, asset_name):
        canonical = self.trade_mapper.normalize_asset(asset_name)
        self.from_asset = canonical
        if hasattr(self, 'lbl_from_token'):
            txt = canonical if canonical else "TOKEN"
            if len(txt) > 6: txt = txt[:6] + ".."
            self.lbl_from_token.text = txt
            if hasattr(self, 'img_from'):
                self.img_from.image = self.get_token_icon(canonical) if canonical else None
            self._refresh_route_label()
            self.on_amount_change(None)
            self.reapply_borders()
            self.refresh_main_balances()

    def set_to_asset(self, asset_name):
        canonical = self.trade_mapper.normalize_asset(asset_name)
        self.to_asset = canonical
        if hasattr(self, 'lbl_to_token'):
            txt = canonical if canonical else "Select"
            if len(txt) > 6: txt = txt[:6] + ".."
            self.lbl_to_token.text = txt
            if hasattr(self, 'img_to'):
                self.img_to.image = self.get_token_icon(canonical) if canonical else None
            self._refresh_route_label()
            self.on_amount_change(None)
            self.reapply_borders()
            self.refresh_main_balances()

    def on_swap_direction(self, widget):
        old_from = self.from_asset
        old_to = self.to_asset
        
        # Try to capture and clean the current quote amount
        quote_text = self.lbl_quote.text if hasattr(self, 'lbl_quote') else "0"
        clean_quote = "0"
        try:
            # Strip commas and check if it's a positive number
            val_str = quote_text.replace(",", "").split()[0] # Handle cases like '1.23 ERG'
            val = float(val_str)
            if val > 0:
                clean_quote = val_str
        except:
            clean_quote = "0"

        if old_from and old_to:
            self.set_from_asset(old_to)
            self.set_to_asset(old_from)
            if hasattr(self, 'inp_amount'):
                self.inp_amount.value = clean_quote
        elif old_from:
            self.from_asset = ""
            self.to_asset = ""
            if hasattr(self, 'lbl_from_token'): self.lbl_from_token.text = "TOKEN"
            if hasattr(self, 'lbl_to_token'): self.lbl_to_token.text = "TOKEN"
            if hasattr(self, 'img_from'): self.img_from.image = None
            if hasattr(self, 'img_to'): self.img_to.image = None
            self.lbl_route.text = ""
            self.lbl_quote.text = "--"
            if hasattr(self, 'inp_amount'): pass
            
        self.refresh_main_balances()

    def _clear_trade_inputs(self):
        """Clears all trade-related inputs (assets, amount, quote) after a successful transaction."""
        self.from_asset = ""
        self.to_asset = ""
        self.last_amount = ""
        if hasattr(self, 'inp_amount'): self.inp_amount.value = ""
        if hasattr(self, 'lbl_from_token'): self.lbl_from_token.text = "TOKEN"
        if hasattr(self, 'lbl_to_token'): self.lbl_to_token.text = "TOKEN"
        if hasattr(self, 'img_from'): self.img_from.image = None
        if hasattr(self, 'img_to'): self.img_to.image = None
        if hasattr(self, 'lbl_route'): self.lbl_route.text = ""
        if hasattr(self, 'lbl_quote'): self.lbl_quote.text = "--"
        self._fav_first_idx = None
        self.refresh_favorites_ui()
        self.refresh_main_balances()

    def _refresh_route_label(self):
        if self.from_asset and self.to_asset:
            route = self.trade_mapper.resolve(self.from_asset, self.to_asset)
            if self.app_settings.get("debug_mode", False) and route:
                self.lbl_route.style.color = LBL_ROUTE_FONT_COLOR
                self.lbl_route.text = f"-> {route.order_type if hasattr(route, 'order_type') else 'AMM'} {route.token_key if hasattr(route, 'token_key') else ''}"
            elif not route:
                self.lbl_route.style.color = COLOR_DANGER
                self.lbl_route.text = "No pool found"
            else:
                self.lbl_route.style.color = LBL_ROUTE_FONT_COLOR
                self.lbl_route.text = ""
        else:
            self.lbl_route.style.color = LBL_ROUTE_FONT_COLOR
            self.lbl_route.text = ""

    def on_max_click(self, widget):
        if not self.from_asset or self.from_asset == "Select token": return
        
        if self.from_asset.upper() == "ERG":
            val = self.current_balances.get('ERG', 0)
            fee_val = self.sld_fee.value if hasattr(self, 'sld_fee') else 0.001
            max_val = max(0, val - fee_val)
            out_str = f"{max_val:.9f}".rstrip('0').rstrip('.')
            if out_str.endswith('.'): out_str = out_str[:-1]
            self.inp_amount.value = out_str if max_val > 0 else "0"
        else:
            # Find token ID and decimals
            tid = None; decimals = 0
            if self.from_asset in self.trade_mapper.tokens:
                t_node = self.trade_mapper.tokens[self.from_asset]
                tid = t_node.get("id"); decimals = t_node.get("dec", 0)
            if not tid:
                for k, v in self.trade_mapper.tokens.items():
                    if "-" in k:
                        if v.get("id_in") and k.split("-")[-1] == self.from_asset:
                            tid = v.get("id_in"); decimals = v.get("dec_in", 0); break
                        if v.get("id_out") and k.split("-")[0] == self.from_asset:
                            tid = v.get("id_out"); decimals = v.get("dec_out", 0); break
            if tid:
                amt = self.current_balances.get('tokens', {}).get(tid, 0)
                fmt_amt = amt / (10**decimals)
                if decimals > 0:
                    display_amt = f"{fmt_amt:.{min(decimals, 8)}f}".rstrip('0').rstrip('.')
                    if display_amt.endswith('.'): display_amt = display_amt[:-1]
                else: display_amt = str(amt)
                self.inp_amount.value = display_amt

    def on_amount_change(self, widget):
        if not hasattr(self, 'lbl_quote'): return
        if hasattr(self, 'inp_amount'):
            self.last_amount = self.inp_amount.value
        self.lbl_quote.text = "Fetching..."
        asyncio.create_task(self.fetch_quote_async())

    async def fetch_quote_async(self):
        await asyncio.sleep(0.4) 
        val = self.inp_amount.value
        try: amount = float(val) if val else 0
        except: return

        if amount <= 0 or not self.from_asset or not self.to_asset:
            self.lbl_quote.text = "--"
            return

        route = self.trade_mapper.resolve(self.from_asset, self.to_asset)
        if not route: return

        self.set_loading(True)
        try:
            check_mempool = True
            if hasattr(self, 'sw_lp'):
                check_mempool = self.sw_lp.value

            client = NodeClient(self.node_url_value)
            res, impact = await asyncio.to_thread(
                Trader(client, None, self.token_manager.tokens).get_quote, 
                route.token_key, amount, route.order_type.lower(), route.pool_type,
                check_mempool=check_mempool
            )
            self.lbl_quote.text = str(res)
            self.last_price_impact = impact
            
            # Update Price Impact UI if label exists
            if hasattr(self, 'lbl_impact'):
                if impact > 5:
                    self.lbl_impact.text = f"Price Impact: {impact:.2f}%"
                    self.lbl_impact.style.color = COLOR_DANGER
                elif impact > 0.01:
                    self.lbl_impact.text = f"Price Impact: {impact:.2f}%"
                    self.lbl_impact.style.color = COLOR_TEXT_DIM
                else:
                    self.lbl_impact.text = ""
        except Exception as e:
            self.lbl_quote.text = f"Err: {e}"
        finally:
            self.set_loading(False)

    def set_exec_mode(self, is_safe):
        self.is_simulation = is_safe
        # Update button colors and submit label directly (no view rebuild)
        if hasattr(self, 'btn_safe'):
            from .theme import COLOR_ACCENT, COLOR_CHIP, COLOR_DANGER
            self.update_widget_color(self.btn_safe, COLOR_ACCENT if is_safe else COLOR_CHIP)
            self.update_widget_color(self.btn_live, COLOR_DANGER if not is_safe else COLOR_CHIP)
        
        # Both Submit and Simulate use the same premium green color
        sub_bg = "#00D18B"
        if hasattr(self, 'btn_submit'):
            self.update_widget_color(self.btn_submit, sub_bg)
            self.btn_submit.text = "SIMULATE" if is_safe else "SUBMIT"
        self.reapply_borders()

    def on_fee_change(self, widget):
        if hasattr(self, 'lbl_fee'):
            self.lbl_fee.text = f"{widget.value:.3f} ERG"

    def open_selector(self, target):
        self._selector_target = target
        self.replace_fav_idx = None
        self.navigate_to("token_selector")

    def filter_tokens(self, widget):
        if not hasattr(self, '_search_task') or self._search_task is None:
            self._search_task = None
        
        if self._search_task:
            self._search_task.cancel()
            
        self._search_task = asyncio.create_task(self._filter_tokens_async())

    async def _filter_tokens_async(self):
        # Debounce search
        await asyncio.sleep(0.3)
        
        text = self.inp_t_search.value.lower() if hasattr(self, 'inp_t_search') and self.inp_t_search.value else ""
        if not hasattr(self, 'box_tokens'): return
        self.box_tokens.clear()
        
        candidates = list(self._all_assets) # Copy to avoid mutation during sort
        balance_cache = {} # name -> raw_amount
        
        # Pre-calculate balances for sorting
        for t in candidates:
            if t.upper() == "ERG":
                balance_cache[t] = self.current_balances.get("ERG", 0)
            else:
                tid = self.token_manager.tokens.get(t, {}).get("id")
                if tid:
                    balance_cache[t] = self.current_balances.get("tokens", {}).get(tid, 0)
                else:
                    balance_cache[t] = 0

        def get_priority(name):
            n_up = name.upper()
            if n_up == "ERG": return 0
            if n_up == "SIGUSD": return 1
            if n_up == "SIGRSV": return 2
            if n_up == "USE": return 3
            if n_up == "DEXYGOLD": return 4
            if balance_cache.get(name, 0) > 0: return 5
            return 6

        candidates.sort(key=lambda x: (get_priority(x), x.lower()))

        if self._selector_target == "fav":
            candidates = [a for a in candidates if a.upper() != "ERG"]
        elif self._selector_target == "to" and self.from_asset:
            # Re-filter based on pairs if needed, keeping the sort
            valid_to = self.trade_mapper.to_assets_for(self.from_asset)
            if valid_to:
                candidates = [a for a in candidates if a in valid_to]
            
        count = 0
        from .ui_views import apply_android_border, apply_android_click
        
        for i, t in enumerate(candidates):
            if not text or text in t.lower():
                ico = self.get_token_icon(t)
                
                btn_inner = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin=2, padding=8))
                if ico:
                    btn_inner.add(toga.ImageView(image=ico, style=Pack(width=48, height=48, margin_right=15)))
                

                btn_inner.add(toga.Label(t, style=Pack(color=BTN_TOKEN_SEL_FONT_COLOR, font_size=FONT_SIZE_LG, flex=1, margin_left=15)))
                
                # Balance Label (if owned)
                raw_bal = balance_cache.get(t, 0)
                if raw_bal > 0:
                    if t.upper() == "ERG":
                        bal_val = self.current_balances.get("ERG", 0)
                        out_str = f"{bal_val:,.9f}".rstrip('0').rstrip('.')
                        if out_str.endswith('.'): out_str = out_str[:-1]
                        display_bal = out_str if bal_val > 0 else "0"
                    else:
                        tid = self.token_manager.tokens.get(t, {}).get("id")
                        info = self.token_cache.get(tid)
                        decimals = info.get("decimals", 0) if info else 0
                        fmt_amt = raw_bal / (10 ** decimals)
                        if decimals > 0:
                            display_bal = f"{fmt_amt:,.{decimals}f}".rstrip('0').rstrip('.')
                            if display_bal.endswith('.'): display_bal = display_bal[:-1]
                        else:
                            display_bal = f"{raw_bal:,}"
                    
                    btn_inner.add(toga.Label(display_bal, style=Pack(color=COLOR_ACCENT, font_size=FONT_SIZE_MD, margin_right=10)))

                btn = apply_android_border(btn_inner, bg_color=BTN_TOKEN_SEL_COLOR, radius=12)
                btn._token_name = t
                apply_android_click(btn, self.on_token_selected)
                
                self.box_tokens.add(btn)
                count += 1
                

                if count % 10 == 0:
                    await asyncio.sleep(0)
                
                if count > 100 and not text: break

    async def on_token_selected(self, widget):
        t = widget._token_name
        if self._selector_target == "fav" and self.replace_fav_idx is not None:
            self.favorites[self.replace_fav_idx] = t
            self._save_json(self.favorites_file, self.favorites)
            self.replace_fav_idx = None
            self.edit_favs_mode = False
            self.navigate_to("main")
        elif self._selector_target == "from":
            self.set_from_asset(t)
            self.navigate_to("main")
        elif self._selector_target == "to":
            self.set_to_asset(t)
            self.navigate_to("main")

    # --- Wallet & Node Logic ---
    def open_wallet_selector(self):
        self.navigate_to("wallet_selector")

    def filter_wallets(self):
        if not hasattr(self, 'box_wallets'): return
        self.box_wallets.clear()
        
        w_opts = [{"display": "ErgoPay", "key": "ErgoPay"}]
        for k, v in self.wallets.items():
            if v.get("read_only"):
                w_opts.append({"display": f"{k} (ergopay)", "key": k})
            else:
                w_opts.append({"display": k, "key": k})
        
        from .ui_views import apply_android_border, apply_android_click
        from .theme import BTN_TOKEN_SEL_COLOR, BTN_TOKEN_SEL_FONT_COLOR, ICON_WALLET, FONT_FAMILY_ICONS, FONT_SIZE_MD
        
        for w_item in w_opts:
            w_display = w_item["display"]
            w_key = w_item["key"]
            # Custom Box for Selection Item
            btn_inner = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin=2, padding=8))
            
            # Use icon from font
            lbl_ico = toga.Label(ICON_WALLET, style=Pack(width=48, color="#FFFFFF", font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_XL, text_align=CENTER, margin_right=15))
            btn_inner.add(lbl_ico)
            
            # Added margin_left to provide padding for the text
            btn_inner.add(toga.Label(w_display, style=Pack(color=BTN_TOKEN_SEL_FONT_COLOR, font_size=FONT_SIZE_MD, flex=1, margin_left=20)))
            
            btn = apply_android_border(btn_inner, bg_color=BTN_TOKEN_SEL_COLOR, radius=12)
            btn._wallet_key = w_key
            btn._wallet_display = w_display
            apply_android_click(btn, self.on_wallet_selected_from_list)
            
            self.box_wallets.add(btn)

    def on_wallet_selected_from_list(self, widget):
        real_key = widget._wallet_key
        display_val = widget._wallet_display
            
        self.app_settings["selected_wallet"] = real_key
        self._save_json(self.settings_file, self.app_settings)
        self.selected_wallet = real_key
        
        # Update Main UI labels
        if hasattr(self, 'lbl_selected_wallet'):
            self.lbl_selected_wallet.text = display_val

        # Common logic for initializing the selected wallet
        self.initialize_wallet_session(real_key, display_val)
        self.navigate_to("main")

    def on_wallet_select(self, widget):

        if getattr(self, '_updating_ui', False): return
        val = widget.value
        real_key = val
        if val and val.endswith(" (ergopay)"):
            real_key = val[:-10]
        self.initialize_wallet_session(real_key, val)

    def initialize_wallet_session(self, real_key, display_val):
        self.app_settings["selected_wallet"] = real_key
        self._save_json(self.settings_file, self.app_settings)
        self.selected_wallet = real_key
        
        if not hasattr(self, 'inp_address'): return
        self.inp_address.readonly = True

        if not display_val or display_val == "Select Wallet":
            self._update_address("")
        elif display_val == "ErgoPay":
            self.inp_address.readonly = False
            self._update_address("")
        else:
            stored = self.wallets.get(real_key)
            if stored and "address" in stored:
                self._update_address(stored["address"])
            else:
                self._update_address("")
            self.on_amount_change(None)
    
        if self.current_address:
            self.fetch_wallet_balances(silent=True)
        self.reapply_borders()
        
    def _update_address(self, addr):
        self._internal_address_update = True
        self.current_address = addr
        if not hasattr(self, 'inp_address'): 
            self._internal_address_update = False
            return
        if not addr:
            self.inp_address.value = ""
            self._internal_address_update = False
            return

        if len(addr) > 20:
             display = f"{addr[:8]}...{addr[-8:]}"
        else:
             display = addr
        self.inp_address.value = display
        self._internal_address_update = False

    def on_address_input_change(self, widget):
        if getattr(self, '_internal_address_update', False): return
        # If user types manually (e.g. ErgoPay mode), sync to current_address
        self.current_address = widget.value
        self.refresh_main_balances()
        if self.current_address:
            self.fetch_wallet_balances(silent=True)

    def refresh_main_balances(self):
        if not hasattr(self, 'lbl_from_balance') or not hasattr(self, 'lbl_to_balance'): return
        
        def get_bal_str(asset):
            if not asset or asset == "Select token": return ""
            if asset.upper() == "ERG":
                bal_val = self.current_balances.get('ERG', 0)
                out_str = f"{bal_val:,.9f}".rstrip('0').rstrip('.')
                if out_str.endswith('.'): out_str = out_str[:-1]
                return f"Bal: {out_str if bal_val > 0 else '0'}"
            
            # Find token ID and decimals
            tid = None
            decimals = 0
            # Most tokens are top-level keys in the trade_mapper.tokens dict
            if asset in self.trade_mapper.tokens:
                t_node = self.trade_mapper.tokens[asset]
                tid = t_node.get("id")
                decimals = t_node.get("dec", 0)
            
            # Fallback: scan for tokens that might only exist as part of a pair
            if not tid:
                for k, v in self.trade_mapper.tokens.items():
                    if "-" in k:
                        if v.get("id_in") and k.split("-")[-1] == asset:
                            tid = v.get("id_in"); decimals = v.get("dec_in", 0); break
                        if v.get("id_out") and k.split("-")[0] == asset:
                            tid = v.get("id_out"); decimals = v.get("dec_out", 0); break
            
            if tid:
                amt = self.current_balances.get('tokens', {}).get(tid, 0)
                fmt_amt = amt / (10**decimals)
                if decimals > 0:
                    display_amt = f"{fmt_amt:,.{min(decimals, 8)}f}".rstrip('0').rstrip('.')
                    if display_amt.endswith('.'): display_amt = display_amt[:-1]
                else: display_amt = f"{amt:,}"
                return f"Bal: {display_amt}"
            return "Bal: 0"
            
        self.lbl_from_balance.text = get_bal_str(self.from_asset)
        self.lbl_to_balance.text = get_bal_str(self.to_asset)
        
        # Toggle MAX button visibility: Hide for ERG, Show for tokens
        if hasattr(self, 'btn_max'):
            if self.from_asset and self.from_asset.upper() == "ERG":
                self.btn_max.style.visibility = 'hidden'
            else:
                self.btn_max.style.visibility = 'visible'

    async def handle_tx_error(self, e, context):
        """Humorous error handling for specific blockchain errors."""
        err_msg = str(e)
        print(f"[DIALOG] {context} Error: {err_msg}", flush=True)
        
        # Check for node connection issues
        if "unreachable" in err_msg.lower() or "blockchaincontext" in err_msg.lower():
            connection_msg = (
                "Piggy cannot reach the Ergo node! 🐽🔌\n\n"
                "The current node seems to be offline or unreachable.\n\n"
                "Please go to Settings and try selecting a different node to continue."
            )
            await self.main_window.dialog(toga.InfoDialog("Node Connection Issue", connection_msg))
        # Check for mempool box conflict (404 not-found)
        elif "404" in err_msg or "not-found" in err_msg.lower():
            funny_msg = (
                "Piggy says: 'Hold your oinks!' 🐷\n\n"
                "Your wallet is currently in a bit of a scramble! It seems you're trying to spend "
                "boxes that are already busy in another transaction (mempool conflict).\n\n"
                "Please wait for your pending trades to confirm first, OR go to the advanced settings "
                "to turn off mempool options to avoid this digital traffic jam!"
            )
            await self.main_window.dialog(toga.InfoDialog("Wallet Scramble!", funny_msg))
        else:
            # Standard error message for everything else
            await self.main_window.dialog(toga.InfoDialog(f"{context} Error", err_msg))

    def on_readonly_toggle(self, widget):
        # Toggle between Mnemonic box and Address box
        if widget.value: # Read-only mode (Standard ErgoPay style)
            self.box_w_mnem.style.visibility = 'hidden'
            self.box_w_mnem.style.height = 0
            # Show address box
            self.box_w_addr.style.visibility = 'visible'
            self.box_w_addr.style.height = 80 
            if hasattr(self, 'inp_w_addr'): self.inp_w_addr.enabled = True
        else: # Mnemonic mode
            self.box_w_mnem.style.visibility = 'visible'
            try:
                del self.box_w_mnem.style.height # Auto height
            except Exception:
                pass
            # Hide address box
            self.box_w_addr.style.visibility = 'hidden'
            self.box_w_addr.style.height = 0
            if hasattr(self, 'inp_w_addr'): self.inp_w_addr.enabled = False
        self.update_save_wallet_btn()

    def on_bio_toggle(self, widget):
        """Toggle password visibility when biometrics is selected."""
        if widget.value: # Biometrics ON
            self.box_w_pass.style.visibility = 'hidden'
            self.box_w_pass.style.height = 0
            self.inp_w_pass.value = ""
            self.inp_w_pass2.value = ""
        else: # Biometrics OFF
            self.box_w_pass.style.visibility = 'visible'
            try: del self.box_w_pass.style.height
            except: pass
        self.update_save_wallet_btn()

    def update_save_wallet_btn(self, widget=None):
        """Validate the Add Wallet form and update the save button color."""
        if not hasattr(self, 'btn_w_save'):
            return
        is_ro = hasattr(self, 'sw_w_readonly') and self.sw_w_readonly.value
        use_bio = hasattr(self, 'sw_w_bio') and self.sw_w_bio.value
        
        if is_ro:
            name = (hasattr(self, 'inp_w_name') and self.inp_w_name.value or "").strip()
            addr = (hasattr(self, 'inp_w_addr') and self.inp_w_addr.value or "").strip()
            ready = bool(name and addr)
        elif use_bio:
            name = (hasattr(self, 'inp_w_name') and self.inp_w_name.value or "").strip()
            mnem = (hasattr(self, 'inp_w_mnem') and self.inp_w_mnem.value or "").strip()
            ready = bool(name and mnem)
        else:
            name = (hasattr(self, 'inp_w_name') and self.inp_w_name.value or "").strip()
            mnem = (hasattr(self, 'inp_w_mnem') and self.inp_w_mnem.value or "").strip()
            pwd  = (hasattr(self, 'inp_w_pass') and self.inp_w_pass.value or "").strip()
            pwd2 = (hasattr(self, 'inp_w_pass2') and self.inp_w_pass2.value or "").strip()
            ready = bool(name and mnem and pwd and pwd2 and pwd == pwd2)
        
        bg = COLOR_ACCENT if ready else "#1C1C1C"
        fg = "#FFFFFF" if ready else "#555555"
        self.btn_w_save.style.color = fg
        self.update_widget_color(self.btn_w_save, background_color=bg)

    def update_save_node_btn(self, widget=None):
        """Validate the Add Node form and update the save button color."""
        if not hasattr(self, 'btn_n_save'):
            return
        name = (hasattr(self, 'inp_node_name') and self.inp_node_name.value or "").strip()
        url = (hasattr(self, 'inp_node_url') and self.inp_node_url.value or "").strip()
        ready = bool(name and url)
        
        bg = COLOR_ACCENT if ready else "#1C1C1C"
        fg = "#FFFFFF" if ready else "#555555"
        self.btn_n_save.style.color = fg
        self.update_widget_color(self.btn_n_save, background_color=bg)

    async def save_new_wallet(self, widget):
        name = self.inp_w_name.value.strip()
        is_readonly = self.sw_w_readonly.value
        
        if is_readonly:
            # ONLY check Name and Address for Read-Only mode
            addr = self.inp_w_addr.value.strip()
            if not name or not addr:
                print("[DIALOG] Error: Name and Address are required for Read-Only wallets.", flush=True)
                await self.main_window.dialog(toga.InfoDialog("Error", "Name and Address are required for Read-Only wallets."))
                return
            
            self.wallets[name] = {
                "address": addr,
                "read_only": True,
                "type": "ergopay"
            }
            self._save_json(self.wallets_file, self.wallets)
            print(f"[DIALOG] Success: Read-only wallet '{name}' saved.", flush=True)
            await self.main_window.dialog(toga.InfoDialog("Success", f"Read-only wallet '{name}' saved."))
            self.selected_wallet = name
            self.navigate_to("main")
        else:
            # REQUIRES Mnemonic and Password for full wallets
            mnem = self.inp_w_mnem.value.strip()
            pwd = self.inp_w_pass.value.strip()
            pwd2 = self.inp_w_pass2.value.strip()
            
            use_bio = self.sw_w_bio.value if hasattr(self, 'sw_w_bio') else False
            
            if not use_bio:
                if not name or not mnem or not pwd:
                    print("[DIALOG] Error: Name, Mnemonic, and Password are required.", flush=True)
                    await self.main_window.dialog(toga.InfoDialog("Error", "Name, Mnemonic, and Password are required."))
                    return
                if pwd != pwd2:
                    print("[DIALOG] Error: Passwords do not match.", flush=True)
                    await self.main_window.dialog(toga.InfoDialog("Error", "Passwords do not match"))
                    return
            else:
                if not name or not mnem:
                     print("[DIALOG] Error: Name and Mnemonic are required.", flush=True)
                     await self.main_window.dialog(toga.InfoDialog("Error", "Name and Mnemonic are required."))
                     return
                
                # VERIFICATION STEP: Since we are going passwordless, confirm biometrics work NOW
                print(f"[piggytrade] Verifying biometrics for new wallet '{name}'...", flush=True)
                success, result = await self.bio_helper.authenticate(
                    title="Confirm Fingerprint",
                    subtitle="Verify biometrics to secure your wallet"
                )
                if not success:
                    print(f"[DIALOG] Biometric Verification Failed: {result}", flush=True)
                    await self.main_window.dialog(toga.InfoDialog("Verification Failed", f"Could not verify biometrics: {result}\n\nPlease try again or use a password."))
                    return
                print("[piggytrade] Biometric verification successful.", flush=True)
                
            asyncio.create_task(self._save_wallet_async(name, mnem, pwd, self.sw_w_legacy.value, use_bio))


    async def _save_wallet_async(self, name, mnem, pwd, use_legacy=False, use_bio=False):
        try:
            # If bio is ON and password is empty, generate a random local key
            if use_bio and not pwd:
                import secrets
                import string
                pwd = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(32))
                print(f"[piggytrade] Generated random local key for biometric wallet '{name}'", flush=True)

            print(f"[piggytrade] Deriving address for {name} (legacy={use_legacy})...", flush=True)
            signer = await asyncio.to_thread(ErgoSigner, self.node_url_value)
            public_address = await asyncio.to_thread(signer.get_address, mnem, "", 0, use_legacy)
            print(f"[piggytrade] Derived address: {public_address}", flush=True)
            
            salt = os.urandom(16)
            kdf = Scrypt(
                salt=salt,
                length=32,
                n=2**15,
                r=8,
                p=1,
                backend=default_backend()
            )
            key = base64.urlsafe_b64encode(kdf.derive(pwd.encode()))
            f = Fernet(key)
            token = f.encrypt(mnem.encode())
            
            self.wallets[name] = {
                "salt": base64.b64encode(salt).decode(),
                "token": base64.b64encode(token).decode(),
                "address": public_address,
                "type": "mnemonic",
                "kdf": "scrypt",
                "use_legacy": use_legacy
            }
            self._save_json(self.wallets_file, self.wallets)
            
            # Step 3: Hardware Keystore Integration
            if use_bio:
                iv, encrypted_pwd = self.ks_helper.encrypt_data(pwd)
                if iv and encrypted_pwd:
                    self.biometrics_config[name] = {"iv": iv, "data": encrypted_pwd}
                    self._save_json(self.biometrics_config_file, self.biometrics_config)
                    print(f"[piggytrade] Biometrics enabled for wallet '{name}'", flush=True)

            print(f"[DIALOG] Success: Wallet '{name}' saved.", flush=True)
            await self.main_window.dialog(toga.InfoDialog("Success", f"Wallet '{name}' saved."))
            self.selected_wallet = name
            self.navigate_to("main")
        except Exception as e:
            print(f"[DIALOG] Error (save wallet): {e}", flush=True)
            await self.main_window.dialog(toga.InfoDialog("Error", str(e)))
        finally:
            # Best effort memory wiping
            if 'mnem' in locals(): del mnem
            if 'pwd' in locals(): del pwd
            if 'key' in locals(): del key
            gc.collect()

    async def delete_current_wallet(self, widget):
        if not self.selected_wallet or self.selected_wallet == "Select Wallet" or self.selected_wallet == "ErgoPay":
            return
            

        print(f"[DIALOG] QuestionDialog: Confirm delete wallet '{self.selected_wallet}'?", flush=True)
        result = await self.main_window.dialog(toga.QuestionDialog("Confirm Delete", f"Delete wallet {self.selected_wallet}?"))
        
        if result:
            # Second confirmation
            print(f"[DIALOG] QuestionDialog: Really REALLY sure?", flush=True)
            result2 = await self.main_window.dialog(toga.QuestionDialog("Confirm Delete", "Are you really REALLY sure? This cannot be undone!"))
            
            if result2:
                if self.selected_wallet in self.wallets:
                    del self.wallets[self.selected_wallet]
                    self._save_json(self.wallets_file, self.wallets)
                    self.selected_wallet = "Select Wallet"
                    self.cached_views.clear()
                    self.navigate_to("main")
                    print("[DIALOG] Deleted: Wallet removed.", flush=True)
                    await self.main_window.dialog(toga.InfoDialog("Deleted", "Wallet removed."))

    def on_node_select(self, widget):
        if getattr(self, '_updating_ui', False): return
        val = widget.value
        self.app_settings["selected_node"] = val
        self._save_json(self.settings_file, self.app_settings)
        if not val or val == "Select Node": return
        self.node_url_value = val.split(": ", 1)[1]
        self.reapply_borders()

    def save_new_node(self, widget):
        name = self.inp_node_name.value.strip()
        url = self.inp_node_url.value.strip()
        if not name or not url: return
        self.custom_nodes[name] = {"url": url, "address": ""}
        self._save_json(self.nodes_file, self.custom_nodes)
        self.navigate_to("settings")

    async def delete_current_node(self, widget):
        val = self.sel_node.value
        if not val or val == "Select Node": return
        
        print(f"[DIALOG] QuestionDialog: Confirm delete node '{val.split(': ')[0]}'?", flush=True)
        result = await self.main_window.dialog(toga.QuestionDialog("Confirm Delete", f"Delete node {val.split(': ')[0]}?"))
        if result:
            key = val.split(": ", 1)[0]
            if key in self.custom_nodes:
                del self.custom_nodes[key]
                self._save_json(self.nodes_file, self.custom_nodes)
                if not self.custom_nodes: 
                    self.custom_nodes = NODES.copy()
                self.navigate_to("settings")
                print("[DIALOG] Deleted: Node removed.", flush=True)
                await self.main_window.dialog(toga.InfoDialog("Deleted", "Node removed."))

    # --- Trading Execution Logic ---
    async def prepare_swap(self, widget):
        if not self.from_asset or not self.to_asset: return

        # Beta Warning Dialog
        result = await self.main_window.dialog(toga.QuestionDialog(
            "Beta Warning",
            "This app is still in beta. By continuing you acknowledge that you understand there might be bugs and that you need to validate the output before submitting to the chain. Submitted transacations are irreversible.Do you want to continue?"
        ))
        if not result:
            return

        route = self.trade_mapper.resolve(self.from_asset, self.to_asset)
        try: amount = float(self.inp_amount.value) if self.inp_amount.value else 0
        except: amount = 0
        if not route or amount <= 0: return
        
        wallet_addr = self.current_address
        if not wallet_addr: return
        
        fee = self.sld_fee.value
        m_me = self.sw_wallet.value
        m_lp = self.sw_lp.value
        
        self.btn_submit.enabled = False
        self.btn_submit.text = "Building transaction..."
        asyncio.create_task(self._prepare_swap_async(route, amount, wallet_addr, fee, m_me, m_lp))

    async def _prepare_swap_async(self, route, amount, wallet_addr, fee, m_me, m_lp):
        self.set_loading(True)
        try:
            client = NodeClient(self.node_url_value)
            trader = Trader(client, TxBuilder(client, wallet_addr), self.token_manager.tokens, None)
            
            trader._auth_link = self._get_node_auth_link()

            tx_dict = await asyncio.to_thread(
                trader.build_swap_transaction,
                route.token_key, amount, route.order_type.lower(),
                pool_type=route.pool_type, sender_address=wallet_addr,
                use_mempool=m_me, use_lp_mempool=m_lp,
                pre_1627=self.use_pre1627_value, fee=fee
            )
            
            self.prepared_tx_dict = tx_dict
            self.navigate_to("review_tx")

            # Clear password and update UI based on Biometrics
            self.inp_rev_pass.value = ""
            bio_entry = self.biometrics_config.get(self.selected_wallet)
            has_bio = bool(bio_entry and self.bio_helper.available)
            
            if has_bio:
                # Biometric wallet: show only the bio hint, hide password UI entirely
                self.lbl_rev_bio_hint.style.visibility = 'visible'
                try: del self.lbl_rev_bio_hint.style.height 
                except: pass
                self.btn_rev_show_pass.style.visibility = 'hidden'
                self.btn_rev_show_pass.style.height = 0
                self.inp_rev_pass.style.visibility = 'hidden'
                self.inp_rev_pass.style.height = 0
            else:
                self.lbl_rev_bio_hint.style.visibility = 'hidden'
                self.lbl_rev_bio_hint.style.height = 0
                self.btn_rev_show_pass.style.visibility = 'hidden'
                self.btn_rev_show_pass.style.height = 0
                self.inp_rev_pass.style.visibility = 'visible'
                try: del self.inp_rev_pass.style.height
                except: pass

            service_fee = tx_dict.get("p_shift", 0) / 1e9
            amt_disp = self.inp_amount.value
            expected = self.lbl_quote.text
            
            # Identify what is being bought and paid
            if route.pool_type == "erg":
                if route.order_type.lower() == "buy":
                    buy_t, buy_a = route.token_key, expected
                    pay_t, pay_a = "ERG", amt_disp
                else:
                    buy_t, buy_a = "ERG", expected
                    pay_t, pay_a = route.token_key, amt_disp
            else:
                out_ast, in_ast = self.trade_mapper._pair_sides.get(route.token_key, (route.token_key, "???"))
                if route.order_type.lower() == "buy":
                    buy_t, buy_a = out_ast, expected
                    pay_t, pay_a = in_ast, amt_disp
                else:
                    buy_t, buy_a = in_ast, expected
                    pay_t, pay_a = out_ast, amt_disp

            if hasattr(self, 'lbl_rev_buy'):
                self.lbl_rev_buy.text = f"{buy_a} {buy_t}"
            
            if hasattr(self, 'lbl_rev_pay'):
                try:
                    p_val = float(str(pay_a).replace(',', ''))
                    cost_erg = fee + service_fee
                    if pay_t == "ERG":
                        total_pay = p_val + cost_erg
                        out_str = f"{total_pay:,.9f}".rstrip('0').rstrip('.')
                        if out_str.endswith('.'): out_str = out_str[:-1]
                        self.lbl_rev_pay.text = f"{out_str} ERG"
                    else:
                        out_cost = f"{cost_erg:,.9f}".rstrip('0').rstrip('.')
                        if out_cost.endswith('.'): out_cost = out_cost[:-1]
                        self.lbl_rev_pay.text = f"{pay_a} {pay_t} + {out_cost} ERG"
                except:
                    self.lbl_rev_pay.text = f"{pay_a} {pay_t}"
                
            if hasattr(self, 'lbl_rev_fees'):
                out_service = f"{service_fee:.9f}".rstrip('0').rstrip('.')
                if out_service.endswith('.'): out_service = out_service[:-1]
                self.lbl_rev_fees.text = f"Miner Fee: {fee:.3f} + Service: {out_service} ERG"
            
            self.lbl_tx_json.value = json.dumps(tx_dict, indent=2)
            

            readable_text = self._generate_readable_tx(tx_dict)
            if hasattr(self, 'lbl_tx_readable'):
                self.lbl_tx_readable.value = readable_text
            
            is_ro = False
            wal_data = self.wallets.get(self.selected_wallet)
            if wal_data and wal_data.get("read_only"):
                is_ro = True
                
            is_ergopay = (self.selected_wallet == "ErgoPay" or is_ro)
            # Check if this is a biometric wallet
            bio_entry = self.biometrics_config.get(self.selected_wallet)
            has_bio = bool(bio_entry and self.bio_helper.available)

            if hasattr(self, 'inp_rev_pass'):
                self.inp_rev_pass.value = ""
                if is_ergopay or has_bio:
                    self.inp_rev_pass.style.visibility = 'hidden'
                    self.inp_rev_pass.style.height = 0
                else:
                    self.inp_rev_pass.style.visibility = 'visible'
                    try: del self.inp_rev_pass.style.height
                    except: pass

            # Also hide/show the bio hint and password-instead button
            if hasattr(self, 'lbl_rev_bio_hint'):
                if has_bio:
                    self.lbl_rev_bio_hint.style.visibility = 'visible'
                    try: del self.lbl_rev_bio_hint.style.height
                    except: pass
                else:
                    self.lbl_rev_bio_hint.style.visibility = 'hidden'
                    self.lbl_rev_bio_hint.style.height = 0
            if hasattr(self, 'btn_rev_show_pass'):
                self.btn_rev_show_pass.style.visibility = 'hidden'
                self.btn_rev_show_pass.style.height = 0

            if is_ergopay:
                self.btn_confirm_tx.text = "Simulate (ErgoPay)" if self.is_simulation else "Ergopay"
                self.update_widget_color(self.btn_confirm_tx, COLOR_BLUE)
            else:
                self.btn_confirm_tx.text = "Sign & Simulate" if self.is_simulation else "Confirm Swap"
                self.update_widget_color(self.btn_confirm_tx, BTN_CONFIRM_REV_COLOR)

            if hasattr(self, 'lbl_rev_title'):
                self.lbl_rev_title.text = "Review TX (SIMULATION)" if self.is_simulation else "Review Transaction"
                
        except Exception as e:
            await self.handle_tx_error(e, "prepare swap")
        finally:
            self.set_loading(False)
            if hasattr(self, 'btn_submit'):
                self.btn_submit.enabled = True
                self.btn_submit.text = "SIMULATE" if self.is_simulation else "SUBMIT"

    def toggle_json(self, widget):
        show_raw = widget.value
        if hasattr(self, 'box_tx_json'):
            self.box_tx_json.style.visibility = 'visible' if show_raw else 'hidden'
            self.box_tx_json.style.flex = 1 if show_raw else 0
            self.box_tx_json.style.height = 350 if show_raw else 0
            if hasattr(self, 'lbl_tx_json'):
                self.lbl_tx_json.style.visibility = 'visible' if show_raw else 'hidden'
                self.lbl_tx_json.style.flex = 1 if show_raw else 0
                self.lbl_tx_json.style.height = 350 if show_raw else 0
            
        if hasattr(self, 'box_tx_readable'):
            self.box_tx_readable.style.visibility = 'hidden' if show_raw else 'visible'
            self.box_tx_readable.style.flex = 0 if show_raw else 1
            self.box_tx_readable.style.height = 0 if show_raw else 350
            if hasattr(self, 'lbl_tx_readable'):
                self.lbl_tx_readable.style.visibility = 'hidden' if show_raw else 'visible'
                self.lbl_tx_readable.style.flex = 0 if show_raw else 1
                self.lbl_tx_readable.style.height = 0 if show_raw else 350

    def _get_token_name_and_decimals(self, tid):
        name = ""
        decimals = 0
        info = self.token_cache.get(tid)
        if info:
            name = info.get("name", "")
            decimals = info.get("decimals", 0)
        
        if not name:

            for t_name, t_info in self.trade_mapper.tokens.items():
                if t_info.get("id") == tid:
                    name = t_name
                    decimals = t_info.get("dec", 0)
                    break
        
        if not name:
            name = f"{tid[:8]}..."
        return name, decimals

    def _generate_readable_tx(self, tx_dict):
        lines = []
        
        # --- INPUTS ---
        input_boxes = tx_dict.get("input_boxes", [])
        if input_boxes:
            lines.append("=== INPUTS: WHAT IS BEING SENT ===")
            for i, box in enumerate(input_boxes):
                addr = box.get("address", "???")
                val_erg = box.get("value", 0) / 1e9
                short_addr = f"{addr[:8]}...{addr[-8:]}" if len(addr) > 20 else addr
                
                label = "User Wallet" if addr == self.current_address else "Contract / Pool"
                lines.append(f"  {label} #{i+1}:")
                lines.append(f"    From: {short_addr}")
                out_val = f"{val_erg:,.9f}".rstrip('0').rstrip('.')
                if out_val.endswith('.'): out_val = out_val[:-1]
                lines.append(f"    Value: {out_val} ERG")
                
                assets = box.get("assets", [])
                if assets:
                    lines.append("    Assets:")
                    for asset in assets:
                        tid = asset.get("tokenId", "")
                        amount = asset.get("amount", 0)
                        name, decimals = self._get_token_name_and_decimals(tid)
                        
                        fmt_amt = amount / (10**decimals)
                        display_amt = f"{fmt_amt:,.{min(decimals, 8)}f}".rstrip('0').rstrip('.') if decimals > 0 else f"{amount:,}"
                        if display_amt.endswith('.'): display_amt = display_amt[:-1]
                        lines.append(f"      - {display_amt} {name}")
                lines.append("")
            lines.append("-" * 36)
            lines.append("")

        # --- OUTPUTS ---
        lines.append("=== OUTPUTS: WHAT IS BEING RECEIVED ===")
        requests = tx_dict.get("requests", [])
        for i, req in enumerate(requests):
            addr = req.get("address", "???")
            val_erg = req.get("value", 0) / 1e9
            short_addr = f"{addr[:8]}...{addr[-8:]}" if len(addr) > 20 else addr
            
            label = "To Wallet (Change)" if addr == self.current_address else "To Contract / Pool"
            
            lines.append(f"  {label} #{i+1}:")
            lines.append(f"    Addr: {short_addr}")
            out_val = f"{val_erg:,.9f}".rstrip('0').rstrip('.')
            if out_val.endswith('.'): out_val = out_val[:-1]
            lines.append(f"    Value: {out_val} ERG")
            
            assets = req.get("assets", [])
            if assets:
                lines.append("    Assets:")
                for asset in assets:
                    tid = asset.get("tokenId", "")
                    amount = asset.get("amount", 0)
                    name, decimals = self._get_token_name_and_decimals(tid)
                    
                    fmt_amt = amount / (10**decimals)
                    display_amt = f"{fmt_amt:,.{min(decimals, 8)}f}".rstrip('0').rstrip('.') if decimals > 0 else f"{amount:,}"
                    if display_amt.endswith('.'): display_amt = display_amt[:-1]
                    
                    lines.append(f"      - {display_amt} {name}")
            lines.append("")
        
        return "\n".join(lines).strip()

    def show_password_input(self, widget):
        """Manually show the password field if the user prefers it over biometrics."""
        self.inp_rev_pass.style.visibility = 'visible'
        try: del self.inp_rev_pass.style.height
        except: pass
        self.lbl_rev_bio_hint.style.visibility = 'hidden'
        self.lbl_rev_bio_hint.style.height = 0
        self.btn_rev_show_pass.style.visibility = 'hidden'
        self.btn_rev_show_pass.style.height = 0

    def open_tx_at_sigmaspace(self, widget):
        tx_id = getattr(widget, "_tx_id", None)
        if tx_id:
            url = f"https://sigmaspace.io/en/transaction/{tx_id}"
            webbrowser.open(url)

    async def copy_tx_json(self, widget):
        if hasattr(self, 'lbl_tx_json'):
            text = self.lbl_tx_json.value
            if text:
                if self._set_clipboard_text(text):
                    await self.main_window.dialog(toga.InfoDialog("Copied", "Transaction JSON copied to clipboard."))

    async def execute_swap(self, widget):
        wal_data = self.wallets.get(self.selected_wallet)
        is_ro = wal_data.get("read_only", False) if wal_data else False

        if self.selected_wallet == "ErgoPay" or is_ro:
            asyncio.create_task(self._execute_ergopay_async())
            return

        password = self.inp_rev_pass.value
        
        # Check if biometrics are available for this wallet
        bio_entry = self.biometrics_config.get(self.selected_wallet)
        if bio_entry and not password:
            # Try biometric unlock
            asyncio.create_task(self._execute_swap_with_biometrics(bio_entry))
            return

        if not password:
            print("[DIALOG] Error: Password is required to confirm.", flush=True)
            await self.main_window.dialog(toga.InfoDialog("Error", "Password is required to confirm."))
            return
            
        self.btn_confirm_tx.enabled = False
        asyncio.create_task(self._execute_swap_async(password))

    async def _execute_swap_with_biometrics(self, bio_entry):
        self.set_loading(True)
        try:
            iv = bio_entry['iv']
            encrypted_data = bio_entry['data']
            
            cipher = self.ks_helper.get_decryption_cipher(iv)
            if not cipher: raise ValueError("Could not initialize Keystore cipher.")
            
            success, result = await self.bio_helper.authenticate(
                title="Unlock Wallet",
                subtitle=f"Confirm swap for {self.selected_wallet}",
                cipher=cipher
            )
            
            if success:
                # result is the CryptoObject, result.getCipher() is the unlocked cipher
                unlocked_cipher = result.getCipher()
                import base64
                ciphertext = base64.b64decode(encrypted_data)
                decrypted_password = bytes(unlocked_cipher.doFinal(ciphertext)).decode('utf-8')
                
                # Now proceed with swap using the decrypted password
                await self._execute_swap_async(decrypted_password)
            else:
                print(f"[DIALOG] Biometric Error: {result}", flush=True)
                await self.main_window.dialog(toga.InfoDialog("Biometric Error", str(result)))
                self.set_loading(False)
                self.btn_confirm_tx.enabled = True
        except Exception as e:
            await self.handle_tx_error(e, "biometric unlock")
        finally:
            if 'decrypted_password' in locals(): del decrypted_password
            gc.collect()

    async def _execute_swap_async(self, password):
        self.set_loading(True)
        try:
            encrypted_data = self.wallets.get(self.selected_wallet)
            if not encrypted_data: raise ValueError("Wallet data not found.")
            
            salt = base64.b64decode(encrypted_data['salt'])
            token = base64.b64decode(encrypted_data['token'])
            
            kdf_type = encrypted_data.get("kdf", "pbkdf2")
            if kdf_type == "scrypt":
                kdf = Scrypt(
                    salt=salt,
                    length=32,
                    n=2**15,
                    r=8,
                    p=1,
                    backend=default_backend()
                )
            else:
                kdf = PBKDF2HMAC(
                    algorithm=hashes.SHA256(),
                    length=32,
                    salt=salt,
                    iterations=100000,
                    backend=default_backend()
                )
            
            key = base64.urlsafe_b64encode(kdf.derive(password.encode()))
            f = Fernet(key)
            decrypted_mnemonic = f.decrypt(token).decode()
            
            execute = not self.is_simulation
            use_legacy = encrypted_data.get("use_legacy", False)
            signer = await asyncio.to_thread(ErgoSigner, self.node_url_value)
            
            tx_id, err = await asyncio.to_thread(
                signer.sign_tx_dict,
                self.prepared_tx_dict, decrypted_mnemonic, "", 0, execute, use_legacy
            )
            
            if tx_id:
                dt_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                buy_text = self.lbl_rev_buy.text if hasattr(self, 'lbl_rev_buy') else "Unknown"
                pay_text = self.lbl_rev_pay.text if hasattr(self, 'lbl_rev_pay') else "Unknown"
                
                self.trades.append({
                    "timestamp": dt_str,
                    "buy": buy_text,
                    "pay": pay_text,
                    "tx_id": tx_id,
                    "is_simulation": not execute,
                    "wallet": self.current_address
                })
                self._save_json(self.trades_file, self.trades)
                
                msg = f"✅ Submitted Live!\nTx ID: {tx_id}" if execute else f"🧪 Simulation OK!\nTx ID: {tx_id}\n(No funds were sent)"
                print(f"[DIALOG] Success: {msg}", flush=True)
                self._clear_trade_inputs()
                await self.main_window.dialog(toga.InfoDialog("Success", msg))
                self.navigate_to("main")
            else:
                print(f"[DIALOG] Swap Error: {err}", flush=True)
                await self.main_window.dialog(toga.InfoDialog("Swap Error", str(err)))
                
        except Exception as e:
            await self.handle_tx_error(e, "execute swap")
        finally:
            # Best effort memory wiping
            if 'password' in locals(): del password
            if 'decrypted_mnemonic' in locals(): del decrypted_mnemonic
            if 'key' in locals(): del key
            gc.collect()
            
            self.set_loading(False)
            if hasattr(self, 'btn_confirm_tx'): self.btn_confirm_tx.enabled = True

    async def _execute_ergopay_async(self):
        if self.is_simulation:
            print(f"[DIALOG] Success: Simulation OK! (ErgoPay builds correctly)", flush=True)
            dt_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            buy_text = self.lbl_rev_buy.text if hasattr(self, 'lbl_rev_buy') else "Unknown"
            pay_text = self.lbl_rev_pay.text if hasattr(self, 'lbl_rev_pay') else "Unknown"
            self.trades.append({
                "timestamp": dt_str,
                "buy": buy_text,
                "pay": pay_text,
                "tx_id": "ErgoPay",
                "is_simulation": True,
                "wallet": self.current_address
            })
            self._save_json(self.trades_file, self.trades)

            await self.main_window.dialog(toga.InfoDialog("Success", "Simulation OK!\nThe transaction would have been sent to your wallet for signing."))
            self._clear_trade_inputs()
            self.navigate_to("main")
            return
            
        self.btn_confirm_tx.enabled = False
        self.set_loading(True)
        try:
            signer = await asyncio.to_thread(ErgoSigner, self.node_url_value)
            uri = await asyncio.to_thread(
                signer.reduce_tx_for_ergopay,
                self.prepared_tx_dict, self.current_address, self.use_pre1627_value
            )
            
            # Log ErgoPay transaction (Live)
            dt_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            buy_text = self.lbl_rev_buy.text if hasattr(self, 'lbl_rev_buy') else "Unknown"
            pay_text = self.lbl_rev_pay.text if hasattr(self, 'lbl_rev_pay') else "Unknown"
            self.trades.append({
                "timestamp": dt_str,
                "buy": buy_text,
                "pay": pay_text,
                "tx_id": "ErgoPay",
                "is_simulation": False,
                "wallet": self.current_address
            })
            self._save_json(self.trades_file, self.trades)
            
            # Print full URI to terminal with clear headers
            print("\n" + "="*80, flush=True)
            print("ERGOPAY URI GENERATED:", flush=True)
            print(uri, flush=True)
            print("="*80 + "\n", flush=True)
            
            # Use a modal dialog instead of a new window
            result = await self.main_window.dialog(toga.QuestionDialog(
                "Digital Signature",
                "Your transaction is ready for signature. Would you like to open your wallet now?"
            ))
            
            if result:
                opened = False
                if IS_ANDROID:
                    try:
                        from java import jclass
                        Intent = jclass('android.content.Intent')
                        Uri = jclass('android.net.Uri')
                        MainActivity = jclass('org.beeware.android.MainActivity')
                        activity = MainActivity.singletonThis
                        intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity.startActivity(intent)
                        opened = True
                        print("[piggytrade] Fired Android Intent for ergopay URI.", flush=True)
                    except Exception as ie:
                        print(f"[piggytrade] Android Intent failed: {ie}", flush=True)

                if not opened:
                    import webbrowser
                    success = webbrowser.open(uri)
                    if not success:
                        print("[piggytrade] webbrowser.open failed for ergopay URI.", flush=True)

                if not opened:
                    # Show copy-to-clipboard fallback dialog only if Intent didn't fire
                    try:
                        for obj in [self, getattr(self, 'app', None)]:
                            if obj and hasattr(obj, 'clipboard'):
                                obj.clipboard.text = str(uri)
                                print("[piggytrade] URI copied to clipboard.", flush=True)
                                break
                    except Exception as ce:
                        print(f"[piggytrade] Clipboard error: {ce}", flush=True)

                    await self.main_window.dialog(toga.InfoDialog(
                        "Action Required",
                        "We couldn't open your wallet automatically.\n\n"
                        "The ErgoPay URI has been copied to your clipboard.\n"
                        "Paste it into your wallet's ErgoPay section."
                    ))

                self._clear_trade_inputs()
                self.navigate_to("main")

        except Exception as e:
            await self.handle_tx_error(e, "ErgoPay")
        finally:
            self.set_loading(False)
            if hasattr(self, 'btn_confirm_tx'):
                self.btn_confirm_tx.enabled = True

    # --- Wallet Viewer Logic ---
    def open_wallet_viewer(self, widget):
        if not getattr(self, 'inp_address', None) or not self.current_address: return
        self.navigate_to("wallet_viewer")

    def fetch_wallet_balances(self, silent=False):
        if not silent and hasattr(self, 'box_wallet_info') and self.box_wallet_info is not None:
            self.box_wallet_info.clear()
            self.box_wallet_info.add(toga.Label("Fetching...", style=Pack(color=LBL_WVIEW_FETCH_FONT_COLOR, background_color=LBL_WVIEW_FETCH_COLOR, **self.font_base)))
        asyncio.create_task(self._fetch_wallet_balances_async(silent))

    async def _fetch_wallet_balances_async(self, silent=False):
        self.set_loading(True)
        addr = self.current_address
        

        check_mempool = False
        if hasattr(self, 'sw_wallet') and self.sw_wallet.value:
            check_mempool = True
        elif hasattr(self, 'sw_unconf') and self.sw_unconf.value:
            check_mempool = True

        try:
            client = NodeClient(self.node_url_value)
            my_assets, nanoerg, _ = await asyncio.to_thread(client.get_my_assets, addr, check_mempool=check_mempool)
            
            self.current_balances = {"ERG": nanoerg / 1e9, "tokens": my_assets}
            self.refresh_main_balances()
            
            dirty_cache = False
            for tid, amt in my_assets.items():
                if tid not in self.token_cache:
                    try:
                        t_url = f"{self.node_url_value}/blockchain/token/byId/{tid}"
                        t_res = await asyncio.to_thread(requests.get, t_url)
                        if t_res.status_code == 200:
                            self.token_cache[tid] = t_res.json()
                            dirty_cache = True
                    except: pass
            
            if dirty_cache:
                self._save_json(self.tokens_cache_file, self.token_cache)
            
            if silent or not hasattr(self, 'box_wallet_info') or self.box_wallet_info is None: return
            self.populate_wallet_view_ui()

        except Exception as e:
            if hasattr(self, 'box_wallet_info') and self.box_wallet_info is not None:
                self.box_wallet_info.clear()
                self.box_wallet_info.add(toga.Label(f"Error: {e}", style=Pack(color=LBL_WVIEW_ERR_FONT_COLOR, background_color=LBL_WVIEW_ERR_COLOR, **{**self.font_base})))
        finally:
            self.set_loading(False)

    def set_wallet_view_tab(self, widget):
        tab = getattr(widget, "_tab", "tokens")
        self._wallet_view_tab = tab
        
        # Update button styles for visual feedback
        if hasattr(self, 'btn_tab_tokens') and hasattr(self, 'btn_tab_history'):
            from .theme import COLOR_ACCENT, COLOR_INPUT_BG
            
            # Reset both
            self.btn_tab_tokens.style.background_color = COLOR_INPUT_BG
            self.btn_tab_history.style.background_color = COLOR_INPUT_BG
            
            # Highlight active
            if tab == "tokens":
                self.btn_tab_tokens.style.background_color = COLOR_ACCENT
            else:
                self.btn_tab_history.style.background_color = COLOR_ACCENT
                
        self.populate_wallet_view_ui()

    def populate_wallet_view_ui(self):
        if not hasattr(self, 'box_wallet_info') or self.box_wallet_info is None: return
        self.box_wallet_info.clear()
        if hasattr(self, 'box_wv_headers'):
            self.box_wv_headers.clear()
        
        addr = self.current_address
        if not addr: return
        total_erg = self.current_balances.get("ERG", 0)
        tokens_raw = self.current_balances.get("tokens", {})

        # Update Summary Card if it exists
        if hasattr(self, 'lbl_wv_addr'):
            self.lbl_wv_addr.text = f"{addr[:12]}...{addr[-12:]}"
        if hasattr(self, 'lbl_wv_erg'):
            out_erg = f"{total_erg:,.9f}".rstrip('0').rstrip('.')
            if out_erg.endswith('.'): out_erg = out_erg[:-1]
            self.lbl_wv_erg.text = f"{out_erg} ERG"

        if self._wallet_view_tab == "tokens":
            # --- TOKENS VIEW ---
            # Column Headers (Frozen)
            if hasattr(self, 'box_wv_headers'):
                self.box_wv_headers.add(toga.Label("Asset", style=Pack(color="#FFFFFF", font_weight=FONT_WEIGHT_BOLD, flex=1)))
                self.box_wv_headers.add(toga.Label("Balance", style=Pack(color="#FFFFFF", font_weight=FONT_WEIGHT_BOLD, width=110, text_align=RIGHT)))

            # Sort tokens: SigUSD and USE first, then tokens with logos, then alphabetical
            def sort_key(item):
                tid, _ = item
                info = self.token_cache.get(tid, {})
                name = info.get("name", tid[:8])
                n_up = name.upper()
                if n_up == "SIGUSD": return 0
                if n_up == "USE": return 1
                if self.get_token_icon(name, tid=tid): return 2
                return 3

            token_items = list(tokens_raw.items())
            token_items.sort(key=lambda x: (sort_key(x), (self.token_cache.get(x[0], {}).get("name") or x[0][:8]).lower()))

            if not token_items:
                self.box_wallet_info.add(toga.Label("No tokens found in this wallet.", style=Pack(color="#AAAAAA", margin_top=20, text_align=CENTER)))
            
            for tid, amt in token_items:
                info = self.token_cache.get(tid)
                name = (info.get("name", tid[:8]) if info else tid[:8]) or "Unknown"
                
                # Manual wrapping after 25 characters
                wrapped_name = ""
                for i in range(0, len(name), 25):
                    wrapped_name += name[i:i+25] + ("\n" if i + 25 < len(name) else "")
                
                decimals = info.get("decimals", 0) if info else 0
                fmt_amt = amt / (10 ** decimals)
                
                if decimals > 0:
                    display_amt = f"{fmt_amt:,.{min(decimals, 8)}f}".rstrip('0').rstrip('.')
                    if display_amt.endswith('.'): display_amt = display_amt[:-1]
                else: display_amt = f"{amt:,}"
                
                ico_path = self.get_token_icon(name)
                
                token_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=4, padding=5))
                token_row = apply_android_border(token_row, bg_color="transparent", radius=0, border_width=0)
                
                if ico_path:
                    token_row.add(toga.ImageView(image=ico_path, style=Pack(width=32, height=32, margin_right=12)))
                else:
                    # Placeholder or empty space to keep alignment
                    token_row.add(toga.Box(style=Pack(width=32, height=32, margin_right=12)))
                
                lbl_name = toga.Label(wrapped_name, style=Pack(color="#FFFFFF", font_size=FONT_SIZE_MD, flex=1))
                lbl_amt = toga.Label(display_amt, style=Pack(color="#FFFFFF", font_size=FONT_SIZE_MD, width=110, text_align=RIGHT))
                
                token_row.add(lbl_name)
                token_row.add(lbl_amt)
                self.box_wallet_info.add(token_row)
        
        else:
            # --- HISTORY VIEW ---
            wallet_trades = [tr for tr in reversed(self.trades) if tr.get("wallet") == addr]
            if hasattr(self, 'sw_sim') and not self.sw_sim.value:
                wallet_trades = [tr for tr in wallet_trades if not tr.get("is_simulation", False)]
                
            if not wallet_trades:
                self.box_wallet_info.add(toga.Label("No trade history found.", style=Pack(color="#AAAAAA", margin_top=20, text_align=CENTER)))
            
            for tr in wallet_trades:
                sim = " (sim)" if tr.get("is_simulation") else ""
                method = " (ErgoPay)" if tr.get("tx_id") == "ErgoPay" else ""
                
                if "buy" in tr and "pay" in tr:
                    action = f"Bought {tr['buy']}\nPaid {tr['pay']}"
                else:
                    action = tr.get('action', 'Unknown Trade')

                item_box = toga.Box(style=Pack(direction=COLUMN, margin_bottom=8, padding=10))
                item_box = apply_android_border(item_box, bg_color="transparent", border_color="#535C6E", border_width=0, radius=10)
                
                header = toga.Label(f"{tr.get('timestamp')}{sim}{method}", style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM))
                item_box.add(header)
                
                lbl_action = toga.Label(action, style=Pack(color="#FFFFFF", font_size=FONT_SIZE_MD, margin_top=4))
                item_box.add(lbl_action)
                
                tx_id = tr.get("tx_id", "")
                is_real_tx = tx_id and tx_id not in ["Simulation", "ErgoPay"] and len(tx_id) > 20
                
                if is_real_tx:
                    link_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=8))
                    trunc_tx = f"{tx_id[:8]}...{tx_id[-8:]}" if len(tx_id) > 20 else tx_id
                    lbl_link = toga.Label(f"Tx: {trunc_tx} \u2197", style=Pack(color=COLOR_ACCENT, font_size=FONT_SIZE_SM, flex=1))
                    lbl_link._tx_id = tx_id
                    apply_android_click(lbl_link, self.open_tx_at_sigmaspace)
                    link_row.add(lbl_link)
                    item_box.add(link_row)
                
                self.box_wallet_info.add(item_box)

    async def export_tokens(self, widget):
        from .platform_setup import IS_ANDROID
        try:
            content = self.token_manager.export_json()
            if IS_ANDROID:
                # Fallback for Android: Copy to clipboard instead of file dialog
                if self._set_clipboard_text(content):
                    await self.main_window.dialog(toga.InfoDialog("Exported", "Token list JSON copied to clipboard. You can now paste it into a file or note."))
                else:
                    await self.main_window.dialog(toga.ErrorDialog("Error", "Clipboard not accessible on this platform."))
                return

            # Modern Toga Save File Dialog syntax
            dialog = toga.SaveFileDialog("Export Tokens", suggested_filename="tokens.json", file_types=["json"])
            save_path = await self.main_window.dialog(dialog)
            if save_path:
                with open(save_path, 'w', encoding='utf-8') as f:
                    f.write(content)
                await self.main_window.dialog(toga.InfoDialog("Success", "Tokens exported successfully."))
        except Exception as e:
            await self.main_window.dialog(toga.ErrorDialog("Error", f"Failed to export tokens: {e}"))

    async def import_tokens(self, widget):
        from .platform_setup import IS_ANDROID
        try:
            new_data = None
            if IS_ANDROID:
                content = self._get_clipboard_text()
                
                if not content or not content.strip().startswith("{"):
                    await self.main_window.dialog(toga.InfoDialog("Import Instructions", "To import on Android:\n1. Copy the JSON text of your token list.\n2. Click Import again.\n\n(File picking is currently limited on this platform)."))
                    return
                
                try:
                    new_data = json.loads(content)
                except:
                    await self.main_window.dialog(toga.ErrorDialog("Error", "Clipboard does not contain valid JSON tokens."))
                    return
            else:
                # Modern Toga Open File Dialog syntax
                dialog = toga.OpenFileDialog("Import Tokens", file_types=["json"])
                open_paths = await self.main_window.dialog(dialog)
                if not open_paths:
                    return
                
                with open(open_paths[0], 'r', encoding='utf-8') as f:
                    new_data = json.load(f)
            
            if new_data and not self.token_manager.validate_format(new_data):
                await self.main_window.dialog(toga.ErrorDialog("Error", "Invalid tokens format. Import refused."))
                return
            
            if new_data:
                curr_count = len(self.token_manager.tokens)
                new_count = len(new_data)
                
                msg = (
                    f"New tokens detected!\n\n"
                    f"Current list: {curr_count} tokens\n"
                    f"Imported list: {new_count} tokens\n\n"
                    f"Do you want to replace your local list with the imported one?"
                )
                
                if await self.main_window.dialog(toga.QuestionDialog("Confirm Import", msg)):
                    self.token_manager.tokens = new_data
                    self.token_manager.save()
                    self.trade_mapper = TradeMapper(self.token_manager.tokens)
                    self._all_assets = self.trade_mapper.all_assets()
                    await self.main_window.dialog(toga.InfoDialog("Success", "Tokens imported successfully."))
                    if "token_selector" in self.cached_views:
                        del self.cached_views["token_selector"]
        except Exception as e:
            await self.main_window.dialog(toga.ErrorDialog("Error", f"Failed to import tokens: {e}"))

    async def check_token_updates(self, widget):
        self.set_loading(True)
        try:
            new_tokens, error_msg = await self.token_manager.fetch_remote(TOKENS_URL)
            if error_msg:
                await self.main_window.dialog(toga.ErrorDialog("Update Failed", f"Could not fetch token list:\n{error_msg}"))
                return
            
            # Simple content comparison
            if json.dumps(new_tokens, sort_keys=True) == json.dumps(self.token_manager.tokens, sort_keys=True):
                await self.main_window.dialog(toga.InfoDialog("Up to Date", "Your token list is already identical to the version on GitHub."))
                return
            
            curr_count = len(self.token_manager.tokens)
            new_count = len(new_tokens)
            
            msg = (
                f"A new token list is available!\n\n"
                f"Current list: {curr_count} tokens\n"
                f"New list: {new_count} tokens\n\n"
                f"Do you want to overwrite your local list with the new version?"
            )
            
            if await self.main_window.dialog(toga.QuestionDialog("Update Available", msg)):
                self.token_manager.tokens = new_tokens
                self.token_manager.save()
                self.trade_mapper = TradeMapper(self.token_manager.tokens)
                self._all_assets = self.trade_mapper.all_assets()
                await self.main_window.dialog(toga.InfoDialog("Success", "Tokens updated successfully."))
                if "token_selector" in self.cached_views:
                    del self.cached_views["token_selector"]
        except Exception as e:
            await self.main_window.dialog(toga.ErrorDialog("Error", f"Failed to check for updates: {e}"))
        finally:
            self.set_loading(False)

    async def import_all_trading_pairs(self, widget):
        # Warning Dialog
        warning_msg = (
            "WARNING! This will import a large list of token pairs, "
            "including low liquidity and potentially scam coins. "
            "Only import if you know what you are doing.\n\n"
            "Do you want to continue?"
        )
        if not await self.main_window.dialog(toga.QuestionDialog("WARNING!", warning_msg)):
            return

        self.set_loading(True)
        try:
            url = "https://raw.githubusercontent.com/FlyingPig5/piggytrade/refs/heads/main/tokens_all.json"
            new_tokens, error_msg = await self.token_manager.fetch_remote(url)
            
            if error_msg:
                await self.main_window.dialog(toga.ErrorDialog("Import Failed", f"Could not fetch token list:\n{error_msg}"))
                return
            
            # Simple content comparison
            if json.dumps(new_tokens, sort_keys=True) == json.dumps(self.token_manager.tokens, sort_keys=True):
                await self.main_window.dialog(toga.InfoDialog("Up to Date", "Your token list is already identical to the 'All' list on GitHub."))
                return
            
            curr_count = len(self.token_manager.tokens)
            new_count = len(new_tokens)
            
            msg = (
                f"A new token list is available!\n\n"
                f"Current list: {curr_count} token pairs\n"
                f"New list: {new_count} token pairs\n\n"
                f"Do you want to overwrite existing tokens.json?"
            )
            
            if await self.main_window.dialog(toga.QuestionDialog("New File Available", msg)):
                self.token_manager.tokens = new_tokens
                self.token_manager.save()
                self.trade_mapper = TradeMapper(self.token_manager.tokens)
                self._all_assets = self.trade_mapper.all_assets()
                await self.main_window.dialog(toga.InfoDialog("Success", "Tokens imported on the device as tokens.json and is the new token list."))
                if "token_selector" in self.cached_views:
                    del self.cached_views["token_selector"]
        except Exception as e:
            await self.main_window.dialog(toga.ErrorDialog("Error", f"Failed to import tokens: {e}"))
        finally:
            self.set_loading(False)


    def open_github(self, widget):
        print("[piggytrade] GitHub click!", flush=True)
        self.open_url("https://github.com/FlyingPig69/piggytrade")

    def open_url(self, url):
        """Generic helper to open URLs securely and reliably across platforms."""
        from .platform_setup import IS_ANDROID
        if IS_ANDROID:
            try:
                from java import jclass
                Intent = jclass('android.content.Intent')
                Uri = jclass('android.net.Uri')
                MainActivity = jclass('org.beeware.android.MainActivity')
                activity = MainActivity.singletonThis
                if activity:
                    intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    print(f"[piggytrade] Opened URL via Intent: {url}", flush=True)
                    return True
            except Exception as e:
                print(f"[piggytrade] Intent open_url failed for {url}: {e}", flush=True)

        import webbrowser
        success = webbrowser.open(url)
        print(f"[piggytrade] Opened URL via webbrowser: {url} | success={success}", flush=True)
        return success


def main():
    return PiggyTrade("PiggyTrade", "com.piggytrade.piggytrade")

if __name__ == '__main__':
    main().main_loop()