package synch;

import java.util.concurrent.Semaphore;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

public class SmokersSemaphoreStrategy implements SynchronizationStrategy {

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];
    private final Semaphore agentSem = new Semaphore(1);
    private final Semaphore[] smokerSems = {new Semaphore(0), new Semaphore(0), new Semaphore(0)};

    public SmokersSemaphoreStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        agentThread = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    agentSem.acquire();
                    int pick = (int) (Math.random() * 3);
                    switch (pick) {
                        case 0 -> { // Falta Tabaco
                            panel.i1 = Ing.PAPEL;
                            panel.i2 = Ing.CERILLOS;
                            smokerSems[0].release();
                        }
                        case 1 -> { // Falta Papel
                            panel.i1 = Ing.TABACO;
                            panel.i2 = Ing.CERILLOS;
                            smokerSems[1].release();
                        }
                        default -> { // Faltan Cerillos
                            panel.i1 = Ing.TABACO;
                            panel.i2 = Ing.PAPEL;
                            smokerSems[2].release();
                        }
                    }
                } catch (InterruptedException e) { return; }
            }
        }, "AgentSem");

        for (int id = 0; id < 3; id++) {
            final int who = id;
            smokerThreads[id] = new Thread(() -> {
                while (panel.running.get()) {
                    try {
                        smokerSems[who].acquire();
                        
                        panel.activeSmoker = who;
                        panel.sstate[who] = SState.ARMANDO;
                        sleepRand(500, 900);
                        
                        panel.sstate[who] = SState.FUMANDO;
                        sleepRand(800, 1400);
                        
                        panel.sstate[who] = SState.ESPERANDO;
                        panel.activeSmoker = -1;
                        panel.i1 = panel.i2 = null;
                        
                        agentSem.release();
                    } catch (InterruptedException e) { return; }
                }
            }, "SmokerSem-" + who);
        }
        
        agentThread.setDaemon(true);
        agentThread.start();
        for(Thread t : smokerThreads) {
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    public void stop() {
        if (agentThread != null) agentThread.interrupt();
        for (Thread t : smokerThreads) {
            if (t != null) t.interrupt();
        }
    }

    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + (int) (Math.random() * (b - a)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}