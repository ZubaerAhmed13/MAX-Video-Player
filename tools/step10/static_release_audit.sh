#!/usr/bin/env bash
set -euo pipefail

fail=0
check_absent() {
  local pattern="$1"; shift
  if grep -RInE --exclude-dir=build --exclude='*.md' "$pattern" "$@"; then
    echo "Forbidden production pattern matched: $pattern" >&2
    fail=1
  fi
}

check_absent 'ALLOW_ALL_HOSTNAME_VERIFIER|TrustAll|trustAllCert|disable.*certificate.*validation' app/src/main
check_absent 'android:debuggable="true"|android:testOnly="true"' app/src/main/AndroidManifest.xml
check_absent 'fallbackToDestructiveMigration' app/src/main
check_absent '-----BEGIN (RSA |EC |DSA )?PRIVATE KEY-----' app/src/main
check_absent '(password|passwd|passphrase|bearer|authorization)[[:space:]]*=[[:space:]]*"[^"$]{8,}"' app/src/main

# Private credential stores must stay excluded from both backup rule families.
for f in app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml; do
  grep -q 'private_vault_auth_v1.xml' "$f"
  grep -q 'private_vault_biometric_v1.xml' "$f"
  grep -q 'network_credential_vault_v1.xml' "$f"
  grep -q 'cloud_token_vault_v1.xml' "$f"
done

# Production network config must retain system trust anchors.
grep -q '<certificates src="system"' app/src/main/res/xml/network_security_config.xml

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "STEP10_STATIC_RELEASE_AUDIT_PASS"
