# SKI Reference — Hager Energy S10 Pairing

Reference values captured during real-device pairing on 2026-08-04, for reuse when
recreating Things during development.

## Own node (`eebus:service:8fe87fad37`)

- SKI: `fe112136731d4d8b785924ce8deb98789db63654`
- This is the SKI of _our_ SHIP node, generated and persisted by jeebus.ship's
  `KeyManagement`/certificate storage. This is the value the Hager S10 needs to
  trust on its own side.

## Hager Energy S10 (real device)

- SKI: `dd8ba427c0a449180513fbb51bf6f0bbdbf0c821`
- Brand: Hager Energy
- Model: S10
- Type: CEM
- mDNS service instance: `KeoApp`
- Device ID (SPINE): `S10-492011009322` / `d:_i:52158_S10-1`
- Network address at time of pairing: `192.168.1.30:12345`

## Things

- `eebus:peer:5494135cb5:dd8ba427c0a449180513fbb51bf6f0bbdbf0c821` — network-discovered,
  read-only record of the real device (SKI parameter only, no trust implied).
- `eebus:oh-peer:8fe87fad37:cf886182c4` — SKI (Trusted SKI) parameter:
  `dd8ba427c0a449180513fbb51bf6f0bbdbf0c821`. This is the Thing that actually grants
  trust for the S10 to connect.

## Reusing the SKI after deleting the oh-peer

Yes — the same SKI can be reused in a new `eebus:oh-peer` Thing without any new
pairing/confirmation step on the S10, **as long as openHAB has not been fully
restarted since the original successful pairing**.

Reason: trust is stored in jeebus.ship's `KeyManagement.trustedSkis`, a `static`,
process-wide, in-memory map keyed purely by the SKI string. Neither deleting the
oh-peer Thing nor `EEBusHandler.recomputeTrustedSkis()` /
`ShipCommunication.withTrustedSkis()` ever removes a SKI from that map — the call
chain is additive-only. Recreating the oh-peer with the same SKI just re-adds an
already-trusted entry (logged as `The SKI ... is already in the list of trusted
SKIs`, harmless) — no new SHIP handshake confirmation is required on the S10 side.

If openHAB _has_ been fully restarted in between, trust starts empty again and a
full handshake happens again on the next connection attempt — but using the
identical SKI is still correct and expected in that case, since it's the S10's own
identity, not something openHAB assigns.

## Deleting and recreating the oh-service Bridge itself

Trust is not only one-directional. There are two independent things to keep intact:

- **openHAB's trust of the S10** — governed by the static, process-wide
  `KeyManagement.trustedSkis` map (see above). Survives Thing deletion regardless
  of Bridge identity.
- **The S10's trust of openHAB** — governed by _our own node's_ SHIP certificate,
  whose SKI is `fe112136731d4d8b785924ce8deb98789db63654`. This certificate is
  stored in a keystore file at:

  ```text
  ${OpenHAB userdata}/eebus/<thing-uid-with-colons-replaced-by-underscores>.jks
  ```

  (see `EEBusHandler.getKeystoreFile()`). The filename is derived from the
  `eebus:service` Bridge's **Thing UID**, and `handleRemoval()` never deletes this
  file — it survives Thing deletion on disk.

**Consequence:** to delete and recreate `oh-service` + `oh-peer` at runtime without
having to re-confirm pairing on the S10 itself, recreate the `eebus:service` Bridge
with the **same Thing ID** it had before (not an auto-generated new one).
`ShipNodeConfiguration` will find the existing `.jks` file and reuse the existing
certificate, so our node's SKI stays `fe112136...` and the S10 still recognizes us.

If the new `eebus:service` gets a different Thing ID, a brand-new keystore/
certificate is created, our node's SKI changes, and the S10 will most likely treat
us as an unknown device again — requiring a fresh manual confirmation on its side,
regardless of what's entered as the trusted SKI in the new `oh-peer`.
