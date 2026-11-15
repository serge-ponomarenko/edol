#!/usr/bin/env bash
# Dev is manually dispatched; production runs only after main CI succeeds.
set -euo pipefail
umask 077

mode="${1:?mode required}"
environment="${2:-dev}"
[[ "$mode" == secure || "$mode" == home ]]
[[ "$environment" == dev || "$environment" == prod ]]
if [[ "$environment" == prod ]]; then
  [[ "${GITHUB_REF:?}" == refs/heads/main ]]
  EDOL_DEV_SSH_HOST="${EDOL_PROD_SSH_HOST:?}"
  EDOL_DEV_SSH_USER="${EDOL_PROD_SSH_USER:?}"
  EDOL_DEV_SSH_PORT="${EDOL_PROD_SSH_PORT:?}"
  EDOL_DEV_SSH_KEY="${EDOL_PROD_SSH_KEY:?}"
  EDOL_DEV_SSH_KNOWN_HOSTS="${EDOL_PROD_SSH_KNOWN_HOSTS:?}"
fi
[[ "${EDOL_SOURCE_SHA:?}" =~ ^[0-9a-f]{40}$ ]]
[[ "$(git rev-parse HEAD)" == "$EDOL_SOURCE_SHA" ]]
generation_args=""
if [[ "$environment" == prod ]]; then
  [[ "$(git rev-parse --is-shallow-repository)" == false ]]
  generation="$(git rev-list --count HEAD)"
  [[ "$generation" =~ ^[1-9][0-9]*$ ]]
  generation_args="--source-generation $generation"
fi
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
remote_root=/srv/edol-dev
[[ "$environment" != prod ]] || remote_root=/srv/edol
remote_dir="$(ssh "${ssh_options[@]}" "$destination" "umask 077; mktemp -d '$remote_root/releases/release.XXXXXXXX'")"
[[ "$remote_dir" =~ ^$remote_root/releases/release\.[a-zA-Z0-9]+$ ]]
scp -q -o BatchMode=yes -o StrictHostKeyChecking=yes -o IdentitiesOnly=yes \
  -o "UserKnownHostsFile=$task_dir/known_hosts" -i "$task_dir/key" -P "$EDOL_DEV_SSH_PORT" \
  "$task_dir/source.tar.gz" "$destination:$remote_dir/source.tar.gz"
ssh "${ssh_options[@]}" "$destination" \
  "tar -xzf '$remote_dir/source.tar.gz' -C '$remote_dir' && python3 '$remote_dir/docker/deploy/dev_deploy.py' deploy-$environment --config '$remote_root/$mode/instance.json' --mode '$mode' --revision '$EDOL_SOURCE_SHA' $generation_args"
