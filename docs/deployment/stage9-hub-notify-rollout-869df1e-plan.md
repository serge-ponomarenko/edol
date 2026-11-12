# Retained Hub/Notify rollout plan - 869df1e

## Final functional acceptance - 2026-11-12

The operator reported STATUS_OVERLAY_HTTP_UPDATED, confirmed Status photos with
the previous overlay, started a print and explicitly accepted Notify functional
testing. The screenshot shows both DIRECT and AGENT overlay photos for the same
physical print. No further print, printer command or Telegram probe was issued
by the assistant. The sections below retain the historical implementation,
operator artifacts and pending gates as they stood at each step; they are not
instructions to repeat completed rollouts.

Fresh read-only logs checked at 12:48:17 UTC, since the new container startups:

- Core `4b12bb792091`, started 12:43:35 UTC, image
  `sha256:e264d311a0b05a719412d076b387c1c9d84c4ecbda7cd72c0b0d924675285ac3`.
- Notify `f2f578abb130`, started 12:43:54 UTC, image
  `sha256:e10862b0217f286e44e674ecb95425847944deabd7b875ebc929119c4a12f4fd`.
  No WARN/ERROR, exception, 403, polling/configuration or temporary-file cleanup
  error markers. Three Status callbacks; latest ingress counters validatedV2=14,
  rejectedEnvelope=0, processingFailures=0. This is a bounded healthy window,
  not proof of every message type or completion of the newly started print.
- Core is not error-free: camera SSLHandshakeException at 12:44:44/12:45:29,
  FTPS_CONNECT_FAILED at 12:44:56 and two metadata retry warnings. Later metadata
  success markers at 12:45:38/12:46:02 and transfer success at 12:46:02 show
  subsequent successful work. One Hibernate dialect deprecation warning also
  occurred. No 403 marker was found. Root cause of transport failures was not
  investigated or changed; record it separately from Notify acceptance.

Core and Notify retain their separate Compose file lists with
`remediation/compose.status-overlay1.yaml` appended. The final source was already
checked with 31 Notify tests and 11 targeted Core tests, plus the documented
Sonar review. No redundant test run was needed for this documentation closure.
Next: [Stage 9 readiness audit](stage9-resume-handoff-2026-11-12.md), preserving
the separate security, TLS, lifecycle, rotation, lockdown and restoration gates.

## Restore the original overlay over authorized HTTP - operator update ready

The operator confirmed raw HTTP photo delivery, then identified a visual
regression: it bypassed the previous status-image overlay. The authorized
correction adds `GET /api/printers/{id}/camera/status-image/content` in Core.
It streams exactly the output of the existing status-image path/renderer,
without renderer, font, geometry, style or metadata-precondition changes.
Unavailable/unreadable media returns 204 with no-store. Notify uses this binary
endpoint with its existing service token, tenant header, bounded HTTP client,
temporary upload file and text fallback. It does not substitute a raw snapshot.
Existing `/camera/**` scope and printer ownership checks protect the new route.

Focused checks passed: Notify 31 tests, Core CameraControllerTest 4 tests and
CorePrinterHttpAuthorizationTest 7 tests. They cover exact rendered-byte delivery,
missing metadata, foreign-tenant rejection, bounded optional media and text
fallback. Java Sonar reported only the known S1598 empty-path snippet artifact
and the previously reviewed safe temporary-file hotspot S5443 (atomic random
Java NIO creation, owner-only POSIX defaults, finally cleanup). The two runtime
Dockerfiles have no Sonar findings. Bash syntax and read-only Compose merges
passed against the actual retained service file lists. No physical test/build
or runtime replacement was performed by the assistant.

The operator bundle is
`edol-hub/target/notify-rollout-869df1e/status-overlay1-operator.tar`, SHA-256
`8c0224c06802e952983d4b10bd561817600a78accfff5af67d8ec54463e406d9`.
It contains committed source archives and explicit patches, not the worktree,
Git history, local environment files, credentials, logs or SDK archive.

| Artifact | SHA-256 / verified Home MCP draft |
| --- | --- |
| `edol-core-d52fdd0e-source.tar` | `3ffa891336037b757dcd43efd1d74b6c2501476f68bd880f8b3c97ee79b619e7` |
| `core-status-overlay1.patch.tar` | `e2bd0419e25961c07646b52700c970b340147ccd85293774f75eb5872f86e2ef` |
| `notify-status-overlay1.patch.tar` | `f87e6e805ecbfb36b69750b1bcc61c6be14f0b12e52897f6cc81fe20d85c6244` |
| `compose.status-overlay1.yaml` | `db4b0db2bc86b19fd624bbdc7e360571c4a9891d6126abd7903ba0b15c0298cf`; `config-60a4eb62e12a8da97150e0d9c5a494d9` |
| `core-status-overlay1.Dockerfile` | `23430722d39c3b981af7d3af9a1ee7632fb45c83305f144d61f1e1e5bba6e91b`; `config-725bb341b0b6c213f11a76066c408bd8` |
| `notify-status-overlay1.Dockerfile` | `a3e0285026e33681ab1799760db465634b257daddb36ceb1fb9c980d11128f7b`; `config-d96b581958dc8d66c6593227e937cdac` |
| `update-status-overlay1.sh` | `a1b0a75b97418640d68713f46cb154208baaa6c4ad497a0d4d09825a2cb95e64`; `script-463d9acce4b1b2c4ad763549a67151f4` |

Core builds from its retained d52fdd0e source plus only CameraController.java;
Notify builds from 869df1e plus the earlier scheduling/HTTP fixes and the endpoint
correction. Runtime layers come from the exact existing Core/Notify image IDs,
retaining JRE, fonts and dependencies. All existing per-service Compose files,
mounts, CA, tenant configuration, scopes/audiences, identities and network
addresses are preserved. The new overlay changes only application images.
Hub, proxy, AMS, database, broker and Keycloak are not recreated.

