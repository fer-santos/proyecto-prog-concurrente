package synch;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

public class ReadersWritersMutexStrategy implements ReadersWritersStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;
    private ReentrantLock mutex;

    public ReadersWritersMutexStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mutex = new ReentrantLock(true);
        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "RW-Mutex-Actor");
            t.setDaemon(true);
            return t;
        });

        spawner = new Thread(this::runSpawner, "RW-Mutex-Spawner");
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
            // No-op cleanup; executor shutdown handled in stop()
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
        if (exec == null) {
            return;
        }
        exec.submit(() -> handleActor(actor));
    }

    private void handleActor(Actor actor) {
        if (actor == null) {
            return;
        }
        if (actor.role == Role.READER) {
            handleReader(actor);
        } else {
            handleWriter(actor);
        }
    }

    private void handleReader(Actor actor) {
        try {
            processReader(actor);
        } finally {
            panel.updateGraphReaderFinishedMutex(actor.id);
            sleepVisualization();
        }
    }

    private void processReader(Actor actor) {
        boolean waitingIncremented = false;
        boolean activeIncremented = false;
        boolean locked = false;
        try {
            panel.readersWaiting++;
            waitingIncremented = true;
            panel.updateGraphReaderRequestingLock(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            mutex.lockInterruptibly();
            locked = true;

            panel.readersWaiting--;
            waitingIncremented = false;
            panel.readersActive++;
            activeIncremented = true;

            panel.updateGraphReaderHoldingLock(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            actor.state = ReadersWritersSim.AState.READING;
            actor.tx = panel.docCenter().x + (Math.random() * 80 - 40);
            actor.ty = panel.docCenter().y + (Math.random() * 80 - 40);
            if (!sleepRand(800, 1500)) {
                return;
            }

            actor.state = ReadersWritersSim.AState.LEAVING;
            actor.tx = panel.getWidth() + 40;
            actor.ty = actor.y;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                if (activeIncremented) {
                    panel.readersActive--;
                    activeIncremented = false;
                }
                panel.updateGraphReaderReleasingLock(actor.id);
                mutex.unlock();
                sleepVisualization();
            }
            if (waitingIncremented) {
                panel.readersWaiting--;
            }
        }
    }

    private void handleWriter(Actor actor) {
        try {
            processWriter(actor);
        } finally {
            panel.updateGraphWriterFinishedMutex(actor.id);
            sleepVisualization();
        }
    }

    private void processWriter(Actor actor) {
        boolean waitingIncremented = false;
        boolean writerActiveSet = false;
        boolean locked = false;
        try {
            panel.writersWaiting++;
            waitingIncremented = true;
            panel.updateGraphWriterRequestingLock(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            mutex.lockInterruptibly();
            locked = true;

            panel.writersWaiting--;
            waitingIncremented = false;
            panel.writerActive = true;
            writerActiveSet = true;

            panel.updateGraphWriterHoldingLock(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            actor.state = ReadersWritersSim.AState.WRITING;
            actor.tx = panel.docCenter().x;
            actor.ty = panel.docCenter().y;
            if (!sleepRand(1000, 1800)) {
                return;
            }

            actor.state = ReadersWritersSim.AState.LEAVING;
            actor.tx = -40;
            actor.ty = actor.y;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                if (writerActiveSet) {
                    panel.writerActive = false;
                    writerActiveSet = false;
                }
                panel.updateGraphWriterReleasingLock(actor.id);
                mutex.unlock();
                sleepVisualization();
            }
            if (waitingIncremented) {
                panel.writersWaiting--;
            }
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