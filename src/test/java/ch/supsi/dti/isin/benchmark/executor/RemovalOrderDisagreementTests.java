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
import ch.supsi.dti.isin.benchmark.config.Config;
import ch.supsi.dti.isin.benchmark.config.ConfigLoader;
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
        final Config config = ConfigLoader.of(
            "common:\n" +
            "  gc: false\n" +
            "  output-folder: " + outputFolder + "\n" +
            "  init-nodes: [8]\n" +
            "  hash-functions: [xx]\n" +
            "algorithms:\n" +
            "  - name: anchor\n" +
            "benchmarks:\n" +
            "  - name: removal-order-disagreement\n" +
            "    args:\n" +
            "      removal-rate: [0.5]\n" +
            "      window-distance: [0.5]\n" +
            "      record-count: 20\n" +
            "      seed: 1\n"
        );
        final BenchmarkConfig benchmark = config.getBenchmarks().get( 0 );
        final AlgorithmConfig algorithm = config.getAlgorithms().get( 0 );
        final ConsistentHashFactory factory = ConsistentHashFactoryLoader.getInstance().load( "anchor", algorithm );

        final RemovalOrderDisagreement executor = new RemovalOrderDisagreement( benchmark );
        assertDoesNotThrow( () -> executor.execute( new ArrayList<>(List.of(factory)) ) );

        final Path results = outputFolder.resolve( "results" ).resolve( "removalorderdisagreement.csv" );
        assertTrue( Files.exists(results) );

        final List<String> lines = Files.readAllLines( results );
        assertTrue( lines.size() > 1 );
        assertTrue( lines.get(0).contains("Benchmark") );
        assertTrue( lines.get(0).contains("Param: removalRate") );
        assertTrue( lines.get(0).contains("Param: windowDistance") );

    }

}