Run after the current print finishes: Core restarts first, then Notify after a
bounded health check. Core metadata/cache may need to become available again;
the original renderer prerequisites and text fallback remain in effect.

```powershell
$a = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$a/status-overlay1-operator.tar" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'cd /home/serge/edol-stage9-hardware/remediation && echo "8c0224c06802e952983d4b10bd561817600a78accfff5af67d8ec54463e406d9  status-overlay1-operator.tar" | sha256sum -c - && tar -xf status-overlay1-operator.tar && bash update-status-overlay1.sh'
```

After `STATUS_OVERLAY_HTTP_UPDATED`, use Status in the existing test chat. A new
print is not required for this check. Actual overlay delivery remains pending
operator deployment/observation. Previous image IDs are saved in
`remediation/status-overlay1/previous-{core,notify}-image`; existing images and
volumes are retained. Do not rerun after partial failure: inspect the failed
step first. Duplicate-message cadence and Stage 9 lifecycle/security work stay
separate. No commit or push was requested for this correction.

## Authorized HTTP snapshot delivery - local implementation ready

The operator requested camera photos while preserving tenant isolation and text
fallback. Notify now reads Core's existing `GET /api/printers/{id}/camera/snapshot`
binary endpoint instead of the Core-local status-image path. Core code, broker,
service credentials/scopes and identities are unchanged. The existing endpoint
requires `core.media.read` and printer ownership; the dedicated Notify client
clones the authenticated tenant interceptor, disables redirects and has 3/5-second
connect/read timeouts. Only HTTP 200 image/jpeg with JPEG start/end markers up
to 8 MiB is used. It reads cached media, never commands a fresh printer capture.

Missing/empty/rejected/invalid/oversized/timed-out media returns text status.
The controller stages accepted bytes in a temporary file solely because
Telebof requires File uploads, then deletes it in `finally` after the synchronous
send. Local staging failure also falls back to text. Telegram transport failures
remain observable and are not blindly retried as potentially duplicate sends.
No Core volume or cross-tenant image cache is introduced.

Verification was limited to the affected flow:

- Notify full suite: 31 tests passed; snapshot HTTP cases cover current-tenant
  and bearer propagation, foreign-tenant rejection without image reuse, missing
  tenant, optional media errors/type/size/timeout. Controller tests verify actual
  staged bytes at send and deletion afterward, plus text fallback.
- Existing `CorePrinterHttpAuthorizationTest`: 7/7 passed, including the camera
  route's cross-tenant access rejection. No Docker/physical print was needed.
- Read-only real Core snapshot GETs with Notify identity returned HTTP 200
  image/jpeg for DIRECT (96,777 bytes) and AGENT (96,759 bytes), with JPEG markers.
  Image contents, tokens and raw errors were not displayed or saved.
- Final Java Sonar analysis: package-path S1598 messages are the known snippet
  artifact. S5443 on the temporary-file call was reviewed as safe use, not
  suppressed: fixed prefix, unpredictable atomic NIO creation, no caller-supplied
  filename, and unconditional cleanup. Java 21 `TempFileHelper` source confirms
  default owner-read/write POSIX permissions on the target Linux filesystem.
  No actionable finding was deferred.

Operator artifacts under `edol-hub/target/notify-rollout-869df1e/`:

| Artifact | SHA-256 / Home MCP draft |
| --- | --- |
| `notify-http-snapshot1.patch.tar` | `397c209e2b58c7526b2b7eec61deae3c72a48d7a95abb9ea961dd1373f394216` |
| `compose.notify-http-snapshot1.yaml` | `90c82d4b34bbb0b5f571d0ad8d2089a5d0b96d8d4f24b3272d0281e6cecdbe53`; `config-377f4f52971c9ef1b4f8cb86d63aa3e4` |
| `update-notify-http-snapshot1.sh` | `b3b93ea13cafa63c1509e46d7947c6dd8007b4e6369efd1a056960692a3a8d0d`; `script-ae743c8f5586113c7a0163b2fc983b2f` |

