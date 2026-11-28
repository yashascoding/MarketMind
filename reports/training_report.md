# Market crisis model evaluation

- Model version: `1.1.0`
- Training date (UTC): 2026-11-27T16:23:23.940525+00:00
- Source datasets used: 15 (all local CSVs)
- Labeled years used for final deployment refit: 2007 to 2026
- Final deployment LSTM label range: 2007-12-11 to 2026-11-12
- Final deployment HMM feature range ends: 2026-11-27
- Train: 2007-12-11 to 2021-02-10
- Validation: 2021-02-25 to 2023-12-18
- Unseen test: 2024-01-03 to 2026-11-12
- Test metrics and backtest are computed before the post-evaluation full-history deployment refit.
- Positive target rate: train 22.3%; validation 9.4%; test 6.5%
- LSTM sequence length: 60 trading sessions
- Validation-selected risk threshold: 0.4348

## Chronological test metrics

| Model | Accuracy | Precision | Recall | F1 | ROC-AUC | PR-AUC | Brier |
|---|---:|---:|---:|---:|---:|---:|---:|
| LSTM | 0.762 | 0.123 | 0.435 | 0.192 | 0.658 | 0.105 | 0.160 |
| Logistic Regression | 0.065 | 0.065 | 1.000 | 0.123 | 0.633 | 0.103 | 0.929 |
| Random Forest | 0.068 | 0.065 | 1.000 | 0.123 | 0.451 | 0.074 | 0.114 |

## Test-period backtest

| Strategy | Cumulative return | Max drawdown | Sharpe | Annualized volatility |
|---|---:|---:|---:|---:|
| Model strategy | 32.32% | -8.84% | 1.224 | 8.47% |
| Fixed 60/20/20 | 33.89% | -11.78% | 1.076 | 10.18% |

Transaction cost: 10 bps per unit of one-way turnover.

## HMM states

| Regime | Observations | Mean daily NIFTY log return | Mean annualized 7-day volatility |
|---|---:|---:|---:|
| Calm | 990 | 0.00098 | 0.098 |
| Volatile | 1630 | 0.00017 | 0.157 |
| Bearish | 652 | -0.00019 | 0.377 |

## Bearish-regime test days

| Portfolio | Conditional cumulative return | Conditional max drawdown |
|---|---:|---:|
| Model strategy | 4.98% | -6.94% |
| Fixed 60/20/20 | 10.98% | -8.02% |

These conditional summaries compound only returns on dates whose close-time HMM signal was Bearish; they are not a contiguous holding-period return.

Confusion matrices use row order actual [Normal, Elevated] and column order predicted [Normal, Elevated].
The `confusion_matrices.png` artifact plots each unseen-test confusion matrix; numeric counts are included in `model_evaluation.json`.
The `calibration_curve.png` artifact and Brier score summarize the class-weighted sigmoid score's test-set calibration; probabilities are not calibrated.

## Limitations observed from this run

- Research prototype; historical results do not guarantee future performance.
- The three-asset backtest uses NIFTY returns, GoldBeES when available (otherwise gold futures converted with USD/INR), and gilt ETF returns (otherwise historical gilt-fund NAV); proxy changes and source-specific tracking differences limit comparability.
- Backtest uses daily close observations and a fixed 10 bps one-way turnover cost; taxes, liquidity, slippage, and fund tracking error are not modeled.
- The backtest starts with a 60/20/20 portfolio and uses close-t signals for the next close-to-close return.
- The class-weighted sigmoid output is an uncalibrated risk score; Brier score and a test calibration curve are reported.
- The final deployment artifacts are refit on all labeled observations through the latest available year after the untouched test evaluation. Reported test metrics are from the pre-refit model; in-sample historical scores from the full-data artifact are not independent performance evidence.
- The preprocessing audit marked isolated extreme reversal records as unavailable without changing source files: {'goldbees.csv': 2, 'niftybees.csv': 2}.
- At the validation-selected F2 threshold, the LSTM produced 142 false elevated-risk alerts on the unseen test period; precision is limited.
- The model-adjusted strategy had lower cumulative return than the fixed 60/20/20 benchmark on this unseen test period.

This is a research prototype, not investment advice. It does not guarantee returns.
