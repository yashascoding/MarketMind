from __future__ import annotations

import numpy as np
from hmmlearn.hmm import GaussianHMM
from scipy.special import logsumexp


def causal_hmm_filter(model: GaussianHMM, observations: np.ndarray) -> np.ndarray:
    """Forward-filter state probabilities without using observations after each date."""
    means = model.means_
    variances = np.maximum(
        np.diagonal(model.covars_, axis1=1, axis2=2), 1e-8
    )
    log_emissions = -0.5 * np.sum(
        np.log(2 * np.pi * variances)[None, :, :]
        + ((observations[:, None, :] - means[None, :, :]) ** 2)
        / variances[None, :, :],
        axis=2,
    )
    prior = np.log(np.maximum(model.startprob_, 1e-300))
    log_transition = np.log(np.maximum(model.transmat_, 1e-300))
    filtered = np.empty((len(observations), model.n_components), dtype="float64")
    for index, emission in enumerate(log_emissions):
        current = prior + emission
        current -= logsumexp(current)
        filtered[index] = np.exp(current)
        prior = logsumexp(current[:, None] + log_transition, axis=0)
    return filtered
