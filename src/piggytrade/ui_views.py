# ui_views.py
import toga
from toga.style import Pack
from toga.style.pack import COLUMN, ROW, CENTER, RIGHT, START

from .platform_setup import IS_ANDROID
from .theme import (
    COLOR_BG, COLOR_CARD, COLOR_INPUT_BG, COLOR_SELECTION_BG, COLOR_INPUT_TEXT, COLOR_INPUT_HINT, COLOR_CHIP, COLOR_QUICK,
    COLOR_TEXT, COLOR_ACCENT, COLOR_DANGER, COLOR_BLUE, COLOR_TEXT_DIM,
    FONT_SIZE_SM, FONT_SIZE_BASE, FONT_SIZE_MD, FONT_SIZE_LG, FONT_SIZE_XL, FONT_SIZE_ICON,
    FONT_WEIGHT_BOLD, LABEL_WIDTH, ICON_COG, ICON_WALLET, ICON_PLUS,
    ICON_TRASH, ICON_SWAP_VERT, ICON_BACK, ICON_PASTE, FONT_FAMILY_ICONS,
    LBL_TITLE_MAIN_COLOR, LBL_TITLE_MAIN_FONT_COLOR, LBL_WALLET_SECTION_COLOR, LBL_WALLET_SECTION_FONT_COLOR, LBL_SWAP_SECTION_COLOR, LBL_SWAP_SECTION_FONT_COLOR, LBL_EDIT_FAVS_COLOR, LBL_EDIT_FAVS_FONT_COLOR, LBL_FROM_ASSET_COLOR, LBL_FROM_ASSET_FONT_COLOR, LBL_ROUTE_COLOR, LBL_ROUTE_FONT_COLOR, LBL_TO_ASSET_COLOR, LBL_TO_ASSET_FONT_COLOR, LBL_QUOTE_COLOR, LBL_QUOTE_FONT_COLOR, LBL_SEL_WALLET_COLOR, LBL_SEL_WALLET_FONT_COLOR, LBL_LP_COLOR, LBL_LP_FONT_COLOR, LBL_FEE_COLOR, LBL_FEE_FONT_COLOR, LBL_FEE_VAL_COLOR, LBL_FEE_VAL_FONT_COLOR, LBL_TITLE_SET_COLOR, LBL_TITLE_SET_FONT_COLOR, LBL_CREDITS_COLOR, LBL_CREDITS_FONT_COLOR, LBL_NODE_SET_COLOR, LBL_NODE_SET_FONT_COLOR, LBL_TITLE_ADD_NODE_COLOR, LBL_TITLE_ADD_NODE_FONT_COLOR, LBL_TITLE_ADD_WALLET_COLOR, LBL_TITLE_ADD_WALLET_FONT_COLOR, LBL_MNEM_COLOR, LBL_MNEM_FONT_COLOR, LBL_RO_COLOR, LBL_RO_FONT_COLOR, LBL_LEGACY_COLOR, LBL_LEGACY_FONT_COLOR, LBL_W_ADDR_COLOR, LBL_W_ADDR_FONT_COLOR, LBL_TITLE_TOKEN_COLOR, LBL_TITLE_TOKEN_FONT_COLOR, LBL_TITLE_REV_COLOR, LBL_TITLE_REV_FONT_COLOR, LBL_RAW_JSON_COLOR, LBL_RAW_JSON_FONT_COLOR, LBL_REV_SUM_COLOR, LBL_REV_SUM_FONT_COLOR, LBL_TITLE_WVIEW_COLOR, LBL_TITLE_WVIEW_FONT_COLOR, LBL_WVIEW_UNCONF_COLOR, LBL_WVIEW_UNCONF_FONT_COLOR, LBL_WVIEW_SIM_COLOR, LBL_WVIEW_SIM_FONT_COLOR, LBL_WVIEW_FETCH_COLOR, LBL_WVIEW_FETCH_FONT_COLOR, LBL_WVIEW_ADDR_COLOR, LBL_WVIEW_ADDR_FONT_COLOR, LBL_WVIEW_ERG_COLOR, LBL_WVIEW_ERG_FONT_COLOR, LBL_WVIEW_TOKEN_COLOR, LBL_WVIEW_TOKEN_FONT_COLOR, LBL_WVIEW_HIST_COLOR, LBL_WVIEW_HIST_FONT_COLOR, LBL_WVIEW_ITM_COLOR, LBL_WVIEW_ITM_FONT_COLOR, LBL_WVIEW_ERR_COLOR, LBL_WVIEW_ERR_FONT_COLOR, BTN_FAV_COLOR, BTN_FAV_FONT_COLOR, BTN_TOKEN_SEL_COLOR, BTN_TOKEN_SEL_FONT_COLOR, BTN_SETTINGS_COLOR, BTN_SETTINGS_FONT_COLOR, BTN_ADD_W_COLOR, BTN_ADD_W_FONT_COLOR, BTN_DEL_W_COLOR, BTN_DEL_W_FONT_COLOR, BTN_PASTE_W_COLOR, BTN_PASTE_W_FONT_COLOR, BTN_VIEW_W_COLOR, BTN_VIEW_W_FONT_COLOR, BTN_FROM_COLOR, BTN_FROM_FONT_COLOR, BTN_SWAP_COLOR, BTN_SWAP_FONT_COLOR, BTN_TO_COLOR, BTN_TO_FONT_COLOR, BTN_SAFE_COLOR, BTN_SAFE_FONT_COLOR, BTN_LIVE_COLOR, BTN_LIVE_FONT_COLOR, BTN_SUBMIT_COLOR, BTN_SUBMIT_FONT_COLOR, BTN_BACK_SET_COLOR, BTN_BACK_SET_FONT_COLOR, BTN_ADD_N_COLOR, BTN_ADD_N_FONT_COLOR, BTN_DEL_N_COLOR, BTN_DEL_N_FONT_COLOR, BTN_IMPORT_COLOR, BTN_EXPORT_COLOR, BTN_IMPORT_ALL_COLOR, BTN_BACK_ADD_N_COLOR, BTN_BACK_ADD_N_FONT_COLOR, BTN_SAVE_N_COLOR, BTN_SAVE_N_FONT_COLOR, BTN_BACK_ADD_W_COLOR, BTN_BACK_ADD_W_FONT_COLOR, BTN_PASTE_ADDR_COLOR, BTN_PASTE_ADDR_FONT_COLOR, BTN_SAVE_W_COLOR, BTN_SAVE_W_FONT_COLOR, BTN_BACK_TOK_COLOR, BTN_BACK_TOK_FONT_COLOR, BTN_CANCEL_REV_COLOR, BTN_CANCEL_REV_FONT_COLOR, BTN_CONFIRM_REV_COLOR, BTN_CONFIRM_REV_FONT_COLOR, BTN_DEL_WVIEW_COLOR, BTN_DEL_WVIEW_FONT_COLOR, BTN_CLOSE_WVIEW_COLOR, BTN_CLOSE_WVIEW_FONT_COLOR)