The uncommitted source patch includes all earlier Notify scheduling changes;
its base remains commit 869df1e. Draft readbacks/local hashes match. Bash syntax
and read-only Compose merge passed: only Notify changes, preserving current
CA/mounts and all existing per-service overlays. Neither the new image build
nor runtime replacement has been performed by the assistant. No commit/push.

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/notify-http-snapshot1.patch.tar" "$artifactRoot/compose.notify-http-snapshot1.yaml" "$artifactRoot/update-notify-http-snapshot1.sh" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'bash /home/serge/edol-stage9-hardware/remediation/update-notify-http-snapshot1.sh'
```

After `NOTIFY_HTTP_SNAPSHOT_UPDATED`, verify Status once using an existing test
printer; no new print is needed to check the latest cached image. Actual Telegram
photo delivery and the updated runtime remain pending operator execution and
observation. Duplicate-message cadence and Stage 9 security/lifecycle gates stay
separate. Existing text status remains the fallback if the current cache is empty.

## First real print: ingestion confirmed; optional-image failure isolated

The operator started a cube print on 2026-11-12. Bounded Core logs contain
PRINT_STARTED for AGENT/DIRECT at 10:56:19 UTC, progress through 100%, and
PRINT_FINISHED for both at 11:07:24 UTC. No exception classes appeared in that
inspected Core print window. Screenshots independently confirm initial
Preparing/Printing text notifications and metadata-bearing updates for both.

Notify validated 92 v2 ingresses in the inspected window, with eight processing
failures (its cumulative failure counter increased from two to ten). Failures
align with both printers' progress milestones and finish. Three Status callback
attempts were logged and each was followed by a sanitized command failure.
Thus Core event production and Notify ingestion occurred; progress/finish
Telegram delivery did not pass, consistent with the operator's observation.

Read-only status-image GETs returned non-null Core-local paths for both printers;
neither file was readable inside Notify. Its shared status sender unconditionally
selected `sendPhoto(File)` when a path existed, so optional inaccessible media
blocked progress, completion and Status responses once metadata became available.
No image bytes, token values or raw Telegram errors were printed during diagnosis.

The minimal local fix sends text when the status-image path is not a readable
regular local file, retaining photo delivery where that file is available.
It does not share Core volumes, replay events or change notification cadence.
Shared API 11/11 and Notify 27/27 tests passed; the two new behavior checks cover
unavailable-image text fallback and retained readable-image photo delivery.
Sonar on modified Java files found only the known S1598 empty-path artifact.
Architecture now records optional-media fallback; authenticated binary-media
delivery and duplicate-looking message cadence are deferred in Project TODO.

Prepared operator artifacts under `edol-hub/target/notify-rollout-869df1e/`:

| Artifact | SHA-256 / Home MCP draft |
| --- | --- |
| `notify-status-fallback1.patch.tar` | `f58c7be79af64a15464c565ec9ce09e4fd41d0519cb66731ceace57df8c15ee0` |
| `compose.notify-status-fallback1.yaml` | `89155c2e893aa0189d57754a4af1a27cbefc13de7dedf6e61979e8887d2bc0bb`; `config-7d0729065404ce55ae55a92bf565471e` |
| `update-notify-status-fallback1.sh` | `98e2c8698143faab5a4138698508f1d4a9395b9ef180e2a637af73292edb22cf`; `script-db5bbd26734d262a4e56f2f4e191dac0` |

The explicit uncommitted source patch includes the prior scheduling fix as well
as the text fallback. Draft/local hashes match. Bash syntax and read-only
Compose merge passed: only Notify changes, with current mounts/CA and overlays
preserved. The assistant has not built/applied this new runtime image.

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/notify-status-fallback1.patch.tar" "$artifactRoot/compose.notify-status-fallback1.yaml" "$artifactRoot/update-notify-status-fallback1.sh" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'bash /home/serge/edol-stage9-hardware/remediation/update-notify-status-fallback1.sh'
```

After `NOTIFY_STATUS_FALLBACK_UPDATED`, verify Status once without starting
another print. Previously failed progress/finish messages are not replayed.
Actual delivery of future print progress/finish remains pending, not inferred
from ingestion counters or mocked media tests. The duplicate-message backlog
was recorded as requested; its behavior is deliberately unchanged.

## Automatic events blocked by Core service allowlist

After the TLS update the operator confirmed bot replies to `/start` and `/log`,
but saw no automatic DIRECT Online/Offline notifications after a power cycle.
Read-only inspection at 2026-11-12 09:05 UTC found two validated Notify ingresses
and two processing failures. Command replies prove polling/Telegram delivery,
not Core reads or event processing. The screenshot's `/log` secure-mode refusal
is expected and does not diagnose automatic notification delivery.

Using the existing Notify identity/scopes and configured tenant, both Core
`GET /api/printers` and the DIRECT state GET returned 403. Core's effective
`EDOL_CORE_TRUSTED_SERVICE_CLIENT_IDS` contains only Hub and AMS. Its tenant
filter rejects Notify's `azp` before downstream reads. This explains event
processing failures and the missing printer-selection reply after `/start`.

A minimal operator correction is prepared: add only the existing
`edol-notify-service` identity to that Core allowlist. Preserve the exact
existing Core image, all credentials/mounts/overrides, static Core `.9` and
trusted-proxy `.7`. This deliberately recreates **Core only** and causes a
short Core outage; it does not rebuild code, alter Notify/Hub or issue a printer
command. The correction is not yet applied by the assistant.

| Artifact | SHA-256 / Home MCP draft |
| --- | --- |
| `compose.core-notify-allowlist.yaml` | `95a53797d635f3c1a4500453c80f07e1bbdd9da4f06d633a678cf1325d9ad3ad`; `config-7dc2fea0e9b833410524881fdfbd200f` |
| `update-core-notify-allowlist.sh` | `cbc70be5343780c6d33181d1f50830dc25cbe445f96b951be1b250803c095af8`; `script-fed391f0800c3d0a521747fd42dd2483` |

