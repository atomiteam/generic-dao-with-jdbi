package org.atomiteam.jdbi.generic.dao;

import org.jdbi.v3.core.Jdbi;

public class SnakeLongDao extends GenericDao<SnakeLongRecord> {
    public SnakeLongDao(Jdbi jdbi) {
        super(jdbi, SnakeLongRecord.class, "snake_long_record", ColumnNaming.SNAKE_CASE);
    }
}
