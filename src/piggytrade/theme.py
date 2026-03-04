# theme.py

from .config import NODES

# =============================================================================
# CENTRALIZED THEME & COLORS
# =============================================================================
# --- Colors ---
COLOR_BG       = "#050B1F"   # Darker for more depth
COLOR_CARD     = "#0E183D"   # Lightened slightly for more contrast with BG
COLOR_BORDER   = "#FFFFFF"   
COLOR_INPUT_BG = "#1A2652"   # Lightened to stand out from CARD
COLOR_SELECTION_BG = "#CCCCCC" # Background for dropdowns (Selection)
COLOR_INPUT_TEXT= "#FFFFFF"  # Font color for text inside inputs and dropdowns
COLOR_INPUT_HINT= "#AAAAAA"  # Placeholder / Hint text color
COLOR_CHIP     = "#21262D"   # Inactive chip / quick-select button
COLOR_QUICK    = "#21262D"   # Favorites grid button background
COLOR_TEXT     = "#F0F6FC"   # Primary text color
COLOR_ACCENT   = "#00F5A0"   # Accent / success / active color
COLOR_DANGER   = "#FF4D4D"   # Danger / delete / LIVE mode color
COLOR_BLUE     = "#58A6FF"   # Informational / JSON viewer text
COLOR_ICON     = "#FFFFFF"   # Icon color
COLOR_ORANGE   = "#FF8C00"   # Orange for special import
COLOR_TEXT_DIM = "#AAAAAA"  # Dimmed text color

# --- Font Sizes ---
FONT_SIZE_SM   = 8   # Small: JSON viewer, minor labels
FONT_SIZE_BASE = 10   # Base: most labels, options
FONT_SIZE_MD   = 12   # Medium: From/To/Amount field labels and buttons
FONT_SIZE_LG   = 16   # Large: Quote display, swap button icon
FONT_SIZE_XL   = 16   # Title / heading font size
FONT_SIZE_ICON = 14   # Icons in buttons


# --- Font Weights ---
FONT_WEIGHT_NORMAL = "normal"
FONT_WEIGHT_BOLD   = "bold"

# --- Layout Constants ---
LABEL_WIDTH = 80   # Width of inline labels (From / Amount / To) for alignment

# ICON_FONT_FAMILY for Material Design Icons
FONT_FAMILY_ICONS = "Material Design Icons"

# Mappings specifically for your uploaded font file
ICON_COG       = "\U0000E8B8" # mdi-cog (Internal font index for gear)
ICON_WALLET    = "\U0000F8FF" # mdi-wallet (Shifted from F059E)
ICON_PLUS      = "\U0000E145" # mdi-plus (This one was correct)
ICON_TRASH     = "\U0000E872" # mdi-delete (Alternative name for mdi-trash-can)
ICON_SWAP_VERT = "\U000FFFA3" # mdi-swap-vertical che
ICON_BACK      = "\U0000EF7D" # mdi-arrow-left (Back arrow)
ICON_PASTE     = "\U0000E2C4" # mdi-content-paste (Standard clipboard)


DEFAULT_FAVORITES = ["ERG", "sigusd", "sigrsv", "rsADA", "cmt", "cat", "paideia", "gif"]
DEFAULT_NODE_KEY = "Pub1"
DEFAULT_NODE = NODES.get(DEFAULT_NODE_KEY)