Both draft readbacks/local hashes match; Bash syntax and the actual Compose
merge passed. Only `edolcore` changed in the rendered service definitions.
The existing source architecture already requires an explicit trusted service
allowlist; no architecture contract or application code changed in this step.

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/compose.core-notify-allowlist.yaml" "$artifactRoot/update-core-notify-allowlist.sh" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'bash /home/serge/edol-stage9-hardware/remediation/update-core-notify-allowlist.sh'
```

After `CORE_NOTIFY_ALLOWLIST_UPDATED`, verify Notify's Core catalog/state GETs
and event processing before announcing print readiness. Polling without errors
alone was not a sufficient readiness gate. Do not repeat a printer power cycle
or replay synthetic MQTT events merely for diagnosis.

The operator also requested a backlog item for repeated Core exceptions while
the printer is off. The inspected tail contained 13 `NoRouteToHostException`
occurrences and one `ConnectException`, with warnings/errors in camera and
Bambu MQTT paths. This is recorded in Project TODO, separately from Agent
OFFLINE and Notify; no retry/logging/firmware behavior was changed now.

## Scheduler replacement observed; Telegram TLS correction pending

The operator ran the scheduler replacement. Notify `15264d5b2453` is running
image `sha256:019d231994857597e6860ff6a7258a3d0723539a23615a0be601be7e3c4f3eba`
with `TELEGRAM_ENABLE=true`, started 2026-11-12 08:45:17 UTC. Settings refresh
now runs without Hub directory/configuration failures, but the first inspected
43-second window contained 19 sanitized polling failures. Thus scheduler
activation is observed; successful polling and print readiness are not.

Secret-safe, read-only host checks using the configured token in memory found
`getMe` successful, no webhook, and access to the configured chat. No message
was sent and no concurrent `getUpdates` consumer was started. JVM metadata
showed its global truststore is `/app/certs/stage9-mqtt-truststore.p12` (PKCS12),
which contains one CA. Telegram TLS verification using those CA anchors failed
with OpenSSL verify error 19. The image's 118 standard public Java CA entries
accepted the same Telegram hostname/chain. These observations identify a real
trust-anchor mismatch; raw Java polling exceptions remain deliberately hidden.

Prepared an operator-only configuration correction using the **same Notify
application image**: create a separate PKCS12 containing its standard public
Java CAs plus the exact existing MQTT CA, retain the current truststore password
without displaying/passing it as an argument, and replace only Notify's
readonly truststore mount. The shared original truststore, all credentials,
TLS hostname/chain verification, Hub, broker and other services are unchanged.
Keytool helper containers run with no network and never start the application.

Artifacts under `edol-hub/target/notify-rollout-869df1e/`:

| Artifact | SHA-256 / Home MCP draft |
| --- | --- |
| `compose.notify-telegram-ca.yaml` | `e599cf21b7491e7134979cb6cc905d891ca2daf34c921b964915e5de244dafc8`; `config-55663568b0e7e62115ee0257a91d041f` |
| `update-notify-telegram-ca.py` | `a34e75aa761acb46feab3b54a19203e107bbaea00848d6ce9df06c4b688cd8d7`; `script-facda7478a2ccc3f7d95311d27fb0e34` |

Draft readback/local hashes match. Python AST parsing passed, Sonar has zero
findings, and read-only Compose rendering changed only Notify. Runtime update
has not been performed by the assistant. No new application build or test
suite is required for this configuration-only correction.

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/compose.notify-telegram-ca.yaml" "$artifactRoot/update-notify-telegram-ca.py" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'python3 /home/serge/edol-stage9-hardware/remediation/update-notify-telegram-ca.py --apply'
```

After `NOTIFY_TELEGRAM_CA_UPDATED`, inspect a settings/polling cycle before
announcing print readiness. Do not start the print while polling failures
continue. This service-specific public/private CA composition does not close
the separate Stage 9 transport, negative-TLS or secret-lifecycle acceptance.

## Observed rollout and Notify scheduling correction

The operator reported `ROLLOUT_READY`, saved the dedicated test token/chat and
enabled Telegram in Hub. Read-only inspection confirmed Hub `305e017c3c52`
running image `sha256:78e311fcacf44753b9d24adb794f31ce09acb6e903946f7cce64a0fa09506b1a`
and Notify `c42919aee4b1` running image
`sha256:a4874d2fa8862e8be7147e338527223a2fbc984f76b9e16513454c5bfc0dc152`,
with `TELEGRAM_ENABLE=true` and Hub URL port 8090. Telegram settings and the
directory were enabled for the same tenant as both DIRECT/AGENT printers;
only presence flags were inspected, not token/ciphertext/chat values.

The operator saw no Online/Offline messages after a printer power cycle.
Notify had two validated v2 ingresses, zero processing failures and no
configuration/polling failure markers in the inspected window. These counters
do not identify the two event types or establish Telegram delivery.

Source diagnosis found `@Scheduled refresh()` without any `@EnableScheduling`
in Notify. The empty runtime bot registry therefore skipped otherwise valid
events before downstream processing. The previous `ROLLOUT_READY` check proved
application startup and configuration ingress, not automatic bot activation.

The local fix adds `NotifySchedulingConfiguration` with scheduling enabled
only for `settings-source=hub`, preserving Home behavior. A real scheduler
regression fails without that annotation and passes with it, without manually
calling refresh or contacting Telegram. Final verification: shared API 11/11,
Notify 25/25, no skips. Sonar on both new Java files found only the known
empty-snippet-path S1598 artifact; no actionable findings. The existing
architectural refresh contract is unchanged; this fixes its missing activation.

Pending operator-only replacement of **Notify only**, from base commit 869df1e
plus the explicit uncommitted source patch (no Hub migration/key/scope replay):

| Artifact | SHA-256 / draft |
| --- | --- |
| `notify-scheduler1.patch.tar` | `ae1cd30761c7ee55c0cd482bd1db2369442d01196e08b15d2c03f4086fb68e54` |
| `compose.notify-scheduler1.yaml` | `196d5848c681f74d19f72dc71384e88771e2b57d0abbb7f6ef773c20b5090bab`; `config-4882c4abe76de3a644be61912774ea66` |
| `update-notify-scheduler1.sh` | `dc2182702546f314429906ef29507b259d9bf4ea775752bad9fa8bd19a3fad0b`; `script-d66181423174550e4d646749c4aeae88` |

