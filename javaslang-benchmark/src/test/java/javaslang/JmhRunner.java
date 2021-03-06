package javaslang;

import javaslang.collection.*;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class JmhRunner {
    /**
     * Runs all the available benchmarks in precision mode.
     * Note: it takes about 3 hours.
     */
    public static void main(String[] args) {
        JmhRunner.runSlowNoAsserts(Array.of(
                ArrayBenchmark.class,
                BitSetBenchmark.class,
                CharSeqBenchmark.class,
                HashSetBenchmark.class,
                ListBenchmark.class,
                PriorityQueueBenchmark.class,
                VectorBenchmark.class
        ));
    }

    /** enables debugging and assertions for benchmarks and production code - the speed results will be totally unreliable */
    public static void runDebugWithAsserts(Array<Class<?>> groups) {
        final Array<String> classNames = groups.map(Class::getCanonicalName);
        run(classNames, 0, 1, 1, 0, VerboseMode.SILENT, Assertions.Enable);

        MemoryUsage.printAndReset();
    }

    @SuppressWarnings("unused")
    public static void runQuickNoAsserts(Array<Class<?>> groups) {
        runAndReport(groups, 10, 10, 10, 1, VerboseMode.NORMAL, Assertions.Disable);
    }

    @SuppressWarnings("unused")
    public static void runNormalNoAsserts(Array<Class<?>> groups) {
        runAndReport(groups, 15, 10, 100, 1, VerboseMode.NORMAL, Assertions.Disable);
    }

    @SuppressWarnings("unused")
    public static void runSlowNoAsserts(Array<Class<?>> groups) {
        runAndReport(groups, 15, 15, 300, 1, VerboseMode.EXTRA, Assertions.Disable);
    }

    public static void runAndReport(Array<Class<?>> groups, int warmupIterations, int measurementIterations, int millis, int forks, VerboseMode silent, Assertions assertions) {
        final Array<String> classNames = groups.map(Class::getCanonicalName);
        final Array<RunResult> results = run(classNames, warmupIterations, measurementIterations, millis, forks, silent, assertions);
        BenchmarkPerformanceReporter.of(classNames, results).print();

        MemoryUsage.printAndReset();
    }

    private static Array<RunResult> run(Array<String> classNames, int warmupIterations, int measurementIterations, int millis, int forks, VerboseMode verboseMode, Assertions assertions) {
        final ChainedOptionsBuilder builder = new OptionsBuilder()
                .shouldDoGC(true)
                .verbosity(verboseMode)
                .shouldFailOnError(true)
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupTime(TimeValue.milliseconds(millis))
                .warmupIterations(warmupIterations)
                .measurementTime(TimeValue.milliseconds(millis))
                .measurementIterations(measurementIterations)
                .forks(forks)
                // We are using 2Gb and setting NewGen to 100% to avoid GC during testing.
                // Any GC during testing will destroy the iteration (i.e. introduce unreliable noise in the measurement), which should get ignored as an outlier
                .jvmArgsAppend("-XX:+UseG1GC", "-Xss100m", "-Xms2g", "-Xmx2g", "-XX:MaxGCPauseMillis=1000", "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=100", "-XX:G1MaxNewSizePercent=100", assertions.vmArg);

        classNames.forEach(builder::include);

        try {
            return Array.ofAll(new Runner(builder.build()).run());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public enum Assertions {
        Enable("-enableassertions"),
        Disable("-disableassertions");

        final String vmArg;

        Assertions(String vmArg) {
            this.vmArg = vmArg;
        }
    }

    public static Integer[] getRandomValues(int size, int seed) {
        return getRandomValues(size, seed, false);
    }

    public static Integer[] getRandomValues(int size, int seed, boolean nonNegative) {
        final Random random = new Random(seed);

        final Integer[] results = new Integer[size];
        for (int i = 0; i < size; i++) {
            final int value = random.nextInt(size) - (nonNegative ? 0 : (size / 2));
            results[i] = value;
        }
        return results;
    }

    /** used for dead code elimination and correctness assertion inside the benchmarks */
    public static int aggregate(int x, int y) {
        return x ^ y;
    }

    /** simplifies construction of immutable collections - with assertion and memory benchmarking */
    public static <T extends Collection<?>, R> R create(Function1<T, R> function, T source, Function1<R, Boolean> assertion) {
        return create(function, source, source.size(), assertion);
    }

    @SuppressWarnings("unchecked")
    public static <T, R> R create(Function1<T, R> function, T source, int elementCount, Function1<R, Boolean> assertion) {
        final R result = function.apply(source);
        assert assertion.apply(result);

        MemoryUsage.storeMemoryUsages(source, elementCount, result);

        return result;
    }
}
