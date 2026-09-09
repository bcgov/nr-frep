# BC Gov SSO cutover — what still has to happen outside this repo

The code in this repository is migrated: it authenticates against BC Gov SSO (Keycloak's standard
realm, via a CSS integration) using `oidc-client-ts` on the front end and an `azp`-checking OAuth2
resource server on the back end. Nothing in it works until the integration itself exists, and
standing up the integration gets you a working login and an **empty** application — the access
grants and the audit history live in the legacy FAM database and have to be moved deliberately.

This page is the part that isn't code.

---

## 0. What DEV already looks like (probed 2026-09-08)

The DEV integration exists. Its Installation JSON gives:

| | |
|---|---|
| `auth-server-url` | `https://dev.loginproxy.gov.bc.ca/auth` |
| `realm` | `standard` |
| `public-client` | `true` |
| `resource` (client id) | `forest-and-range-evaluation-program-6538` |

So `KEYCLOAK_ISSUER_URI` = `https://dev.loginproxy.gov.bc.ca/auth/realms/standard` and
`KEYCLOAK_CLIENT_ID` = `forest-and-range-evaluation-program-6538`. The trailing `6538` is the CSS
**integration id** — the value §3's audit import wants as `integration`.

**The same client id is provisioned in all three realms** — probing DEV, TEST and PROD, a bogus
client id returns `Client not found` while `forest-and-range-evaluation-program-6538` returns
`Invalid parameter` (i.e. the client resolves, the redirect URI does not). So `KEYCLOAK_CLIENT_ID` is
one value repeated across the three environments; only `KEYCLOAK_ISSUER_URI` differs:

| Env | `KEYCLOAK_ISSUER_URI` | Client | Redirect URIs |
|---|---|---|---|
| dev | `https://dev.loginproxy.gov.bc.ca/auth/realms/standard` | ✅ | `*` — accepts any origin (accepted risk, see §1) |
| test | `https://test.loginproxy.gov.bc.ca/auth/realms/standard` | ✅ | ✅ scoped correctly (verified 2026-09-08) |
| prod | `https://loginproxy.gov.bc.ca/auth/realms/standard` | ✅ | ✅ scoped correctly (verified 2026-09-08) |

TEST and PROD each hold exactly their `/authCallback` URI plus the bare origin for post-logout, and
reject everything else. Both were probed with the same controls, all correctly refused:

| Control | Why it matters |
|---|---|
| `https://attacker.example/steal` | the basic open-redirect case |
| `https://<host>.attacker.example/authCallback` | suffix confusion — a prefix match would allow it |
| `http://frep.nrs.gov.bc.ca/authCallback` (prod) | scheme downgrade |
| TEST's URI against the PROD realm | cross-environment bleed |

PROD cannot use a wildcard at all (see §1), which suits it: one vanity host,
`frontend/openshift.vanity-route.yml`.

Probing DEV directly (the commands in §2) confirmed:

- ✅ The issuer string matches our config **exactly**, and JWKS is at
  `/protocol/openid-connect/certs` — which is what `application.yml` derives.
- ✅ The client resolves and `http://localhost:3000/authCallback` is registered.
- ✅ **PKCE is enforced** — a request without `code_challenge_method` is rejected.
- ⚠️ **Only `azureidir` is live** — on DEV *and* TEST. Every `kc_idp_hint` (none, `idir`,
  `bceidbusiness`, and a deliberately fake alias) resolves to `/broker/azureidir/login`. Business
  BCeID is selected on the integration but **pending approval**, and until it lands the BCeID button
  silently signs users in through IDIR. No code change needed; the hint is already `bceidbusiness`
  and starts working on its own once approved. Re-run probe 3 to confirm when it does.
- ⚠️ **The redirect URI list is `*`.** `https://attacker.example/steal` is accepted and would
  receive an authorization code. A deliberate, DEV-only trade-off — see §1.

---

## 1. CSS console checklist — per environment