Config/script were created as Home MCP drafts and read back with matching
local hashes. `bash -n` passed, and read-only Compose rendering changed only
Notify while preserving `TELEGRAM_ENABLE=true` and the existing overlays.
No runtime replacement or print was performed by the assistant.

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/notify-scheduler1.patch.tar" "$artifactRoot/compose.notify-scheduler1.yaml" "$artifactRoot/update-notify-scheduler1.sh" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'bash /home/serge/edol-stage9-hardware/remediation/update-notify-scheduler1.sh'
```

After replacement, confirm one settings refresh/polling cycle before announcing
print readiness. Old Online/Offline events are not replayed by this fix; no
new power cycle or NFC work is required merely to validate scheduling. The
operator's separate Agent OFFLINE observation is recorded in Project TODO
without assigning a cause or changing firmware/lifecycle behavior.

## Active operator execution - updated authorization

The operator subsequently authorized the key, Notify scope/audience, Hub/V10,
then Notify with **`TELEGRAM_ENABLE=true`**, followed by an operator-started test
print. This supersedes the disabled-master-switch preparation plan below.
All message types for the dedicated test bot/chat are permitted, with the stated
maximum of Graham's number (no practical count restriction for this finite
test). The operator will enter the test token/chat locally in Hub, never in chat.
No new NFC/firmware work or production changes are authorized.

Prepared a one-shot operator script and enabled override under
`edol-hub/target/notify-rollout-869df1e/`. The script performs only the approved
test-environment steps: pinned-source image builds, private Hub backup, key
creation/reuse without disclosure, the exact optional Notify scope and its
conditional Hub audience, then separate Hub and Notify replacements. It verifies
Hub V10/config API and Notify startup, with an empty enabled-tenant directory
before starting Notify. Core/AMS/proxy/other services are not recreated. It
stops on failure and refuses blind replay over a partial rollout stage.

| Artifact | SHA-256 / Home MCP draft |
| --- | --- |
| `deploy-hub-notify-869df1e.py` | `07fb44e223c25587d1ce9e03fad6012ee0055dd08e8d2c8af0f674d276dfc5db`; `script-6cdbec01c26463336ee540ce692ebb02` |
| `compose.telegram-enabled-869df1e.yaml` | `92aa794da4a5ed2f809b3042baa800eaa85edc7bd19eed0a9f6878ed83b23cf8`; `config-5339e95c7a9cb549995e498f927fb9bc` |

Both were created with `create_draft` and verified by `get_draft`; hashes match
the local copies. Python syntax and the three-operation identity write set were
checked offline with fake HTTP, and final Sonar analysis has zero findings.
Existing application suites were not repeated. The source tar remains pinned
to the committed implementation and retains the hash in the artifact table below.

Operator commands from the Windows repository root:

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/edol-hub-notify-869df1e-source.tar" "$artifactRoot/compose.telegram-enabled-869df1e.yaml" "$artifactRoot/deploy-hub-notify-869df1e.py" edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'python3 /home/serge/edol-stage9-hardware/remediation/deploy-hub-notify-869df1e.py --apply'
```

Expected final marker: `ROLLOUT_READY: V10; TELEGRAM_ENABLE=true; enabled tenant
directory empty`. If `ROLLOUT_STOPPED` appears, return only that phase/error-class
marker (and HTTP status if present); do not paste secrets or raw server logs.
The script was prepared, **not executed by the assistant**. Application remains
pending operator execution and observed results. After rollout, use Hub
`/settings/telegram` as the test tenant OWNER to enter the dedicated test token
and exact test chat, then deliberately enable that tenant. Verify its loaded
Notify configuration before telling the operator to start the print. Normal
events may deliver after enabling, as authorized; no startup probe is added.

The remainder of this document preserves the earlier detailed preparation and
rollback reference; its `TELEGRAM_ENABLE=false` commands are historical and must
not be used for this newly authorized enabled rollout.

## Status and approval boundary

Prepared on 2026-11-12; **NOT EXECUTED**. The source change is committed locally
as `869df1ea2c6f2d3af94aa0aa6b351a20df8e6be7` (`EDOL-048-82`). No push.
Only the implementation/documentation commit was authorized for execution.
Server inspection was read-only; candidate configs exist only in isolated draft
staging and as hash-verified local copies. No image was built or deployed, key
generated, database migrated, identity modified, bot configured or print started.

**Proxy drift resolved by operator recovery:** Core is now running at `.9` and
the retained HTTPS proxy at `.7`. Read-only CA/IP-verified probes confirmed
terminal missing-header 400, synthetic-invalid-header 401 and Agent upload GET
401. No further proxy recovery is needed before planning the Hub/Notify update.
The drift described next is the historical pre-recovery observation.

Core was at `10.253.90.7` while its effective
`SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` is `10[.]253[.]90[.]7`. The Stage 9
network has no device HTTPS proxy attachment and `192.168.0.200:18443` is
unreachable. Follow-up inspection found the retained standalone proxy stopped
after an upstream `edolams` DNS startup failure; it was not deleted. See the
[minimal operator recovery](stage9-proxy-drift-2026-11-12.md), which assigns the
existing Core container `.9` and retains `.7` for the proxy and `.8` for Notify.
Recovery has since been performed and verified as described above. Do not interpret a
running Core as an operational trusted-proxy baseline. Resolve this drift in a
separately approved scope and re-inspect addressing before applying this plan;
do not move/recreate Core or start/rebuild a proxy as an implicit Notify fix.
If that recovery changes addresses, regenerate/review the candidate IP pins.

The approval for a later application must name: two new Hub/Notify images,
Hub V10, one installation encryption key, and the exact Notify-only Keycloak
optional scope/audience changes below. Other services, identities, schemas,
volumes and firmware are excluded. Telegram remains disabled throughout.

## Observed baseline

Read-only server observations at approximately 2026-11-12 07:31-07:39 UTC:

