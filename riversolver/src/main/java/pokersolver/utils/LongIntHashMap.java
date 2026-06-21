package pokersolver.utils;

/**
 * Open-addressing hash map from non-zero {@code long} keys to {@code int} values.
 *
 * <p>Built for the 5-card rank dictionary (millions of entries): primitive arrays avoid the
 * per-entry node and boxing overhead of {@code Map<Long, Integer>}, and {@link #get} allocates
 * nothing — both matter because rank lookups sit on the showdown hot path of CFR training.
 *
 * <p>Key {@code 0} is reserved as the empty-slot marker; card bitmask keys always have at least
 * one bit set. Not thread-safe while being populated, safe for concurrent reads afterwards.
 */
public final class LongIntHashMap {

    private static final float LOAD_FACTOR = 0.6f;

    private long[] keys;
    private int[] values;
    private int mask;
    private int size;
    private int growAt;

    public LongIntHashMap(int expectedEntries) {
        int capacity = tableSizeFor(expectedEntries);
        allocate(capacity);
    }

    /** Returns the value for {@code key}, or {@code missingValue} if absent. */
    public int get(long key, int missingValue) {
        long[] keys = this.keys;
        int i = slot(key);
        while (true) {
            long k = keys[i];
            if (k == key) return values[i];
            if (k == 0) return missingValue;
            i = (i + 1) & mask;
        }
    }

    /**
     * Associates {@code key} with {@code value}. Returns {@code false} if the key was already
     * present (the existing value is left untouched).
     */
    public boolean put(long key, int value) {
        if (key == 0) throw new IllegalArgumentException("key 0 is reserved as the empty-slot marker");
        if (size >= growAt) grow();
        int i = slot(key);
        while (true) {
            long k = keys[i];
            if (k == 0) {
                keys[i] = key;
                values[i] = value;
                size++;
                return true;
            }
            if (k == key) return false;
            i = (i + 1) & mask;
        }
    }

    public int size() {
        return size;
    }

    private int slot(long key) {
        // SplitMix64 finalizer: card bitmasks cluster in their low bits, so mix before masking.
        long h = key;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) h & mask;
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new int[capacity];
        mask = capacity - 1;
        growAt = (int) (capacity * LOAD_FACTOR);
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        allocate(oldKeys.length * 2);
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != 0) put(oldKeys[i], oldValues[i]);
        }
    }

    private static int tableSizeFor(int expectedEntries) {
        long minCapacity = (long) Math.ceil(expectedEntries / LOAD_FACTOR);
        int capacity = Long.SIZE - Long.numberOfLeadingZeros(Math.max(minCapacity - 1, 1));
        return 1 << capacity;
    }
}
