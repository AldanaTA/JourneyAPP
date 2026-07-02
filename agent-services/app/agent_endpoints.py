# agent_endpoints.py

from __future__ import annotations

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers.power_routes import router as power_router
from routers.trait_routes import router as trait_router

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


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


app.include_router(power_router)
app.include_router(trait_router)