Do DEV first. Everything else is unverifiable without it and the lead time is external, so this is
the one item that genuinely belongs at the start rather than the end.

- [ ] Integration created; note the client id
- [ ] Redirect URI `<origin><base>/authCallback`
- [ ] Post-logout redirect URI `<origin><base>`
- [ ] Access-token mappers: `idir_username`, `idir_user_guid`, `identity_provider`, `display_name`,
      `given_name`, `family_name`, `email`
- [ ] Roles created and assigned — use the **`roles-new`** endpoint; the older `roles` endpoint 404s
      for anyone who has never signed in
- [ ] Existing FAM/Cognito group membership migrated to CSS role assignments (§3)
- [ ] Service-account client scopes, if the evaluator directory lookup is to keep working (§4)

**The mappers must be on the ACCESS token, not just the ID token.** FREP reads its profile claims
off the access token; there is no userinfo call any more. Get this wrong and `idir_username` is
absent, which silently routes every user down the GUID fallback in `JwtPrincipalUtil` — the app
works, and the ids it writes match nothing the legacy application ever wrote.

### Redirect URIs — the 50-slot scheme is now dead weight

**CSS allows wildcard redirect URIs in Dev and Test, but not in Prod** (stated in the CSS console).
A host-pattern such as `https://nr-frep-*.apps.gold.devops.gov.bc.ca/authCallback` is **not accepted**
by the CSS app — the only wildcard it takes is a bare `*`.

**Decision (2026-09-08): DEV stays at `*` for now.** The consequence is that DEV will hand an
authorization code to any origin, so a crafted link could capture a DEV session with the victim's
DEV roles. Scoped to DEV, whose data is disposable, and TEST and PROD are both tightly enumerated —
PROD cannot be wildcarded at all. Revisit if CSS gains host patterns.

**The useful consequence:** the 50-hostname PR-preview bucketing (`nr-frep-0` … `nr-frep-49`,
`slot = PR# % 50`) exists *only* because Cognito rejected wildcards in CallbackURLs, and DEV's `*`
already accepts every preview hostname. Verified: `nr-frep-7`, `nr-frep-57`, `nr-frep-123` and
`nr-frep-9999` are all accepted. So the modulo serves no purpose, and the slot machinery in
`.github/workflows/pr-open.yml` and `frontend/openshift.route.yml` can be deleted — which also
removes the `HostAlreadyClaimed` collision between PR 7 and PR 57.

That deletion depends on DEV keeping a wildcard. If DEV is ever tightened to an enumerated list, the
slots (or an equivalent) have to come back.

PROD gets exact URIs, which is what it wants anyway: one vanity host, no pattern.

---

## 2. Probe the realm before debugging the code

These take seconds and tell you whether the problem is yours or the integration's.

```bash
ISSUER=https://dev.loginproxy.gov.bc.ca/auth/realms/standard
CLIENT_ID=<the CSS integration's client id>

# 1. Realm reachable, and the issuer string matches your config EXACTLY
#    (oidc-client-ts validates the `iss` claim against the authority you gave it).
curl -s "$ISSUER/.well-known/openid-configuration" | jq -r .issuer

# 2. Does the client exist, and is the redirect URI registered?
#    "Client not found." = the client id is wrong.
#    A 302 back to your redirect_uri = client and URI are both good.
curl -s -o /dev/null -w '%{http_code}\n' -G "$ISSUER/protocol/openid-connect/auth" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=http://localhost:3000/authCallback" \
  --data-urlencode "response_type=code" --data-urlencode "scope=openid"

# 3. Does kc_idp_hint resolve to a real broker? A 303 to /broker/azureidir/login proves it.
#    An UNRECOGNISED hint is SILENTLY IGNORED — Keycloak falls through to whatever provider the
#    client has, so on a single-provider integration a wrong value still lands the user in the right
#    place and looks like it works, until a second provider is added.
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' -G "$ISSUER/protocol/openid-connect/auth" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=http://localhost:3000/authCallback" \
  --data-urlencode "response_type=code" --data-urlencode "scope=openid" \
  --data-urlencode "kc_idp_hint=azureidir"

# 4. Probe with a DELIBERATELY WRONG redirect URI. If the realm accepts
#    https://example.com/anything, the client's redirect list is a wildcard.
curl -s -o /dev/null -w '%{http_code}\n' -G "$ISSUER/protocol/openid-connect/auth" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=https://example.com/anything" \
  --data-urlencode "response_type=code" --data-urlencode "scope=openid"
```

