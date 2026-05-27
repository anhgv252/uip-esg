"""Pytest configuration for forecast-service tests."""
import pytest


def pytest_configure(config):
    config.addinivalue_line(
        "markers", "slow: marks tests as slow (real ARIMA fitting — skip with -m 'not slow')"
    )
