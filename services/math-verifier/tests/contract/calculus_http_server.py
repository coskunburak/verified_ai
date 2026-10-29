"""Owner Java contract harness: real authenticated ASGI HTTP and supervised worker."""
import logging
import socket
import sys

import uvicorn

from app.main import app

if __name__ == "__main__":
    for handler in logging.getLogger().handlers:
        if isinstance(handler, logging.StreamHandler):
            handler.setStream(sys.stderr)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen(16)
        print(listener.getsockname()[1], flush=True)
        server = uvicorn.Server(uvicorn.Config(app, log_level="error", access_log=False))
        server.run(sockets=[listener])
