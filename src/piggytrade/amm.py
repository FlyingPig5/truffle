# amm.py
from decimal import Decimal, getcontext
getcontext().prec = 100

def buy_token(amount, erg_pool, token_amount_full):
    initial_erg = erg_pool
    initial_token = token_amount_full
    k = Decimal(initial_erg * initial_token)
    erg_new = Decimal(initial_erg + amount)
    token_new = Decimal(k / erg_new)
    token_delta = initial_token - token_new
    return (token_delta, token_new, erg_new)

def sell_token(tokens_to_sell, pool_nanoerg, token_balance):
    tokens_to_sell = Decimal(tokens_to_sell)
    pool_nanoerg = Decimal(pool_nanoerg)
    token_balance = Decimal(token_balance)
    k = pool_nanoerg * token_balance
    token_new = token_balance + tokens_to_sell
    erg_new = k / token_new
    erg_delta = pool_nanoerg - erg_new
    return (int(erg_delta), int(tokens_to_sell), int(token_new), int(erg_new))

def token_for_token(tokens_to_sell, tx_in_balance, tx_out_balance, fee_percentage):
    effective = tokens_to_sell * (1 - fee_percentage)
    new_tx_in_balance = tx_in_balance + effective
    tx_out_amount = tx_out_balance - (tx_out_balance * tx_in_balance) / new_tx_in_balance
    return tx_out_amount
