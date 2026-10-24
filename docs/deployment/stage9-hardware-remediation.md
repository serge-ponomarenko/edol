# Stage 9 Hardware Acceptance Remediation — 2026-10-24

Latest AMS-only follow-up: see **AMS terminal HTTPS enrollment and independent
MQTT processing diagnostics** at the end. It records source diagnostics and
offline results; physical terminal enrollment remains FAIL. It does not reopen
bootstrap, change the proxy or close Agent's separate remaining gates.

## Current state: resume, do not bootstrap

Latest source status: the final **Continuous MQTT, verified transfer completion
and dashboard commands** section supersedes the historical MQTT-pause guidance.
The operator's Core 185ed932 / Agent 8db73079 report confirms real Agent camera
uploads/display with that workaround. The new continuous-MQTT source is local
and has NOT been deployed, flashed or physically accepted.

The October 24 operator report (backend `ecea9b0`, Agent `bf84769`)
supersedes the historical checkpoints below. Verified physical results:
Agent HTTPS enrollment and MQTT provisioning HTTP 200, Agent ACTIVE,
DIRECT and AGENT real telemetry and independent Hub print jobs, DIRECT live
camera. The IPv4 SAN TLS correction is physically verified; **TLS -9984 is no
longer the blocker**. Both logical printers intentionally observe one P1S.

The latest camera/revoke report supersedes that checkpoint: backend `9d42b1c`
and Agent `5aba847` were tested. Firmware flash, previous-identity revocation,
new enrollment, ACTIVE/MQTT READY and automatic pairing-page status update are
**operator-confirmed PASS**. Camera captures real JPEGs (about 81 KB), but
snapshot HTTPS upload fails with `NOT_TRUSTED` (`0x00000008`), not CN_MISMATCH.
Agent camera display is therefore **BLOCKED**, not PASS. New full print lifecycle,
model transfers and AMS hardware acceptance are **NOT RUN**. The final
camera/revoke section records this increment; its source has **not been deployed
or flashed**. Earlier sections are historical evidence.

The subsequent retest at Core `185ed932` / Agent `0252423` confirms the same
HTTPS leaf validates in an explicit TLS probe but fails camera upload with
leaf-only NOT_TRUSTED. Camera capture passes, upload/display remain blocked.
New print evidence also confirms Printing cards and DIRECT Core model/metadata
success, but Agent FTPS data TLS fails allocation (`-32512`), followed by an
aborted HTTP body. See the final **TLS memory-pressure follow-up** section.
The current source change has not been flashed or deployed.
Do not repeat bootstrap, enrollment, identity preparation or historical checks.
Earlier deployment/flash instructions are historical, not instructions to repeat.

The existing Debian run is `/home/serge/edol-stage9-hardware`, Compose project
`edol-stage9-hardware`, base `compose.backend.yaml`. Preserve its database,
Tenant A/B, printer `01a16493-e4ed-766b-8e75-53e168d39efa`, Keycloak realm,
Dynamic Security state, CA/private keys and device settings. No migration is
introduced by this remediation. No deployment or hardware action is implicit.

## Confirmed causes and fixes

| Report | Evidence and classification | Remediation |
| --- | --- | --- |
| HW-01 | `PrinterManagementController.editForm` never supplied `agentManagementAvailable`, but `printers/form.html` required it. Confirmed UI defect. Read-only container inspection also found no `EDOL_HUB_AGENT_ENABLED`; this separate deployment prerequisite defaults OFF. | Populate navigation after owner authorization; explain a disabled management feature or disabled AGENT printer. Enable the Hub feature in the candidate override. |
| HW-01 enabled state | Core `AgentPairingService.lockPrinter` requires an enabled AGENT printer. The reported printer is disabled. This is an intentional security prerequisite; disabling also retires authority. | Explain it in UI. The owner must explicitly enable and save the existing AGENT printer before pairing, after confirming this is the intended printer. No DB edits or automatic enable. |
| AMS-02 / SEC-01 | Read-only live Hub service grant had `aud=[edol-core-api,account]`, missing `edol-ams-api`; authenticated AMS management GET returned 401. Issuer already matched `http://192.168.0.200:18086/realms/edol`. | Add only required service audience mappers; keep issuer/audience validators. Candidate requests `core.agent.manage` as well as existing scopes. Distinguish authentication, authorization and connectivity in UI. |
| NOTIFY-01 | Base YAML required `TELEGRAM_ADMIN_ID` and bot token even with `TELEGRAM_ENABLE=false`. | Empty/zero inert defaults; enabled Telegram still validates its token. No dummy operational values needed for disabled Telegram. |
| NOTIFY-02 | `NotifyRecipientResolver.validateMappings` rejected an empty map unconditionally in secure mode. | Require nonempty mappings only for enabled Telegram. Unknown tenant/chat still fails closed; malformed provided mappings still fail startup. |
| AMS-01 | `AmsPrinterTenantResolver.validateMappings` rejected zero printers before any request. | Allow empty configuration, retaining complete/unique mapping validation and rejection of every unknown printer/envelope. Keep the existing real printer mapping. This does not add dynamic mapping discovery. |
| UI-01 | Generic connection fields were shown for every mode. Secure identity is actually persisted in Core and LAN access is configured on Agent. | Hide the unused fields in secure AGENT mode, explain the portal/pairing path, preserve submitted/stored values and existing validation. Home/DIRECT behavior remains. |
| UI-02 | Generic catches obscured upstream HTTP status and connection failures. | Safe categorized errors and structured class/status-only logs; never print exception bodies, credentials or tokens. |
| Proxy / CFG-02 | Read-only proxy config routes Agent prefix correctly but lacks terminal prefix, explicit forwarding-header stripping and the model upload size limit. Forwarding trust must not be enabled on a LAN-exposed raw backend. | Generate a candidate for the existing proxy, adding only the two device API prefixes, stripping client forwarding headers and preserving TLS. Candidate binds Core raw HTTP to loopback and removes AMS published ports before enabling trusted forwarding. No production proxy change. |
| DIRECT telemetry | Follow-up operator stack traces show `BambuMqttConnection.messageArrived` calling tenant-requiring persistence directly on a Paho callback thread. Connect-time tenant scope does not propagate to that thread. | Execute report/disconnect callbacks through the existing trusted runtime tenant executor. Identity comes from the runtime binding, not the topic/payload. Home uses its existing no-tenant executor. |
| DIRECT camera / reconnect | `LegacyCameraProvider`, `RtspsCameraProvider` and the MQTT connection manager read a derived repository query without an RLS-setting transaction. A Java tenant scope alone is insufficient. | Use the existing `PrinterManagementService.getConnection` transaction. Camera/network I/O remains outside that short transaction. No bypass roles or schema changes. |
| False Ready / zero metrics | Connection establishment marks transport online before state parsing. Overview treated online + missing gcode state as Ready and rendered default primitive zeros; failed telemetry could leave this misleading view. | Wait for processed telemetry before consumer connectivity publication. Overview explicitly shows waiting/unavailable when no usable telemetry exists and avoids invented zero-temperature rendering in that state. |

Firmware source confirms secure enrollment, MQTT provisioning and uploads use
fixed `/api/agent/v1/...` paths and `https://<CoreHost>:<CorePort>`. The legacy
upload-path field is used only in Home. `SecureTransport` supplies the compiled
CA, and clients connect with the configured hostname. No plaintext fallback is
added; the same run CA must validate both broker and Agent HTTPS certificates.
These source facts do not prove a physical enrollment or TLS handshake.

## Automation and deployment plan

`tools/stage9/resume.py` is a standard-library-only Debian-host helper:

- `check`: one bounded read-only service diagnostic, short JSON suitable for
  saving as the next chat's checkpoint. Uses existing container credentials in
  memory; prints only states, public claim summary and HTTP codes. No DB or
  MQTT requests, pairing creation, raw logs, `docker compose config` dump or
  device access. It does not test the full browser/firmware path.
- `prepare`: exclusively creates new nonsecret candidate files under
  `remediation/`; does not overwrite existing configuration or deploy.
