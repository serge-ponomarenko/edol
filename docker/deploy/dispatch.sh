#!/usr/bin/env bash
# Executed only by an explicitly dispatched workflow. Never source instance secrets.
set -euo pipefail
umask 077

mode="${1:?mode required}"
[[ "$mode" == secure || "$mode" == home ]]
[[ "${EDOL_SOURCE_SHA:?}" =~ ^[0-9a-f]{40}$ ]]
[[ "$(git rev-parse HEAD)" == "$EDOL_SOURCE_SHA" ]]
[[ "${EDOL_DEV_SSH_HOST:?}" =~ ^[a-zA-Z0-9.-]+$ ]]
[[ "${EDOL_DEV_SSH_USER:?}" =~ ^[a-zA-Z0-9_-]+$ ]]
[[ "${EDOL_DEV_SSH_PORT:?}" =~ ^[0-9]+$ ]]

task_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/edol-deploy.XXXXXXXX")"
trap 'rm -rf -- "$task_dir"' EXIT
printf '%s\n' "${EDOL_DEV_SSH_KEY:?}" > "$task_dir/key"
printf '%s\n' "${EDOL_DEV_SSH_KNOWN_HOSTS:?}" > "$task_dir/known_hosts"
ssh_options=(-T -o BatchMode=yes -o StrictHostKeyChecking=yes -o IdentitiesOnly=yes
  -o "UserKnownHostsFile=$task_dir/known_hosts" -i "$task_dir/key" -p "$EDOL_DEV_SSH_PORT")
destination="$EDOL_DEV_SSH_USER@$EDOL_DEV_SSH_HOST"

sources=(pom.xml .dockerignore docker)
for module in edol-core-api edol-core edol-hub edol-ams edol-notify; do
  sources+=("$module/pom.xml" "$module/src")
done
# Explicit source allowlist excludes .git, all .env files, target, logs and SDK archives.
tar --exclude='__pycache__' --exclude='*.pyc' --exclude='application-dev.yaml' \
  --exclude='application-dev.yml' -czf "$task_dir/source.tar.gz" "${sources[@]}"

# The remote fixed root is operator-provisioned. No repository reset/clean is used.
remote_dir="$(ssh "${ssh_options[@]}" "$destination" 'umask 077; mktemp -d /srv/edol-dev/releases/release.XXXXXXXX')"
[[ "$remote_dir" =~ ^/srv/edol-dev/releases/release\.[a-zA-Z0-9]+$ ]]
scp -q -o BatchMode=yes -o StrictHostKeyChecking=yes -o IdentitiesOnly=yes \
  -o "UserKnownHostsFile=$task_dir/known_hosts" -i "$task_dir/key" -P "$EDOL_DEV_SSH_PORT" \
  "$task_dir/source.tar.gz" "$destination:$remote_dir/source.tar.gz"
ssh "${ssh_options[@]}" "$destination" \
  "tar -xzf '$remote_dir/source.tar.gz' -C '$remote_dir' && python3 '$remote_dir/docker/deploy/dev_deploy.py' deploy-dev --config '/srv/edol-dev/$mode/instance.json' --mode '$mode' --revision '$EDOL_SOURCE_SHA'"
