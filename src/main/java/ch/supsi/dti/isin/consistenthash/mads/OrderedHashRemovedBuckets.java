package ch.supsi.dti.isin.consistenthash.mads;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * {@link RemovedBuckets} implementation backed by primitive sorted hash chains.
 * Collision chains are kept in ascending bucket-id order, making polling deterministic.
 */
final class OrderedHashRemovedBuckets implements RemovedBuckets {

    private static final int EMPTY = -1;
    private static final int INITIAL_CAPACITY = 16;

    private int[] heads;
    private int[] values;
    private int[] next;
    private int size;
    private int nextUnused;
    private int free;

    OrderedHashRemovedBuckets() {
        this.heads = new int[INITIAL_CAPACITY];
        this.values = new int[INITIAL_CAPACITY];
        this.next = new int[INITIAL_CAPACITY];
        this.nextUnused = 0;
        this.free = EMPTY;
        Arrays.fill(heads, EMPTY);
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void add(int bucket) {
        if ((size + 1) * 4 > heads.length * 3) {
            rehash(heads.length << 1);
        }

        final int head = slot(bucket);
        int previous = EMPTY;
        int current = heads[head];

        while (current != EMPTY && values[current] < bucket) {
            previous = current;
            current = next[current];
        }
        if (current != EMPTY && values[current] == bucket) {
            return;
        }

        final int entry = allocateEntry();
        values[entry] = bucket;
        next[entry] = current;

        if (previous == EMPTY) {
            heads[head] = entry;
        } else {
            next[previous] = entry;
        }
        ++size;
    }

    @Override
    public boolean remove(int bucket) {
        final int head = slot(bucket);
        int previous = EMPTY;
        int current = heads[head];

        while (current != EMPTY && values[current] < bucket) {
            previous = current;
            current = next[current];
        }
        if (current == EMPTY || values[current] != bucket) {
            return false;
        }

        if (previous == EMPTY) {
            heads[head] = next[current];
        } else {
            next[previous] = next[current];
        }
        releaseEntry(current);
        --size;
        return true;
    }

    @Override
    public int pollNext() {
        if (isEmpty()) {
            throw new NoSuchElementException("No removed buckets available");
        }

        for (int head = 0; head < heads.length; ++head) {
            final int entry = heads[head];
            if (entry != EMPTY) {
                final int bucket = values[entry];
                heads[head] = next[entry];
                releaseEntry(entry);
                --size;
                return bucket;
            }
        }

        throw new NoSuchElementException("No removed buckets available");
    }

    private void rehash(int capacity) {
        final int[] previousHeads = heads;
        final int[] previousValues = values;
        final int[] previousNext = next;

        heads = new int[capacity];
        values = new int[capacity];
        next = new int[capacity];
        nextUnused = 0;
        free = EMPTY;
        size = 0;
        Arrays.fill(heads, EMPTY);

        for (int head = 0; head < previousHeads.length; ++head) {
            int current = previousHeads[head];
            while (current != EMPTY) {
                add(previousValues[current]);
                current = previousNext[current];
            }
        }
    }

    private int allocateEntry() {
        if (free != EMPTY) {
            final int entry = free;
            free = next[entry];
            return entry;
        }

        return nextUnused++;
    }

    private void releaseEntry(int entry) {
        next[entry] = free;
        free = entry;
    }

    private int slot(int bucket) {
        return mix(bucket) & (heads.length - 1);
    }

    private static int mix(int value) {
        int hash = value;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return hash;
    }
}
