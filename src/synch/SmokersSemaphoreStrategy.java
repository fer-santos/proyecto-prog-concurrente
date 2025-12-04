package synch;

import java.util.concurrent.Semaphore;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

public class SmokersSemaphoreStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];
    private final Semaphore agentSem = new Semaphore(1, true);
    private final Semaphore[] smokerSems = {
        new Semaphore(0, true),
        new Semaphore(0, true),
        new Semaphore(0, true)
    };

    public SmokersSemaphoreStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        agentThread = new Thread(this::runAgent, "AgentSem");
        agentThread.setDaemon(true);
        agentThread.start();

        for (int id = 0; id < smokerThreads.length; id++) {
            final int smokerId = id;
            Thread t = new Thread(() -> runSmoker(smokerId), "SmokerSem-" + smokerId);
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
        agentSem.release();
        for (int i = 0; i < smokerThreads.length; i++) {
            Thread t = smokerThreads[i];
            if (t != null) {
                t.interrupt();
            }
            smokerSems[i].release();
        }
    }

    private void runAgent() {
        boolean permitHeld = false;
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphAgentWaitingSemaphore();
                if (!sleepVisualization()) {
                    break;
                }

                try {
                    agentSem.acquire();
                    permitHeld = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                Ing[] pair = pickRandomPair();
                panel.i1 = pair[0];
                panel.i2 = pair[1];
                panel.activeSmoker = -1;

                panel.updateGraphAgentHoldingSemaphore(pair[0], pair[1]);
                if (!sleepVisualization()) {
                    break;
                }

                int smokerId = smokerForPair(pair[0], pair[1]);
                panel.updateGraphAgentSignalingSemaphore(smokerId, pair[0], pair[1]);
                smokerSems[smokerId].release();
                permitHeld = false; 
                if (!sleepVisualization()) {
                    break;
                }

                panel.updateGraphAgentIdleSemaphore();
                if (!sleepVisualization()) {
                    break;
                }
            }
        } finally {
            if (permitHeld) {
                agentSem.release();
            }
            panel.updateGraphAgentIdleSemaphore();
        }
    }

    private void runSmoker(int smokerId) {
        boolean permitHeld = false;
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphSmokerWaitingSemaphore(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                try {
                    smokerSems[smokerId].acquire();
                    permitHeld = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                panel.updateGraphSmokerGrantedSemaphore(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                panel.activeSmoker = smokerId;
                panel.sstate[smokerId] = SState.ARMANDO;
                panel.updateGraphSmokerTakingSemaphore(smokerId);
                if (!sleepVisualization()) {
                    break;
                }

                if (!sleepRand(500, 900)) {
                    break;
                }

                panel.sstate[smokerId] = SState.FUMANDO;
                if (!sleepRand(800, 1400)) {
                    break;
                }

                panel.sstate[smokerId] = SState.ESPERANDO;
                panel.activeSmoker = -1;
                panel.i1 = null;
                panel.i2 = null;

                panel.updateGraphSmokerFinishedSemaphore(smokerId);
                permitHeld = false;
                agentSem.release();
                if (!sleepVisualization()) {
                    break;
                }
            }
        } finally {
            if (permitHeld) {
                agentSem.release();
            }
            panel.updateGraphSmokerFinishedSemaphore(smokerId);
        }
    }

    private Ing[] pickRandomPair() {
        int pick = rnd(3);
        return switch (pick) {
            case 0 -> new Ing[]{Ing.PAPEL, Ing.CERILLOS};
            case 1 -> new Ing[]{Ing.TABACO, Ing.CERILLOS};
            default -> new Ing[]{Ing.TABACO, Ing.PAPEL};
        };
    }

    private int smokerForPair(Ing ing1, Ing ing2) {
        if ((ing1 == Ing.PAPEL && ing2 == Ing.CERILLOS) || (ing1 == Ing.CERILLOS && ing2 == Ing.PAPEL)) {
            return 0;
        }
        if ((ing1 == Ing.TABACO && ing2 == Ing.CERILLOS) || (ing1 == Ing.CERILLOS && ing2 == Ing.TABACO)) {
            return 1;
        }
        return 2;
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