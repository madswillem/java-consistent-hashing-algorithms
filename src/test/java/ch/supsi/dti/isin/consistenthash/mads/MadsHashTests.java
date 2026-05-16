package ch.supsi.dti.isin.consistenthash.mads;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.nerd4j.utils.lang.RequirementFailure;

import ch.supsi.dti.isin.cluster.Node;
import ch.supsi.dti.isin.cluster.SimpleNode;
import ch.supsi.dti.isin.consistenthash.ConsistentHashContract;

/**
 * Test suite for the class {@link MadsHash}.
 * 
 * @author Massimo Coluzzi
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class MadsHashTests implements ConsistentHashContract<MadsHash>
{

    /* ******************* */
    /*  INTERFACE METHODS  */
    /* ******************* */

    /**
     * {@inheritDoc}
     */
    @Override
    public MadsHash sampleValue( Collection<? extends Node> nodes )
    {

        return new MadsHash( nodes, nodes.size() << 1 );

    }

        
    /**
     * Creates a new {@link MadsHash} with the
     * given size and capacity.
     * 
     * @param nodes the initial nodes
     * @param capacity the overall capacity
     */
    public MadsHash sampleValue( Collection<? extends Node> nodes, int capacity )
    {

        return new MadsHash( nodes, capacity );

    }

    
    /* ************** */
    /*  TEST METHODS  */
    /* ************** */

    
    @ParameterizedTest
    @NullAndEmptySource
    public void the_cluster_must_have_at_least_one_node( List<Node> nodes )
    {

        assertThrows(
            RequirementFailure.class,
            () -> new MadsHash( nodes, 10 )
        );
        
    }

    @Test
    public void initial_nodes_cannot_be_null()
    {

        assertThrows(
            RequirementFailure.class,
            () -> new MadsHash( Collections.singletonList(null), 10 )
        );
        
    }

    @Test
    public void initial_nodes_cannot_be_duplicated()
    {

        final List<Node> nodes = IntStream.of( 1, 1 )
            .mapToObj( SimpleNode::of )
            .collect( toList() );

        assertThrows(
            RequirementFailure.class,
            () -> new MadsHash( nodes, 10 )
        );
        
    }

    @Test
    public void the_cluster_size_cannot_be_greater_than_the_overall_capacity()
    {

        final List<Node> nodes = IntStream.of( 1, 2 )
            .mapToObj( SimpleNode::of )
            .collect( toList() );

        assertThrows(
            RequirementFailure.class,
            () -> sampleValue( nodes, 1 )
        );
        
    }

    @Test
    public void adding_nodes_can_grow_beyond_the_initial_capacity()
    {

        final List<Node> nodes = IntStream.of( 1, 2, 3, 4, 5 )
            .mapToObj( SimpleNode::of )
            .collect( toList() );

        final MadsHash madsHash = sampleValue( nodes, 6 );
        
        assertDoesNotThrow( () -> madsHash.addNodes( Collections.singleton(SimpleNode.of(100))) );
        assertDoesNotThrow( () -> madsHash.addNodes(Collections.singleton(SimpleNode.of(101))) );
        assertEquals( 7, madsHash.nodeCount() );

    }

    @Test
    public void restored_nodes_should_keep_their_previous_buckets()
    {

        final List<Node> nodes = SimpleNode.create( 100 );
        final MadsHash madsHash = sampleValue( nodes, 200 );
        final List<String> keys = IntStream.range( 0, 1000 ).mapToObj( String::valueOf ).collect( toList() );
        final List<Node> removed = IntStream.iterate( 99, i -> i - 1 ).limit( 50 ).mapToObj( SimpleNode::of ).collect( toList() );

        final Map<String,Node> before = new HashMap<>();
        for( String key : keys )
            before.put( key, madsHash.getNode( key ) );

        madsHash.removeNodes( removed );

        Collections.reverse( removed );
        madsHash.addNodes( removed );

        for( String key : keys )
            assertEquals( before.get( key ), madsHash.getNode( key ) );

    }

}
