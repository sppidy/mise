# Security policy

Mise is pre-1.0 software. Security fixes are made on the default branch; older commits and APKs do not receive separate support.

Only the latest tagged release and the current `main` branch receive security fixes.

## Reporting a vulnerability

Use **Report a vulnerability** in this repository's Security tab. That creates a private report through GitHub. If the option is not available, contact the repository owner through GitHub and ask for a private channel before sending exploit details.

Please do not open a public issue for an unpatched vulnerability. Include the affected commit or version, a minimal reproduction, likely impact, and any mitigation you have already tested. Remove personal recipe data, tokens, cookies, and private hostnames from the report.

## Deployment boundary

The server has no built-in authentication or user isolation. Anyone who can reach the API can read or replace the stored recipe box. Do not expose port 8787 directly to the public internet. Use a private network, VPN, or authenticated reverse proxy, and back up `DATA_DIR` before upgrades.

The importer fetches user-supplied URLs. It rejects non-public IP ranges, pins each request to the addresses it checked, and applies redirect, time, type, and size limits. Reports that bypass those controls are particularly useful.

Use HTTPS for Android or browser connections that leave the local machine. Plain HTTP exposes recipe data and allows it to be changed in transit.

## Secrets

The project does not require API keys for its default setup. Keep reverse-proxy credentials, Android keystores, `keystore.properties`, `.env` files, and any future service tokens outside the repository. If a secret reaches Git history, revoke it before removing it from the branch.
