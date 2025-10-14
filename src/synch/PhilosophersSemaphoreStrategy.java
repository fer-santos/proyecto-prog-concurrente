package synch;

import java.util.concurrent.Semaphore;
import problemas.PhilosophersSim;

public class PhilosophersSemaphoreStrategy implements SynchronizationStrategy {
    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    private Semaphore[] forks;
    private Semaphore waiter;

    public PhilosophersSemaphoreStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }

    private void sleepRand(int min, int max) {
        try {
            Thread.sleep(min + (int)(Math.random() * (max-min)));
        } catch (InterruptedException ignored) {}
    }
    
    @Override
    public void start() {
        forks = new Semaphore[PhilosophersSim.N];
        for(int i=0; i<PhilosophersSim.N; i++) forks[i] = new Semaphore(1, true);
        waiter = new Semaphore(PhilosophersSim.N - 1, true);

        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                while (panel.running.get()) {
                    panel.state[id] = PhilosophersSim.State.THINKING;
                    sleepRand(400, 1000);
                    
                    panel.state[id] = PhilosophersSim.State.HUNGRY;
                    int left = id;
                    int right = (id + 1) % PhilosophersSim.N;
                    
                    try {
                        waiter.acquire();
                        forks[left].acquire();
                        panel.chopstickOwner[left] = id;
                        forks[right].acquire();
                        panel.chopstickOwner[right] = id;
                        
                        panel.state[id] = PhilosophersSim.State.EATING;
                        sleepRand(500, 900);
                        
                        panel.chopstickOwner[left] = -1;
                        panel.chopstickOwner[right] = -1;
                        forks[right].release();
                        forks[left].release();
                        waiter.release();
                    } catch (InterruptedException e) { return; }
                    
                    sleepRand(200, 500);
                }
            }, "Philosopher-Sem-" + id);
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