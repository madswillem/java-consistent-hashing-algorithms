package ch.supsi.dti.isin.consistenthash.mads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderedHashRemovedBuckets}.
 */
class OrderedHashRemovedBucketsTests {

    @Test
    void new_instance_should_be_empty() {
        final RemovedBuckets removed = new OrderedHashRemovedBuckets();

        assertTrue(removed.isEmpty());
        assertThrows(NoSuchElementException.class, removed::pollNext);
    }

    @Test
    void single_add_should_be_returned_by_pollNext() {
        final RemovedBuckets removed = new OrderedHashRemovedBuckets();

        removed.add(5);

        assertFalse(removed.isEmpty());
        assertEquals(5, removed.pollNext());
        assertTrue(removed.isEmpty());
    }

    @Test
    void duplicate_add_should_not_duplicate_output() {
        final RemovedBuckets removed = new OrderedHashRemovedBuckets();

        removed.add(5);
        removed.add(5);
        removed.add(5);

        assertEquals(5, removed.pollNext());
        assertTrue(removed.isEmpty());
    }

    @Test
    void multiple_insertion_orders_should_produce_the_same_poll_sequence() {
        final int[] firstOrder = { 7, 2, 5, 1, 9, 3 };
        final int[] secondOrder = { 3, 9, 1, 5, 2, 7 };

        assertEquals(pollSequence(firstOrder), pollSequence(secondOrder));
    }

    @Test
    void remove_should_remove_exact_bucket_before_polling() {
        final RemovedBuckets removed = new OrderedHashRemovedBuckets();

        removed.add(4);
        removed.add(1);
        removed.add(7);

        assertTrue(removed.remove(4));
        assertFalse(removed.remove(4));
        final List<Integer> polled = pollAll(removed);

        assertEquals(new HashSet<>(List.of(1, 7)), new HashSet<>(polled));
        assertFalse(polled.contains(4));
        assertTrue(removed.isEmpty());
    }

    @Test
    void repeated_pollNext_should_be_independent_of_insertion_order() {
        final int[] firstOrder = { 12, 0, 8, 3, 15, 1, 19, 22, 6, 11 };
        final int[] secondOrder = { 11, 6, 22, 19, 1, 15, 3, 8, 0, 12 };

        assertEquals(pollSequence(firstOrder), pollSequence(secondOrder));
    }

    @Test
    void should_remain_correct_across_rehash_and_resizing() {
        final int[] descending = IntStream.iterate(99, bucket -> bucket - 1).limit(100).toArray();
        final int[] ascending = IntStream.range(0, 100).toArray();

        assertEquals(pollSequence(descending), pollSequence(ascending));
    }

    private static List<Integer> pollSequence(int[] buckets) {
        final RemovedBuckets removed = new OrderedHashRemovedBuckets();
        final List<Integer> sequence = new ArrayList<>();

        for (int bucket : buckets) {
            removed.add(bucket);
        }
        while (!removed.isEmpty()) {
            sequence.add(removed.pollNext());
        }

        return sequence;
    }

    private static List<Integer> pollAll(RemovedBuckets removed) {
        final List<Integer> sequence = new ArrayList<>();

        while (!removed.isEmpty()) {
            sequence.add(removed.pollNext());
        }

        return sequence;
    }
}
