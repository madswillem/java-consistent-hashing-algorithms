package ch.supsi.dti.isin.benchmark.executor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import ch.supsi.dti.isin.benchmark.adapter.HashFunctionLoader;
import ch.supsi.dti.isin.benchmark.config.AlgorithmConfig;
import ch.supsi.dti.isin.benchmark.adapter.ConsistentHashFactory;
import ch.supsi.dti.isin.benchmark.config.BenchmarkConfig;
import ch.supsi.dti.isin.benchmark.config.CommonConfig;
import ch.supsi.dti.isin.benchmark.config.ConfigUtils;
import ch.supsi.dti.isin.benchmark.config.InconsistentValueException;
import ch.supsi.dti.isin.benchmark.config.InvalidTypeException;
import ch.supsi.dti.isin.benchmark.config.IterationsConfig;
import ch.supsi.dti.isin.benchmark.config.JMHConfigWrapper;
import ch.supsi.dti.isin.benchmark.config.MissingValueException;
import ch.supsi.dti.isin.benchmark.config.TimeConfig;
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

    /** Fractions of initial nodes to remove. */
    private final String[] removalRates;

    /** Fractions of the removal list to shuffle at each index. */
    private final String[] windowDistances;

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

        this.removalRates = getPercentages( "removalrate", DEFAULT_REMOVAL_RATE, false );
        this.windowDistances = getPercentages( "windowdistance", DEFAULT_WINDOW_DISTANCE, true );
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

        final Path file = BenchmarkExecutionUtils.getOutputFile( config );

        final CommonConfig common = config.getCommon();
        final TimeConfig time = common.getTime();
        final IterationsConfig iterations = common.getIterations();

        final Options opt = new OptionsBuilder()
            .include( RemovalOrderDisagreement.RemovalOrderDisagreementExecutor.class.getCanonicalName() )

            .param( "benchmark", config.getName() )
            .param( "function", BenchmarkExecutionUtils.getHashFunctionNames(config) )
            .param( "initNodes", BenchmarkExecutionUtils.getInitNodes(config) )
            .param( "algorithm", BenchmarkExecutionUtils.getAlgorithms(factories) )
            .param( "removalRate", removalRates )
            .param( "windowDistance", windowDistances )
            .param( "recordCount", String.valueOf(recordCount) )
            .param( "seed", String.valueOf(seed) )

            .resultFormat( ResultFormatType.CSV )
            .result( file.toString() )

            .shouldDoGC( common.isGc() )
            .forks( 1 )

            .mode( Mode.AverageTime )
            .timeUnit( time.getUnit() )
            .warmupTime( time.getWarmup() )
            .measurementTime( time.getExecution() )
            .warmupIterations( iterations.getWarmup() )
            .measurementIterations( iterations.getExecution() )

            .build();

        try{

            new Runner( opt ).run();

        }catch( RunnerException ex )
        {

            throw BenchmarkExecutionException.of( ex );

        }

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
     * Returns a random sample of nodes preserving the shuffled order.
     *
     * @param nodes source nodes
     * @param count number of nodes to sample
     * @param random random generator to use
     * @return sampled nodes
     */
    static List<Node> sampleNodes( List<Node> nodes, int count, Random random )
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
    private static Metrics collectMetrics(
        HashFunction function, ConsistentHashFactory factory, List<Node> initNodes,
        List<Node> baselineOrder, float removalRate, float windowDistance, int index, List<Node> variantOrder,
        List<String> records
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
    private String[] getPercentages( String property, float defaultValue, boolean inclusive )
    {

        final Object value = config.getArgs().get( property );
        if( value == null )
            return new String[]{ String.valueOf(defaultValue) };

        final ValuePath path = config.getPath().append( "args" ).append( property );
        if( ! (value instanceof List) )
            throw InvalidTypeException.of( path, value, List.class );

        @SuppressWarnings("unchecked")
        final List<Object> values = (List<Object>) value;
        if( values.isEmpty() )
            return new String[]{ String.valueOf(defaultValue) };

        final String[] result = new String[values.size()];
        for( int i = 0; i < values.size(); ++i )
        {

            final ValuePath itemPath = path.append( i );
            final Object item = values.get( i );
            if( item == null )
                throw MissingValueException.of( itemPath );

            if( ! (item instanceof Number) )
                throw InvalidTypeException.of( itemPath, item, Number.class );

            final float percentage = ((Number) item).floatValue();
            if( percentage < 0 || (inclusive ? percentage > 1 : percentage >= 1) )
                throw InconsistentValueException.notAPercentage( itemPath, percentage );

            result[i] = String.valueOf( percentage );

        }

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


    /** Inner class that executes the benchmark through JMH. */
    @State(Scope.Benchmark)
    public static class RemovalOrderDisagreementExecutor
    {

        /** Name of the current benchmark. */
        @Param({})
        private String benchmark;

        /** Number of nodes used to initialize the cluster. */
        @Param({})
        private int initNodes;

        /** Hash function used to initialize the cluster. */
        @Param({})
        private String function;

        /** Name of the algorithm to benchmark. */
        @Param({})
        private String algorithm;

        /** Fraction of initial nodes to remove. */
        @Param({})
        private float removalRate;

        /** Fraction of removal list shuffled at each index. */
        @Param({})
        private float windowDistance;

        /** Number of deterministic record IDs used for mapping comparison. */
        @Param({})
        private int recordCount;

        /** Seed used to choose removed nodes and generate variant orders. */
        @Param({})
        private long seed;

        /** Factory used to create consistent hash instances. */
        private ConsistentHashFactory factory;

        /** Hash function used by the benchmark. */
        private HashFunction hashFunction;

        /** Initial nodes used by the benchmark. */
        private List<Node> nodes;

        /** Baseline removal order. */
        private List<Node> baselineOrder;

        /** Variant removal orders keyed by shuffled window index. */
        private Map<Integer,List<Node>> variants;

        /** Records to compare. */
        private List<String> records;

        /** Whether this algorithm can run this benchmark. */
        private boolean supported;

        /**
         * Setups config values before running the benchmark.
         *
         * @param wrapper automatically populated JMH config wrapper
         */
        @Setup
        public void setup( JMHConfigWrapper wrapper )
        {

            final AlgorithmConfig algorithmConfig = BenchmarkExecutionUtils.getAlgorithmConfig( wrapper.getConfig(), algorithm );

            this.factory = BenchmarkExecutionUtils.getFactory( algorithmConfig );
            this.hashFunction = HashFunctionLoader.getInstance().load( function );
            this.nodes = SimpleNode.create( initNodes );
            this.records = generateRecords( recordCount );

            final ConsistentHash probe = factory.createConsistentHash( hashFunction, nodes );
            this.supported = ! probe.supportsOnlyLifoRemovals();
            if( ! supported )
            {
                logger.info( "Skipping " + algorithm + " because it only supports LIFO removals" );
                this.baselineOrder = Collections.emptyList();
                this.variants = Collections.emptyMap();
                return;
            }

            final int removedNodesCount = (int)( initNodes * removalRate );
            if( removedNodesCount <= 0 )
            {
                this.baselineOrder = Collections.emptyList();
                this.variants = Collections.emptyMap();
                return;
            }

            final Random random = new Random( seed );
            this.baselineOrder = sampleNodes( nodes, removedNodesCount, random );
            this.variants = generateRemovalOrders( baselineOrder, windowDistance, random );

        }

        /**
         * Computes the average disagreement across all generated removal-order variants.
         *
         * @return average failure rate for the configured parameter combination
         */
        @Benchmark
        public double disagreement()
        {

            if( ! supported || variants.isEmpty() )
                return 0;

            double total = 0;
            for( Map.Entry<Integer,List<Node>> variant : variants.entrySet() )
            {

                final Metrics metrics = collectMetrics(
                    hashFunction, factory, nodes, baselineOrder, removalRate, windowDistance,
                    variant.getKey(), variant.getValue(), records
                );
                total += metrics.failureRate();

            }

            return total / variants.size();

        }

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
