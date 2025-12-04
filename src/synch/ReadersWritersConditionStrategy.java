package synch;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

public class ReadersWritersConditionStrategy implements ReadersWritersStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition okToRead = lock.newCondition();
    private final Condition okToWrite = lock.newCondition();

    private int readersActive;
    private boolean writerActive;
    private int writersWaiting;
    private boolean preferWriter;

    public ReadersWritersConditionStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        readersActive = 0;
        writerActive = false;
        writersWaiting = 0;
        preferWriter = false;
        panel.readersWaiting = 0;
        panel.writersWaiting = 0;
        panel.readersActive = 0;
        panel.writerActive = false;

        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "RW-Cond-Actor");
            t.setDaemon(true);
            return t;
        });

        spawner = new Thread(this::runSpawner, "RW-Cond-Spawner");
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
            }
        } finally {
            // Executor cleanup handled in stop()
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
            handleReader(actor);
        } else {
            handleWriter(actor);
        }
    }

    private void handleReader(Actor actor) {
        try {
            processReader(actor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            panel.updateGraphReaderFinishedCondition(actor.id);
            sleepVisualization();
        }
    }

    private void processReader(Actor actor) throws InterruptedException {
        panel.updateGraphReaderRequestingLockCondition(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            panel.updateGraphReaderHoldingLockCondition(actor.id);
            ensure(sleepVisualization());

            while (writerActive || (preferWriter && writersWaiting > 0)) {
                panel.readersWaiting++;
                try {
                    panel.updateGraphReaderWaitingCondition(actor.id);
                    ensure(sleepVisualization());
                    okToRead.await();
                    panel.updateGraphReaderSignaledCondition(actor.id);
                    ensure(sleepVisualization());
                } finally {
                    panel.readersWaiting = Math.max(0, panel.readersWaiting - 1);
                }
            }

            readersActive++;
            panel.readersActive = readersActive;

            panel.updateGraphReaderReleasingLockCondition(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        actor.state = ReadersWritersSim.AState.READING;
        actor.tx = panel.docCenter().x + (Math.random() * 80 - 40);
        actor.ty = panel.docCenter().y + (Math.random() * 80 - 40);
        panel.updateGraphReaderUsingDocumentCondition(actor.id);
        ensure(sleepVisualization());
        ensure(sleepRand(800, 1500));

        actor.state = ReadersWritersSim.AState.LEAVING;
        actor.tx = panel.getWidth() + 40;
        actor.ty = actor.y;

        panel.updateGraphReaderRequestingLockCondition(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            panel.updateGraphReaderHoldingLockCondition(actor.id);
            ensure(sleepVisualization());

            readersActive = Math.max(0, readersActive - 1);
            panel.readersActive = readersActive;

            if (readersActive == 0) {
                preferWriter = true;
                if (writersWaiting > 0) {
                    panel.updateGraphReaderSignalingWriterCondition(actor.id);
                    ensure(sleepVisualization());
                    okToWrite.signal();
                } else {
                    panel.updateGraphReaderSignalingReadersCondition(actor.id);
                    ensure(sleepVisualization());
                    okToRead.signalAll();
                }
            }

            panel.updateGraphReaderReleasingLockCondition(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void handleWriter(Actor actor) {
        try {
            processWriter(actor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            panel.updateGraphWriterFinishedCondition(actor.id);
            sleepVisualization();
        }
    }

    private void processWriter(Actor actor) throws InterruptedException {
        panel.updateGraphWriterRequestingLockCondition(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            panel.updateGraphWriterHoldingLockCondition(actor.id);
            ensure(sleepVisualization());

            while (readersActive > 0 || writerActive) {
                writersWaiting++;
                panel.writersWaiting = writersWaiting;
                preferWriter = true;
                try {
                    panel.updateGraphWriterWaitingCondition(actor.id);
                    ensure(sleepVisualization());
                    okToWrite.await();
                    panel.updateGraphWriterSignaledCondition(actor.id);
                    ensure(sleepVisualization());
                } finally {
                    writersWaiting = Math.max(0, writersWaiting - 1);
                    panel.writersWaiting = writersWaiting;
                }
            }

            writerActive = true;
            panel.writerActive = true;

            panel.updateGraphWriterReleasingLockCondition(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        actor.state = ReadersWritersSim.AState.WRITING;
        actor.tx = panel.docCenter().x;
        actor.ty = panel.docCenter().y;
        panel.updateGraphWriterUsingDocumentCondition(actor.id);
        ensure(sleepVisualization());
        ensure(sleepRand(1000, 1800));

        actor.state = ReadersWritersSim.AState.LEAVING;
        actor.tx = -40;
        actor.ty = actor.y;

        panel.updateGraphWriterRequestingLockCondition(actor.id);
        ensure(sleepVisualization());

        lock.lockInterruptibly();
        try {
            panel.updateGraphWriterHoldingLockCondition(actor.id);
            ensure(sleepVisualization());

            writerActive = false;
            panel.writerActive = false;
            preferWriter = false;

            if (writersWaiting > 0) {
                panel.updateGraphWriterSignalingWriterCondition(actor.id);
                ensure(sleepVisualization());
                okToWrite.signal();
            } else {
                panel.updateGraphWriterSignalingReadersCondition(actor.id);
                ensure(sleepVisualization());
                okToRead.signalAll();
            }

            panel.updateGraphWriterReleasingLockCondition(actor.id);
            ensure(sleepVisualization());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
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