def apply_android_border(widget, radius=15, border_width=1, bg_color=None, border_color="#21262D", hint_color=None, v_padding=10, h_padding=25, is_numeric=False, text_color=None):
    if not IS_ANDROID: return widget
    if bg_color is None:
        try: bg_color = COLOR_INPUT_BG
        except: bg_color = "#30363D"
    
    # Save the kwargs to re-apply later if Toga wipes the style
    widget._toga_border_kwargs = dict(radius=radius, border_width=border_width, bg_color=bg_color, border_color=border_color, hint_color=hint_color, v_padding=v_padding, h_padding=h_padding)
    
    try:
        from java import jclass
        # Cache class lookups for performance during animations
        if not hasattr(apply_android_border, '_GradientDrawable'):
            apply_android_border._GradientDrawable = jclass("android.graphics.drawable.GradientDrawable")
            apply_android_border._Color = jclass("android.graphics.Color")
            apply_android_border._InputType = jclass("android.text.InputType")
        
        GradientDrawable = apply_android_border._GradientDrawable
        Color = apply_android_border._Color
        
        # Determine background color
        if bg_color and bg_color.lower() == "transparent":
            android_bg_color = Color.TRANSPARENT
        elif bg_color:
            android_bg_color = Color.parseColor(bg_color)
        else:
            android_bg_color = Color.TRANSPARENT

        drawable = GradientDrawable()
        drawable.setShape(GradientDrawable.RECTANGLE)
        drawable.setCornerRadius(float(radius))
        
        if border_color and border_color.lower() == "transparent":
            android_stroke_color = Color.TRANSPARENT
        else:
            android_stroke_color = Color.parseColor(border_color)
            
        drawable.setStroke(border_width, android_stroke_color)
        drawable.setColor(android_bg_color)
        
        native = widget._impl.native
        native.setBackground(drawable)
        native.setPadding(h_padding, v_padding, h_padding, v_padding)

        if is_numeric:
            InputType = apply_android_border._InputType
            native.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL)

        # Apply text/hint colors efficiently
        if text_color:
            native.setTextColor(Color.parseColor(text_color))
        
        hint_c = hint_color or COLOR_INPUT_HINT
        try: native.setHintTextColor(Color.parseColor(hint_c))
        except: pass
            
    except Exception as e:
        print(f"[piggytrade] Border apply failed: {e}", flush=True)
    return widget

def fix_android_slider(widget):
    """Fix for Toga Slider inside ScrollContainer on Android."""
    if not IS_ANDROID: return widget
    try:
        from java import jclass, dynamic_proxy
        View = jclass("android.view.View")
        MotionEvent = jclass("android.view.MotionEvent")
        
        class SliderTouchListener(dynamic_proxy(View.OnTouchListener)):
            def onTouch(self, v, event):
                action = event.getAction()
                if action == MotionEvent.ACTION_DOWN:
                    # Tell parents (ScrollContainer) NOT to steal this gesture
                    v.getParent().requestDisallowInterceptTouchEvent(True)
                elif action in [MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL]:
                    v.getParent().requestDisallowInterceptTouchEvent(False)
                return False # Return False so the SeekBar still handles the event
        
        widget._impl.native.setOnTouchListener(SliderTouchListener())
    except Exception as e:
        print(f"[piggytrade] Slider fix failed: {e}", flush=True)
    return widget

def fix_android_scroll(widget):
    """Fix for nested scrollable widgets (like MultilineTextInput) on Android."""
    if not IS_ANDROID: return widget
    try:
        from java import jclass, dynamic_proxy
        View = jclass("android.view.View")
        
        class ScrollTouchListener(dynamic_proxy(View.OnTouchListener)):
            def onTouch(self, v, event):
                # When touching this widget, tell parents NOT to intercept
                v.getParent().requestDisallowInterceptTouchEvent(True)
                return False
        
        widget._impl.native.setOnTouchListener(ScrollTouchListener())
    except Exception as e:
        print(f"[piggytrade] Scroll fix failed: {e}", flush=True)
    return widget

def apply_android_click(widget, handler):
    """Add a click listener to any Android native view (for Box-as-button)."""
    if not IS_ANDROID: return widget
    try:
        from java import jclass, dynamic_proxy
        import asyncio
        View = jclass("android.view.View")
        class ClickListener(dynamic_proxy(View.OnClickListener)):
            def onClick(self, v):
                if asyncio.iscoroutine(handler) or asyncio.iscoroutinefunction(handler):
                    asyncio.create_task(handler(widget))
                else:
                    handler(widget)
        native = widget._impl.native
        native.setOnClickListener(ClickListener())
        native.setClickable(True)
    except Exception as e:
        print(f"[piggytrade] Click failed: {e}", flush=True)
    return widget

