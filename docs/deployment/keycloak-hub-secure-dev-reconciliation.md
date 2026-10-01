# Keycloak Hub Secure-Dev Reconciliation Checklist

## Status and safety boundary

This is a preparation checklist, not an operational procedure. It authorizes
no connection, write, reset, recreation, deletion, re-import, realm export, or
client change on the remote-dev Keycloak instance. Run it only after an
explicit, separately reviewed change approval.

The remote-dev realm remains live infrastructure. Its tracked realm artifact
is the review source of truth; an already imported realm must not be changed by
restart or re-import.

## Required approved input

An operator must supply one approved public HTTPS Hub hostname before an exact
client reconciliation can be prepared. Do not derive it from the Keycloak
hostname or use a wildcard, an IP address, `localhost`, or a plain-HTTP value.

For an approved hostname `<hub-host>`, the exact values to review are:

| Client field | Required value |
| --- | --- |
| Realm | `edol` |
| Client | `edol-hub-web` |
| Redirect URI | `https://<hub-host>/login/oauth2/code/edol-keycloak` |
| Web origin | `https://<hub-host>` |
| Post-logout redirect URI | `https://<hub-host>/` |

The values must remain exact single values. Do not add wildcard redirect URIs,
wildcard web origins, alternate callback paths, direct-access grants, implicit
flow, service accounts, device authorization, offline access, or browser token
storage to accommodate this Hub test.

## Reconciliation review

When approval exists, compare the live client with the tracked realm artifact
and the approved values above. Record a redacted before/after matrix covering:

1. Authorization Code enabled and PKCE method `S256` required.
2. Confidential client authentication retained; its secret stays in the
   approved secret mechanism and is never printed.
3. Access-token lifetime remains five minutes; client session idle and maximum
   remain 30 minutes and eight hours.
4. The exact redirect URI, web origin, and post-logout URI match the approved
   Hub HTTPS hostname.
5. Direct grant, implicit flow, service account, device authorization, and
   offline-token use remain disabled for `edol-hub-web`.

The current accepted remote-dev realm contains the pre-BFF local development
callback and origin. Reconciliation may replace only the approved client values
through the separately reviewed operation; it must never reset, recreate, or
re-import the live realm.

## Completion gate

This checklist is complete only when an operator has recorded redacted evidence
that the exact client values were reconciled without a realm reset or import.
That evidence is a prerequisite for, but does not itself authorize, the
separate Hub-only browser smoke test on a disposable database.
