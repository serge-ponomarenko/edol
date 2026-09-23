---
name: edol-remote-docker-verification
description: Run Docker-dependent EDOL verification from Windows through the temporary remote Docker tunnel, including Testcontainers tests; do not use for ordinary local tests or persistent server services.
---

# EDOL Remote Docker Verification

Use this skill when a locally running Windows process needs Docker: Java
Testcontainers, Docker-backed integration tests, or equivalent verification.
Do not use it for tests that do not require Docker, commands that run wholly
locally, or operations on existing home-server containers.

## Procedure

1. Inspect the Maven layout and the affected test before running it. Run a
   selected Docker-dependent test from its owning module, for example from
   `edol-core`:

   ```powershell
   ..\.agents\scripts\with-remote-docker.ps1 mvn -Dtest=CoreMigrationTest test
   ```

   Alternatively, construct a reactor command scoped to that module and its
   required dependencies. Do not run a selected test from the reactor root
   without module scoping: another module can fail with "No tests matching
   pattern ... were executed" before the target module runs. Do not change the
   wrapper to work around this Maven behavior.

2. Invoke Docker-dependent commands only through
   `.agents/scripts/with-remote-docker.ps1`. It opens a temporary tunnel to the
   Debian Docker socket, waits for it, scopes `DOCKER_HOST` and
   `TESTCONTAINERS_HOST_OVERRIDE` to the wrapped command, preserves the child
   exit code, and removes the tunnel in cleanup. Do not use a Docker `ssh://`
   context, create a second tunnel, or leave one running persistently.
   The wrapper resolves `ssh.exe` from `PATH`, then Windows OpenSSH and Git for
   Windows locations. If it cannot find a client, report that prerequisite;
   do not conclude that OpenSSH is absent merely because it is not on `PATH`.

3. Testcontainers 1.21.3 currently requires the existing user-level docker-java
   compatibility setting `api.version=1.44` for Docker Engine 29.x. Verify or
   report that prerequisite when Docker API negotiation fails; never create or
   modify the user configuration from this workflow. Consider a Testcontainers
   upgrade separately as a dependency change.

## Failure Classification and Safety

- Treat an occupied `127.0.0.1:23750`, SSH startup/readiness failure, Docker API
  connection failure, or Testcontainers/Ryuk/container startup or host-port
  reachability failure as Docker infrastructure failures. Report the failed
  boundary and preserve the command output; do not interfere with the process
  already using the port.
- Treat Maven, Flyway, SQL, or assertion failures after Testcontainers has
  started successfully as normal test or application failures, not tunnel
  failures. The wrapper cleanup still applies.
- The remote Engine also runs persistent services. Never run `docker system
  prune`, image or volume prune, alter the daemon, expose its API on the LAN,
  or stop, remove, restart, or modify unrelated containers, networks, or
  volumes. Testcontainers-managed disposable resources are allowed; cached
  Ryuk and test images may remain and must not be cleaned automatically.
