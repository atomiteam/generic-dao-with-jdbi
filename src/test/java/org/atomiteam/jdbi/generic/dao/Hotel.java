package org.atomiteam.jdbi.generic.dao;

public class Hotel extends Entity {

    public static final String TEST_STATIC = "static value";

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
