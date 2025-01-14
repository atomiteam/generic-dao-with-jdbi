package org.atomiteam.jdbi.generic.dao;

public class Hotel extends Entity {

	public static final String TEST_STATIC = "static value";

	private String name;
	@Json
	private Address address;

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

}
