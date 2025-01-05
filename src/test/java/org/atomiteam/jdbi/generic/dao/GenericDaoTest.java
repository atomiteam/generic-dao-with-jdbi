package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Test class for GenericDao functionality.
 */
public class GenericDaoTest {

    private Jdbi mockJdbi;
    private Jdbi jdbi;
    private HotelDao hotelDao;
    private Handle handle;

    /**
     * Sets up the in-memory database and DAO before each test.
     * 
     * @throws Exception if setup fails.
     */
    @BeforeEach
    public void setUp() throws Exception {
        jdbi = Jdbi.create("jdbc:h2:mem:test;database_to_upper=false");
        jdbi.installPlugin(new H2DatabasePlugin());

        // Open a persistent handle
        handle = jdbi.open();

        handle.execute( //
                "CREATE TABLE IF NOT EXISTS hotel (" + //
                        "id VARCHAR PRIMARY KEY," + //
                        "name VARCHAR" + //
                        ")" //
        );

        // Create a mocked Jdbi
        mockJdbi = Mockito.mock(Jdbi.class);

        // Mock the withHandle method
        Mockito.when(mockJdbi.withHandle(Mockito.any())).thenAnswer(invocation -> {
            // Get the HandleCallback passed to withHandle
            HandleCallback callback = invocation.getArgument(0);
            // Simulate calling the callback with the mocked Handle
            return callback.withHandle(handle);
        });

        hotelDao = new HotelDao(mockJdbi);
    }

    /**
     * Tests the insertion and retrieval of a hotel entity.
     */
    @Test
    public void testInsert() {
        handle.execute("INSERT INTO \"hotel\" (id) VALUES (?)", "1");
        handle.createQuery("SELECT * FROM \"hotel\" WHERE id = :id");

        Hotel hotel = new Hotel();
        hotel.setId("Hotel_Insert_1");
        hotel.setName("Hotel 1");

        // Insert a hotel
        hotelDao.insert(hotel);
        hotelDao.update(hotel);

        // Verify insertion
        Optional<Hotel> insertedHotel = hotelDao.getById("Hotel_Insert_1");
        assertTrue(insertedHotel.isPresent(), "Inserted Hotel not found.");
        assertEquals("Hotel_Insert_1", insertedHotel.get().getId());
        assertEquals("Hotel 1", insertedHotel.get().getName());
    }

    /**
     * Tests retrieval of a hotel by ID.
     */
    @Test
    public void testGetById() {
        // Insert a hotel
        insertTestHotel("Hotel1");

        // Retrieve it by ID
        Optional<Hotel> actualHotel = hotelDao.getById("Hotel1");
        assertTrue(actualHotel.isPresent(), "Hotel not found.");
        assertEquals("Hotel1", actualHotel.get().getId());

        // Try to retrieve a non-existent hotel
        Optional<Hotel> nonExistentHotel = hotelDao.getById("non_existent_id");
        assertFalse(nonExistentHotel.isPresent(), "Non-existent Hotel should be empty.");
    }

    /**
     * Tests deletion of a hotel.
     */
    @Test
    public void testDelete() {
        // Insert a hotel
        insertTestHotel("Hotel_Delete_1");

        // Delete the hotel
        hotelDao.delete("Hotel_Delete_1");

        // Verify deletion
        Optional<Hotel> deletedHotel = hotelDao.getById("Hotel_Delete_1");
        assertFalse(deletedHotel.isPresent(), "Deleted Hotel should not exist.");
    }

