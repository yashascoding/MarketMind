from __future__ import annotations

from functools import lru_cache
from typing import Annotated

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator

from marketmind.inference import ModelService

app = FastAPI(
    title="MarketMind Market Risk API",
    description="Research-only model outputs trained on the local historical CSV datasets.",
    version="1.0.0",
)


@lru_cache(maxsize=1)
def get_model_service() -> ModelService:
    try:
        return ModelService()
    except (FileNotFoundError, ImportError, OSError, ValueError) as error:
        raise HTTPException(
            status_code=503,
            detail=f"Market models are unavailable: {error}",
        ) from error


class CurrentAllocation(BaseModel):
    model_config = ConfigDict(extra="forbid")

    nifty50: Annotated[float, Field(ge=0, le=100)]
    gold: Annotated[float, Field(ge=0, le=100)]
    bonds: Annotated[float, Field(ge=0, le=100)]

    @model_validator(mode="after")
    def validate_total(self) -> "CurrentAllocation":
        if abs(self.nifty50 + self.gold + self.bonds - 100.0) > 1e-6:
            raise ValueError("Current asset weights must sum to exactly 100%.")
        return self


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "MarketMind"}


@app.get("/predict-risk")
def predict_risk() -> dict:
    return get_model_service().risk_response()


@app.get("/market-regime")
def market_regime() -> dict:
    return get_model_service().regime_response()


@app.get("/portfolio-recommendation")
@app.get("/portfolio/recommendation")
@app.get("/portfolio/recommend")
def portfolio_recommendation() -> dict:
    return get_model_service().portfolio_response()


@app.post("/portfolio-recommendation")
@app.post("/portfolio/recommendation")
def portfolio_recommendation_for_holdings(current: CurrentAllocation) -> dict:
    return get_model_service().portfolio_response(current.model_dump())


@app.get("/analytics")
def analytics() -> dict:
    return get_model_service().analytics()


@app.get("/model-performance")
def model_performance() -> dict:
    return get_model_service().model_performance()


@app.get("/market-overview")
def market_overview() -> dict:
    return get_model_service().market_overview()
