# generic-dao-with-jdbi

Generic CRUD and query access for JDBC databases using JDBI.

## Version 2.0 highlights

Version 2.0 adds support for:

- Plain Java POJOs; extending a persistence base class is optional.
- String and numeric primary keys, including `Long` / `BIGINT` IDs.
- Database-generated `id` values, assigned back to the inserted object.
- Configurable Java-property to database-column naming.
- `ColumnNaming.SNAKE_CASE`, for example `sampleColumn` -> `sample_column`.
- `@Column` for explicit database column overrides.
- Java-property based filtering and sorting when snake-case mapping is enabled.
- Map-based `get` and `list` convenience methods.
- Numeric and enum conversion during row mapping.
- A reusable JDBI-native query layer for joins, projections, searches and reports.

## Basic POJO example

```java
public class Account {
    private Long id;
    private String displayName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
```

```java
public class AccountDao extends GenericDao<Account> {
    public AccountDao(Jdbi jdbi) {
        super(jdbi, Account.class, "account", ColumnNaming.SNAKE_CASE);
    }
}
```

With snake-case naming enabled, `displayName` maps to `display_name`.

The existing `Entity`, `LongEntity`, and `BaseEntity<ID>` classes remain available as convenience base classes, but using them is optional.

## Generated Long IDs

If the POJO contains an `id` property and its value is `null`, `insert` omits the id from the INSERT, reads the generated database key, and assigns it back to the same object.

```java
Account account = new Account();
account.setDisplayName("Example");

dao.insert(account);
Long generatedId = account.getId();
```

`insert` also returns the same entity instance, so callers that want the persisted object can use the return value while existing callers may simply ignore it.

If the ID is already populated, it is inserted normally.

## Filtering

Filtering and sorting use Java property names even when the database uses snake case.

```java
List<Account> accounts = dao.filter(
    Filtering.create()
        .eq("displayName", "Example")
        .withSorting("displayName", Sorting.ASC)
);
```

Use `GenericDao` + `Filtering` for normal single-table entity access.

## Custom joins and projections

Use `JdbiQuery` + `JdbiQueryExecutor` when the query is a join, projection, search or report instead of creating another application-specific SQL builder.

```java
JdbiQuery query = JdbiQuery.select(
        "select a.id, a.display_name from account a join tenant t on t.id = a.tenant_id")
    .eqIfPresent("t.id", tenantId)
    .likeIfPresent("a.display_name", namePattern)
    .orderBy("a.id", Sorting.DESC)
    .page(offset, limit);

JdbiQueryExecutor executor = new JdbiQueryExecutor(jdbi);
QueryPage<Account> page = new QueryPage<>(
    executor.pojos(query, Account.class, ColumnNaming.SNAKE_CASE),
    executor.count(query));
```

The query layer provides:

- unique named bindings for dynamic values;
- `IN` / `NOT IN` collection expansion through JDBI;
- `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `like`, null checks and optional predicates;
- named bindings for stable SQL resources with `:name` placeholders;
- pagination without application-specific `LIMIT/OFFSET` concatenation;
- exact total counts generated from the same query specification;
- scalar, map, POJO and custom-function mapping;
- standard POJO mapping through the same `ReflectionEntityCodec` used by `GenericDao`;
- client sorting through an explicit allow-list using `orderByAllowed`.

For a nested/domain projection, keep only the domain mapping in the application:

```java
List<OrderSummary> items = executor.list(query, row ->
    new OrderSummary(
        ((Number) row.get("order_id")).longValue(),
        (String) row.get("customer_name")));
```

Do not concatenate request values into `where`, `order by`, `limit`, or `offset`. For request-controlled sorting, map public sort keys to trusted SQL expressions:

```java
Map<String, String> allowedSorts = new LinkedHashMap<>();
allowedSorts.put("id", "a.id");
allowedSorts.put("name", "a.display_name");

query.orderByAllowed(requestedSort, allowedSorts, "id", Sorting.DESC);
```

## Map compatibility helpers

```java
Map<String, Object> values = new HashMap<>();
values.put("displayName", "Example");

Optional<Account> account = dao.get(values);
List<Account> accounts = dao.list(values);
```

Map keys are Java property names and are resolved through the configured column naming strategy.

## Updates and deletes

`update` retains the affected-row-count behavior. `updateAndReturn` is available for entity-oriented flows.

```java
int changed = dao.update(account);
Account sameAccount = dao.updateAndReturn(account);
```

For an explicit patch, including writing a SQL NULL, use the map update API:

```java
Map<String, Object> changes = new HashMap<>();
changes.put("displayName", null);
dao.update(account.getId(), changes);
```

`delete` accepts either an ID value or an entity instance with a populated `id` property.

```java
dao.delete(account.getId());
dao.delete(account);
```

## Spring / DataSource integration

The library itself does not require Spring. In Spring applications, create one JDBI instance from the application's existing `DataSource` and optionally expose one query executor:

```java
@Bean
public Jdbi jdbi(DataSource dataSource) {
    return Jdbi.create(dataSource);
}

