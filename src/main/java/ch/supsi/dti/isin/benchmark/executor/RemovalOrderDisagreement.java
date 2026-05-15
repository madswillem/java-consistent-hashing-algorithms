package ch.supsi.dti.isin.benchmark.executor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import ch.supsi.dti.isin.benchmark.adapter.ConsistentHashFactory;
import ch.supsi.dti.isin.benchmark.config.BenchmarkConfig;
import ch.supsi.dti.isin.benchmark.config.ConfigUtils;
import ch.supsi.dti.isin.benchmark.config.InconsistentValueException;
import ch.supsi.dti.isin.benchmark.config.InvalidTypeException;
import ch.supsi.dti.isin.benchmark.config.ValuePath;
import ch.supsi.dti.isin.cluster.Node;
import ch.supsi.dti.isin.cluster.SimpleNode;
import ch.supsi.dti.isin.consistenthash.ConsistentHash;
import ch.supsi.dti.isin.hashfunction.HashFunction;

/**
 * Benchmark measuring how much different node-removal orders disagree after
 * removing the same set of nodes from two equivalent consistent hash instances.
 */
public class RemovalOrderDisagreement extends BenchmarkExecutor
{

    /** Java Logging System. */
    private static final Logger logger = Logger.getLogger( RemovalOrderDisagreement.class.getName() );

    /** Default fraction of initial nodes to remove. */
    public static final float DEFAULT_REMOVAL_RATE = 0.75f;

    /** Default fraction of the removal list to shuffle at each index. */
    public static final float DEFAULT_WINDOW_DISTANCE = 0.5f;

    /** Default number of records used to measure disagreement. */
    public static final int DEFAULT_RECORD_COUNT = 100_000;

    /** Default random seed used for reproducible node-order generation. */
    public static final long DEFAULT_SEED = 0L;

    /** Fraction of initial nodes to remove. */
    private final float removalRate;

    /** Fraction of the removal list to shuffle at each index. */
    private final float windowDistance;

    /** Number of records used to measure disagreement. */
    private final int recordCount;

    /** Random seed used for reproducible node-order generation. */
    private final long seed;


    /**
     * Constructor with parameters.
     *
     * @param config configuration to use to setup the current benchmark
     */
    public RemovalOrderDisagreement( BenchmarkConfig config )
    {

        super( config );

        this.removalRate = getPercentage( "removalrate", DEFAULT_REMOVAL_RATE );
        this.windowDistance = getPercentageInclusive( "windowdistance", DEFAULT_WINDOW_DISTANCE );
        this.recordCount = getPositiveInt( "recordcount", DEFAULT_RECORD_COUNT );
        this.seed = getLong( "seed", DEFAULT_SEED );

    }


    /* ***************** */
    /*  EXTENSION HOOKS  */
    /* ***************** */


    /**
     * {@inheritDoc}
     */
    @Override
    protected void performBenchmak( List<ConsistentHashFactory> factories ) throws Exception
    {

        runAndWriteMetrics( factories );

    }


    /* ***************** */
    /*  PACKAGE METHODS  */
    /* ***************** */


    /**
     * Generates the same removal-order variants as the Python helper
     * generate_removal_different_places.
     *
     * @param originalOrder baseline removal order
     * @param distance fraction of the removal order to shuffle at each index
     * @param random random generator to use for shuffling
     * @return map from start index to generated variant removal order
     */
    static <T> Map<Integer,List<T>> generateRemovalOrders( List<T> originalOrder, float distance, Random random )
    {

        final int size = originalOrder.size();
        final int scrambleCount = (int)( size * distance );
        final Map<Integer,List<T>> variants = new LinkedHashMap<>();

        for( int i = 0; i < size; ++i )
        {

            if( i + scrambleCount > size )
                break;

            final List<T> variant = new ArrayList<>( originalOrder );
            Collections.shuffle( variant.subList(i, i + scrambleCount), random );
            variants.put( i, variant );

        }

        return variants;

    }


    /**
     * Returns the configured number of keys as deterministic record IDs.
     *
     * @param recordCount number of records to generate
     * @return list of record IDs
     */
    static List<String> generateRecords( int recordCount )
    {

        return IntStream.range( 0, recordCount )
            .mapToObj( i -> "record_" + i )
            .toList();

    }


    /* ***************** */
    /*  PRIVATE METHODS  */
    /* ***************** */


