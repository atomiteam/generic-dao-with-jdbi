# generic-dao-with-jdbi

Generic CRUD access for JDBC databases using JDBI.

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

## Basic POJO example

```java
public class Account {
    private Long id;
    private String displayName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
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

`delete` accepts either an ID value or an entity instance with a populated `id` property.

```java
dao.delete(account.getId());
dao.delete(account);
```

## Spring / DataSource integration

The library itself does not require Spring. In Spring applications, create one JDBI instance from the application's existing `DataSource` and inject it into DAOs:

```java
@Bean
public Jdbi jdbi(DataSource dataSource) {
    return Jdbi.create(dataSource);
}
```

This allows JDBI to use the application's existing connection pool and database configuration.
