# Contract: Hexagonal Write Path (US2–US3)

## REST → Application Service mapping

| Endpoint | Method | Service method |
|----------|--------|----------------|
| /api/v1/users/me | PATCH | UserApplicationService.updateProfile |
| /api/v1/users/me/presence | PATCH | UserApplicationService.updatePresence |
| /api/v1/users/me/privacy | PATCH | UserApplicationService.updatePrivacy |
| /api/v1/users/heartbeat | POST | UserApplicationService.touchHeartbeat |
| /api/v1/admin/organizations | GET/POST | OrganizationApplicationService |
| /api/v1/admin/organizations/{id} | DELETE | OrganizationApplicationService.deleteIfUnused |
| /api/v1/admin/users/{id}/organization | PUT | OrganizationApplicationService.setUserOrg |
| /api/v1/files/upload | POST | FileApplicationService.upload |
| /api/v1/files/{id} | GET/DELETE | FileApplicationService.download/delete |

## Invariants

- Resources MUST NOT call `api.repository.*` for listed write paths after US2.
- Responses for User GET/PATCH MUST use same DTO mapping via `UserDomainMapper`.
- Auth registration and Keycloak upsert remain legacy until explicit future spec.

## US3 extensions

| Endpoint | Port |
|----------|------|
| /api/v1/users/me/saved-chat | SavedChatPort |
| /api/v1/files/{id}/public-links | PublicLinkPort |
