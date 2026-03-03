# trade_mapper.py
from dataclasses import dataclass
from typing import Optional

ERG = "ERG"

@dataclass
class TradeRoute:
    token_key: str      
    order_type: str     
    pool_type: str      

class TradeMapper:
    def __init__(self, tokens: dict):
        self.tokens = tokens
        self._erg_tokens = [k for k in tokens if not self._is_pair_key(k)]
        self._pair_tokens = [k for k in tokens if self._is_pair_key(k)]
        self._pair_sides: dict[str, tuple[str, str]] = {}  
        for key in self._pair_tokens:
            parts = key.split("-", 1)
            if len(parts) == 2:
                out_asset, in_asset = parts[0], parts[1]
                self._pair_sides[key] = (out_asset, in_asset)

    def all_assets(self) -> list:
        seen = set()
        seen.add(ERG)
        for k in self._erg_tokens:
            seen.add(k)
        for out_asset, in_asset in self._pair_sides.values():
            seen.add(out_asset)
            seen.add(in_asset)
        others = sorted(a for a in seen if a != ERG)
        return [ERG] + others

    def normalize_asset(self, asset_name: str) -> str:
        if not asset_name:
            return asset_name
        upper = asset_name.upper()
        if upper == "ERG":
            return ERG
        for asset in self.all_assets():
            if asset.upper() == upper:
                return asset
        return asset_name  

    def to_assets_for(self, from_asset: str) -> list:
        reachable = set()
        fa = self.normalize_asset(from_asset)
        if fa == ERG:
            for k in self._erg_tokens:
                reachable.add(k)
        elif fa in self._erg_tokens:
            reachable.add(ERG)
        for key, (out_asset, in_asset) in self._pair_sides.items():
            if fa == in_asset:
                reachable.add(out_asset)
            elif fa == out_asset:
                reachable.add(in_asset)
        if fa in self._erg_tokens:
            reachable.add(ERG)
        reachable.discard(fa)
        return sorted(reachable)

    def resolve(self, from_asset: str, to_asset: str) -> Optional[TradeRoute]:
        fa = self.normalize_asset(from_asset)
        ta = self.normalize_asset(to_asset)
        if fa == ERG and ta in self._erg_tokens:
            return TradeRoute(token_key=ta, order_type="BUY", pool_type="erg")
        if ta == ERG and fa in self._erg_tokens:
            return TradeRoute(token_key=fa, order_type="SELL", pool_type="erg")
        for key, (out_asset, in_asset) in self._pair_sides.items():
            if fa == in_asset and ta == out_asset:
                return TradeRoute(token_key=key, order_type="BUY", pool_type="token")
            if fa == out_asset and ta == in_asset:
                return TradeRoute(token_key=key, order_type="SELL", pool_type="token")
        return None  

    def describe_route(self, route: TradeRoute, amount: str, expected: str) -> str:
        # Pattern: {ReceivedAmount} {ReceivedAsset} for {SentAmount} {SentAsset}
        if route.pool_type == "erg":
            if route.order_type == "BUY":
                return f"{expected} {route.token_key} for {amount} ERG"
            else:
                return f"{expected} ERG for {amount} {route.token_key}"
        else:
            out_asset, in_asset = self._pair_sides.get(route.token_key, (route.token_key, "???"))
            if route.order_type == "BUY":
                return f"{expected} {out_asset} for {amount} {in_asset}"
            else:
                return f"{expected} {in_asset} for {amount} {out_asset}"

    @staticmethod
    def _is_pair_key(key: str) -> bool:
        return "-" in key
