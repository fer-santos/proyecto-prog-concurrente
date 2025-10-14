package synch;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

public class WaterTankSemaphoreStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;
    private Semaphore semEmpty, semFull;
    private ReentrantLock semMutex;

    public WaterTankSemaphoreStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        semEmpty = new Semaphore(WaterTankSim.SLOTS);
        semFull = new Semaphore(0);
        semMutex = new ReentrantLock(true);

        producer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    semEmpty.acquire();
                    semMutex.lock();
                    try {
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                    } finally {
                        semMutex.unlock();
                    }
                    semFull.release();
                    Thread.sleep(200 + (int) (Math.random() * 250));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Producer-Sem");

        consumer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    semFull.acquire();
                    semMutex.lock();
                    try {
                        if (panel.level > 0) {
                            panel.level--;
                        }
                    } finally {
                        semMutex.unlock();
                    }
                    semEmpty.release();
                    Thread.sleep(200 + (int) (Math.random() * 250));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Consumer-Sem");

        producer.setDaemon(true);
        consumer.setDaemon(true);
        producer.start();
        consumer.start();
    }

    @Override
    public void stop() {
        if (producer != null) producer.interrupt();
        if (consumer != null) consumer.interrupt();
    }
}