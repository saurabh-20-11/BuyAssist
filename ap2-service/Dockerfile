# BuyAssist AP2 service (FastAPI + official Google AP2 SDK).
FROM python:3.12-slim

WORKDIR /app

# System deps for building the AP2 SDK's crypto dependencies, plus git to install it.
RUN apt-get update && apt-get install -y --no-install-recommends \
        git build-essential \
    && rm -rf /var/lib/apt/lists/*

# Python deps.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir "git+https://github.com/google-agentic-commerce/AP2.git"

# App code.
COPY app app

# Render provides $PORT; default to 8081 locally.
ENV PORT=8081
EXPOSE 8081
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8081}"]
