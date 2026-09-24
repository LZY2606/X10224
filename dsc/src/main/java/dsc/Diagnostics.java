package dsc;

import java.util.ArrayList;
import java.util.List;

/** Non-fatal problems found while parsing or converting. Values stay in place. */
public final class Diagnostics {
    public final List<String> items = new ArrayList<>();

    public void add(String code, String message) {
        items.add(code + ": " + message);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public String toString() {
        return String.join(" | ", items);
    }
}