    /**
     * Runs the benchmark and writes the results.
     *
     * @param factories the algorithms to benchmark
     * @throws IOException if an error occurred while writing results on file
     */
    private void runAndWriteMetrics( List<ConsistentHashFactory> factories ) throws IOException
    {

        final Path file = BenchmarkExecutionUtils.getOutputFile( config );
        try( final BufferedWriter writer = Files.newBufferedWriter(file) )
        {

            final List<String> records = generateRecords( recordCount );
            final List<HashFunction> functions = BenchmarkExecutionUtils.getHashFunctions( config );

            printHeader( writer );
            for( HashFunction function : functions )
                for( ConsistentHashFactory factory : factories )
                    for( int nodesCount : config.getCommon().getInitNodes() )
                    {

                        if( config.getCommon().isGc() )
                            System.gc();

                        runAndWriteMetrics( writer, function, factory, nodesCount, records );
                        writer.flush();

                    }

        }

    }

    /**
     * Runs the benchmark for a single hash function, algorithm, and node count.
     *
     * @param writer writer used to persist metrics
     * @param function hash function to use
     * @param factory algorithm factory to benchmark
     * @param nodesCount initial number of nodes
     * @param records records to compare
     * @throws IOException if writing fails
     */
    private void runAndWriteMetrics(
        BufferedWriter writer, HashFunction function, ConsistentHashFactory factory,
        int nodesCount, List<String> records
    ) throws IOException
    {

        final String algorithm = factory.getConfig().getName();
        final List<Node> initNodes = SimpleNode.create( nodesCount );
        final ConsistentHash probe = factory.createConsistentHash( function, initNodes );

        if( probe.supportsOnlyLifoRemovals() )
        {
            logger.info( "Skipping " + algorithm + " because it only supports LIFO removals" );
            return;
        }

        final int removedNodesCount = (int)( nodesCount * removalRate );
        if( removedNodesCount <= 0 )
            return;

        final Random random = new Random( seed );
        final List<Node> droppedNodes = sampleNodes( initNodes, removedNodesCount, random );
        final Map<Integer,List<Node>> variants = generateRemovalOrders( droppedNodes, windowDistance, random );

        for( Map.Entry<Integer,List<Node>> variant : variants.entrySet() )
        {

            final Metrics metrics = collectMetrics(
                function, factory, initNodes, droppedNodes, variant.getKey(), variant.getValue(), records
            );
            printMetrics( writer, metrics );

        }

    }

    /**
     * Returns a random sample of nodes preserving the shuffled order.
     *
     * @param nodes source nodes
     * @param count number of nodes to sample
     * @param random random generator to use
     * @return sampled nodes
     */
    private static List<Node> sampleNodes( List<Node> nodes, int count, Random random )
    {

        final List<Node> sample = new ArrayList<>( nodes );
        Collections.shuffle( sample, random );
        return new ArrayList<>( sample.subList(0, count) );

    }

    /**
     * Collects disagreement metrics for one variant order.
     *
     * @param function hash function to use
     * @param factory algorithm factory to benchmark
     * @param initNodes initial nodes
     * @param baselineOrder baseline removal order
     * @param index start index of the shuffled removal window
     * @param variantOrder variant removal order
     * @param records records to compare
     * @return collected metrics
     */
    private Metrics collectMetrics(
        HashFunction function, ConsistentHashFactory factory, List<Node> initNodes,
        List<Node> baselineOrder, int index, List<Node> variantOrder, List<String> records
    )
    {

        final ConsistentHash baseline = factory.createConsistentHash( function, initNodes );
        final ConsistentHash variant = factory.createConsistentHash( function, initNodes );

        baseline.removeNodes( applyOrder(baselineOrder) );
        variant.removeNodes( applyOrder(variantOrder) );

        int mismatches = 0;
        for( String record : records )
            if( ! baseline.getNode(record).equals(variant.getNode(record)) )
                ++mismatches;

        return new Metrics(
            function.name(), factory.getConfig().getName(), initNodes.size(), baselineOrder.size(),
            records.size(), removalRate, windowDistance, index, mismatches
        );

    }

    /**
     * Returns the actual order used to call removeNodes.
     * <p>
     * The experiment defines index 0 as the earliest logical removals. Engines
     * record replacements as nodes are removed, so the logical removal chain must
     * be applied from newest to oldest, matching the original Python experiment's
     * reversed(...) calls for AnchorHash.
     *
     * @param logicalOrder logical removal order used by the experiment
     * @return physical order to pass to the algorithm
     */
    static <T> List<T> applyOrder( List<T> logicalOrder )
    {

        final List<T> applied = new ArrayList<>( logicalOrder );
        Collections.reverse( applied );
        return applied;

    }

