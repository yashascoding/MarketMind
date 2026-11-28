import unittest

import numpy as np

from marketmind.pipeline import (
    ASSET_FILES,
    FEATURE_COLUMNS,
    engineer_features,
    inspect_datasets,
    load_aligned_prices,
    make_future_risk_labels,
)
from marketmind.portfolio import ALLOCATIONS, choose_allocation


class ActualDatasetPipelineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.prices = load_aligned_prices()
        cls.features = engineer_features(cls.prices)
        cls.labeled = make_future_risk_labels(cls.prices, cls.features).dropna(
            subset=["elevated_risk"]
        )

    def test_all_sources_use_the_full_nifty_calendar_without_fabricated_prices(self):
        self.assertEqual(list(self.prices.columns), list(ASSET_FILES))
        self.assertEqual(len(ASSET_FILES), 15)
        self.assertGreater(len(self.prices), 4000)
        self.assertFalse(self.prices["nifty"].isna().any())
        self.assertTrue(self.prices["gilt_etf"].isna().any())
        self.assertTrue(self.prices["gilt_etf"].iloc[0:100].isna().all())
        self.assertFalse(self.prices.index.has_duplicates)
        self.assertEqual(
            sorted(set(self.prices.index.year)),
            list(range(2007, 2027)),
        )

    def test_every_dataset_contributes_features_and_forward_target_has_both_classes(self):
        self.assertEqual(list(self.features.columns), FEATURE_COLUMNS)
        for asset in ASSET_FILES:
            self.assertIn(f"log_return_{asset}", self.features)
            self.assertIn(f"volatility_30d_{asset}", self.features)
        self.assertGreater(int(self.labeled["elevated_risk"].sum()), 0)
        self.assertLess(int(self.labeled["elevated_risk"].sum()), len(self.labeled))

    def test_bad_etf_reversal_rows_are_removed_but_real_vix_spikes_remain(self):
        source_audit = {
            item["file"]: item["isolated_reversal_prices_removed"]
            for item in inspect_datasets()
        }
        self.assertEqual(source_audit["goldbees.csv"], 2)
        self.assertEqual(source_audit["niftybees.csv"], 2)
        self.assertAlmostEqual(
            self.prices.loc["2008-07-15", "india_vix"], 49.77, places=2
        )
        self.assertAlmostEqual(
            self.prices.loc["2011-08-08", "us_vix"], 48.0, places=2
        )

    def test_future_price_changes_do_not_change_past_features(self):
        cutoff = self.prices.index[-25]
        changed_prices = self.prices.copy()
        changed_prices.loc[changed_prices.index > cutoff, "nifty"] *= 1.5
        changed_features = engineer_features(changed_prices)
        np.testing.assert_allclose(
            self.features.loc[cutoff].to_numpy(),
            changed_features.loc[cutoff].to_numpy(),
            equal_nan=True,
        )

    def test_foreign_close_is_not_used_on_the_same_nifty_session(self):
        source_date = self.prices.index[500]
        changed_prices = self.prices.copy()
        changed_prices.loc[source_date, "sp500"] *= 1.5
        changed_features = engineer_features(changed_prices)
        np.testing.assert_allclose(
            self.features.loc[source_date].to_numpy(),
            changed_features.loc[source_date].to_numpy(),
            equal_nan=True,
        )


class AllocationTests(unittest.TestCase):
    def test_allocation_strategies_sum_to_one_hundred(self):
        for weights in ALLOCATIONS.values():
            self.assertEqual(sum(weights.values()), 100.0)

    def test_missing_or_uncertain_signal_uses_cautious_allocation(self):
        strategy, weights, _ = choose_allocation(None, 0.4, "Calm", 0.9)
        self.assertEqual(strategy, "cautious")
        self.assertEqual(weights, ALLOCATIONS["cautious"])

    def test_elevated_risk_overrides_calm_regime(self):
        strategy, weights, _ = choose_allocation(0.7, 0.4, "Calm", 0.9)
        self.assertEqual(strategy, "defensive")
        self.assertEqual(weights, ALLOCATIONS["defensive"])


if __name__ == "__main__":
    unittest.main()
