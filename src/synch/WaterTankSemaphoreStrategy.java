package synch;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

public class WaterTankSemaphoreStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;
    private Semaphore semEmpty, semFull;
    private ReentrantLock semMutex;
    private static final long VISUALIZATION_DELAY = 420L;

    public WaterTankSemaphoreStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        semEmpty = new Semaphore(WaterTankSim.SLOTS);
        semFull = new Semaphore(0);
        semMutex = new ReentrantLock(true);

        producer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    panel.updateGraphProducerWaitingEmptySemaphore();
                    semEmpty.acquire();
                    panel.updateGraphProducerAcquiredEmptySemaphore();
                    Thread.sleep(VISUALIZATION_DELAY);

                    panel.updateGraphProducerWaitingMutexSemaphore();
                    boolean locked = false;
                    semMutex.lockInterruptibly();
                    locked = true;
                    try {
                        panel.updateGraphProducerHoldingMutexSemaphore();
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphProducerAccessingBufferSemaphore();
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);
                    } finally {
                        if (locked) {
                            panel.updateGraphProducerReleasingMutexSemaphore();
                            semMutex.unlock();
                            locked = false;
                        }
                    }

                    panel.updateGraphProducerSignalingFullSemaphore();
                    semFull.release();
                    Thread.sleep(Math.max(120L, VISUALIZATION_DELAY / 2));
                    panel.updateGraphProducerIdleSemaphore();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphProducerIdleSemaphore();
            }
        }, "Producer-Sem");

        consumer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    panel.updateGraphConsumerWaitingFullSemaphore();
                    semFull.acquire();
                    panel.updateGraphConsumerAcquiredFullSemaphore();
                    Thread.sleep(VISUALIZATION_DELAY);

                    panel.updateGraphConsumerWaitingMutexSemaphore();
                    boolean locked = false;
                    semMutex.lockInterruptibly();
                    locked = true;
                    try {
                        panel.updateGraphConsumerHoldingMutexSemaphore();
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphConsumerAccessingBufferSemaphore();
                        if (panel.level > 0) {
                            panel.level--;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);
                    } finally {
                        if (locked) {
                            panel.updateGraphConsumerReleasingMutexSemaphore();
                            semMutex.unlock();
                            locked = false;
                        }
                    }

                    panel.updateGraphConsumerSignalingEmptySemaphore();
                    semEmpty.release();
                    Thread.sleep(Math.max(120L, VISUALIZATION_DELAY / 2));
                    panel.updateGraphConsumerIdleSemaphore();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphConsumerIdleSemaphore();
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