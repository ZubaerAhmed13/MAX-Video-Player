# Step 10 Certification Evidence

Store only lightweight, sanitized evidence for an exact candidate SHA under `certification/step10/<candidate-sha>/`.

Recommended contents:

- `device-matrix.json`
- `device-results/`
- `media-results/`
- `network-results/`
- `performance-summary.json`
- `known-limitations.md`

Do not create a candidate directory merely to imply a device was tested. Create it when real evidence exists, record the exact tested Git SHA in each generated summary, and do not mix binaries across candidates. Do not commit huge logs, copyrighted media, device serial/IMEI/MAC/account identifiers, Wi-Fi/NAS credentials, tokens, PINs/passphrases or signing material.