### Symptoms and where to look

| Symptom | Cause |
|---|---|
| Every request 401s, health probes included | Issuer or JWKS path wrong; note Keycloak publishes at `/protocol/openid-connect/certs`, not `/.well-known/jwks.json` |
| Sign-in succeeds, then every API call 401s | The SPA's issuer/client id and the API's have drifted apart — the `azp` check refuses the token. Reads like a permissions problem; it is not |
| Signed in, but routed to `/unauthorized` | Roles are in the other claim, or not assigned. Both `client_roles` and `resource_access.<client>.roles` are read, so this is an assignment problem |
| Audit columns start reading `AZUREIDIR\…` | The provider normalisation was bypassed. `JwtPrincipalUtilTest` pins this |
| Logout "works" but the next sign-in never prompts | The stored user was cleared before `signoutRedirect()`, so the realm could not attribute the logout to a session |

---

## 3. Bringing the existing data across

> **Role names changed in the new FAM.** Legacy FAM held `FREP_ADMIN`, `FREP_EDITOR`, and 23
> `FREP_CHR_EDITOR_DISTRICT_<code>` roles. The CSS integration defines three: **`FREP_ADMINISTRATOR`**
> (renamed), **`FREP_EDITOR`** (unchanged), and **`FREP_CHR_EDITOR`** — one role with the district
> carried as a **scope** rather than baked into 23 role names.
>
> Every `FREP_ADMIN` / `FREP_CHR_EDITOR_DISTRICT_*` below is describing what the **legacy** extract
> returned, not what the app sees now. The application code was renamed to match on 2026-09-09; the
> district-scope handling is still open, pending confirmation of how the scope reaches the token.

Standing up the integration gets you an empty application. Do this pull **first** — it is the one
users notice, it is small, and it validates the role names before the much larger audit import.

The legacy FAM database is Aurora PostgreSQL in a private VPC; assume you cannot reach it from a
laptop, VPN or not (`psql` hangs rather than refusing). That leaves the **RDS Query Editor** (needs
the Data API enabled) or a snapshot → S3 → Athena export.

**The Query Editor is not psql**, and all three differences produce a syntax error somewhere other
than the mistake:

- `\set` does not exist — it is a psql client directive. Use a `params` CTE.
- Newlines are flattened before execution, so a `--` comment swallows the rest of the query. Use
  `/* … */` block comments, or none.
- A `;` inside a string literal splits the statement. Append it as `|| chr(59)` when generating SQL
  with SQL.

### 3.1 Discovery — run this first

Legacy models each environment as its own application row (`FREP_DEV`, `FREP_TEST`, `FREP_PROD`),
while the new integration has one client with three environments — so you extract per environment and
upload per environment.

**Step 0 — find the application rows.** Do not assume the names. `SELECT *` rather than named columns
because the point is to see what the table actually holds:

```sql
/* Query Editor: block comments only. A -- comment eats the rest of the query,
   because newlines are flattened before execution. */
SELECT *
FROM app_fam.fam_application
WHERE application_name ILIKE '%FREP%'
   OR application_name ILIKE '%FOREST%'
ORDER BY application_name;
```

If that returns nothing, drop the `WHERE` entirely and read the list — it is one row per application
per environment, so it is short enough to eyeball. Note the exact `application_name` values you get
back; every query below keys off them.

**Step 1 — roles and how many people hold each.** Substitute the names from step 0 if they are not
`FREP%`:

