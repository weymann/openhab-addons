# Concept: `boschoidc` Binding (Bosch/Buderus PointT Cloud API)

## One-Sentence Summary

`boschoidc` lets openHAB users who own a cloud-connected Bosch/Buderus/Junkers heating system (the kind normally controlled via the MyBuderus / Bosch DashApp) read temperatures and heating-circuit/DHW status, and adjust setpoints and operation modes, without leaving openHAB.

---

## Problem Statement

Bosch and Buderus moved their newer heating gateways (e.g. MX300/MX400-class controllers) to a cloud-only architecture: **SingleKey ID** (OAuth2/OIDC login) plus the **PointT REST API**. There is no local network API for these gateways — everything goes through `pointt-api.bosch-thermotechnology.com`.

openHAB users with this generation of hardware currently have no integration option:

- The existing `km200` binding only talks to the older local **KM50/KM100/KM200** web gateways, which expose a LAN-based API. It cannot reach PointT-only gateways.
- `boschshc` covers Bosch Smart Home (security/climate accessories), a different product line and API, not heating controllers.
- Community requests for a Buderus/PointT binding go back several years without a maintained solution (see `binding-request-buderus-heatpump` and `binding-request-buderus-web-gateway` threads on the openHAB community forum).
- The only current workaround is a third-party Home Assistant custom component (`buderus_ha`), which is not usable from openHAB.

---

## Target Audience

**Primary:** openHAB users who own a Bosch/Buderus/Junkers heating system paired with the MyBuderus or Bosch DashApp mobile app (PointT/SingleKey ID backend). Typically homeowners, not necessarily developers — they expect the same setup effort as any other cloud binding (Netatmo, Spotify, etc.).

**Secondary:** openHAB community members who have been asking for Buderus cloud support for years and currently rely on scripts or the Home Assistant workaround.

---

## Why Not Extend `km200`?

| | `km200` (existing) | `boschoidc` (proposed) |
|---|---|---|
| Transport | Local HTTP, LAN only | Cloud REST (PointT API) |
| Auth | Pre-shared key/AES | OAuth2 Authorization Code + PKCE via SingleKey ID |
| Gateway generation | KM50 / KM100 / KM200 | Newer cloud-only gateways (MX300/MX400-class) |
| Reachability | Requires same network as gateway | Works from anywhere via Bosch cloud |

The two APIs are different enough (transport, auth, and the fact that PointT gateways have no local endpoint at all) that this should be a **separate binding**, not a mode inside `km200`. Sharing the resource-path vocabulary (`/heatingCircuits/hc1/...`, `/dhwCircuits/dhw1/...`) at the constants level is a nice-to-have, not a requirement for MVP.

---

## Proposed Scope (MVP)

### Bridge/Thing Model

- **Bridge** `account` — holds the OAuth2 token state (access/refresh token, expiry) for one SingleKey ID login. One bridge per Bosch/Buderus account.
- **Thing** `gateway` — one per heating gateway returned by `GET /gateways/`. Discovered automatically under the account bridge once authorized.

### Channel Groups (per gateway, derived from the resource paths in the reverse-engineering analysis)

| Group | Channels (examples) | Read/Write |
|---|---|---|
| `system` | outdoor temperature, firmware version, global season optimizer mode | read-only |
| `heatingCircuit` (hc1) | current room temperature, manual room setpoint | setpoint is read/write |
| `dhw` (dhw1) | current DHW temperature, charge duration, single-charge setpoint, operation mode, instant-charge switch, reduce-temp-on-alarm switch | mostly read/write |
| `heatSource` (hs1) | heat source status values | read-only |
| `notifications` | active notification/fault trigger | read-only |

Only resources the gateway itself reports as `writeable == 1` are exposed as writable channels — the binding should query this at initialization rather than hardcoding writability.

### Discovery

A `ThingHandlerService`-based discovery service on the account bridge calls `GET /gateways/` and proposes one `gateway` Thing per result, using the gateway's serial ID as a stable identifier.

---

## The Hard Part: Authorization UX

