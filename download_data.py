import os
import yfinance as yf

os.makedirs("data/raw", exist_ok=True)

tickers = {
    "nifty": "^NSEI",
    "india_vix": "^INDIAVIX",
    "us_vix": "^VIX",
    "sp500": "^GSPC",
    "usdinr": "INR=X",
    "dxy": "DX-Y.NYB",
    "brent": "BZ=F",
    "gold": "GC=F",
    "gilt_etf": "LTGILTBEES.NS",   # Indian long-term gilt ETF (bond proxy)
    "goldbees": "GOLDBEES.NS",     # Indian gold ETF (INR gold proxy)
    "niftybees": "NIFTYBEES.NS",   # tradable Nifty proxy
    "us_10y": "^TNX",              # US 10Y yield
    "tlt": "TLT",                  # US 20Y+ Treasury ETF (long history)
}

for name, ticker in tickers.items():
    df = yf.download(ticker, start="2007-01-01", auto_adjust=False)
    df.to_csv(f"data/raw/{name}.csv")
    print(name, df.shape)