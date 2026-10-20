# PostgreSQL and Aurora DSQL integration

The DAO operates through JDBI/JDBC, not AWS database-specific SQL APIs. It emits unquoted
identifiers, standard parameterized CRUD, and PostgreSQL-compatible OFFSET/keyset SQL.
The DSQL helpers add JSON binding and bounded whole-transaction retries. The application
still owns tenant/workspace authorization, optimistic versions, quotas, idempotency,
transactional outbox effects, and any schema/data migration.

## Connection and IAM

Add the AWS connector to the application (not as a mandatory dependency for every DAO
consumer), using a Java 17+ runtime:

```xml
<dependency>
  <groupId>software.amazon.dsql</groupId>
  <artifactId>aurora-dsql-jdbc-connector</artifactId>
  <version>1.0.0</version>
</dependency>
```

Configure the application's DataSource/pool with the AWS JDBC connector URL:

```text
jdbc:aws-dsql:postgresql://YOUR_CLUSTER.dsql.YOUR_REGION.on.aws/postgres?user=admin
```

Use an appropriately restricted database role in production. The connector uses the
AWS credential provider chain and obtains IAM tokens when connections are established.
Do not hardcode access keys or cache a one-off authentication token as a permanent pool
password. Configure TLS and pool/session lifetimes according to AWS guidance. Then:

```java
Jdbi jdbi = DsqlSupport.create(dataSource);
GenericDao<Contact> contacts = DsqlSupport.dao(jdbi, Contact.class, "business.contacts")
    .scoped(Map.of("tenantId", verifiedTenantId));
```

Plain `DsqlSupport.configure(Jdbi.create(url))` is also supported. No schema creation,
IAM permission changes, data copying, or DDL is performed by these helpers.

## Compatibility contract

- Use `using(handle)` for all DAOs/executors participating in one transaction.
- `DsqlTransactions.run` retries SQLSTATE `40001` from the callback or commit. It retries
  the entire operation, not individual statements, and avoids isolation setters and
  savepoints. Keep transactions short and within current DSQL service limits.
- Client-assigned string/UUID IDs are recommended. Database-generated key retrieval is
  retained for compatible schemas/drivers; do not assume an existing MySQL identity
  definition is valid DSQL DDL.
- Use the DSQL codec for `@Json` binding. It does not cast arbitrary JSON text or generate
  unsupported JSON search/index expressions automatically.
- Define column names unquoted in new SQL/schema source. PostgreSQL physically folds
  them to lowercase; the codec maps folded labels back to camelCase Java properties.
  Existing quoted identifiers require a reviewed schema migration. This change does
  not rewrite VolaCRM's draft schema or migrate any existing database.
- DDL and DML belong in separate transactions. Create asynchronous indexes using the
  documented DSQL process, not a generic PostgreSQL schema runner.
- Keyset continuation values require application-side signing, query/scope binding,
  and type validation. ORM/DAO filtering is not a substitute for authorization.
- Keep unknown commit outcomes out of automatic retry. A non-40001 connection failure
  may require reconciliation using an application idempotency key.

## Tests

The default suite uses H2 PostgreSQL mode plus JDBC mocks. It tests scope isolation,
OR-group containment, composite/version keys, rollback across borrowed Handles,
case-folded mapping, JSON binding, keyset null/duplicate ordering and callback/commit
conflict retries. These tests do not prove a live DSQL service behaves identically.

`.github/workflows/database-contract.yml` runs the same real-database contract against
PostgreSQL 16 in CI. Run it locally against a disposable PostgreSQL database:

```bash
DAO_JDBC_URL='jdbc:postgresql://localhost:5432/contract' \
DAO_JDBC_USER=postgres DAO_JDBC_PASSWORD=YOUR_TEST_PASSWORD \
mvn -Pdatabase-contract verify
```

For an actual disposable DSQL database, configure AWS credentials and connect permissions,
then run on Java 17+:

```bash
DAO_JDBC_URL='jdbc:aws-dsql:postgresql://YOUR_CLUSTER.dsql.YOUR_REGION.on.aws/postgres?user=YOUR_TEST_ROLE' \
mvn -Pdsql-contract verify
```

The contract creates one randomly named table, exercises scoped CRUD, JSONB, optimistic
keys, rollback, and keyset reads, and drops that table in cleanup. The test role needs
CREATE/DROP TABLE permission in its test schema. Never point this at a production database.
Without `DAO_JDBC_URL`, the real-database contract is explicitly skipped. A passing local
suite or PostgreSQL CI result must not be reported as live DSQL verification. Real OCC
contention, IAM token renewal and service quotas require an additional environment-level
DSQL rehearsal with the consuming application.

## Primary references

- [AWS JDBC connector and IAM setup](https://docs.aws.amazon.com/aurora-dsql/latest/userguide/SECTION_program-with-jdbc-connector.html)
- [DSQL concurrency and SQLSTATE 40001](https://docs.aws.amazon.com/aurora-dsql/latest/userguide/working-with-concurrency-control.html)
- [Supported SQL](https://docs.aws.amazon.com/aurora-dsql/latest/userguide/working-with-postgresql-compatibility-supported-sql-features.html)
- [DSQL DDL transaction rules](https://docs.aws.amazon.com/aurora-dsql/latest/userguide/working-with-ddl.html)
- [JDBI 3.49.6 guide](https://jdbi.org/releases/3.49.6/)