This is the one point that most needs team alignment before `$Architect` starts designing.

SingleKey ID's Authorization Code + PKCE flow redirects to a **fixed, non-configurable custom URI scheme** (`com.buderus.tt.dashtt://app/login`), because the client ID belongs to Bosch's own DashApp — openHAB cannot register its own redirect URI. Home Assistant solves this by having the user log in in their browser, then manually copy the resulting (unreachable) redirect URL back into the integration.

### Option A — Manual URL Paste (Mirrors the Home Assistant Flow)

- Pros: Proven to work against this exact API; no dependency on openHAB core OAuth servlet changes; simple to implement in a config-flow-style Thing configuration parameter.
- Cons: Poor UX for non-developer users — copying a URL from a failed browser redirect is not intuitive; error-prone (state/code must be extracted correctly); no self-service retry without repeating the whole login.

### Option B — Local Relay Page

Run a small local HTTP endpoint in the binding (via openHAB's HTTP service) that the user points their browser to _after_ the SingleKey ID redirect fails, pasting only the raw redirect URL there for parsing; same underlying mechanism as Option A but with a friendlier UI and inline validation.

- Pros: Same reliability as Option A, better error messages, still no need for a public redirect URI.
- Cons: More implementation effort for marginal UX improvement; still requires the copy/paste step since the redirect URI itself cannot be changed.

**Recommendation:** Start with Option A for the MVP (fastest path to a working binding, same trust level as the reference implementation), and revisit Option B once the binding has real users and feedback on where the manual flow breaks down.

---

## Non-Goals (MVP)

- No support for the legacy **XMPP/EasyRemote** protocol (Nefit Easy, Bosch/IVT heat pumps, EasyControl CT200, older Buderus KM50/KM100/KM200 reached via XMPP instead of LAN) — that is a different transport and credential model, already partially covered by `km200` for the LAN case.
- No support for gateway claiming/removal or multi-user gateway management (`pointt.gateway.claiming`, `pointt.gateway.removal`, `pointt.gateway.users` scopes) — read/control only.
- No local/offline mode — the binding is cloud-dependent by nature of the API.

---

## Open Questions for the Team — Resolved

1. **Manual-URL-paste authorization UX for the first release: accepted or blocker?**
   **Decision:** Accepted for MVP. Option A (manual copy/paste of the redirect URL, mirroring the Home Assistant reference implementation) ships in the first release. Option B (local relay page with inline validation) stays as a documented future UX improvement, not a blocker.
1. **OAuth scope list: full DashApp scope or a reduced set?**
   **Decision:** Reduced scope: `openid`, `offline_access`, `pointt.gateway.list`, `pointt.gateway.resource.dashapp`. Matches the MVP feature set (read/write on already-claimed gateways only); no claiming, removal, user-management, tariff, or Castt-flow scopes. Open risk to verify during implementation: whether SingleKey ID accepts a reduced scope list for this `client_id` — if not, fall back to the full DashApp scope list and drop unused permissions from the UI/docs only.
1. **Binding ID: keep `boschoidc` or rename?**
   **Decision:** Rename, using the API's own domain as the naming anchor: `boschthermotechnology` (from `pointt-api.bosch-thermotechnology.com`). Covers Bosch, Buderus, and Junkers uniformly (all run on the same backend), avoids confusion with the unrelated `boschshc` (Bosch Smart Home) binding, and stays discoverable without picking a single brand. Rename must happen now (artifact ID, Java package, class names, `addon.xml`, i18n keys) before more code is written — a follow-up task for `$Architect`/`$Dev`, not `$Concept`.

## Recommendation

Proceed with the MVP scope above (single account bridge, one gateway Thing type, read/write channels for heating circuit and DHW, Option A auth, reduced OAuth scope, binding renamed to `boschthermotechnology`). This delivers real value to a user base that has been asking for it for years, using a resource model that is already reverse-engineered and low-risk. The authorization UX and scope decisions above should be captured as an ADR before `$Architect` starts on the OAuth client design and the project rename.