def build_main_view(app):
    box = toga.Box(style=Pack(direction=COLUMN, margin=10, padding_bottom=40, background_color=COLOR_BG))
    
    # Header Bar - Absolute Centering
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=5, margin_bottom=10))
    
    # Left and Right spacers with flex=1 ensure the title is perfectly centered
    left_side = toga.Box(style=Pack(flex=1))
    
    title_box = toga.Box(style=Pack(direction=ROW, align_items=CENTER))
    title_box.add(toga.Label("Piggy", style=Pack(color=LBL_TITLE_MAIN_FONT_COLOR, background_color="transparent", **app.font_title)))
    
    # Load and scale the round logo
    logo_path = app.paths.app / "resources" / "piggytrade.png"
    logo_img = toga.Image(logo_path)
    logo_view = toga.ImageView(image=logo_img, style=Pack(width=32, height=32, margin_left=2, margin_right=2))
    title_box.add(logo_view)
    
    title_box.add(toga.Label("Trade", style=Pack(color=LBL_TITLE_MAIN_FONT_COLOR, background_color="transparent", **app.font_title)))
    
    right_side = toga.Box(style=Pack(direction=ROW, align_items=CENTER, flex=1, justify_content="end"))
    app.ai_main = toga.ActivityIndicator(style=Pack(width=25, height=25, margin_right=5))
    btn_settings = apply_android_border(toga.Button(ICON_COG, style=Pack(color=BTN_SETTINGS_FONT_COLOR, background_color=BTN_SETTINGS_COLOR, width=40, height=40, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=lambda w: app.navigate_to("settings")), bg_color=BTN_SETTINGS_COLOR, v_padding=2, h_padding=2, radius=10)
    
    right_side.add(app.ai_main)
    right_side.add(btn_settings)
    
    header.add(left_side)
    header.add(title_box)
    header.add(right_side)
    box.add(header)

    # Reverted to original background color
    wallet_card = toga.Box(style=Pack(direction=COLUMN, background_color=COLOR_CARD, margin=10, margin_bottom=2))
    wallet_card = apply_android_border(wallet_card, bg_color=COLOR_CARD, border_width=0, radius=15, v_padding=15)
    # Reverted Wallet Section Title background to transparent
    wallet_title_row = toga.Box(style=Pack(direction=ROW, margin_bottom=5, background_color="transparent"))
    wallet_title_row.add(toga.Label("WALLET", style=Pack(color=LBL_WALLET_SECTION_FONT_COLOR, background_color="transparent", **app.font_bold, flex=1, text_align=CENTER)))
    wallet_card.add(wallet_title_row)
    
    # Aligned with Interactive Rows (20px left margin)
    w_row1 = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=5, margin_left=20, justify_content=START))
    
    # Custom Wallet Selector Box
    display_wallet = app.selected_wallet if app.selected_wallet else "Select Wallet"
    if app.selected_wallet in app.wallets:
        v = app.wallets[app.selected_wallet]
        if v.get("read_only"):
            display_wallet = f"{app.selected_wallet} (ergopay)"
        else:
            display_wallet = app.selected_wallet
        
    app.btn_wallet_sel = toga.Box(style=Pack(direction=ROW, align_items=CENTER, width=290, height=50, background_color=COLOR_INPUT_BG))
    app.btn_wallet_sel = apply_android_border(app.btn_wallet_sel, bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=12, h_padding=25, v_padding=5)
    
    app.lbl_selected_wallet = toga.Label(display_wallet, style=Pack(color="#FFFFFF", font_size=FONT_SIZE_MD, flex=1, background_color="transparent", margin_left=10))
    app.btn_wallet_sel.add(app.lbl_selected_wallet)
    
    # Selection Arrow Icon (using Cog or similar if no arrow, but we can just use text for now or a small icon)
    # For now, just the label is fine, matches the premium feel of asset selectors
    
    apply_android_click(app.btn_wallet_sel, lambda w: app.open_wallet_selector())
    
    # Symmetric padding and dark navy background
    btn_add_w = apply_android_border(
        toga.Button(ICON_PLUS, style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, width=50, height=50, margin_left=8, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_ICON), on_press=lambda w: app.navigate_to("add_wallet")),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=10, v_padding=0, h_padding=0, text_color="#FFFFFF"
    )
    w_row1.add(app.btn_wallet_sel); w_row1.add(btn_add_w)
    
    # Aligned with interactive rows
    w_row2 = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=10, margin_left=20, justify_content=START))
    # Widen address field to 290px - Match dark navy button style
    app.inp_address = apply_android_border(
        toga.TextInput(placeholder="Enter wallet Address", readonly=True, style=Pack(width=290, height=50, font_size=FONT_SIZE_BASE, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.on_address_input_change),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", v_padding=0, radius=10
    )
    btn_view_w = apply_android_border(
        toga.Button(ICON_WALLET, style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, width=50, height=50, margin_left=8, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_ICON), on_press=app.open_wallet_viewer),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=10, v_padding=0, h_padding=0, text_color="#FFFFFF"
    )
    w_row2.add(app.inp_address); w_row2.add(btn_view_w)
    
    wallet_card.add(w_row1); wallet_card.add(w_row2)
    box.add(wallet_card)

    # Trade Card - Reverted to original background
    trade_card = toga.Box(style=Pack(direction=COLUMN, background_color=COLOR_CARD, margin=10, margin_top=2, padding_top=20))
    
    # Favorites Grid
    app.fav_box = toga.Box(style=Pack(direction=COLUMN, margin_top=10, margin_bottom=5, align_items=CENTER))
    app.refresh_favorites_ui()
    trade_card.add(app.fav_box)

    # Edit Favorites Control (now below favorites)
    edit_favs_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=5, padding_left=22))
    edit_favs_row.add(toga.Label("Edit Favorites", style=Pack(color=LBL_EDIT_FAVS_FONT_COLOR, background_color=LBL_EDIT_FAVS_COLOR, **app.font_base)))
    app.switch_edit_favs = toga.Switch("", value=app.edit_favs_mode, on_change=app.toggle_edit_favs)
    edit_favs_row.add(app.switch_edit_favs)
    trade_card.add(edit_favs_row)

    font_sel = {"font_size": FONT_SIZE_MD}

    # FROM CARD - Aligned (20px left margin)
    from_card = toga.Box(style=Pack(direction=COLUMN, margin_bottom=0, margin_left=20, margin_right=10))
    from_card = apply_android_border(from_card, bg_color=COLOR_CARD, border_color="transparent", radius=15, v_padding=0)
    
    # Merged From Box (Amount + Asset selector) - Width 348 to match wallet row (290+50+8)
    app.from_top_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, width=348, height=65, background_color=COLOR_INPUT_BG))
    app.from_top_row = apply_android_border(app.from_top_row, bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=10)
    
    app.inp_amount = apply_android_border(toga.TextInput(value=app.last_amount, placeholder="0.0", style=Pack(flex=1, font_size=FONT_SIZE_XL, color=COLOR_INPUT_TEXT, background_color="transparent"), on_change=app.on_amount_change), is_numeric=True, bg_color="transparent", border_width=0, h_padding=15, v_padding=5, text_color="#FFFFFF", radius=10)
    
    # Custom Button (Box with Icon + Text) - Inside the merged box
    btn_from_val = app.from_asset if app.from_asset else "TOKEN"
    if len(btn_from_val) > 6: btn_from_val = btn_from_val[:6] + ".."
    from_ico = app.get_token_icon(app.from_asset) if app.from_asset else None
    # Moved box left by adding margin_right
    app.btn_from = toga.Box(style=Pack(direction=ROW, align_items=CENTER, width=130, height=65, justify_content=CENTER, background_color="transparent", margin_right=10))
    app.img_from = toga.ImageView(image=from_ico, style=Pack(width=32, height=32, margin_left=10, margin_right=5))
    app.lbl_from_token = toga.Label(btn_from_val, style=Pack(color=BTN_FROM_FONT_COLOR, font_size=FONT_SIZE_MD, flex=1, text_align=CENTER, background_color="transparent"))
    app.btn_from.add(app.img_from)
    app.btn_from.add(app.lbl_from_token)
    
    apply_android_click(app.btn_from, lambda w: app.open_selector("from"))

    app.from_top_row.add(app.inp_amount)
    app.from_top_row.add(app.btn_from)
    from_bot_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, padding_left=25, padding_right=25, margin_bottom=10))
    max_visible = 'hidden' if app.from_asset and app.from_asset.upper() == "ERG" else 'visible'
    app.btn_max = apply_android_border(
        toga.Button("MAX", style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, font_size=FONT_SIZE_SM, width=35, height=25, font_weight=FONT_WEIGHT_BOLD, visibility=max_visible), on_press=app.on_max_click),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=10, v_padding=0, h_padding=0
    )
    app.lbl_from_balance = toga.Label("", style=Pack(color="#AAAAAA", background_color="transparent", font_size=FONT_SIZE_SM, flex=1, text_align=RIGHT, padding_right=10))
    from_bot_row.add(app.lbl_from_balance)
    from_bot_row.add(app.btn_max)
    from_card.add(from_bot_row)
    from_card.add(app.from_top_row)

    # MID ROW (Swap & Route)
    mid_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=0, padding_left=10, padding_right=10))
    app.lbl_route = toga.Label("", style=Pack(color=LBL_ROUTE_FONT_COLOR, background_color="transparent", **{**app.font_base, "font_size": FONT_SIZE_SM, "flex": 1}))
    btn_swap_box = toga.Box(style=Pack(direction=ROW, flex=1, align_items=CENTER))
    btn_swap = apply_android_border(
        toga.Button(ICON_SWAP_VERT, style=Pack(color="#FFFFFF", background_color="transparent", width=42, height=42, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_XL), on_press=app.on_swap_direction),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=21, v_padding=0, h_padding=0, text_color="#FFFFFF"
    )
    right_spacer = toga.Box(style=Pack(flex=1))
    btn_swap_box.add(app.lbl_route)
    btn_swap_box.add(btn_swap)
    btn_swap_box.add(right_spacer)
    mid_row.add(btn_swap_box)

    # TO CARD - Aligned (20px left margin)
    to_card = toga.Box(style=Pack(direction=COLUMN, margin_bottom=10, margin_left=20, margin_right=10))
    to_card = apply_android_border(to_card, bg_color=COLOR_CARD, border_color="transparent", radius=15, v_padding=0)
    
    # Merged To Box (Quote + Asset selector) - Width 348 to match wallet row (290+50+8)
    app.to_top_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, width=348, height=65, background_color=COLOR_INPUT_BG))
    app.to_top_row = apply_android_border(app.to_top_row, bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=12)
    
    app.lbl_quote = toga.Label("0.0", style=Pack(color=LBL_QUOTE_FONT_COLOR, background_color="transparent", font_size=FONT_SIZE_XL, flex=1, padding_left=15))
    
    # Custom Button (Box with Icon + Text) - Inside the merged box
    btn_to_val = app.to_asset if app.to_asset else "TOKEN"
    if len(btn_to_val) > 6: btn_to_val = btn_to_val[:6] + ".."
    to_ico = app.get_token_icon(app.to_asset) if app.to_asset else None
    # Moved box left by adding margin_right
    app.btn_to = toga.Box(style=Pack(direction=ROW, align_items=CENTER, width=130, height=65, justify_content=CENTER, background_color="transparent", margin_right=10))
    app.img_to = toga.ImageView(image=to_ico, style=Pack(width=32, height=32, margin_left=10, margin_right=5))
    app.lbl_to_token = toga.Label(btn_to_val, style=Pack(color=BTN_TO_FONT_COLOR, font_size=FONT_SIZE_MD, flex=1, text_align=CENTER, background_color="transparent"))
    app.btn_to.add(app.img_to)
    app.btn_to.add(app.lbl_to_token)
    
    apply_android_click(app.btn_to, lambda w: app.open_selector("to"))
    
    app.to_top_row.add(app.lbl_quote)
    app.to_top_row.add(app.btn_to)
    to_card.add(app.to_top_row)
    
    to_bot_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, padding_left=20, padding_right=20))
    app.lbl_impact = toga.Label("", style=Pack(color=COLOR_TEXT_DIM, background_color="transparent", font_size=FONT_SIZE_SM, flex=1))
    app.lbl_to_balance = toga.Label("", style=Pack(color="#AAAAAA", background_color="transparent", font_size=FONT_SIZE_SM, flex=1, text_align=RIGHT, padding_bottom=5))
    to_bot_row.add(app.lbl_impact)
    to_bot_row.add(app.lbl_to_balance)
    to_card.add(to_bot_row)

    trade_card.add(from_card)
    trade_card.add(mid_row)
    trade_card.add(to_card)
    debug_mode = app.app_settings.get("debug_mode", False)

    # Options
    if debug_mode:
        opt_row = toga.Box(style=Pack(direction=ROW, margin_bottom=10))
        opt_row.add(toga.Label("Wallet", style=Pack(color=LBL_SEL_WALLET_FONT_COLOR, background_color=LBL_SEL_WALLET_COLOR, **app.font_base)))
        app.sw_wallet = toga.Switch("", value=True, style=Pack(margin_right=10), on_change=lambda w: app.fetch_wallet_balances(silent=True))
        opt_row.add(app.sw_wallet)
        opt_row.add(toga.Label("LP", style=Pack(color=LBL_LP_FONT_COLOR, background_color=LBL_LP_COLOR, **app.font_base)))
        app.sw_lp = toga.Switch("", value=True, on_change=lambda w: app.on_amount_change(None))
        opt_row.add(app.sw_lp)
        trade_card.add(opt_row)
    else:
        # Default states if hidden
        app.sw_wallet = toga.Switch("", value=True, on_change=lambda w: app.fetch_wallet_balances(silent=True))
        app.sw_lp = toga.Switch("", value=True, on_change=lambda w: app.on_amount_change(None))
    
    # Fee Slider
    fee_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=10))
    fee_row.add(toga.Label("Miner Fee:", style=Pack(color=LBL_FEE_FONT_COLOR, background_color=LBL_FEE_COLOR, **app.font_base, padding_left=10)))
    app.sld_fee = fix_android_slider(toga.Slider(min=0.001, max=0.5, value=0.001, style=Pack(flex=1, margin_left=5, margin_right=5), on_change=app.on_fee_change))
    app.lbl_fee = toga.Label("0.001 ERG", style=Pack(color=LBL_FEE_VAL_FONT_COLOR, background_color=LBL_FEE_VAL_COLOR, **{**app.font_base, "width": 80}))
    fee_row.add(app.sld_fee); fee_row.add(app.lbl_fee)
    trade_card.add(fee_row)

    # Execution
    if debug_mode:
        exec_row = toga.Box(style=Pack(direction=ROW, margin_bottom=10))
        btn_safe_bg = COLOR_ACCENT if app.is_simulation else COLOR_CHIP
        btn_live_bg = COLOR_CHIP if app.is_simulation else COLOR_DANGER
        
        app.btn_safe = apply_android_border(toga.Button("Check TX", style=Pack(color=COLOR_INPUT_TEXT, background_color=btn_safe_bg, flex=1), on_press=lambda w: app.set_exec_mode(True)), bg_color=btn_safe_bg, radius=10)
        app.btn_live = apply_android_border(toga.Button("LIVE", style=Pack(color=COLOR_INPUT_TEXT, background_color=btn_live_bg, flex=1, margin_left=5), on_press=lambda w: app.set_exec_mode(False)), bg_color=btn_live_bg, radius=10)
        exec_row.add(app.btn_safe); exec_row.add(app.btn_live)
        trade_card.add(exec_row)

    btn_sub_bg = "#00D18B"
    app.btn_submit = apply_android_border(
        toga.Button("SIMULATE" if app.is_simulation else "SUBMIT", style=Pack(color=COLOR_INPUT_TEXT, background_color=btn_sub_bg, font_weight=FONT_WEIGHT_BOLD, margin=10, margin_bottom=20, font_size=FONT_SIZE_XL), on_press=app.prepare_swap),
        bg_color=btn_sub_bg, radius=12
    )
    trade_card.add(app.btn_submit)

    trade_card = apply_android_border(trade_card, bg_color=COLOR_CARD, border_width=0, radius=15, v_padding=15)

    box.add(trade_card)
    
    if app.selected_wallet:
        app.initialize_wallet_session(app.selected_wallet, display_wallet)
    return toga.ScrollContainer(content=box, horizontal=False, style=Pack(flex=1))