    /**
     * Tests updating of a hotel.
     */
    @Test
    public void testUpdate() {
        // Insert a hotel
        insertTestHotel("Hotel_Update_1");
        Optional<Hotel> actualHotelAfterInsert = hotelDao.getById("Hotel_Update_1");
        assertEquals("Test Hotel", actualHotelAfterInsert.get().getName());

        // Update the hotel
        Hotel updatedHotel = new Hotel();
        updatedHotel.setId("Hotel_Update_1");
        updatedHotel.setName("Updated Name");
        hotelDao.update(updatedHotel);

        // Verify the update
        Optional<Hotel> actualHotel = hotelDao.getById("Hotel_Update_1");
        assertTrue(actualHotel.isPresent(), "Updated Hotel not found.");
        assertEquals("Updated Name", actualHotel.get().getName());
    }

    /**
     * Inserts a test hotel into the database.
     * 
     * @param id the ID of the hotel.
     */
    private void insertTestHotel(String id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName("Test Hotel");
        hotelDao.insert(hotel);
    }

    @Test
    public void testFilterWithAllOperators() {
        // Insert test data
        insertTestHotel("Hotel_1");
        insertTestHotel("Hotel_2");
        insertTestHotel("Hotel_3");

        // Test Eq (equality operator)
        List<Hotel> eqHotels = hotelDao.filter(Filtering.create().eq("id", "Hotel_1"));
        assertEquals(1, eqHotels.size(), "Eq operator should retrieve exactly one match.");
        assertEquals("Hotel_1", eqHotels.get(0).getId());

        // Test NotEq (inequality operator)
        List<Hotel> notEqHotels = hotelDao.filter(Filtering.create().notEq("id", "Hotel_1"));
        assertEquals(2, notEqHotels.size(), "NotEq operator should retrieve two matches.");
        assertTrue(notEqHotels.stream().noneMatch(h -> "Hotel_1".equals(h.getId())));

        // Test Like operator
        List<Hotel> likeHotels = hotelDao.filter(Filtering.create().like("id", "Hotel%"));
        assertEquals(3, likeHotels.size(), "Like operator should retrieve all matches.");

        // Test NotLike operator
        List<Hotel> notLikeHotels = hotelDao.filter(Filtering.create().notLike("id", "Hotel_1"));
        assertEquals(2, notLikeHotels.size(), "NotLike operator should retrieve two matches.");
        assertTrue(notLikeHotels.stream().noneMatch(h -> "Hotel_1".equals(h.getId())));

        // Test In operator
        List<Hotel> inHotels = hotelDao.filter(Filtering.create().in("id", List.of("Hotel_1", "Hotel_3")));
        assertEquals(2, inHotels.size(), "In operator should retrieve two matches.");
        assertTrue(inHotels.stream().anyMatch(h -> "Hotel_1".equals(h.getId())));
        assertTrue(inHotels.stream().anyMatch(h -> "Hotel_3".equals(h.getId())));

        // Test NotIn operator
        List<Hotel> notInHotels = hotelDao.filter(Filtering.create().notIn("id", List.of("Hotel_1", "Hotel_3")));
        assertEquals(1, notInHotels.size(), "NotIn operator should retrieve one match.");
        assertEquals("Hotel_2", notInHotels.get(0).getId());
    }

    @Test
    public void testEmptyAndNullFilters() {
        // Empty filters
        List<Hotel> allHotels = hotelDao.filter(Filtering.create());
        assertFalse(allHotels.isEmpty(), "Filter with no conditions should return all records.");

    }

    @Test
    public void testInsertAndUpdateEdgeCases() {
        // Insert an entity with only ID
        Hotel minimalHotel = new Hotel();
        minimalHotel.setId("Minimal_Hotel");
        hotelDao.insert(minimalHotel);

        Optional<Hotel> retrievedMinimalHotel = hotelDao.getById("Minimal_Hotel");
        assertTrue(retrievedMinimalHotel.isPresent());
        assertNull(retrievedMinimalHotel.get().getName());

        // Update with no changes
        Hotel unchangedHotel = retrievedMinimalHotel.get();
        int rowsUpdated = hotelDao.update(unchangedHotel);
        assertEquals(0, rowsUpdated, "Updating with no changes should not affect any rows.");
    }

}