```sql
SELECT a.application_name,
       a.app_environment,
       r.role_name,
       r.role_type_code,
       COUNT(x.user_id) AS users
FROM app_fam.fam_application a
JOIN app_fam.fam_role r                ON r.application_id = a.application_id
LEFT JOIN app_fam.fam_user_role_xref x ON x.role_id = r.role_id
WHERE a.application_name LIKE 'FREP%'
GROUP BY a.application_name, a.app_environment, r.role_name, r.role_type_code
ORDER BY a.application_name, r.role_name;
```

#### What FREP's discovery actually returned (2026-09-08)

Three application rows, the **same 25 roles in each** (`FREP_ADMIN`, `FREP_EDITOR`, and 23
`FREP_CHR_EDITOR_DISTRICT_<code>`), so only the application row differs per environment:

| app | grant rows | admin | editor | CHR district | districts with holders |
|---|---|---|---|---|---|
| `FREP_DEV` | 3 | 3 | 0 | 0 | 0 of 23 |
| `FREP_TEST` | 21 | 20 | 1 | 0 | 0 of 23 |
| `FREP_PROD` | **94** | 6 | 43 | 45 | 18 of 23 |

**PROD is the only environment carrying real access.** DEV and TEST hold admin/editor grants only and
no CHR at all, so re-granting those by hand in CSS is likely cheaper than running the extract for
them. PROD districts with no holders: DFN, DMK, DQU, DSE, DSS.

Those are grant *rows*, not people — three districts for one person is three rows — and the discovery
query applies no expiry filter, so the uploadable count is lower. §3.2's filter is what decides it.

#### Two things this changes about the extract

**Every FREP role is `role_type_code = 'C'`, and none are `'A'`** — the opposite of the REPT case the
playbook's queries were written for. `'A'` is an abstract role and `'C'` a concrete one; legacy models
a scoped grant as a concrete role pointing at its abstract parent via `parent_role_id`, with the scope
(a forest client) hung off itself. Since there are no `'A'` rows here for anything to point at, these
are almost certainly parentless concrete roles — but confirm it, because §3.2's
`COALESCE(parent.role_name, r.role_name)` reports the **parent** whenever one is set, which would
silently collapse all 23 district roles into a single name:

```sql
/* If parent_role_id and client_number_id are NULL for every row, the
   COALESCE(parent.role_name, ...) and the fam_forest_client join in the
   extract are dead weight and the extract simplifies. */
SELECT r.role_name, r.role_type_code, r.parent_role_id,
       r.client_number_id, r.display_name
FROM app_fam.fam_role r
JOIN app_fam.fam_application a ON a.application_id = r.application_id
WHERE a.application_name = 'FREP_PROD'
ORDER BY r.role_name;
```

**All 23 district role names are 28 characters** (`FREP_CHR_EDITOR_DISTRICT_DCC`; the prefix alone is
25). Confirm whether CSS enforces a length cap **before** generating an upload file.

> Correction to an earlier draft of this page, which asserted a 25-character ceiling on CSS role
> names. The evidence for 25 is that **FAM's user-search API** returns 422 for a `role` parameter
> longer than that — a different system, and the reason the evaluator directory lookup already fails
> for district-scoped users today. FAM plainly *stores* 28-character role names, since these are
> them. Whether CSS caps role-name length is unverified.

If the names do have to shorten, it is a code change too: `FREP_CHR_EDITOR_DISTRICT_` is hardcoded as
`RoleConstants.CHR_DISTRICT_EDITOR_PREFIX` (backend) and `CHR_DISTRICT_EDITOR_PREFIX` in
`context/auth/types.ts` (frontend). Dropping just `DISTRICT_` gives `FREP_CHR_EDITOR_DCC` at 19.

Sizing the actual upload, expiry included:

