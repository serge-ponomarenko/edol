"""Verify empty migration results and data-preserving reprovisioning in a temporary cluster.

This verifies PostgreSQL SQL/ownership/RLS, not Flyway CLI or application startup.
No existing profile, cluster, Docker daemon or server is contacted.
"""

import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_deployment import fixture
import database
import dev_deploy


def execute(command, data=None):
    result = subprocess.run(command, input=data, text=True, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout


def sql(pg_bin, port, text, database_name="postgres", user="postgres"):
    return execute([str(pg_bin / "psql"), "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-h", "127.0.0.1",
                    "-p", str(port), "-U", user, "-d", database_name], text)


def migrate(pg_bin, port, config):
    for module in ("core", "hub", "ams"):
        if module == "ams" and config["mode"] == "home":
            continue
        root = dev_deploy.ROOT / f"edol-{module}/src/main/resources/db"
        files = list((root / "migration").glob("V*__*.sql"))
        if module == "core" and config["mode"] == "secure":
            files += list((root / "secure-migration").glob("V*__*.sql"))
        files.sort(key=lambda p: int(p.name.split("__")[0][1:]))
        role = module + "_schema_owner" if config["mode"] == "secure" else "home"
        database_name = "edol_hub" if module == "hub" else "edol_devices"
        prefix = f"SET ROLE {role}; SET search_path TO {module},public;\n"
        for source in files:
            sql(pg_bin, port, prefix + source.read_text(), database_name)
        if module == "hub" and config["mode"] == "home":
            for source in (root / "home-migration").glob("R__*.sql"):
                sql(pg_bin, port, prefix + source.read_text(), database_name)
        # A minimal history table tests the deployment's grants, not Flyway bookkeeping.
        sql(pg_bin, port, prefix + f"CREATE TABLE IF NOT EXISTS {module}.flyway_schema_history(installed_rank integer);", database_name)
    sql(pg_bin, port, database.post_migration(config))


def exercise(pg_bin, port, mode):
    config = fixture(mode)
    sql(pg_bin, port, database.provision(config))
    migrate(pg_bin, port, config)
    for table in ("printers", "users", "filaments", "filament_spools", "print_jobs"):
        assert sql(pg_bin, port, f"SELECT count(*) FROM hub.{table};", "edol_hub").strip() == "0"
    assert sql(pg_bin, port, "SELECT count(*) FROM core.printers;", "edol_devices").strip() == "0"
    if mode == "secure":
        assert sql(pg_bin, port, "SELECT count(*) FROM core.agents; SELECT count(*) FROM ams.terminals;", "edol_devices").strip() == "0\n0"
    # Simulate a later operator change in this disposable test cluster only.
    if mode == "secure":
        tenant = "019a7000-0000-7000-8000-000000000001"
        sql(pg_bin, port, f"INSERT INTO hub.tenants(id,name) VALUES ('{tenant}','Operator test tenant');", "edol_hub")
    else:
        tenant = sql(pg_bin, port, "SELECT tenant_id FROM hub.home_installations;", "edol_hub").strip()
    sql(pg_bin, port, f"INSERT INTO hub.material_types(tenant_id,name) VALUES ('{tenant}','Operator test PLA');", "edol_hub")
    for name in ("edol_devices", "edol_hub"):
        sql(pg_bin, port, "CREATE TABLE public.operator_sentinel(id integer); INSERT INTO public.operator_sentinel VALUES (17);", name)
    if mode == "secure":
        sql(pg_bin, port, "CREATE TABLE public.operator_sentinel(id integer); INSERT INTO public.operator_sentinel VALUES (19);", "edol_keycloak")
    sql(pg_bin, port, database.provision(config))
    sql(pg_bin, port, database.post_migration(config))
    for name in ("edol_devices", "edol_hub"):
        assert sql(pg_bin, port, "SELECT id FROM public.operator_sentinel;", name).strip() == "17"
    assert sql(pg_bin, port, "SELECT count(*) FROM hub.material_types;", "edol_hub").strip() == "1"
    if mode == "secure":
        verify_secure(pg_bin, port, tenant)
    print(json.dumps({"mode": mode, "empty_schema": "PASS", "operator_data_preserved": "PASS"}))


def verify_secure(pg_bin, port, tenant):
    assert sql(pg_bin, port, "SELECT id FROM public.operator_sentinel;", "edol_keycloak").strip() == "19"
    assert sql(pg_bin, port, "SELECT count(*) FROM hub.material_types;", "edol_hub", "hub_runtime").strip() == "0"
    query = f"BEGIN; SET LOCAL edol.tenant_id='{tenant}'; SELECT count(*) FROM hub.material_types; ROLLBACK;"
    assert sql(pg_bin, port, query, "edol_hub", "hub_runtime").strip() == "1"
    assert sql(pg_bin, port, "SELECT has_table_privilege('hub_runtime','hub.flyway_schema_history','INSERT');", "edol_hub").strip() == "f"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pg-bin", type=Path, required=True)
    args = parser.parse_args()
    for mode in ("secure", "home"):
        with tempfile.TemporaryDirectory(prefix="edol-deploy-pg-") as temporary:
            root = Path(temporary)
            data = root / "data"
            execute([str(args.pg_bin / "initdb"), "-D", str(data), "-U", "postgres", "-A", "trust", "--locale=C", "--encoding=UTF8"])
            with socket.socket() as sock:
                sock.bind(("127.0.0.1", 0))
                port = sock.getsockname()[1]
            with (root / "postgres.log").open("w") as log:
                process = subprocess.Popen([str(args.pg_bin / "postgres"), "-D", str(data), "-p", str(port), "-h", "127.0.0.1"],
                                           stdout=log, stderr=log,
                                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
                try:
                    wait_ready(args.pg_bin, port, process)
                    exercise(args.pg_bin, port, mode)
                finally:
                    if process.poll() is None:
                        execute([str(args.pg_bin / "pg_ctl"), "-D", str(data), "-m", "fast", "-w", "stop"])
                    process.wait(timeout=30)


def wait_ready(pg_bin, port, process):
    for _ in range(50):
        if process.poll() is not None:
            raise RuntimeError("Owned temporary PostgreSQL exited before readiness")
        check = subprocess.run([str(pg_bin / "pg_isready"), "-h", "127.0.0.1", "-p", str(port)], capture_output=True)
        if check.returncode == 0:
            return
        time.sleep(0.2)
    raise RuntimeError("Owned temporary PostgreSQL readiness timeout")


if __name__ == "__main__":
    main()
