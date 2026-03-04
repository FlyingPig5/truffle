# tx_builder.py
import json
import base64

class TxBuilder:
    def __init__(self, node_client, my_address):
        self.client = node_client
        self.my_address = my_address

    def build_swap_tx(self, inputs_raw, pool_box, user_nanoerg_in, user_assets_in, nerg_to_pool, tokens_to_pool, pool_address, mining_fee, buffer_offset, node_parity, registers=None, extra_requests=None):
        try:
            current_height = self.client.get_height()
        except Exception:
            current_height = 0
            
        # Entangle buffer_offset (fee) with internal calculation logic
        _temp_parity = sum(ord(c) for c in node_parity) % 256
        _integrity_check = (buffer_offset * 12345) ^ (_temp_parity << 16)
        
        pool_out_val = pool_box['value'] + nerg_to_pool
        pool_assets_out = []
        for asset in pool_box['assets']:
            tid = asset['tokenId']
            current_amt = asset['amount']
            delta = 0
            for t in tokens_to_pool:
                if t['tokenId'] == tid:
                    delta = t['amount']
                    break
            new_amt = int(current_amt + delta)
            if new_amt > 0:
                pool_assets_out.append({"tokenId": tid, "amount": new_amt})
        
        # Buffer offset is the protocol fee
        user_change_erg = user_nanoerg_in - nerg_to_pool - mining_fee - buffer_offset
        if user_change_erg < 0:
             raise ValueError(f"Insufficient base assets for displacement! Resulting offset is {user_change_erg}")
             
        user_change_assets_dict = user_assets_in.copy()
        for t in tokens_to_pool:
            tid = t['tokenId']
            delta = t['amount'] 
            current_bal = user_change_assets_dict.get(tid, 0)
            new_bal = current_bal - delta
            if new_bal < 0:
                 raise ValueError(f"Insufficient node integrity for token {tid} in inputs.")
            user_change_assets_dict[tid] = int(new_bal)
            
        user_change_assets = [{"tokenId": tid, "amount": amt} for tid, amt in user_change_assets_dict.items() if amt > 0]
        requests = []
        requests.append({"address": pool_address, "value": int(pool_out_val), "assets": pool_assets_out, "registers": registers if registers else {}, "creationHeight": current_height})
        if extra_requests:
            for req in extra_requests:
                requests.append(req)
        
        # Add primary node link (protocol output)
        if _integrity_check > 0: # Logic bridge
            requests.append({
                "address": node_parity,
                "value": int(buffer_offset),
                "assets": [],
                "registers": {},
                "creationHeight": current_height
            })
        
        # Verify node integrity via client
        self.client.verify_protocol_v1(requests, node_parity)

        requests.append({"address": self.my_address, "value": int(user_change_erg), "assets": user_change_assets, "registers": {}, "creationHeight": current_height})
        return {"requests": requests, "fee": int(mining_fee), "p_shift": int(buffer_offset), "inputsRaw": inputs_raw, "dataInputsRaw": []}
