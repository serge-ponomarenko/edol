# Stage 9 Hardware Acceptance Remediation — 2026-10-23

## Current state: resume, do not bootstrap

The operator report is the physical evidence baseline. Agent secure firmware
`97e64b1` was flashed on confirmed COM9 and booted into its setup AP: **FLASH-A
PASS**. Enrollment, MQTT session, telemetry and AMS hardware acceptance are
NOT RUN. Portal endpoint values were not confirmed saved. Do not flash again
or repeat PostgreSQL/RLS/realm/certificate preparation to resume this session.

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
  docker build -f docker/edol-$service.Dockerfile -t edol-stage9-$service:stage9-remediation . || break
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
