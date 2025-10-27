package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

// NOTA: Esta es la implementación del patrón MONITOR (Mutex + Condition)
public class WaterTankConditionStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;
    private ReentrantLock mtxPC;
    private Condition notEmpty, notFull;
    private static final long VISUALIZATION_DELAY = 420L;

    public WaterTankConditionStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mtxPC = new ReentrantLock(true);
        notEmpty = mtxPC.newCondition();
        notFull = mtxPC.newCondition();

        producer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    boolean locked = false;
                    panel.updateGraphProducerWaitingLockCondition();
                    try {
                        mtxPC.lockInterruptibly();
                        locked = true;
                        panel.updateGraphProducerHoldingLockCondition();
                        Thread.sleep(VISUALIZATION_DELAY);

                        while (panel.level == WaterTankSim.SLOTS) {
                            panel.updateGraphProducerWaitingNotFullCondition();
                            Thread.sleep(VISUALIZATION_DELAY);
                            notFull.await();
                            panel.updateGraphProducerSignaledByNotFullCondition();
                            Thread.sleep(VISUALIZATION_DELAY);
                            panel.updateGraphProducerHoldingLockCondition();
                            Thread.sleep(VISUALIZATION_DELAY);
                        }

                        panel.updateGraphProducerProducingCondition();
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphProducerSignalingNotEmptyCondition();
                        notEmpty.signal();
                        Thread.sleep(Math.max(120L, VISUALIZATION_DELAY / 2));
                    } finally {
                        if (locked) {
                            panel.updateGraphProducerReleasingLockCondition();
                            mtxPC.unlock();
                            locked = false;
                        }
                    }
                    panel.updateGraphProducerIdleCondition();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphProducerIdleCondition();
            }
        }, "Producer-Condition-Visual");

        consumer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    boolean locked = false;
                    panel.updateGraphConsumerWaitingLockCondition();
                    try {
                        mtxPC.lockInterruptibly();
                        locked = true;
                        panel.updateGraphConsumerHoldingLockCondition();
                        Thread.sleep(VISUALIZATION_DELAY);

                        while (panel.level == 0) {
                            panel.updateGraphConsumerWaitingNotEmptyCondition();
                            Thread.sleep(VISUALIZATION_DELAY);
                            notEmpty.await();
                            panel.updateGraphConsumerSignaledByNotEmptyCondition();
                            Thread.sleep(VISUALIZATION_DELAY);
                            panel.updateGraphConsumerHoldingLockCondition();
                            Thread.sleep(VISUALIZATION_DELAY);
                        }

                        panel.updateGraphConsumerConsumingCondition();
                        if (panel.level > 0) {
                            panel.level--;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphConsumerSignalingNotFullCondition();
                        notFull.signal();
                        Thread.sleep(Math.max(120L, VISUALIZATION_DELAY / 2));
                    } finally {
                        if (locked) {
                            panel.updateGraphConsumerReleasingLockCondition();
                            mtxPC.unlock();
                            locked = false;
                        }
                    }
                    panel.updateGraphConsumerIdleCondition();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphConsumerIdleCondition();
            }
        }, "Consumer-Condition-Visual");

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