def build_settings_view(app):
    box = toga.Box(style=Pack(direction=COLUMN, flex=1, margin=5, background_color=COLOR_BG))
    
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin=10, margin_bottom=5))
    btn_back = apply_android_border(toga.Button(ICON_BACK, style=Pack(color=BTN_BACK_SET_FONT_COLOR, background_color=BTN_BACK_SET_COLOR, width=36, height=36, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=lambda w: app.navigate_to("main")), bg_color=BTN_BACK_SET_COLOR, v_padding=2, h_padding=2, radius=10)
    title = toga.Label("Settings", style=Pack(color=LBL_TITLE_SET_FONT_COLOR, background_color=LBL_TITLE_SET_COLOR, **app.font_title, flex=1, margin_left=10))
    header.add(btn_back); header.add(title)
    box.add(header)
    
    # Everything in one big card container - wider and taller with more internal space
    main_card = toga.Box(style=Pack(direction=COLUMN, flex=1, background_color=COLOR_CARD, margin_left=10, margin_right=10, margin_top=0, margin_bottom=10, padding_top=40, padding_bottom=20, padding_left=20, padding_right=20))
    # Using h_padding=30 and v_padding=20 in apply_android_border to ensure native padding is applied
    main_card = apply_android_border(main_card, bg_color=COLOR_CARD, border_width=0, radius=30, h_padding=30, v_padding=20)

    # Logo & Credits
    logo_section = toga.Box(style=Pack(direction=COLUMN, align_items=CENTER, margin_bottom=15))
    logo_path = app.paths.app / "resources" / "piggytrade.png"
    if logo_path.exists():
        logo_img = toga.Image(logo_path)
        logo_section.add(toga.ImageView(logo_img, style=Pack(width=100, height=100, margin_bottom=10)))
    logo_section.add(toga.Label("Ergo trading shouldn't be a desk job.\nSwap on the go!", style=Pack(color=LBL_CREDITS_FONT_COLOR, background_color="transparent", **{**app.font_base, "text_align": CENTER})))
    
    # GitHub Link (Transparent)
    github_path = app.paths.app / "resources" / "github.png"
    if github_path.exists():
        gh_img = toga.ImageView(toga.Image(github_path), style=Pack(width=24, height=24, margin_right=10))
        gh_lbl = toga.Label("This app is open source!", style=Pack(color="#FFFFFF", font_size=FONT_SIZE_MD, background_color="transparent"))
        gh_btn_inner = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=10, background_color="transparent"))
        gh_btn_inner.add(gh_img); gh_btn_inner.add(gh_lbl)
        
        gh_btn = apply_android_border(gh_btn_inner, bg_color="transparent", border_width=0, radius=0, h_padding=15, v_padding=5)
        apply_android_click(gh_btn, app.open_github)
        apply_android_click(gh_img, app.open_github)
        apply_android_click(gh_lbl, app.open_github)
        logo_section.add(gh_btn)
    main_card.add(logo_section)
    
    # Node Section
    main_card.add(toga.Label("NODE", style=Pack(color=LBL_NODE_SET_FONT_COLOR, background_color="transparent", **app.font_bold, margin_top=15, margin_bottom=5, margin_left=10)))
    n_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_left=10, margin_right=30))
    n_opts = ["Select Node"] + [f"{k}: {v['url']}" for k, v in app.custom_nodes.items()]
    app.sel_node = apply_android_border(toga.Selection(items=n_opts, style=Pack(flex=1, font_size=FONT_SIZE_BASE, color="#000000", background_color=COLOR_SELECTION_BG), on_change=app.on_node_select), bg_color=COLOR_SELECTION_BG, radius=10)
    if app.app_settings.get("selected_node") in n_opts:
        app.sel_node.value = app.app_settings.get("selected_node")
        
    btn_add_n = apply_android_border(
        toga.Button(ICON_PLUS, style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, width=32, height=32, margin_left=5, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_ICON), on_press=lambda w: app.navigate_to("add_node")),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=8, v_padding=0, h_padding=0, text_color="#FFFFFF"
    )
    btn_del_n = apply_android_border(toga.Button(ICON_TRASH, style=Pack(color=BTN_DEL_N_FONT_COLOR, background_color=BTN_DEL_N_COLOR, width=32, height=32, margin_left=5, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_ICON), on_press=app.delete_current_node), bg_color=BTN_DEL_N_COLOR, v_padding=2, h_padding=2, radius=8)
    n_row.add(app.sel_node); n_row.add(btn_add_n); n_row.add(btn_del_n)
    main_card.add(n_row)

    # Debug Section
    d_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=20, margin_left=10, margin_right=15))
    d_row.add(toga.Label("Debug/Advanced", style=Pack(color=LBL_NODE_SET_FONT_COLOR, background_color="transparent", **app.font_bold, flex=1)))
    def on_debug_toggle(widget):
        app.app_settings["debug_mode"] = widget.value
        app._save_json(app.settings_file, app.app_settings)
        app.cached_views.clear()
        app.cached_views["main"] = app.views["main"]()
        app.navigate_to("settings")
    sw_debug = toga.Switch("", value=app.app_settings.get("debug_mode", False), on_change=on_debug_toggle)
    d_row.add(sw_debug)
    main_card.add(d_row)

    # Service Fees Section
    main_card.add(toga.Label("SERVICE FEES", style=Pack(color=LBL_NODE_SET_FONT_COLOR, background_color="transparent", **app.font_bold, margin_top=20, margin_bottom=5, margin_left=10)))
    fee_desc = (
        "• Token to Token trades: FREE!\n"
        "• Under 10 ERG: 0.0001 ERG\n"
        "• Over 10 ERG: 0.05% (max 1 ERG)\n\n"
        "Yeah, it's CHEAP!!"
    )
    main_card.add(toga.Label(fee_desc, style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM, margin_left=15)))

    # Token Management Section
    main_card.add(toga.Label("TOKEN LIST MANAGEMENT", style=Pack(color=LBL_NODE_SET_FONT_COLOR, background_color="transparent", **app.font_bold, margin_top=20, margin_bottom=10, margin_left=10)))
    t_btn_row = toga.Box(style=Pack(direction=ROW, margin_left=10, margin_right=15))
    btn_import = apply_android_border(toga.Button("Import", style=Pack(flex=1, color="#FFFFFF", background_color=BTN_IMPORT_COLOR), on_press=app.import_tokens), bg_color=BTN_IMPORT_COLOR, radius=10)
    btn_export = apply_android_border(toga.Button("Export", style=Pack(flex=1, color="#FFFFFF", background_color=BTN_EXPORT_COLOR, margin_left=5), on_press=app.export_tokens), bg_color=BTN_EXPORT_COLOR, radius=10)
    
    debug_mode = app.app_settings.get("debug_mode", False)
    if debug_mode:
        t_btn_row.add(btn_import); t_btn_row.add(btn_export)
        main_card.add(t_btn_row)
        btn_import_all = apply_android_border(
            toga.Button('Import All Trading Pairs', style=Pack(color="#FFFFFF", background_color=BTN_IMPORT_ALL_COLOR, margin_top=10, margin_left=10, margin_right=15, font_weight=FONT_WEIGHT_BOLD), on_press=app.import_all_trading_pairs),
            bg_color=BTN_IMPORT_ALL_COLOR, radius=10
        )
        main_card.add(btn_import_all)
    
    btn_sync_tokens = apply_android_border(
        toga.Button("Check for new trading pairs", style=Pack(color="#FFFFFF", background_color=COLOR_ACCENT, margin_top=10, margin_left=10, margin_right=15, font_weight=FONT_WEIGHT_BOLD), on_press=app.check_token_updates),
        bg_color=COLOR_ACCENT, radius=10
    )
    main_card.add(btn_sync_tokens)
   
    box.add(main_card)

    main_scroll = toga.ScrollContainer(content=box, style=Pack(flex=1))
    return main_scroll

