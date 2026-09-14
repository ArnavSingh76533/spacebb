#!/usr/bin/env python3
"""Fetch a pinned, checksum-verified build input. Never runs on the user's phone."""
from pathlib import Path
import hashlib
import urllib.request

SOURCE = "https://raw.githubusercontent.com/StevenBlack/hosts/83dd698bbcdcb8c11a7796af7188d7ab4ccd02f1/hosts"
SHA256 = "91ccff4cf105c9303caff6edcbc4964bf6400da86d51382d97267c9cb3a516cf"
DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/hosts.txt"
if DEST.exists() and hashlib.sha256(DEST.read_bytes()).hexdigest() == SHA256:
    print("Verified bundled filter snapshot")
else:
    data = urllib.request.urlopen(SOURCE, timeout=90).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("Filter checksum mismatch; refusing unverified build input")
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_bytes(data)
    print("Downloaded and verified pinned filter snapshot")
