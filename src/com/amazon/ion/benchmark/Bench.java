package com.amazon.ion.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * JMH benchmark for a single options combination.
 */
@State(Scope.Benchmark)
public class Bench {

    /**
     * Name of the input file.
     */
    @Param({""})
    private String input;

    /**
     * Serialized options combination.
     */
    @Param({"{}"})
    private String options;

    MeasurableTask measurableTask = null;
    Callable<Void> taskToMeasure = null;

    @Setup(Level.Trial)
    public void setUpTrial() throws Exception {
        OptionsCombinationBase optionsCombination = OptionsCombinationBase.from(options);
        measurableTask = optionsCombination.createMeasurableTask(input);
        measurableTask.setUpTrial();
        taskToMeasure = measurableTask.getTask();
    }

    @Setup(Level.Iteration)
    public void setUpIteration() throws IOException {
        measurableTask.setUpIteration();
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() throws IOException {
        measurableTask.tearDownIteration();
    }

    @Benchmark
    public void run() throws Exception {
        taskToMeasure.call();
    }
}
