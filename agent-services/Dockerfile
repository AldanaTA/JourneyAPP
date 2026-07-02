FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# psycopg + common build deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential gcc libpq-dev curl \
  && rm -rf /var/lib/apt/lists/*

COPY app/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

#Copy from app directory
COPY app/ ./

EXPOSE 8800

CMD ["uvicorn", "app.agent_endpoints:app", "--host", "0.0.0.0", "--port", "8800"]
