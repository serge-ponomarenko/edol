# Stage 9 proxy drift - minimal operator recovery

## Read-only findings

The HTTPS proxy was not deleted. Existing standalone container
`edol-stage9-agent-https` (`dbca1c142b53`) has no Compose project labels, so the
earlier project-filtered inventory did not include it. It retains the original
image, config/certificate mounts, host port `18443` -> container `8443`, and
static endpoint configuration `10.253.90.7`.

The last attempt started at **2026-11-12 02:02:49 UTC** and exited `1` at
02:02:50. The bounded nginx log identifies **host not found in upstream
`edolams`**. AMS started later, at 02:05:29. Proxy restart policy is `no`, so
nginx was not automatically retried after its dependency became available.
No certificate-load or port-bind failure marker appeared in the inspected tail.
Historical Docker events for that attempt are no longer available; the actor
or command which initiated the attempt is not established.

The retained Core container (`53193e200ff3`, same image) started at 02:05:40.
It has a dynamic endpoint (`IPAMConfig=null`) and now occupies `.7`, while the
stopped proxy has no active endpoint. This accounts for the address collision
on a later proxy start. Core still trusts exactly `10[.]253[.]90[.]7`; broadening
that pattern is not a recovery step.

Current network: PostgreSQL `.2`, Hub `.3`, Keycloak `.4`, MQTT `.5`, AMS `.6`,
Core `.7`. Addresses `.8` and `.9` were free. Preserve `.8` for the separately
prepared Notify rollout and use `.9` for the existing Core container.

## Operator recovery and verified outcome

The operator executed the prepared recovery script. Subsequent read-only
inspection confirmed the same Core container running at `10.253.90.9`, the
same proxy running at `10.253.90.7`, and AMS still running at `.6`. CA/IP-verified
HTTPS works. The script's expected terminal status was too strict: without an
Authorization header, Spring rejects the required `@RequestHeader` with HTTP
400 before terminal authentication. A synthetic invalid header returned 401;
the unauthenticated Agent upload GET also returned 401. No valid terminal
credential, NFC operation or printer command was used. Do not rerun the recovery
script merely because its original probe printed 400.

The commands below are the historical operator recovery, not a request to
repeat it.

Run in a quiet test window without an active print. Briefly stop only Core,
reattach that same container at `.9` with its existing DNS aliases, start it,
then start the same retained proxy at `.7`. AMS is already running. Do not
recreate containers, edit images/trust/config/credentials, renew certificates,
run Compose/bootstrap, update Hub/Notify or start a print.

The operator script is a reviewed local copy of Home MCP draft
`script-251cf8e469e3c6b0b984cf4221999599`; `get_draft` readback matches SHA-256
`531244233141590fff46527b1dc41e2282cf925c48cef401c72544acbac30c08`.
Local copy:
`edol-hub/target/notify-rollout-869df1e/restore-proxy-20261112.sh`.

After reading that script, the operator can copy and run it from PowerShell:

```powershell
scp C:/Users/Ponomarenko/IdeaProjects/edol/edol-hub/target/notify-rollout-869df1e/restore-proxy-20261112.sh edol-server:/home/serge/edol-stage9-hardware/remediation/
ssh edol-server 'bash /home/serge/edol-stage9-hardware/remediation/restore-proxy-20261112.sh'
```

Its only precondition query checks `.9` is still free. Its mutation commands are:

```bash
docker stop 53193e200ff3
docker network disconnect edol-stage9-hardware_stage9 53193e200ff3
docker network connect --ip 10.253.90.9 \
  --alias edolcore --alias edol-stage9-hardware-edolcore-1 \
  edol-stage9-hardware_stage9 53193e200ff3
docker start 53193e200ff3
docker start dbca1c142b53
```

Two short checks finish the script: existing proxy `nginx -t` and a
CA/IP-verified HTTPS GET without credentials to `/api/terminal/v1/state`.
Expected result: nginx configuration accepted and **HTTP 400** for the terminal
GET with its required Authorization header omitted; an invalid synthetic header
reaches authentication and returns **401**. This exercises
no NFC operation, enrollment, firmware or printer command. No broader test
suite or acceptance cycle is required for this recovery.

If reattachment/start fails, stop at that step; do not recreate anything or
change trusted-proxy settings. If only nginx fails, leave Core on `.9` and the
proxy stopped, then inspect its narrow startup error. Do not move Core back to
the proxy's trusted `.7`. All identities, volumes, images, CA/NVS and per-service
overrides remain in place. Endpoint pins persist for these current containers;
a future Compose recreation needs the same addressing to be represented in
its reviewed configuration. This is not a claim that all future start ordering
or network allocation is solved.

## Verification performed

Read-only Docker metadata, nginx error classification and routing inspection
only. The prepared shell script passed `bash -n` and its precondition snippet
passed Python AST parsing without execution. Draft/local hashes match.
Recovery was performed by the operator; the outcome above was independently
checked read-only. Hub/Notify rollout and printing remain outside the executed
scope and are not implicitly authorized by this recovery.
