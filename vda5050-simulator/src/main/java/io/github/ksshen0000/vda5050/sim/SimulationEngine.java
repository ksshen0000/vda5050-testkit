package io.github.ksshen0000.vda5050.sim;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advances all simulated vehicles on one thread with a fixed step. Simulated time per step is
 * {@code tickMillis * timeScale}, so a time scale above 1 runs the simulation faster than real time.
 */
public final class SimulationEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final List<SimulatedAgv> agvs = new CopyOnWriteArrayList<>();
    private final long tickMillis;
    private volatile double timeScale;
    private ScheduledExecutorService executor;

    public SimulationEngine(long tickMillis, double timeScale) {
        this.tickMillis = tickMillis;
        this.timeScale = timeScale;
    }

    public void add(SimulatedAgv agv) {
        agvs.add(agv);
    }

    public Optional<SimulatedAgv> find(String serialNumber) {
        return agvs.stream().filter(a -> a.vehicle().serialNumber().equals(serialNumber)).findFirst();
    }

    public List<SimulatedAgv> agvs() {
        return List.copyOf(agvs);
    }

    public boolean remove(String serialNumber) {
        Optional<SimulatedAgv> agv = find(serialNumber);
        agv.ifPresent(a -> {
            agvs.remove(a);
            a.disconnect();
        });
        return agv.isPresent();
    }

    /** Runs {@code action} on the simulation thread, e.g. to change a vehicle safely. */
    public void execute(Consumer<SimulationEngine> action) {
        if (executor == null) {
            action.accept(this);
        } else {
            executor.execute(() -> action.accept(this));
        }
    }

    /**
     * Runs {@code query} on the simulation thread and waits for the result, so callers on other threads
     * see a consistent snapshot.
     */
    public <T> T query(java.util.function.Function<SimulationEngine, T> query) {
        if (executor == null) {
            return query.apply(this);
        }
        try {
            return executor.submit(() -> query.apply(this)).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("simulation query failed", e);
        }
    }

    public void setTimeScale(double timeScale) {
        if (timeScale <= 0) {
            throw new IllegalArgumentException("time scale must be positive");
        }
        this.timeScale = timeScale;
    }

    public double timeScale() {
        return timeScale;
    }

    /** Advances every vehicle by {@code dt} simulated seconds. */
    public void step(double dt) {
        for (SimulatedAgv agv : agvs) {
            try {
                agv.step(dt);
            } catch (RuntimeException e) {
                log.error("simulation step failed for {}", agv.vehicle().serialNumber(), e);
            }
        }
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().name("simulation").unstarted(r));
        executor.scheduleAtFixedRate(() -> step(tickMillis / 1000.0 * timeScale), tickMillis, tickMillis,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        agvs.forEach(SimulatedAgv::disconnect);
    }
}
