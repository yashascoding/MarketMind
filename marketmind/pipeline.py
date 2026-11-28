from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data" / "raw"
REPORT_DIR = ROOT / "reports"

ASSET_FILES = {
    "brent": "brent.csv",
    "dxy": "dxy.csv",
    "gilt_etf": "gilt_etf.csv",
    "gilt_fund": "gilt_fund.csv",
    "gold": "gold.csv",
    "goldbees": "goldbees.csv",
    "india_vix": "india_vix.csv",
    "nifty": "nifty.csv",
    "niftybees": "niftybees.csv",
    "nse_gsec_10y": "nse_gsec_10y_clean_price.csv",
    "sp500": "sp500.csv",
    "tlt": "tlt.csv",
    "us_10y": "us_10y.csv",
    "us_vix": "us_vix.csv",
    "usdinr": "usdinr.csv",
}
PRICE_COLUMNS = {
    "gilt_etf": "Adj Close",
    "gilt_fund": "nav",
    "goldbees": "Adj Close",
    "niftybees": "Adj Close",
    "tlt": "Adj Close",
}
LAGGED_ASSETS = {
    "brent",
    "dxy",
    "gold",
    "gilt_fund",
    "sp500",
    "tlt",
    "us_10y",
    "us_vix",
    "usdinr",
}
ALIGNMENT_TOLERANCE_DAYS = 7
PRICE_SPIKE_MIN_MOVE = 0.80
PRICE_SPIKE_REVERSAL_TOLERANCE = 0.10

FEATURE_COLUMNS = [
    feature
    for asset in ASSET_FILES
    for feature in (
        f"daily_return_{asset}",
        f"log_return_{asset}",
        f"volatility_7d_{asset}",
        f"volatility_30d_{asset}",
        f"ma50_ratio_{asset}",
        f"ma200_ratio_{asset}",
        f"drawdown_{asset}",
    )
]
FEATURE_COLUMNS += [
    f"corr_30d_nifty_{asset}" for asset in ASSET_FILES if asset != "nifty"
]
FEATURE_COLUMNS += [
    "india_vix_level",
    "us_vix_level",
    "us_10y_level",
    "global_mean_log_return",
    "global_volatility_7d",
]

HMM_FEATURE_COLUMNS = [
    "log_return_nifty",
    "volatility_7d_nifty",
    "drawdown_nifty",
    "global_mean_log_return",
    "global_volatility_7d",
]
LABEL_HORIZON = 10
DRAWDOWN_THRESHOLD = -0.05
ANNUALIZED_VOLATILITY_THRESHOLD = 0.30


def _read_market_csv(path: Path, keep_duplicates: bool = False) -> pd.DataFrame:
    """Read flat NSE CSVs and yfinance two-row headers without losing columns."""
    with path.open("r", encoding="utf-8-sig") as source:
        first_line = source.readline()
    if first_line.startswith("Price,"):
        data = pd.read_csv(path, header=[0, 1])
        date_values = data.iloc[:, 0]
        data.columns = data.columns.get_level_values(0)
        data = data.iloc[:, 1:]
    else:
        data = pd.read_csv(path)
        date_column = next(
            (column for column in data.columns if str(column).lower() == "date"),
            data.columns[0],
        )
        date_values = data.pop(date_column)

    data.index = pd.to_datetime(date_values, format="%Y-%m-%d", errors="coerce")
    data.index.name = "date"
    data = data.loc[~data.index.isna()].sort_index()
    if not keep_duplicates:
        data = data.loc[~data.index.duplicated(keep="last")]
    return data


def _remove_isolated_reversal_spikes(
    prices: pd.Series,
) -> tuple[pd.Series, int]:
    """Mark short extreme price dislocations followed by a near-complete reversal."""
    prices = prices.sort_index().loc[~prices.index.duplicated(keep="last")]
    previous = prices.shift(1)
    spikes = pd.Series(False, index=prices.index)
    for run_length in (1, 2, 3):
        baseline = previous
        displaced = pd.Series(True, index=prices.index)
        for offset in range(run_length):
            displaced &= (
                (prices.shift(-offset) / baseline - 1).abs()
                >= PRICE_SPIKE_MIN_MOVE
            )
        reversal = (prices.shift(-run_length) / baseline - 1).abs()
        run_starts = displaced & (reversal <= PRICE_SPIKE_REVERSAL_TOLERANCE)
        for offset in range(run_length):
            spikes |= run_starts.shift(offset, fill_value=False)
    return prices.mask(spikes), int(spikes.sum())


