FROM python:3.11-slim AS runtime

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY pyproject.toml .
COPY app ./app
RUN pip install --no-cache-dir .

RUN adduser --disabled-password --gecos "" verifier
USER verifier

EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1", "--limit-concurrency", "32", "--backlog", "32", "--timeout-keep-alive", "5"]

# Owner/CI validation dependencies only; the default image remains the production runtime.
FROM runtime AS owner-tests
USER root
RUN pip install --no-cache-dir '.[dev]'
USER verifier

FROM runtime AS production