def build_add_node_view(app):
    box = toga.Box(style=Pack(direction=COLUMN, flex=1, margin=10, background_color=COLOR_BG))
    
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=15))
    btn_back = apply_android_border(toga.Button(ICON_BACK, style=Pack(color=BTN_BACK_SET_FONT_COLOR, background_color=BTN_BACK_SET_COLOR, width=36, height=36, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=lambda w: app.navigate_to("settings")), bg_color=BTN_BACK_SET_COLOR, v_padding=2, h_padding=2, radius=10)
    title = toga.Label("Add Custom Node", style=Pack(color=LBL_TITLE_ADD_NODE_FONT_COLOR, background_color=LBL_TITLE_ADD_NODE_COLOR, **app.font_title, flex=1, margin_left=10))
    header.add(btn_back); header.add(title)
    box.add(header)
    
    content = toga.Box(style=Pack(direction=COLUMN, padding=15))
    content = apply_android_border(content, bg_color=COLOR_CARD, border_width=0, radius=15)
    
    app.inp_node_name = apply_android_border(toga.TextInput(placeholder="Node Name (e.g. MyNode)", style=Pack(margin_bottom=10, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_node_btn), radius=10)
    app.inp_node_url = apply_android_border(toga.TextInput(placeholder="URL (http://...)", style=Pack(margin_bottom=20, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_node_btn), radius=10)
    app.btn_n_save = apply_android_border(toga.Button("Save Node", style=Pack(color="#555555", background_color="#1C1C1C"), on_press=app.save_new_node), radius=10)
    
    content.add(app.inp_node_name); content.add(app.inp_node_url); content.add(app.btn_n_save)
    box.add(content)
    return toga.ScrollContainer(content=box, style=Pack(flex=1))

def build_add_wallet_view(app):
    box = toga.Box(style=Pack(direction=COLUMN, flex=1, margin=5, background_color=COLOR_BG))
    
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin=10, margin_bottom=5))
    btn_back = apply_android_border(toga.Button(ICON_BACK, style=Pack(color=BTN_BACK_ADD_W_FONT_COLOR, background_color=BTN_BACK_ADD_W_COLOR, width=36, height=36, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=lambda w: app.navigate_to("main")), bg_color=BTN_BACK_ADD_W_COLOR, v_padding=2, h_padding=2, radius=10)
    title = toga.Label("Add Wallet", style=Pack(color=LBL_TITLE_ADD_WALLET_FONT_COLOR, background_color=LBL_TITLE_ADD_WALLET_COLOR, **app.font_title, flex=1, margin_left=10))
    header.add(btn_back); header.add(title)
    box.add(header)
    
    # Everything in one big card container - matching settings theme
    main_card = toga.Box(style=Pack(direction=COLUMN, flex=1, background_color=COLOR_CARD, margin_left=10, margin_right=10, margin_top=0, margin_bottom=10, padding_top=20, padding_bottom=20, padding_left=20, padding_right=20))
    main_card = apply_android_border(main_card, bg_color=COLOR_CARD, border_width=0, radius=20, h_padding=30, v_padding=20)
    
    app.inp_w_name = apply_android_border(toga.TextInput(placeholder="Wallet Name", style=Pack(margin_top=10, margin_bottom=15, margin_left=10, margin_right=15, font_size=FONT_SIZE_LG, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_wallet_btn), radius=10)
    main_card.add(app.inp_w_name)

    ro_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=20, margin_left=10, margin_right=15))
    ro_row.add(toga.Box(style=Pack(flex=1)))
    app.sw_w_readonly = toga.Switch("", value=True, on_change=app.on_readonly_toggle)
    ro_row.add(app.sw_w_readonly)
    ro_row.add(toga.Label("Read Only / Ergopay", style=Pack(color=LBL_RO_FONT_COLOR, background_color=LBL_RO_COLOR, font_size=FONT_SIZE_MD, font_weight=FONT_WEIGHT_BOLD, margin_left=10)))
    main_card.add(ro_row)

    # Help instructions (Always visible)
    help_box = toga.Box(style=Pack(direction=COLUMN, margin_bottom=20, margin_left=10, margin_right=15))
    
    lbl_rec = toga.Label("Recommended:", style=Pack(color="#28A745", font_size=FONT_SIZE_BASE, font_weight=FONT_WEIGHT_BOLD))
    
    txt1 = ("With Ergopay you sign the transaction using \nthe official Ergo Mobile Wallet.")
    lbl1 = toga.Label(txt1, style=Pack(color="#28A745", font_size=FONT_SIZE_BASE))
    
    txt2 = ("Mnemonics are encrypted on device and\nallow for faster trades by sign transactions\nOnly primary address is supported.")
    lbl2 = toga.Label(txt2, style=Pack(color="#FFFFFF", font_size=FONT_SIZE_BASE, margin_top=10))
    
    help_box.add(lbl_rec); help_box.add(lbl1); help_box.add(lbl2)
    main_card.add(help_box)
    

    app.box_w_mnem = toga.Box(style=Pack(direction=COLUMN, margin_left=10, margin_right=15))
    app.inp_w_mnem = apply_android_border(toga.MultilineTextInput(placeholder="Secret mnemonic (12, 15, or 18 words)", style=Pack(margin_bottom=10, height=100, font_size=FONT_SIZE_LG, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_wallet_btn), radius=10)

    # Hide Legacy toggle by default (only show in debug mode)
    if app.app_settings.get("debug_mode", False):
        leg_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=10))
        leg_row.add(toga.Label("Legacy (Pre-1.6.27)", style=Pack(color=LBL_LEGACY_FONT_COLOR, background_color=LBL_LEGACY_COLOR, font_size=FONT_SIZE_MD, font_weight=FONT_WEIGHT_BOLD, flex=1)))
        app.sw_w_legacy = toga.Switch("", value=False)
        leg_row.add(app.sw_w_legacy)
        app.box_w_mnem.add(leg_row)
    else:
        app.sw_w_legacy = toga.Switch("", value=False) # Keep the object for logic but don't add to UI

    # Wrap password fields so we can hide them
    app.box_w_pass = toga.Box(style=Pack(direction=COLUMN))
    app.inp_w_pass = apply_android_border(toga.PasswordInput(placeholder="Encryption Password", style=Pack(margin_bottom=10, font_size=FONT_SIZE_LG, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_wallet_btn), radius=10)
    app.inp_w_pass2 = apply_android_border(toga.PasswordInput(placeholder="Confirm Password", style=Pack(margin_bottom=10, font_size=FONT_SIZE_LG, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_wallet_btn), radius=10)
    app.box_w_pass.add(app.inp_w_pass); app.box_w_pass.add(app.inp_w_pass2)
    
    is_android = IS_ANDROID
    is_available = is_android and hasattr(app, 'bio_helper') and getattr(app.bio_helper, 'available', False)
    
    app.bio_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=20, visibility=('visible' if is_android else 'hidden')))
    app.sw_w_bio = toga.Switch("", value=False, enabled=is_available, on_change=app.on_bio_toggle)
    
    if is_available:
        bio_label_text = "Use Fingerprint for Security"
        bio_color = COLOR_TEXT
    else:
        bio_label_text = "Biometrics Unavailable"
        bio_color = COLOR_TEXT_DIM
        
    app.bio_row.add(toga.Label(bio_label_text, style=Pack(color=bio_color, flex=1, font_size=FONT_SIZE_BASE)))
    app.bio_row.add(app.sw_w_bio)
    
    app.box_w_mnem.add(app.inp_w_mnem); app.box_w_mnem.add(app.box_w_pass); app.box_w_mnem.add(app.bio_row)

    # Address box — hidden by default, shown only when Read Only toggle is on
    app.box_w_addr = toga.Box(style=Pack(direction=COLUMN, visibility='hidden', height=0, margin_left=10, margin_right=15))
    app.inp_w_addr = apply_android_border(toga.TextInput(placeholder="Ergo address (9h...)", style=Pack(margin_bottom=20, font_size=FONT_SIZE_LG, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.update_save_wallet_btn), radius=10)
    app.inp_w_addr.enabled = False # Disabled by default
    app.box_w_addr.add(app.inp_w_addr)

    # Save button
    app.btn_w_save = apply_android_border(toga.Button("Encrypt & Save Wallet", style=Pack(color="#555555", background_color="#1C1C1C", margin_left=10, margin_right=15, height=50), on_press=app.save_new_wallet), radius=10)

    main_card.add(app.box_w_mnem)
    main_card.add(app.box_w_addr)
    main_card.add(app.btn_w_save)
    
    scroll = toga.ScrollContainer(content=main_card, style=Pack(flex=1))
    box.add(scroll)
    
    # Force initial display layout depending on the default switch value
    app.on_readonly_toggle(app.sw_w_readonly)
    
    return box

def build_token_selector_view(app):
    # Root box that acts as a semi-transparent background covering the screen
    # We use a dark color to dim the background
    root = toga.Box(style=Pack(direction=COLUMN, flex=1, background_color="#0A122EF0"))
    
    # helper for dismissal
    def dismiss_selector(widget):
        app.edit_favs_mode = False
        app.navigate_to("main")

    # Top Spacer for vertical centering - Dismiss on click
    spacer_top = toga.Box(style=Pack(flex=1))
    apply_android_click(spacer_top, dismiss_selector)
    root.add(spacer_top)
    
    # Inner row to handle horizontal centering and side clicks
    mid_row = toga.Box(style=Pack(direction=ROW))
    
    spacer_left = toga.Box(style=Pack(width=20))
    apply_android_click(spacer_left, dismiss_selector)
    
    spacer_right = toga.Box(style=Pack(width=20))
    apply_android_click(spacer_right, dismiss_selector)
    
    # Floating selector box
    selector_box = toga.Box(style=Pack(direction=COLUMN, flex=1, height=480, background_color=COLOR_CARD))
    selector_box = apply_android_border(selector_box, bg_color=COLOR_CARD, border_width=0, radius=15)
    
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=5, margin_bottom=10, padding_left=10, padding_right=10))
    # Style back arrow with dark navy background and lighter grey border
    btn_back = apply_android_border(
        toga.Button(ICON_BACK, style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, width=42, height=42, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=dismiss_selector),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=10
    )
    title = toga.Label("Select Asset", style=Pack(color="#FFFFFF", background_color="transparent", font_size=FONT_SIZE_LG, font_weight=FONT_WEIGHT_BOLD, flex=1, margin_left=12))
    header.add(btn_back); header.add(title)
    selector_box.add(header)
    
    search_box = toga.Box(style=Pack(padding_left=10, padding_right=10, margin_bottom=10))
    app.inp_t_search = apply_android_border(
        toga.TextInput(placeholder="Search token...", style=Pack(flex=1, font_size=FONT_SIZE_LG, color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG), on_change=app.filter_tokens),
        bg_color=COLOR_INPUT_BG
    )
    search_box.add(app.inp_t_search)
    selector_box.add(search_box)
    
    # Scroll list with fixed height to ensure rendering on Android
    app.scroll_tokens = toga.ScrollContainer(style=Pack(height=350, margin_bottom=10), horizontal=False)
    app.box_tokens = toga.Box(style=Pack(direction=COLUMN, padding_left=5, padding_right=5))
    app.scroll_tokens.content = app.box_tokens
    selector_box.add(app.scroll_tokens)
    
    mid_row.add(spacer_left)
    mid_row.add(selector_box)
    mid_row.add(spacer_right)
    root.add(mid_row)
    
    # Bottom Spacer for vertical centering - Dismiss on click
    spacer_bot = toga.Box(style=Pack(flex=1))
    apply_android_click(spacer_bot, dismiss_selector)
    root.add(spacer_bot)
    
    app.filter_tokens(None)
    return root