```sql
SELECT COUNT(*) AS all_grants,
       COUNT(*) FILTER (WHERE x.expiry_date IS NULL OR x.expiry_date > now()) AS active_grants,
       COUNT(*) FILTER (WHERE x.expiry_date IS NOT NULL AND x.expiry_date <= now()) AS expired_grants,
       COUNT(DISTINCT x.user_id) AS distinct_users
FROM app_fam.fam_user_role_xref x
JOIN app_fam.fam_role r        ON r.role_id = x.role_id
JOIN app_fam.fam_application a ON a.application_id = r.application_id
WHERE a.application_name = 'FREP_PROD';
```

### 3.2 The access pull

One row per grant, in the columns the bulk uploader accepts. Somebody holding a role for three
organizations is three rows.

```sql
SELECT
    u.user_name AS username,
    CASE u.user_type_code WHEN 'I' THEN 'IDIR'
                          WHEN 'B' THEN 'BCEID'
                          ELSE u.user_type_code END AS user_type,
    COALESCE(parent.role_name, r.role_name) AS role,
    '' AS district,
    COALESCE(fc.forest_client_number, '') AS organization,
    '' AS region
FROM app_fam.fam_user_role_xref x
JOIN app_fam.fam_user               u      ON u.user_id  = x.user_id
JOIN app_fam.fam_role               r      ON r.role_id  = x.role_id
LEFT JOIN app_fam.fam_role          parent ON parent.role_id = r.parent_role_id
LEFT JOIN app_fam.fam_forest_client fc     ON fc.client_number_id = r.client_number_id
JOIN app_fam.fam_application        a      ON a.application_id = r.application_id
WHERE a.application_name = 'FREP_PROD'
  AND (x.expiry_date IS NULL OR x.expiry_date > now())
ORDER BY username, role, organization;
```

- `user_type_code` is a single letter in legacy (`'I'`/`'B'`) and a word in the new system.
- The `expiry_date` filter drops lapsed grants. Keep it for a file you are about to upload; drop it
  if you are reconciling against the old system.
- **Usernames, not GUIDs** — the uploader resolves each username against the directory itself, so
  anyone whose account no longer exists fails that lookup and is reported per row. Read the upload
  report; rows are not silently dropped, but they are easy not to notice.

#### FREP's PROD pull, reconciled (2026-09-08)

Ran clean and matched discovery exactly — **94 rows: 6 `FREP_ADMIN`, 43 `FREP_EDITOR`, 45 CHR across
18 districts**, covering **50 distinct people**. The five zero-holder districts (DFN, DMK, DQU, DSE,
DSS) are correctly absent. 94 out of a possible 94 means **no PROD grant has lapsed** — the expiry
filter dropped nothing.

`district`, `organization` and `region` came back empty on all 94 rows, district roles included. That
confirms what §3.1 suspected: FREP's roles are parentless and carry no forest client, so the
`COALESCE(parent.role_name, …)` and both `LEFT JOIN`s in the query above are inert for FREP. They do
no harm; there is simply nothing for them to resolve.

Four details that matter when the file is reviewed by hand:

- **`MSMACDON` holds a district role and no global role.** Correct by design — per-district CHR
  editors hold no global role and are authorised through `@auth` (see `RoleConstants`). The only such
  person of the 50. A reviewer tidying up "incomplete" rows would remove their entire access.
- **`LSWAN` holds two districts** (DCC, DND) as two rows, which is the shape the uploader wants.
- **PROD is 100% IDIR**, so the PROD upload is not blocked on Business BCeID approval. TEST is —
  it contains one Business BCeID account.
- **DEV (3 rows) and TEST (21 rows)** are admin/editor only, no CHR. Faster to grant by hand in CSS
  than to prepare and verify two more CSVs. `MOF_FAMT` appears in both and is a test account; it does
  not appear in PROD.

---

### 3.3 The audit pull — three pre-flight checks first

This one generates `INSERT` statements rather than data: you run it, copy the `stmt` column, and run
that against the new database. Everything here is a **read** against legacy FAM; nothing is written
until you run the generated statements somewhere else.

Do the checks below before generating. Each of the traps they catch produced rows that imported
cleanly and displayed wrong — none of them error.

