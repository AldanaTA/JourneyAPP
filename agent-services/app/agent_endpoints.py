# agent_endpoints.py

from __future__ import annotations

from dotenv import load_dotenv
from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse

from helpers.auth_helpers import require_admin
from routers.power_routes import router as power_router
from routers.trait_routes import router as trait_router
from routers.job_routes import router as job_router
from routers.race_routes import router as race_router
from routers.item_routes import router as item_router
from routers.creature_routes import router as creature_router
from routers.rules_routes import router as rules_router

load_dotenv()

app = FastAPI(
    title="Journey Agent Import API",
    version="1.0.0",
    description="Upload TXT, DOCX, or PDF files and extract Journey content into the database.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ],
    allow_origin_regex=r"^http://192\.168\.40\.\d{1,3}:8800$",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def root():
    return {"status": "ok"}

@app.get("/favicon.ico", include_in_schema=False)
def favicon():
    from fastapi.responses import RedirectResponse
    return RedirectResponse(url="/docs")

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


# Importers write official content, so every import route needs an admin access token from the Journey API
# (Authorization: Bearer <token>). "/", "/health", and "/docs" stay open.
for router in (power_router, trait_router, job_router, race_router, item_router, creature_router, rules_router):
    app.include_router(router, dependencies=[Depends(require_admin)])
