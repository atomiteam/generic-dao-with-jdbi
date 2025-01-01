
# Generic DAO with JDBI

A Java library providing a generic Data Access Object (DAO) implementation using [JDBI](https://jdbi.org/) for database interactions. This project simplifies CRUD operations, enhances flexibility, and promotes code reusability.

## Features

- **Simplified CRUD Operations**: Perform create, read, update, and delete operations with ease.
- **Change Tracking**: Utility methods for tracking changes in entity fields.
- **Flexible Mapping**: Seamless mapping of database rows to Java objects using `BeanMapper`.
- **Test-Friendly Setup**: In-memory H2 database configuration for testing.

## Prerequisites

- Java 11 or higher.
- Maven.

## Constraints

- **Case-Sensitive Matching**: Column names in the database and Java property names must match exactly, including case sensitivity.
- **ID Column**: All entities must include an `id` column of type `VARCHAR`.

## Installation

Clone the repository:

```bash
git clone https://github.com/your-username/generic-dao-with-jdbi.git
```

Navigate to the project directory and build the project:

```bash
cd generic-dao-with-jdbi
mvn clean install
```

## Usage

### Setting Up a New DAO

1. **Create an Entity**: Extend the `Entity` class for your data model.

```java
public class Hotel extends Entity {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

2. **Define a DAO**: Create a DAO class by extending `GenericDao`.

```java
public class HotelDao extends GenericDao<Hotel> {
    public HotelDao(Jdbi jdbi) {
        super(jdbi, Hotel.class, "hotel");
    }
}
```

3. **Use the DAO**: Interact with your DAO in the application.

```java
Jdbi jdbi = Jdbi.create("jdbc:h2:mem:test");
HotelDao hotelDao = new HotelDao(jdbi);

Hotel hotel = new Hotel();
hotel.setId("1");
hotel.setName("Hotel Paradise");
hotelDao.insert(hotel);
```

### Testing

This project includes unit tests using JUnit and Mockito. To run the tests:

```bash
mvn test
```

## Project Structure

- **`Entity.java`**: A base class for all data entities.
- **`GenericDao.java`**: Generic DAO implementation for CRUD operations.
- **`Hotel.java`**: Example entity representing a hotel.
- **`HotelDao.java`**: Example DAO for the `Hotel` entity.
- **`GenericDaoTest.java`**: Test cases for the `GenericDao` functionality.

## Dependencies

This project uses the following dependencies:

- [JDBI 3](https://jdbi.org/) for database interactions.
- [JUnit 5](https://junit.org/junit5/) for testing.
- [Mockito](https://site.mockito.org/) for mocking.
- [H2 Database](https://www.h2database.com/) for in-memory database testing.

All dependencies are managed via Maven. See the `pom.xml` file for details.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

## Contributing

Contributions are welcome! Please submit issues or pull requests via GitHub.

## Contact

For questions or suggestions, please reach out to [your-email@example.com].
