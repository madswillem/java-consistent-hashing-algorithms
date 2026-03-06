package ch.supsi.dti.isin.benchmark.adapter.consistenthash.mads;


import ch.supsi.dti.isin.benchmark.adapter.BucketBasedEnginePilot;
import ch.supsi.dti.isin.consistenthash.mads.MadsEngine;

/**
 * Implementation of the {@link ConsistentHashEnginePilot} interface for the {@code Mads} algorithm.
 *
 * @author Samuel De Babo Martins
 * @author Massimo Coluzzi
 */
public class MadsEnginePilot extends BucketBasedEnginePilot
{

    /**
     * Constructor with parameters.
     *
     * @param engine the bucket-based engine to pilot
     */
    public MadsEnginePilot( MadsEngine engine )
    {

        super( engine );

    }

}
