# Contract: E2EE MLS Full (US7)

## Wire (existing phase 1)

- Message types: `e2ee-mls-welcome`, `e2ee-mls-commit`, application ciphertext
- Send field: `e2ee_scheme=mls` | `legacy`
- Capabilities: `mls_status`, `e2ee_schemes[]`

## NATS subjects (docs/NATS_SUBJECTS_INTEROP.md)

| Subject | Direction | Payload |
|---------|-----------|---------|
| mls.welcome | publish + **consume** | KMLS welcome bytes |
| mls.commit | publish + **consume** | KMLS commit bytes |
| mls.epoch | publish + **consume** | epoch notification |

## Client obligations (US7)

- Generate key packages in browser (RFC 9420)
- Encrypt application messages before REST send
- Decrypt locally; never rely on `/plaintext-preview` when MLS active

## Server obligations

- Real MLS group state in MlsService (not stub pass-through)
- NATS consumer dispatches to group members
- Legacy path unchanged for `e2ee_scheme=legacy`

## Admin

- GET `/admin/e2ee/status`: pending_migrations, active_groups, library_version

## Gates

- T130 product sign-off before T140+
- Security review before prod enable
