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

## MinIO integration-test image recovery

The owner full API run could not pull `minio/minio:RELEASE.2024-07-16T23-46-41Z`.
There is no owner-approved cached image or mirror. `minio-test/Dockerfile` provides
an explicit source-build option for the same release's commit
`3535197f993dea840dcb96302ba4d883dc619097`, checked against the fetched tag.
Source: [official release](https://github.com/minio/minio/releases/tag/RELEASE.2024-07-16T23-46-41Z).
Owner evidence on 2026-10-26 confirms the build (96.5s) and S3 test (1/1, no errors
or skips, BUILD SUCCESS at 00:28:44+03:00). It produces a local test fixture,
not a byte-identical upstream binary; upstream release linker metadata is not applied.

The build downloads the official Go 1.22.5 builder, MinIO source and its pinned Go
dependencies. Go runs inside Docker; no host Go installation is required.
Docker must be running and network access is required. Execute each command only
after the preceding command succeeds; return the first failure log.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
docker build -t verified-ai/minio-test:2024-07-16-3535197 infra/local/minio-test
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd services/api
mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 -Dtest=S3CompatibleProblemAssetStorageTest test
```

The test still exercises real presigned uploads, object metadata, reads, hashes,
derivative storage and deletion. There is no automatic mirror fallback or test skip.
The Maven property selects the image explicitly. `make test-api` now depends on
`make test-minio-image` and passes that property; Backend CI builds and selects the
same image. The owner also verified `make test-minio-image` (0.7s, cached);
hosted CI and the integrated `make test-api` wrapper have not yet been executed. Direct
Maven commands must pass the property as shown above. Compose still uses its original
image and requires a separate local-stack recovery. Do not retag this build as an official MinIO image.
This old source/toolchain is only for local regression recovery. A maintained
storage/test dependency strategy remains required before production use.

