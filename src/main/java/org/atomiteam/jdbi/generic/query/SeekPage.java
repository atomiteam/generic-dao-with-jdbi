package org.atomiteam.jdbi.generic.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** No COUNT query. Continuation values are data, not an authorization or signed cursor token. */
public final class SeekPage<T> {
    private final List<T> items;
    private final boolean hasMore;
    private final List<?> nextValues;
    SeekPage(List<T> items, boolean hasMore, List<?> nextValues) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.hasMore = hasMore;
        this.nextValues = nextValues == null ? null : Collections.unmodifiableList(new ArrayList<>(nextValues));
    }
    public List<T> getItems() { return items; }
    public boolean hasMore() { return hasMore; }
    public List<?> getNextValues() { return nextValues; }
}
