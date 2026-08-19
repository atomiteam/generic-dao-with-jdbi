package org.atomiteam.jdbi.generic.dao;

import org.jdbi.v3.core.Jdbi;

public class AtomiPojoDao extends GenericDao<AtomiPojo> {
    public AtomiPojoDao(Jdbi jdbi) {
        super(jdbi, AtomiPojo.class, "atomi_pojo", ColumnNaming.SNAKE_CASE);
    }
}