def inspect_datasets(data_dir: Path = DATA_DIR) -> list[dict[str, Any]]:
    """Summarize every actual CSV, including the source schema and data-quality counts."""
    records: list[dict[str, Any]] = []
    asset_by_file = {filename: asset for asset, filename in ASSET_FILES.items()}
    for path in sorted(data_dir.glob("*.csv")):
        data = _read_market_csv(path, keep_duplicates=True)
        asset = asset_by_file.get(path.name)
        spike_count = 0
        if asset is not None:
            price_column = PRICE_COLUMNS.get(asset, "Close")
            if price_column in data.columns:
                unique_data = data.loc[~data.index.duplicated(keep="last")]
                prices = pd.to_numeric(unique_data[price_column], errors="coerce")
                prices = prices.replace([np.inf, -np.inf], np.nan)
                prices = prices.where(prices > 0)
                _, spike_count = _remove_isolated_reversal_spikes(prices)
        records.append(
            {
                "file": path.name,
                "rows": int(len(data)),
                "columns": ["date", *[str(column) for column in data.columns]],
                "dtypes": {
                    "date": "datetime64[ns]",
                    **{str(column): str(dtype) for column, dtype in data.dtypes.items()},
                },
                "missing_by_column": {
                    "date": 0,
                    **{
                        str(column): int(count)
                        for column, count in data.isna().sum().items()
                    },
                },
                "duplicate_dates_removed": int(data.index.duplicated().sum()),
                "duplicate_records": int(data.reset_index().duplicated().sum()),
                "isolated_reversal_prices_removed": spike_count,
                "date_start": (
                    data.index.min().date().isoformat() if not data.empty else None
                ),
                "date_end": (
                    data.index.max().date().isoformat() if not data.empty else None
                ),
            }
        )
    return records


def load_aligned_prices(data_dir: Path = DATA_DIR) -> pd.DataFrame:
    """Align all source prices to the NIFTY calendar without extending stale quotes."""
    series: dict[str, pd.Series] = {}
    for asset, filename in ASSET_FILES.items():
        path = data_dir / filename
        if not path.is_file():
            raise FileNotFoundError(f"Required source dataset is missing: {path}")
        source = _read_market_csv(path)
        preferred = PRICE_COLUMNS.get(asset, "Close")
        if preferred not in source.columns:
            raise ValueError(f"{filename} must contain a {preferred!r} column")
        values = pd.to_numeric(source[preferred], errors="coerce")
        values = values.replace([np.inf, -np.inf], np.nan)
        values = values.where(values > 0)
        values, _ = _remove_isolated_reversal_spikes(values)
        series[asset] = values.dropna().rename(asset)

    calendar = series["nifty"].dropna().index
    if calendar.empty:
        raise ValueError("The NIFTY source contains no valid positive closing prices.")
    tolerance = pd.Timedelta(days=ALIGNMENT_TOLERANCE_DAYS)
    aligned: dict[str, pd.Series] = {}
    for asset, values in series.items():
        if asset == "nifty":
            aligned[asset] = values.reindex(calendar)
        else:
            aligned[asset] = values.reindex(calendar, method="ffill", tolerance=tolerance)
    prices = pd.DataFrame(aligned, index=calendar).replace([np.inf, -np.inf], np.nan)
    prices.index.name = "date"
    return prices


