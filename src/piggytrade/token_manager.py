import json
import requests
import pathlib
import asyncio

class TokenManager:
    def __init__(self, data_dir, resource_dir):
        self.data_file = pathlib.Path(data_dir) / "tokens.json"
        self.resource_file = pathlib.Path(resource_dir) / "tokens.json"
        self.tokens = {}
        self.load()

    def load(self):
        """Load tokens from resources and merge with data dir (personal overrides)."""
        resource_tokens = {}
        if self.resource_file.exists():
            try:
                with open(self.resource_file, 'r', encoding='utf-8') as f:
                    resource_tokens = json.load(f)
            except Exception as e:
                print(f"[TokenManager] Error loading from resources: {e}")

        data_tokens = {}
        if self.data_file.exists():
            try:
                with open(self.data_file, 'r', encoding='utf-8') as f:
                    data_tokens = json.load(f)
            except Exception as e:
                print(f"[TokenManager] Error loading from data: {e}")

        self.tokens = resource_tokens.copy()
        self.tokens.update(data_tokens)
        
        if len(self.tokens) > len(data_tokens):
            self.save()

    def save(self):
        """Save current tokens to the data directory."""
        try:
            with open(self.data_file, 'w', encoding='utf-8') as f:
                json.dump(self.tokens, f, indent=4, ensure_ascii=False)
        except Exception as e:
            print(f"[TokenManager] Error saving to data: {e}")

    def validate_format(self, data):
        """Check if the provided data matches the expected token list format."""
        if not isinstance(data, dict) or not data:
            return False
        
        for name, config in data.items():
            if not isinstance(config, dict):
                return False
            
            # Case 1: Standard ERG-Token Pool
            is_standard = all(k in config for k in ['id', 'pid', 'lp', 'dec'])
            # Case 2: Token-to-Token Pool
            is_t2t = all(k in config for k in ['id_in', 'id_out', 'pid', 'dec_in', 'dec_out'])
            
            if not (is_standard or is_t2t):
                return False
        return True

    async def fetch_remote(self, url):
        """Fetch tokens from a remote URL. Returns (data, error_msg)."""
        try:
            resp = await asyncio.to_thread(requests.get, url, timeout=10)
            if resp.status_code != 200:
                return None, f"Server returned status {resp.status_code}"
            
            try:
                new_tokens = resp.json()
            except Exception:
                return None, "Response is not valid JSON."

            if self.validate_format(new_tokens):
                return new_tokens, None
            else:
                return None, "Downloaded file does not match the required token list format."

        except Exception as e:
            return None, f"Network error: {str(e)}"

    def export_json(self):
        """Return the current tokens as a formatted JSON string."""
        return json.dumps(self.tokens, indent=4, ensure_ascii=False)
