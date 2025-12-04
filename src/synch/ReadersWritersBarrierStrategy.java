package synch;

import java.awt.Color;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

public class ReadersWritersBarrierStrategy implements ReadersWritersStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition okToRead = lock.newCondition();
    private final Condition okToWrite = lock.newCondition();
    private volatile CyclicBarrier cycleBarrier;

    private int readersActive;
    private boolean writerActive;
    private int writersWaiting;

    public ReadersWritersBarrierStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        readersActive = 0;
        writerActive = false;
        writersWaiting = 0;
        panel.readersActive = 0;
        panel.writerActive = false;
        panel.readersWaiting = 0;
        panel.writersWaiting = 0;
        cycleBarrier = new CyclicBarrier(2);

        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "RW-Barrier-Actor");
            t.setDaemon(true);
            return t;
        });

        spawner = new Thread(this::runSpawner, "RW-Barrier-Spawner");
        spawner.setDaemon(true);
        spawner.start();
    }

    @Override
    public void stop() {
        if (spawner != null) {
            spawner.interrupt();
        }
        if (exec != null) {
            exec.shutdownNow();
        }
        lock.lock();
        try {
            okToRead.signalAll();
            okToWrite.signalAll();
        } finally {
            lock.unlock();
        }
        CyclicBarrier barrier = cycleBarrier;
        cycleBarrier = null;
        if (barrier != null) {
            barrier.reset();
        }
    }

    private void runSpawner() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                if (panel.actors.size() < 20) {
                    spawn(Math.random() < 0.7 ? Role.READER : Role.WRITER);
                }
                if (!sleepRand(400, 1000)) {
                    break;
                }
                awaitSpawnerBarrier();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitSpawnerBarrier() throws InterruptedException {
        CyclicBarrier barrier = cycleBarrier;
        if (barrier == null) {
            return;
        }
        try {
            barrier.await();
        } catch (BrokenBarrierException e) {
            resetCycleBarrier(barrier);
        }
    }

    private void spawn(Role role) {
        Actor actor = new Actor();
        actor.role = role;
        actor.color = (role == Role.READER) ? new Color(90, 160, 255) : new Color(230, 90, 90);
        actor.id = panel.getNextActorId();
        actor.x = (role == Role.READER) ? panel.getWidth() + 40 : -40;
        actor.y = panel.getHeight() * 0.75 + (Math.random() * 40 - 20);
        actor.tx = (role == Role.READER) ? panel.getWidth() - 80 : 80;
        actor.ty = actor.y;
        if (!panel.tryAddActor(actor)) {
            return;
        }
    }

    @Override
    public void requestAccess(Actor actor) {
        if (exec == null || actor == null) {
            return;
        }
        exec.submit(() -> handleActor(actor));
    }

    private void handleActor(Actor actor) {
        if (actor.role == Role.READER) {
            try {
                processReader(actor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphReaderFinishedBarrier(actor.id);
                sleepVisualization();
            }
        } else {
            try {
                processWriter(actor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphWriterFinishedBarrier(actor.id);
                sleepVisualization();
            }
        }
    }

    private void processReader(Actor actor) throws InterruptedException {
        panel.updateGraphReaderRequestingBarrierLock(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            while (writerActive || writersWaiting > 0) {
                panel.readersWaiting++;
                try {
                    panel.updateGraphReaderWaitingBarrierLock(actor.id);
                    ensure(sleepVisualization());
                    okToRead.await();
                } finally {
                    panel.readersWaiting = Math.max(0, panel.readersWaiting - 1);
                }
            }
            panel.updateGraphReaderHoldingBarrierLock(actor.id);
            ensure(sleepVisualization());

            readersActive++;
            panel.readersActive = readersActive;

            panel.updateGraphReaderReleasingBarrierLock(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        actor.state = ReadersWritersSim.AState.READING;
        actor.tx = panel.docCenter().x + (Math.random() * 80 - 40);
        actor.ty = panel.docCenter().y + (Math.random() * 80 - 40);
        panel.updateGraphReaderUsingDocumentBarrier(actor.id);
        ensure(sleepVisualization());
        ensure(sleepRand(800, 1500));

        actor.state = ReadersWritersSim.AState.LEAVING;
        actor.tx = panel.getWidth() + 40;
        actor.ty = actor.y;

        panel.updateGraphReaderRequestingBarrierLock(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            panel.updateGraphReaderHoldingBarrierLock(actor.id);
            ensure(sleepVisualization());

            readersActive = Math.max(0, readersActive - 1);
            panel.readersActive = readersActive;

            if (readersActive == 0) {
                if (writersWaiting > 0) {
                    okToWrite.signal();
                } else {
                    okToRead.signalAll();
                }
            }

            panel.updateGraphReaderReleasingBarrierLock(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        waitAtBarrier(actor.id, true);
    }

    private void processWriter(Actor actor) throws InterruptedException {
        panel.updateGraphWriterRequestingBarrierLock(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            while (readersActive > 0 || writerActive) {
                writersWaiting++;
                panel.writersWaiting = writersWaiting;
                try {
                    panel.updateGraphWriterWaitingBarrierLock(actor.id);
                    ensure(sleepVisualization());
                    okToWrite.await();
                } finally {
                    writersWaiting = Math.max(0, writersWaiting - 1);
                    panel.writersWaiting = writersWaiting;
                }
            }
            panel.updateGraphWriterHoldingBarrierLock(actor.id);
            ensure(sleepVisualization());

            writerActive = true;
            panel.writerActive = true;

            panel.updateGraphWriterReleasingBarrierLock(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        actor.state = ReadersWritersSim.AState.WRITING;
        actor.tx = panel.docCenter().x;
        actor.ty = panel.docCenter().y;
        panel.updateGraphWriterUsingDocumentBarrier(actor.id);
        ensure(sleepVisualization());
        ensure(sleepRand(1000, 1800));

        actor.state = ReadersWritersSim.AState.LEAVING;
        actor.tx = -40;
        actor.ty = actor.y;

        panel.updateGraphWriterRequestingBarrierLock(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            panel.updateGraphWriterHoldingBarrierLock(actor.id);
            ensure(sleepVisualization());

            writerActive = false;
            panel.writerActive = false;

            if (writersWaiting > 0) {
                okToWrite.signal();
            } else {
                okToRead.signalAll();
            }

            panel.updateGraphWriterReleasingBarrierLock(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        waitAtBarrier(actor.id, false);
    }

    private void waitAtBarrier(int actorId, boolean isReader) throws InterruptedException {
        if (isReader) {
            panel.updateGraphReaderWaitingBarrierGate(actorId);
        } else {
            panel.updateGraphWriterWaitingBarrierGate(actorId);
        }
        ensure(sleepVisualization());

        CyclicBarrier barrier = cycleBarrier;
        if (barrier == null) {
            if (isReader) {
                panel.updateGraphReaderCrossingBarrierGate(actorId);
            } else {
                panel.updateGraphWriterCrossingBarrierGate(actorId);
            }
            ensure(sleepVisualization());
            return;
        }

        try {
            barrier.await();
            if (isReader) {
                panel.updateGraphReaderCrossingBarrierGate(actorId);
            } else {
                panel.updateGraphWriterCrossingBarrierGate(actorId);
            }
            ensure(sleepVisualization());
        } catch (BrokenBarrierException e) {
            resetCycleBarrier(barrier);
            if (isReader) {
                panel.updateGraphReaderCrossingBarrierGate(actorId);
            } else {
                panel.updateGraphWriterCrossingBarrierGate(actorId);
            }
            ensure(sleepVisualization());
        }
    }

    private void resetCycleBarrier(CyclicBarrier broken) {
        synchronized (this) {
            if (cycleBarrier == broken) {
                cycleBarrier = new CyclicBarrier(2);
            }
        }
    }

    private void ensure(boolean keepRunning) throws InterruptedException {
        if (!keepRunning) {
            throw new InterruptedException("Visualization interrupted");
        }
    }

    private boolean sleepVisualization() {
        if (!panel.running.get()) {
            return false;
        }
        try {
            Thread.sleep(VISUALIZATION_DELAY);
            return panel.running.get() && !Thread.currentThread().isInterrupted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean sleepRand(int min, int max) {
        if (!panel.running.get()) {
            return false;
        }
        int span = Math.max(0, max - min);
        int duration = min + rnd(span + 1);
        try {
            Thread.sleep(duration);
            return panel.running.get() && !Thread.currentThread().isInterrupted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private int rnd(int bound) {
        if (bound <= 0) {
            return 0;
        }
        return (int) (Math.random() * bound);
    }
}