# =============================================================================
# INDIVIDUAL COMPONENT COLORS
# =============================================================================
# LABELS (Color and Font Color)
LBL_TITLE_MAIN_COLOR = "transparent"
LBL_TITLE_MAIN_FONT_COLOR = COLOR_TEXT # Title Label
LBL_WALLET_SECTION_COLOR = "transparent"
LBL_WALLET_SECTION_FONT_COLOR = COLOR_TEXT # Wallet Section Label
LBL_SWAP_SECTION_COLOR = "transparent"
LBL_SWAP_SECTION_FONT_COLOR = COLOR_TEXT # Swap Section Label
LBL_EDIT_FAVS_COLOR = "transparent"
LBL_EDIT_FAVS_FONT_COLOR = COLOR_TEXT # Edit Favs Label
LBL_FROM_ASSET_COLOR = "transparent"
LBL_FROM_ASSET_FONT_COLOR = COLOR_TEXT # From Asset Label
LBL_ROUTE_COLOR = "transparent"
LBL_ROUTE_FONT_COLOR = COLOR_BLUE # Route Label
LBL_TO_ASSET_COLOR = "transparent"
LBL_TO_ASSET_FONT_COLOR = COLOR_TEXT # To Asset Label
LBL_QUOTE_COLOR = "transparent"
LBL_QUOTE_FONT_COLOR = COLOR_ACCENT # Quote Label
LBL_SEL_WALLET_COLOR = "transparent"
LBL_SEL_WALLET_FONT_COLOR = COLOR_TEXT # Select Wallet Label
LBL_LP_COLOR = "transparent"
LBL_LP_FONT_COLOR = COLOR_TEXT # LP Label
LBL_FEE_COLOR = "transparent"
LBL_FEE_FONT_COLOR = COLOR_TEXT # Fee Label
LBL_FEE_VAL_COLOR = "transparent"
LBL_FEE_VAL_FONT_COLOR = COLOR_TEXT # Fee Value Label
LBL_TITLE_SET_COLOR = "transparent"
LBL_TITLE_SET_FONT_COLOR = COLOR_TEXT # Settings Title Label
LBL_CREDITS_COLOR = "transparent"
LBL_CREDITS_FONT_COLOR = COLOR_ACCENT # Settings Credits Label
LBL_NODE_SET_COLOR = "transparent"
LBL_NODE_SET_FONT_COLOR = COLOR_TEXT # Node Settings Label
LBL_TITLE_ADD_NODE_COLOR = "transparent"
LBL_TITLE_ADD_NODE_FONT_COLOR = COLOR_TEXT # Add Node Title Label
LBL_TITLE_ADD_WALLET_COLOR = "transparent"
LBL_TITLE_ADD_WALLET_FONT_COLOR = COLOR_TEXT # Add Wallet Title Label
LBL_MNEM_COLOR = "transparent"
LBL_MNEM_FONT_COLOR = COLOR_TEXT # Add Wallet Mnemonic Label
LBL_RO_COLOR = "transparent"
LBL_RO_FONT_COLOR = COLOR_TEXT # Add Wallet ReadOnly Label
LBL_LEGACY_COLOR = "transparent"
LBL_LEGACY_FONT_COLOR = COLOR_TEXT # Add Wallet Legacy Label
LBL_W_ADDR_COLOR = "transparent"
LBL_W_ADDR_FONT_COLOR = COLOR_TEXT # Add Wallet Address Label
LBL_TITLE_TOKEN_COLOR = "transparent"
LBL_TITLE_TOKEN_FONT_COLOR = COLOR_TEXT # Token Selector Title Label
LBL_TITLE_REV_COLOR = "transparent"
LBL_TITLE_REV_FONT_COLOR = COLOR_TEXT # Review Tx Title Label
LBL_RAW_JSON_COLOR = "transparent"
LBL_RAW_JSON_FONT_COLOR = COLOR_TEXT # Review Tx Show JSON Label
LBL_REV_SUM_COLOR = "transparent"
LBL_REV_SUM_FONT_COLOR = COLOR_TEXT # Review Tx Summary Label
LBL_TITLE_WVIEW_COLOR = "transparent"
LBL_TITLE_WVIEW_FONT_COLOR = COLOR_TEXT # Wallet View Title Label
LBL_WVIEW_UNCONF_COLOR = "transparent"
LBL_WVIEW_UNCONF_FONT_COLOR = COLOR_TEXT # Wallet View Unconfirmed Label
LBL_WVIEW_SIM_COLOR = "transparent"
LBL_WVIEW_SIM_FONT_COLOR = COLOR_TEXT # Wallet View Simulated Label
LBL_WVIEW_FETCH_COLOR = "transparent"
LBL_WVIEW_FETCH_FONT_COLOR = COLOR_TEXT # Wallet Info Fetching Label
LBL_WVIEW_ADDR_COLOR = "transparent"
LBL_WVIEW_ADDR_FONT_COLOR = COLOR_TEXT # Wallet Info Address Label
LBL_WVIEW_ERG_COLOR = "transparent"
LBL_WVIEW_ERG_FONT_COLOR = COLOR_TEXT # Wallet Info ERG Label
LBL_WVIEW_TOKEN_COLOR = "transparent"
LBL_WVIEW_TOKEN_FONT_COLOR = COLOR_TEXT # Wallet Info Token Label
LBL_WVIEW_HIST_COLOR = "transparent"
LBL_WVIEW_HIST_FONT_COLOR = COLOR_TEXT # Wallet Info History Title Label
LBL_WVIEW_ITM_COLOR = "transparent"
LBL_WVIEW_ITM_FONT_COLOR = COLOR_TEXT # Wallet Info History Item Label
LBL_WVIEW_ERR_COLOR = "transparent"
LBL_WVIEW_ERR_FONT_COLOR = COLOR_DANGER # Wallet Info Error Label

