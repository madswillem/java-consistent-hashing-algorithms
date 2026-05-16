package ch.supsi.dti.isin.consistenthash.mads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BitSetRemovedBuckets}.
 */
class BitSetRemovedBucketsTests {

    @Test
    void new_instance_should_be_empty() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        assertTrue(removed.isEmpty());
    }

    @Test
    void add_should_mark_bucket_as_removed() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        removed.add(5);
        assertFalse(removed.isEmpty());
    }

    @Test
    void pollNext_should_return_buckets_in_ascending_order() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        removed.add(7);
        removed.add(2);
        removed.add(5);
        removed.add(1);

        assertEquals(1, removed.pollNext());
        assertEquals(2, removed.pollNext());
        assertEquals(5, removed.pollNext());
        assertEquals(7, removed.pollNext());
        assertTrue(removed.isEmpty());
    }

    @Test
    void pollNext_should_throw_if_empty() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        assertThrows(NoSuchElementException.class, removed::pollNext);
    }

    @Test
    void remove_should_clear_bucket_and_return_presence() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        removed.add(5);
        assertTrue(removed.remove(5));
        assertFalse(removed.remove(5));
        assertTrue(removed.isEmpty());
    }

    @Test
    void remove_should_ignore_invalid_buckets() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        assertFalse(removed.remove(-1));
        assertFalse(removed.remove(100));
    }

    @Test
    void isEmpty_should_reflect_state() {
        final RemovedBuckets removed = new BitSetRemovedBuckets();
        assertTrue(removed.isEmpty());
        removed.add(3);
        assertFalse(removed.isEmpty());
        removed.pollNext();
        assertTrue(removed.isEmpty());
    }
}