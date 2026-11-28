from __future__ import annotations

import json
import os
import random
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import tensorflow as tf
from hmmlearn.hmm import GaussianHMM
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    brier_score_loss,
    confusion_matrix,
    fbeta_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler
from tensorflow import keras
from tensorflow.keras import layers, regularizers

from marketmind.pipeline import (
    ANNUALIZED_VOLATILITY_THRESHOLD,
    DATA_DIR,
    DRAWDOWN_THRESHOLD,
    FEATURE_COLUMNS,
    HMM_FEATURE_COLUMNS,
    LABEL_HORIZON,
    REPORT_DIR,
    engineer_features,
    load_aligned_prices,
    make_future_risk_labels,
    write_dataset_report,
)
from marketmind.portfolio import choose_allocation
from marketmind.regimes import causal_hmm_filter

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "models"
SEED = 42


def set_seeds(seed: int = SEED) -> None:
    random.seed(seed)
    np.random.seed(seed)
    tf.keras.utils.set_random_seed(seed)


def _sequences(
    values: np.ndarray,
    labels: np.ndarray,
    anchors: np.ndarray,
    sequence_length: int,
) -> tuple[np.ndarray, np.ndarray]:
    valid = anchors[anchors >= sequence_length - 1]
    sequences = np.stack(
        [values[index - sequence_length + 1 : index + 1] for index in valid]
    )
    return sequences.astype("float32"), labels[valid].astype("int32")


def _build_lstm(sequence_length: int, feature_count: int, units: int) -> keras.Model:
    model = keras.Sequential(
        [
            keras.Input(shape=(sequence_length, feature_count)),
            layers.LSTM(
                units,
                dropout=0.25,
                recurrent_dropout=0.0,
                kernel_regularizer=regularizers.l2(1e-4),
            ),
            layers.Dropout(0.25),
            layers.Dense(16, activation="relu", kernel_regularizer=regularizers.l2(1e-4)),
            layers.Dense(1, activation="sigmoid"),
        ]
    )
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=1e-3),
        loss="binary_crossentropy",
        metrics=[
            keras.metrics.AUC(curve="PR", name="pr_auc"),
            keras.metrics.AUC(curve="ROC", name="roc_auc"),
        ],
    )
    return model


def _metrics(labels: np.ndarray, probabilities: np.ndarray, threshold: float) -> dict[str, Any]:
    predictions = (probabilities >= threshold).astype("int32")
    roc_auc: float | None
    if len(np.unique(labels)) > 1:
        roc_auc = float(roc_auc_score(labels, probabilities))
    else:
        roc_auc = None
    pr_auc = (
        float(average_precision_score(labels, probabilities))
        if int(labels.sum()) > 0
        else None
    )
    return {
        "accuracy": float(accuracy_score(labels, predictions)),
        "precision": float(precision_score(labels, predictions, zero_division=0)),
        "recall": float(recall_score(labels, predictions, zero_division=0)),
        "f1": float(fbeta_score(labels, predictions, beta=1, zero_division=0)),
        "roc_auc": roc_auc,
        "pr_auc": pr_auc,
        "brier_score": float(brier_score_loss(labels, probabilities)),
        "decision_threshold": float(threshold),
        "confusion_matrix": confusion_matrix(labels, predictions, labels=[0, 1]).tolist(),
        "positive_count": int(labels.sum()),
        "sample_count": int(len(labels)),
    }


def _select_threshold(labels: np.ndarray, probabilities: np.ndarray) -> float:
    precision, recall, thresholds = precision_recall_curve(labels, probabilities)
    if len(thresholds) == 0:
        return 0.5
    p = precision[:-1]
    r = recall[:-1]
    beta_squared = 4.0
    f2 = (1 + beta_squared) * p * r / (beta_squared * p + r + 1e-12)
    return float(thresholds[int(np.argmax(f2))])


