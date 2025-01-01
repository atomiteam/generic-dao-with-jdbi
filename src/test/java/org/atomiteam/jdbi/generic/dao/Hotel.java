package org.atomiteam.jdbi.generic.dao;

import org.atomiteam.jdbi.generic.dao.Entity;

public class Hotel extends Entity {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
