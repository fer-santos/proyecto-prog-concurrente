package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

public class SmokersBarrierStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];

    private CyclicBarrier barrier;
    private ReentrantLock tableLock;

    public SmokersBarrierStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        tableLock = new ReentrantLock(true);
        barrier = new CyclicBarrier(4);

        agentThread = new Thread(this::runAgent, "Agent-Barrier");
        agentThread.setDaemon(true);
        agentThread.start();

        for (int id = 0; id < smokerThreads.length; id++) {
            final int smokerId = id;
            Thread t = new Thread(() -> runSmoker(smokerId), "Smoker-Barrier-" + smokerId);
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
        for (Thread t : smokerThreads) {
            if (t != null) {
                t.interrupt();
            }
        }
        if (barrier != null) {
            barrier.reset();
        }
        if (tableLock != null && tableLock.isHeldByCurrentThread()) {
            tableLock.unlock();
        }
    }

    private void runAgent() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphAgentRequestingBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                try {
                    tableLock.lockInterruptibly();
                    locked = true;

                    if (panel.i1 == null && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        Ing[] pair = pickRandomPair();
                        panel.i1 = pair[0];
                        panel.i2 = pair[1];
                        panel.activeSmoker = -1;
                        panel.updateGraphAgentPlacingBarrier(pair[0], pair[1]);
                    } else {
                        panel.updateGraphAgentTableBusyBarrier();
                    }

                    if (!sleepVisualization()) {
                        locked = releaseAgentTableLock(locked);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    locked = releaseAgentTableLock(locked);
                }

                panel.updateGraphAgentWaitingBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                if (!awaitBarrierAgent()) {
                    break;
                }

                panel.updateGraphAgentFinishedBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                if (!sleepRand(400, 800)) {
                    break;
                }
            }
        } finally {
            panel.updateGraphAgentFinishedBarrier();
        }
    }

    private void runSmoker(int smokerId) {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphSmokerRequestingBarrier(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                boolean smokedThisRound = false;
                try {
                    tableLock.lockInterruptibly();
                    locked = true;

                    if (canSmokeNow(smokerId) && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.i1 = null;
                        panel.i2 = null;
                        panel.activeSmoker = smokerId;
                        panel.sstate[smokerId] = SState.ARMANDO;
                        panel.updateGraphSmokerTakingBarrier(smokerId);
                        smokedThisRound = true;
                    } else {
                        panel.updateGraphSmokerIdleBarrier(smokerId);
                    }

                    if (!sleepVisualization()) {
                        locked = releaseSmokerTableLock(locked);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    locked = releaseSmokerTableLock(locked);
                }

                if (smokedThisRound) {
                    if (!sleepRand(500, 900)) {
                        break;
                    }
                    panel.sstate[smokerId] = SState.FUMANDO;
                    if (!sleepRand(800, 1400)) {
                        break;
                    }

                    boolean finishLocked = false;
                    try {
                        tableLock.lockInterruptibly();
                        finishLocked = true;
                        panel.sstate[smokerId] = SState.ESPERANDO;
                        panel.activeSmoker = -1;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        if (finishLocked) {
                            tableLock.unlock();
                        }
                    }

                    panel.updateGraphSmokerIdleBarrier(smokerId);
                    if (!sleepVisualization()) {
                        break;
                    }
                }

                panel.updateGraphSmokerWaitingBarrier(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                if (!awaitBarrierSmoker(smokerId)) {
                    break;
                }

                panel.updateGraphSmokerIdleBarrier(smokerId);
                if (!sleepVisualization()) {
                    break;
                }
            }
        } finally {
            panel.sstate[smokerId] = SState.ESPERANDO;
            panel.activeSmoker = -1;
            panel.updateGraphSmokerIdleBarrier(smokerId);
        }
    }

    private boolean awaitBarrierAgent() {
        if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        panel.updateGraphAgentReleasedBarrier();
        return sleepVisualization();
    }

    private boolean awaitBarrierSmoker(int smokerId) {
        if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        panel.updateGraphSmokerReleasedBarrier(smokerId);
        return sleepVisualization();
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

    private boolean releaseAgentTableLock(boolean locked) {
        if (locked) {
            tableLock.unlock();
            return false;
        }
        return locked;
    }

    private boolean releaseSmokerTableLock(boolean locked) {
        if (locked) {
            tableLock.unlock();
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
