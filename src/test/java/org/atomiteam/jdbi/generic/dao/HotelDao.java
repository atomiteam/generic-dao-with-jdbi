package org.atomiteam.jdbi.generic.dao;

import org.atomiteam.jdbi.generic.dao.GenericDao;
import org.jdbi.v3.core.Jdbi;

public class HotelDao extends GenericDao<Hotel> {

    public HotelDao(Jdbi jdbi) {
        super(jdbi, Hotel.class, "hotel");
    }
}
