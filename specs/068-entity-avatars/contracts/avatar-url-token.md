# Avatar URL access token (`avt`)

## Format

```
avt = base64url(payload) + "." + base64url(HMAC-SHA256(secret, payload))
payload = viewer_id|file_id|w|h|exp_epoch_seconds
```

## Validation

- HMAC with current or previous secret (24h rotation window)
- exp not passed
- file_id, w, h match query params
- AvatarAccessPort allows viewer + file

## TTL

Default 3600 seconds.

## Env

- `AVATAR_TOKEN_HMAC_SECRET`
- `AVATAR_TOKEN_HMAC_SECRET_PREVIOUS` (optional)
