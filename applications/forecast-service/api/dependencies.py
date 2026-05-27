import re

from fastapi import Request, HTTPException


async def get_tenant_id(request: Request) -> str:
    """
    Extract tenant_id from internal X-Tenant-ID header.
    This header is set by Java backend after JWT verification.
    forecast-service is network-isolated; direct external calls are not possible.

    ADR-032 Decision 4: missing → 403, invalid format → 400
    """
    tenant_id = request.headers.get("X-Tenant-ID")
    if not tenant_id or not tenant_id.strip():
        raise HTTPException(
            status_code=403,
            detail="Missing X-Tenant-ID header — internal access only",
        )
    if not re.match(r"^[a-zA-Z0-9_-]{1,64}$", tenant_id):
        raise HTTPException(
            status_code=400,
            detail="Invalid X-Tenant-ID format",
        )
    return tenant_id
