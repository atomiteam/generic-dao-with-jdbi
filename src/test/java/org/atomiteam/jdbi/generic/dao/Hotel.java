package org.atomiteam.jdbi.generic.dao;

public class Hotel extends Entity {

    public static final String TEST_STATIC = "static value";

    private String name;
    @Json
    private Address address;

    @Transient
    private Address transientProperty;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Address getTransientProperty() {
        return transientProperty;
    }

    public void setTransientProperty(Address transientProperty) {
        this.transientProperty = transientProperty;
    }

}
