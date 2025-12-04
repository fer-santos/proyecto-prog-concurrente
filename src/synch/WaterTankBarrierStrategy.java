package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock; 
import problemas.WaterTankSim;









public class WaterTankBarrierStrategy implements SynchronizationStrategy {

    private final WaterTankSim panel;
    private Thread producer, consumer;

    
    private CyclicBarrier barrier;

    
    
    private final ReentrantLock levelLock = new ReentrantLock();
    private static final long VISUALIZATION_DELAY = 420L;

    public WaterTankBarrierStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(2); 

        
        producer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    panel.updateGraphProducerWorkingBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);

                    levelLock.lockInterruptibly();
                    boolean locked = true;
                    try {
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);
                    } finally {
                        if (locked) {
                            levelLock.unlock();
                            locked = false;
                        }
                    }

                    panel.updateGraphProducerWaitingBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);
                    barrier.await();
                    panel.updateGraphProducerReleasedBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);

                    panel.updateGraphProducerIdleBarrier();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphProducerIdleBarrier();
            }
        }, "Producer-Barrier-Visual");

        
        consumer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    levelLock.lockInterruptibly();
                    boolean locked = true;
                    try {
                        if (panel.level > 0) {
                            panel.level--;
                        }
                        panel.updateGraphConsumerWorkingBarrier();
                        Thread.sleep(VISUALIZATION_DELAY);
                    } finally {
                        if (locked) {
                            levelLock.unlock();
                            locked = false;
                        }
                    }

                    Thread.sleep(VISUALIZATION_DELAY);
                    panel.updateGraphConsumerWaitingBarrier();
                    barrier.await();
                    panel.updateGraphConsumerReleasedBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);

                    panel.updateGraphConsumerIdleBarrier();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphConsumerIdleBarrier();
            }
        }, "Consumer-Barrier-Visual");

        producer.setDaemon(true);
        consumer.setDaemon(true);
        producer.start();
        consumer.start();
    }

    @Override
    public void stop() {
        
        
        if (producer != null) {
            producer.interrupt();
        }
        if (consumer != null) {
            consumer.interrupt();
        }
    }
}
