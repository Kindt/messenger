# Spec 068 — Entity avatars

## Scope

User, group, channel, p2p (peer display), conference/live (via chat), bot (users row), plugin/org logo (W8).

## ACL (HTTP mint)

- User avatar: same org; blocks → null URL; hidden user → no profile for others.
- Chat avatar: chat member, not banned.
- Signed `avt` token on all `avatar_url` fields (see contracts/avatar-url-token.md).

## UI matrix

See plan W3–W6 touchpoints in tasks.md.

## NATS

- `user.presence` subject with `type: avatar` (`UserAvatarEvent`).
- Per-recipient deliver with personalized `avatar_url`.
- `type: chat.avatar` (`ChatAvatarEvent`) to chat members.

## Policy precedence

1. org disabled → PATCH 403
2. ldap_only → import only
3. avatar_hidden → others null
4. block → null + 403 resize
