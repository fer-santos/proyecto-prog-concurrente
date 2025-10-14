package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

public class WaterTankMutexStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;
    private ReentrantLock mtxPC;
    private Condition notEmpty, notFull;

    public WaterTankMutexStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mtxPC = new ReentrantLock(true);
        notEmpty = mtxPC.newCondition();
        notFull = mtxPC.newCondition();

        producer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    mtxPC.lock();
                    try {
                        while (panel.level == WaterTankSim.SLOTS) {
                            notFull.await();
                        }
                        panel.level++;
                        notEmpty.signal();
                    } finally {
                        mtxPC.unlock();
                    }
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Producer-Mutex");

        consumer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    mtxPC.lock();
                    try {
                        while (panel.level == 0) {
                            notEmpty.await();
                        }
                        panel.level--;
                        notFull.signal();
                    } finally {
                        mtxPC.unlock();
                    }
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Consumer-Mutex");

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