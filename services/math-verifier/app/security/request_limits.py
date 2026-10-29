"""Bound bytes before JSON/Pydantic parsing, including requests without Content-Length."""
import json

from fastapi.responses import JSONResponse

MAX_REQUEST_BYTES = 65536


class RequestLimits:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["method"] not in {"POST", "PUT", "PATCH"}:
            return await self.app(scope, receive, send)
        chunks, size = [], 0
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            body = message.get("body", b"")
            size += len(body)
            if size > MAX_REQUEST_BYTES:
                response = JSONResponse(status_code=413, content={"code": "VERIFIER_REQUEST_TOO_LARGE"})
                return await response(scope, receive, send)
            chunks.append(body)
            if not message.get("more_body", False):
                break
        payload = b"".join(chunks)

        def unique_fields(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise ValueError("duplicate JSON field")
                result[key] = value
            return result

        try:
            json.loads(payload, object_pairs_hook=unique_fields)
        except (ValueError, UnicodeError, RecursionError):
            response = JSONResponse(status_code=422, content={"code": "VERIFIER_INPUT_SCHEMA_INVALID"})
            return await response(scope, receive, send)
        sent = False

        async def bounded_receive():
            nonlocal sent
            if not sent:
                sent = True
                return {"type": "http.request", "body": payload, "more_body": False}
            return await receive()

        await self.app(scope, bounded_receive, send)