@Bean
public JdbiQueryExecutor jdbiQueryExecutor(Jdbi jdbi) {
    return new JdbiQueryExecutor(jdbi);
}
```

This allows both `GenericDao` and the custom query layer to use the application's existing connection pool and database configuration.

## 2.1: scoped CRUD, shared transactions, PostgreSQL and Aurora DSQL

The core remains Java 11 compatible. The optional AWS Aurora DSQL JDBC connector requires
Java 17 or newer. See [the DSQL integration guide](docs/aurora-dsql.md) for authentication,
compatibility limits, and the opt-in real-database contract test.

### Mandatory scope and composite keys

```java
GenericDao<Activity> activities = DsqlSupport.dao(jdbi, Activity.class, "business.activities")
    .scoped(Map.of("tenantId", tenantId, "workspaceId", workspaceId));

Optional<Activity> current = activities.getById(activityId);
int changed = activities.updateByKey(
    Map.of("id", activityId, "version", expectedVersion),
    Map.of("title", "Updated title", "version", expectedVersion + 1));
// changed == 0 means the scoped record/key version did not match.
activities.deleteByKey(Map.of("id", activityId, "version", expectedVersion + 1));
```

Scope is immutable and is always ANDed outside caller filters, including OR groups.
Inserts inject missing scope values and reject conflicting values. Updates cannot change
scope fields. A second `scoped` call can add scope but cannot replace it. Empty/null keys
and null scope values are rejected. Existing unscoped methods remain available for
backward compatibility; the application must obtain scope from verified authorization,
not request-controlled tenant IDs. Explicit `Filtering.equalTo` and composite key methods
use literal equality; legacy `Filtering.eq` retains its historical string-prefix behavior.

### One transaction across DAOs and queries

```java
DsqlTransactions.run(jdbi, 4, handle -> {
    GenericDao<Activity> txActivities = activities.using(handle);
    int changed = txActivities.updateByKey(
        Map.of("id", activityId, "version", expectedVersion),
        Map.of("title", "Updated title", "version", expectedVersion + 1));
    if (changed != 1) throw new IllegalStateException("Record changed; reload before saving");
    outbox.using(handle).insert(event);
    return txActivities.getById(activityId);
});
```

`GenericDao.using(handle)` and `JdbiQueryExecutor.using(handle)` borrow the supplied
Handle without closing, committing or rolling it back. Use them only within the
Handle's lifetime and thread. `DsqlTransactions` retries the complete callback on
SQLSTATE `40001`, including conflicts detected at commit, with bounded jittered backoff
and a fresh Handle. It does not retry permission errors, unique violations or ambiguous
connection failures. Keep callbacks replayable: use stable operation IDs/outbox records;
never send email, write S3, or perform other external effects inside them. No savepoints
or isolation-level changes are introduced.

### Keyset pagination

```java
List<SeekColumn> order = List.of(
    new SeekColumn("a.createdAt", Sorting.DESC, SeekColumn.Nulls.LAST),
    new SeekColumn("a.id", Sorting.DESC, SeekColumn.Nulls.LAST));
JdbiQuery query = JdbiQuery.select("SELECT a.* FROM business.activities a")
    .eq("a.tenantId", tenantId)
    .eq("a.workspaceId", workspaceId)
    .seekAfter(order, previousValues); // null for the first page

ReflectionEntityCodec<Activity> codec = new ReflectionEntityCodec<>(Activity.class);
SeekPage<Activity> page = new JdbiQueryExecutor(jdbi).seekPage(query, 20,
    codec::fromStorage, item -> Arrays.asList(item.getCreatedAt(), item.getId()));
