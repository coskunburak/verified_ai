# Local integration-test fixture only; not a production storage image.
FROM golang:1.22.5-bookworm AS build
ENV GOTOOLCHAIN=local
WORKDIR /src
# Pin both the original release tag and its commit; fail if the tag moves.
RUN git init . \
    && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth 1 origin refs/tags/RELEASE.2024-07-16T23-46-41Z \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "3535197f993dea840dcb96302ba4d883dc619097"
RUN CGO_ENABLED=0 go build -mod=readonly -trimpath -o /out/minio . \
    && mkdir -p /out/data /out/tmp \
    && chmod 1777 /out/tmp

FROM scratch
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="3535197f993dea840dcb96302ba4d883dc619097" \
      org.opencontainers.image.description="Source-built local test fixture; not the upstream release binary"
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /src/LICENSE /licenses/minio/LICENSE
COPY --from=build /out/minio /minio
COPY --from=build --chown=10001:10001 /out/data /data
COPY --from=build /out/tmp /tmp
ENV HOME=/tmp
USER 10001:10001
EXPOSE 9000
ENTRYPOINT ["/minio"]
CMD ["server", "/data"]