**Check A — what shapes are actually in there?** Volume per environment, which permission types
exist, and whether `roles` is an array or null. This is the one that tells you whether the generator
even fits FREP's data:

```sql
SELECT a.application_name,
       l.privilege_change_type_code,
       l.privilege_details->>'permission_type' AS permission_type,
       jsonb_typeof(l.privilege_details->'roles') AS roles_type,
       COUNT(*) AS rows
FROM app_fam.fam_privilege_change_audit l
JOIN app_fam.fam_application a ON a.application_id = l.application_id
WHERE a.application_name LIKE 'FREP%'
GROUP BY 1, 2, 3, 4
ORDER BY 1, 2, 3;
```

What to look for:

- **`Application Admin`** rows carry `roles: null`. The generator's first `WHEN` branch rewrites them
  into the new vocabulary, `APP_ADMIN_6538_PROD`, so they render like native rows. FREP has 6 PROD
  admins, so this branch will fire.
- **`Delegated Admin`** rows *do* carry roles and pass through the display-name mapping unchanged —
  but the new system encodes delegation as `DELEGATED_ADMIN_<id>_<ENV>__<ROLE>`, and **this script
  will not produce that shape**. If any turn up, they need a decision before you generate.
- **`roles_type`** of anything other than `array` or `null` means a row shape the generator passes
  through untouched.

**Check B — which roles won't map?** The audit stores the role's `display_name`; everything else uses
`role_name`. Import a display name and the role column renders empty, because nothing matches it. Run
this and expect **zero rows**:

```sql
SELECT DISTINCT elem->>'role' AS unmapped_role
FROM app_fam.fam_privilege_change_audit l
JOIN app_fam.fam_application a ON a.application_id = l.application_id
CROSS JOIN LATERAL jsonb_array_elements(
    CASE WHEN jsonb_typeof(l.privilege_details->'roles') = 'array'
         THEN l.privilege_details->'roles' ELSE '[]'::jsonb END) AS elem
WHERE a.application_name = 'FREP_PROD'
  AND NOT EXISTS (SELECT 1 FROM app_fam.fam_role fr
                  WHERE fr.application_id = l.application_id
                    AND fr.display_name = elem->>'role');
```

**Check C — whose cached details are incomplete?** Names come off `fam_user`, not a directory lookup —
that is why the legacy UI still renders a full name for someone whose IDIR was deactivated years ago.
Anyone listed here will import with a blank name, and the accounts most likely to be blank are exactly
the deactivated ones no lookup could resolve either:

```sql
/* Anyone whose cached details are incomplete, before you generate anything. */
SELECT tu.user_name, tu.first_name, tu.last_name, tu.email
FROM app_fam.fam_privilege_change_audit l
JOIN app_fam.fam_application a ON a.application_id = l.application_id
JOIN app_fam.fam_user tu ON tu.user_id = l.change_target_user_id
WHERE a.application_name = 'FREP_PROD'
  AND (tu.first_name IS NULL OR tu.email IS NULL)
GROUP BY 1, 2, 3, 4 ORDER BY 1;
```

### 3.4 Generating the INSERTs

Parameters are set for FREP PROD: legacy app `FREP_PROD`, CSS integration **6538** (the suffix of
`forest-and-range-evaluation-program-6538`), and `css_app_name` as the app should read in the new UI.
No environment suffix is needed there — `css_environment` already carries it. Swap `legacy_app` for
`FREP_DEV` / `FREP_TEST` to generate the other environments.