List<?> nextValues = page.getNextValues(); // null at the end
```

The executor fetches `pageSize + 1` rows, without a COUNT query or mutating the caller's
query. Ordering supports multiple columns, mixed directions and explicit null placement.
End the ordering with a unique stable tie-breaker. Ordering expressions must come from
trusted application metadata. The application must serialize/sign continuation values,
bind them to scope/filter/order/principal, validate types, and enforce authorization.
These values are not an authenticated cursor token. Do not mix seek order with offsets.
Concurrent edits do not provide a frozen snapshot across separate page requests.

### Column names and camelCase

Keep Java fields and SQL source names such as `displayName` unquoted. PostgreSQL/DSQL
folds that SQL identifier to physical `displayname`; row mapping now resolves those
folded labels case-insensitively while preserving Java `displayName`. SQL generation
does not introduce quoted names by default. To preserve an existing quoted column,
a custom EntityCodec may return a quoted identifier such as `"displayName"` from
`columnName`. Qualified table names can likewise use quoted components. Each component
must contain only a valid identifier; expressions and embedded quotes are rejected.
Existing quoted mixed-case columns need an explicit migration before switching to
unquoted SQL; deleting quote characters does not rename stored columns.

Use `DsqlSupport.dao(...)` for `@Json` fields: JSON is encoded once and bound using
JDBC `Types.OTHER` so PostgreSQL/DSQL JSON/JSONB columns do not receive VARCHAR values.
The default `GenericDao` codec remains portable and unchanged for existing users.
JDBC timestamps/dates and UUID strings map to matching Java temporal/UUID fields.

### Typed projections and existing quoted columns

Use `DsqlSupport.dao(db, Row.class, table, ColumnNaming.QUOTED_IDENTITY)`
for existing quoted camelCase schemas; no custom column codec is required.
The default unquoted naming remains unchanged. Row labels are resolved without SQL
quotes, case-insensitively, and ambiguous labels are rejected.

`PostgresEntityCodec.fromProperties(values)` maps validated Java property values
to an ordinary POJO, including ISO date/time strings and generic `@Json` fields.
Unknown properties are rejected. Use `@Json JsonElement` when JSON numeric tokens
must survive a read/write without conversion to floating point.

`dao.replaceByKey(keys, entity)` replaces all mapped non-id properties including
nulls, with mandatory scope and optimistic keys. Leave database-managed columns
out of the POJO; unlike a patch, omitted POJO values are explicitly cleared.
JSON encoding occurs once. A borrowed handle stays owned by its caller.

`SqlStatements` executes trusted specialized SQL on borrowed JDBC connections,
without completing or closing their transactions. Its DataSource list method owns
only its acquired read connection. Entity CRUD belongs in GenericDao.

Applications with idempotent replay can explicitly supply additional retry states
and an error factory to `DsqlTransactions.run`. The default remains 40001 only.
Commit failures rely on JDBI's rollback rather than issuing a duplicate rollback.

### Design, operation coverage and quality gates

See [design and test coverage](docs/DESIGN_AND_TEST_COVERAGE.md) for the public-operation
matrix, internal responsibilities and native integration limits. Run `mvn verify`
for unit tests and the JaCoCo gate (90% line / 80% branch coverage).
CI publishes HTML/XML coverage and test results. Java 11 remains the library target;
formatting uses Java 17+ with `bash scripts/format-java.sh --check` or `--write`.
PostgreSQL and MySQL contracts run separately; DSQL driver-level tests are not a live
AWS/IAM validation.

## Explicit database engine configuration

Select the engine explicitly in application configuration; the library does not
read environment variables, open IAM sessions or manage credentials implicitly.

```java
DatabaseEngine engine = DatabaseEngine.parse(configuration.getDatabaseEngine());
// Supported values: dsql, postgresql, mysql (case-insensitive).
Jdbi database = engine.create(dataSource);
GenericDao<Activity> activities =
        engine.dao(database, Activity.class, "activities")
                .scoped(Map.of("tenantId", tenantId));
activities.insert(activity);
Optional<Activity> stored = activities.getByKey(Map.of("id", activity.id));
JdbiQuery query = engine.query("SELECT * FROM activities")
        .eq("tenantId", tenantId)
        .seekAfter(List.of(new SeekColumn("id", Sorting.ASC, SeekColumn.Nulls.LAST)), null);
```

| Engine | Default POJO column naming | JSON binding | JDBC configuration supplied by caller |
| --- | --- | --- | --- |
| DSQL | Quoted camelCase | Native PostgreSQL JSON | Aurora DSQL connector URL, region/IAM identity and TLS |
| POSTGRESQL | Quoted camelCase | Native PostgreSQL JSON | PostgreSQL JDBC URL, authentication and TLS |
| MYSQL | Backtick-quoted camelCase | Portable JSON strings | MySQL JDBC URL, authentication and TLS |

Existing unquoted or snake_case schemas can pass `ColumnNaming.IDENTITY` or
`ColumnNaming.SNAKE_CASE` explicitly. Engine-incompatible quote strategies are
rejected. Existing GenericDao/DsqlSupport constructors retain their behavior.
MySQL keyset queries use boolean null ordering instead of unsupported
`NULLS FIRST/LAST`; the engine is preserved by query copies.
Use `engine.query(...)` for this behavior; legacy `JdbiQuery.select(sql)` keeps its
PostgreSQL/DSQL dialect for compatibility. This is not a translator for arbitrary
vendor-specific SQL supplied in projections, predicates or annotations.

Native CI tests exercise PostgreSQL 16 and MySQL 8.4. H2 and JDBC mock tests cover
DSQL binding, scopes, rollback/retries and no-savepoint/no-isolation-setter rules.
An actual Aurora DSQL test is separate: provide the approved connector/driver,
`DAO_JDBC_URL` and IAM configuration, then run `mvn -Pdsql-contract verify`.
No live DSQL cluster is exercised by the ordinary unit or PostgreSQL/MySQL jobs.
