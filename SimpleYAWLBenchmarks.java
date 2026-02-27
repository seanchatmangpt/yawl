import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(value = 1)
@State(Scope.Benchmark)
public class SimpleYAWLBenchmarks {

    @Benchmark
    public void testVirtualThreadCreation(Blackhole bh) throws InterruptedException {
        Thread.ofVirtual().name("benchmark-" + Thread.currentThread().getId())
               .start(() -> bh.consume("test"))
               .join();
    }

    @Benchmark
    public void testSequentialOperations(Blackhole bh) {
        for (int i = 0; i < 1000; i++) {
            bh.consume(i * 2);
        }
    }

    @Benchmark
    public void testConcurrentOperations(Blackhole bh) throws InterruptedException {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                for (int j = 0; j < 100; j++) {
                    bh.consume(j);
                }
            });
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}