def build_wallet_selector_view(app):
    # Root box that acts as a semi-transparent background covering the screen
    root = toga.Box(style=Pack(direction=COLUMN, flex=1, background_color="#0A122EF0"))
    
    # helper for dismissal
    def dismiss_selector(widget):
        app.edit_favs_mode = False
        app.navigate_to("main")

    # Top Spacer for vertical centering - Dismiss on click
    spacer_top = toga.Box(style=Pack(flex=1))
    apply_android_click(spacer_top, dismiss_selector)
    root.add(spacer_top)
    
    # Inner row to handle horizontal centering and side clicks
    mid_row = toga.Box(style=Pack(direction=ROW))
    
    spacer_left = toga.Box(style=Pack(width=20))
    apply_android_click(spacer_left, dismiss_selector)
    
    spacer_right = toga.Box(style=Pack(width=20))
    apply_android_click(spacer_right, dismiss_selector)
    
    # Floating selector box
    selector_box = toga.Box(style=Pack(direction=COLUMN, flex=1, height=480, background_color=COLOR_CARD))
    selector_box = apply_android_border(selector_box, bg_color=COLOR_CARD, border_width=0, radius=15)
    
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=5, margin_bottom=10, padding_left=10, padding_right=10))
    # Style back arrow with dark navy background and lighter grey border
    btn_back = apply_android_border(
        toga.Button(ICON_BACK, style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, width=42, height=42, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=dismiss_selector),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=10
    )
    title = toga.Label("Select Wallet", style=Pack(color="#FFFFFF", background_color="transparent", font_size=FONT_SIZE_LG, font_weight=FONT_WEIGHT_BOLD, flex=1, margin_left=12))
    header.add(btn_back); header.add(title)
    selector_box.add(header)
    
    # Wallet Scroll List
    app.scroll_wallets = toga.ScrollContainer(style=Pack(height=400, margin_bottom=10), horizontal=False)
    app.box_wallets = toga.Box(style=Pack(direction=COLUMN, padding_left=5, padding_right=5))
    app.scroll_wallets.content = app.box_wallets
    selector_box.add(app.scroll_wallets)
    
    mid_row.add(spacer_left)
    mid_row.add(selector_box)
    mid_row.add(spacer_right)
    root.add(mid_row)
    
    # Bottom Spacer for vertical centering - Dismiss on click
    spacer_bot = toga.Box(style=Pack(flex=1))
    apply_android_click(spacer_bot, dismiss_selector)
    root.add(spacer_bot)
    
    app.filter_wallets()
    return root