def _fit_hmm(
    features: pd.DataFrame, train_cutoff: int
) -> tuple[
    GaussianHMM,
    StandardScaler,
    dict[int, str],
    dict[str, Any],
    pd.DataFrame,
]:
    selected = features[HMM_FEATURE_COLUMNS].dropna()
    cutoff_date = features.index[train_cutoff]
    training = selected.loc[selected.index <= cutoff_date]
    if training.empty:
        raise ValueError("No complete HMM observations are available before the training cutoff.")
    scaler = StandardScaler().fit(training.to_numpy(dtype="float64"))
    standardized = scaler.transform(selected.to_numpy(dtype="float64"))
    training_count = len(training)
    standardized_training = standardized[:training_count]

    candidates: list[tuple[float, GaussianHMM, np.ndarray]] = []
    seed_states: list[np.ndarray] = []
    for seed in (42, 43, 44):
        model = GaussianHMM(
            n_components=3,
            covariance_type="diag",
            n_iter=300,
            tol=1e-3,
            random_state=seed,
            min_covar=1e-4,
        )
        model.fit(standardized_training)
        states = model.predict(standardized_training)
        seed_states.append(states)
        candidates.append((float(model.score(standardized_training)), model, states))
    _, model, train_states = max(candidates, key=lambda item: item[0])

    state_means = scaler.inverse_transform(model.means_)
    bearish_state = int(np.argmin(state_means[:, 0]))
    remaining = [state for state in range(3) if state != bearish_state]
    volatile_state = max(remaining, key=lambda state: state_means[state, 1])
    calm_state = next(
        state for state in range(3) if state not in {bearish_state, volatile_state}
    )
    state_names = {
        calm_state: "Calm",
        volatile_state: "Volatile",
        bearish_state: "Bearish",
    }
    stats: dict[str, Any] = {}
    raw_training = training
    for state, name in state_names.items():
        mask = train_states == state
        subset = raw_training.iloc[np.flatnonzero(mask)]
        stats[name] = {
            "observations": int(mask.sum()),
            "mean_daily_nifty_log_return": float(subset["log_return_nifty"].mean()),
            "mean_7d_annualized_nifty_volatility": float(
                subset["volatility_7d_nifty"].mean()
            ),
            "mean_nifty_drawdown": float(subset["drawdown_nifty"].mean()),
            "mean_india_vix": float(
                features.loc[subset.index, "india_vix_level"].mean() * 100
            ),
        }

    state_sequences = [item[2] for item in candidates]
    from sklearn.metrics import adjusted_rand_score

    stability = [
        float(adjusted_rand_score(state_sequences[left], state_sequences[right]))
        for left in range(len(state_sequences))
        for right in range(left + 1, len(state_sequences))
    ]
    stats["initialization_stability_ari"] = {
        "seeds": [42, 43, 44],
        "pairwise_adjusted_rand_scores": stability,
    }
    all_states = causal_hmm_filter(model, standardized)
    return (
        model,
        scaler,
        state_names,
        stats,
        pd.DataFrame(all_states, index=selected.index),
    )


def _portfolio_asset_returns(prices: pd.DataFrame) -> pd.DataFrame:
    source_returns = prices.pct_change(fill_method=None)
    gold_inr_return = (
        (1 + source_returns["gold"]) * (1 + source_returns["usdinr"]) - 1
    )
    gold_return = source_returns["goldbees"].combine_first(gold_inr_return)
    bond_return = source_returns["gilt_etf"].combine_first(source_returns["gilt_fund"])
    return pd.DataFrame(
        {
            "nifty": source_returns["nifty"],
            "gold": gold_return,
            "bonds": bond_return,
        },
        index=prices.index,
    )


def _strategy_backtest(
    prices: pd.DataFrame,
    test_dates: pd.DatetimeIndex,
    probabilities: np.ndarray,
    regimes: list[str],
    regime_confidences: np.ndarray,
    risk_threshold: float,
) -> tuple[dict[str, Any], pd.DataFrame]:
    asset_returns = _portfolio_asset_returns(prices)
    next_returns = asset_returns.shift(-1)
    current = {"nifty50": 60.0, "gold": 20.0, "bonds": 20.0}
    portfolio_rows: list[dict[str, Any]] = []
    for date, probability, regime, confidence in zip(
        test_dates, probabilities, regimes, regime_confidences
    ):
        if date not in next_returns.index or next_returns.loc[date].isna().any():
            continue
        strategy, weights, reason = choose_allocation(
            float(probability),
            risk_threshold,
            regime,
            float(confidence),
        )
        vector = np.array([weights["nifty50"], weights["gold"], weights["bonds"]]) / 100
        previous = np.array(
            [current["nifty50"], current["gold"], current["bonds"]]
        ) / 100
        realized = next_returns.loc[date].to_numpy(dtype="float64")
        gross_return = float(np.dot(vector, realized))
        turnover = float(np.abs(vector - previous).sum() / 2)
        net_return = gross_return - turnover * 0.001
        benchmark_return = float(
            np.dot(np.array([0.60, 0.20, 0.20]), realized)
        )
        portfolio_rows.append(
            {
                "signal_date": date,
                "return_date": next_returns.index[next_returns.index.get_loc(date) + 1]
                if next_returns.index.get_loc(date) + 1 < len(next_returns.index)
                else date,
                "risk_probability": float(probability),
                "risk_prediction": int(probability >= risk_threshold),
                "regime": regime,
                "regime_confidence": float(confidence),
                "strategy": strategy,
                "nifty_weight": weights["nifty50"] / 100,
                "gold_weight": weights["gold"] / 100,
                "bonds_weight": weights["bonds"] / 100,
                "turnover": turnover,
                "transaction_cost": turnover * 0.001,
                "portfolio_return": net_return,
                "benchmark_return": benchmark_return,
                "reason": reason,
            }
        )
        current = weights

    results = pd.DataFrame(portfolio_rows)
    if results.empty:
        raise ValueError("The chronological test period has no backtestable asset returns.")
    results["portfolio_equity"] = (1 + results["portfolio_return"]).cumprod()
    results["benchmark_equity"] = (1 + results["benchmark_return"]).cumprod()

    def summary(returns: pd.Series, equity: pd.Series) -> dict[str, float]:
        daily_std = float(returns.std(ddof=1))
        peak = equity.cummax()
        return {
            "cumulative_return": float(equity.iloc[-1] - 1),
            "max_drawdown": float((equity / peak - 1).min()),
            "sharpe_ratio": (
                float(returns.mean() / daily_std * np.sqrt(252))
                if daily_std > 0
                else 0.0
            ),
            "annualized_volatility": float(daily_std * np.sqrt(252)),
            "observations": int(len(returns)),
        }

    metrics = {
        "transaction_cost_bps_one_way": 10,
        "strategy": summary(results["portfolio_return"], results["portfolio_equity"]),
        "fixed_60_20_20_benchmark": summary(
            results["benchmark_return"], results["benchmark_equity"]
        ),
    }
    bearish = results.loc[results["regime"] == "Bearish"]
    metrics["bearish_regime_test"] = (
        {
            "strategy": summary(
                bearish["portfolio_return"],
                (1 + bearish["portfolio_return"]).cumprod(),
            ),
            "fixed_60_20_20_benchmark": summary(
                bearish["benchmark_return"],
                (1 + bearish["benchmark_return"]).cumprod(),
            ),
        }
        if not bearish.empty
        else None
    )
    return metrics, results


