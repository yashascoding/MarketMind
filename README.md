# MarketMind research prototype

MarketMind now includes a Python training and inference service alongside the existing Android app. It trains from the local `data/raw/*.csv` files; the source CSVs are not modified. The application is a research prototype, not investment advice or a guarantee of returns.

## What data is used

All 15 CSVs in `data/raw/` contribute model features: Brent, DXY, LTGILTBEES, historical gilt-fund NAV, gold futures, GoldBeES, India VIX, NIFTY 50, NIFTY BeES, the 10-year GSec clean-price series, S&P 500, TLT, US 10-year yield, US VIX, and USD/INR. The dataset audit reports each file's schema, dtypes, row counts, date range, duplicates, missing values, and aligned coverage.

NIFTY 50 trading dates are the master calendar, spanning the available observations from 2007 through 2026. Other prices are aligned backward only when the latest quote is at most seven calendar days old. Pre-inception, stale, and unavailable prices remain missing; the pipeline never fabricates a common full history or drops an entire year to force an inner join. Foreign-market closes, USD/INR, and historical gilt-fund NAV features are lagged by one NIFTY session to avoid using information that was not available at the Indian close.

All source datasets are inputs, but they are not interchangeable investments. In particular, the 10-year GSec clean price is a feature, not a total-return bond investment. Historical backtesting uses NIFTY returns, GoldBeES where available (otherwise gold futures converted with USD/INR), and gilt ETF returns (otherwise the earlier gilt-fund NAV). Those proxy handoffs and their limitations are documented in the training report.

## Reproduce training

Use Python 3.12 or newer:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m marketmind.train
python -m unittest discover -s tests
```

Training writes model files and feature/label/training metadata under `models/`, and the inspection report, engineered feature/label table, metrics, out-of-sample predictions, backtest series, and plots under `reports/`. Run the training command again after replacing/updating source CSVs. No model is retrained when the API receives a request.

## Method

Features for all 15 sources include percentage and log returns, 7/30-session annualized volatility, 50/200-session moving-average ratios, drawdowns, and 30-session NIFTY cross-asset correlations. India VIX, US VIX, and US 10-year yield levels are included, along with cross-asset aggregate return and volatility for the HMM. Full rolling lookbacks are required. Missing feature values are median-imputed with explicit missingness indicators; the evaluation imputer and scaler are fit only on training dates and persisted for matching inference preprocessing.

The target at close *t* is elevated when the next ten trading sessions contain a maximum NIFTY drawdown of at least 5% or realize annualized volatility of at least 30%. Chronological train/validation/test partitions are 70/15/15 with ten labels purged at boundaries. Validation chooses among 20/30/60-day LSTM inputs and 32/64 units, and selects an F2 risk threshold; the test partition is used once for reported metrics and backtesting. Logistic Regression and Random Forest baselines use class weighting. LSTM training uses dropout, L2 regularization, early stopping, a learning-rate scheduler, and class weights. After this holdout evaluation is complete, deployment artifacts are refit on all labeled years in the available 2007–2026 history. Reported test metrics remain from the pre-refit models; historical scores from the full-history deployment model are not independent performance evidence. Because class weighting affects score calibration, sigmoid outputs are treated as uncalibrated risk scores; Brier scores and a test calibration plot are produced.

The 3-state diagonal Gaussian HMM is fit on standardized NIFTY return, trailing volatility, VIX, and drawdown features from the training window. Learned states are mapped by observed training characteristics, not state number. State transition probabilities and three-seed initialization agreement are recorded. Historical HMM probabilities use a causal forward filter rather than full-sequence smoothing.

The portfolio policy maps model outputs to fixed Normal (60/20/20), Cautious (40/25/35), and Defensive (25/25/50) NIFTY/Gold/Bonds weights. Missing or low-confidence signals select Cautious; elevated risk or a Bearish state selects Defensive. The test backtest applies close-*t* signals to the next close-to-close return and charges 10 bps per unit of one-way turnover against a fixed 60/20/20 benchmark. Taxes, market impact, liquidity, tracking error, and instrument-specific implementation costs are not modeled.

## Run the API

Train models first, then from the repository root:

```powershell
python -m uvicorn marketmind.api:app --host 0.0.0.0 --port 8000
```

Endpoints:

- `GET /health`
- `GET /predict-risk`
- `GET /market-regime`
- `GET /portfolio-recommendation` (also `GET /portfolio/recommend` for the Android client)
- `POST /portfolio-recommendation` with `{"nifty50": 60, "gold": 20, "bonds": 20}` to compare against supplied holdings; weights must sum to 100.
- `GET /analytics`
- `GET /model-performance`
- `GET /market-overview`

The GET portfolio endpoint uses a disclosed 60/20/20 benchmark as its comparison baseline, not a claim about a user's actual holdings. The Android app defaults to live backend mode; sample data is available only when Demo Mode is explicitly enabled. Set the backend URL in Settings (Android emulator default: `http://10.0.2.2:8000/`).

## Android authentication setup

The Android sign-in and account-creation forms use Firebase Authentication with email/password and Google sign-in through Android Credential Manager. In the Firebase console:

1. Register an Android app with package name `com.example.marketmind`.
2. Enable **Authentication > Sign-in method > Email/Password** and **Google**.
3. Add the app's SHA-1 signing-certificate fingerprint under **Project settings > General > Your apps**. For the local debug certificate, run `.\gradlew.bat signingReport` from the repository root and use the debug variant's SHA-1.
4. Download the updated `google-services.json` and place it at `app/google-services.json`. The Google sign-in flow requires the generated Web client ID in this file.

The Google Services Gradle plugin processes this file during the Android build. Without the Firebase config, the project still builds, but sign-in reports that Firebase or Google sign-in setup is missing.

Firebase Authentication currently gates Android sign-in and signup only. The FastAPI market-data endpoints are not protected by Firebase ID-token verification; do not treat app sign-in as API authorization.

## Evaluation outputs

`reports/training_report.md` and `reports/model_evaluation.json` contain validation and unseen-test metrics, confusion matrices, regime statistics, and backtest results. `reports/test_period_evaluation.png` visualizes unseen test-period returns, risk probability, regimes, and outcomes. Results are generated from local data when training runs; no performance value is hardcoded into the reports or API.