def build_review_tx_view(app):
    box = toga.Box(style=Pack(direction=COLUMN, flex=1, margin=10, background_color=COLOR_BG))
    
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_bottom=15))
    app.lbl_rev_title = toga.Label("Review Transaction", style=Pack(color=LBL_TITLE_REV_FONT_COLOR, background_color=LBL_TITLE_REV_COLOR, **app.font_title, flex=1))
    app.ai_rev = toga.ActivityIndicator(style=Pack(width=25, height=25, margin_right=5))
    header.add(app.lbl_rev_title); header.add(app.ai_rev)
    box.add(header)
    
    
    scroll = toga.ScrollContainer(style=Pack(flex=1, margin_bottom=10))
    content = toga.Box(style=Pack(direction=COLUMN))
    
    # Summary Card for "Buying/Paying"
    app.box_rev_summary = toga.Box(style=Pack(direction=COLUMN, margin_bottom=10))
    app.box_rev_summary = apply_android_border(app.box_rev_summary, bg_color=COLOR_CARD, border_width=0, radius=15, v_padding=15, h_padding=20)
    
    # Buying Row
    buy_box = toga.Box(style=Pack(direction=COLUMN, margin_bottom=8))
    buy_box.add(toga.Label("YOU ARE BUYING:", style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM)))
    app.lbl_rev_buy = toga.Label("", style=Pack(color=COLOR_ACCENT, font_size=FONT_SIZE_XL, font_weight=FONT_WEIGHT_BOLD))
    buy_box.add(app.lbl_rev_buy)
    
    # Paying Row
    pay_box = toga.Box(style=Pack(direction=COLUMN, margin_bottom=10))
    pay_box.add(toga.Label("YOU ARE PAYING (INCL. FEES):", style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM)))
    app.lbl_rev_pay = toga.Label("", style=Pack(color="#FFFFFF", font_size=FONT_SIZE_XL, font_weight=FONT_WEIGHT_BOLD))
    pay_box.add(app.lbl_rev_pay)
    
    # Fees info
    app.lbl_rev_fees = toga.Label("", style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM))
    
    app.box_rev_summary.add(buy_box)
    app.box_rev_summary.add(pay_box)
    app.box_rev_summary.add(app.lbl_rev_fees)
    
    app.lbl_rev_verify = toga.Label("Verify all details carefully.", style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM, margin_bottom=10, text_align=CENTER))
    
    app.box_tx_json = toga.Box(style=Pack(direction=COLUMN, visibility='hidden', flex=0, height=0))
    app.lbl_tx_json = fix_android_scroll(toga.MultilineTextInput(
        readonly=True, 
        style=Pack(
            color=COLOR_BLUE, 
            background_color=COLOR_INPUT_BG,
            font_size=FONT_SIZE_SM, 
            flex=0,
            height=0,
            visibility='hidden',
            margin=5
        )
    ))
    app.box_tx_json.add(app.lbl_tx_json)

    app.box_tx_readable = toga.Box(style=Pack(direction=COLUMN, visibility='visible', flex=1, height=350))
    app.lbl_tx_readable = fix_android_scroll(toga.MultilineTextInput(
        readonly=True,
        style=Pack(
            color=COLOR_ACCENT,
            background_color=COLOR_INPUT_BG,
            font_size=FONT_SIZE_MD,
            flex=1,
            margin=5
        )
    ))
    app.box_tx_readable.add(app.lbl_tx_readable)
    
    content.add(app.box_rev_summary)
    content.add(app.lbl_rev_verify)
    content.add(app.box_tx_readable)
    content.add(app.box_tx_json)
    scroll.content = content
    box.add(scroll)
    
    j_row = toga.Box(style=Pack(direction=ROW, align_items=CENTER, margin_top=5, margin_bottom=10))
    j_row.add(toga.Label("Show Raw Transaction", style=Pack(color=LBL_RAW_JSON_FONT_COLOR, background_color=LBL_RAW_JSON_COLOR, **{**app.font_base, "flex": 1})))
    
    if app.app_settings.get("debug_mode", False):
        btn_copy = apply_android_border(
            toga.Button(ICON_PASTE, style=Pack(color="#FFFFFF", background_color=BTN_EXPORT_COLOR, width=32, height=32, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_ICON, margin_right=10), on_press=app.copy_tx_json),
            bg_color=BTN_EXPORT_COLOR, radius=8, v_padding=0, h_padding=0
        )
        j_row.add(btn_copy)

    app.sw_json = toga.Switch("", value=False, on_change=app.toggle_json)
    j_row.add(app.sw_json)
    box.add(j_row)
    
    # Biometric/Password container
    app.box_rev_auth = toga.Box(style=Pack(direction=COLUMN, margin_bottom=10))
    
    app.lbl_rev_bio_hint = toga.Label("🔒 Biometric Security Active", style=Pack(color=COLOR_ACCENT, font_size=FONT_SIZE_BASE, margin_bottom=5, text_align=CENTER))
    app.btn_rev_show_pass = toga.Button("Use Password Instead", on_press=app.show_password_input, style=Pack(color=COLOR_TEXT_DIM, font_size=FONT_SIZE_SM, margin_bottom=5))
    
    app.inp_rev_pass = apply_android_border( toga.PasswordInput(placeholder="Wallet Password", style=Pack(color=COLOR_INPUT_TEXT, background_color=COLOR_INPUT_BG)), radius=10)
    
    app.box_rev_auth.add(app.lbl_rev_bio_hint)
    app.box_rev_auth.add(app.btn_rev_show_pass)
    app.box_rev_auth.add(app.inp_rev_pass)
    
    box.add(app.box_rev_auth)
    
    btn_row = toga.Box(style=Pack(direction=ROW))
    btn_cancel = apply_android_border( toga.Button("Cancel", style=Pack(color=BTN_CANCEL_REV_FONT_COLOR, background_color=BTN_CANCEL_REV_COLOR, flex=1), on_press=lambda w: app.navigate_to("main")), radius=10)
    app.btn_confirm_tx = apply_android_border( toga.Button("Confirm Swap", style=Pack(color=BTN_CONFIRM_REV_FONT_COLOR, background_color=BTN_CONFIRM_REV_COLOR, flex=1, margin_left=10), on_press=app.execute_swap), radius=10)
    btn_row.add(btn_cancel); btn_row.add(app.btn_confirm_tx)
    box.add(btn_row)
    
    app.toggle_json(app.sw_json)
    return box

