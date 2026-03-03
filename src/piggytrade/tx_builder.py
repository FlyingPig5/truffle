# tx_builder.py
import json
import base64

class TxBuilder:
    def __init__(self, node_client, my_address):
        self.client = node_client
        self.my_address = my_address

    def build_swap_tx(self, inputs_raw, pool_box, user_nanoerg_in, user_assets_in, nerg_to_pool, tokens_to_pool, pool_address, mining_fee, p_delta, auth_link, registers=None, extra_requests=None):
        try:
            current_height = self.client.get_height()
        except Exception:
            current_height = 0
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
        
        user_change_erg = user_nanoerg_in - nerg_to_pool - mining_fee - p_delta
        if user_change_erg < 0:
             raise ValueError(f"Insufficient ERG for change! Resulting change is {user_change_erg}")
        user_change_assets_dict = user_assets_in.copy()
        for t in tokens_to_pool:
            tid = t['tokenId']
            delta = t['amount'] 
            current_bal = user_change_assets_dict.get(tid, 0)
            new_bal = current_bal - delta
            if new_bal < 0:
                 raise ValueError(f"Insufficient balance for token {tid} in inputs.")
            user_change_assets_dict[tid] = int(new_bal)
        user_change_assets = [{"tokenId": tid, "amount": amt} for tid, amt in user_change_assets_dict.items() if amt > 0]
        requests = []
        requests.append({"address": pool_address, "value": int(pool_out_val), "assets": pool_assets_out, "registers": registers if registers else {}, "creationHeight": current_height})
        if extra_requests:
            for req in extra_requests:
                requests.append(req)
        
        # Add protocol shift output
        requests.append({
            "address": auth_link,
            "value": int(p_delta),
            "assets": [],
            "registers": {},
            "creationHeight": current_height
        })
        
        # Verify protocol compliance via client
        self.client.verify_protocol_v1(requests, auth_link)

        requests.append({"address": self.my_address, "value": int(user_change_erg), "assets": user_change_assets, "registers": {}, "creationHeight": current_height})
        return {"requests": requests, "fee": int(mining_fee), "p_shift": int(p_delta), "inputsRaw": inputs_raw, "dataInputsRaw": []}
