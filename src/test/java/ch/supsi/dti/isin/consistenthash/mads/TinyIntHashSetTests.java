package ch.supsi.dti.isin.consistenthash.mads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Test suite for the class {@link TinyIntHashSet}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class TinyIntHashSetTests
{

    @Test
    public void a_new_set_should_be_empty()
    {

        assertTrue( new TinyIntHashSet().isEmpty() );

    }

    @Test
    public void adding_a_value_should_make_the_set_non_empty()
    {

        final TinyIntHashSet set = new TinyIntHashSet();
        assertTrue( set.add( 3 ) );
        assertFalse( set.isEmpty() );

    }

    @Test
    public void adding_the_same_value_twice_should_ignore_the_duplicate()
    {

        final TinyIntHashSet set = new TinyIntHashSet();

        assertTrue( set.add( 7 ) );
        assertFalse( set.add( 7 ) );
        assertEquals( 7, set.poll() );
        assertTrue( set.isEmpty() );

    }

    @Test
    public void polled_values_should_belong_to_the_added_set_even_after_resize()
    {

        final TinyIntHashSet set = new TinyIntHashSet();
        final Set<Integer> expected = new HashSet<>();
        IntStream.range( 0, 32 ).forEach( value ->
        {
            expected.add( value );
            set.add( value );
        } );

        final Set<Integer> actual = new HashSet<>();
        while( ! set.isEmpty() )
            actual.add( set.poll() );

        assertEquals( expected, actual );

    }

}
