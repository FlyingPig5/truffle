# wallet_manager.py

import os
import json
import base64
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.backends import default_backend
from cryptography.fernet import Fernet
import toga

from .ergpy_signer import ErgoSigner

class WalletManager:
    def __init__(self, data_dir):
        self.wallets_file = data_dir / "wallets.json"
        self.wallets = self._load_json(self.wallets_file, {})

    def _load_json(self, path, default_val):
        if path.exists():
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                return default_val
        return default_val

    def _save_json(self, path, data):
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=4)
        except Exception as e:
            print(f"Error saving {path}: {e}")

    def save(self):
        """Persists wallets dictionary to disk."""
        self._save_json(self.wallets_file, self.wallets)

    async def derive_and_save_mnemonic_wallet(self, node_url, name, mnem, pwd, use_legacy=False):
        """Asynchronously derives address and encrypts mnemonic for secure local storage."""
        import asyncio
        print(f"[piggytrade] Deriving address for {name} (legacy={use_legacy})...", flush=True)
        signer = await asyncio.to_thread(ErgoSigner, node_url)
        # Offload the heavy Java derivation code to a background thread to keep UI responsive
        public_address = await asyncio.to_thread(signer.get_address, mnem, "", 0, use_legacy)
        print(f"[piggytrade] Derived address: {public_address}", flush=True)
        
        salt = os.urandom(16)
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=salt,
            iterations=100000,
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
            "use_legacy": use_legacy
        }
        self.save()
        return public_address

    def decrypt_mnemonic(self, wallet_data, password):
        """Attempt to decrypt a stored mnemonic using the provided password."""
        salt = base64.b64decode(wallet_data["salt"])
        token = base64.b64decode(wallet_data["token"])
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=salt,
            iterations=100000,
            backend=default_backend()
        )
        key = base64.urlsafe_b64encode(kdf.derive(password.encode()))
        f = Fernet(key)
        return f.decrypt(token).decode()

