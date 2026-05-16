package ch.supsi.dti.isin.consistenthash.mads;

/**
 * Tracks buckets removed from the working set and available for reuse.
 */
public interface RemovedBuckets {

    /**
     * Returns whether there are any removed buckets available.
     *
     * @return {@code true} if no removed buckets are tracked
     */
    boolean isEmpty();

    /**
     * Adds the given removed bucket.
     *
     * @param bucket the removed bucket to track
     */
    void add(int bucket);

    /**
     * Removes the given bucket, if present.
     *
     * @param bucket the bucket to remove
     * @return {@code true} if the bucket was removed
     */
    boolean remove(int bucket);

    /**
     * Returns and removes the next bucket to restore.
     *
     * @return the next bucket to restore
     */
    int pollNext();
}
