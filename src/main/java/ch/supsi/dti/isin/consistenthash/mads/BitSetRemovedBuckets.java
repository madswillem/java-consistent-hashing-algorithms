package ch.supsi.dti.isin.consistenthash.mads;

import java.util.BitSet;
import java.util.NoSuchElementException;

/**
 * {@link RemovedBuckets} implementation backed by a {@link BitSet}.
 * Optimized for low removed/high non-removed ratios and minimal memory overhead.
 * Buckets are returned in ascending order by {@link #pollNext()}.
 */
final class BitSetRemovedBuckets implements RemovedBuckets {

    private final BitSet buckets;

    BitSetRemovedBuckets() {
        this.buckets = new BitSet();
    }

    @Override
    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    @Override
    public void add(int bucket) {
        buckets.set(bucket);
    }

    @Override
    public boolean remove(int bucket) {
        if (bucket < 0) {
            return false;
        }
        final boolean wasPresent = buckets.get(bucket);
        buckets.clear(bucket);
        return wasPresent;
    }

    @Override
    public int pollNext() {
        if (isEmpty()) {
            throw new NoSuchElementException("No removed buckets available");
        }
        final int next = buckets.nextSetBit(0);
        buckets.clear(next);
        return next;
    }
}