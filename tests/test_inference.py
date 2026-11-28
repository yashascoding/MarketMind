import unittest

from pydantic import ValidationError

from marketmind.api import CurrentAllocation
from marketmind.inference import ModelService


class TrainedModelInferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = ModelService()

    def test_latest_prediction_uses_current_aligned_observation(self):
        result = self.service.snapshot()
        self.assertIn(result["risk_level"], {"Normal", "Elevated"})
        self.assertGreaterEqual(result["risk_probability"], 0.0)
        self.assertLessEqual(result["risk_probability"], 1.0)
        self.assertIn(result["regime"], {"Calm", "Volatile", "Bearish"})
        self.assertAlmostEqual(sum(result["regime_probabilities"].values()), 1.0)
        self.assertEqual(
            result["date"].date().isoformat(),
            self.service.market_overview()["timestamp"],
        )

    def test_loaded_artifacts_include_all_test_comparisons(self):
        comparison = self.service.model_performance()["model_comparison"]
        self.assertEqual(set(comparison), {"LSTM", "Logistic Regression", "Random Forest"})
        for metrics in comparison.values():
            self.assertIn("confusion_matrix", metrics["test"])
            self.assertIn("brier_score", metrics["test"])

    def test_recommendation_is_fully_allocated(self):
        response = self.service.portfolio_response()
        self.assertAlmostEqual(
            sum(item["suggested_pct"] for item in response["allocations"]), 100.0
        )
        self.assertIn("benchmark assumption", response["current_allocation_source"])

    def test_current_allocation_must_total_one_hundred(self):
        with self.assertRaises(ValidationError):
            CurrentAllocation(nifty50=40, gold=30, bonds=20)


if __name__ == "__main__":
    unittest.main()