| Item | Observed value |
| --- | --- |
| Retained root | `/home/serge/edol-stage9-hardware` |
| Compose project | `edol-stage9-hardware` |
| Hub | `fd31576babfa`, running, `edol-stage9-hub:484b442`, IP `.3` |
| Hub image ID | `sha256:73a0612da8c670ce00fd2389a85e31f47772c5b616c268f50e22e91d8eb805f4` |
| Notify | `bc8ef663d02b`, exited 1 since October 27, `edol-stage9-notify:04ed4cc` |
| Notify image ID | `sha256:6ddc8f538584791ca40a5531d86a95a01e6b594221dda31e71c85d9e1f6d38a3` |
| PostgreSQL | `709b369a1262`, retained bind `data/postgres`, DB `edol_stage9_hardware` |
| Hub migration history | V1-V9 successful; neither Telegram table exists |
| Hub roles | `hub_schema_owner` NOLOGIN, `hub_flyway`/`hub_runtime` LOGIN; all non-superuser, NOBYPASSRLS |
| Flyway authority | `hub_flyway` is a member of `hub_schema_owner` |
| New-table privileges | Default privileges of `hub_schema_owner` in `hub`: runtime SELECT/INSERT/UPDATE/DELETE |
| Existing tenant count | 2 |
| Existing server backend | `72a050b366e6e5fa1c225a8b7730aa455c621302`, no tracked diff |
| Hub env | `secrets/hub.env`; retains its existing Spring JSON scope override |
| Notify env | `secrets/notify.env`; `TELEGRAM_ENABLE=false`; no Spring JSON/scope override observed |
| New key file | `secrets/hub-telegram.env` does not exist |
| Keycloak | `da05a4061360`, realm `edol`, issuer `http://192.168.0.200:18086/realms/edol` |
| Notify client | `edol-notify-service`, internal ID `0694b01b-7957-4042-9c30-de9425291975` |
| Notify current claims | `azp=edol-notify-service`; audiences `edol-core-api`, `account`; existing Core scopes and `tenant.context` |
| Missing authority | No `hub.notify.read` client scope or `edol-hub-api` Notify audience |
| HTTPS leaf | Valid until 2026-11-15 06:58:07 UTC; listener currently unavailable |

MQTT certificate files were not readable by the SSH user; their present validity
is not asserted. Recheck through an approved TLS probe before runtime work.
Retained Core/AMS/MQTT/Keycloak/PostgreSQL container IDs must remain unchanged
by this rollout after any separately approved baseline recovery.

## Concrete artifacts

Local files are under `edol-hub/target/notify-rollout-869df1e/`; preserve them
before any subsequent Maven clean. The tar is `git archive` of only the pinned
module sources, required POMs and two Dockerfiles, not the working tree. It
contains no `.git`, local env files, logs or SDK archive.

| File | SHA-256 |
| --- | --- |
| `edol-hub-notify-869df1e-source.tar` | `9a5d8ce6f2759c6972cf6c8c47ab5a1a01dbf6aeb59ef390ec3229b215dceb5d` |
| `compose.telegram-869df1e.yaml` | `5f54b1081625f07bbdec78868072741441d4d0666398f673ff942cad94eddd0f` |
| `compose.telegram-rollback-869df1e.yaml` | `6b6e65c8b62344ebbc40fd89bd7310315dde128c0c133b3c111eaafdb6f8c5a3` |

The configs were created through Home MCP `create_draft(kind=config)` and read
back through `get_draft`; local copy hashes match:

- Rollout draft `config-c033229182c7a02e9871c32b51986322`.
- Rollback draft `config-71ffbda116601f8b7be605a9815ee3ee`.

Read-only Compose rendering with `--no-env-resolution` against the actual base
and retained Hub override succeeded. It changed only `edolhub` and `edolnotify`,
retained `secrets/hub.env` and appended `secrets/hub-telegram.env`, retained
mounts/ports, pinned Hub `.3` and Notify `.8`, and set Notify's URL to
`http://edolhub:8090` (not the source default port 8080). Notify is forced off.
The render used existing image IDs as syntax-only variable values. New image IDs
cannot be known until the pinned source is built; capture and verify them before
application. No claim of new-image runtime verification is made.

All seven Bash command blocks passed `bash -n` without execution; the embedded
key generator passed Python AST parsing, the PowerShell transfer block passed
PowerShell parser validation, and both Keycloak JSON bodies parsed successfully.
The rollback overlay also rendered successfully against the actual base/old-Hub
files with the exact old image ID, Flyway disabled and Hub IP `.3`. These are
syntax/merge checks, not execution, migration or rollback acceptance.

## 1. Preflight and protected backups - after approval only

1. Resolve the separate proxy drift; inspect the whole retained network again.
   Confirm Hub's current IP and that the proposed Notify IP is free. Verify
   existing certificate trust/validity without insecure fallback or renewal.
2. Confirm the operator has no active print/assignment operation and agrees a
   Hub-only outage. Do not start a print or send any printer command.
3. Capture a secret-free manifest: all Stage 9 container/image IDs, status,
   mounts, networks, ports, per-service Compose file lists, Hub V1-V9 checksums,
   relevant row counts and current Keycloak scope/mapper IDs. Store private
   evidence outside Git. Do not save a raw Docker environment dump.
4. Take a protected consistent Hub-schema backup and a narrow Keycloak metadata
   snapshot before mutation. Preserve original images, env files, overrides,
   database bind and identities. Keep any secret-bearing backups private. Do not
   export/replace the entire realm as an automatic rollback operation.

Example server-side Hub backup (Unix socket; no password argument):

