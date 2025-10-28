package synch;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

public class ReadersWritersSemaphoreStrategy implements ReadersWritersStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;
    private Semaphore rwMutex;
    private ReentrantLock rcountMutex;
    private volatile boolean readersSemaphoreHeld;

    public ReadersWritersSemaphoreStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        rwMutex = new Semaphore(1, true);
        rcountMutex = new ReentrantLock(true);
    readersSemaphoreHeld = false;
        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "RW-Sem-Actor");
            t.setDaemon(true);
            return t;
        });

        spawner = new Thread(this::runSpawner, "RW-Sem-Spawner");
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
        readersSemaphoreHeld = false;
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
            // executor cleanup handled on stop
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
        panel.actors.add(actor);
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
        } finally {
            panel.updateGraphReaderFinishedSemaphore(actor.id);
            sleepVisualization();
        }
    }

    private void processReader(Actor actor) {
        boolean waitingIncremented = false;
        boolean countLocked = false;
        boolean rwHeld = false;
        boolean activeIncremented = false;
        try {
            panel.readersWaiting++;
            waitingIncremented = true;
            panel.updateGraphReaderRequestingCountSemaphore(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            rcountMutex.lockInterruptibly();
            countLocked = true;

            panel.updateGraphReaderHoldingCountSemaphore(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            if (panel.readersActive == 0) {
                panel.updateGraphReaderRequestingRwSemaphore(actor.id);
                if (!sleepVisualization()) {
                    return;
                }
                rwMutex.acquire();
                rwHeld = true;
                readersSemaphoreHeld = true;
                panel.updateGraphReaderHoldingRwSemaphore(actor.id);
                if (!sleepVisualization()) {
                    return;
                }
            }

            panel.readersActive++;
            activeIncremented = true;
            panel.readersWaiting--;
            waitingIncremented = false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } finally {
            if (countLocked) {
                panel.updateGraphReaderReleasingCountSemaphore(actor.id);
                rcountMutex.unlock();
                countLocked = false;
                if (!sleepVisualization()) {
                    if (rwHeld) {
                        readersSemaphoreHeld = false;
                        rwMutex.release();
                        panel.updateGraphReaderReleasingRwSemaphore(actor.id);
                        rwHeld = false;
                    }
                    return;
                }
            }
        }

        try {
            actor.state = ReadersWritersSim.AState.READING;
            actor.tx = panel.docCenter().x + (Math.random() * 80 - 40);
            actor.ty = panel.docCenter().y + (Math.random() * 80 - 40);
            panel.updateGraphReaderUsingDocumentSemaphore(actor.id);
            if (!sleepVisualization()) {
                return;
            }
            if (!sleepRand(800, 1500)) {
                return;
            }

            actor.state = ReadersWritersSim.AState.LEAVING;
            actor.tx = panel.getWidth() + 40;
        } finally {
            boolean finishCountLocked = false;
            try {
                panel.updateGraphReaderRequestingCountSemaphore(actor.id);
                if (!sleepVisualization()) {
                    return;
                }
                rcountMutex.lockInterruptibly();
                finishCountLocked = true;
                panel.updateGraphReaderHoldingCountSemaphore(actor.id);
                if (!sleepVisualization()) {
                    return;
                }
                if (activeIncremented) {
                    panel.readersActive--;
                    activeIncremented = false;
                }
                if (panel.readersActive == 0 && readersSemaphoreHeld) {
                    readersSemaphoreHeld = false;
                    panel.updateGraphReaderReleasingRwSemaphore(actor.id);
                    rwMutex.release();
                    rwHeld = false;
                    if (!sleepVisualization()) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (finishCountLocked) {
                    panel.updateGraphReaderReleasingCountSemaphore(actor.id);
                    rcountMutex.unlock();
                    if (!sleepVisualization()) {
                        return;
                    }
                }
            }
        }
    }

    private void handleWriter(Actor actor) {
        boolean waitingIncremented = false;
        boolean rwHeld = false;
        boolean writerActiveSet = false;
        try {
            panel.writersWaiting++;
            waitingIncremented = true;
            panel.updateGraphWriterRequestingSemaphore(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            rwMutex.acquire();
            rwHeld = true;
            panel.writersWaiting--;
            waitingIncremented = false;
            panel.updateGraphWriterHoldingSemaphore(actor.id);
            if (!sleepVisualization()) {
                return;
            }

            panel.writerActive = true;
            writerActiveSet = true;
            actor.state = ReadersWritersSim.AState.WRITING;
            actor.tx = panel.docCenter().x;
            actor.ty = panel.docCenter().y;
            panel.updateGraphWriterUsingDocumentSemaphore(actor.id);
            if (!sleepVisualization()) {
                return;
            }
            if (!sleepRand(1000, 1800)) {
                return;
            }

            actor.state = ReadersWritersSim.AState.LEAVING;
            actor.tx = -40;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (writerActiveSet) {
                panel.writerActive = false;
                writerActiveSet = false;
            }
            if (rwHeld) {
                panel.updateGraphWriterReleasingSemaphore(actor.id);
                rwMutex.release();
                rwHeld = false;
                sleepVisualization();
            }
            if (waitingIncremented) {
                panel.writersWaiting--;
            }
            panel.updateGraphWriterFinishedSemaphore(actor.id);
            sleepVisualization();
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