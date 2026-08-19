package org.atomiteam.jdbi.generic.dao;

/**
 * Backward-compatible entity base using a String primary key.
 */
public class Entity extends BaseEntity<String> {

    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
    }
}