    /**
     * Prints the CSV header.
     *
     * @param writer the writer to use
     * @throws IOException if the writer fails
     */
    private static void printHeader( BufferedWriter writer ) throws IOException
    {

        writer.write( "HashFunction,Algorithm,Nodes,RemovedNodes,RecordCount,RemovalRate,WindowDistance,Index,Mismatches,FailureRate" );
        writer.newLine();

    }

    /**
     * Prints the collected metrics in CSV format.
     *
     * @param writer writer to use
     * @param metrics metrics to print
     * @throws IOException if writing fails
     */
    private static void printMetrics( BufferedWriter writer, Metrics metrics ) throws IOException
    {

        writer.write( metrics.function );
        writer.write( ',' );
        writer.write( metrics.algorithm );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.nodes) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.removedNodes) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.recordCount) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.removalRate) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.windowDistance) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.index) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.mismatches) );
        writer.write( ',' );
        writer.write( String.valueOf(metrics.failureRate()) );
        writer.newLine();

    }

    /**
     * Reads an integer argument that must be greater than zero.
     *
     * @param property normalized property name
     * @param defaultValue default value
     * @return configured value or default
     */
    private int getPositiveInt( String property, int defaultValue )
    {

        final Object value = config.getArgs().get( property );
        if( value == null )
            return defaultValue;

        final int result = ConfigUtils.toInt( config.getPath().append("args").append(property), value );
        if( result <= 0 )
            throw InconsistentValueException.lessOrEqual( config.getPath().append("args").append(property), 0, result );

        return result;

    }

    /**
     * Reads a percentage argument in range [0,1).
     *
     * @param property normalized property name
     * @param defaultValue default value
     * @return configured value or default
     */
    private float getPercentage( String property, float defaultValue )
    {

        final Object value = config.getArgs().get( property );
        if( value == null )
            return defaultValue;

        final ValuePath path = config.getPath().append( "args" ).append( property );
        if( ! (value instanceof Number) )
            throw InvalidTypeException.of( path, value, Number.class );

        final float result = ((Number) value).floatValue();
        if( result < 0 || result >= 1 )
            throw InconsistentValueException.notAPercentage( path, result );

        return result;

    }

    /**
     * Reads a percentage argument in range [0,1].
     *
     * @param property normalized property name
     * @param defaultValue default value
     * @return configured value or default
     */
    private float getPercentageInclusive( String property, float defaultValue )
    {

        final Object value = config.getArgs().get( property );
        if( value == null )
            return defaultValue;

        final ValuePath path = config.getPath().append( "args" ).append( property );
        if( ! (value instanceof Number) )
            throw InvalidTypeException.of( path, value, Number.class );

        final float result = ((Number) value).floatValue();
        if( result < 0 || result > 1 )
            throw InconsistentValueException.notAPercentage( path, result );

        return result;

    }

    /**
     * Reads a long argument.
     *
     * @param property normalized property name
     * @param defaultValue default value
     * @return configured value or default
     */
    private long getLong( String property, long defaultValue )
    {

        final Object value = config.getArgs().get( property );
        if( value == null )
            return defaultValue;

        final ValuePath path = config.getPath().append( "args" ).append( property );
        if( ! (value instanceof Number) )
            throw InvalidTypeException.of( path, value, Number.class );

        return ((Number) value).longValue();

    }


    /* *************** */
    /*  INNER CLASSES  */
    /* *************** */


    /** Collected metrics for one variant order. */
    private static class Metrics
    {

        /** Hash function name. */
        private final String function;

        /** Algorithm name. */
        private final String algorithm;

        /** Initial number of nodes. */
        private final int nodes;

        /** Number of removed nodes. */
        private final int removedNodes;

        /** Number of records compared. */
        private final int recordCount;

        /** Fraction of initial nodes removed. */
        private final float removalRate;

        /** Fraction of removal list shuffled at each index. */
        private final float windowDistance;

        /** Start index of the shuffled window. */
        private final int index;

        /** Number of records mapped differently. */
        private final int mismatches;

        /**
         * Constructor with parameters.
         */
        private Metrics(
            String function, String algorithm, int nodes, int removedNodes, int recordCount,
            float removalRate, float windowDistance, int index, int mismatches
        )
        {

            this.function = function;
            this.algorithm = algorithm;
            this.nodes = nodes;
            this.removedNodes = removedNodes;
            this.recordCount = recordCount;
            this.removalRate = removalRate;
            this.windowDistance = windowDistance;
            this.index = index;
            this.mismatches = mismatches;

        }

        /**
         * Returns the fraction of records mapped differently.
         *
         * @return failure rate
         */
        private double failureRate()
        {

            return (double) mismatches / recordCount;

        }

    }

}