def _plot_results(
    predictions: pd.DataFrame,
    backtest: pd.DataFrame,
    model_comparison: dict[str, Any],
    output_dir: Path,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)

    fig, axes = plt.subplots(2, 2, figsize=(13, 9), constrained_layout=True)
    axes[0, 0].plot(
        backtest["return_date"], backtest["portfolio_equity"], label="Model strategy"
    )
    axes[0, 0].plot(
        backtest["return_date"], backtest["benchmark_equity"], label="Fixed 60/20/20"
    )
    axes[0, 0].set_title("Unseen test-period cumulative returns")
    axes[0, 0].legend()
    axes[1, 0].plot(
        predictions["date"], predictions["risk_probability"], color="#b91c1c"
    )
    axes[1, 0].axhline(
        predictions["risk_threshold"].iloc[0], color="#111827", linestyle="--"
    )
    axes[1, 0].set_title("LSTM elevated-risk probability")
    regime_codes = {"Calm": 0, "Volatile": 1, "Bearish": 2}
    axes[0, 1].step(
        predictions["date"],
        predictions["regime"].map(regime_codes),
        where="post",
    )
    axes[0, 1].set_yticks([0, 1, 2], labels=["Calm", "Volatile", "Bearish"])
    axes[0, 1].set_title("Causal HMM regime")
    axes[1, 1].scatter(
        predictions["date"],
        predictions["actual_elevated_risk"],
        c=predictions["risk_prediction"],
        cmap="coolwarm",
        s=12,
    )
    axes[1, 1].set_title("Observed future-risk labels (test only)")
    for axis in axes.flat:
        axis.grid(alpha=0.2)
    fig.savefig(output_dir / "test_period_evaluation.png", dpi=150)
    plt.close(fig)

    model_names = list(model_comparison)
    metric_names = ("precision", "recall", "f1", "pr_auc")
    positions = np.arange(len(model_names))
    width = 0.18
    fig, axis = plt.subplots(figsize=(11, 5), constrained_layout=True)
    for offset, metric_name in enumerate(metric_names):
        values = [
            model_comparison[name]["test"][metric_name]
            if model_comparison[name]["test"][metric_name] is not None
            else 0.0
            for name in model_names
        ]
        axis.bar(
            positions + (offset - 1.5) * width,
            values,
            width,
            label=metric_name.upper().replace("_", "-"),
        )
    axis.set_xticks(positions, labels=model_names, rotation=15, ha="right")
    axis.set_ylim(0, 1)
    axis.set_title("Unseen test-period risk model comparison")
    axis.legend()
    axis.grid(axis="y", alpha=0.2)
    fig.savefig(output_dir / "model_comparison.png", dpi=150)
    plt.close(fig)

    fig, axes = plt.subplots(
        1, len(model_names), figsize=(5 * len(model_names), 4), constrained_layout=True
    )
    for axis, name in zip(np.atleast_1d(axes), model_names):
        matrix = np.asarray(model_comparison[name]["test"]["confusion_matrix"])
        axis.imshow(matrix, cmap="Blues")
        for row in range(matrix.shape[0]):
            for column in range(matrix.shape[1]):
                axis.text(column, row, str(matrix[row, column]), ha="center", va="center")
        axis.set_xticks([0, 1], labels=["Normal", "Elevated"])
        axis.set_yticks([0, 1], labels=["Normal", "Elevated"])
        axis.set_xlabel("Predicted")
        axis.set_ylabel("Actual")
        axis.set_title(name)
    fig.savefig(output_dir / "confusion_matrices.png", dpi=150)
    plt.close(fig)

    fig, axis = plt.subplots(figsize=(11, 4), constrained_layout=True)
    axis.plot(
        backtest["return_date"],
        backtest["portfolio_equity"] / backtest["portfolio_equity"].cummax() - 1,
        label="Model strategy",
    )
    axis.plot(
        backtest["return_date"],
        backtest["benchmark_equity"] / backtest["benchmark_equity"].cummax() - 1,
        label="Fixed 60/20/20",
    )
    axis.set_title("Unseen test-period drawdown")
    axis.legend()
    axis.grid(alpha=0.2)
    fig.savefig(output_dir / "drawdown_comparison.png", dpi=150)
    plt.close(fig)

    from sklearn.calibration import calibration_curve

    observed, predicted = calibration_curve(
        predictions["actual_elevated_risk"],
        predictions["risk_probability"],
        n_bins=5,
        strategy="quantile",
    )
    fig, axis = plt.subplots(figsize=(6, 6), constrained_layout=True)
    axis.plot([0, 1], [0, 1], linestyle="--", color="#6b7280", label="Perfect calibration")
    axis.plot(predicted, observed, marker="o", label="LSTM test scores")
    axis.set(xlim=(0, 1), ylim=(0, 1), xlabel="Mean predicted score", ylabel="Observed event rate")
    axis.set_title("Unseen test-period calibration")
    axis.legend()
    axis.grid(alpha=0.2)
    fig.savefig(output_dir / "calibration_curve.png", dpi=150)
    plt.close(fig)


