# Dataset inspection

Generated from the checked-in local CSV files by `python -m marketmind.train`.

| File | Rows | Date range | Duplicate dates | Duplicate records | Isolated reversal prices removed | Missing values by column | Columns (dtype) |
|---|---:|---|---:|---:|---:|---|---|
| `brent.csv` | 4813 | 2007-07-30 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `dxy.csv` | 5010 | 2007-01-02 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `gilt_etf.csv` | 1850 | 2018-05-11 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `gilt_fund.csv` | 2943 | 2006-04-03 - 2018-06-01 | 0 | 0 | 0 | none | date (datetime64[ns]), nav (float64) |
| `gold.csv` | 5009 | 2007-01-02 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `goldbees.csv` | 4411 | 2009-01-02 - 2026-11-27 | 0 | 0 | 2 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `india_vix.csv` | 4590 | 2008-03-03 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `nifty.csv` | 4709 | 2007-09-17 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `niftybees.csv` | 4412 | 2009-01-02 - 2026-11-27 | 0 | 0 | 2 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `nse_gsec_10y_clean_price.csv` | 2202 | 2018-01-01 - 2026-11-26 | 0 | 0 | 0 | Open: 3, High: 3, Low: 3 | date (datetime64[ns]), Open (float64), High (float64), Low (float64), Close (float64) |
| `sp500.csv` | 5007 | 2007-01-03 - 2026-11-25 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `tlt.csv` | 5007 | 2007-01-03 - 2026-11-25 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `us_10y.csv` | 5006 | 2007-01-03 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `us_vix.csv` | 5011 | 2007-01-03 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |
| `usdinr.csv` | 5161 | 2007-01-01 - 2026-11-27 | 0 | 0 | 0 | none | date (datetime64[ns]), Adj Close (float64), Close (float64), High (float64), Low (float64), Open (float64), Volume (float64) |

## Model assets

- brent: data/raw/brent.csv (Close price series)
- dxy: data/raw/dxy.csv (Close price series)
- gilt_etf: data/raw/gilt_etf.csv (Adj Close price series)
- gilt_fund: data/raw/gilt_fund.csv (nav price series)
- gold: data/raw/gold.csv (Close price series)
- goldbees: data/raw/goldbees.csv (Adj Close price series)
- india_vix: data/raw/india_vix.csv (Close price series)
- nifty: data/raw/nifty.csv (Close price series)
- niftybees: data/raw/niftybees.csv (Adj Close price series)
- nse_gsec_10y: data/raw/nse_gsec_10y_clean_price.csv (Close price series)
- sp500: data/raw/sp500.csv (Close price series)
- tlt: data/raw/tlt.csv (Adj Close price series)
- us_10y: data/raw/us_10y.csv (Close price series)
- us_vix: data/raw/us_vix.csv (Close price series)
- usdinr: data/raw/usdinr.csv (Close price series)

## NIFTY-calendar source coverage

| Source | NIFTY dates with fresh observations | Coverage | First aligned | Last aligned |
|---|---:|---:|---|---|
| `brent` | 4703/4709 | 99.9% | 2007-09-17 | 2026-11-27 |
| `dxy` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `gilt_etf` | 1999/4709 | 42.5% | 2018-05-11 | 2026-11-27 |
| `gilt_fund` | 2623/4709 | 55.7% | 2007-09-17 | 2018-06-08 |
| `gold` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `goldbees` | 4392/4709 | 93.3% | 2009-01-02 | 2026-11-27 |
| `india_vix` | 4593/4709 | 97.5% | 2008-03-03 | 2026-11-27 |
| `nifty` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `niftybees` | 4392/4709 | 93.3% | 2009-01-02 | 2026-11-27 |
| `nse_gsec_10y` | 2194/4709 | 46.6% | 2018-01-02 | 2026-11-27 |
| `sp500` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `tlt` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `us_10y` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `us_vix` | 4709/4709 | 100.0% | 2007-09-17 | 2026-11-27 |
| `usdinr` | 4698/4709 | 99.8% | 2007-09-17 | 2026-11-27 |

## Preprocessing decisions

- Original data/raw CSVs are read-only inputs; preprocessing writes separate reports and artifacts.
- All 15 local CSVs are model inputs. NIFTY Close defines the target calendar and labels; other sources are retained as independent features even when their histories overlap or have different start/end dates.
- Duplicate source dates keep the final source row; invalid and nonpositive prices remain unavailable rather than being fabricated. Adjusted Close is used for ETF datasets where supplied; gilt_fund uses NAV.
- One to three consecutive source observations are marked unavailable only when each moves at least 80% from the preceding price and the following observation is within 10% of that preceding price. Counts are included in the dataset audit; original CSVs are unchanged.
- Cross-market quotes are backward-as-of aligned only when no more than seven calendar days old. No values are extended before a source begins or after its latest fresh observation.
- Foreign-market closes, USD/INR, and historical gilt-fund NAV features are lagged by one NIFTY session so that information published after the Indian close cannot leak into same-day predictions.
- Missing engineered features are median-imputed with missingness indicators fitted on training dates only; the same saved imputer and scaler are used at inference.
- Returns, 7/30-session volatility, 50/200-session moving-average ratios, drawdowns, and NIFTY cross-asset correlations are engineered for every source; full trailing windows are required and never backfilled.
- HMM features include NIFTY return, volatility and drawdown plus cross-asset aggregate return and volatility from all available sources.
- The 10-session target is elevated if the future path's maximum drawdown is at most -5% OR future annualized realized volatility is at least 30%.
- Sequences are labeled at their last observed close and forecast the next ten sessions; chronological splits purge ten labels at each boundary.
- All dates in the NIFTY source timeline from 2007 through 2026 are retained; chronological partitions remain separate, with test dates held out for final evaluation.
- Feature imputers and scalers are fit on the training partition only; validation selects hyperparameters and thresholds, and test is evaluated once.
- Backtest allocations use the signal available at close t for close-to-close return t+1, with 10 bps applied to one-way turnover.
