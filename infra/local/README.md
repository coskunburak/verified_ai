# Local Diagnostics

Start the local stack:

```sh
make up
```

Stop it:

```sh
make down
```

Inspect API logs:

```sh
docker compose logs -f api
```

Inspect verifier logs:

```sh
docker compose logs -f math-verifier
```

Inspect PostgreSQL:

```sh
psql postgresql://verified_ai_local:local_dev_password_change_me@localhost:5432/verified_ai
```

Inspect Redis:

```sh
docker compose exec redis redis-cli ping
```

Inspect object storage:

Open `http://localhost:9001` with the local MinIO credentials from `.env.example`.

Follow all logs:

```sh
docker compose logs -f
```

Correlation IDs:

- iOS and API use `X-Request-Id`.
- API logs expose `traceId` and `correlationId`.
- The math verifier echoes `X-Request-Id` and logs the same correlation ID.

Do not place real production secrets in local env files.

