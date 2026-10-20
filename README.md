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