def engineer_features(prices: pd.DataFrame) -> pd.DataFrame:
    """Create close-of-day features only; no future observations enter these columns."""
    prices = prices.sort_index().loc[~prices.index.duplicated(keep="last")]
    feature_prices = prices.copy()
    for asset in LAGGED_ASSETS:
        feature_prices[asset] = feature_prices[asset].shift(1)
    returns = feature_prices.pct_change(fill_method=None)
    log_returns = np.log(feature_prices.where(feature_prices > 0)).diff()
    features: dict[str, pd.Series] = {}
    for asset in ASSET_FILES:
        features[f"daily_return_{asset}"] = returns[asset]
        features[f"log_return_{asset}"] = log_returns[asset]
        for window in (7, 30):
            features[f"volatility_{window}d_{asset}"] = (
                log_returns[asset].rolling(window, min_periods=window).std()
                * np.sqrt(252)
            )
        for window in (50, 200):
            average = feature_prices[asset].rolling(window, min_periods=window).mean()
            features[f"ma{window}_ratio_{asset}"] = feature_prices[asset] / average - 1
        features[f"drawdown_{asset}"] = (
            feature_prices[asset] / feature_prices[asset].cummax() - 1
        )

    for asset in ASSET_FILES:
        if asset != "nifty":
            features[f"corr_30d_nifty_{asset}"] = returns["nifty"].rolling(
                30, min_periods=30
            ).corr(returns[asset])
    features["india_vix_level"] = feature_prices["india_vix"] / 100
    features["us_vix_level"] = feature_prices["us_vix"] / 100
    features["us_10y_level"] = feature_prices["us_10y"]
    features["global_mean_log_return"] = log_returns.mean(axis=1)
    features["global_volatility_7d"] = pd.concat(
        {
            asset: features[f"volatility_7d_{asset}"]
            for asset in ASSET_FILES
        },
        axis=1,
    ).mean(axis=1)
    feature_frame = pd.DataFrame(features, index=prices.index)
    feature_frame = feature_frame.replace([np.inf, -np.inf], np.nan)
    return feature_frame.loc[:, FEATURE_COLUMNS]


def make_future_risk_labels(
    prices: pd.DataFrame, features: pd.DataFrame
) -> pd.DataFrame:
    """Flag a 10-session forward drawdown of 5% or annualized realized volatility >=30%."""
    nifty_return = prices["nifty"].pct_change(fill_method=None)
    future_returns = nifty_return.shift(-1)

    def worst_path_drawdown(window: np.ndarray) -> float:
        path = np.concatenate(([1.0], np.cumprod(1.0 + window)))
        peaks = np.maximum.accumulate(path)
        return float(np.min(path / peaks - 1.0))

    future_drawdown = (
        future_returns.rolling(LABEL_HORIZON)
        .apply(worst_path_drawdown, raw=True)
        .shift(-(LABEL_HORIZON - 1))
    )
    future_volatility = (
        future_returns.rolling(LABEL_HORIZON)
        .std()
        .shift(-(LABEL_HORIZON - 1))
        * np.sqrt(252)
    )

    result = features.copy()
    result["future_drawdown_10d"] = future_drawdown
    result["future_volatility_10d"] = future_volatility
    result["elevated_risk"] = (
        (future_drawdown <= DRAWDOWN_THRESHOLD)
        | (future_volatility >= ANNUALIZED_VOLATILITY_THRESHOLD)
    ).astype("float")
    result.loc[future_drawdown.isna() | future_volatility.isna(), "elevated_risk"] = np.nan
    return result