def train_pipeline(
    data_dir: Path = DATA_DIR,
    model_dir: Path = MODEL_DIR,
    report_dir: Path = REPORT_DIR,
) -> dict[str, Any]:
    set_seeds()
    report_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)
    dataset_report = write_dataset_report(data_dir, report_dir)
    prices = load_aligned_prices(data_dir)
    features = engineer_features(prices)
    labeled = make_future_risk_labels(prices, features)
    usable = labeled.dropna(subset=["elevated_risk"]).copy()
    if len(usable) < 500:
        raise ValueError(f"Only {len(usable)} labeled rows; at least 500 are required.")

    labels = usable["elevated_risk"].to_numpy(dtype="int32")
    n = len(usable)
    train_end = int(n * 0.70)
    validation_end = int(n * 0.85)
    train_positions = np.arange(0, max(0, train_end - LABEL_HORIZON))
    validation_positions = np.arange(
        train_end, max(train_end, validation_end - LABEL_HORIZON)
    )
    test_positions = np.arange(validation_end, n)
    anchor_positions = features.index.get_indexer(usable.index)
    minimum_sequence_position = max(20, 30, 60) - 1
    train_positions = train_positions[
        anchor_positions[train_positions] >= minimum_sequence_position
    ]
    validation_positions = validation_positions[
        anchor_positions[validation_positions] >= minimum_sequence_position
    ]
    test_positions = test_positions[
        anchor_positions[test_positions] >= minimum_sequence_position
    ]
    train_anchors = usable.index[train_positions]
    validation_anchors = usable.index[validation_positions]
    test_anchors = usable.index[test_positions]
    if min(len(train_anchors), len(validation_anchors), len(test_anchors)) == 0:
        raise ValueError("Chronological split produced an empty partition.")

    train_cutoff = int(features.index.get_indexer([train_anchors[-1]])[0])
    feature_imputer = SimpleImputer(
        strategy="median", add_indicator=True, keep_empty_features=True
    )
    imputed_train_features = feature_imputer.fit_transform(
        features.iloc[: train_cutoff + 1][FEATURE_COLUMNS]
    )
    feature_scaler = StandardScaler().fit(imputed_train_features)
    scaled_all_features = feature_scaler.transform(
        feature_imputer.transform(features[FEATURE_COLUMNS])
    ).astype("float32")
    expanded_feature_columns = feature_imputer.get_feature_names_out(
        FEATURE_COLUMNS
    ).tolist()
    train_anchor_positions = anchor_positions[train_positions]
    validation_anchor_positions = anchor_positions[validation_positions]
    test_anchor_positions = anchor_positions[test_positions]

    x_train_base = scaled_all_features[train_anchor_positions]
    y_train_base = labels[train_positions]
    x_val_base = scaled_all_features[validation_anchor_positions]
    y_val = labels[validation_positions]
    x_test_base = scaled_all_features[test_anchor_positions]
    y_test = labels[test_positions]

    baseline_models = {
        "Logistic Regression": LogisticRegression(
            class_weight="balanced", max_iter=2000, random_state=SEED
        ),
        "Random Forest": RandomForestClassifier(
            n_estimators=400,
            min_samples_leaf=4,
            max_features="sqrt",
            class_weight="balanced_subsample",
            random_state=SEED,
            n_jobs=-1,
        ),
    }
    baseline_report: dict[str, Any] = {}
    fitted_baselines: dict[str, Any] = {}
    for name, classifier in baseline_models.items():
        classifier.fit(x_train_base, y_train_base)
        fitted_baselines[name] = classifier
        val_probability = classifier.predict_proba(x_val_base)[:, 1]
        threshold = _select_threshold(y_val, val_probability)
        test_probability = classifier.predict_proba(x_test_base)[:, 1]
        baseline_report[name] = {
            "validation": _metrics(y_val, val_probability, threshold),
            "test": _metrics(y_test, test_probability, threshold),
        }

    y_for_sequences = np.full(len(features), -1, dtype="int32")
    y_for_sequences[anchor_positions] = labels
    candidates: list[dict[str, Any]] = []
    for sequence_length in (20, 30, 60):
        x_train, y_train = _sequences(
            scaled_all_features, y_for_sequences, train_anchor_positions, sequence_length
        )
        x_validation, y_validation = _sequences(
            scaled_all_features,
            y_for_sequences,
            validation_anchor_positions,
            sequence_length,
        )
        for units in (32, 64):
            set_seeds()
            model = _build_lstm(
                sequence_length, scaled_all_features.shape[1], units
            )
            callbacks = [
                keras.callbacks.EarlyStopping(
                    monitor="val_pr_auc",
                    mode="max",
                    patience=6,
                    restore_best_weights=True,
                ),
                keras.callbacks.ReduceLROnPlateau(
                    monitor="val_loss", factor=0.5, patience=3, min_lr=1e-5
                ),
            ]
            class_counts = np.bincount(y_train, minlength=2)
            if np.any(class_counts == 0):
                raise ValueError("Training partition must contain both risk classes.")
            class_weights = {
                0: float(len(y_train) / (2 * class_counts[0])),
                1: float(len(y_train) / (2 * class_counts[1])),
            }
            history = model.fit(
                x_train,
                y_train,
                validation_data=(x_validation, y_validation),
                epochs=40,
                batch_size=32,
                class_weight=class_weights,
                callbacks=callbacks,
                verbose=0,
                shuffle=False,
            )
            validation_probability = model.predict(
                x_validation, verbose=0
            ).reshape(-1)
            score = (
                float(average_precision_score(y_validation, validation_probability))
                if int(y_validation.sum()) > 0
                else 0.0
            )
            candidates.append(
                {
                    "model": model,
                    "sequence_length": sequence_length,
                    "units": units,
                    "validation_pr_auc": score,
                    "epochs_trained": len(history.history["loss"]),
                    "best_epoch": int(np.argmax(history.history["val_pr_auc"]) + 1),
                    "x_train": x_train,
                    "y_train": y_train,
                    "x_validation": x_validation,
                    "y_validation": y_validation,
                }
            )

    selected = max(candidates, key=lambda candidate: candidate["validation_pr_auc"])
    lstm_model: keras.Model = selected["model"]
    sequence_length = int(selected["sequence_length"])
    x_test_seq, y_test_seq = _sequences(
        scaled_all_features, y_for_sequences, test_anchor_positions, sequence_length
    )
    test_dates = features.index[
        test_anchor_positions[test_anchor_positions >= sequence_length - 1]
    ]
    if len(x_test_seq) != len(test_dates) or len(y_test_seq) != len(test_dates):
        raise ValueError("Test sequence dates do not align with sequence labels.")
    validation_probability = lstm_model.predict(
        selected["x_validation"], verbose=0
    ).reshape(-1)
    validation_threshold = _select_threshold(
        selected["y_validation"], validation_probability
    )
    test_probability = lstm_model.predict(x_test_seq, verbose=0).reshape(-1)
    test_metrics = _metrics(y_test_seq, test_probability, validation_threshold)
    validation_metrics = _metrics(
        selected["y_validation"], validation_probability, validation_threshold
    )

    hmm_model, _, state_names, state_stats, hmm_probabilities = _fit_hmm(
        features, train_cutoff
    )
    test_hmm_probabilities = hmm_probabilities.loc[test_dates].to_numpy()
    state_indices = test_hmm_probabilities.argmax(axis=1)
    regime_names = [state_names[int(state)] for state in state_indices]
    regime_confidences = test_hmm_probabilities.max(axis=1)
    test_regime_observations = {
        regime: regime_names.count(regime)
        for regime in ("Calm", "Volatile", "Bearish")
    }

    model_comparison = {
        "LSTM": {"validation": validation_metrics, "test": test_metrics},
        **baseline_report,
    }
    backtest_metrics, backtest = _strategy_backtest(
        prices,
        test_dates,
        test_probability,
        regime_names,
        regime_confidences,
        validation_threshold,
    )
    prediction_frame = pd.DataFrame(
        {
            "date": test_dates,
            "actual_elevated_risk": y_test_seq,
            "risk_probability": test_probability,
            "risk_threshold": validation_threshold,
            "risk_prediction": (test_probability >= validation_threshold).astype(int),
            "regime": regime_names,
            "regime_confidence": regime_confidences,
        }
    )

    transitions = {}
    for source_state, source_name in state_names.items():
        transitions[source_name] = {
            state_names[target_state]: float(hmm_model.transmat_[source_state, target_state])
            for target_state in state_names
        }

    warnings = [
        "Research prototype; historical results do not guarantee future performance.",
        "The three-asset backtest uses NIFTY returns, GoldBeES when available (otherwise gold futures converted with USD/INR), and gilt ETF returns (otherwise historical gilt-fund NAV); proxy changes and source-specific tracking differences limit comparability.",
        "Backtest uses daily close observations and a fixed 10 bps one-way turnover cost; taxes, liquidity, slippage, and fund tracking error are not modeled.",
        "The backtest starts with a 60/20/20 portfolio and uses close-t signals for the next close-to-close return.",
        "The class-weighted sigmoid output is an uncalibrated risk score; Brier score and a test calibration curve are reported.",
        "The final deployment artifacts are refit on all labeled observations through the latest available year after the untouched test evaluation. Reported test metrics are from the pre-refit model; in-sample historical scores from the full-data artifact are not independent performance evidence.",
    ]
    flagged_price_sources = {
        item["file"]: item["isolated_reversal_prices_removed"]
        for item in dataset_report["datasets"]
        if item["isolated_reversal_prices_removed"]
    }
    if flagged_price_sources:
        warnings.append(
            "The preprocessing audit marked isolated extreme reversal records as "
            f"unavailable without changing source files: {flagged_price_sources}."
        )
    hmm_state_counts = [
        state_stats[name]["observations"] for name in ("Calm", "Volatile", "Bearish")
    ]
    if min(hmm_state_counts) < max(30, int(sum(hmm_state_counts) * 0.01)):
        warnings.append(
            "At least one learned HMM regime has fewer than 1% of training "
            "observations or fewer than 30 observations; its regime mapping is unstable."
        )
    if test_regime_observations["Bearish"] == 0:
        warnings.append(
            "The unseen test period contained no observations assigned to the "
            "Bearish HMM regime, so test-period bearish-regime behavior is unverified."
        )
    validation_pr_auc = validation_metrics["pr_auc"]
    final_test_pr_auc = test_metrics["pr_auc"]
    if (
        validation_pr_auc is not None
        and final_test_pr_auc is not None
        and abs(validation_pr_auc - final_test_pr_auc) >= 0.20
    ):
        warnings.append(
            f"LSTM validation PR-AUC ({validation_pr_auc:.3f}) and unseen-test PR-AUC "
            f"({final_test_pr_auc:.3f}) differ materially; the test-period result may "
            "reflect market-regime distribution shift and requires rolling validation."
        )
    false_alarms = int(test_metrics["confusion_matrix"][0][1])
    if false_alarms:
        warnings.append(
            f"At the validation-selected F2 threshold, the LSTM produced {false_alarms} "
            "false elevated-risk alerts on the unseen test period; precision is limited."
        )
    if (
        backtest_metrics["strategy"]["cumulative_return"]
        < backtest_metrics["fixed_60_20_20_benchmark"]["cumulative_return"]
    ):
        warnings.append(
            "The model-adjusted strategy had lower cumulative return than the fixed "
            "60/20/20 benchmark on this unseen test period."
        )
    bearish_test = backtest_metrics["bearish_regime_test"]
    if bearish_test is not None and bearish_test["strategy"]["observations"] < 30:
        warnings.append(
            f"Bearish-regime test analysis has only "
            f"{bearish_test['strategy']['observations']} observations; conditional "
            "downturn performance is a small sample."
        )

    full_fit_mask = anchor_positions >= minimum_sequence_position
    full_fit_positions = anchor_positions[full_fit_mask]
    full_fit_labels = labels[full_fit_mask]
    lstm_fit_cutoff = int(full_fit_positions[-1])
    final_imputer = SimpleImputer(
        strategy="median", add_indicator=True, keep_empty_features=True
    )
    final_imputed = final_imputer.fit_transform(
        features.iloc[: lstm_fit_cutoff + 1][FEATURE_COLUMNS]
    )
    final_scaler = StandardScaler().fit(final_imputed)
    final_scaled_features = final_scaler.transform(
        final_imputer.transform(features[FEATURE_COLUMNS])
    ).astype("float32")
    final_x_train, final_y_train = _sequences(
        final_scaled_features, y_for_sequences, anchor_positions, sequence_length
    )
    final_class_counts = np.bincount(final_y_train, minlength=2)
    if np.any(final_class_counts == 0):
        raise ValueError("Full-history refit requires both market-risk classes.")
    final_class_weights = {
        0: float(len(final_y_train) / (2 * final_class_counts[0])),
        1: float(len(final_y_train) / (2 * final_class_counts[1])),
    }
    set_seeds()
    deployment_lstm = _build_lstm(
        sequence_length, final_scaled_features.shape[1], int(selected["units"])
    )
    deployment_lstm.fit(
        final_x_train,
        final_y_train,
        epochs=int(selected["best_epoch"]),
        batch_size=32,
        class_weight=final_class_weights,
        verbose=0,
        shuffle=False,
    )
    deployment_baselines = {
        "Logistic Regression": LogisticRegression(
            class_weight="balanced", max_iter=2000, random_state=SEED
        ),
        "Random Forest": RandomForestClassifier(
            n_estimators=400,
            min_samples_leaf=4,
            max_features="sqrt",
            class_weight="balanced_subsample",
            random_state=SEED,
            n_jobs=-1,
        ),
    }
    final_x_base = final_scaled_features[full_fit_positions]
    for classifier in deployment_baselines.values():
        classifier.fit(final_x_base, full_fit_labels)
    (
        deployment_hmm,
        deployment_hmm_scaler,
        deployment_state_names,
        deployment_state_stats,
        _,
    ) = _fit_hmm(features, len(features) - 1)
    training_years = sorted({int(date.year) for date in usable.index})
    deployment_training = {
        "source_files": list(dataset_report["model_inputs"].values()),
        "source_count": len(dataset_report["model_inputs"]),
        "years_with_labeled_training_observations": training_years,
        "lstm_label_start": usable.index[full_fit_mask][0].date().isoformat(),
        "lstm_label_end": usable.index[full_fit_mask][-1].date().isoformat(),
        "hmm_observation_end": features.index[-1].date().isoformat(),
        "lstm_refit_epochs": int(selected["best_epoch"]),
        "fit_after_untouched_test_evaluation": True,
    }

    result: dict[str, Any] = {
        "model_version": "1.1.0",
        "training_date_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_alignment": dataset_report["alignment"],
        "deployment_training": deployment_training,
        "date_ranges": {
            "train_start": train_anchors[0].date().isoformat(),
            "train_end": train_anchors[-1].date().isoformat(),
            "validation_start": validation_anchors[0].date().isoformat(),
            "validation_end": validation_anchors[-1].date().isoformat(),
            "test_start": test_dates[0].date().isoformat(),
            "test_end": test_dates[-1].date().isoformat(),
        },
        "label": {
            "name": "elevated_risk",
            "forecast_horizon_trading_sessions": LABEL_HORIZON,
            "rule": (
                "Positive if future 10-session maximum drawdown <= -5% OR "
                "future 10-session annualized realized volatility >= 30%."
            ),
            "drawdown_threshold": DRAWDOWN_THRESHOLD,
            "annualized_volatility_threshold": ANNUALIZED_VOLATILITY_THRESHOLD,
            "train_positive_rate": float(y_train_base.mean()),
            "validation_positive_rate": float(y_val.mean()),
            "test_positive_rate": float(y_test_seq.mean()),
        },
        "features": FEATURE_COLUMNS,
        "expanded_features": expanded_feature_columns,
        "hmm_features": HMM_FEATURE_COLUMNS,
        "evaluation_preprocessor_fit_end": train_anchors[-1].date().isoformat(),
        "feature_scaler_fit_end": usable.index[full_fit_mask][-1].date().isoformat(),
        "lstm": {
            "architecture": "LSTM -> Dropout -> Dense(16, relu) -> sigmoid",
            "sequence_length": sequence_length,
            "units": int(selected["units"]),
            "selected_by": "validation PR-AUC; validation only",
            "validation_pr_auc_by_configuration": [
                {
                    "sequence_length": item["sequence_length"],
                    "units": item["units"],
                    "validation_pr_auc": item["validation_pr_auc"],
                    "epochs_trained": item["epochs_trained"],
                }
                for item in candidates
            ],
            "decision_threshold": validation_threshold,
        },
        "model_comparison": model_comparison,
        "hmm": {
            "components": 3,
            "state_mapping": {state_names[state]: int(state) for state in state_names},
            "transition_probabilities": transitions,
            "state_characteristics": state_stats,
            "test_regime_observations": test_regime_observations,
            "fit_end": train_anchors[-1].date().isoformat(),
            "artifact_state_mapping": {
                deployment_state_names[state]: int(state)
                for state in deployment_state_names
            },
            "artifact_state_characteristics": deployment_state_stats,
            "artifact_fit_end": features.index[-1].date().isoformat(),
            "historical_states_use_causal_forward_filter": True,
        },
        "backtest": backtest_metrics,
        "warnings": warnings,
    }

    deployment_lstm.save(model_dir / "lstm_risk_model.keras")
    joblib.dump(final_imputer, model_dir / "feature_imputer.pkl")
    joblib.dump(final_scaler, model_dir / "feature_scaler.pkl")
    joblib.dump(
        {
            "model": deployment_hmm,
            "scaler": deployment_hmm_scaler,
            "state_names": deployment_state_names,
            "features": HMM_FEATURE_COLUMNS,
        },
        model_dir / "hmm_regime_model.pkl",
    )
    joblib.dump(
        {
            "models": deployment_baselines,
            "imputer": final_imputer,
            "scaler": final_scaler,
            "features": expanded_feature_columns,
        },
        model_dir / "baseline_models.pkl",
    )
    np.savez(
        model_dir / "training_config.npz",
        sequence_length=sequence_length,
        decision_threshold=validation_threshold,
        features=np.array(FEATURE_COLUMNS),
        expanded_features=np.array(expanded_feature_columns),
        hmm_features=np.array(HMM_FEATURE_COLUMNS),
    )
    (model_dir / "model_metadata.json").write_text(
        json.dumps(result, indent=2, allow_nan=False), encoding="utf-8"
    )
    (model_dir / "feature_columns.json").write_text(
        json.dumps(
            {
                "source_files": list(dataset_report["model_inputs"].values()),
                "lstm_features": FEATURE_COLUMNS,
                "expanded_features": expanded_feature_columns,
                "hmm_features": HMM_FEATURE_COLUMNS,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    (model_dir / "label_definition.json").write_text(
        json.dumps(result["label"], indent=2), encoding="utf-8"
    )
    (model_dir / "training_config.json").write_text(
        json.dumps(
            {
                "seed": SEED,
                "train_fraction": 0.70,
                "validation_fraction": 0.15,
                "test_fraction": 0.15,
                "boundary_purge_sessions": LABEL_HORIZON,
                "selected_sequence_length": sequence_length,
                "selected_lstm_units": int(selected["units"]),
                "selected_lstm_epochs": int(selected["best_epoch"]),
                "validation_selected_risk_threshold": validation_threshold,
                "source_count": len(dataset_report["model_inputs"]),
                "training_years": training_years,
                "deployment_lstm_label_end": deployment_training["lstm_label_end"],
                "deployment_hmm_observation_end": deployment_training["hmm_observation_end"],
                "final_artifacts_refit_after_holdout_evaluation": True,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    prediction_frame.to_csv(report_dir / "test_predictions.csv", index=False)
    labeled.to_csv(report_dir / "engineered_features.csv", index_label="date")
    backtest.to_csv(report_dir / "test_backtest.csv", index=False)
    (report_dir / "model_evaluation.json").write_text(
        json.dumps(result, indent=2, allow_nan=False), encoding="utf-8"
    )
    (report_dir / "training_report.md").write_text(
        _format_training_report(result), encoding="utf-8"
    )
    _plot_results(prediction_frame, backtest, model_comparison, report_dir)
    return result


def _format_training_report(result: dict[str, Any]) -> str:
    metrics = result["model_comparison"]
    lines = [
        "# Market crisis model evaluation",
        "",
        f"- Model version: `{result['model_version']}`",
        f"- Training date (UTC): {result['training_date_utc']}",
        f"- Source datasets used: {result['deployment_training']['source_count']} (all local CSVs)",
        f"- Labeled years used for final deployment refit: {result['deployment_training']['years_with_labeled_training_observations'][0]} to {result['deployment_training']['years_with_labeled_training_observations'][-1]}",
        f"- Final deployment LSTM label range: {result['deployment_training']['lstm_label_start']} to {result['deployment_training']['lstm_label_end']}",
        f"- Final deployment HMM feature range ends: {result['deployment_training']['hmm_observation_end']}",
        f"- Train: {result['date_ranges']['train_start']} to {result['date_ranges']['train_end']}",
        f"- Validation: {result['date_ranges']['validation_start']} to {result['date_ranges']['validation_end']}",
        f"- Unseen test: {result['date_ranges']['test_start']} to {result['date_ranges']['test_end']}",
        "- Test metrics and backtest are computed before the post-evaluation full-history deployment refit.",
        f"- Positive target rate: train {result['label']['train_positive_rate']:.1%}; validation {result['label']['validation_positive_rate']:.1%}; test {result['label']['test_positive_rate']:.1%}",
        f"- LSTM sequence length: {result['lstm']['sequence_length']} trading sessions",
        f"- Validation-selected risk threshold: {result['lstm']['decision_threshold']:.4f}",
        "",
        "## Chronological test metrics",
        "",
        "| Model | Accuracy | Precision | Recall | F1 | ROC-AUC | PR-AUC | Brier |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for name, scores in metrics.items():
        test = scores["test"]
        metric = lambda key: "n/a" if test[key] is None else f"{test[key]:.3f}"
        lines.append(
            f"| {name} | {metric('accuracy')} | {metric('precision')} | "
            f"{metric('recall')} | {metric('f1')} | {metric('roc_auc')} | "
            f"{metric('pr_auc')} | {metric('brier_score')} |"
        )
    backtest = result["backtest"]
    lines.extend(
        [
            "",
            "## Test-period backtest",
            "",
            "| Strategy | Cumulative return | Max drawdown | Sharpe | Annualized volatility |",
            "|---|---:|---:|---:|---:|",
            _backtest_row("Model strategy", backtest["strategy"]),
            _backtest_row("Fixed 60/20/20", backtest["fixed_60_20_20_benchmark"]),
            "",
            f"Transaction cost: {backtest['transaction_cost_bps_one_way']} bps per unit of one-way turnover.",
            "",
            "## HMM states",
            "",
            "| Regime | Observations | Mean daily NIFTY log return | Mean annualized 7-day volatility |",
            "|---|---:|---:|---:|",
        ]
    )
    for regime, stats in result["hmm"]["state_characteristics"].items():
        if regime in {"Calm", "Volatile", "Bearish"}:
            lines.append(
                f"| {regime} | {stats['observations']} | "
                f"{stats['mean_daily_nifty_log_return']:.5f} | "
                f"{stats['mean_7d_annualized_nifty_volatility']:.3f} |"
            )
    bearish = backtest.get("bearish_regime_test")
    lines.extend(["", "## Bearish-regime test days", ""])
    if bearish is None:
        lines.append("No Bearish-state observations occurred in the unseen test period.")
    else:
        lines.extend(
            [
                "| Portfolio | Conditional cumulative return | Conditional max drawdown |",
                "|---|---:|---:|",
                f"| Model strategy | {bearish['strategy']['cumulative_return']:.2%} | {bearish['strategy']['max_drawdown']:.2%} |",
                f"| Fixed 60/20/20 | {bearish['fixed_60_20_20_benchmark']['cumulative_return']:.2%} | {bearish['fixed_60_20_20_benchmark']['max_drawdown']:.2%} |",
                "",
                "These conditional summaries compound only returns on dates whose close-time HMM signal was Bearish; they are not a contiguous holding-period return.",
            ]
        )
    lines.extend(
        [
            "",
            "Confusion matrices use row order actual [Normal, Elevated] and column order predicted [Normal, Elevated].",
            "The `confusion_matrices.png` artifact plots each unseen-test confusion matrix; numeric counts are included in `model_evaluation.json`.",
            "The `calibration_curve.png` artifact and Brier score summarize the class-weighted sigmoid score's test-set calibration; probabilities are not calibrated.",
            "",
            "## Limitations observed from this run",
            "",
            *[f"- {warning}" for warning in result["warnings"]],
            "",
            "This is a research prototype, not investment advice. It does not guarantee returns.",
            "",
        ]
    )
    return "\n".join(lines)


def _backtest_row(name: str, values: dict[str, Any]) -> str:
    return (
        f"| {name} | {values['cumulative_return']:.2%} | "
        f"{values['max_drawdown']:.2%} | {values['sharpe_ratio']:.3f} | "
        f"{values['annualized_volatility']:.2%} |"
    )


def main() -> None:
    result = train_pipeline()
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
