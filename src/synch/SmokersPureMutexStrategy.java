package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

public class SmokersPureMutexStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];
    private final ReentrantLock mutex = new ReentrantLock(true);

    public SmokersPureMutexStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        agentThread = new Thread(this::runAgent, "AgentPureMutex");
        agentThread.setDaemon(true);
        agentThread.start();

        for (int id = 0; id < smokerThreads.length; id++) {
            final int smokerId = id;
            Thread t = new Thread(() -> runSmoker(smokerId), "SmokerPureMutex-" + smokerId);
            t.setDaemon(true);
            smokerThreads[id] = t;
            t.start();
        }
    }

    @Override
    public void stop() {
        if (agentThread != null) {
            agentThread.interrupt();
        }
        for (Thread smokerThread : smokerThreads) {
            if (smokerThread != null) {
                smokerThread.interrupt();
            }
        }
    }

    private void runAgent() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphAgentRequestingLock();
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                try {
                    mutex.lockInterruptibly();
                    locked = true;

                    Ing ing1 = panel.i1;
                    Ing ing2 = panel.i2;
                    if (ing1 == null && ing2 == null) {
                        Ing[] pair = pickRandomPair();
                        ing1 = pair[0];
                        ing2 = pair[1];
                        panel.i1 = ing1;
                        panel.i2 = ing2;
                        panel.activeSmoker = -1;
                    }

                    panel.updateGraphAgentHoldingLock(ing1, ing2);
                    if (!sleepVisualization()) {
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (locked) {
                        panel.updateGraphAgentReleasingLock();
                        mutex.unlock();
                        locked = false;
                        if (!sleepVisualization()) {
                            break;
                        }
                    }
                }

                if (!sleepRand(600, 1100)) {
                    break;
                }
            }
        } finally {
            panel.updateGraphAgentReleasingLock();
        }
    }

    private void runSmoker(int smokerId) {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphSmokerRequestingLock(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                boolean didSmoke = false;
                try {
                    mutex.lockInterruptibly();
                    locked = true;

                    panel.updateGraphSmokerHoldingLock(smokerId);
                    if (!sleepVisualization()) {
                        break;
                    }

                    if (canSmokeNow(smokerId)) {
                        panel.i1 = null;
                        panel.i2 = null;
                        panel.activeSmoker = smokerId;
                        panel.sstate[smokerId] = SState.ARMANDO;
                        didSmoke = true;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (locked) {
                        panel.updateGraphSmokerReleasingLock(smokerId);
                        mutex.unlock();
                        locked = false;
                        if (!sleepVisualization()) {
                            break;
                        }
                    }
                }

                if (didSmoke) {
                    if (!sleepRand(500, 900)) {
                        break;
                    }
                    panel.sstate[smokerId] = SState.FUMANDO;
                    if (!sleepRand(800, 1400)) {
                        break;
                    }

                    boolean finishLocked = false;
                    try {
                        mutex.lockInterruptibly();
                        finishLocked = true;
                        panel.sstate[smokerId] = SState.ESPERANDO;
                        panel.activeSmoker = -1;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        if (finishLocked) {
                            mutex.unlock();
                        }
                    }
                } else {
                    if (!sleepRand(100, 250)) {
                        break;
                    }
                }
            }
        } finally {
            panel.sstate[smokerId] = SState.ESPERANDO;
        }
    }

    private boolean canSmokeNow(int smokerId) {
        return (smokerId == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS))
                || (smokerId == 1 && pairIs(Ing.TABACO, Ing.CERILLOS))
                || (smokerId == 2 && pairIs(Ing.TABACO, Ing.PAPEL));
    }

    private boolean pairIs(Ing a, Ing b) {
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }

    private Ing[] pickRandomPair() {
        int pick = rnd(3);
        switch (pick) {
            case 0:
                return new Ing[]{Ing.PAPEL, Ing.CERILLOS};
            case 1:
                return new Ing[]{Ing.TABACO, Ing.CERILLOS};
            default:
                return new Ing[]{Ing.TABACO, Ing.PAPEL};
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
        int duration = min + rnd(span);
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