def write_dataset_report(
    data_dir: Path = DATA_DIR, output_dir: Path = REPORT_DIR
) -> dict[str, Any]:
    """Write a reproducible inspection report from the local source files."""
    records = inspect_datasets(data_dir)
    prices = load_aligned_prices(data_dir)
    coverage = {
        asset: {
            "file": filename,
            "usable_nifty_dates": int(prices[asset].notna().sum()),
            "coverage_fraction": float(prices[asset].notna().mean()),
            "first_aligned_date": (
                prices[asset].first_valid_index().date().isoformat()
                if prices[asset].notna().any()
                else None
            ),
            "last_aligned_date": (
                prices[asset].last_valid_index().date().isoformat()
                if prices[asset].notna().any()
                else None
            ),
        }
        for asset, filename in ASSET_FILES.items()
    }
    report = {
        "datasets": records,
        "model_inputs": {
            asset: (
                f"data/raw/{filename} "
                f"({PRICE_COLUMNS.get(asset, 'Close')} price series)"
            )
            for asset, filename in ASSET_FILES.items()
        },
        "alignment": {
            "method": (
                "NIFTY trading dates are the master calendar. Other source prices are "
                f"backward-as-of aligned for at most {ALIGNMENT_TOLERANCE_DAYS} calendar "
                "days; pre-inception, stale, and unavailable observations remain missing."
            ),
            "aligned_rows": int(len(prices)),
            "date_start": prices.index.min().date().isoformat(),
            "date_end": prices.index.max().date().isoformat(),
            "duplicate_dates": 0,
            "source_coverage": coverage,
            "incomplete_master_dates_dropped": False,
        },
        "decisions": [
            "Original data/raw CSVs are read-only inputs; preprocessing writes separate reports and artifacts.",
            "All 15 local CSVs are model inputs. NIFTY Close defines the target calendar and labels; other sources are retained as independent features even when their histories overlap or have different start/end dates.",
            "Duplicate source dates keep the final source row; invalid and nonpositive prices remain unavailable rather than being fabricated. Adjusted Close is used for ETF datasets where supplied; gilt_fund uses NAV.",
            f"One to three consecutive source observations are marked unavailable only when each moves at least {PRICE_SPIKE_MIN_MOVE:.0%} from the preceding price and the following observation is within {PRICE_SPIKE_REVERSAL_TOLERANCE:.0%} of that preceding price. Counts are included in the dataset audit; original CSVs are unchanged.",
            "Cross-market quotes are backward-as-of aligned only when no more than seven calendar days old. No values are extended before a source begins or after its latest fresh observation.",
            "Foreign-market closes, USD/INR, and historical gilt-fund NAV features are lagged by one NIFTY session so that information published after the Indian close cannot leak into same-day predictions.",
            "Missing engineered features are median-imputed with missingness indicators fitted on training dates only; the same saved imputer and scaler are used at inference.",
            "Returns, 7/30-session volatility, 50/200-session moving-average ratios, drawdowns, and NIFTY cross-asset correlations are engineered for every source; full trailing windows are required and never backfilled.",
            "HMM features include NIFTY return, volatility and drawdown plus cross-asset aggregate return and volatility from all available sources.",
            "The 10-session target is elevated if the future path's maximum drawdown is at most -5% OR future annualized realized volatility is at least 30%.",
            "Sequences are labeled at their last observed close and forecast the next ten sessions; chronological splits purge ten labels at each boundary.",
            "All dates in the NIFTY source timeline from 2007 through 2026 are retained; chronological partitions remain separate, with test dates held out for final evaluation.",
            "Feature imputers and scalers are fit on the training partition only; validation selects hyperparameters and thresholds, and test is evaluated once.",
            "Backtest allocations use the signal available at close t for close-to-close return t+1, with 10 bps applied to one-way turnover.",
        ],
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "data_inspection.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8"
    )
    lines = [
        "# Dataset inspection",
        "",
        "Generated from the checked-in local CSV files by `python -m marketmind.train`.",
        "",
        "| File | Rows | Date range | Duplicate dates | Duplicate records | Isolated reversal prices removed | Missing values by column | Columns (dtype) |",
        "|---|---:|---|---:|---:|---:|---|---|",
    ]
    for item in records:
        missing = ", ".join(
            f"{column}: {count}"
            for column, count in item["missing_by_column"].items()
            if count
        ) or "none"
        columns = ", ".join(
            f"{column} ({dtype})" for column, dtype in item["dtypes"].items()
        )
        lines.append(
            f"| `{item['file']}` | {item['rows']} | "
            f"{item['date_start']} - {item['date_end']} | "
            f"{item['duplicate_dates_removed']} | {item['duplicate_records']} | "
            f"{item['isolated_reversal_prices_removed']} | "
            f"{missing} | {columns} |"
        )
    lines.extend(["", "## Model assets", ""])
    lines.extend(f"- {key}: {value}" for key, value in report["model_inputs"].items() if isinstance(value, str))
    lines.extend(["", "## NIFTY-calendar source coverage", ""])
    lines.append(
        "| Source | NIFTY dates with fresh observations | Coverage | First aligned | Last aligned |"
    )
    lines.append("|---|---:|---:|---|---|")
    for asset, item in coverage.items():
        lines.append(
            f"| `{asset}` | {item['usable_nifty_dates']}/{len(prices)} | "
            f"{item['coverage_fraction']:.1%} | {item['first_aligned_date']} | "
            f"{item['last_aligned_date']} |"
        )
    lines.extend(["", "## Preprocessing decisions", ""])
    lines.extend(f"- {decision}" for decision in report["decisions"])
    (output_dir / "data_inspection.md").write_text(
        "\n".join(lines) + "\n", encoding="utf-8"
    )
    return report
