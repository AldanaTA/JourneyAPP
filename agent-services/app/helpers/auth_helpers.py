"""Verifies the access JWTs issued by the Journey API (journey-app) and requires an admin.

The API signs access tokens with HS256 and the shared JWT_SECRET. Here the token is checked for a valid
signature, expiry, and issuer, then against the access_tokens table, so signing out in the app (or a revoked
session) stops a token working here at once too. Importers write official content, so they need the
"admin" scope.
"""

from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass

import jwt
import psycopg
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

JWT_ISSUER = os.getenv("JWT_ISSUER", "journey-api")
MIN_SECRET_BYTES = 32

# auto_error=False so a missing token returns our 401 (with WWW-Authenticate) rather than FastAPI's 403.
bearer_scheme = HTTPBearer(auto_error=False, description="Access token from POST /api/v1/auth/login")

ACTIVE_TOKEN_SQL = """
SELECT 1
FROM access_tokens a
JOIN users u ON u.id = a.user_id
WHERE a.token_hash = %s
  AND a.revoked_at IS NULL
  AND a.expires_at > NOW()
  AND u.disabled_at IS NULL
"""


@dataclass(frozen=True)
class AuthenticatedUser:
    id: str
    name: str | None
    scopes: frozenset[str]


def _unauthorized(detail: str) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


def _secret() -> str:
    secret = os.getenv("JWT_SECRET", "")
    if len(secret.encode("utf-8")) < MIN_SECRET_BYTES:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="JWT_SECRET is not configured on the agent service (it must match the API's).",
        )
    return secret


def _is_active(token: str) -> bool:
    database_url = os.getenv("DATABASE_URL")
    if not database_url:
        raise HTTPException(status_code=500, detail="DATABASE_URL is missing on the server.")
    token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
    with psycopg.connect(database_url) as conn:
        return conn.execute(ACTIVE_TOKEN_SQL, (token_hash,)).fetchone() is not None


def current_user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme)) -> AuthenticatedUser:
    """FastAPI dependency: the signed-in user, or 401."""
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise _unauthorized("Sign in required")
    token = credentials.credentials
    try:
        claims = jwt.decode(
            token,
            _secret(),
            algorithms=["HS256"],
            issuer=JWT_ISSUER,
            options={"require": ["exp", "iss", "sub"]},
        )
    except jwt.ExpiredSignatureError:
        raise _unauthorized("Token expired")
    except jwt.InvalidTokenError:
        raise _unauthorized("Invalid token")
    if not _is_active(token):
        raise _unauthorized("The token was revoked")
    scopes = frozenset(str(claims.get("scope", "")).split())
    return AuthenticatedUser(id=claims["sub"], name=claims.get("name"), scopes=scopes)


def require_admin(user: AuthenticatedUser = Depends(current_user)) -> AuthenticatedUser:
    """FastAPI dependency: the signed-in user if they are an admin, else 403."""
    if "admin" not in user.scopes:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admins only")
    return user
