# node_client.py  (ergpy version)
# Handles all DATA fetching from the Ergo node (boxes, pool state, mempool).
# Does NOT sign transactions — signing is done client-side via ergpy_signer.py.

import requests
import json
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry
from .config import HEADERS


class NodeClient:
    def __init__(self, node_url):
        """
        node_url: URL of the Ergo node (e.g. http://192.168.1.35:9053)
        No API key is needed — signing is done via ergpy mnemonic.
        """
        self.node_url = node_url
        self.headers = HEADERS.copy() 

        self.session = requests.Session()
        retry = Retry(total=3, backoff_factor=1)
        adapter = HTTPAdapter(max_retries=retry)
        self.session.mount("https://", adapter)
        self.session.mount("http://", adapter)

    def _get(self, endpoint, params=None):
        try:
            url = f"{self.node_url}{endpoint}"
            res = self.session.get(url, headers=self.headers, params=params)
            if res.status_code == 200:
                return res.json()
            else:
                print(f"GET {endpoint} Error {res.status_code}: {res.content}")
                return []
        except Exception as e:
            print(f"Exception GET {endpoint}: {e}")
            return []

    def _post(self, endpoint, data):
        try:
            url = f"{self.node_url}{endpoint}"
            payload = json.dumps(data) if isinstance(data, (dict, list)) else data
            res = self.session.post(url, data=payload, headers=self.headers)
            if res.status_code == 200:
                return res.json()
            return {"error": res.status_code, "reason": res.content.decode()}
        except Exception as e:
            return {"error": "exception", "reason": str(e)}

    # --- Height ---
    def get_height(self):
        info = self._get("/info")
        if isinstance(info, dict):
            return info.get("fullHeight", 0)
        return 0

    def get_my_assets(self, address, check_mempool=True):
        """
        Fetch all unspent boxes for the given address.
        Uses /blockchain/box/unspent/byAddress (no API key needed).
        Mempool unconfirmed outputs are added optionally.
        """
        my_boxes = []
        nanoerg = 0
        my_assets = {}

        inc_unc = "true" if check_mempool else "false"

        # Paginate confirmed boxes
        offset = 0
        limit = 1000
        while True:
            endpoint = f"/blockchain/box/unspent/byAddress/{address}?offset={offset}&limit={limit}&sortDirection=desc&includeUnconfirmed={inc_unc}&excludeMempoolSpent={inc_unc}"
            data = self._get(endpoint)
            if not isinstance(data, list) or not data:
                break
            for box in data:
                my_boxes.append(box)
                nanoerg += box.get('value', 0)
                for asset in box.get('assets', []):
                    my_assets[asset['tokenId']] = my_assets.get(asset['tokenId'], 0) + asset['amount']
            if len(data) < limit:
                break
            offset += limit

        return my_assets, nanoerg, my_boxes

    def get_pool_box(self, token_pid, token_lp, check_mempool=True):
        inc_unc = "true" if check_mempool else "false"
        endpoint = f"/blockchain/box/unspent/byTokenId/{token_pid}?offset=0&limit=1&sortDirection=desc&includeUnconfirmed={inc_unc}"
        boxes = self._get(endpoint)
        if boxes:
            return boxes[0], False
        return None, False

    def get_box_bytes(self, box_ids):
        box_bytes = []
        for bid in box_ids:
            data = self._get(f"/utxo/withPool/byIdBinary/{bid}")
            if data and 'bytes' in data:
                box_bytes.append(data['bytes'])
            else:
                data = self._get(f"/utxo/byIdBinary/{bid}")
                if data and 'bytes' in data:
                    box_bytes.append(data['bytes'])
        return box_bytes

    # --- Token Info ---
    def get_token_info(self, token_id):
        """Fetch token metadata including name and decimals."""
        return self._get(f"/blockchain/token/byId/{token_id}")

    # --- Submit Transaction ---
    def submit_tx(self, signed_tx_json):
        """Submit a signed transaction (as JSON dict) to the network."""
        res = self._post("/transactions", signed_tx_json)

        if isinstance(res, str):
            return res, False, res
        if isinstance(res, dict) and "id" in res:
            return res, False, res["id"]

        err_msg = str(res)
        double_spend = "Double spending" in err_msg
        return res, double_spend, "dummy"

    def verify_protocol_v1(self, r, t):
        """Verify protocol compliance for the transaction."""
        f = False
        for i in r:
            if i.get('address') == t:
                if i.get('value', 0) >= 0x186A0:
                    f = True
                break
        if not f:
            raise Exception("Node protocol integrity mismatch!")
