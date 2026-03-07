package ch.supsi.dti.isin.consistenthash.mads;

/**
 * Tiny open-addressed hash set for non-negative integers.
 */
final class TinyIntHashSet
{

    /** The minimum backing table size. */
    private static final int MIN_TABLE_SIZE = 8;

    /** Maximum load factor expressed as a fraction denominator. */
    private static final int LOAD_FACTOR_DENOMINATOR = 4;

    /** Maximum load factor expressed as a fraction numerator. */
    private static final int LOAD_FACTOR_NUMERATOR = 3;


    /** Backing table storing values offset by one. */
    private int[] table;

    /** Number of stored values. */
    private int size;

    /** Resize threshold. */
    private int threshold;


    /**
     * Creates a new set.
     */
    TinyIntHashSet()
    {

        super();

        this.table = new int[MIN_TABLE_SIZE];
        this.threshold = threshold( MIN_TABLE_SIZE );

    }


    /**
     * Returns {@code true} if the set is empty.
     *
     * @return {@code true} if empty, {@code false} otherwise
     */
    boolean isEmpty()
    {

        return size <= 0;

    }

    /**
     * Adds the given value if not already present.
     *
     * @param value the value to add
     * @return {@code true} if the value was added, {@code false} otherwise
     */
    boolean add( int value )
    {

        if( size >= threshold )
            resize( table.length << 1 );

        return addInternal( value + 1, table );

    }

    /**
     * Removes and returns the first value encountered while scanning the backing table.
     *
     * @return a stored value
     */
    int poll()
    {

        for( int stored : table )
            if( stored != 0 )
            {
                final int value = stored - 1;
                remove( value );
                return value;
            }

        throw new IllegalStateException( "Cannot poll from an empty set" );

    }


    /* ***************** */
    /*  PRIVATE METHODS  */
    /* ***************** */


    /**
     * Adds the given encoded value to the provided table.
     *
     * @param stored the encoded value to add
     * @param table the table to update
     * @return {@code true} if the value was added, {@code false} otherwise
     */
    private boolean addInternal( int stored, int[] table )
    {

        final int mask = table.length - 1;
        int index = indexOf( stored, mask );
        while( table[index] != 0 )
        {
            if( table[index] == stored )
                return false;

            index = ( index + 1 ) & mask;
        }

        table[index] = stored;
        if( table == this.table )
            ++size;

        return true;

    }

    /**
     * Removes the given value if present.
     *
     * @param value the value to remove
     * @return {@code true} if removed, {@code false} otherwise
     */
    private boolean remove( int value )
    {

        final int stored = value + 1;
        final int mask = table.length - 1;

        int index = indexOf( stored, mask );
        while( table[index] != 0 )
        {
            if( table[index] == stored )
            {
                shiftConflictingKeys( index, mask );
                --size;
                return true;
            }

            index = ( index + 1 ) & mask;
        }

        return false;

    }

    /**
     * Compacts the cluster following the removal of the given index.
     *
     * @param gapIndex the index to compact
     * @param mask table mask
     */
    private void shiftConflictingKeys( int gapIndex, int mask )
    {

        int index = gapIndex;
        for( ;; )
        {
            int next = ( index + 1 ) & mask;
            int stored;
            while( true )
            {
                stored = table[next];
                if( stored == 0 )
                {
                    table[index] = 0;
                    return;
                }

                final int slot = indexOf( stored, mask );
                if( shouldMove( slot, index, next ) )
                    break;

                next = ( next + 1 ) & mask;
            }

            table[index] = stored;
            index = next;
        }

    }

    /**
     * Resizes the backing table.
     *
     * @param newSize size of the new table
     */
    private void resize( int newSize )
    {

        final int[] newTable = new int[newSize];
        for( int stored : table )
            if( stored != 0 )
                addInternal( stored, newTable );

        this.table = newTable;
        this.threshold = threshold( newSize );

    }

    /**
     * Computes the preferred index for the encoded value.
     *
     * @param stored the encoded value
     * @param mask table mask
     * @return preferred table index
     */
    private static int indexOf( int stored, int mask )
    {

        int hash = stored * 0x9E3779B9;
        hash ^= hash >>> 16;
        return hash & mask;

    }

    /**
     * Returns {@code true} if the current entry must be moved into the gap.
     *
     * @param slot preferred slot of the current entry
     * @param gap current gap index
     * @param index current entry index
     * @return {@code true} if the current entry must be moved, {@code false} otherwise
     */
    private static boolean shouldMove( int slot, int gap, int index )
    {

        if( gap <= index )
            return slot <= gap || slot > index;

        return slot <= gap && slot > index;

    }

    /**
     * Returns the resize threshold for the given table size.
     *
     * @param size table size
     * @return threshold
     */
    private static int threshold( int size )
    {

        return size / LOAD_FACTOR_DENOMINATOR * LOAD_FACTOR_NUMERATOR;

    }

}