# BUTTONS (Color and Font Color)
BTN_FAV_COLOR = COLOR_BLUE
BTN_FAV_FONT_COLOR = COLOR_BG  # Dark text on blue for contrast
BTN_TOKEN_SEL_COLOR = COLOR_INPUT_BG
BTN_TOKEN_SEL_FONT_COLOR = COLOR_TEXT # Token Selector Tokens
BTN_SETTINGS_COLOR = COLOR_BG
BTN_SETTINGS_FONT_COLOR = COLOR_TEXT # Settings Button
BTN_ADD_W_COLOR = "#000000"
BTN_ADD_W_FONT_COLOR = COLOR_ICON # Add Wallet Button (White icon)
BTN_DEL_W_COLOR = COLOR_DANGER
BTN_DEL_W_FONT_COLOR = COLOR_TEXT # Delete Wallet Button
BTN_PASTE_W_COLOR = COLOR_CHIP
BTN_PASTE_W_FONT_COLOR = COLOR_TEXT # Paste Address Button
BTN_VIEW_W_COLOR = "#000000"
BTN_VIEW_W_FONT_COLOR = COLOR_ICON # View Wallet Button (White icon)

BTN_FROM_COLOR = COLOR_INPUT_BG
BTN_FROM_FONT_COLOR = COLOR_INPUT_TEXT # From Asset Button
BTN_SWAP_COLOR = COLOR_CARD
BTN_SWAP_FONT_COLOR = COLOR_ACCENT # Swap Direction Button
BTN_TO_COLOR = COLOR_INPUT_BG
BTN_TO_FONT_COLOR = COLOR_INPUT_TEXT # To Asset Button
BTN_SAFE_COLOR = COLOR_CHIP
BTN_SAFE_FONT_COLOR = COLOR_TEXT # Safe Mode Button
BTN_LIVE_COLOR = COLOR_CHIP
BTN_LIVE_FONT_COLOR = COLOR_TEXT # Live Mode Button
BTN_SUBMIT_COLOR = COLOR_BLUE
BTN_SUBMIT_FONT_COLOR = COLOR_BG # Submit/Simulate Button
BTN_BACK_SET_COLOR = COLOR_BG
BTN_BACK_SET_FONT_COLOR = COLOR_TEXT # Back Button (Settings)
BTN_ADD_N_COLOR = "#000000"
BTN_ADD_N_FONT_COLOR = COLOR_ICON # Add Node Button (White icon)
BTN_DEL_N_COLOR = COLOR_DANGER
BTN_DEL_N_FONT_COLOR = COLOR_TEXT # Delete Node Button
BTN_IMPORT_COLOR = COLOR_BLUE
BTN_EXPORT_COLOR = COLOR_BLUE
BTN_IMPORT_ALL_COLOR = COLOR_ORANGE
BTN_DEL_N_FONT_COLOR = COLOR_TEXT # Delete Node Button
BTN_BACK_ADD_N_COLOR = COLOR_BG
BTN_BACK_ADD_N_FONT_COLOR = COLOR_TEXT # Back Button (Add Node)
BTN_SAVE_N_COLOR = COLOR_ACCENT
BTN_SAVE_N_FONT_COLOR = COLOR_BG # Save Node Button
BTN_BACK_ADD_W_COLOR = COLOR_BG
BTN_BACK_ADD_W_FONT_COLOR = COLOR_TEXT # Back Button (Add Wallet)
BTN_PASTE_ADDR_COLOR = COLOR_CHIP
BTN_PASTE_ADDR_FONT_COLOR = COLOR_TEXT # Paste Address Button (Add Wallet)
BTN_SAVE_W_COLOR = COLOR_ACCENT
BTN_SAVE_W_FONT_COLOR = COLOR_BG # Save Wallet Button
BTN_BACK_TOK_COLOR = COLOR_BG
BTN_BACK_TOK_FONT_COLOR = COLOR_TEXT # Back Button (Token Selector)
BTN_CANCEL_REV_COLOR = COLOR_DANGER
BTN_CANCEL_REV_FONT_COLOR = COLOR_TEXT # Cancel Button (Review Tx)
BTN_CONFIRM_REV_COLOR = COLOR_ACCENT
BTN_CONFIRM_REV_FONT_COLOR = COLOR_BG # Confirm Button (Review Tx)
BTN_DEL_WVIEW_COLOR = COLOR_DANGER
BTN_DEL_WVIEW_FONT_COLOR = COLOR_TEXT # Delete Button (Wallet View)
BTN_CLOSE_WVIEW_COLOR = COLOR_CHIP
BTN_CLOSE_WVIEW_FONT_COLOR = COLOR_TEXT # Close Button (Wallet View)
