package org.atomiteam.jdbi.generic.query;

import java.util.Collections;
import java.util.List;

/** Result of one paged query execution. */
public final class QueryPage<T> {
    private final List<T> items;
    private final long total;

    public QueryPage(List<T> items, long total) {
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
        this.total = total;
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
}
