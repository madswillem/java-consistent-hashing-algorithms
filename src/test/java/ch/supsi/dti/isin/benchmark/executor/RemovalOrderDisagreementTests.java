package ch.supsi.dti.isin.benchmark.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import ch.supsi.dti.isin.benchmark.adapter.ConsistentHashFactory;
import ch.supsi.dti.isin.benchmark.adapter.ConsistentHashFactoryLoader;
import ch.supsi.dti.isin.benchmark.config.AlgorithmConfig;
import ch.supsi.dti.isin.benchmark.config.BenchmarkConfig;
import ch.supsi.dti.isin.benchmark.config.CommonConfig;
import ch.supsi.dti.isin.benchmark.config.ValuePath;

/**
 * Suite to test class {@link RemovalOrderDisagreement}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class RemovalOrderDisagreementTests
{

    @Test
    public void generateRemovalOrders_should_shuffle_the_window_at_each_possible_index()
    {

        final List<Integer> original = IntStream.range( 0, 10 ).boxed().toList();
        final Map<Integer,List<Integer>> variants = RemovalOrderDisagreement.generateRemovalOrders(
            original, 0.3f, new Random(1)
        );

        assertEquals( 8, variants.size() );
        for( Map.Entry<Integer,List<Integer>> entry : variants.entrySet() )
        {

            final int index = entry.getKey();
            final List<Integer> variant = entry.getValue();

            assertEquals( original.size(), variant.size() );
            assertEquals( new HashSet<>(original), new HashSet<>(variant) );
            assertEquals( original.subList(0, index), variant.subList(0, index) );
            assertEquals( original.subList(index + 3, original.size()), variant.subList(index + 3, variant.size()) );

        }

    }

    @Test
    public void applyOrder_should_reverse_the_logical_removal_chain()
    {

        final List<Integer> logicalOrder = List.of( 0, 1, 2, 3, 4 );
        final List<Integer> applyOrder = RemovalOrderDisagreement.applyOrder( logicalOrder );

        assertEquals( List.of(4, 3, 2, 1, 0), applyOrder );
        assertEquals( List.of(0, 1, 2, 3, 4), logicalOrder );

    }

    @Test
    public void benchmark_executor_should_be_loaded_by_name()
    {

        final CommonConfig common = CommonConfig.of( ValuePath.root(), null );
        final BenchmarkConfig benchmark = BenchmarkConfig.of(
            ValuePath.root().append("benchmarks").append(0),
            common,
            Map.of( "name", "removal-order-disagreement" )
        );

        final BenchmarkExecutor executor = assertDoesNotThrow(
            () -> BenchmarkExecutorLoader.getInstance().load( "removal-order-disagreement", benchmark )
        );

        assertTrue( executor instanceof RemovalOrderDisagreement );

    }

    @Test
    public void execute_should_write_disagreement_results_for_supported_algorithms() throws IOException
    {

        final Path outputFolder = Files.createTempDirectory( "removal-order-disagreement-" + UUID.randomUUID() );
        final CommonConfig common = CommonConfig.of(
            ValuePath.root().append("common"),
            Map.of(
                "gc", false,
                "output-folder", outputFolder.toString(),
                "init-nodes", List.of(8),
                "hash-functions", List.of("xx")
            )
        );
        final BenchmarkConfig benchmark = BenchmarkConfig.of(
            ValuePath.root().append("benchmarks").append(0),
            common,
            Map.of(
                "name", "removal-order-disagreement",
                "args", Map.of(
                    "removal-rate", 0.5,
                    "window-distance", 0.5,
                    "record-count", 20,
                    "seed", 1
                )
            )
        );
        final AlgorithmConfig algorithm = AlgorithmConfig.of(
            ValuePath.root().append("algorithms").append(0),
            Map.of( "name", "anchor" )
        );
        final ConsistentHashFactory factory = ConsistentHashFactoryLoader.getInstance().load( "anchor", algorithm );

        final RemovalOrderDisagreement executor = new RemovalOrderDisagreement( benchmark );
        assertDoesNotThrow( () -> executor.execute( new ArrayList<>(List.of(factory)) ) );

        final Path results = outputFolder.resolve( "results" ).resolve( "removalorderdisagreement.csv" );
        assertTrue( Files.exists(results) );

        final List<String> lines = Files.readAllLines( results );
        assertEquals( "HashFunction,Algorithm,Nodes,RemovedNodes,RecordCount,RemovalRate,WindowDistance,Index,Mismatches,FailureRate", lines.get(0) );
        assertEquals( 4, lines.size() );
        for( int i = 1; i < lines.size(); ++i )
        {

            final String[] values = lines.get(i).split( "," );
            assertEquals( "XX", values[0] );
            assertEquals( "anchor", values[1] );
            assertEquals( "8", values[2] );
            assertEquals( "4", values[3] );
            assertEquals( "20", values[4] );

        }

    }

}