```bash
cd /home/serge/edol-stage9-hardware
umask 077
mkdir -m 700 remediation/notify-869df1e
docker exec -i 709b369a1262 sh -c \
  'exec pg_dump -U "${POSTGRES_USER:-postgres}" -d edol_stage9_hardware --schema=hub --format=custom' \
  > remediation/notify-869df1e/hub-before-v10.dump
test -s remediation/notify-869df1e/hub-before-v10.dump
```

Do not blindly reuse an existing staging directory. Verify its ownership and
contents; do not overwrite a prior backup. A backup's existence alone is not
restore acceptance: inspect its table list and confirm a usable protected copy.

## 2. Transfer and build isolated pinned images

Operator transfer from repository root (PowerShell), after creating the approved
staging directory. Source transfer does not modify the retained backend checkout:

```powershell
$artifactRoot = 'C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e'
scp "$artifactRoot/edol-hub-notify-869df1e-source.tar" edol-server:/home/serge/edol-stage9-hardware/remediation/notify-869df1e/
scp "$artifactRoot/compose.telegram-869df1e.yaml" edol-server:/home/serge/edol-stage9-hardware/remediation/
scp "$artifactRoot/compose.telegram-rollback-869df1e.yaml" edol-server:/home/serge/edol-stage9-hardware/remediation/
```

On the server, verify all three hashes above before extraction or use. Then:

```bash
cd /home/serge/edol-stage9-hardware/remediation/notify-869df1e
mkdir -m 700 source
tar -xf edol-hub-notify-869df1e-source.tar -C source
cd source
docker build --pull=false --label org.opencontainers.image.revision=869df1ea2c6f2d3af94aa0aa6b351a20df8e6be7 \
  -f docker/edol-hub.Dockerfile -t edol-stage9-hub:869df1e .
docker build --pull=false --label org.opencontainers.image.revision=869df1ea2c6f2d3af94aa0aa6b351a20df8e6be7 \
  -f docker/edol-notify.Dockerfile -t edol-stage9-notify:869df1e .
```

If either new tag already exists, stop and inspect it rather than replacing an
unidentified image. Base-image/dependency downloads may still be necessary on a
cache miss; no running service or old image is removed. Dockerfiles package with
tests skipped; the commit's prior source verification is recorded separately.

Capture the built image IDs and revision labels in the private manifest. Set:

```bash
export EDOL_NOTIFY_ROLLOUT_HUB_IMAGE="$(docker image inspect edol-stage9-hub:869df1e --format '{{.Id}}')"
export EDOL_NOTIFY_ROLLOUT_NOTIFY_IMAGE="$(docker image inspect edol-stage9-notify:869df1e --format '{{.Id}}')"
```

Validate both are the reviewed `sha256:` IDs with the exact revision label,
then use those IDs, not mutable tags, in the Compose substitutions below.

## 3. Create the installation encryption key once

Keep existing `hub.env` unchanged. The new key is an installation secret, not a
Telegram token; it stays outside the database, Git, logs and conversation. The
following command refuses replacement and symlinks; run locally on the server:

```bash
python3 - <<'PY'
import base64, os, pathlib, secrets
root = pathlib.Path('/home/serge/edol-stage9-hardware/secrets')
if root.is_symlink() or root.resolve() != root:
    raise SystemExit('Refusing an unexpected secrets directory')
path = root / 'hub-telegram.env'
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
try:
    data = b'EDOL_HUB_TELEGRAM_ENCRYPTION_KEY=' + base64.b64encode(secrets.token_bytes(32)) + b'\n'
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
except BaseException:
    raise SystemExit('Key creation incomplete; inspect the private file locally, do not regenerate blindly')
print('Encryption key file created; value not displayed')
PY
```

Verify regular-file ownership/mode 0600, one expected variable and decoded
length 32 **without printing its value**. Prepare a separately protected key
backup. Never regenerate on a rerun; loss/change makes saved tokens unreadable.
The Compose operator reads this mode-0600 file as env input; it is not mounted
into the application container and does not need world-readable permissions.
Docker inspect can therefore expose the key to Docker administrators. Avoid
raw inspect/config output and keep the broader Stage 9 secret-storage gate open.

## 4. Add the narrow Notify scope and conditional Hub audience

Use the existing local operator authentication mechanism; never put admin or
service passwords in command arguments, chat, Git or raw HTTP logs. Re-resolve
the client UUID by `clientId=edol-notify-service` before writing; assert realm
`edol` on the retained Stage 9 Keycloak, not a production realm.

Apply only this REST change set after snapshot/review:

1. `POST /admin/realms/edol/client-scopes` with:

```json
{"name":"hub.notify.read","protocol":"openid-connect","attributes":{"include.in.token.scope":"true","display.on.consent.screen":"false"}}
```

2. Resolve its returned scope ID. Attach a mapper to that **new optional scope**,
   not to the realm or all clients:
   `POST /admin/realms/edol/client-scopes/{newScopeId}/protocol-mappers/models`:

```json
{"name":"edol-hub-api-audience","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper","config":{"included.custom.audience":"edol-hub-api","access.token.claim":"true","id.token.claim":"false"}}
```

3. `PUT /admin/realms/edol/clients/0694b01b-7957-4042-9c30-de9425291975/optional-client-scopes/{newScopeId}`.
   Record created scope/mapper/assignment IDs. Do not modify other clients,
   existing Core mapper, defaults, service-account roles, `fullScopeAllowed`,
   secrets, broker ACLs or realm-wide scope assignments.
4. Obtain a fresh service token in memory requesting the commit's existing Core
   scopes plus `tenant.context hub.notify.read`. Verify signature/issuer through
   Hub and print only a redacted claim summary: correct `azp`, Hub **and** Core
   audiences and required scopes. Preserve existing claims; do not replace Core
   scopes with the new Hub scope. No Keycloak restart or credential rotation.

