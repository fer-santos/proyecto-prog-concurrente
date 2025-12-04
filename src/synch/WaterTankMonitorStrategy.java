package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;





public class WaterTankMonitorStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;

    
    private ReentrantLock lock; 
    private Condition notEmpty; 
    private Condition notFull;  
    private static final long VISUALIZATION_DELAY = 420L;

    public WaterTankMonitorStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        lock = new ReentrantLock(true);
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();

        
        producer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    boolean locked = false;
                    panel.updateGraphProducerWaitingMonitor();
                    try {
                        lock.lockInterruptibly();
                        locked = true;
                        panel.updateGraphProducerInMonitor();
                        Thread.sleep(VISUALIZATION_DELAY);

                        while (panel.level == WaterTankSim.SLOTS) {
                            panel.updateGraphProducerWaitingNotFullMonitor();
                            Thread.sleep(VISUALIZATION_DELAY);
                            notFull.await();
                            panel.updateGraphProducerSignaledNotFullMonitor();
                            Thread.sleep(VISUALIZATION_DELAY);
                            panel.updateGraphProducerInMonitor();
                            Thread.sleep(VISUALIZATION_DELAY);
                        }

                        panel.updateGraphProducerProducingMonitor();
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphProducerSignalNotEmptyMonitor();
                        notEmpty.signal();
                        Thread.sleep(Math.max(120L, VISUALIZATION_DELAY / 2));
                    } finally {
                        if (locked) {
                            panel.updateGraphProducerExitMonitor();
                            lock.unlock();
                            locked = false;
                        }
                    }
                    panel.updateGraphProducerIdleMonitor();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphProducerIdleMonitor();
            }
        }, "Producer-Monitor-Visual");

        
        consumer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    boolean locked = false;
                    panel.updateGraphConsumerWaitingMonitor();
                    try {
                        lock.lockInterruptibly();
                        locked = true;
                        panel.updateGraphConsumerInMonitor();
                        Thread.sleep(VISUALIZATION_DELAY);

                        while (panel.level == 0) {
                            panel.updateGraphConsumerWaitingNotEmptyMonitor();
                            Thread.sleep(VISUALIZATION_DELAY);
                            notEmpty.await();
                            panel.updateGraphConsumerSignaledNotEmptyMonitor();
                            Thread.sleep(VISUALIZATION_DELAY);
                            panel.updateGraphConsumerInMonitor();
                            Thread.sleep(VISUALIZATION_DELAY);
                        }

                        panel.updateGraphConsumerConsumingMonitor();
                        if (panel.level > 0) {
                            panel.level--;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphConsumerSignalNotFullMonitor();
                        notFull.signal();
                        Thread.sleep(Math.max(120L, VISUALIZATION_DELAY / 2));
                    } finally {
                        if (locked) {
                            panel.updateGraphConsumerExitMonitor();
                            lock.unlock();
                            locked = false;
                        }
                    }
                    panel.updateGraphConsumerIdleMonitor();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphConsumerIdleMonitor();
            }
        }, "Consumer-Monitor-Visual");

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