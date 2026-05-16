package ch.supsi.dti.isin.consistenthash.mads;

import java.util.TreeSet;

/**
 * {@link RemovedBuckets} implementation backed by a {@link TreeSet}.
 */
final class TreeSetRemovedBuckets implements RemovedBuckets {

    private final TreeSet<Integer> buckets;

    TreeSetRemovedBuckets() {
        this.buckets = new TreeSet<>();
    }

    @Override
    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    @Override
    public void add(int bucket) {
        buckets.add(bucket);
    }

    @Override
    public boolean remove(int bucket) {
        return buckets.remove(bucket);
    }

    @Override
    public int pollNext() {
        return buckets.pollFirst();
    }
}
