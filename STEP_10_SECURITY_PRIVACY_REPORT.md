# Step 10 Security & Privacy Report

## Source/configuration review

- `AndroidManifest.xml` requests media/network/foreground-service/notification/biometric permissions; it does not request camera, microphone or location.
- `backup_rules.xml` and `data_extraction_rules.xml` exclude private-vault authentication/biometric state, network credential vault and cloud token vault preferences.
- `network_security_config.xml` retains Android system trust anchors. Cleartext remains available only because HTTP/FTP are explicit product features; TLS verification is not disabled.
- Step-10 static audit rejects obvious trust-all/disabled-certificate patterns, destructive Room fallback, production private-key material, release debuggable/testOnly flags and obvious hard-coded credential assignments in production source.
- Step-10 package audit records APK/AAB SHA-256 and package/permission metadata and rejects debuggable/testOnly or private-key/sentinel-secret material in release artifacts.

## Dependency vulnerability review — Bouncy Castle via SMBJ

A current dependency review identified that SMBJ `0.14.0` brings runtime `org.bouncycastle:bcprov-jdk18on:1.79`. Public 2026 advisories affect that BC line: CVE-2025-14813 concerns the GOST CTR implementation and CVE-2026-0636 concerns `LDAPStoreHelper`. MAX source does not directly import or invoke Bouncy Castle/GOST/LDAP APIs, so those specific paths are not established MAX execution paths. However, Step 10 removes the affected packaged version by directly pinning `bcprov-jdk18on` to vendor-fixed `1.84`.

Acceptance condition: clean build plus retained Step-7 SMB/network/protocol regression must pass with the resolved 1.84 provider. This report deliberately does not claim “0 vulnerabilities”; it records the signal, affected transitive package, code-path assessment, mitigation and required regression.

## Physical privacy validation

Hardware/system screenshots, screen recording, recents/task preview, lock-screen notification privacy, Bluetooth/wearable metadata exposure, biometric cancellation/enrollment changes/Keystore invalidation and OEM-specific `FLAG_SECURE` behavior remain `NOT VERIFIED` until real devices execute them.

## Production signing

`PRODUCTION SIGNING/PUBLISHING NOT PERFORMED`. No keystore, signing password or production secret is committed.
