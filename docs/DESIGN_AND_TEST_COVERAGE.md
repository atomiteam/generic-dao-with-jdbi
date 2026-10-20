# Design and test coverage

## Responsibilities

| Component | Responsibility |
| --- | --- |
| GenericDao | Public CRUD orchestration, immutable scope and borrowed handle selection |
| DaoPredicates | One validated predicate/binding snapshot for reads, counts, updates and deletes |
| EntityCodec | Pluggable entity mapping; consumers do not need a persistence base class |
| EntityModel | Cached immutable persistent-field metadata, inheritance and construction |
| ReflectionEntityCodec | Portable typed conversion, aliases and JSON serialization |
| PostgresEntityCodec | Native PostgreSQL/DSQL JSON argument policy |
| StatementBindings | Shared scalar/collection binding for CRUD and dynamic queries |
| JdbiQuery / SqlFragments | Trusted query composition, validation and parameter snapshots |
| JdbiQueryExecutor | Mapping, counts, offset/keyset results and compiled query execution |
| DsqlTransactions | Transaction lifecycle, bounded whole-unit retries and error policy |
| DsqlSupport | Idempotent JDBC JSON configuration, independent of IAM credential acquisition |

The public DAO, query, filtering and codec APIs remain compatible. Internal helpers
are implementation details. New entities use ordinary POJOs or EntityCodec;
PostgreSQL JSON behavior extends one encoding hook instead of duplicating field scans.
BaseEntity.toChanges delegates to the same codec. No additional runtime dependency or
mandatory inheritance is introduced.

Models must not shadow an inherited persistent field with the same Java property
name; ambiguous mappings now fail explicitly. Static, Java-transient, synthetic and
@Transient fields are excluded consistently.

## Operation and integration matrix

| Supported behavior | Behavioral coverage |
| --- | --- |
| Insert, supplied/generated IDs, String/Long IDs, POJOs, optional legacy bases | GenericDaoTest, AtomiCompatibilityTest, SnakeCaseLongIdTest, CustomCodecTest |
| Get/list/filter/count, all 12 operators, literal keys and legacy comparison prefixes | DaoOperationsTest, GenericDaoTest, SnakeCaseLongIdTest |
| Entity update, patch/SQL NULL, optimistic complete replacement, no-op update, all delete variants | DaoOperationsTest, ScopedDaoTest, TypedProjectionTest |
| Tenant/composite scope, OR containment, immutable scope, stale versions, rollback | ScopedDaoTest, DaoOperationsTest, TypedProjectionTest |
| Identity/snake_case/quoted naming, column aliases, qualified tables and injection rejection | CodecContractTest, QuotedColumnsTest, SchemaQualifiedTableTest |
| Inherited fields, setters, generic JSON, ISO/JDBC dates, UUIDs, numeric types and enums | CodecContractTest, PropertyUtilsTest, PostgresCodecTest |
| Custom mapping without inheriting ReflectionEntityCodec | CustomCodecTest |
| Query comparisons and optional variants, collections, predicates, allowlisted sorting | QueryOperationsTest, QueryValidationTest, JdbiQueryTest |
| Map/function/POJO/scalar projections, first/empty, count, offset pages and snapshots | QueryOperationsTest, CompiledQueryTest |
| Keyset duplicates/nulls/directions, continuation, validation and borrowed handles | SeekPaginationTest, QueryValidationTest |
| Nested/base WHERE and OR, quoted text/comments, rejected-builder reuse | QueryOperationsTest, QueryValidationTest, QueryRegressionTest |
| Specialized JDBC resource ownership, rollback/suppression and typed/null binding | SqlStatementsTest, SqlStatementsFailureTest |
| Serialization/commit retry, explicit policy, attempt limits, nonretryable failures and interruption | DsqlTransactionsTest, TransactionFailureTest |
| DSQL native JSON binder, idempotent configuration, no savepoints/isolation setters | DsqlIntegrationTest, PostgresCodecTest |
| Real PostgreSQL JSONB, folded/quoted columns, JSON lists, scopes and transactions | DatabaseContractTest, TypedPostgresContractTest |
| Real MySQL generated IDs, snake_case, JSON, offset compatibility and mutations | MysqlContractTest, MysqlJsonContractTest |

The default Java 11 suite uses H2 and JDBC mocks, with no live database credentials.
Native PostgreSQL 16 and MySQL 8.4 contracts run in CI on Java 17. Environment-specific
tests are explicitly skipped when their URL is absent. These skips are not test passes.

A live Aurora DSQL cluster, IAM token renewal, real OCC contention and service limits
are not validated by H2/mocks or PostgreSQL CI. The opt-in dsql-contract profile remains
available for a disposable DSQL cluster using the application's AWS credential chain.
No live DSQL validation is claimed.

## Regression gates

- mvn verify runs tests, JaCoCo reports and a coverage gate: at least 90% lines and 80% branches.
- CI uploads HTML/XML coverage and Surefire results as unit-test-coverage.
- bash scripts/format-java.sh --check enforces pinned google-java-format AOSP style
  for every production/test Java file; --write applies it on Java 17+.
- Existing Java 11 compilation remains the minimum-library-runtime compatibility gate.
- Backend integration must rerun its full suite whenever the immutable DAO pin changes.

Coverage percentages measure exercised code, not proof of correctness. The matrix
records observable behavior and the native database checks separately.

## Correctness improvements covered by regression tests

- Scope predicates stay outside user-selected OR groups in CRUD.
- Added query filters also contain an existing base WHERE/OR predicate; nested WHERE
  keywords, quoted literals and comments do not redirect filter insertion.
- Invalid predicate/expression additions do not leave orphan query parameters.
- Collection filters encode each element through the codec before list binding.
- JSON trees preserve exact numeric tokens and explicit null members.
- Page results and collection bindings use defensive snapshots.
- Commit failure relies on JDBI rollback ownership; callback failures preserve their
  original exception if rollback also fails.
