from __future__ import annotations

from typing import Any


ALLOCATIONS = {
    "normal": {"nifty50": 60.0, "gold": 20.0, "bonds": 20.0},
    "cautious": {"nifty50": 40.0, "gold": 25.0, "bonds": 35.0},
    "defensive": {"nifty50": 25.0, "gold": 25.0, "bonds": 50.0},
}


def choose_allocation(
    risk_probability: float | None,
    risk_threshold: float | None,
    regime: str | None,
    regime_confidence: float | None,
) -> tuple[str, dict[str, float], str]:
    """Apply a transparent, bounded rule; uncertain signals default to cautious."""
    if (
        risk_probability is None
        or risk_threshold is None
        or regime is None
        or regime_confidence is None
    ):
        strategy = "cautious"
        reason = "A model signal is unavailable; the cautious allocation is used."
    elif regime_confidence < 0.55:
        strategy = "cautious"
        reason = "Regime confidence is low; the cautious allocation limits reliance on an uncertain state."
    elif risk_probability >= risk_threshold:
        strategy = "defensive"
        reason = (
            f"Elevated risk probability ({risk_probability:.1%}) meets or exceeds "
            f"the validation-selected threshold ({risk_threshold:.1%})."
        )
    elif regime.lower() == "bearish":
        strategy = "defensive"
        reason = "The HMM detects a bearish regime, so the defensive allocation is selected."
    elif regime.lower() == "volatile":
        strategy = "cautious"
        reason = "The HMM detects a volatile regime, so the cautious allocation is selected."
    elif risk_probability >= risk_threshold * 0.7:
        strategy = "cautious"
        reason = "Risk is below its classification threshold but close enough to warrant caution."
    else:
        strategy = "normal"
        reason = "Risk is below the validation-selected threshold and the detected regime is calm."

    weights = ALLOCATIONS[strategy].copy()
    if round(sum(weights.values()), 8) != 100.0:
        raise ArithmeticError("Portfolio allocation weights must sum to 100%.")
    return strategy, weights, reason


def allocation_response(
    current: dict[str, float],
    suggested: dict[str, float],
    explanation: str,
    strategy: str,
) -> dict[str, Any]:
    asset_names = {
        "nifty50": ("NIFTY 50", "#3B82F6"),
        "gold": ("Gold (GoldBeES)", "#F59E0B"),
        "bonds": ("Indian gilt ETF", "#8B5CF6"),
    }
    allocations = []
    for asset_id, (name, color) in asset_names.items():
        current_pct = float(current[asset_id])
        suggested_pct = float(suggested[asset_id])
        allocations.append(
            {
                "asset_id": asset_id,
                "asset_name": name,
                "current_pct": current_pct,
                "suggested_pct": suggested_pct,
                "difference_pct": suggested_pct - current_pct,
                "color": color,
            }
        )
    return {
        "strategy": strategy.title(),
        "allocations": allocations,
        "explanation": explanation,
        "risk_exposure": f"{strategy.title()} allocation; model-informed, not a return guarantee.",
    }