- `identity --allow-identity-update`: explicit operator action ensuring the
  required service audience mappers and optional Hub Agent scope on existing
  clients. Uses existing Keycloak bootstrap admin configuration in memory.
  No client, user or realm is recreated; existing unrelated mappings remain.
  The API paths follow the [Keycloak Admin REST contract](https://www.keycloak.org/docs-api/latest/rest-api/index.html).
  If bootstrap admin access is unavailable, stop and use the established admin
  procedure; never reset credentials to make the script work.

After reviewing these fixes, copy/use this checkout on the Debian host. All
commands below are **operator deployment instructions, not already executed**.
From the checkout, run the initial diagnostic once:

```sh
stage9_source_dir="$(pwd -P)"
python3 tools/stage9/resume.py check > stage9-before.json
python3 tools/stage9/resume.py prepare --image-tag stage9-remediation
```

`check` exits 1 when blocked; save the JSON and resolve its named failure,
rather than re-running all historical gates. The candidate uses Compose
`!override` to replace exposed ports, requiring Compose >=2.24.4; normal list
merging would retain the unsafe old binding. See the
[Compose merge contract](https://docs.docker.com/reference/compose-file/merge/).
The helper checks this version before generating anything. It never updates
Compose on the host.

Build the four repaired service images from this reviewed checkout, without
starting them (reuse existing Docker/Maven caches):

```sh
for service in core hub ams notify; do
  docker build -f docker/edol-$service.Dockerfile -t edol-stage9-$service:stage9-remediation . || exit 1
  docker image inspect edol-stage9-$service:stage9-remediation --format '{{.Id}}' || exit 1
done
```

Before starting, confirm all four builds completed. The follow-up DIRECT fixes
now require a repaired Core image too. Keep the previous images as
rollback targets. From the run directory, validate the merged candidate without
printing resolved secrets:

```sh
cd /home/serge/edol-stage9-hardware
docker compose -p edol-stage9-hardware -f compose.backend.yaml -f remediation/compose.resume.yaml config --quiet
```

Review only the new candidate files, not a secret-expanded Compose dump. Keep
the current issuer and existing AMS printer mapping. The override retains the
base environment files/volumes and uses the same realm/DB/broker. Confirm
existing Core integration/printer runtime and Hub MQTT remain enabled. The
operator then explicitly authorizes the following narrowly scoped deployment:

1. Run `python3 "$stage9_source_dir/tools/stage9/resume.py" identity --allow-identity-update`.
   Re-running is safe: existing matching audience mappers are retained.
2. From the run directory recreate **only** the four application services:

   ```sh
   docker compose -p edol-stage9-hardware -f compose.backend.yaml -f remediation/compose.resume.yaml up -d --no-deps edolcore edolhub edolams edolnotify
   ```

3. Review the existing proxy container's config mount. Back up its current
   config in the private run directory and install the generated candidate at
   that same mounted source path, preserving cert/key mounts, port 18443 and
   network. For a file bind mount preserve its inode by updating contents in
   place. Run `docker exec edol-stage9-agent-https nginx -t`; only after success
   run `docker exec edol-stage9-agent-https nginx -s reload`. If validation fails,
   restore the previous contents and do not reload. This affects only the
   existing disposable proxy. Core raw HTTP is now loopback-only; remote direct
   access to port 18081 is deliberately unavailable.
4. Run `python3 "$stage9_source_dir/tools/stage9/resume.py" check > stage9-after.json`.
   Required service status paths must now succeed; known unpaired devices may
   return 404. A successful check means READY_FOR_OWNER_ACTION, not hardware
   PASS. Check intentionally does not enable the printer or issue a code.

The temporary proxy remains outside Compose lifecycle; its mount/ownership is
recorded rather than replaced with a second container. Rollback selects previous
application images and proxy config, preserving current DB/authority state and
necessary audience fixes. Never restore stale broker authority data or expose
raw HTTP while retaining framework forwarding trust.

## Verification record

The bounded regression sets contain **59 distinct Java tests PASS**, zero
failures/errors/skips in their successful final runs, plus **9 offline Python
tests PASS**. This is targeted evidence, not a new full-reactor result.
An initial new-test fixture failure was corrected (global template model and
explicitly distinct mock providers); the corresponding final template and
controller tests passed. Existing Core broker/routing/tenant tests executed
with real disposable Mosquitto through the remote-Docker wrapper. No test used
the operator's Stage 9 broker or printer.

The offline command is:

```sh
python3 -m unittest discover -s tools/stage9 -p 'test_*.py'
```

Maintainer regression commands (already executed; not operator prerequisites):

```text
.agents/skills/edol-remote-docker-verification/scripts/with-remote-docker.ps1 mvn -B -pl=edol-core -am -Dtest=CoreTenantContextFilterTest,SecureAgentHttpTest,AgentMqttManagementTest,SecureAgentBrokerTest,SecureAgentRoutingTest -Dsurefire.failIfNoSpecifiedTests=false test
.agents/skills/edol-remote-docker-verification/scripts/with-remote-docker.ps1 mvn -B -pl=edol-hub,edol-notify,edol-ams -am -Dtest=AgentManagementPageTest,PrinterManagementControllerTest,AmsTerminalManagementClientTest,TerminalManagementApiControllerTest,CoreAgentManagementClientTest,AgentManagementControllerTest,TelegramDisabledStartupTest,NotifyRecipientResolverTest,AmsPrinterTenantResolverTest,TerminalPairingManagementServiceTest,TerminalAuthenticationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Core's five-class subset passed before the failed Hub fixture in the initial
combined reactor invocation. The equivalent scoped command is shown above;
it is not claimed as an additional run. Subsequent final runs selected only
the changed Hub/Notify classes; repeated tests are not added to the distinct
59-test total. Final `NotifyRecipientResolverTest` alone passed 6 tests after
the analyzer-driven helper extraction.

Read-only Sonar covered all 16 modified/new supported Java, HTML and Python
files in that initial increment. Actionable findings were fixed; only the known pathless Java S1598
snippet artifact remains. No suppression was added. `git diff --check` passed.

The helper's `check` was also executed read-only against the preserved run. It
confirmed Core printer GET 200 (disabled AGENT), no Agent binding (404), AMS
management 401, stopped Notify, absent Hub feature flag, correct issuer and
missing AMS audience. Requesting the additional Agent scope succeeded; the
deployed default Hub registration simply did not request it. The public CA
verified the existing HTTPS listener, which returned 403; enrollment remains
NOT RUN.

The generated override was merged with the **actual existing**
`compose.backend.yaml` using the host's Compose parser, in a temporary test
fixture without deployment: PASS. It resolved only loopback Core port 18081;
Hub retained its existing 18082 binding; AMS/Notify had no published ports.
That initial merge retained Core image `04ed4cc`; the later DIRECT increment
selects the repaired Core image as well. Secrets were not printed or copied into the candidate.
Earlier historical Compose NOT RUN records are not this candidate's evidence.

Identity mutation, repaired image deployment, candidate proxy activation and
post-deployment positive service checks remain NOT RUN pending the explicit
deployment step. Physical enrollment/telemetry/AMS acceptance remains blocked
until then; no unit test or read-only diagnostic changes that status.

## Hardware acceptance resumption: exact next operator steps

1. Open the existing Hub printer edit page:
   `http://192.168.0.200:18082/printers/01a16493-e4ed-766b-8e75-53e168d39efa/edit`.
   Confirm Owner A and the existing P1S in AGENT mode. Enable and Save it through
   the UI. No recreation, tenant seeding or SQL is needed.
2. Click **Manage printer agent**, then **Pair agent**. Keep its five-minute
   code private. If a fresh code cannot be issued, stop on the exact safe UI
   error; do not proceed to flash or reconstruct API calls manually.
3. On the already flashed Agent AP, set Core Host `192.168.0.200`, Core Port
   `18443`, EDOL MQTT Host `192.168.0.200`, MQTT Port `18884`; keep actual printer
   LAN values private and enter the new code. Hosts have no scheme prefix.
   The legacy Core Upload Path does not control secure routes. Save & Reboot
   once and observe its result. Do not reuse the AMS code or MQTT passwords.
4. Confirm ACTIVE plus MQTT READY in Hub, then actual telemetry for this printer.
   A TLS probe/HTTP 403, process startup or successful firmware upload is not
   telemetry evidence. If pairing fails, record the numeric enrollment/TLS
   status and one corresponding safe server failure; do not start a new audit.
5. Continue the combined runbook from Agent uploads, then FLASH-T/AMS-READ using
   the operational secure terminal build. The same TLS listener now routes
   `/api/terminal/v1/**` to AMS. Confirm its actual USB port independently.
6. Continue one approved AMS metadata write/inverse, remaining physical TLS and
   recovery/revoke/replace cases, then restore both devices. Reuse accepted
   source/disposable security and Stage 8 evidence; do not manually repeat unit
   test cases or provision extra owners/printers before the real positive path.

Keep one small results file: latest service check, last successful physical
step, next action, and unresolved failure. No full logs or six-hour chat replay
are required to resume. Missing optional metrics/counters remain unavailable,
never zero. Every unexecuted physical case remains NOT RUN.

## Cleanup journal and remaining gates

Preserve `edol-stage9-agent-https`, `proxy/agent-https.conf`, its server
certificate/private key/CSR, `certs/ca.pem`, local `stage9-ca.pem`, existing
service env files, realm mapper history and backups. New helper candidates and
redacted JSON checkpoints belong to this same run. No cleanup has been performed.

The Agent worktree now ignores the entire `stage9-restoration/` directory in
addition to binary/key patterns. Keep its existing image; compute its SHA256
locally without dumping contents. It is a saved local build artifact, not a
verified dump of the device's former firmware. Original terminal enrollment
cannot be recovered by reflashing: retain the previously agreed fresh-owner
pairing/spare-device restoration plan. No erase or secret extraction.

P0 remains open until repaired services are deployed and owner pairing, device
enrollment/MQTT/telemetry and terminal paths are actually observed. Source tests
alone do not close it. Production rollout, fresh-environment bootstrap, cleanup,
device restoration and final hardware acceptance remain separate gates.

The follow-up DIRECT test printer is
`01a168b6-1317-73ea-83eb-55785be588f3`. After the same approved redeployment,
observe new telemetry and camera frames for that existing record; no printer
recreation, credentials change or firmware flash is prescribed by these fixes.
The new regressions use synthetic callbacks, restricted disposable PostgreSQL
and fake camera transport, never this physical printer. Source evidence does
not mark its actual DIRECT connection/camera PASS.

## Follow-up DIRECT regression evidence

An additional **15 Java tests passed**, with no failures/errors/skips in their
successful final runs: callback tenant isolation/Home 3, telemetry ordering 2,
runtime isolation 3, camera controller 2, overview service 2, real PostgreSQL
configuration/camera RLS 3. Node template tests passed 3; the existing Python
helper tests passed 9 again (not nine additional distinct cases).

The PostgreSQL fixture uses the production naming strategy, lazy repository
bootstrap and tenant-aware JPA transaction manager. It reproduces a reconnect
configuration load without an outer transaction, real RLS-authorized camera
configuration access with fake SSL/frame I/O, and denied foreign/no-context
reads. Runtime database access uses a non-superuser, non-BYPASSRLS login. It
does not exercise physical printer TLS or claim a real camera frame.

Maintainer commands, not additional manual acceptance prerequisites:

```text
.agents/skills/edol-remote-docker-verification/scripts/with-remote-docker.ps1 mvn -B -pl=edol-core,edol-hub -am -Dtest=DirectMqttTenantCallbackTest,BambuTelemetryConsumerTest,DirectRuntimePersistenceTest,CoreRuntimeIsolationConfigurationTest,CameraControllerTest,PrinterOverviewServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
node --test tools/stage9/test_printer_overview.cjs
```

The combined invocation initially stopped at persistence-fixture setup after
10 Core tests passed. After correcting the fixture's missing production
settings, the three persistence cases passed in a separate scoped invocation;
the two Hub overview tests also passed separately. The 15-test count combines
these successful class results, not a claimed final full-reactor invocation.

Sonar checked the follow-up Java/HTML/JavaScript sources and updated Python
helper, for 26 supported changed source files across both increments. Only
the known pathless Java S1598 artifact remains. The touched overview template
also gained pinned, SHA384-verified CDN dependencies and small analyzer-driven
equivalent JavaScript changes. No suppression was added. Physical DIRECT and
Agent observations await the approved deployment and operator session.

## Follow-up checkpoint: DIRECT background tasks and Agent TLS (-9984)

This section is the current resumption procedure. Previous sections preserve
historical source/disposable evidence; they are not a new preparation checklist.

### Evidence and root causes

- **DIRECT-RECOVERY-01, confirmed code defect:** `RecoveryStartupCoordinator.
  startRecoveryIfNeeded` submitted `runRecoveryWorkflow` directly. A new virtual
  thread does not inherit the trusted tenant scope. It now executes through
  `CoreRuntimeTenantExecutor` before synchronization, printer lookup or commands.
- **DIRECT-RECONNECT-01, confirmed code defect:** `PrinterTelemetryReconnectScheduler.
  reconnect` called `isConnected` on the scheduler thread, outside that executor.
  Provider selection can read printer configuration. Both the check and optional
  connect now run inside the worker's trusted runtime scope. Unknown bindings and
  foreign active scopes still fail closed; no payload-derived tenant or RLS bypass.
- **Helper readiness, confirmed code defect:** a verified HTTPS authentication
  denial HTTP 401 was excluded from the expected probe statuses. `resume.py`
  now accepts 401 alongside 403/405, only with verified TLS. Device-management
  404 is an unpaired state. READY_FOR_OWNER_ACTION is not enrolled/physical PASS.
- **Agent clock readiness, confirmed code weakness, physical causality unknown:**
  the old lower-bound-only epoch check could skip synchronization. Secure firmware
  now requires an observed SNTP callback during this boot and checks clock
  continuity against its monotonic anchor. Timeout remains fail closed. It does
  not authenticate NTP or bypass certificate validity checks.
- **AGENT-ENROLLMENT-01 remains P0 / unresolved physical cause.** Arduino
  2.0.17 / local mbedTLS 2.28.7 reports aggregate -9984 and clears TLS state on
  failed-connect cleanup. The lab-only client captures available verification
  flags before cleanup. TIME/TLS and enrollment failures report public endpoint,
  numeric UTC epoch, NTP state and embedded public CA PEM hash; never credentials
  or response bodies. Unavailable flags stay unavailable, not numeric zero.
- An IP-only SAN is a candidate name-check incompatibility: upstream mbedTLS
  2.28.7's [SAN verifier](https://github.com/Mbed-TLS/mbedtls/blob/v2.28.7/library/x509_crt.c)
  handles DNS names there. This is an inference, not proof of the deployed vendor
  verifier or the certificate received by the ESP32. Collect device flags and
  actual SAN before proposing a DNS/certificate change. Do not regenerate CA,
  disable hostname checking or treat Debian OpenSSL success as ESP32 success.
- DIRECT overview initialization later reached Ready/IDLE: no persistent UI
  defect established, no UI change. First-use NVS NOT_FOUND and an unmatched
  WebServer request are unclassified observations, with no proven blocking effect.

### Targeted verification of this increment

PASS: 7 Java tests (BackgroundRuntimeTenantTest 4,
DirectMqttTenantCallbackTest 3), zero failures/errors/skips; 10 resume-helper
Python tests; 9 firmware native executables; 3 CA-hook Python tests; Home,
secure and secure_smoke ESP32 builds. Both secure environments without the CA
failed as required. These are source/build results, not hardware acceptance.

The initial Maven run encountered stale generated reactor artifacts; scoped
`clean test` passed. The remote-Docker wrapper failed SSH with exit 255 before
Maven. The three DirectRuntimePersistenceTest PostgreSQL/RLS cases were **NOT
RUN this increment**; retain their previous accepted evidence without counting
them again. No full reactor, live TLS handshake, deployment, serial or flash
was performed. Read-only Sonar checked all 8 supported changed sources (3 Java,
5 Python): only the known pathless Java S1598 snippet artifact; no suppression.
C++ is unsupported by the available analyzer.

Maintainer commands (already executed, not operator prerequisites):

```text
mvn -B -pl edol-core -am -Dtest=BackgroundRuntimeTenantTest,DirectMqttTenantCallbackTest -Dsurefire.failIfNoSpecifiedTests=false clean test
py tools/stage9/test_resume.py
py scripts/run_host_tests.py
py test/test_secure_trust.py
pio run -e lolin_d32
pio run -e lolin_d32_secure
pio run -e lolin_d32_secure_smoke
```

Firmware commands run from the Agent worktree; secure builds require its existing
public CA file environment variable. The preserved pre-build smoke binary is
`stage9-restoration/pre-verification-secure-smoke-firmware.bin`, SHA256
`5b9c36167864237dd7c869901088d56380cf0161ec412004c569224a2c665cc9`.
This is a local build artifact, not a dump of installed firmware.
Verified new smoke build SHA256:
`1a853c1fbc7257f382c3acab58570fb5ce27db32924e37319cedd4f9fdb9a723`.
A fresh build may differ because build epoch is embedded.

### Operator resumption: one action, one observation

**No action is authorized by this document alone.** Review the source increment,
then obtain explicit deployment/flash approval. Do not rerun prepare, identity,
all-service deployment, historical RLS checks or proxy reload.

1. Update only the clean existing backend checkout to the reviewed fix commit
   after it is published. Build **Core only**, with an explicit unique tag.
   These shell commands stop on failure and preserve the currently running
   image as a rollback tag before changing anything:

   ```sh
   cd /home/serge/edol-stage9-hardware/backend
   (
     set -eu
     stage9_revision=$(git rev-parse --short HEAD)
     stage9_tag="stage9-runtime-$stage9_revision"
     if docker image inspect "edol-stage9-core:before-$stage9_tag" >/dev/null 2>&1; then
       echo "Rollback tag already exists; review the previous attempt before continuing" >&2
       exit 1
     fi
     stage9_core=$(docker compose -p edol-stage9-hardware -f ../compose.backend.yaml -f ../remediation/compose.resume.yaml ps -q edolcore)
     test -n "$stage9_core"
     stage9_previous=$(docker inspect --format '{{.Image}}' "$stage9_core")
     docker image tag "$stage9_previous" "edol-stage9-core:before-$stage9_tag"
     docker build --pull=false -f docker/edol-core.Dockerfile -t "edol-stage9-core:$stage9_tag" .
     docker image inspect "edol-stage9-core:$stage9_tag" --format '{{.Id}}'
     stage9_override="../remediation/compose.$stage9_tag.yaml"
     set -C
     printf 'services:\n  edolcore:\n    image: edol-stage9-core:%s\n' "$stage9_tag" > "$stage9_override"
     docker compose -p edol-stage9-hardware -f ../compose.backend.yaml -f ../remediation/compose.resume.yaml -f "$stage9_override" config --quiet
     docker compose -p edol-stage9-hardware -f ../compose.backend.yaml -f ../remediation/compose.resume.yaml -f "$stage9_override" up -d --no-deps --pull never edolcore
   )
   ```

   If the override already exists, stop and review it; do not overwrite it.
   Record the new tag/override and rollback tag in the session checkpoint. The
   explicit `-t` and image inspection avoid the previous latest/tag mismatch;
   no retagging of unrelated images or rebuilding four applications is needed.
   Observe DIRECT telemetry and camera on its existing dashboard and one
   reconnect interval; capture only any new recovery/reconnect exception.
   Use `python3 tools/stage9/resume.py check` only if a named readiness question
   remains; its result does not replace physical enrollment.

2. Prepare the new Agent lab image in the existing worktree. The previous
   flashed firmware lacks TIME/TLS diagnostics; this is a source change requiring
   a separately approved upload. Keep previous build artifacts in the ignored
   restoration directory; never commit binaries or the public CA copy.

   ```powershell
   Set-Location C:\Users\Ponomarenko\IdeaProjects\edol\.worktrees\edol-agent-stage9
   $env:EDOL_AGENT_ROOT_CA_FILE = (Resolve-Path stage9-restoration/stage9-ca.pem).Path
   pio run -e lolin_d32_secure_smoke
   if ($LASTEXITCODE -ne 0) { throw "Secure smoke build failed" }
   Get-FileHash .pio/build/lolin_d32_secure_smoke/firmware.bin -Algorithm SHA256
   ```

   Confirm the actual Agent port with the operator (last confirmed COM9; COM16
   was an AMS port). Only then, on explicit approval:

   ```powershell
   pio run -e lolin_d32_secure_smoke -t upload --upload-port COM9
   ```

   No erase-flash, filesystem upload or NVS reset. Clear the process-local CA
   variable afterward if it was introduced for this build.

3. On the already configured Wi-Fi connection, open the existing lab console
   at 115200/LF. Send **TIME**, then **TLS**. TLS only contacts configured Core
   `192.168.0.200:18443`; it sends no HTTP request/pairing code. If Wi-Fi is not
   connected, use the existing portal connection procedure first. Capture only
   the bounded SMOKE clock/TLS lines and numeric errors.
   - NTP timeout: investigate the device network's UDP 123 reachability; stop
     enrollment attempts. Do not accept a guessed epoch.
   - expired/future flags: compare recorded device UTC with actual certificate
     validity. Name flag: inspect the public certificate SAN against the configured
     host and SDK verifier. Trust flag: compare embedded CA PEM SHA256 with the
     existing public CA and actual chain. More than one flag can be set.
   - Unknown/unavailable flags: retain the exact error; do not infer success or
     change certificates speculatively.
   - For a name/date question only, an operator can inspect the public server
     certificate without any key or credential dump:

     ```sh
     openssl s_client -connect 192.168.0.200:18443 -CAfile /home/serge/edol-stage9-hardware/certs/ca.pem -verify_return_error </dev/null 2>/dev/null | openssl x509 -noout -dates -ext subjectAltName
     ```

4. Only after the device TLS probe is verified, open the existing AGENT printer
   in Hub. Confirm its intended enabled state through the UI; explicitly enable
   and Save if still disabled. Issue a **fresh** Agent pairing code, enter it
   privately in the existing setup portal and Save & Reboot once. Keep Core
   host/port 192.168.0.200/18443 and MQTT host/port 192.168.0.200/18884.
   Legacy upload path is ignored in secure mode. Observe ACTIVE and MQTT READY,
   then genuine telemetry. A TLS probe, status404 or startup log cannot mark
   these PASS. If TLS still fails, stop at its diagnostics rather than issuing
   repeated codes.

5. Continue the existing combined runbook from HTTPS uploads, then AMS FLASH-T
   and read-only management. Confirm the terminal's actual USB port separately.
   Metadata write/inverse, remaining security/revoke/replace/recovery cases and
   restoration each retain their existing operator approval boundaries. Do not
   mark AMS or any unexecuted physical case PASS.

### Rollback and cleanup obligations

Core rollback changes only the image: create a new small override referencing
the recorded `edol-stage9-core:before-stage9-runtime-<revision>` tag, validate it
with the same base/resume files, then `up -d --no-deps --pull never edolcore`.
Do not rerun identity, restore a database snapshot or reset Dynamic Security.
A source-only rollback cannot restore consumed pairing codes; use normal owner
lifecycle when needed. No DB migration or API/topic change was introduced here.

Agent rollback requires explicit upload approval and the preserved known
previous binary with its recorded hash, or the reviewed previous source/CA
build. Confirm port; use PlatformIO's bounded upload procedure or the existing
workbook's binary restoration procedure. Do not invent raw flash offsets.
Reflashing alone preserves NVS and does not undo issued/revoked backend authority.
Retain the agreed normal-firmware/settings restoration obligations for both
devices. No erase, secret extraction or silent authority recreation.

Cleanup remains **NOT STARTED**. Preserve the existing proxy container, both
proxy config/backup paths, HTTPS server certificate/key/CSR, CA, realm/mappers,
volumes, service env files, generated resume overrides and restoration artifacts.
Add the unique Core override/tag, redacted checkpoint and local pre-build binary
to the existing cleanup journal; none are authorization to delete resources.

## Setup UX follow-up: single automatic provisioning session

The subsequent firmware source change supersedes the separate TIME/TLS-first
operator sequence above for normal first-time setup. No deployment, flash,
reset or existing Stage 9 change was performed for this increment.

After a separately approved build/upload of this new source, the operator uses
one form: Wi-Fi, existing printer LAN settings, Core/MQTT endpoints and a fresh
owner-issued code, then **Save & Reboot**. Saving returns the same form with
progress; the device performs Wi-Fi → NTP → Core TLS verification → enrollment
→ MQTT credentials → MQTT TLS/subscriptions automatically, then reboots after
success. There is no separate unpaired configuration pass or required console
command. Keep the setup page open; bounded network calls may delay status
updates for tens of seconds. Reconnect to the same AP if STA association changes
its radio channel, then reopen http://192.168.4.1 without resetting anything.

For the existing disposable run use Core 192.168.0.200:18443 and MQTT
192.168.0.200:18884. Keep real printer credentials private. Legacy upload path
remains irrelevant in secure mode. The single form prefills saved settings and
masks Wi-Fi/access-code fields. Pairing code is never prefilled or returned in
progress responses. Local setup still has its deliberately open trust boundary.

The code remains only in RAM for up to five minutes from entry; authoritative
server expiry can occur earlier. Blank resubmission does not extend its lease.
Pre-send Wi-Fi/NTP/TLS failures retry automatically after 15 seconds; Retry
saved settings offers an immediate bounded retry. Correct fields on the same
form without re-entering unchanged values. Success/rejection/uncertain enrollment
clears the pending code. A code expiring during a network step is cleared when
the bounded call returns to the flow; the backend still enforces actual expiry.
Power loss before enrollment loses the code, preserving settings; enter a fresh
code in the same form. With an existing identity, blank code retains it.

MQTT provisioning now establishes verified TLS before setting the persistent
one-time attempt marker. A failed pre-send handshake is retryable. After a
request could have reached Core, lost/malformed responses or storage failure
still require owner replacement; the marker is never silently cleared. Restart
with a marker but no usable secret opens setup with the replacement explanation.
No HTTP credential is reused as an MQTT password; existing routes/topics/NVS
keys and Home provisioning semantics are preserved.

TLS -9984 remains **unresolved physical cause**: no new TIME/TLS output was
provided with this request, and device access is explicitly prohibited. New
secure_smoke setup TLS/enrollment paths emit the existing safe clock/verification
diagnostics automatically. If the automatic sequence fails TLS, collect only
SMOKE clock/TLS lines and numeric errors; TIME/TLS are optional lab diagnostics,
not an extra setup prerequisite. Do not guess time/name/trust causes, regenerate
certificates, disable verification or repeatedly consume pairing codes.

Resume physical observations only after reviewing and approving this firmware
increment. ACTIVE/MQTT READY, genuine Agent telemetry/uploads and AMS remain
NOT RUN. Existing backend Core-only deployment/rollback instructions still
apply if that increment is not yet deployed. Firmware rollback uses the
preserved reviewed previous artifact/source and normal approved upload; no
erase/NVS reset or authority/database rollback. An already consumed code cannot
be restored by firmware rollback. All existing cleanup obligations remain.

Source verification for this setup increment: 10 native executables passed,
3 CA-hook Python tests passed, all three ESP32 environments built successfully.
The native runner's final Windows temporary-directory cleanup failed after all
executables passed; this is a runner limitation, not a clean runner exit or
hardware result. The verification record in firmware docs includes the retained
local build artifact and rollback-source limitations. Sonar checked the changed
Python runner plus extracted portal HTML/JS, with no remaining findings; C++ is
unsupported. No backend application code changed or Maven checks were repeated.

## Confirmed TLS root cause and IPv4 SAN fix

This checkpoint supersedes the earlier unknown-cause notes. The operator has
now supplied NTP/CA confirmation and device verification flags **0x00000004
(CN_MISMATCH), without time/trust flags**, for HTTPS 192.168.0.200:18443. The
existing server certificate has matching IP Address SAN and passes OpenSSL IP
verification. No server certificate or trust-anchor change is indicated.

### Exact bundled implementation

Local SDK metadata identifies Arduino-ESP32 2.0.17, PlatformIO Espressif32
6.13.0, ESP-IDF v4.4.7 at `38eeba213a`. The IDF mbedTLS gitlink resolves to
Espressif **2.28.7**, commit `2b8e772fc1cb0732cda3bae7d1e9d6f4cfaf63d9`.
This is the vendor fork, not an assumed upstream release.

In that exact [x509_crt.c](https://github.com/espressif/mbedtls/blob/2b8e772fc1cb0732cda3bae7d1e9d6f4cfaf63d9/library/x509_crt.c),
`x509_get_subject_alt_name` retains raw SAN entries, but `x509_crt_check_san`
only checks dNSName. `x509_crt_verify_name` does not fall back to CN when a SAN
extension exists. Arduino's unchanged `mbedtls_ssl_set_hostname(IP-string)`
therefore yields CN_MISMATCH despite an exact binary iPAddress SAN. The
OpenSSL/ESP32 difference is explained by the different name matchers.

### Minimal secure correction

The EDOL secure transport subclass installs a **per-connection IPv4 identity
callback** before Arduino TLS setup. It requires an exact binary four-byte
iPAddress SAN on the leaf; it neither accepts CN-only certificates nor treats
a textual DNS SAN/wildcard as an IP identity. Matching replaces only the leaf
name decision; mismatching explicitly sets CN_MISMATCH. All other flags and
non-leaf certificates remain untouched. DNS retains native verification.
The expected hostname still reaches set_hostname and VERIFY_REQUIRED remains.

The same client is used by Core HTTPS enrollment/uploads/provisioning, TLS
diagnostics and EDOL MQTT. Each connection reinstalls its own callback; stop
resets the previous TLS context, preventing reuse of another endpoint's IP.
Home and the acknowledged local-printer LAN TLS limitation are unchanged.
The secure platform is pinned at 6.13.0 and a version guard requires an explicit
review before changing mbedTLS. No SDK files, backend routes, topics, NVS,
CA/certificates, proxy, broker or other infrastructure configuration changed.

### Source evidence and next operator action

The exact vendor source was built locally with desktop crypto and disposable
certificates unrelated to Stage 9. **16/16 cases PASS**:

| Control | Result |
| --- | --- |
| Original verifier, matching IP SAN | Reproduces CN_MISMATCH |
| Correct IP SAN with strict callback | Accepted, flags 0 |
| Foreign IP / wrong SAN / CN-only / textual DNS IP / malformed IP SAN | Rejected, CN_MISMATCH |
| Mixed SAN including correct IP | Accepted |
| DNS matching / foreign hostname | Accepted / rejected by native verification |
| Wrong CA / expired / future / damaged signature | Rejected; non-name flags preserved |
| Callback before ssl_setup, hostname and VERIFY_REQUIRED | Preserved by real vendor TLS context setup |
| Parser and non-leaf flag controls | Passed |

The existing **10 native executable tests passed**, including per-client hook
installation, independent IP contexts, DNS reuse and ambiguous numeric address
rejection. Their runner still exits 1 for the documented Windows temp-directory
cleanup error after tests pass. **3 CA-hook tests and all three ESP32 builds
passed**. Secure ELF links the verifier and all connect overrides; Home ELF does
not. Sonar checked both changed Python runners without findings; C++/CMake is
unsupported. No backend Maven/history checks were repeated.

Reproduction commands, pinned source hashes and fixtures are recorded in Agent
`docs/verification.md` and `scripts/run_ip_san_tests.py`. These are maintainer
checks, not another operator preparation checklist. Tests do not establish a
physical ESP32 TLS handshake; **new firmware hardware acceptance remains NOT RUN**.

After review and separate flash approval, use the existing secure_smoke build
procedure with the **same CA and endpoints**. Confirm actual Agent USB port;
last recorded COM9 is not permanent identification. There is no backend redeploy
or certificate renewal prescribed by this fix. Open the existing setup form,
retain saved fields, enter a fresh owner code and Save & Reboot once. Observe
verified TLS, enrollment, MQTT READY and genuine telemetry. TIME/TLS remain
optional diagnostics if another failure occurs; no unpaired setup pass is needed.
Stop on a new nonzero verification flag; do not remove verification or replay
uncertain one-time requests.

No physical action or Stage 9 network access occurred in this increment. The
pre-build local smoke artifact was preserved as
`stage9-restoration/pre-ip-san-fix-7f00b48.bin`, SHA256
`777de1ab437b59732bbb06f2e6349b83e6baf2808b046776c748362a90687e0a`.
It is not a device dump. Rollback requires approved firmware upload from that
reviewed artifact or previous source/CA; no reset, erase, DB/authority rollback
or CA replacement. Earlier firmware retains the IP-SAN defect. Generated local
test CA/keys stay ignored under .pio and are never device trust material.
Existing cleanup/restoration obligations remain; cleanup is NOT STARTED.

## October 24 — printing, model transfers and Agent camera (A–G)

### Source diagnosis and changes

| Finding | Root cause and implemented boundary | Physical retest |
| --- | --- | --- |
| A: Ready during RUNNING, no progress | `PrinterStateService.updateGcodeState` did not set `printing`; only `MetadataParsedEventListener` set it after metadata success. The existing overview API/template uses that bit and already polls every five seconds. Derive it from PREPARE/RUNNING/PAUSE, clear on other reported states, preserve on telemetry deltas. Late metadata cannot resurrect a finished print. No DTO or overview production template change. | Pending |
| B: DIRECT configuration missing | `DirectModelTransferProvider.requestModel` used a repository query without the transaction that installs the RLS tenant setting. Use `PrinterManagementService.getConnection`; directory preparation and FTPS run after the short configuration transaction. Foreign/missing tenant remains denied. | Pending |
| C: Agent DOWNLOAD_FAILED | Physical logs do not identify the FTPS substage. Confirmed source defects: PASV parser required `227 (` rather than accepting normal human-readable prefixes, reply reader did not handle multiline replies, and download could discard bytes buffered at EOF. Correct those paths, bound reply/idle waits, classify failures without logging raw replies. **The precise physical cause is still unproven.** | Blocked pending approved firmware retest |
| D: retries lose tenant | `MetadataAcquisitionService` scheduled a plain callback. Every acquisition attempt now executes through `CoreRuntimeTenantExecutor` using the trusted printer binding; it never derives a tenant from model/MQTT payloads or supplies a global fallback. | Pending |
| E: DIRECT requires Agent binding at finish | `DefaultAgentCommandGateway.disableSnapshotScheduler` published unconditionally. Snapshot controls now check persisted connection mode; DIRECT produces no Agent command. AGENT missing/revoked authority still fails closed. | Pending |
| F: Agent camera unavailable | `AgentCameraProvider.supports` returned false; the separate firmware snapshot scheduler was enabled only after successful metadata parsing. Core's existing 15-second camera background loop now requests the existing `UPLOAD_SNAPSHOT` command. Firmware uploads asynchronously through credential-bound HTTPS into `CameraSnapshotStore`. A pending request returns no bytes and cannot replace a cached frame. Metadata parsing no longer enables a second periodic scheduler. | Pending; no image success claimed |
| G: enrollment status stale | The pairing page rendered server status only once. Add owner/printer-authorized GET `/printers/{printerId}/agent/status`, no-store, returning only state/MQTT state/expiry. Poll every five seconds while visible, add Refresh status and safe retry feedback, clear displayed code on ACTIVE/revocation/expiry. Polling never issues a code or rotates authority. | Pending |

Agent FTPS diagnostics use `DOWNLOAD_FAILED:<stage>` in the existing reason
field, plus numeric `ftp_code`, `transferred` and `expected` in local logs.
Stages include PASV_REPLY, PASV_PARSE, DATA_CONNECT, RETR_REPLY, DATA_READ,
DATA_TIMEOUT, HTTP_STREAM_WRITE and COMPLETION_REPLY. No raw FTP replies,
request/response bodies, codes or credentials are logged. The correlated Core
500 may follow an incomplete upload; the report does not establish that it was
the original download failure. `SecureAgentController` now records status and
exception class (and issued IDs for storage I/O errors) without bodies/secrets.

PASV human-readable prefixes are not fixed protocol text; see
[RFC 1123 section 4.1.2.6](https://www.rfc-editor.org/rfc/rfc1123#section-4.1.2.6).
Unicode model basenames such as `Куб.gcode.3mf` remain supported. Routes,
MQTT topics, payload fields, CA checks and NVS keys are unchanged. The existing
local-printer LAN certificate-verification limitation is unchanged and separate
from verified EDOL TLS. This increment adds no schema/configuration migration.

### Automated evidence

| Check | Actual result |
| --- | --- |
| Selected Core/Hub regression tests: LivePrintingStateTest, MetadataRetryTenantTest, AgentSnapshotRoutingTest, DirectModelTransferProviderTest, AgentManagementControllerTest, AgentManagementPageTest, PrinterOverviewServiceTest, CameraControllerTest | 20 PASS, 0 failures/errors/skips |
| DirectRuntimePersistenceTest with isolated PostgreSQL 17/Testcontainers, Flyway 11, runtime role without RLS bypass | 4 PASS, 0 failures/errors/skips; includes real transaction scope for DIRECT model configuration, missing/foreign tenant rejection and no network I/O inside transaction |
| SecureAgentHttpTest | 3 PASS, 0 failures/errors/skips |
| Node overview rendering and Agent status polling tests | 7 PASS |
| Firmware native executables | 11 distinct PASS; FTPS production source tested separately. Full runner stops after ten passing executables on Windows WinError 32 temporary-directory cleanup; runner exit is not a clean PASS |
| Python CA-hook tests | 3 PASS |
| ESP32 Home / secure / secure_smoke builds | All three SUCCESS with the existing public CA for secure builds |
| Physical model transfers, new Agent camera path, corrected overview and finish lifecycle | NOT RUN for this increment |

Final distinct Java total is **27**, not the sum including repeated checks.
After Sonar corrections, AgentSnapshotRoutingTest (3),
AgentManagementPageTest (2) and the seven Node cases passed again. The extracted
Python source/include selection and standalone FTPS test also passed again.
Read-only Sonar covered all changed supported sources: 16 Java, one HTML,
two JavaScript and one Python file. Actionable findings were corrected. Only
the known pathless Java S1598 snippet artifact remains (real package paths
match); nothing was suppressed. The available analyzer does not support C++.
Both repositories passed `git diff --check`.

Maintainer reproduction (no device required):

```powershell
mvn -B -pl edol-core,edol-hub -am '-Dtest=LivePrintingStateTest,MetadataRetryTenantTest,AgentSnapshotRoutingTest,DirectModelTransferProviderTest,AgentManagementControllerTest,AgentManagementPageTest,PrinterOverviewServiceTest,CameraControllerTest' -Dsurefire.failIfNoSpecifiedTests=false clean test
# DirectRuntimePersistenceTest requires the isolated remote-Docker verification skill.
mvn -B -pl edol-core -am -Dtest=DirectRuntimePersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl edol-core -am -Dtest=SecureAgentHttpTest -Dsurefire.failIfNoSpecifiedTests=false test
node --test tools/stage9/test_printer_overview.cjs tools/stage9/test_agent_status.cjs
```

### Minimal operator resumption — separate approval required

1. Preserve current image references and rollback artifacts. After explicit
   deployment approval, update the existing backend checkout and build only
   Core and Hub using the existing packaging procedure. Tag the actual built
   images with a new unique tag before changing the candidate override; do not
   assume Maven creates that tag. Deploy only those two services using existing
   `compose.backend.yaml` and `remediation/compose.resume.yaml`, with
   `--no-deps --pull never`. Do not run prepare, identity, bootstrap or migrations,
   recreate infrastructure, reload the proxy or change authorities/certificates.
2. Open the existing AGENT printer's Agent page. Refresh status must show ACTIVE
   and the MQTT state, and keep updating. **Do not re-pair an already working
   Agent.** Keep its existing identity, portal settings, CA and endpoints.
3. Check Agent camera after one background interval plus upload time. Verify a
   genuinely changing image with the operator. If unavailable, record only safe
   command/upload status and numeric errors. A command sent or an HTTP endpoint
   response alone does not establish a live camera PASS.
4. For the FTPS changes, request separate firmware upload approval and confirm
   the actual Agent USB port (COM9 is historical). Build `lolin_d32_secure_smoke`
   with the same public CA via `EDOL_AGENT_ROOT_CA_FILE`, preserve/hash the
   current build artifact if present, and upload firmware only. No erase-flash,
   filesystem upload, NVS reset, new code, CA or credential rotation.
5. Observe one operator-approved real print through both existing logical
   printers. Check Printing/progress on both cards, metadata/preview/filament for
   the Unicode model, and STARTED → RUNNING → FINISHED with independent sessions
   and jobs. DIRECT finish must produce no Agent-binding error. Force no extra
   print commands from the test agent. Allow an ordinary failed acquisition to
   retry and confirm no missing tenant context. If Agent transfer still fails,
   collect the new stage, FTP numeric code, byte counts and HTTPS status; stop
   there rather than changing credentials/TLS or repeating bootstrap.
6. Only after these read-only observations pass, resume the existing separately
   approved recovery/revoke/replace and AMS terminal hardware plan. AMS hardware
   is NOT RUN. Device restoration and cleanup remain separate approved actions.

No full historical reactor/security audits are needed for this retest. Known
enrollment/TLS and telemetry PASS remain accepted; source tests do not convert
pending hardware cases into PASS. No deployment or hardware action occurred
during this remediation.

After separate deployment approval and updating the existing clean backend
checkout to the reviewed revision, the two-service command sequence is:

```sh
cd /home/serge/edol-stage9-hardware/backend
(
  set -eu
  stage9_tag="stage9-print-$(git rev-parse --short HEAD)"
  stage9_override="../remediation/compose.$stage9_tag.yaml"
  test ! -e "$stage9_override"
  for stage9_app in core hub; do
    if docker image inspect "edol-stage9-$stage9_app:before-$stage9_tag" >/dev/null 2>&1; then
      echo 'Rollback tag exists; stop and review the previous attempt' >&2
      exit 1
    fi
    stage9_container=$(docker compose -p edol-stage9-hardware -f ../compose.backend.yaml -f ../remediation/compose.resume.yaml ps -q "edol$stage9_app")
    test -n "$stage9_container"
    stage9_image=$(docker inspect --format '{{.Image}}' "$stage9_container")
    docker image tag "$stage9_image" "edol-stage9-$stage9_app:before-$stage9_tag"
    docker build --pull=false -f "docker/edol-$stage9_app.Dockerfile" -t "edol-stage9-$stage9_app:$stage9_tag" .
  done
  set -C
  printf 'services:\n  edolcore:\n    image: edol-stage9-core:%s\n  edolhub:\n    image: edol-stage9-hub:%s\n' "$stage9_tag" "$stage9_tag" > "$stage9_override"
  docker compose -p edol-stage9-hardware -f ../compose.backend.yaml -f ../remediation/compose.resume.yaml -f "$stage9_override" config --quiet
  docker compose -p edol-stage9-hardware -f ../compose.backend.yaml -f ../remediation/compose.resume.yaml -f "$stage9_override" up -d --no-deps --pull never edolcore edolhub
)
```

Record the override path and both rollback tags. Stop on any error; do not
overwrite an existing override or rollback tag. These commands are instructions
only and were **not executed** against the Stage 9 environment.

### Rollback and cleanup journal

Rollback only changed Core/Hub application images to the recorded prior image
references using the existing Compose files and `--no-deps --pull never`.
If firmware was explicitly updated, restore the reviewed previous artifact or
`bf84769` source with the same public CA, after separate operator approval.
Do not restore a database snapshot, revoke/reissue credentials, reset NVS or
roll back broker state. Existing identities and telemetry data must survive.

Preserve the existing HTTPS proxy `edol-stage9-agent-https`, both proxy config
copies, `remediation/agent-https.resume.conf`, CA/server keys and certificates,
the realm/authorities/volumes, and all `stage9-restoration` artifacts. No new
Stage 9 infrastructure resource was created. A secure_smoke build artifact did
not exist immediately before this increment's builds; no pre-build binary
backup is claimed. Existing restoration files remain ignored and untouched.
Isolated Testcontainers verification completed and its tunnel closed; it is not
part of the live Stage 9 run. Cleanup is **NOT STARTED**.

## October 24 — snapshot TLS and revoke follow-up

### Current hardware checkpoint

Operator evidence at backend `9d42b1c` and Agent `5aba847` establishes:
firmware flash, old identity revoked, fresh enrollment, ACTIVE/MQTT READY,
automatic pairing-page update, Bambu MQTT and real camera JPEG capture PASS.
It does **not** establish HTTPS snapshot upload or a displayed Agent camera.
Upload failed twice with `-9984`, `verify_flags=0x00000008` (NOT_TRUSTED),
with no name/time flags. NTP and the embedded public CA match the report.
New printing lifecycle, model transfers and AMS hardware acceptance are NOT RUN.

### Defect 1 — confirmed lifecycle defects; trust cause remains bounded

The installed Arduino-ESP32 2.0.17 source has these exact semantics:

* `WiFiClientSecure.cpp::stop` closes an owned socket, assigns -1, then calls
  `ssl_client.cpp::stop_ssl_socket`. That function frees TLS state and finally
  `memset`s the entire context, changing the descriptor back to **0**.
* `HttpUploadClient::connect` first aborts the previous upload; preparation then
  calls `setTimeout`, and `SecureTransport::Client::connect` stops the client
  again. Unlike a freshly constructed enrollment client (socket -1), this path
  can configure an invalid descriptor or close descriptor 0 owned by another
  connection. It explains the reported `setSocketOption errno=9` and is unsafe
  with simultaneous camera/MQTT connections.
* `WiFiClientSecure::setTimeout(uint32_t seconds)` takes **seconds**. Preparation
  passed 10000 (intended milliseconds), configuring 10,000 seconds. It now passes
  10; HTTPClient's own timeout API remains in milliseconds and is unchanged.

The secure subclass now always overrides `stop`, including the normal secure
build, restores socket -1 after SDK cleanup, and uses that override before
every new connection. Repeated abort/end/failed-connect cleanup is safe.
The SDK's `_CA_cert` pointer survives stop; the parsed CA chain is freed and
reloaded from that pointer on connect. `prepare` still installs the same CA.
Enrollment's HTTPClient references the transport; uploads own one transport
member by value; AgentService holds a reference, not a copy, to the uploader.
There is no CA replacement or shared TLS context introduced by this change.

**The supplied evidence does not prove that descriptor misuse caused the
NOT_TRUSTED flag.** That flag may indicate failure to build/validate a trusted
chain; errno=9 and the prior name mismatch are not proof of that cause.
Accordingly the source fix is not labelled a proven snapshot-TLS resolution.
The lab-only verifier now reports numeric flags at each certificate depth and
SHA-256 of the received leaf DER certificate, allowing the actual peer seen
during upload to be compared with the working TLS probe/enrollment. It prints
neither the certificate body nor request/response bodies or credentials.
Unavailable flags remain unavailable, never invented zeroes.

The IPv4 callback still changes **only** the leaf CN_MISMATCH decision for an
exact binary IPv4 SAN. DNS uses native identity validation. NOT_TRUSTED, time,
signature and other flags are preserved. The smoke observer delegates that same
decision and cannot accept a failed chain; normal secure builds omit the extra
certificate diagnostics. Strict CA verification and plaintext rejection remain.

### Defect 2 — lifecycle-aware background camera

`DefaultAgentCommandGateway.uploadSnapshot` previously checked only connection
mode; a revoked/unpaired Agent therefore reached the throwing `commandTopic`
every 15 seconds. `AgentMqttService.availableCommandTopic` now offers the same
validated suffix, usable binding and current-tenant filter as ordinary command
routing, returning empty when no usable authority exists. Only background
snapshot publication uses this optional path. `AgentCommandPublisher` also
requires the printer runtime's online state and safely skips if the secure MQTT
component is disabled. Home keeps its existing snapshot topic/behavior.

Revocation removes usability even before heartbeat expiry, so stale online state
does not permit snapshots for the retired identity. Replacement READY authority
plus online state resumes requests without a Core restart. Tenant context is
still required; missing/foreign tenant cannot publish, and ordinary commands
retain their missing-binding exception. No authority creation or provisioning
occurs on this path. The broker still enforces revocation for any message that
was already in flight; a source test is not new broker-revocation evidence.

### Defect 3 — authenticated revocation signal is not implemented

The broker disconnects/denies the old credential, but MQTT does not distinguish
owner revocation from all authentication/network/configuration failures.
`AgentPairingService.authenticate` and `core.find_agent_credential` intentionally
reject retired credentials with the same unauthorized outcome as invalid ones;
there is no credential-bound firmware lifecycle/status response confirming
revocation. A generic HTTPS 401 is therefore insufficient too.

Automatic Setup Mode is **not implemented**: inferring revocation from those
signals would break recovery from temporary TLS/MQTT outages. An explicit,
authenticated revocation-confirmation protocol is recorded as deferred work in
`docs/ai-workflow.md`. Until that separate contract exists, use the lab `SETUP`
command after an operator-confirmed revoke, then the existing portal and a fresh
owner code. Wi-Fi, printer settings, endpoints, CA and existing NVS are retained;
no erase/reset is needed. Source regression failures do not clear identity.

### Automated verification for this increment

| Check | Actual result and limit |
| --- | --- |
| AgentCameraLifecycleTest | 4 PASS: ACTIVE → REVOKED/unpaired → replacement/offline → online, foreign/missing tenant, absent secure MQTT component and Home compatibility |
| AgentSnapshotRoutingTest, SecureAgentRoutingTest, SecureAgentHttpTest | 7 PASS combined; final distinct Java total **11**, 0 failures/errors/skips |
| Native production-code host executables | 12 distinct PASS, including the new combined enrollment/upload lifecycle executable; fake sockets/TLS are not real TLS acceptance |
| Exact audited Espressif mbedTLS 2.28.7 | 16 real-vendor certificate cases PASS, including foreign CA, damaged signature, expired/future, wrong IP and DNS negatives |
| Python CA-hook tests | 3 PASS |
| ESP32 Home / secure / secure_smoke | Three successful builds, existing CA reused for secure environments |
| Read-only Sonar | All five changed Java files and one Python file analyzed; only the known pathless Java S1598 snippet artifact remains, no suppression. C++ unsupported |
| Physical verified snapshot upload/dashboard and revoke/reconnect retest | NOT RUN for this source increment |

The secure transport test models the audited SDK's zeroed descriptor and verifies
25 repeated cleanup/reconnect cycles without descriptor-0 closes/options. The
combined test uses the production enrollment, upload and secure transport code,
then repeats JPEG uploads, rejects four TLS failure categories before HTTP
transmission and recovers without clearing identity. Certificate-depth logging
does not alter trust/time flags. Vendor certificate tests establish the verifier
controls separately; neither these nor fakes establish a live ESP32 handshake.

Initial Maven incremental/clean attempts and PlatformIO attempts were blocked
by local build/cache permissions. Repeating with authorized access succeeded;
the final results above replace those blocked attempts. No application code was
changed to work around the sandbox. Maintainer reproduction:

```powershell
mvn -B -pl edol-core -am clean test '-Dtest=AgentCameraLifecycleTest,AgentSnapshotRoutingTest,SecureAgentRoutingTest,SecureAgentHttpTest' -Dsurefire.failIfNoSpecifiedTests=false
# In the Agent worktree; standalone tests avoid the known Windows runner cleanup issue:
py -c "from pathlib import Path; from scripts.run_host_tests import run_test; run_test(Path.cwd(),'secure_transport','src/security/SecureTransport.cpp',secure=True,smoke=True); run_test(Path.cwd(),'tls_lifecycle','src/http/HttpUploadClient.cpp',secure=True,smoke=True)"
py -m unittest discover -s test -p 'test_secure_trust.py'
```

Exact-vendor reproduction and the three build environments are documented in
Agent `docs/verification.md`. Full historical reactor, Stage 7/8 and live-broker
audits were not repeated. Both working trees pass `git diff --check`.

### Operator steps (after separate approvals)

1. Approve and deploy **Core only** from the reviewed source using the existing
   Compose run and Core-only image/rollback procedure above. Hub, proxy, realm,
   database and broker configuration need no changes. Do not run bootstrap,
   prepare, identity, migrations or historical audits.
2. Approve firmware upload separately. Confirm the actual Agent USB port;
   preserve the current artifact, build `lolin_d32_secure_smoke` using the same
   `EDOL_AGENT_ROOT_CA_FILE`, and upload firmware only. Do not reset NVS,
   re-provision MQTT or replace the current working identity just for TLS testing.
3. With the saved identity/endpoints, run the existing lab `TLS` command once,
   then `SNAPSHOT`, followed by one more snapshot after completion. Check HTTP
   200 and an actually changing Agent dashboard image. A JPEG capture alone is
   not upload/display PASS. A successful probe is not upload PASS either.
4. If upload still rejects trust, stop repeated attempts. Record only stage,
   verification flags/depth, leaf SHA-256 and numeric HTTP/TLS results. Compare
   the probe/upload leaf hashes. If needed, a separately approved read-only
   TLS 1.2 peer check on Debian can fingerprint the existing endpoint:

   ```sh
   cd /home/serge/edol-stage9-hardware
   openssl s_client -connect 192.168.0.200:18443 -servername 192.168.0.200 -tls1_2 -CAfile certs/ca.pem -verify_ip 192.168.0.200 -verify_return_error </dev/null 2>remediation/snapshot-tls-verify.log | openssl x509 -outform DER | openssl dgst -sha256
   ```

   Check the verification log for success independently of the pipeline's last
   exit status. Do not post full logs or certificate bodies. No CA/server change
   is prescribed if the fingerprint differs or verification fails.
5. After camera success, separately approve a lifecycle test: revoke through
   Hub, observe at least two 15-second camera intervals with no new snapshot
   commands/missing-binding stack traces, and confirm the old identity is denied.
   Open SETUP explicitly, retain saved fields, issue a fresh owner code and pair.
   Confirm automatic ACTIVE/MQTT READY and resumed real snapshots. Also verify
   that a temporary network outage reconnects with the same identity and does
   not automatically open Setup Mode. Execute disruptions one at a time with
   the operator; do not use fabricated traffic or infer missing counters as zero.
   Record the observation window. If command emission is not observable, mark
   it NOT OBSERVED rather than asserting a zero count from missing logs.
6. Only then resume the existing print/model and AMS plan. Those observations
   remain NOT RUN until actually executed. Preserve all resources and restoration
   obligations; cleanup is NOT STARTED.

### Rollback and artifacts

Restore only the previous Core application image and, after explicit approval,
the reviewed `5aba847` firmware artifact/source with the same CA. Preserve the
current paired identity and saved settings. Do not restore old revoked secrets,
roll back authority/DB state, replace certificates or reset NVS.
The pre-build binary was preserved locally as
`stage9-restoration/pre-camera-tls-fix-secure-smoke-firmware.bin`, SHA-256
`5b9c36167864237dd7c869901088d56380cf0161ec412004c569224a2c665cc9`.
This is a build artifact, not a device flash dump, and remains ignored.
No new Stage 9 infrastructure, deployment, hardware operation or cleanup was
performed during this source increment. Existing proxy/key/data artifacts and
the prior cleanup journal remain unchanged.

## TLS memory-pressure follow-up — retest at Core 185ed932 / Agent 0252423

### New physical evidence

| Observation | Result and boundary |
| --- | --- |
| Explicit Agent TLS probe against Core HTTPS leaf `e8a4bcbf5943fd7270fcce1d193dac15429556b2bd6d78fe36e0503909ee92e8` | Verified; depths 1 and 0, flags zero |
| Two real camera captures, 91,637 / 92,040 bytes | Capture PASS; HTTPS upload FAIL twice with the identical leaf, depth 0 only and NOT_TRUSTED 0x8 |
| Same existing CA, ready NTP, preserved identity after flash | Operator confirmed; neither CA replacement nor re-pair is needed to investigate |
| DIRECT Core model `Куб.gcode.3mf` | curl exit 0, metadata extracted and parsed successfully; Core acquisition stopped normally |
| Agent model | `DOWNLOAD_FAILED:DATA_CONNECT`; ESP32 reports -32512, SSL memory allocation failed at FTPS data TLS setup |
| Core model HTTP 500 / ClientAbortException | Follows the Agent's aborted stream; it is not evidence of an HTTPS authentication/CA failure |
| Printer overview | Both cards show Printing, progress area and DIRECT preview; Hub jobs also receive later progress events |
| Hub metadata event | processingFailures rose to 1 after DIRECT metadata. Envelope rejection stayed zero. The exception category is absent; complete Hub metadata persistence is not PASS |
| Full STARTED → RUNNING → FINISHED, camera display, Agent model success, revoke/re-pair and AMS | Pending; no full lifecycle or final acceptance claimed |

The early boot leaf fingerprint cannot be attributed solely from the old
pathless certificate log: broker TLS on 18884 and Core HTTPS on 18443 are
different endpoints. It is not used to explain this failure. The later probe
successfully verifies exactly the same Core leaf rejected during upload.
New lab connection diagnostics include host/port before certificate output.

### Root mechanism and falsifiable evidence

Production `AgentService.uploadSnapshot` opened camera TLS before HTTPS while
both Bambu and EDOL MQTT TLS were live. The probe had no camera TLS session.
Model transfer opened FTPS control and HTTPS before opening FTPS data, reaching
five TLS sessions; the latter failed with an explicit allocation error.
JPEG data is streamed in 1 KiB chunks; the reported 90 KiB frame size does not
mean a 90 KiB JPEG buffer was allocated by AgentService.

In the pinned Espressif mbedTLS 2.28.7 source:

1. `library/x509_crt.c::x509_crt_check_signature` calls the public-key verifier.
2. `x509_crt_find_parent_in` sets `signature_is_good = ret == 0`; for a trusted
   root candidate, **any** failed check (including allocation failure) skips it.
3. With no suitable parent, `x509_crt_verify_chain` adds NOT_TRUSTED and ends the
   chain at the leaf. The callback therefore sees depth 0 / 0x8, rather than a
   separate allocator error or depth-1 CA callback.

The exact-vendor regression loads the same valid leaf and correct CA before
injecting one allocation failure during verification. `fault_at=1` reproduces
the observed leaf-only NOT_TRUSTED; releasing the restriction restores depths
1/0 with zero flags. Three rejection/recovery cycles pass. This explains why a
sound trust anchor and identical server certificate can produce these different
outcomes under pressure. No verifier code/flag is patched to accept the failure.

Memory pressure is supported by the production TLS overlap, the explicit FTPS
allocation error and real-verifier reproduction. The exact allocation on the
physical camera attempt was not traced; this source evidence does not itself
establish a successful hardware fix. If the new sequence still fails with
adequate memory, retain that counterevidence and investigate it rather than
declaring the CA bad or bypassing NOT_TRUSTED.

### Minimal patch and compatibility

* Secure AgentService operations now temporarily disconnect **printer MQTT
  only** before opening bulk connections. The EDOL MQTT client is not
  intentionally disconnected and its authority/credentials remain unchanged.
* On every success/failure exit, HTTPS and camera/FTPS sockets are closed before
  printer MQTT reconnect. An unsuccessful reconnect is left to the existing
  main-loop retry. An already-offline printer MQTT link is not force-connected
  by the transfer. Home does not perform this pause.
* This reduces active TLS sessions by one during transfers and creates a
  printer-telemetry interruption. The existing synchronous-loop/EDOL keepalive
  limitation remains; no durable command or zero-telemetry-gap guarantee is added.
* The descriptor-sentinel wrapper now also covers camera, FTPS and printer MQTT;
  it fixes the remaining LAN `setSocketOption`/descriptor-0 path. FTPS timeouts
  now use 10 seconds rather than 10000 seconds. Existing LAN certificate policy
  is unchanged and remains separate from strict EDOL HTTPS/MQTT verification.
* Lab-only free-heap/largest-block and endpoint diagnostics accompany the
  existing numeric certificate flags/depth and public leaf fingerprint. They
  contain no credential, code, body, certificate dump or private key.

CA installation, exact IPv4 SAN checks, trust/signature/time checks, routes,
topics, payloads, NVS and credential formats are unchanged. No backend runtime
source, broker, database, proxy, certificate or realm change is required.

### Source verification

| Check | Actual result |
| --- | --- |
| Exact vendor mbedTLS controls | 16 PASS, plus allocation-fault reproduction and three rejection/recovery cycles PASS |
| Native executable configurations | 13 distinct PASS, including separate Home/secure production AgentService orchestration tests |
| Full native runner exit | Windows temp-directory cleanup error after twelve executables; FTPS passed separately. Do not label the runner exit PASS |
| CA-hook Python tests | 3 PASS |
| Home, secure, secure_smoke ESP32 builds | Three SUCCESS, same public CA; source/build checks only |
| Sonar | Modified Python runner analyzed without findings; C++/CMake unsupported by the available analyzer |
| Backend runtime tests | Not repeated: this increment changes backend documentation only |
| New physical upload, model/reconnect/revoke outcomes | NOT RUN |

Reproduction commands and fault-injection boundaries are in Agent
`docs/verification.md`. The fault allocator is desktop-test-only and does not
change the firmware allocator or the pinned SDK. Vendor source hashes remain
checked. Both repositories pass `git diff --check`.

### Next operator session — firmware only, approval before flash

1. Keep Core `185ed932` running. Do **not** rebuild/redeploy Hub/Core or other
   services for this increment; backend changes are documentation only.
2. After explicit flash approval, confirm the actual Agent USB port (COM9 was
   last observed), preserve/hash the existing binary and build the reviewed
   `lolin_d32_secure_smoke` revision with the unchanged Stage 9 public CA via
   `EDOL_AGENT_ROOT_CA_FILE`. Upload firmware only; retain paired identity,
   Wi-Fi, printer LAN settings and Core/MQTT endpoints. No erase/reset/re-pair.
3. Run one explicit `TLS` probe. Then run **exactly two `SNAPSHOT` commands
   sequentially**, waiting for each operation to finish. Require verified TLS,
   HTTP 200 and a genuinely updating Agent camera image in Hub. Observe printer
   telemetry reconnecting after transfer cleanup; a JPEG capture or TLS probe
   alone is not snapshot upload/display PASS.
4. If NOT_TRUSTED or allocation failure persists, stop. Retain only stage,
   host/port, flags/depth, peer leaf SHA-256 and numeric heap/largest-block
   observations. Do not rotate CA/credentials or repeat bootstrap. No additional
   revoke/re-pair test while camera remains blocked.
5. After camera success, separately approve the existing revoke → explicit
   SETUP → fresh pairing → ACTIVE/MQTT READY → resumed-camera regression.
   Full printing, Agent Unicode model transfer and AMS follow as separately
   observed steps. Keep independent jobs for the two logical printer IDs.

The Hub processing failure remains a separate item requiring safe exception
category evidence; the aggregate counter does not establish a tenant or
metadata root cause. Do not mark it resolved from the Core's parsing success.

### Rollback / cleanup journal extension

No new Stage 9 resource, deployment, hardware or cleanup action occurred.
Preserve all existing volumes, realm, authorities, certificates, proxy configs
and restoration obligations. A new ignored pre-build artifact was saved:
`stage9-restoration/pre-pressure-verification-secure-smoke-firmware.bin`,
1,032,928 bytes, SHA-256
`03f262360cebc75f657a15029d74a3ceda65f32accbdbe76812a727b788aa3fc`.
It is a local build artifact, not a device flash dump. After separate approval,
rollback uses that artifact or `0252423` source and the same CA, preserving
current identity/NVS. No backend runtime rollback is required by this patch;
do not restore old revoked secrets or broker/DB state. Cleanup is NOT STARTED.

## Continuous MQTT, verified transfer completion and dashboard commands

This source increment supersedes the intentional MQTT-pause workaround above.
Baseline: backend `ea576b3078d67c3d4227aabefa81d5a2b19214fe`, deployed Core
`185ed932`, clean Agent worktree `8db73079a0315ce51727717ae420f3badde77e4f`.
The Agent worktree was detached at that commit and was attached to its existing
`stage9-agent-pairing` branch without resetting files or copying history.
Backend remains on `EDOL-048`; unrelated `edol-hub/logs/` is excluded.

### Evidence and root causes

The operator's `8db73079` report confirms two uploaded JPEGs, 84,760 and 85,186
bytes, further HTTP 200 responses and a real Hub camera image. NTP, CA and exact
IPv4 SAN, DIRECT/AGENT printing telemetry, and DIRECT Unicode model metadata
work. These are historical hardware results with the MQTT-pause workaround,
not acceptance of the current source. Reconnects of 1487–2320 ms were introduced
by firmware. Both logical printer IDs remain independent; layer display is
unchanged. No new deployment/device observation occurred in this increment.

Source investigation found:

- Agent commands executed synchronous upload services directly in the EDOL
  PubSubClient callback. Keeping a socket open would still starve both MQTT
  loops, and recursive loop() calls would reuse callback buffers unsafely.
- The audited WiFiClientSecure.available() returns a negative TLS error/close
  result and stops the client; read(buffer,n) returns -1 when available <= 0.
  The old `while (data_.available())` treated a negative result as true and
  could report DATA_READ after the declared payload. Core had already received
  Content-Length bytes and emitted MODEL AVAILABLE before FTP completion.
  Hardware's exact terminal TLS error remains unmeasured; the new regression
  reproduces this source path, not the historical physical read itself.
- Both dashboard pages issued command POSTs without CSRF. The detail page
  ignored HTTP status entirely. Secure Hub rejects these requests before the
  controller, explaining the common DIRECT/AGENT symptom and absent command
  logs. No live browser POST was replayed. This is source/MockMvc evidence;
  actual operator-browser network capture remains a physical retest item.
  The existing RestTemplate already has CoreTenantPropagationInterceptor;
  changing to RestClient is unnecessary. Hub validates printer ownership and
  uses its service token/server-established tenant; Core requires
  core.command.execute and printer ownership before selecting transport.

### Memory budget and design comparison

The exact Arduino-ESP32 2.0.17 / espressif32 6.13.0 ESP32 SDK configuration has
CONFIG_MBEDTLS_SSL_MAX_CONTENT_LEN=16384 and no enabled dynamic TLS buffers.
WiFiClientSecure exposes no supported runtime TLS buffer-resizing API.
PubSubClient.setBufferSize changes MQTT packet storage, not TLS records.
The pinned vendor verifier remains `2b8e772fc1cb0732cda3bae7d1e9d6f4cfaf63d9`.

| Source-backed component | Snapshot | Model |
| --- | ---: | ---: |
| Maximum TLS session overlap (including two MQTT) | 4 | 5 |
| TLS record **content** floor, two 16 KiB directions/session | 131,072 B | 163,840 B |
| Secure MQTT packet buffers (two 8192 B allocations) | 16,384 B | 16,384 B |
| New worker stack, ESP-IDF bytes; local stream buffers live inside it | 12,288 B | 12,288 B |
| Sum of these floors, excluding everything below | 159,744 B | 192,512 B |

These are allocation lower bounds, **not total heap usage or capacity proof**.
Record overhead, TLS contexts, parsed chains, ECDH/signature temporaries, TCP,
Wi-Fi, other tasks and ArduinoJson/String allocations are additional. The
request handoff has a 256-byte active name and one 256-byte pending model;
the event queue has three slots, each limited to 2048 serialized bytes plus
String overhead. No whole model/JPEG buffer is allocated. The prior successful
upload reported free_heap=132920 / largest_block=77812 before HTTPS while
printer MQTT was paused. It cannot establish current continuous-MQTT margin.
Its largest block was also smaller than either successful full JPEG.

| Approach | Assessment against this SDK/workload |
| --- | --- |
| Remove disconnect and keep synchronous transfers | Reject: MQTT loop starvation and callback reentrancy remain |
| Recursively service MQTT from transfer callbacks | Reject: PubSubClient and callback buffer reuse are unsafe |
| Resize TLS buffers/session reuse | No supported wrapper resizing/resumption contract; MQTT packet resize is unrelated. Requires a separate exact-SDK change and negative TLS controls |
| Whole-image/model RAM staging | Needs additional large storage while MQTT survives; observed largest block is below one JPEG. Segmented staging also consumes handshake headroom |
| Flash/filesystem staging | Could reduce TLS overlap, but changes storage/lifetime/wear contracts; no existing filesystem is silently formatted, repartitioned or adopted |
| Core reads the remote printer directly | Does not cover private remote LANs; an outbound Agent is still required |
| One low-priority bulk task, EDOL-first TLS and bounded streaming | Selected: preserves sole ownership of both MQTT clients, services telemetry/commands during blocking bulk handshakes, reduces EDOL verifier pressure, and rejects resource failures |

HTTPS is prepared with both MQTT sessions established, before camera/FTPS TLS
exists. The prepared connection is reused only while still connected; there
is no HTTPS reconnect under LAN pressure. FTPS control/data or camera close
before HTTP finalization. This reduces EDOL verification overlap from four TLS
sessions to three, but maximum streaming overlap remains four/five. No claim
is made that model streaming or two snapshots fit the actual device.

Admission uses the actual SDK IN/OUT record-buffer lengths (including record
overhead), both free heap and the largest contiguous block. It is a necessary
record floor, deliberately not an invented signature peak threshold. Sequential
EDOL setup addresses its transient verifier peak; every later allocation or
handshake failure still rejects the transfer and cleans up bulk clients.
Calibrated peak headroom requires operator measurements. If continuous MQTT
plus four/five sessions exceeds hardware capacity, clean transfer rejection is
the supported limit; do not restore the pause or weaken EDOL TLS. A different
outbound Agent hardware/storage design needs a separate review.

### Scheduling, transfer outcome and commands

One worker at idle priority owns all camera/FTPS/HTTP access. The normal
Arduino loop owns both PubSubClients, command forwarding, heartbeat and event
publication. A callback copies a validated filename (maximum 255 bytes, no
control characters) into a bounded mailbox and returns. One active operation
and at most one pending, coalesced model are allowed. Snapshots never queue;
models outrank them. Completion starts a 15-second cooldown, with no automatic
worker retry. Worker creation starts only after both MQTT clients connect and
retries allocation at most every 30 seconds. Model events retain disconnected
outcomes, retry publish at most three times one second apart and expire after
60 seconds. No new bulk operation starts before that bounded handoff drains;
expiry explicitly logs missing delivery confirmation. The main loop also drains
and collects completion after Wi-Fi loss, avoiding a stuck operation/setup gate.
This remains bounded best-effort MQTT delivery, not a durable transfer receipt.
The lab console reports request
QUEUED/REJECTED separately from bulk completion and blocks SETUP/TLS/TIME while
bulk work is active. Home uses the same scheduling without secure enrollment
or EDOL trust requirements; DIRECT backend behavior is retained.

FTPS requires a strictly parsed SIZE (1 byte to 32 MiB), RETR 125/150, exactly
SIZE bytes and final 226. Negative available/read before SIZE, truncated or
excess data, missing/wrong completion, write failures and timeouts fail.
After the authenticated payload count, a negative data EOF does not trigger
another payload read; final control-channel completion is still mandatory.
A one-byte output tail prevents HTTP from reaching Content-Length before FTP
completion. Only then is the tail committed and HTTP status checked. Thus a
post-payload FTP failure cannot race Core's complete-body acceptance. Each
started firmware attempt emits one terminal event. An HTTP acknowledgement
lost after sending the tail is inherently uncertain, reported as
HTTP_UPLOAD_FAILED_OR_UNCONFIRMED; this protocol has no new transfer receipt ID.
Core metadata success/stop prevents late failures from reactivating retries.
Duplicate failure scheduling shares one pending timer; four retries exhaust
acquisition. A generation guard prevents an old timer clearing new work.

Camera payloads are bounded to 256 KiB and 30 seconds; FTPS data to 120 seconds
and 32 MiB; LAN/EDOL TLS handshakes and idle I/O to ten seconds. Command/topic,
identity/credential/NVS formats, descriptor sentinel and strict EDOL trust,
time, signature and exact IP SAN checks are preserved. Printer-LAN trust
limitations remain acknowledged and separate.

The dashboard command helper supplies server-rendered CSRF, bounds pending
commands per printer and request wait to ten seconds, rejects login redirects,
and shows safe rejection/unknown-outcome feedback. It never automatically
replays a command. HTTP success says accepted and waits for telemetry. Proxy
logs contain printer ID, allowlisted command and numeric status, not tokens or
bodies; upstream rejection bodies are not exposed. Both real Core gateways are
covered offline; the secure Agent route must resolve a usable persisted
binding for the trusted current tenant, never a payload tenant/printer claim.

Hub processingFailures remains a separate unresolved historical observation.
New diagnostics record validated event/printer UUID, allowlisted type and
exception class, without message/SQL/body or stack dumps. Envelope and receipt
isolation are unchanged. The counter cannot be closed from Core parsing PASS.

### Source verification and limitations

See Agent docs/verification.md for native commands. Targeted checks use offline
stubs/MockMvc; no live MQTT, printer commands, serial, Docker, deployment,
bootstrap, migrations, certificate/authority changes or data cleanup occurred.
Final counts and local reviewed commits are recorded below after verification.

### Minimal next physical acceptance — NOT EXECUTED

1. Separately approve backend deployment and Agent flash of the reviewed local
   revisions. Core/Hub source both changed; do not blindly apply the earlier
   firmware-only instruction. Preserve the existing run, all data/volumes,
   realm, authorities, CA/private keys and restoration artifacts. No migration
   or bootstrap is needed. Confirm the actual Agent USB port; COM9 is historical.
2. Preserve/hash the accepted local build artifact before any future build/flash
   workflow. Use the same public CA and retain all identity/NVS/settings; no
   erase, reset or re-pair. Current implementation work performed builds only.
3. With separate operator approval, two sequential snapshots: wait for each
   bulk completion **and 15-second cooldown**. Require both MQTT connections
   and ongoing telemetry/keepalive throughout, verified TLS, HTTP 200 and real
   Hub image refresh. Record stage/free heap/largest block at each handshake.
   Request QUEUED, capture alone or an open socket without telemetry is not PASS.
4. Unicode model: record SIZE, transferred byte count, final FTP code 226,
   matching HTTP Content-Length/200 and exactly one consistent terminal outcome
   with metadata. Verify no queued retry survives success. A lost HTTP response
   remains uncertain; do not call it a definitive remote failure or replay blindly.
5. Exercise an independently approved insufficient-memory/defer case without
   disconnecting MQTT or weakening TLS. If either workload cannot fit, stop and
   record the hardware limit. Do not substitute the pause workaround.
6. Separately approve controlled dashboard Pause then inverse Resume, DIRECT
   and AGENT independently, with operator observing the print and agreeing to
   restoration. Check browser POST/CSRF/status, bounded Hub log and telemetry.
   No such command has been sent by the implementation agent.
7. Keep revoke/re-pair and AMS as separately approved later gates. AMS hardware
   acceptance: NOT RUN. Cleanup: NOT STARTED.

### Rollback boundary

No deployment/flash occurred, so no runtime rollback was executed. Future
rollback requires separate approval: backend to the pre-increment baseline
(deployed Core 185ed932), Agent to 8db73079 using the unchanged CA and current
NVS. That Agent rollback restores the known telemetry pause and therefore does
not satisfy the continuous-MQTT requirement. Restore no revoked credential or
old broker/database state; preserve all prior restoration obligations/artifacts.
No push or publication from the secret-bearing original Agent repository.

### Final targeted verification record

| Check | Actual final result |
| --- | --- |
| Selected Core tests | 18 PASS, zero failures/errors/skips |
| Selected Hub tests | 20 PASS, zero failures/errors/skips |
| Browser command helper Node tests | 5 PASS, zero failures/skips |
| Native executable configurations | 12 distinct assertion-PASS configurations, including Home/secure production bulk task and both MQTT wrappers |
| Windows transfer_mailbox runner | Assertion PASS, followed by WinError 32/5 temp cleanup; runner exit is not clean PASS |
| Exact vendor TLS controls | 16 PASS; allocation-fault regression and three rejection/recovery cycles PASS |
| Final Home firmware build | SUCCESS, 14.578 s, static RAM 14.7%, flash 73.9% |
| Final secure firmware build | SUCCESS, 15.744 s, static RAM 15.1%, flash 78.3% |
| Final secure_smoke firmware build | SUCCESS, 15.637 s, static RAM 15.2%, flash 78.7% |
| Both Git diffs | Whitespace check PASS |
| New hardware / commands / deployment | NOT RUN |

Actual backend and browser commands, from the backend root:

```powershell
mvn -B -pl edol-core,edol-hub -am -Dtest=DashboardCommandRoutingTest,AgentPrinterCommandAuthorityTest,MetadataAcquisitionRetryTest,PrinterCommandControllerTest,CorePrinterHttpAuthorizationTest,AgentSnapshotRoutingTest,CorePrinterProxyControllerTest,SecureHubWebSecurityTest,SecureMqttEventListenerTest,CoreRestClientInjectionContextTest -Dsurefire.failIfNoSpecifiedTests=false test
node --test edol-hub/src/test/js/printer-commands.test.mjs
```

Counts come from the selected final Surefire XML files. No PostgreSQL,
Testcontainers or complete reactor verification was selected, so zero skips
applies only to this bounded suite. Native commands/configurations are in Agent
docs/verification.md. The 37,722-byte Unicode regression uses synthetic bytes
through real FtpsClient and DeferredTailStream, not the operator's model file.
It requires 226 and checks 426 never releases the final HTTP byte.

Read-only Sonar analyzed all 14 modified supported Java/HTML/JS/Python files.
New actionable findings (Python duplicate literal and feedback element
semantics) were fixed and reanalyzed. JS/helper tests and Python have no
remaining findings; the printers list has none. The Java snippet analyzer
reports S1598 for all nine packages because its file path is empty, despite
matching real package directories. S1075 on the existing /api/printers/ protocol
path is a false positive: the host is configurable, but this API path is the
contract. No suppression was added. Full dashboard detail analysis still
reports 18 pre-existing findings in unchanged sections: CDN integrity,
accessibility, commented code and formatting advice, including three invalid
top-level-await suggestions for a classic script. These are recorded separately
in Project TODO; the full file is not claimed Sonar-clean. No unrelated
CDN/style refactor was mixed into this command fix. C++ is unsupported by this
MCP analyzer; its build/native evidence is reported separately.

Initial host fixture compile/link errors, a Mockito wildcard compilation issue
and a MockMvc invocation-count expectation were corrected before these final
results. Local cache/compiler permissions and Windows cleanup artifacts were
kept separate from source assertions. All local commits are created by the
commit-agent after this source review; their SHA values are in the final
implementation handoff. There is no push or runtime acceptance claim.

## AMS terminal HTTPS enrollment and independent MQTT processing diagnostics

### Scope and source findings

Baseline: backend source `d52fdd0` on `EDOL-048` (operator Core runtime
`185ed932`); terminal `0749cec` on `main`, initially clean and ahead of origin
by two commits. Unrelated `edol-hub/logs/` remains excluded. No bootstrap,
server request, live log capture, broker/DB/realm/proxy change, deployment,
serial access, flash, enrollment attempt, NVS reset or environment cleanup
occurred. Existing CA retained. No architecture, schema or endpoint contract changed.

Terminal diagnostics commit: `27790ac834635b9ef7aceb0d6059a98b0a12cab0`.
Its [production HTTPS verification document](https://github.com/serge-ponomarenko/edol-ams-terminal/blob/27790ac834635b9ef7aceb0d6059a98b0a12cab0/docs/https-enrollment-diagnostics.md)
contains exact source references, limitations, commands and image provenance.

| Terminal source | Finding |
| --- | --- |
| `settings_screen.cpp:onEnrollClick`; `terminal_enrollment_service.cpp:9` | Applies visible host/port/HTTPS; queues one enrollment POST with the owner code, no auto retry. |
| `terminal_api.cpp:22` | Constructs the explicitly selected scheme, host and port for `/api/terminal/v1/enroll`. |
| `http_service.cpp:68` | Epoch > 2024-01-01 gate; otherwise NTP with a two-second wait. A plausible epoch does not establish current NTP synchronization. |
| `http_service.cpp:158` | CA/time precheck, secure begin and POST; no plaintext fallback. Generic UI error previously hid all nonpositive results. |
| `terminal_enrollment_service.cpp:41` | Only successful response plus credential persistence enables secure mode. Lost one-time reply can be uncertain. |
| `ams_service.cpp:152` | Before enrollment, polling uses legacy HTTP independently of the enrollment HTTPS switch. Its recurring 400 is a separate request. |
| `scripts/edol_terminal_ca.py`; `platformio.ini` | Correct variables are EDOL_AMS_ROOT_CA_FILE and EDOL_TERMINAL_SECURE_BUILD. Forced build-local header inclusion confirmed in an actual production compiler invocation. |

Baseline local object/ELF/BIN contain the exact normalized header CA, not just
PEM markers. Cached IDE metadata omitted the include and is not actual compiler
evidence. This does not identify the image currently flashed on COM16.
Normalized PEM SHA-256:
`a83bd645d1319b7427c0af43585971c0bf1ee8437d70b4a3ec5e25d28d7e7b38`.
OpenSSL parses/self-verifies this trust anchor, valid 2026-10-22 11:23:40Z through
2026-11-21 11:23:40Z. This is not verification of the server leaf/chain or of
the operator's intended original CA selection; compare with the existing run CA.

The observer pins the already installed espressif32 6.13.0 / Arduino 2.0.17
lifecycle. It records safe category, scheme/host/port, CA presence, actual
epoch/clock gate, free/largest heap, HTTP result and SDK TLS error. It captures
available certificate flags before Arduino's failed-connect cleanup without
altering flags or adding a network probe. Unavailable is printed literally.
An SDK upgrade requires re-auditing the observer. Wrong-variable builds now
fail explicitly; CA-free builds overwrite stale generated CA content.

`connect_start` precedes combined DNS/TCP/TLS, not a separately observed
handshake boundary. `precheck_failed`, `begin_failed`, `http_response` and
`transport_failed` distinguish observable stages. HTTP -1 alone is not a
certificate diagnosis. No pairing codes, credentials, headers, bodies, whole
URLs, query values or PEM are logged. Runtime build output gives environment,
normalized public CA hash and application-descriptor ELF identity; the offline
auditor checks CA in object/ELF/BIN and matching ELF identity inside BIN.

Agent's memory-pressure NOT_TRUSTED and IPv4 SAN lessons remain hypotheses,
not terminal causes. The terminal has different UI allocations and one
synchronous TLS request at a time. Flashed identity, current NTP time,
actual TLS flags/heap, server chain/identity and receipt of the physical POST
remain unknown. No physical flags are inferred from the generic screen error.

### Independent AMS MQTT investigation

`edol-ams/.../event/MqttEventListener.java:63` resolves tenant **after** counting
validated envelopes and **before** switching event types, including types with
no AMS handler. `AmsPrinterTenantResolver.java:64` rejects unknown printers and
tenant mismatches. Both increment processingFailures, not rejectedEnvelope.
Two logical printers observing one P1S do not share a logical ID or gain an
implicit mapping. This reproduces a source path yielding the reported counter
pattern, not a proven classification of the historical physical failures.

For mapped `ams.slot.loaded`, `MqttEventListener.java:143` reads slot and
`AmsSpoolChangerService.java:35` checks RAM staging/expiry, then calls Hub if
a staged spool exists. This handler does not directly write AMS JPA. A missing
slot, upstream HTTP/Hub persistence or other handler failure is a separate
candidate; the old counter-only log cannot distinguish them.

New logs distinguish envelope rejection, tenant mapping and handler failure,
with validated event/tenant/printer UUID, allowlisted type, unmapped-printer
reason, exception/cause class and available upstream HTTP status. No exception
message, SQL, body or stack dump. Tests reproduce foreign/unknown rejection,
handler/persistence categories, malformed slot and upstream 403 redaction;
tenant scope closes after handler failure. No mapping, tenant isolation or
handler acceptance policy is weakened. Actual failed event types/mappings and
categories require later runtime diagnostics; no AMS deployment was performed.
Hub processingFailures remains separate and unresolved.

### Final verification and artifacts

| Source-only check | Final result |
| --- | --- |
| Actual production HttpService native controls, CA present / absent | Both PASS |
| Actual CA hook and image audit Python tests | 6 PASS |
| AMS targeted Maven | 23 PASS, zero failures/errors/skips |
| Secure normal esp32dev with retained CA | SUCCESS; RAM 112,812 / 327,680 B, flash 1,237,545 / 1,310,720 B |
| Forced-header compiler invocation / final image audit | Confirmed / all checks PASS |
| Sonar supported modified Java/Python files | Seven analyzed; no actionable findings |
| Both repositories git diff --check | PASS |
| Physical enrollment, authenticated state, live MQTT processing | NOT RUN |

Successful backend command:

```powershell
mvn -B -pl edol-ams -am '-Dtest=MqttEventListenerTest,AmsPrinterTenantResolverTest,TerminalAuthenticationServiceTest,TerminalPairingManagementServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Fresh XML: listener 8, tenant resolver 3, authentication 3, pairing management 9.
Initial stale/inaccessible generated artifacts required a scoped elevated clean
build; the final tests passed after the last test addition. No stale XML counted.
Full reactor and Docker/migration tests were not run for diagnostic-only changes.
An initial verbose PlatformIO build failed SCons 4.11.1 command printing
(`_Null + str`) after successful compilation/linking; ordinary nonverbose build
succeeded, including after final source changes. No toolchain patch was needed.
Synthetic flags and static RAM sizes are not physical TLS or capacity evidence.

Final local BIN SHA-256:
`95f5f097e4748aa66c0481cb2f3693736b7cfa41f78889d9ac7240321480f073`.
ELF SHA-256 (compare with runtime AMS build output):
`42489fd12cd71f4c72f664c5e9442e880b58c958cc0df3dbfd9972eab35d0a4e`.
Baseline BIN was preserved in ignored terminal
`.pio/pre-enrollment-diagnostics/firmware.bin`, SHA-256
`780ec5ee5a17545fccbdba5e1280c65a78423a16b39635edd6c20248183b51d1`.
That is a local build artifact, not a flash dump. The extracted existing public
CA is retained locally there; no PEM or binary was committed.

Sonar's two Java S1598 empty-snippet-path reports are MCP artifacts: actual
package/file paths match. No suppressions. C++ is unsupported by this MCP;
native tests and actual ESP32 compilation cover it without claiming C++ Sonar.

### Minimal next operator procedure — NOT EXECUTED

Use the linked terminal document's exact commands. First inspect the previous
pairing/identity outcome in Hub read-only. Build normal esp32dev with
EDOL_TERMINAL_SECURE_BUILD=1 and **EDOL_AMS_ROOT_CA_FILE** pointing to the same
existing Stage 9 public CA; run the offline image audit. After separate operator
flash authorization confirm COM16, upload without erase/uploadfs/sec06, then
compare runtime environment/CA/ELF identity with the audit. Preserve NVS/settings.
Resolve the uncertain prior outcome before obtaining one fresh code and
pressing ENROLL once. Never replay the old code or delete an ACTIVE identity
just to retry.

Record only safe enrollment HTTP/TLS/flags, epoch, heap and timestamp. Correlate
privately with the existing proxy/AMS logs, reporting exact enrollment POST
receipt/status within a known logging window. Do not repeat malformed Debian
400 probes or change the working proxy. Require enrollment HTTP 200, local
credential persistence, intended Hub terminal ACTIVE and subsequent authenticated
HTTPS state HTTP 200/real state. Stop after failure or uncertain response for
owner status inspection; no automatic replay or credential reset.

Operator checkpoint remains: Agent camera PASS with MQTT pause; continuous MQTT
NOT SATISFIED; DIRECT model PASS; Agent DATA_READ unresolved; observed RUNNING
telemetry PASS; terminal Wi-Fi and proxy PASS; terminal enrollment FAIL;
AMS processing FAIL observed; Hub processing separately unresolved;
terminal authenticated operations NOT RUN; cleanup NOT STARTED.
New AMS diagnostics require a separately authorized deployment to gather live
evidence; no deployment is part of this source handoff.
