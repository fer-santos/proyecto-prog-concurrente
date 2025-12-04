package problemas;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import synch.ReadersWritersBarrierStrategy;
import synch.ReadersWritersConditionStrategy;
import synch.ReadersWritersMonitorStrategy;
import synch.ReadersWritersMutexStrategy;
import synch.ReadersWritersSemaphoreStrategy;
import synch.ReadersWritersStrategy;
import synch.SynchronizationStrategy;
import core.DrawingPanel;

public class ReadersWritersSim extends JPanel implements SimPanel {

    private static final int MAX_READERS = 5;
    private static final int MAX_WRITERS = 5;

    public enum Role {
        READER, WRITER
    }

    public enum AState {
        ARRIVING, WAITING, READING, WRITING, LEAVING, DONE
    }

    public static class Actor {

        public Role role;
        public AState state = AState.ARRIVING;
        public double x, y, tx, ty; 
        public Color color;
        public int id; 
    }

    
    public final AtomicBoolean running = new AtomicBoolean(false);
    public volatile int readersActive = 0; 
    public volatile boolean writerActive = false;
    
    public volatile int readersWaiting = 0;
    public volatile int writersWaiting = 0;

    public final List<Actor> actors = Collections.synchronizedList(new ArrayList<>());
    private int nextActorId = 1; 

    
    private final Timer timer = new Timer(30, e -> stepAndRepaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy; 

    
    private DrawingPanel drawingPanel = null;

    
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public ReadersWritersSim() {
        setBackground(new Color(238, 238, 238));
    }

    private void resetState() {

        synchronized (actors) {
            actors.clear();
        }

        readersActive = 0;
        writerActive = false;
        readersWaiting = 0;
        writersWaiting = 0;
        nextActorId = 1; 
    }

    
    @Override
    public void showSkeleton() {
        stopSimulation(); 
        methodTitle = "";
        resetState();    
        clearRagGraph(); 
        repaint();       
    }

    
    private void clearRagGraph() {
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> drawingPanel.clearGraph());
        }
    }

    
    @Override
    public void startWith(SyncMethod method) {
        stopSimulation();
        clearRagGraph(); 
        resetState();    

        
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex";
        } else if (method == SyncMethod.SEMAPHORES) {
            methodTitle = "Semáforos";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        } else if (method == SyncMethod.MONITORS) {
            methodTitle = "Monitores";
        } else if (method == SyncMethod.BARRIERS) {
            methodTitle = "Barreras";
        } else {
            methodTitle = "Desconocido";
        }

        
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> {
                if (method == SyncMethod.MUTEX) {
                    
                    drawingPanel.setupReadersWritersGraph_Mutex();
                } else if (method == SyncMethod.SEMAPHORES) {
                    drawingPanel.setupReadersWritersGraph_Semaphore();
                } else if (method == SyncMethod.VAR_COND) {
                    drawingPanel.setupReadersWritersGraph_Condition();
                } else if (method == SyncMethod.MONITORS) {
                    drawingPanel.setupReadersWritersGraph_Monitor();
                } else if (method == SyncMethod.BARRIERS) {
                    drawingPanel.setupReadersWritersGraph_Barrier();
                }

                
            });
        }

        running.set(true); 

        
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new ReadersWritersMutexStrategy(this); 
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new ReadersWritersSemaphoreStrategy(this); 
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new ReadersWritersConditionStrategy(this); 
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new ReadersWritersMonitorStrategy(this); 
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new ReadersWritersBarrierStrategy(this); 
        }

        currentStrategy = tempStrategy;

        if (currentStrategy != null) {

            if (!(currentStrategy instanceof ReadersWritersStrategy)) {
                System.err.println("Error: La estrategia seleccionada no implementa ReadersWritersStrategy.");
                methodTitle = "ERROR DE TIPO";
                running.set(false);
                repaint();
                clearRagGraph();
                return; 
            }
            currentStrategy.start(); 
            timer.start();          
        } else {
            System.err.println("Método de sincronización no implementado: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false);
            repaint();
            clearRagGraph();
        }
    }

    

    public void updateGraphReaderRequestingLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showActorRequestingLock_RW("L" + actorId));
        }
    }

    public void updateGraphReaderHoldingLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showActorHoldingLock_RW("L" + actorId));
        }
    }

    public void updateGraphReaderReleasingLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showActorReleasingLock_RW("L" + actorId));
        }
    }

    public void updateGraphWriterRequestingLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showActorRequestingLock_RW("E" + actorId));
        }
    }

    public void updateGraphWriterHoldingLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showActorHoldingLock_RW("E" + actorId));
        }
    }

    public void updateGraphWriterReleasingLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showActorReleasingLock_RW("E" + actorId));
        }
    }

    public void updateGraphReaderFinishedMutex(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderFinishedMutex_RW("L" + actorId));
        }
    }

    public void updateGraphWriterFinishedMutex(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterFinishedMutex_RW("E" + actorId));
        }
    }

    
    public void updateGraphReaderRequestingCountSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderRequestingCountSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderHoldingCountSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderHoldingCountSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderReleasingCountSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderReleasingCountSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderRequestingRwSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderRequestingRwSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderHoldingRwSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderHoldingRwSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderReleasingRwSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderReleasingRwSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderUsingDocumentSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderUsingDocumentSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphReaderFinishedSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderFinishedSemaphore_RW("L" + actorId));
        }
    }

    public void updateGraphWriterRequestingSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterRequestingSemaphore_RW("E" + actorId));
        }
    }

    public void updateGraphWriterHoldingSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterHoldingSemaphore_RW("E" + actorId));
        }
    }

    public void updateGraphWriterUsingDocumentSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterUsingDocumentSemaphore_RW("E" + actorId));
        }
    }

    public void updateGraphWriterReleasingSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterReleasingSemaphore_RW("E" + actorId));
        }
    }

    public void updateGraphWriterFinishedSemaphore(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterFinishedSemaphore_RW("E" + actorId));
        }
    }

    
    public void updateGraphReaderRequestingLockCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderRequestingLockCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderHoldingLockCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderHoldingLockCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderWaitingCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderWaitingCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderSignaledCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderSignaledCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderReleasingLockCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderReleasingLockCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderUsingDocumentCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderUsingDocumentCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderSignalingWriterCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderSignalingWriterCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderSignalingReadersCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderSignalingReadersCondition_RW("L" + actorId));
        }
    }

    public void updateGraphReaderFinishedCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderFinishedCondition_RW("L" + actorId));
        }
    }

    public void updateGraphWriterRequestingLockCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterRequestingLockCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterHoldingLockCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterHoldingLockCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterWaitingCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterWaitingCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterSignaledCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterSignaledCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterReleasingLockCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterReleasingLockCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterUsingDocumentCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterUsingDocumentCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterSignalingWriterCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterSignalingWriterCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterSignalingReadersCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterSignalingReadersCondition_RW("E" + actorId));
        }
    }

    public void updateGraphWriterFinishedCondition(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterFinishedCondition_RW("E" + actorId));
        }
    }

    
    public void updateGraphReaderRequestingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderRequestingMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderHoldingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderHoldingMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderWaitingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderWaitingMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderSignaledMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderSignaledMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderReleasingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderReleasingMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderUsingDocumentMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderUsingDocumentMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderSignalingWriterMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderSignalingWriterMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderSignalingReadersMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderSignalingReadersMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphReaderFinishedMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderFinishedMonitor_RW("L" + actorId));
        }
    }

    public void updateGraphWriterRequestingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterRequestingMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterHoldingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterHoldingMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterWaitingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterWaitingMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterSignaledMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterSignaledMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterReleasingMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterReleasingMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterUsingDocumentMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterUsingDocumentMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterSignalingWriterMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterSignalingWriterMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterSignalingReadersMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterSignalingReadersMonitor_RW("E" + actorId));
        }
    }

    public void updateGraphWriterFinishedMonitor(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterFinishedMonitor_RW("E" + actorId));
        }
    }

    
    public void updateGraphReaderRequestingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderRequestingBarrierLock_RW("L" + actorId));
        }
    }

    public void updateGraphReaderWaitingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderWaitingBarrierLock_RW("L" + actorId));
        }
    }

    public void updateGraphReaderHoldingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderHoldingBarrierLock_RW("L" + actorId));
        }
    }

    public void updateGraphReaderUsingDocumentBarrier(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderUsingDocumentBarrier_RW("L" + actorId));
        }
    }

    public void updateGraphReaderReleasingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderReleasingBarrierLock_RW("L" + actorId));
        }
    }

    public void updateGraphReaderWaitingBarrierGate(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderWaitingBarrierGate_RW("L" + actorId));
        }
    }

    public void updateGraphReaderCrossingBarrierGate(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderCrossingBarrierGate_RW("L" + actorId));
        }
    }

    public void updateGraphReaderFinishedBarrier(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showReaderFinishedBarrier_RW("L" + actorId));
        }
    }

    public void updateGraphWriterRequestingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterRequestingBarrierLock_RW("E" + actorId));
        }
    }

    public void updateGraphWriterWaitingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterWaitingBarrierLock_RW("E" + actorId));
        }
    }

    public void updateGraphWriterHoldingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterHoldingBarrierLock_RW("E" + actorId));
        }
    }

    public void updateGraphWriterUsingDocumentBarrier(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterUsingDocumentBarrier_RW("E" + actorId));
        }
    }

    public void updateGraphWriterReleasingBarrierLock(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterReleasingBarrierLock_RW("E" + actorId));
        }
    }

    public void updateGraphWriterWaitingBarrierGate(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterWaitingBarrierGate_RW("E" + actorId));
        }
    }

    public void updateGraphWriterCrossingBarrierGate(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterCrossingBarrierGate_RW("E" + actorId));
        }
    }

    public void updateGraphWriterFinishedBarrier(int actorId) {
        if (drawingPanel != null && currentStrategy instanceof ReadersWritersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showWriterFinishedBarrier_RW("E" + actorId));
        }
    }


    public int getNextActorId() {
        return nextActorId++;
    }

    public boolean tryAddActor(Actor actor) {
        if (actor == null) {
            return false;
        }
        synchronized (actors) {
            int limit = actor.role == Role.READER ? MAX_READERS : MAX_WRITERS;
            int count = 0;
            for (Actor existing : actors) {
                if (existing != null && existing.role == actor.role && existing.state != AState.DONE) {
                    count++;
                }
            }
            if (count >= limit) {
                return false;
            }
            actors.add(actor);
            return true;
        }
    }

    
    @Override
    public void stopSimulation() {
        running.set(false);
        if (currentStrategy != null) {
            currentStrategy.stop(); 
            currentStrategy = null;
        }
        timer.stop(); 
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    public Point docCenter() {
        int w = getWidth() > 0 ? getWidth() : 600;
        int h = getHeight() > 0 ? getHeight() : 400;
        return new Point(w / 2, (int) (h * 0.45));
    }

    
    
    private void stepAndRepaint() {
        List<Actor> toRemove = new ArrayList<>();
        
        synchronized (actors) {
            for (Actor a : actors) {
                if (a == null) {
                    continue; 
                }
                double vx = a.tx - a.x, vy = a.ty - a.y;
                double d = Math.hypot(vx, vy);
                double sp = 8.0; 
                if (d > 1) { 
                    a.x += vx / d * Math.min(sp, d);
                    a.y += vy / d * Math.min(sp, d);
                } else if (a.state == AState.ARRIVING) { 
                    a.state = AState.WAITING;

                    if (currentStrategy instanceof ReadersWritersStrategy) {
                        ((ReadersWritersStrategy) currentStrategy).requestAccess(a);
                    }
                } else if (a.state == AState.LEAVING && d <= 1) { 
                    a.state = AState.DONE;
                    toRemove.add(a); 
                }
            }
            actors.removeAll(toRemove); 
        }
        repaint(); 
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();


        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String t = "Lectores-Escritores (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
        }

        
        Rectangle doc = new Rectangle(docCenter().x - 150, docCenter().y - 100, 300, 200);

        boolean isWriterActive = this.writerActive;
        int activeReaders = this.readersActive;
        g2.setColor(isWriterActive ? new Color(255, 180, 180) : (activeReaders > 0 ? new Color(190, 235, 190) : new Color(240, 240, 240)));
        g2.fillRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);


        g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
        String docTitle = isWriterActive ? "(ESCRIBIENDO)" : (activeReaders > 0 ? "(LEYENDO)" : "(Libre)");
        drawCenteredString(g2, docTitle, docCenter().x, doc.y - 12);

        
        g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        g2.setColor(new Color(70, 70, 70));
        g2.drawString("Leyendo: " + activeReaders, 20, h - 54);
        g2.drawString("Escritor activo: " + (isWriterActive ? "Sí" : "No"), 20, h - 36);
        g2.drawString("Lectores en espera: " + this.readersWaiting, w - 220, h - 54);
        g2.drawString("Escritores en espera: " + this.writersWaiting, w - 220, h - 36);

        
        synchronized (actors) {
            List<Actor> actorsCopy = new ArrayList<>(actors); 
            for (Actor a : actorsCopy) {
                if (a != null) { 
                    drawActor(g2, a);
                }
            }
        }

        g2.dispose();
    }

    private void drawActor(Graphics2D g2, Actor a) {
        if (a == null) {
            return;
        }
        int r = 16; 
        int drawX = (int) a.x;
        int drawY = (int) a.y;
        g2.setColor(a.color != null ? a.color : Color.GRAY);
        g2.fillOval(drawX - r, drawY - r, r * 2, r * 2);
        g2.setColor(Color.BLACK);
        g2.drawOval(drawX - r, drawY - r, r * 2, r * 2);
        
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE); 
        drawCenteredString(g2, a.role == Role.READER ? "L" : "E", drawX, drawY);
    }


    private void drawCenteredString(Graphics2D g2, String s, int x, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, x - fm.stringWidth(s) / 2, y + fm.getAscent() / 2 - 2); 
    }

} 
