package ch.supsi.dti.isin.consistenthash.mads;

import ch.supsi.dti.isin.consistenthash.BucketBasedEngine;
import ch.supsi.dti.isin.hashfunction.HashFunction;
import com.google.common.hash.Hashing;
import java.util.BitSet;
import java.util.Random;

/**
 * Implementation of the {@code MadsHash} algorithm as described in the related paper:
 * {@code https://arxiv.org/pdf/2107.07930.pdf}
 *
 * <p>
 * <b>IMPORTANT:</b>
 * This class is not performing any consistency check
 * to avoid the performance tests to be falsified.
 *
 * @author Mads Leonardi
 */
public class MadsEngine implements BucketBasedEngine {

    /** Working nodes. */
    private int size;

    /** Overall size of the cluster. */
    private int capacity;

    /**
     * A bit array representing the failed nodes.
     * If a bucket is failed the position of the related bit is 1.
     */
    private final BitSet failed;

    /** Keeps track of the removed nodes. */
    private final RemovedBuckets removed;

    /** Hashing function to use */
    private final HashFunction hashFunction;

    /**
     * Constructor with parameters.
     *
     * @param nodes        initial cluster nodes
     * @param capacity     overall number of available buckets
     * @param hashFunction the hash function to use
     */
    public MadsEngine(int size, int capacity, HashFunction hashFunction) {
        this(size, capacity, hashFunction, new OrderedHashRemovedBuckets());
    }

    /**
     * Constructor with parameters.
     *
     * @param size          initial cluster nodes
     * @param capacity      overall number of available buckets
     * @param hashFunction  the hash function to use
     * @param removed       removed buckets tracker
     */
    public MadsEngine(int size, int capacity, HashFunction hashFunction, RemovedBuckets removed) {
        super();
        this.size = size;
        this.capacity = capacity;

        this.removed = removed;
        this.hashFunction = hashFunction;

        this.failed = new BitSet(capacity);
        this.failed.set(size, capacity);
    }

    /* **************** */
    /*  PUBLIC METHODS  */
    /* **************** */

    /**
     * Returns the bucket where the given key should be mapped.
     *
     * @param key the key to map
     * @return the related bucket
     */
    public int getBucket(String key) {
        /*
         * We invoke JumpHash to get a bucket
         * in the range [0,capacity-1].
         */
        int b = Hashing.consistentHash(hashFunction.hash(key), capacity);
        if (!failed.get(b)) {
            return b;
        }

        final Random random = new Random(hashFunction.hash(key));
        b = random.nextInt(capacity);

        /* Loop until hitting a working bucket. */
        while (failed.get(b) /* Next random in sequence. */) b = random.nextInt(
            capacity
        );

        return b;
    }

    /**
     * Adds a new bucket to the engine.
     *
     * @return the index of the added bucket
     */
    public int addBucket() {
        /*
         * If the set is not empty takes the first bucket found in it.
         * Otherwise, uses the next available bucket (with index 'size').
         */
        final int b;
        if (removed.isEmpty()) {
            b = size;
            ++capacity;
        } else {
            b = removed.pollNext();
        }

        failed.clear(b);
        ++size;

        return b;
    }

    /**
     * Adds the given bucket back to the engine.
     *
     * @param b index of the bucket to add
     * @return the added bucket
     */
    public int addBucket(int b) {
        failed.clear(b);
        removed.remove(b);
        capacity = capacity > b ? capacity : b + 1;
        ++size;

        return b;
    }

    /**
     * Removes the given bucket from the engine.
     *
     * @param b index of the bucket to remove
     * @return the removed bucket
     */
    public int removeBucket(int b) {
        if (removed.isEmpty() && b == capacity - 1) {
            --size;
            failed.clear(b);
            --capacity;

            return b;
        }

        /* Updates the size of the working set. */
        --size;
        failed.set(b);
        removed.add(b);

        return b;
    }

    /**
     * Returns the size of the working set.
     *
     * @return size of the working set.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the overall capacity of the cluster.
     *
     * @return overall capacity of the cluster.
     */
    public int capacity() {
        return capacity;
    }
}
