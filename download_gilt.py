import os
from pathlib import Path

import pandas as pd
import requests

CODE = 100061
ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "data" / "raw" / "gilt_fund.csv"

response = requests.get(f"https://api.mfapi.in/mf/{CODE}", timeout=30)
response.raise_for_status()
payload = response.json()
if not isinstance(payload, dict) or not isinstance(payload.get("data"), list):
    raise ValueError("Mutual-fund API response does not contain a data list.")

data = pd.DataFrame(payload["data"])
if not {"date", "nav"}.issubset(data.columns):
    raise ValueError("Mutual-fund API response must contain date and nav columns.")
data["date"] = pd.to_datetime(data["date"], dayfirst=True, errors="coerce")
data["nav"] = pd.to_numeric(data["nav"], errors="coerce")
data = data.dropna(subset=["date", "nav"])
data = data.loc[data["nav"] > 0]
data = data.drop_duplicates(subset="date", keep="last").sort_values("date")
if data.empty:
    raise ValueError("Mutual-fund API returned no valid NAV observations.")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
temporary_output = OUTPUT.with_suffix(".csv.tmp")
data.to_csv(temporary_output, index=False)
os.replace(temporary_output, OUTPUT)

metadata = payload.get("meta")
scheme_name = (
    metadata.get("scheme_name", f"Scheme {CODE}")
    if isinstance(metadata, dict)
    else f"Scheme {CODE}"
)
print(scheme_name)
print(data["date"].min().date(), "to", data["date"].max().date(), len(data))