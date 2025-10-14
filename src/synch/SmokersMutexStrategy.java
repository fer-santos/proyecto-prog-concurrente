package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

public class SmokersMutexStrategy implements SynchronizationStrategy {

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];
    private ReentrantLock mtx;
    private Condition tableEmpty, canSmoke;

    public SmokersMutexStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mtx = new ReentrantLock(true);
        tableEmpty = mtx.newCondition();
        canSmoke = mtx.newCondition();

        agentThread = new Thread(() -> {
            while (panel.running.get()) {
                mtx.lock();
                try {
                    while (panel.i1 != null) { // Esperar a que la mesa esté vacía
                        tableEmpty.await();
                    }
                    putRandomPair();
                    canSmoke.signalAll(); // Avisar a los fumadores que hay ingredientes
                } catch (InterruptedException e) { return; } 
                finally {
                    mtx.unlock();
                }
                sleepRand(400, 800);
            }
        }, "AgentMutex");

        for (int id = 0; id < 3; id++) {
            final int who = id;
            smokerThreads[id] = new Thread(() -> {
                while (panel.running.get()) {
                    mtx.lock();
                    try {
                        while (!canSmokeNow(who)) {
                            canSmoke.await();
                        }
                        
                        panel.i1 = panel.i2 = null; // Tomar ingredientes
                        panel.activeSmoker = who;
                        panel.sstate[who] = SState.ARMANDO;
                        
                        // Salir del lock para permitir que el agente ponga más ingredientes
                        // si otro fumador pudiera fumar (aunque no en este diseño)
                    } catch (InterruptedException e) { return; }
                    finally {
                        mtx.unlock();
                    }

                    sleepRand(500, 900); // Armando
                    panel.sstate[who] = SState.FUMANDO;
                    sleepRand(800, 1400); // Fumando
                    
                    mtx.lock();
                    try {
                        panel.sstate[who] = SState.ESPERANDO;
                        panel.activeSmoker = -1;
                        tableEmpty.signal(); // Avisar al agente que la mesa está libre
                    } finally {
                        mtx.unlock();
                    }
                }
            }, "SmokerMutex-" + who);
        }
        
        agentThread.setDaemon(true);
        agentThread.start();
        for (Thread t : smokerThreads) {
            t.setDaemon(true);
            t.start();
        }
    }
    
    private boolean canSmokeNow(int me) {
        return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS)) ||
               (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS)) ||
               (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));
    }

    private boolean pairIs(Ing a, Ing b) {
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }
    
    private void putRandomPair() {
        int pick = (int) (Math.random() * 3);
        switch (pick) {
            case 0 -> { panel.i1 = Ing.PAPEL; panel.i2 = Ing.CERILLOS; }
            case 1 -> { panel.i1 = Ing.TABACO; panel.i2 = Ing.CERILLOS; }
            default -> { panel.i1 = Ing.TABACO; panel.i2 = Ing.PAPEL; }
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