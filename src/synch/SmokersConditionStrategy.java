package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

public class SmokersConditionStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];

    private ReentrantLock monitorLock;
    private Condition tableEmpty;
    private Condition canSmoke;

    public SmokersConditionStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        monitorLock = new ReentrantLock(true);
        tableEmpty = monitorLock.newCondition();
        canSmoke = monitorLock.newCondition();

        agentThread = new Thread(this::runAgent, "Agent-Cond");
        agentThread.setDaemon(true);
        agentThread.start();

        for (int id = 0; id < smokerThreads.length; id++) {
            final int smokerId = id;
            Thread t = new Thread(() -> runSmoker(smokerId), "Smoker-Cond-" + smokerId);
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
        if (monitorLock != null) {
            monitorLock.lock();
            try {
                tableEmpty.signalAll();
                canSmoke.signalAll();
            } finally {
                monitorLock.unlock();
            }
        }
    }

    private void runAgent() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphAgentRequestingCondition();
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                try {
                    monitorLock.lockInterruptibly();
                    locked = true;

                    while (panel.i1 != null && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.updateGraphAgentIdleCondition();
                        if (!sleepVisualization()) {
                            locked = releaseLockSafely(locked);
                            return;
                        }
                        tableEmpty.await();
                        panel.updateGraphAgentRequestingCondition();
                        if (!sleepVisualization()) {
                            locked = releaseLockSafely(locked);
                            return;
                        }
                    }

                    if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                        locked = releaseLockSafely(locked);
                        break;
                    }

                    Ing[] pair = pickRandomPair();
                    panel.i1 = pair[0];
                    panel.i2 = pair[1];
                    panel.activeSmoker = -1;
                    panel.updateGraphAgentPlacingIngredientsCondition(pair[0], pair[1]);
                    if (!sleepVisualization()) {
                        locked = releaseLockSafely(locked);
                        break;
                    }

                    panel.updateGraphAgentSignalingCondition();
                    canSmoke.signalAll();
                    if (!sleepVisualization()) {
                        locked = releaseLockSafely(locked);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    locked = releaseLockSafely(locked);
                }

                if (!sleepRand(400, 800)) {
                    break;
                }
            }
        } finally {
            panel.updateGraphAgentIdleCondition();
        }
    }

    private void runSmoker(int smokerId) {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphSmokerWaitingCondition(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                try {
                    monitorLock.lockInterruptibly();
                    locked = true;

                    while (!canSmokeNow(smokerId) && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.updateGraphSmokerWaitingCondition(smokerId);
                        if (!sleepVisualization()) {
                            locked = releaseLockSafely(locked);
                            return;
                        }
                        canSmoke.await();
                    }

                    if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                        locked = releaseLockSafely(locked);
                        break;
                    }

                    panel.i1 = null;
                    panel.i2 = null;
                    panel.activeSmoker = smokerId;
                    panel.sstate[smokerId] = SState.ARMANDO;
                    panel.updateGraphSmokerTakingCondition(smokerId);
                    if (!sleepVisualization()) {
                        locked = releaseLockSafely(locked);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    locked = releaseLockSafely(locked);
                }

                if (!sleepRand(500, 900)) {
                    break;
                }

                panel.sstate[smokerId] = SState.FUMANDO;
                if (!sleepRand(800, 1400)) {
                    break;
                }

                boolean finishLocked = false;
                try {
                    monitorLock.lockInterruptibly();
                    finishLocked = true;

                    panel.sstate[smokerId] = SState.ESPERANDO;
                    panel.activeSmoker = -1;
                    panel.updateGraphSmokerSignalingCondition();
                    tableEmpty.signal();
                    if (!sleepVisualization()) {
                        finishLocked = releaseLockSafely(finishLocked);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    finishLocked = releaseLockSafely(finishLocked);
                }

                panel.updateGraphSmokerIdleCondition(smokerId);
                if (!sleepVisualization()) {
                    break;
                }
            }
        } finally {
            panel.sstate[smokerId] = SState.ESPERANDO;
            panel.updateGraphSmokerIdleCondition(smokerId);
        }
    }

    private boolean canSmokeNow(int me) {
        return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS))
                || (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS))
                || (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));
    }

    private boolean pairIs(Ing a, Ing b) {
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }

    private Ing[] pickRandomPair() {
        int pick = rnd(3);
        return switch (pick) {
            case 0 -> new Ing[]{Ing.PAPEL, Ing.CERILLOS};
            case 1 -> new Ing[]{Ing.TABACO, Ing.CERILLOS};
            default -> new Ing[]{Ing.TABACO, Ing.PAPEL};
        };
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

    private boolean releaseLockSafely(boolean locked) {
        if (locked) {
            monitorLock.unlock();
            return false;
        }
        return locked;
    }

    private int rnd(int bound) {
        if (bound <= 0) {
            return 0;
        }
        return (int) (Math.random() * bound);
    }
}