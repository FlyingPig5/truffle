# trader_ergpy.py
import json
from math import ceil
from . import amm
from decimal import Decimal, getcontext
from .config import SPECTRUM_ADDRESS, SPECTRUM_TOKEN_ADDRESS, USE_CONFIG, DEXYGOLD_CONFIG

getcontext().prec = 100

class Trader:
    def __init__(self, node_client, tx_builder, tokens_dict, signer=None):
        self.client = node_client
        self.builder = tx_builder
        self.tokens = tokens_dict
        self.signer = signer

    def get_token_config(self, token_name):

        if token_name in self.tokens:
            return self.tokens[token_name]
        

        for key, info in self.tokens.items():
            if key.lower() == token_name.lower():
                return info
            if isinstance(info, dict) and info.get("name", "").lower() == token_name.lower():
                return info
        raise ValueError(f"Token '{token_name}' not found in tokens.json")

    def get_quote(self, token_name, amount, order_type, pool_type="erg", check_mempool=True):
        try:
            cfg = self.get_token_config(token_name.strip())
        except ValueError as e:
            return f"Error: {e}"
        token_id = cfg.get('id')
        token_pid = cfg['pid']
        lp_id = cfg.get('lp')
        pool_box, _ = self.client.get_pool_box(token_pid, lp_id, check_mempool=check_mempool)
        if not pool_box:
            return "Error: Pool not found"
        def get_bal(box, tid):
            return int(next((a['amount'] for a in box['assets'] if a['tokenId'] == tid), 0))
        pool_nanoerg = int(pool_box['value'])
        amount_dec = Decimal(str(amount))
        if pool_type == "erg":
            pool_token_bal = get_bal(pool_box, token_id)
            if order_type == "buy":
                nerg_to_send = int(amount_dec * Decimal("1000000000"))
                fee_mult = Decimal("1") - Decimal(str(cfg['fee']))
                erg_amm = int(Decimal(nerg_to_send) * fee_mult)
                delta, _, _ = amm.buy_token(erg_amm, pool_nanoerg, pool_token_bal)
                readable_out = int(delta) / (10 ** cfg['dec'])
                return f"{readable_out:,.4f}"
            elif order_type == "sell":
                token_amt = int(amount_dec * (Decimal("10") ** cfg['dec']))
                erg_out, _, _, _ = amm.sell_token(token_amt, pool_nanoerg, pool_token_bal)
                fee_mult = Decimal("1") - Decimal(str(cfg['fee']))
                erg_received = int(Decimal(str(erg_out)) * fee_mult)
                readable_out = erg_received / 1e9
                return f"{readable_out:,.4f}"
        elif pool_type == "token":
            tid_x = cfg.get('id_in')
            tid_y = cfg.get('id_out')
            dec_x = cfg.get('dec_in', 0)
            dec_y = cfg.get('dec_out', 0)
            pool_bal_x = get_bal(pool_box, tid_x)
            pool_bal_y = get_bal(pool_box, tid_y)
            if order_type == "sell":
                amt_in_x = int(amount_dec * (Decimal("10") ** dec_x))
                delta_y = int(amm.token_for_token(amt_in_x, pool_bal_x, pool_bal_y, cfg['fee']))
                readable_out = delta_y / (10 ** dec_y)
                return f"{readable_out:,.4f}"
            elif order_type == "buy":
                amt_in_y = int(amount_dec * (Decimal("10") ** dec_y))
                delta_x = int(amm.token_for_token(amt_in_y, pool_bal_y, pool_bal_x, cfg['fee']))
                readable_out = delta_x / (10 ** dec_x)
                return f"{readable_out:,.4f}"
        return "Error: Unknown Pool Type"

    def build_swap_transaction(self, token_name, amount, order_type, pool_type="erg", sender_address=None, fee=0.002, use_mempool=True, use_lp_mempool=True, mnemonic="", mnemonic_password="", pre_1627=True):
        fee_nano = int(Decimal(str(fee)) * Decimal("1000000000"))
        try:
            cfg = self.get_token_config(token_name.strip())
        except ValueError as e:
            raise ValueError(str(e))
        wallet_address = sender_address
        if not wallet_address and self.signer and mnemonic:
            wallet_address = self.signer.get_address(mnemonic, mnemonic_password, use_pre1627=pre_1627)
        if not wallet_address:
            raise ValueError("Sender address could not be determined.")
        self.builder.my_address = wallet_address
        my_assets, my_nanoerg, user_boxes = self.client.get_my_assets(wallet_address, check_mempool=use_mempool)
        token_id = cfg.get('id')
        token_pid = cfg['pid']
        lp_id = cfg.get('lp')
        pool_box, from_mempool = self.client.get_pool_box(token_pid, lp_id, check_mempool=use_lp_mempool)
        if not pool_box:
            raise ValueError(f"Pool box for {token_name} not found.")
        bank_box = None
        extra_requests = []
        if token_name.lower() == "use":
            bank_nft_id = USE_CONFIG['lp_nft']
            bank_box, _ = self.client.get_pool_box(bank_nft_id, bank_nft_id, check_mempool=use_lp_mempool)
            if not bank_box:
                raise ValueError("USE Bank Box not found!")
        def get_bal(box, tid):
            return int(next((a['amount'] for a in box['assets'] if a['tokenId'] == tid), 0))
        pool_nanoerg = int(pool_box['value'])
        amount_dec = Decimal(str(amount))
        nerg_to_pool = 0
        tokens_to_pool_list = []
        pool_addr = SPECTRUM_ADDRESS
        if pool_type == "erg":
            pool_token_bal = get_bal(pool_box, token_id)
            if token_name.lower() == "use":
                pool_addr = USE_CONFIG['pool_address']
                extra_requests.append({"address": USE_CONFIG['bank_address'], "value": 1000000000, "assets": [{"tokenId": USE_CONFIG['lp_nft'], "amount": 1}], "registers": {}})
            elif token_name.lower() == "dexygold":
                pool_addr = DEXYGOLD_CONFIG['pool_address']
            if order_type.lower() == "buy":
                nerg_to_send = int(amount_dec * Decimal("1000000000"))
                req_erg = nerg_to_send + fee_nano
                if my_nanoerg < req_erg:
                    raise ValueError(f"Insufficient ERG. Have {my_nanoerg / 1e9:.4f}, Need {req_erg / 1e9:.4f}")
                fee_mult = Decimal("1") - Decimal(str(cfg['fee']))
                erg_amm = int(Decimal(nerg_to_send) * fee_mult)
                delta, _, _ = amm.buy_token(erg_amm, pool_nanoerg, pool_token_bal)
                tokens_to_pool_list = [{"tokenId": token_id, "amount": -int(delta)}]
                nerg_to_pool = nerg_to_send
            elif order_type.lower() == "sell":
                token_amt = int(amount_dec * (Decimal("10") ** cfg['dec']))
                if my_assets.get(token_id, 0) < token_amt:
                    raise ValueError(f"Insufficient Tokens. Have {my_assets.get(token_id, 0) / (10**cfg['dec']):.4f}, Need {amount}")
                erg_out, _, _, _ = amm.sell_token(token_amt, pool_nanoerg, pool_token_bal)
                fee_mult = Decimal("1") - Decimal(str(cfg['fee']))
                erg_received = int(Decimal(str(erg_out)) * fee_mult)
                tokens_to_pool_list = [{"tokenId": token_id, "amount": token_amt}]
                nerg_to_pool = -erg_received
        elif pool_type == "token":
            pool_addr = SPECTRUM_TOKEN_ADDRESS
            tid_x, tid_y = cfg.get('id_in'), cfg.get('id_out')
            dec_x, dec_y = cfg.get('dec_in', 0), cfg.get('dec_out', 0)
            pool_bal_x, pool_bal_y = get_bal(pool_box, tid_x), get_bal(pool_box, tid_y)
            if order_type.lower() == "sell":
                amt_in_x = int(amount_dec * (Decimal("10") ** dec_x))
                if my_assets.get(tid_x, 0) < amt_in_x:
                    raise ValueError("Insufficient Base Token")
                delta_y = int(amm.token_for_token(amt_in_x, pool_bal_x, pool_bal_y, cfg['fee']))
                tokens_to_pool_list = [{"tokenId": tid_x, "amount": amt_in_x}, {"tokenId": tid_y, "amount": -delta_y}]
            elif order_type.lower() == "buy":
                amt_in_y = int(amount_dec * (Decimal("10") ** dec_y))
                if my_assets.get(tid_y, 0) < amt_in_y:
                    raise ValueError("Insufficient Quote Token")
                delta_x = int(amm.token_for_token(amt_in_y, pool_bal_y, pool_bal_x, cfg['fee']))
                tokens_to_pool_list = [{"tokenId": tid_y, "amount": amt_in_y}, {"tokenId": tid_x, "amount": -delta_x}]
        input_ids = [pool_box['boxId']]
        if bank_box:
            input_ids.append(bank_box['boxId'])
        input_ids.extend([b['boxId'] for b in user_boxes])
        inputs_raw = self.client.get_box_bytes(input_ids)


        from .ergpy_signer import ErgoSigner
        p_delta = ErgoSigner.get_ergopay_protocol_context(nerg_to_pool)
        auth_link = getattr(self, '_auth_link', None) # Passed from app.py

        tx_dict = self.builder.build_swap_tx(inputs_raw=inputs_raw, pool_box=pool_box, user_nanoerg_in=my_nanoerg, user_assets_in=my_assets, nerg_to_pool=nerg_to_pool, tokens_to_pool=tokens_to_pool_list, pool_address=pool_addr, mining_fee=fee_nano, p_delta=p_delta, auth_link=auth_link, registers={"R4": cfg['R4']}, extra_requests=extra_requests)
        tx_dict["inputIds"] = input_ids
        
        input_boxes = [pool_box]
        if bank_box:
            input_boxes.append(bank_box)
        input_boxes.extend(user_boxes)
        tx_dict["input_boxes"] = input_boxes
        
        return tx_dict