```sql
WITH params AS (
  SELECT 'FREP_PROD'::varchar AS legacy_app,
         6538::int AS integration,
         'Forest and Range Evaluation Program'::varchar AS css_app_name
),
src AS (
  SELECT l.change_date, l.create_date, l.privilege_change_type_code,
    l.change_performer_user_details, p.integration, p.css_app_name,
    lower(a.app_environment) AS css_environment,
    CASE
      WHEN l.privilege_details->>'permission_type' = 'Application Admin'
        THEN jsonb_build_object('permission_type', 'End User', 'roles',
               jsonb_build_array(jsonb_build_object(
                 'role', 'APP_ADMIN_' || p.integration || '_' || upper(a.app_environment),
                 'scopes', NULL,
                 'role_assignment_expiry_date', NULL)))
      WHEN jsonb_typeof(l.privilege_details->'roles') <> 'array'
        THEN l.privilege_details
      ELSE jsonb_set(l.privilege_details, '{roles}', COALESCE((
        SELECT jsonb_agg(jsonb_set(elem, '{role}', to_jsonb(COALESCE((
          SELECT COALESCE(pr.role_name, fr.role_name)
          FROM app_fam.fam_role fr
          LEFT JOIN app_fam.fam_role pr ON pr.role_id = fr.parent_role_id
          WHERE fr.application_id = l.application_id AND fr.display_name = elem->>'role'
          LIMIT 1), elem->>'role'))) ORDER BY ord)
        FROM jsonb_array_elements(l.privilege_details->'roles')
             WITH ORDINALITY AS t(elem, ord)
      ), l.privilege_details->'roles'))
    END AS privilege_details,
    CASE WHEN pu.user_guid IS NULL THEN 'system'
         ELSE (CASE pu.user_type_code WHEN 'I' THEN 'IDIR' ELSE 'BCEID_BUS' END)
              || '\' || upper(pu.user_guid) END AS performer_key,
    (CASE tu.user_type_code WHEN 'I' THEN 'IDIR' ELSE 'BCEID_BUS' END)
         || '\' || upper(tu.user_guid) AS target_key,
    jsonb_strip_nulls(jsonb_build_object(
        'user_guid',  upper(tu.user_guid),
        'username',   tu.user_name,
        'first_name', tu.first_name,
        'last_name',  tu.last_name,
        'email',      tu.email)) AS target_details
  FROM params p
  CROSS JOIN app_fam.fam_privilege_change_audit l
  JOIN app_fam.fam_application a ON a.application_id = l.application_id
  LEFT JOIN app_fam.fam_user pu ON pu.user_id = l.change_performer_user_id
  JOIN app_fam.fam_user tu ON tu.user_id = l.change_target_user_id
  WHERE a.application_name = p.legacy_app
)
SELECT format('INSERT INTO app_fam.fam_privilege_change_audit (css_integration_id, css_environment, change_date, change_performer_user_details, change_target_user_details, performer_user, target_user, privilege_change_type_code, privilege_details, create_user, create_date, update_user, update_date, css_application_name) VALUES (%s, %L, %L, %L::jsonb, %L::jsonb, %L, %L, %L, %L::jsonb, %L, %L, %L, %L, %L)',
    integration, css_environment, change_date,
    change_performer_user_details::text, target_details::text,
    performer_key, target_key, privilege_change_type_code,
    privilege_details::text,
    performer_key, create_date, performer_key, create_date,
    css_app_name) || chr(59) AS stmt
FROM src
ORDER BY change_date;
```

#### What FREP's generated statements looked like (2026-09-09)

| | dev | test | prod |
|---|---|---|---|
| statements | 10 | 57 | **100** |
| GRANT / REVOKE | 8 / 2 | 41 / 16 | 100 / 0 |
| `APP_ADMIN_6538_<ENV>` | 3 | 4 | 6 |
| unmapped role names | **2** | **2** | 0 |

**PROD cross-checks exactly against the access pull.** 6 `APP_ADMIN_6538_PROD` appointments + 94
`FREP_*` role grants = 100 statements, and those 94 are row-for-row the same 94 in the §3.2 extract —
43 `FREP_EDITOR`, 6 `FREP_ADMIN`, 45 district grants across 18 districts. Two independently-derived
datasets agreeing to the row is the strongest signal available that the generator is correct.

Everything else on PROD is clean: all `End User`, integration `6538`, environment `prod`, every GUID
upper-cased, every key `IDIR\<GUID>`, no duplicates, exactly one role per statement, all terminated
with `);` (so the `|| chr(59)` worked), and **no missing names or emails**.

