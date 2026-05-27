"""Security tests for tenant validation (ADR-032 Decision 4)."""
import pandas as pd
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch

from main import app


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def mock_ch():
    """Mock ClickHouse so tenant-validation tests don't need a live DB."""
    with patch("services.forecast_service.fetch_hourly_energy", return_value=pd.DataFrame()):
        yield


class TestTenantValidation:
    """ADR-032 D4: missing X-Tenant-ID → 403, invalid format → 400, valid → 200."""

    def test_missing_tenant_id_returns_403(self, client):
        response = client.get("/api/v1/forecast/energy", params={"building_id": "b1"})
        assert response.status_code == 403
        assert "Missing X-Tenant-ID" in response.json()["detail"]

    def test_empty_tenant_id_returns_403(self, client):
        response = client.get(
            "/api/v1/forecast/energy",
            params={"building_id": "b1"},
            headers={"X-Tenant-ID": ""},
        )
        assert response.status_code == 403

    def test_whitespace_tenant_id_returns_403(self, client):
        response = client.get(
            "/api/v1/forecast/energy",
            params={"building_id": "b1"},
            headers={"X-Tenant-ID": "   "},
        )
        assert response.status_code == 403

    def test_invalid_format_returns_400(self, client):
        response = client.get(
            "/api/v1/forecast/energy",
            params={"building_id": "b1"},
            headers={"X-Tenant-ID": "!!!invalid!!!"},
        )
        assert response.status_code == 400
        assert "Invalid X-Tenant-ID" in response.json()["detail"]

    def test_valid_tenant_id_accepted(self, client, mock_ch):
        """Valid format but will likely get 200 or error from downstream (no data).
        The key test is NOT 403/400 — tenant validation passes."""
        response = client.get(
            "/api/v1/forecast/energy",
            params={"building_id": "b1", "horizon_days": 1},
            headers={"X-Tenant-ID": "tenant-123"},
        )
        # Should NOT be 403 or 400 — tenant validation passed
        assert response.status_code not in (403, 400)

    def test_valid_uuid_tenant_id(self, client, mock_ch):
        response = client.get(
            "/api/v1/forecast/energy",
            params={"building_id": "b1", "horizon_days": 1},
            headers={"X-Tenant-ID": "550e8400-e29b-41d4-a716-446655440000"},
        )
        assert response.status_code not in (403, 400)

    def test_tenant_id_with_hyphens_and_underscores(self, client, mock_ch):
        response = client.get(
            "/api/v1/forecast/energy",
            params={"building_id": "b1", "horizon_days": 1},
            headers={"X-Tenant-ID": "my-tenant_2024"},
        )
        assert response.status_code not in (403, 400)

    def test_tenant_id_too_long_returns_400(self):
        """Max 64 chars per regex."""
        from api.dependencies import get_tenant_id
        from fastapi import Request
        from unittest.mock import MagicMock

        request = MagicMock(spec=Request)
        request.headers = {"X-Tenant-ID": "a" * 65}

        with pytest.raises(Exception) as exc_info:
            import asyncio
            asyncio.get_event_loop().run_until_complete(get_tenant_id(request))
        assert exc_info.value.status_code == 400
