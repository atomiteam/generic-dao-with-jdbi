# Generic DAO regression check — 20 October 2026

## Revisions and scope

- Baseline: main `849c5ee2df3fc9aa8b080d033c8e91ab7c2558c2` (2.0.1).
- Candidate before fixes: PR #2, `b18a78ebfdd2831d98edbbf61c47cc34d23e62b9`.
- Corrected candidate: this revision of PR #2, still 2.1.0-SNAPSHOT.
- No release, deployment, schema migration, or production data mutation performed.

The check covers the existing CRUD/POJO/snake-case/Long-ID contracts and the new scoped
CRUD, composite/version keys, borrowed Handles, JSON binding, keyset pagination, and
DSQL retry helpers. It is not a binary compatibility certification against version 1.11
or a live Aurora DSQL service certification.

## Findings reproduced and fixed

| Finding | Classification | Reproduction | Fix |
|---|---|---|---|
| Offset without limit renders bare OFFSET | Regression introduced by the DSQL changes; MySQL requires LIMIT | The new offset-only regression test failed on the pre-fix candidate because generated SQL omitted LIMIT | Both DAO and query builder now use signed bigint maximum `9223372036854775807` as the implicit limit. It fits PostgreSQL and is accepted by MySQL; ordinary explicit-limit paging is preserved. Query-builder values remain bound parameters. |
| An OR fragment can escape preceding predicates | Inherited defect, reproduced independently on baseline and pre-fix candidate | `tenantId = 'a' AND name = 'Alpha' OR name = 'Beta'` returned both tenant rows, expected one | Individually parenthesize generated predicate fragments before joining with AND. List and count use the same grouping. Developer-authored base SQL remains trusted application code; it is not an authorization parser. |

Tests failed before the changes and pass afterward. The OR probe was also run separately
against baseline and failed there, distinguishing an inherited defect from a newly
introduced regression.

## Local execution evidence

| Run | Result |
|---|---|
| Original baseline suite, unchanged | 37 tests passed |
| Two new regression probes against the pre-fix candidate | 2 failed, as expected: missing LIMIT and OR containment |
| OR probe against baseline, separate from the original suite | 1 failed, confirming the inherited defect |
| Complete corrected candidate suite | 49 passed, 0 failed/errors; 2 environment-gated database contracts skipped |

Local runtime: OpenJDK 17.0.20, Maven 3.9.11, compiler release 11. Mockito needed its
Byte Buddy agent supplied explicitly because dynamic attachment is restricted here.
Commands used the Maven settings for the current execution proxy, then offline mode
once dependencies were available:

```bash
mvn -DargLine=-javaagent:/PATH/byte-buddy-agent-1.15.4.jar test
```

No skipped real-database test is counted as a pass. H2 tests run both MySQL and
PostgreSQL compatibility modes, but do not replace the real database contracts.

## Coverage retained and expanded

- Existing CRUD, generated numeric IDs, ordinary POJOs, explicit column mappings,
  snake_case properties, JSON encoding, enum/numeric conversion and schema-qualified tables.
- Tenant/workspace scope applied to reads, counts, updates/deletes and OR groups;
  inserts cannot override scope, and scope fields cannot be reassigned.
- Composite keys without a synthetic ID, literal IDs, stale version writes and empty-key rejection.
- Borrowed Handle ownership and rollback across DAOs/query executors.
- Folded camelCase labels, PostgreSQL JSON/SQL NULL binding, temporal/UUID mapping,
  and JSON binder registration on previously opened Handles.
- Ascending/descending keyset ordering with duplicate values, null placement, unique
  tie-breakers and no mutation of the original query during page execution.
- Whole-transaction retry on callback and commit SQLSTATE 40001; non-conflict errors
  are not retried and retries remain bounded.
- New regression coverage for OR containment and offset-only paging in both SQL families.

## CI and environment validation

The pre-fix candidate's standard build and PostgreSQL 16 contract already passed; neither
had the two new probes. This revision adds those probes and a MySQL 8.4 service job.
The PostgreSQL contract now exercises offset-only execution too. Check PR #2's current
commit checks for the updated pipeline outcomes rather than attributing older runs to
new code.

The MySQL contract exercises scoped CRUD, folded/mapped column labels, OR containment,
and offset-only DAO/query execution. The PostgreSQL contract additionally verifies JSONB,
rollback, optimistic keys, and keyset reads. See `docs/aurora-dsql.md` for the real DSQL
profile. No live DSQL endpoint, IAM renewal, or actual distributed OCC contention was
available in this check; those remain environment-level validation requirements.