**The one finding: `Decision Maker` appears twice in DEV and twice in TEST.** It is not a FREP role —
it is a `display_name` with no matching `fam_role` row, so the generator's
`COALESCE(…, elem->>'role')` fallback passed the raw display name through. All four rows date from
11 June 2026, minutes apart; the role has since been removed from `fam_role`. **This is precisely what
pre-flight check B exists to surface** — running the generator first means the fallback swallows it
silently rather than the check reporting it.

PROD is unaffected, and the DEV/TEST audit history is of little value, so the cheapest resolution is
simply not to import DEV/TEST audit at all. If you do import them, either accept four rows whose role
column matches nothing, or filter them out first.

`MOF_FAMT` (a service account, no email) is the only row check C would have flagged — DEV and TEST
only, and it imports with a blank email column.

### 3.5 Verify by reading the data back

Import into a throwaway environment first and **export the resulting table to CSV**, then compare rows
the application wrote against rows the script wrote. Reading them side by side is what surfaces a
duplicated run, rows landing under the wrong integration id, and blank name columns.

Keep this to hand before every import, scoped to exactly what you inserted, so a bad run costs a
minute rather than a restore:

```sql
DELETE FROM app_fam.fam_privilege_change_audit
WHERE css_integration_id = 6538
  AND css_environment = 'prod'
  AND create_user <> 'system';
```

The `create_user <> 'system'` guard matters: rows the legacy application itself wrote carry
`system` as the performer, and those are not yours to delete.


## 4. The evaluator directory lookup

`FamUserDirectoryService` (the Administration → "Add evaluator" search, FREP301) calls FAM's
`/external/v1/users` and passes **the caller's access token straight through**. That token is now
minted by the standard realm rather than by Cognito, so whether legacy FAM still accepts it has to be
confirmed against a real environment.

The documented replacement is `nr-user-lookup-api`, which authenticates with FREP's **own**
client-credentials service account rather than the caller's token — a different integration, needing
a service-account client from CSS. Script its creation idempotently and gate the CI step on the admin
credential being present, so PR previews skip it and degrade gracefully.

Until then the service behaves as it always has when misconfigured: an empty evaluator search rather
than a broken page.

---

## 5. Suggested order

1. **CSS integration for DEV.** External lead time, and everything else is unverifiable without it.
2. Backend resource server + claim mapping — done, with tests (`JwtPrincipalUtilTest`).
3. Frontend auth service + `/authCallback` — done.
4. Cognito surface deleted: config, logout chain, userinfo service, env vars, CSP entries — done.
5. Session-timeout constants recomputed against the realm's real TTLs — done at 25 min idle / 20 min
   warning, assuming a 30-minute refresh token. **Re-check that assumption against the actual realm
   settings**; if the realm gives something other than 30 minutes, `SessionTimeout/index.tsx` shows
   the arithmetic to redo.
6. CI/CD variables (`KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID` — one GitHub **variable** each, not
   secrets, feeding both deploy templates) and the service-account script.
7. e2e sessionStorage handling — done (`e2e/fixtures.ts`).
8. Local dev config.
9. **Migrate the data (§3) once a real environment works end to end.**

---

## 6. What is left as an open question

- ~~Whether CSS accepts wildcard redirect URIs~~ — **answered: `*` only, in Dev and Test; not in
  Prod, and no host patterns.** DEV stays at `*` by decision, which makes the 50-slot PR scheme
  deletable (§1).
- When Business BCeID clears approval on each integration (§0).
- Whether legacy FAM's `/external/v1/users` accepts a standard-realm token, or whether the
  `nr-user-lookup-api` service account is needed now rather than later (§4).
- Whether programmatic CI login is possible at all: IDIR - MFA requires a second factor a script
  cannot supply, so unattended e2e needs an MFA-exempt service account. Find this out early — it
  decides the shape of the whole e2e strategy.
- The realm's actual refresh-token TTL, which the session-timeout constants are derived from (§5).
