package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.*;

class ScopedDaoTest {
    public static class Link {
        public String tenantId, contactId, tagId, label;

        public Link() {}
    }

    @Test
    void compositeOnlyEntitiesDoNotRequireASyntheticId() {
        Jdbi db = db();
        db.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE links(tenantId varchar,contactId varchar,tagId varchar,label varchar,PRIMARY KEY(tenantId,contactId,tagId))"));
        GenericDao<Link> links =
                new GenericDao<>(db, Link.class, "links").scoped(Map.of("tenantId", "a"));
        Link link = new Link();
        link.contactId = "person";
        link.tagId = "tag";
        link.label = "First";
        links.insert(link);
        Map<String, Object> key = Map.of("contactId", "person", "tagId", "tag");
        assertEquals("First", links.getByKey(key).orElseThrow().label);
        assertEquals(1, links.updateByKey(key, Map.of("label", "Updated")));
        assertEquals("Updated", links.getByKey(key).orElseThrow().label);
        assertEquals(1, links.deleteByKey(key));
    }

    public static class Item {
        public String id, tenantId, workspaceId, displayName;
        public Long version;

        public Item() {}

        Item(String id, String tenant, String workspace, String name) {
            this.id = id;
            tenantId = tenant;
            workspaceId = workspace;
            displayName = name;
            version = 1L;
        }
    }

    private Jdbi db() {
        Jdbi jdbi =
                Jdbi.create(
                        "jdbc:h2:mem:"
                                + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbi.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE items (tenantId varchar, workspaceId varchar, id varchar, displayName varchar, version bigint, PRIMARY KEY(tenantId,workspaceId,id))"));
        return jdbi;
    }

    @Test
    void scopeProtectsEveryCrudPathIncludingOrPredicatesAndOptimisticKeys() {
        Jdbi jdbi = db();
        GenericDao<Item> all = new GenericDao<>(jdbi, Item.class, "items");
        all.insert(new Item("same", "a", "w", "Alpha"));
        all.insert(new Item("same", "b", "w", "Beta"));
        all.insert(new Item("same", "a", "other", "Other workspace"));
        GenericDao<Item> dao = all.scoped(Map.of("tenantId", "a", "workspaceId", "w"));
        assertEquals("Alpha", dao.getById("same").orElseThrow().displayName);
        assertEquals(1, dao.count(Filtering.create()));
        assertEquals(
                1,
                dao.filter(
                                Filtering.create()
                                        .equalTo("displayName", "Beta")
                                        .equalTo("id", "same")
                                        .withOperator(LogicalOperator.OR))
                        .size());
        assertEquals(
                1,
                dao.updateByKey(
                        Map.of("id", "same", "version", 1L),
                        Map.of("displayName", "Changed", "version", 2L)));
        assertEquals(
                0,
                dao.updateByKey(
                        Map.of("id", "same", "version", 1L), Map.of("displayName", "Stale")));
        Item changed = dao.getByKey(Map.of("id", "same", "version", 2L)).orElseThrow();
        assertEquals("Changed", changed.displayName);
        assertThrows(
                IllegalArgumentException.class, () -> dao.update("same", Map.of("tenantId", "b")));
        changed.tenantId = "b";
        assertThrows(IllegalArgumentException.class, () -> dao.update(changed));
        assertThrows(
                IllegalArgumentException.class,
                () -> dao.insert(new Item("bad", "b", "w", "Wrong scope")));
        assertThrows(IllegalArgumentException.class, () -> dao.scoped(Map.of("tenantId", "b")));
        assertThrows(IllegalArgumentException.class, () -> dao.deleteByKey(Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> dao.updateByKey(Map.of(), Map.of("displayName", "Bad")));
        assertEquals(1, dao.deleteByKey(Map.of("id", "same", "version", 2L)));
        assertEquals(2, all.count(Filtering.create()));
        assertEquals(
                "Beta",
                all.scoped(Map.of("tenantId", "b")).getById("same").orElseThrow().displayName);
    }

    @Test
    void borrowedHandleRollsBackSeveralScopedDaosAndRemainsOpen() {
        Jdbi jdbi = db();
        GenericDao<Item> all = new GenericDao<>(jdbi, Item.class, "items");
        assertThrows(
                IllegalStateException.class,
                () ->
                        jdbi.useTransaction(
                                h -> {
                                    all.scoped(Map.of("tenantId", "a", "workspaceId", "w"))
                                            .using(h)
                                            .insert(new Item("one", null, null, "One"));
                                    all.scoped(Map.of("tenantId", "b", "workspaceId", "w"))
                                            .using(h)
                                            .insert(new Item("two", null, null, "Two"));
                                    assertFalse(h.isClosed());
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(0, all.count(Filtering.create()));
        jdbi.useTransaction(
                h -> {
                    GenericDao<Item> dao =
                            all.scoped(Map.of("tenantId", "a", "workspaceId", "w")).using(h);
                    dao.insert(new Item(">literal", null, null, "Camel case"));
                    assertEquals("Camel case", dao.getById(">literal").orElseThrow().displayName);
                    assertEquals("a", dao.getById(">literal").orElseThrow().tenantId);
                    assertEquals(1, dao.update(">literal", Map.of("displayName", "Updated")));
                    assertEquals(1, dao.filter(Filtering.create().withOffset(0L)).size());
                    assertEquals(1, dao.delete(">literal"));
                    assertFalse(h.isClosed());
                });
    }
}
