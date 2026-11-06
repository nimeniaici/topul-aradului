"""Sistemul Elo (ca la șah) pentru dueluri între locuri."""


def expected(rating_a: float, rating_b: float) -> float:
    """Probabilitatea ca A să câștige în fața lui B."""
    return 1 / (1 + 10 ** ((rating_b - rating_a) / 400))


def update(winner: float, loser: float, k: float = 32) -> tuple[float, float]:
    """Returnează noile ratinguri (câștigător, pierzător)."""
    delta = k * (1 - expected(winner, loser))
    return winner + delta, loser - delta