If the scope/mapper now exists or the client metadata differs, stop and compare
instead of duplicating or overwriting it. The old Notify client has
`fullScopeAllowed=true`; changing that is a separate lifecycle/security scope.

## 5. Recreate Hub only and apply V10 through Flyway

First render merged config privately, without outputting secret values. Check
both old and new env-file paths, truststore/log mounts, port 18082->8090,
expected network/IP, exact image IDs and retained Hub service scope override.
`config --no-env-resolution --format json` is safe for structural comparison;
any fully resolved config must remain private and must not be pasted into logs.

Operator command after all gates above:

```bash
cd /home/serge/edol-stage9-hardware
docker compose --project-directory "$PWD" -p edol-stage9-hardware \
  -f compose.backend.yaml -f remediation/compose.hub-484b442.yaml \
  -f remediation/compose.telegram-869df1e.yaml \
  up -d --no-deps --no-build --pull never --force-recreate edolhub
```

This is a deliberate **single-service** replacement. Do not omit the service
name/flags, use generic Compose up, `down`, `--remove-orphans`, bootstrap/resume
scripts or a shared full-stack overlay. Hub alone runs Flyway as `hub_flyway`
with the existing `SET ROLE hub_schema_owner`; do not execute V10 SQL manually
and then try to manufacture its Flyway history.

Bound readiness to 120 seconds, using sanitized startup markers and harmless
HTTP/browser probes; never print raw exceptions/configuration. Read-only SQL
must confirm V10 success, unchanged V1-V9 checksums, both new tables owned by
`hub_schema_owner`, ENABLE/FORCE RLS, expected policies/default grants and empty
Telegram settings/directory. Reconfirm runtime remains NOBYPASSRLS and has no
Flyway-history access. Confirm existing tenant/printer data integrity and the
OWNER page's `no-store`, CSRF and token-status behavior. Do not enter a real bot
token or enable a tenant. On any failure, keep Notify stopped and use the
rollback boundary below; never `repair`, drop tables or widen role privileges.

## 6. Recreate Notify only, still disabled

After successful Hub migration/readiness:

```bash
docker compose --project-directory "$PWD" -p edol-stage9-hardware \
  -f compose.backend.yaml -f remediation/compose.hub-484b442.yaml \
  -f remediation/compose.telegram-869df1e.yaml \
  up -d --no-deps --no-build --pull never --force-recreate edolnotify
```

Verify the exact image ID, preserved Notify service/MQTT credentials, existing
truststore at `/app/certs/stage9-mqtt-truststore.p12`, log mount, unchanged
subscriptions, secure profile, URL `http://edolhub:8090`, IP `.8` and
`TELEGRAM_ENABLE=false`. Bound startup/readiness to 120 seconds and observe a
quiet 60-second window. Empty mappings must not cause exit 1. Polling must not
start; no Telegram API request is authorized by this phase.

Using existing Notify credentials locally/in memory, a fresh token with the new
scope must receive `200`, `Cache-Control: no-store`, and `[]` from
`GET http://192.168.0.200:18082/internal/notify/telegram/tenants`. An unauthenticated
request must fail authentication. Use sanitized status/claim summaries only;
never output access tokens or secret-bearing settings responses. These are
ingress/configuration checks, not Telegram delivery acceptance.

Compare before/after manifests: only Hub/Notify container IDs/images may differ.
Core, AMS, PostgreSQL, MQTT, Keycloak and their mounts/volumes/identities must be
unchanged; proxy recovery, if approved separately, has its own evidence. Keep
new tenant settings disabled and leave the dedicated bot token/chat unentered.
Record the approved override list for each recreated service for future restart.

## Rollback boundaries

1. Stop the new Notify by its freshly verified exact container ID. Do not start
   the old Notify image: its baseline was stopped/exited 1. No bot delivery.
2. For a Hub rollback, use the prepared old-image override, with Flyway disabled
   for the old binary so V10 history is not rewritten or downgraded:

```bash
docker compose --project-directory "$PWD" -p edol-stage9-hardware \
  -f compose.backend.yaml -f remediation/compose.hub-484b442.yaml \
  -f remediation/compose.telegram-rollback-869df1e.yaml \
  up -d --no-deps --no-build --pull never --force-recreate edolhub
```

3. V10 is additive. Retain its tables, rows, Flyway history and encryption key;
   do not drop/downgrade or restore an entire shared PostgreSQL volume. Verify
   the old image's readiness against the retained schema; this runtime rollback
   is a pending gate, not an already tested result. A DB restore needs a separate
   reviewed target/outage decision if transactional migration recovery fails.
4. Remove only the optional Notify scope assignment created by this rollout.
   Remove its new mapper/scope only after verifying no other client uses them
   and with explicit rollback approval. Preserve original Core scopes/audience,
   all clients/users/credentials. Existing access tokens can remain valid until
   expiry; scope removal is not instant revocation. Never import a whole realm
   snapshot over retained active identities.
5. Preserve artifacts/evidence/key for diagnosis. No image/volume/network prune,
   history rewrite, firmware action or automatic cleanup of previous overrides.

## Completion criteria for this update

Hub serves OWNER settings using V10 and the preserved tenant boundary; Notify
stays running with Telegram disabled; the new Notify scope/audience grants only
the intended exact internal reads; both service images and all unchanged service
IDs/mounts are recorded. No secret value or Telegram message was exposed/sent.
This is a configuration rollout only. Agree the exact future test tenant/chat,
logical printer(s), event types/media and enforceable message cap separately.
Do not announce print readiness or close security/lifecycle/TLS gates from this
update. In particular, encryption at rest does not establish approved transport
for future bot-token entry or real Hub-to-Notify secret delivery.
