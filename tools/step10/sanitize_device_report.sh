#!/usr/bin/env bash
set -euo pipefail
root="${1:?Usage: $0 file-or-directory}"
python3 - "$root" <<'PY'
from pathlib import Path
import re, sys
root = Path(sys.argv[1])
paths = [root] if root.is_file() else [p for p in root.rglob('*') if p.is_file()]
patterns = [
    (re.compile(r'(?i)(authorization\s*[:=]\s*)([^\s]+)'), r'\1[REDACTED]'),
    (re.compile(r'(?i)(bearer\s+)[A-Za-z0-9._~+/=-]+'), r'\1[REDACTED]'),
    (re.compile(r'(?i)((?:password|passwd|passphrase|token|cookie)\s*[:=]\s*)([^\s,;]+)'), r'\1[REDACTED]'),
    (re.compile(r'(?i)(serial(?:no|number)?\s*[:=]\s*)([^\s,;]+)'), r'\1[REDACTED]'),
    (re.compile(r'(?i)(imei\s*[:=]\s*)([^\s,;]+)'), r'\1[REDACTED]'),
    (re.compile(r'(?i)(mac(?: address)?\s*[:=]\s*)([0-9a-f:.-]+)'), r'\1[REDACTED]'),
]
for path in paths:
    try:
        text = path.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    for pattern, replacement in patterns:
        text = pattern.sub(replacement, text)
    path.write_text(text, encoding='utf-8')
PY
