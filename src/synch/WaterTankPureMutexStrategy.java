package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;




public class WaterTankPureMutexStrategy implements SynchronizationStrategy {

    private final WaterTankSim simPanel;
    private Thread producer, consumer;
    private final ReentrantLock mutex = new ReentrantLock();

    

    private static final long VISUALIZATION_DELAY = 500; 

    public WaterTankPureMutexStrategy(WaterTankSim panel) {
        this.simPanel = panel;
    }

    @Override
    public void start() {
        producer = new Thread(() -> {
            while (simPanel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    simPanel.updateGraphProducerRequestingMutex();
                    mutex.lock(); 
                    try {
                        simPanel.updateGraphProducerHoldingMutex();

                        
                        Thread.sleep(VISUALIZATION_DELAY);

                        
                        if (simPanel.level < WaterTankSim.SLOTS) {
                            simPanel.level++;
                        } else {
                            simPanel.updateGraphProducerBlockedByBuffer();
                            
                            Thread.sleep(VISUALIZATION_DELAY / 2); 
                        }
                    } finally {
                        simPanel.updateGraphProducerReleasingMutex();
                        mutex.unlock(); 
                    }
                    
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Producer-PureMutex-Delayed"); 

        consumer = new Thread(() -> {
            while (simPanel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    simPanel.updateGraphConsumerRequestingMutex();
                    mutex.lock(); 
                    try {
                        simPanel.updateGraphConsumerHoldingMutex();

                        
                        Thread.sleep(VISUALIZATION_DELAY);

                        
                        if (simPanel.level > 0) {
                            simPanel.level--;
                        } else {
                            simPanel.updateGraphConsumerBlockedByBuffer();
                            
                            Thread.sleep(VISUALIZATION_DELAY / 2);
                        }
                    } finally {
                        simPanel.updateGraphConsumerReleasingMutex();
                        mutex.unlock(); 
                    }
                    
                    
                    Thread.sleep(300 + (int) (Math.random() * 400));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Consumer-PureMutex-Delayed"); 

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