def build_wallet_viewer_view(app):
    box = toga.Box(style=Pack(direction=COLUMN, flex=1, background_color=COLOR_BG))
    
    # Header
    header = toga.Box(style=Pack(direction=ROW, align_items=CENTER, padding=10, background_color="transparent"))
    btn_back_wv = apply_android_border(
        toga.Button(ICON_BACK, style=Pack(color="#FFFFFF", background_color="transparent", width=40, height=40, font_family=FONT_FAMILY_ICONS, font_size=FONT_SIZE_LG), on_press=lambda w: app.navigate_to("main")),
        bg_color="transparent", border_width=0, radius=10
    )
    title = toga.Label("Wallet", style=Pack(color="#FFFFFF", **app.font_title, flex=1, margin_left=10))
    
    app.ai_wv = toga.ActivityIndicator(style=Pack(width=25, height=25, margin_right=10))
    btn_sync = apply_android_border(
        toga.Button("\u21BB", style=Pack(color="#FFFFFF", background_color=COLOR_INPUT_BG, width=40, height=40, font_size=FONT_SIZE_LG), on_press=lambda w: app.fetch_wallet_balances(silent=False)),
        bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=20
    )
    
    header.add(btn_back_wv); header.add(title); header.add(app.ai_wv); header.add(btn_sync)
    box.add(header)

    # Everything in one big card container - matching settings theme
    main_card = toga.Box(style=Pack(direction=COLUMN, flex=1, background_color=COLOR_CARD, margin_left=10, margin_right=10, margin_top=0, margin_bottom=10, padding_top=20, padding_bottom=20, padding_left=20, padding_right=20))
    main_card = apply_android_border(main_card, bg_color=COLOR_CARD, border_width=0, radius=20, h_padding=30, v_padding=20)

    # Address & Balance Section
    app.lbl_wv_addr = toga.Label("Address: ...", style=Pack(color="#FFFFFF", font_size=FONT_SIZE_XL, margin_bottom=10,margin_top=15,margin_left=10, background_color="transparent"))
    app.lbl_wv_erg = toga.Label("0.0000 ERG", style=Pack(color="#FFFFFF", font_size=FONT_SIZE_LG, font_weight=FONT_WEIGHT_BOLD,margin_left=10, background_color="transparent"))
    main_card.add(app.lbl_wv_addr)
    main_card.add(app.lbl_wv_erg)
    
    # Tab Bar (Sub-container, but fits inside main_card)
    tab_bar = toga.Box(style=Pack(direction=ROW, margin_top=15, margin_bottom=10, height=45))
    tab_bar = apply_android_border(tab_bar, bg_color=COLOR_INPUT_BG, border_color="#535C6E", border_width=1, radius=12)
    
    app.btn_tab_tokens = toga.Box(style=Pack(direction=ROW, align_items=CENTER, flex=1, justify_content=CENTER))
    app.btn_tab_tokens.add(toga.Label("Tokens", style=Pack(color="#FFFFFF", margin_left=10, font_weight=FONT_WEIGHT_BOLD)))
    app.btn_tab_tokens._tab = "tokens"
    apply_android_click(app.btn_tab_tokens, app.set_wallet_view_tab)
    
    app.btn_tab_history = toga.Box(style=Pack(direction=ROW, align_items=CENTER, flex=1, justify_content=CENTER))
    app.btn_tab_history.add(toga.Label("History", style=Pack(color="#FFFFFF", margin_left=10, font_weight=FONT_WEIGHT_BOLD)))
    app.btn_tab_history._tab = "history"
    apply_android_click(app.btn_tab_history, app.set_wallet_view_tab)
    
    t_bg = COLOR_ACCENT if app._wallet_view_tab == "tokens" else COLOR_INPUT_BG
    h_bg = COLOR_ACCENT if app._wallet_view_tab == "history" else COLOR_INPUT_BG
    app.btn_tab_tokens = apply_android_border(app.btn_tab_tokens, bg_color=t_bg, radius=10, h_padding=0, v_padding=0)
    app.btn_tab_history = apply_android_border(app.btn_tab_history, bg_color=h_bg, radius=10, h_padding=0, v_padding=0)
    
    tab_bar.add(app.btn_tab_tokens)
    tab_bar.add(app.btn_tab_history)
    main_card.add(tab_bar)

    # Options (Simulated)
    app.sw_unconf = toga.Switch("", value=False)
    if app.app_settings.get("debug_mode", False):
        opt_row = toga.Box(style=Pack(direction=ROW, margin_bottom=10, align_items=CENTER, background_color="transparent"))
        opt_row.add(toga.Label("Sim.", style=Pack(color="#AAAAAA", font_size=FONT_SIZE_SM)))
        app.sw_sim = toga.Switch("", value=app.is_simulation, on_change=lambda w: app.fetch_wallet_balances(silent=False))
        opt_row.add(app.sw_sim)
        main_card.add(opt_row)
    else:
        app.sw_sim = toga.Switch("", value=False)

    # Frozen Header Container (Asset / Balance)
    app.box_wv_headers = toga.Box(style=Pack(direction=ROW, margin_bottom=5, padding_left=5, padding_right=5, background_color="transparent"))
    main_card.add(app.box_wv_headers)
    
    # Scrollable Content (List) - Flexible height
    app.scroll_wallet = toga.ScrollContainer(style=Pack(flex=1), horizontal=False)
    app.box_wallet_info = toga.Box(style=Pack(direction=COLUMN))
    app.scroll_wallet.content = app.box_wallet_info
    main_card.add(app.scroll_wallet)
    
    # Delete Wallet Button at bottom
    btn_del = apply_android_border(
        toga.Button(ICON_TRASH + " Delete Wallet", style=Pack(color="#FF4444", background_color="transparent", flex=1, font_size=FONT_SIZE_MD, margin_top=10), on_press=app.delete_current_wallet),
        bg_color="transparent", border_color="#FF4444", border_width=1, radius=10
    )
    main_card.add(btn_del)

    box.add(main_card)
    
    return box
