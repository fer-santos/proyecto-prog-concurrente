package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.PhilosophersSim;

public class PhilosophersMutexStrategy implements SynchronizationStrategy {
    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    private ReentrantLock diningMutex;

    public PhilosophersMutexStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }
    
    private void sleepRand(int min, int max) {
        try {
            Thread.sleep(min + (int)(Math.random() * (max-min)));
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void start() {
        diningMutex = new ReentrantLock(true);
        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                while (panel.running.get()) {
                    panel.state[id] = PhilosophersSim.State.THINKING;
                    sleepRand(400, 1000);
                    
                    panel.state[id] = PhilosophersSim.State.HUNGRY;
                    int left = id;
                    int right = (id + 1) % PhilosophersSim.N;
                    
                    diningMutex.lock();
                    try {
                        panel.chopstickOwner[left] = id;
                        panel.chopstickOwner[right] = id;
                        panel.state[id] = PhilosophersSim.State.EATING;
                        sleepRand(500, 900);
                        panel.chopstickOwner[left] = -1;
                        panel.chopstickOwner[right] = -1;
                    } finally {
                        diningMutex.unlock();
                    }
                    sleepRand(200, 500);
                }
            }, "Philosopher-Mutex-" + id);
            threads[i].setDaemon(true);
            threads[i].start();
        }
    }

    @Override
    public void stop() {
        for (Thread t : threads) {
            if (t != null) t.interrupt();
        }
    }
}