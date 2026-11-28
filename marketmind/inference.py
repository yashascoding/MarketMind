from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from tensorflow import keras

from marketmind.pipeline import (
    DATA_DIR,
    FEATURE_COLUMNS,
    HMM_FEATURE_COLUMNS,
    engineer_features,
    load_aligned_prices,
)
from marketmind.portfolio import allocation_response, choose_allocation
from marketmind.regimes import causal_hmm_filter

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "models"
REPORT_DIR = ROOT / "reports"
BASELINE_ALLOCATION = {"nifty50": 60.0, "gold": 20.0, "bonds": 20.0}


class ModelService:
    def __init__(self, model_dir: Path = MODEL_DIR, data_dir: Path = DATA_DIR):
        metadata_path = model_dir / "model_metadata.json"
        required = (
            metadata_path,
            model_dir / "lstm_risk_model.keras",
            model_dir / "feature_imputer.pkl",
            model_dir / "feature_scaler.pkl",
            model_dir / "hmm_regime_model.pkl",
            model_dir / "baseline_models.pkl",
        )
        missing = [str(path) for path in required if not path.is_file()]
        if missing:
            raise FileNotFoundError(
                "Trained artifacts are missing. Run `python -m marketmind.train` first. "
                f"Missing: {', '.join(missing)}"
            )
        self.metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        self.lstm = keras.models.load_model(model_dir / "lstm_risk_model.keras")
        self.feature_imputer = joblib.load(model_dir / "feature_imputer.pkl")
        self.feature_scaler = joblib.load(model_dir / "feature_scaler.pkl")
        hmm_bundle = joblib.load(model_dir / "hmm_regime_model.pkl")
        self.hmm = hmm_bundle["model"]
        self.hmm_scaler = hmm_bundle["scaler"]
        self.state_names = hmm_bundle["state_names"]
        self.baseline_bundle = joblib.load(model_dir / "baseline_models.pkl")
        self.sequence_length = int(self.metadata["lstm"]["sequence_length"])
        self.risk_threshold = float(self.metadata["lstm"]["decision_threshold"])
        self.data_dir = data_dir

    def snapshot(self) -> dict[str, Any]:
        prices = load_aligned_prices(self.data_dir)
        features = engineer_features(prices)
        if len(features) < self.sequence_length:
            raise ValueError("Not enough recent observations to create a model input.")

        scaled_features = self.feature_scaler.transform(
            self.feature_imputer.transform(features[FEATURE_COLUMNS])
        ).astype("float32")
        sequences = np.expand_dims(
            scaled_features[-self.sequence_length :], axis=0
        )
        risk_probability = float(self.lstm.predict(sequences, verbose=0)[0, 0])

        hmm_features = features[HMM_FEATURE_COLUMNS].dropna()
        if hmm_features.empty or hmm_features.index[-1] != features.index[-1]:
            raise ValueError("Latest observation is missing required HMM regime features.")
        hmm_input = self.hmm_scaler.transform(
            hmm_features.to_numpy(dtype="float64")
        )
        regime_probabilities = causal_hmm_filter(self.hmm, hmm_input)[-1]
        state = int(regime_probabilities.argmax())
        regime = self.state_names[state]
        regime_confidence = float(regime_probabilities[state])
        date = features.index[-1]
        history = self._risk_history(features, scaled_features)
        return {
            "date": date,
            "risk_probability": risk_probability,
            "risk_threshold": self.risk_threshold,
            "risk_level": (
                "Elevated" if risk_probability >= self.risk_threshold else "Normal"
            ),
            "regime": regime,
            "regime_confidence": regime_confidence,
            "regime_probabilities": {
                self.state_names[int(index)]: float(value)
                for index, value in enumerate(regime_probabilities)
            },
            "history": history,
        }

    def _risk_history(
        self, features: pd.DataFrame, scaled_features: np.ndarray
    ) -> list[dict[str, Any]]:
        path = REPORT_DIR / "test_predictions.csv"
        entries: dict[pd.Timestamp, float] = {}
        if path.is_file():
            saved = pd.read_csv(path, parse_dates=["date"])
            for row in saved.itertuples(index=False):
                entries[pd.Timestamp(row.date)] = float(row.risk_probability)

        latest_feature_date = features.index[-1]
        last_saved = max(entries) if entries else None
        if last_saved is None or latest_feature_date > last_saved:
            new_dates = [
                (index, date)
                for index, date in enumerate(features.index)
                if (last_saved is None or date > last_saved)
                and index >= self.sequence_length - 1
            ]
            if new_dates:
                sequences = np.stack(
                    [
                        scaled_features[index - self.sequence_length + 1 : index + 1]
                        for index, _ in new_dates
                    ]
                )
                probabilities = self.lstm.predict(sequences, verbose=0).reshape(-1)
                entries.update(
                    {
                        date: float(probability)
                        for (_, date), probability in zip(new_dates, probabilities)
                    }
                )

        start = latest_feature_date - pd.Timedelta(days=365)
        return [
            {
                "timestamp": int(date.replace(tzinfo=timezone.utc).timestamp() * 1000),
                "probability": probability * 100,
            }
            for date, probability in sorted(entries.items())
            if date >= start
        ]

    def risk_response(self) -> dict[str, Any]:
        result = self.snapshot()
        return {
            "risk_probability": result["risk_probability"] * 100,
            "risk_threshold": result["risk_threshold"] * 100,
            "risk_level": result["risk_level"],
            "explanation": (
                f"Estimated probability of a NIFTY 50 10-session future drawdown of at "
                f"least 5% or annualized realized volatility of at least 30%. "
                f"Validation-selected threshold: {result['risk_threshold']:.1%}. "
                "The class-weighted sigmoid score is not calibrated; this is a research estimate, not investment advice."
            ),
            "timestamp": result["date"].date().isoformat(),
            "history": result["history"],
        }

    def regime_response(self) -> dict[str, Any]:
        result = self.snapshot()
        return {
            "regime": result["regime"],
            "confidence": result["regime_confidence"] * 100,
            "description": (
                f"Three-state Gaussian HMM; states mapped using training-period "
                f"NIFTY returns and volatility. Current filtered posterior confidence "
                f"is {result['regime_confidence']:.1%}."
            ),
            "timestamp": result["date"].date().isoformat(),
            "probabilities": result["regime_probabilities"],
        }

    def portfolio_response(
        self, current: dict[str, float] | None = None
    ) -> dict[str, Any]:
        result = self.snapshot()
        strategy, suggested, explanation = choose_allocation(
            result["risk_probability"],
            result["risk_threshold"],
            result["regime"],
            result["regime_confidence"],
        )
        source = current or BASELINE_ALLOCATION
        response = allocation_response(source, suggested, explanation, strategy)
        response.update(
            {
                "risk_probability": result["risk_probability"] * 100,
                "risk_level": result["risk_level"],
                "regime": result["regime"],
                "regime_confidence": result["regime_confidence"] * 100,
                "timestamp": result["date"].date().isoformat(),
                "current_allocation_source": (
                    "user_supplied"
                    if current is not None
                    else "60/20/20 benchmark assumption; replace with actual holdings"
                ),
                "disclaimer": "Model output is a research prototype, not a guaranteed investment recommendation.",
            }
        )
        return response

    def market_overview(self) -> dict[str, Any]:
        prices = load_aligned_prices(self.data_dir)
        items = []
        definitions = (
            ("nifty50", "NIFTY 50", "NIFTY", "nifty", "Indices"),
            ("gold", "Gold ETF", "GOLDBEES", "goldbees", "Commodities"),
            ("bonds", "Indian gilt ETF", "LTGILTBEES", "gilt_etf", "Bonds"),
        )
        for asset_id, name, symbol, column, category in definitions:
            values = prices[column].dropna()
            if len(values) < 2:
                raise ValueError(f"Not enough recent source observations for {name}.")
            latest = float(values.iloc[-1])
            previous = float(values.iloc[-2])
            change = (latest / previous - 1) * 100
            items.append(
                {
                    "id": asset_id,
                    "name": name,
                    "symbol": symbol,
                    "price": latest,
                    "change_pct": change,
                    "trend": "up" if change > 0 else "down" if change < 0 else "neutral",
                    "category": category,
                }
            )
        return {
            "assets": items,
            "timestamp": prices.index[-1].date().isoformat(),
        }

    def analytics(self) -> dict[str, Any]:
        prediction_path = REPORT_DIR / "test_predictions.csv"
        backtest_path = REPORT_DIR / "test_backtest.csv"
        if not prediction_path.is_file() or not backtest_path.is_file():
            raise FileNotFoundError("Out-of-sample evaluation files are missing; retrain the models.")
        predictions = pd.read_csv(prediction_path, parse_dates=["date"])
        backtest = pd.read_csv(backtest_path, parse_dates=["return_date"])
        performance = [
            {
                "timestamp": int(row.return_date.replace(tzinfo=timezone.utc).timestamp() * 1000),
                "portfolio_return": (float(row.portfolio_equity) - 1) * 100,
                "benchmark_return": (float(row.benchmark_equity) - 1) * 100,
            }
            for row in backtest.itertuples(index=False)
        ]
        regime_counts = predictions["regime"].value_counts(normalize=True)
        regime_segments = [
            {"regime": regime, "percentage": float(regime_counts.get(regime, 0) * 100)}
            for regime in ("Calm", "Volatile", "Bearish")
        ]
        metrics = self.metadata["backtest"]["strategy"]
        return {
            "performance": performance,
            "sharpe_ratio": metrics["sharpe_ratio"],
            "max_drawdown": metrics["max_drawdown"] * 100,
            "volatility": metrics["annualized_volatility"] * 100,
            "insights": (
                f"Unseen test period {self.metadata['date_ranges']['test_start']} to "
                f"{self.metadata['date_ranges']['test_end']}: model strategy cumulative "
                f"return {metrics['cumulative_return']:.1%}, max drawdown "
                f"{metrics['max_drawdown']:.1%}, Sharpe {metrics['sharpe_ratio']:.2f}. "
                "Historical test results are not a guarantee of future performance."
            ),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "regime_segments": regime_segments,
            "historical_risk": [
                {
                    "timestamp": int(row.date.replace(tzinfo=timezone.utc).timestamp() * 1000),
                    "probability": float(row.risk_probability) * 100,
                    "actual_elevated_risk": int(row.actual_elevated_risk),
                }
                for row in predictions.itertuples(index=False)
            ],
            "model_performance": self.metadata["model_comparison"],
            "backtest": self.metadata["backtest"],
        }

    def model_performance(self) -> dict[str, Any]:
        return {
            "model_version": self.metadata["model_version"],
            "training_date_utc": self.metadata["training_date_utc"],
            "date_ranges": self.metadata["date_ranges"],
            "label": self.metadata["label"],
            "features": self.metadata["features"],
            "lstm": self.metadata["lstm"],
            "model_comparison": self.metadata["model_comparison"],
            "hmm": self.metadata["hmm"],
            "backtest": self.metadata["backtest"],
            "warnings": self.metadata["warnings"],
        }
