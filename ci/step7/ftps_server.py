#!/usr/bin/env python3
"""Deterministic explicit-FTPS server used only by the isolated Step 7 CI lane."""

from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import TLS_FTPHandler
from pyftpdlib.servers import FTPServer


authorizer = DummyAuthorizer()
authorizer.add_user("maxci", "max-network-ci", "/tmp/max-video-step7", perm="elr")

handler = TLS_FTPHandler
handler.authorizer = authorizer
handler.certfile = "/tmp/max-video-step7-ftps-server.pem"
handler.keyfile = "/tmp/max-video-step7-ftps-server.key"
handler.tls_control_required = True
handler.tls_data_required = True
handler.passive_ports = range(30000, 30050)

FTPServer(("0.0.0.0", 2122), handler).serve_forever()
