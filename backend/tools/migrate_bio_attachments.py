#!/usr/bin/env python3
"""One-time migration of Biodiversity attachment bytes: Oracle BLOB -> object storage.

This is throwaway cutover tooling, not part of the application. Delete it once Phase 4b
has shipped and the migration is proven. See bio-attachments-object-storage.local.md.

WHAT IT DOES
    For every row in THE.BIODIVERSITY_ATTACH_CONTENT with a non-empty BLOB, writes those
    bytes to the shared object store at key `slr/<biodiversity_chklst_attach_id>` -- the
    exact key the app reads (ProtocolChecklistWriteRepositoryImpl.BIO_OBJECT_PREFIX and
    ProtocolChecklistService.bioObjectKey).

    Only the bytes matter. The app takes fileName and mimeType from Oracle via
    FREP_CHECKLIST_ATTACHMENTS.GET_BLOB and reads only the body from object storage, so
    object metadata and content-type are cosmetic here.

PROD SIZE (measured 2026-08-17)
    5,005 metadata rows / 5,005 content rows / 0 orphans / 3 empty BLOBs.
    2.0 GB total, 3.3 MB largest, ~390 KB mean => 5,002 objects to write, minutes to run.

MODES
    preflight Read-only, no bulk work. Proves the connection, the grants and the LOB read
              before anything else is attempted. Run this FIRST, in DEV, then in PROD.
              --write-probe additionally writes and deletes one throwaway object to prove
              put/delete permissions.
    plan      Read-only. Counts, byte totals, largest row, empty-BLOB ids, and how many
              objects already exist. Run after preflight passes.
    migrate   Copies BLOB -> object storage. Idempotent: skips keys that already exist
              (--force overwrites) and skips empty BLOBs. Safe to re-run after a failure.
    verify    Read-only. One paginated listing compared against the DB. This is the
              go-live gate; it must pass before Phase 4b removes the read fallback.

RUNBOOK (in-cluster, PROD)
    NS=<prod-namespace>; ZONE=prod

    # DATABASE_HOST / DATABASE_SERVICE_NAME are Template params, not Secret keys --
    # read them off the running backend rather than retyping them.
    DB_HOST=$(oc -n $NS set env deploy/nr-frep-backend-$ZONE --list | grep ^DATABASE_HOST= | cut -d= -f2)
    DB_SVC=$(oc -n $NS set env deploy/nr-frep-backend-$ZONE --list | grep ^DATABASE_SERVICE_NAME= | cut -d= -f2)

    # The `app` label puts the pod under the existing -egress NetworkPolicy.
    oc -n $NS run bio-attach-migrate --rm -it --restart=Never \
        --image=python:3.12-slim \
        --labels="app=nr-frep-backend-$ZONE" \
        --env="DATABASE_HOST=$DB_HOST" --env="DATABASE_SERVICE_NAME=$DB_SVC" \
        --overrides='{"spec":{"containers":[{"name":"bio-attach-migrate","image":"python:3.12-slim",
                      "stdin":true,"tty":true,"command":["bash"],
                      "envFrom":[{"secretRef":{"name":"nr-frep-backend-secret-'$ZONE'"}}]}]}}'

    # then, inside the pod:
    pip install --quiet oracledb boto3
    #   paste this script to /tmp/m.py (oc cp, or a heredoc)
    python /tmp/m.py plan
    python /tmp/m.py migrate
    python /tmp/m.py verify

TLS NOTE (the one thing that may bite)
    The app reaches Oracle over TCPS/1543 and gets its truststore from an initContainer,
    which means the DB certificate is probably not chained to a public CA. python-oracledb
    thin mode verifies the server certificate too. If `plan` fails on a TLS/certificate
    error, grab the chain from inside the cluster and point the script at it:

        openssl s_client -showcerts -connect $DATABASE_HOST:1543 </dev/null 2>/dev/null \
            | openssl x509 -outform PEM > /tmp/wallet/ewallet.pem
        export ORACLE_WALLET_DIR=/tmp/wallet

    If that turns into a fight, the batched admin-endpoint alternative sidesteps it
    entirely -- the app already holds a working DB connection.

ENVIRONMENT
    DATABASE_HOST, DATABASE_SERVICE_NAME, DATABASE_USER, DATABASE_PASSWORD
    DATABASE_PORT           optional, default 1543
    OBJECT_STORAGE_HOST, OBJECT_STORAGE_BUCKET,
    OBJECT_STORAGE_ACCESS_KEY, OBJECT_STORAGE_SECRET_KEY
    ORACLE_WALLET_DIR       optional, see TLS NOTE
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import boto3
import oracledb
from botocore.config import Config
from botocore.exceptions import ClientError

KEY_PREFIX = "slr/"
CONTENT_TABLE = "THE.BIODIVERSITY_ATTACH_CONTENT"
META_TABLE = "THE.BIODIVERSITY_CHKLST_ATTACH"
ID_COLUMN = "BIODIVERSITY_CHKLST_ATTACH_ID"
BLOB_COLUMN = "ATTACHMENT_CONTENT"

DEFAULT_MANIFEST = "/tmp/bio-attach-manifest.json"


# ── connections ────────────────────────────────────────────────────────────────

def require_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        sys.exit(f"missing required environment variable: {name}")
    return value


def oracle_pool(size: int) -> oracledb.ConnectionPool:
    """A fixed-size pool: one connection per worker, so memory stays bounded at
    concurrency x file size rather than batch size x file size."""
    kwargs = dict(
        user=require_env("DATABASE_USER"),
        password=require_env("DATABASE_PASSWORD"),
        dsn=(
            f'(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)'
            f'(HOST={require_env("DATABASE_HOST")})'
            f'(PORT={os.environ.get("DATABASE_PORT", "1543")}))'
            f'(CONNECT_DATA=(SERVICE_NAME={require_env("DATABASE_SERVICE_NAME")})'
            f'(SERVER=DEDICATED)))'
        ),
        min=size,
        max=size,
        increment=0,
    )
    wallet = os.environ.get("ORACLE_WALLET_DIR")
    if wallet:
        kwargs["wallet_location"] = wallet
    return oracledb.create_pool(**kwargs)


def s3_client():
    """S3 client matching ObjectStorageService: path-style, us-east-1, and flexible
    checksums off -- the BC Gov gateway rejects CRC32 + aws-chunked trailers with a
    'Content-SHA256 did not match' 400 (see the comment in ObjectStorageService.client)."""
    checksum_opts = {}
    try:  # botocore >= 1.36 defaults these on; older versions reject the kwargs
        Config(request_checksum_calculation="when_required")
        checksum_opts = {
            "request_checksum_calculation": "when_required",
            "response_checksum_validation": "when_required",
        }
    except TypeError:
        pass

    return boto3.client(
        "s3",
        endpoint_url=require_env("OBJECT_STORAGE_HOST"),
        aws_access_key_id=require_env("OBJECT_STORAGE_ACCESS_KEY"),
        aws_secret_access_key=require_env("OBJECT_STORAGE_SECRET_KEY"),
        region_name="us-east-1",
        config=Config(
            signature_version="s3v4",
            s3={"addressing_style": "path"},
            retries={"max_attempts": 5, "mode": "standard"},
            **checksum_opts,
        ),
    )


# ── reads ──────────────────────────────────────────────────────────────────────

def fetch_inventory(pool, after_id: int | None, limit: int | None):
    """(id, blob_length) for every content row, ascending. 5k rows -- one short query,
    no cursor held open across the uploads."""
    sql = (
        f"SELECT c.{ID_COLUMN}, DBMS_LOB.GETLENGTH(c.{BLOB_COLUMN}) "
        f"  FROM {CONTENT_TABLE} c "
        f" WHERE (:after_id IS NULL OR c.{ID_COLUMN} > :after_id) "
        f" ORDER BY c.{ID_COLUMN}"
    )
    if limit:
        sql += " FETCH FIRST :row_limit ROWS ONLY"
    binds = {"after_id": after_id}
    if limit:
        binds["row_limit"] = limit

    with pool.acquire() as conn, conn.cursor() as cur:
        cur.execute(sql, binds)
        return [(int(row[0]), int(row[1] or 0)) for row in cur]


def fetch_blob(pool, attach_id: int) -> bytes:
    with pool.acquire() as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT {BLOB_COLUMN} FROM {CONTENT_TABLE} WHERE {ID_COLUMN} = :id",
            {"id": attach_id},
        )
        row = cur.fetchone()
        if row is None or row[0] is None:
            return b""
        lob = row[0]
        return lob.read() if hasattr(lob, "read") else bytes(lob)


def list_existing_keys(s3, bucket: str) -> dict[str, int]:
    """key -> size for everything under slr/. One paginated listing (5k keys = 6 pages)
    rather than a HEAD per id."""
    sizes: dict[str, int] = {}
    for page in s3.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=KEY_PREFIX):
        for obj in page.get("Contents", []):
            sizes[obj["Key"]] = obj["Size"]
    return sizes


# ── modes ──────────────────────────────────────────────────────────────────────

def cmd_preflight(args, pool, s3, bucket):
    """Cheapest possible proof that the two things this script needs actually work as the
    app's DB user. Two distinct failure modes are in play and they look nothing alike:

      ORA-28759 / ORA-29024 / TLS errors -> the TCPS trust problem; see TLS NOTE above.
      ORA-00942 'table or view does not exist' -> a GRANT problem, not a typo. The app
          reads these tables through FREP_CHECKLIST_ATTACHMENTS, which runs with DEFINER
          rights, so DATABASE_USER may never have been granted SELECT on them directly.
          Fix: GRANT SELECT ON THE.<table> TO <DATABASE_USER>; (same as the grant the
          native attachment-list query needed).
    """
    failures = []

    with pool.acquire() as conn, conn.cursor() as cur:
        cur.execute("SELECT USER, SYS_CONTEXT('USERENV','DB_NAME') FROM dual")
        user, db_name = cur.fetchone()
        print(f"connected  : {user}@{db_name}  (TCPS OK)")

    for table in (META_TABLE, CONTENT_TABLE):
        try:
            with pool.acquire() as conn, conn.cursor() as cur:
                cur.execute(f"SELECT COUNT(*) FROM {table}")
                print(f"SELECT ok  : {table}  ({cur.fetchone()[0]} rows)")
        except oracledb.Error as exc:
            failures.append(f"{table}: {exc}")
            print(f"SELECT FAIL: {table}  -> {exc}")

    # A real LOB fetch on the smallest non-empty row: proves DBMS_LOB access and the
    # thin-mode LOB read path without pulling anything large.
    try:
        with pool.acquire() as conn, conn.cursor() as cur:
            cur.execute(
                f"SELECT {ID_COLUMN} FROM {CONTENT_TABLE} "
                f" WHERE DBMS_LOB.GETLENGTH({BLOB_COLUMN}) > 0 "
                f" ORDER BY DBMS_LOB.GETLENGTH({BLOB_COLUMN}) FETCH FIRST 1 ROWS ONLY"
            )
            row = cur.fetchone()
        if row:
            sample_id = int(row[0])
            data = fetch_blob(pool, sample_id)
            print(f"LOB read ok: id {sample_id}, {len(data):,} bytes "
                  f"-> would write key {KEY_PREFIX}{sample_id}")
        else:
            print("LOB read   : skipped, no non-empty rows")
    except oracledb.Error as exc:
        failures.append(f"LOB read: {exc}")
        print(f"LOB read FAIL: {exc}")

    try:
        listed = s3.list_objects_v2(Bucket=bucket, Prefix=KEY_PREFIX, MaxKeys=1)
        print(f"bucket ok  : {bucket}  (list under {KEY_PREFIX} returned "
              f"{len(listed.get('Contents', []))} key(s))")
    except ClientError as exc:
        failures.append(f"bucket list: {exc}")
        print(f"bucket FAIL: {exc}")

    if args.write_probe:
        probe = f"{KEY_PREFIX}_preflight-probe"
        try:
            s3.put_object(Bucket=bucket, Key=probe, Body=b"probe",
                          ContentType="application/octet-stream")
            s3.delete_object(Bucket=bucket, Key=probe)
            print(f"write ok   : put+delete {probe}")
        except ClientError as exc:
            failures.append(f"write probe: {exc}")
            print(f"write FAIL : {exc}")
    else:
        print("write probe: skipped (pass --write-probe to test put/delete)")

    if failures:
        print(f"\nPREFLIGHT FAILED ({len(failures)}):")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("\nPREFLIGHT PASSED -- safe to run `plan`.")
    return 0


def cmd_plan(args, pool, s3, bucket):
    inventory = fetch_inventory(pool, args.after_id, args.limit)
    empties = [i for i, n in inventory if n == 0]
    payload = [(i, n) for i, n in inventory if n > 0]
    existing = list_existing_keys(s3, bucket)

    with pool.acquire() as conn, conn.cursor() as cur:
        cur.execute(f"SELECT COUNT(*) FROM {META_TABLE}")
        meta_rows = cur.fetchone()[0]

    total = sum(n for _, n in payload)
    todo = [(i, n) for i, n in payload if f"{KEY_PREFIX}{i}" not in existing]

    print(f"metadata rows        : {meta_rows}")
    print(f"content rows         : {len(inventory)}")
    print(f"  with bytes         : {len(payload)}  ({total:,} bytes, {total / 1e9:.2f} GB)")
    print(f"  empty/NULL BLOB    : {len(empties)}  -> skipped: {empties}")
    print(f"largest row          : {max((n for _, n in payload), default=0):,} bytes")
    print(f"objects already in {KEY_PREFIX}: {len(existing)}")
    print(f"to migrate now       : {len(todo)}  ({sum(n for _, n in todo):,} bytes)")
    print()
    print(f"GATE: verify must reach {len(payload)} objects, NOT {meta_rows} -- the "
          f"{len(empties)} empty-BLOB row(s) can never have one.")


def cmd_migrate(args, pool, s3, bucket):
    inventory = fetch_inventory(pool, args.after_id, args.limit)
    empties = [i for i, n in inventory if n == 0]
    payload = [(i, n) for i, n in inventory if n > 0]

    existing = set() if args.force else set(list_existing_keys(s3, bucket))
    todo = [(i, n) for i, n in payload if f"{KEY_PREFIX}{i}" not in existing]
    skipped_existing = len(payload) - len(todo)

    print(f"{len(todo)} object(s) to write, {sum(n for _, n in todo):,} bytes; "
          f"skipping {skipped_existing} already present and {len(empties)} empty")

    migrated: list[int] = []
    failed: list[dict] = []
    lock = threading.Lock()
    started = time.monotonic()

    def worker(attach_id: int, expected: int):
        data = fetch_blob(pool, attach_id)
        if len(data) != expected:
            raise ValueError(f"read {len(data)} bytes, DBMS_LOB reported {expected}")
        s3.put_object(
            Bucket=bucket,
            Key=f"{KEY_PREFIX}{attach_id}",
            Body=data,
            ContentType="application/octet-stream",
        )
        return attach_id

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool_exec:
        futures = {pool_exec.submit(worker, i, n): i for i, n in todo}
        for done in as_completed(futures):
            attach_id = futures[done]
            try:
                done.result()
                with lock:
                    migrated.append(attach_id)
                    count = len(migrated)
                if count % 250 == 0 or count == len(todo):
                    rate = count / max(time.monotonic() - started, 0.001)
                    print(f"  {count}/{len(todo)}  ({rate:.0f}/s)")
            except (ClientError, oracledb.Error, ValueError) as exc:
                with lock:
                    failed.append({"id": attach_id, "error": f"{type(exc).__name__}: {exc}"})

    manifest = {
        "migrated": sorted(migrated),
        "failed": sorted(failed, key=lambda f: f["id"]),
        "skipped_empty": empties,
        "skipped_existing": skipped_existing,
        "elapsed_seconds": round(time.monotonic() - started, 1),
    }
    with open(args.manifest, "w") as handle:
        json.dump(manifest, handle, indent=2)

    print(f"\nmigrated {len(migrated)}, failed {len(failed)}, "
          f"skipped {skipped_existing} existing + {len(empties)} empty "
          f"in {manifest['elapsed_seconds']}s")
    print(f"manifest: {args.manifest}")
    if failed:
        print(f"FAILED ids: {[f['id'] for f in failed]}")
        print("re-run `migrate` -- it skips what already landed and retries only these")
        return 1
    return 0


def cmd_verify(args, pool, s3, bucket):
    inventory = fetch_inventory(pool, None, None)
    expected = {i: n for i, n in inventory if n > 0}
    empties = [i for i, n in inventory if n == 0]
    actual = list_existing_keys(s3, bucket)

    missing = [i for i in expected if f"{KEY_PREFIX}{i}" not in actual]
    mismatched = [
        {"id": i, "db_bytes": n, "object_bytes": actual[f"{KEY_PREFIX}{i}"]}
        for i, n in expected.items()
        if f"{KEY_PREFIX}{i}" in actual and actual[f"{KEY_PREFIX}{i}"] != n
    ]
    orphans = [k for k in actual if k[len(KEY_PREFIX):].isdigit()
               and int(k[len(KEY_PREFIX):]) not in expected]

    print(f"expected objects : {len(expected)}")
    print(f"found objects    : {len(actual)}")
    print(f"missing          : {len(missing)}  {missing[:20]}{' ...' if len(missing) > 20 else ''}")
    print(f"size mismatches  : {len(mismatched)}  {mismatched[:10]}")
    print(f"unexpected keys  : {len(orphans)}  {orphans[:10]}")
    print(f"empty-BLOB rows  : {len(empties)}  {empties} (excluded by design)")

    if missing or mismatched:
        print("\nGATE FAILED -- do not go live, and do not ship Phase 4b.")
        return 1
    print("\nGATE PASSED -- every non-empty BLOB has a byte-identical object.")
    if empties:
        print(f"NOTE: ids {empties} have no bytes in Oracle either. After 4b removes the "
              f"BLOB fallback they will error rather than return an empty file; either "
              f"delete those metadata rows at cutover or handle not-found as a 404.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("mode", choices=["preflight", "plan", "migrate", "verify"])
    parser.add_argument("--concurrency", type=int, default=8,
                        help="parallel streams (default 8; peak memory ~= this x 3.3 MB)")
    parser.add_argument("--after-id", type=int, default=None,
                        help="only rows with id > this (partial runs)")
    parser.add_argument("--limit", type=int, default=None, help="cap rows considered")
    parser.add_argument("--force", action="store_true",
                        help="overwrite objects that already exist")
    parser.add_argument("--write-probe", action="store_true",
                        help="preflight only: put+delete one throwaway object")
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    args = parser.parse_args()

    bucket = require_env("OBJECT_STORAGE_BUCKET")
    s3 = s3_client()
    pool = oracle_pool(1 if args.mode == "preflight" else args.concurrency)
    try:
        modes = {"preflight": cmd_preflight, "plan": cmd_plan,
                 "migrate": cmd_migrate, "verify": cmd_verify}
        return modes[args.mode](args, pool, s3, bucket) or 0
    finally:
        pool.close()


if __name__ == "__main__":
    sys.exit(main())
