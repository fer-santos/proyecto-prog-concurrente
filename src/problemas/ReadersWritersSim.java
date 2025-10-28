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
import javax.swing.SwingUtilities; // <--- Import necesario
import javax.swing.Timer;
// --- Importaciones de Estrategias ---
import synch.ReadersWritersBarrierStrategy;
import synch.ReadersWritersConditionStrategy;
import synch.ReadersWritersMonitorStrategy;
import synch.ReadersWritersMutexStrategy;
import synch.ReadersWritersSemaphoreStrategy;
import synch.ReadersWritersStrategy;
import synch.SynchronizationStrategy;
// --- IMPORTACIÓN CORRECTA ---
import core.DrawingPanel;

public class ReadersWritersSim extends JPanel implements SimPanel {

    public enum Role {
        READER, WRITER
    }

    public enum AState {
        ARRIVING, WAITING, READING, WRITING, LEAVING, DONE
    }

    public static class Actor {

        public Role role;
        public AState state = AState.ARRIVING;
        public double x, y, tx, ty; // Posición actual y objetivo
        public Color color;
        public int id; // ID único para el grafo RAG
    }

    // --- Estado de la Simulación ---
    public final AtomicBoolean running = new AtomicBoolean(false);
    public volatile int readersActive = 0; // Contadores lógicos (protegidos por estrategia)
    public volatile boolean writerActive = false;
    // Contadores para visualización (actualizados desde estrategia/monitor)
    public volatile int readersWaiting = 0;
    public volatile int writersWaiting = 0;
    // Lista sincronizada de actores visuales
    public final List<Actor> actors = Collections.synchronizedList(new ArrayList<>());
    private int nextActorId = 1; // Para asignar IDs a los actores

    // --- UI y Estrategia ---
    private final Timer timer = new Timer(30, e -> stepAndRepaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy; // Mantiene la estrategia actual

    // --- NUEVO CAMPO ---
    private DrawingPanel drawingPanel = null;

    // --- NUEVO MÉTODO IMPLEMENTADO ---
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public ReadersWritersSim() {
        setBackground(new Color(238, 238, 238));
    }

    private void resetState() {
        // Usa synchronized para modificar la lista de forma segura
        synchronized (actors) {
            actors.clear();
        }
        // Resetea contadores lógicos y visuales
        readersActive = 0;
        writerActive = false;
        readersWaiting = 0;
        writersWaiting = 0;
        nextActorId = 1; // Reinicia contador de ID
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void showSkeleton() {
        stopSimulation(); // Detiene hilos y limpia estrategia
        methodTitle = "";
        resetState();    // Reinicia estado lógico
        clearRagGraph(); // Limpia el grafo asociado
        repaint();       // Redibuja este panel
    }

    // --- NUEVO MÉTODO AUXILIAR ---
    private void clearRagGraph() {
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> drawingPanel.clearGraph());
        }
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void startWith(SyncMethod method) {
        clearRagGraph(); // Limpia grafo al inicio
        resetState();    // Reinicia estado lógico

        // --- Lógica de Título ---
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex (Solo 1 a la vez)";
        } else if (method == SyncMethod.SEMAPHORES) {
            methodTitle = "Semáforos (Pref. Lectores)";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        } else if (method == SyncMethod.MONITORS) {
            methodTitle = "Monitores";
        } else if (method == SyncMethod.BARRIERS) {
            methodTitle = "Barreras (Artificial)";
        } else {
            methodTitle = "Desconocido";
        }

        // --- Configuración Inicial del Grafo RAG ---
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> {
                if (method == SyncMethod.MUTEX) {
                    // Llama a un método específico (a crear en DrawingPanel)
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
                // Añadiremos setups para otros métodos después
                // else if ...
            });
        }

        running.set(true); // Marcar como corriendo

        // --- Lógica de Estrategia ---
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new ReadersWritersMutexStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new ReadersWritersSemaphoreStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new ReadersWritersConditionStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new ReadersWritersMonitorStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new ReadersWritersBarrierStrategy(this); // Pasa 'this'
        }

        currentStrategy = tempStrategy;

        if (currentStrategy != null) {
            // Verifica si la estrategia necesita la interfaz específica
            if (!(currentStrategy instanceof ReadersWritersStrategy)) {
                System.err.println("Error: La estrategia seleccionada no implementa ReadersWritersStrategy.");
                methodTitle = "ERROR DE TIPO";
                running.set(false);
                repaint();
                clearRagGraph();
                return; // No continuar si el tipo es incorrecto
            }
            currentStrategy.start(); // Inicia spawner, etc.
            timer.start();          // Inicia timer de animación
        } else {
            System.err.println("Método de sincronización no implementado: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false);
            repaint();
            clearRagGraph();
        }
    }

    // --- NUEVOS MÉTODOS para ser llamados por la ESTRATEGIA (Mutex Puro) ---
    // Reciben el ID único del actor
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

    // --- Métodos para Semáforos ---
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

    // --- Métodos para Variable de Condición ---
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

    // --- Métodos para Monitores ---
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

    // --- Métodos para Barreras ---
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

    // Método para asignar un ID único al Actor cuando se crea
    public int getNextActorId() {
        return nextActorId++;
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void stopSimulation() {
        running.set(false);
        if (currentStrategy != null) {
            currentStrategy.stop(); // Interrumpe Spawner y ExecutorService
            currentStrategy = null;
        }
        timer.stop(); // Detiene timer de animación
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

    // --- stepAndRepaint, paintComponent, drawActor, drawCentered SIN CAMBIOS ---
    // (Asegúrate de que el código que tenías esté aquí)
    private void stepAndRepaint() {
        List<Actor> toRemove = new ArrayList<>();
        // Sincroniza acceso a la lista 'actors'
        synchronized (actors) {
            for (Actor a : actors) {
                if (a == null) {
                    continue; // Seguridad extra
                }
                double vx = a.tx - a.x, vy = a.ty - a.y;
                double d = Math.hypot(vx, vy);
                double sp = 8.0; // Velocidad de movimiento
                if (d > 1) { // Si no ha llegado al destino
                    a.x += vx / d * Math.min(sp, d);
                    a.y += vy / d * Math.min(sp, d);
                } else if (a.state == AState.ARRIVING) { // Si llegó a la zona de espera
                    a.state = AState.WAITING;
                    // Llama a requestAccess solo si la estrategia es del tipo correcto
                    if (currentStrategy instanceof ReadersWritersStrategy) {
                        ((ReadersWritersStrategy) currentStrategy).requestAccess(a);
                    }
                } else if (a.state == AState.LEAVING && d <= 1) { // Si llegó al punto de salida
                    a.state = AState.DONE;
                    toRemove.add(a); // Marcar para eliminar
                }
            }
            actors.removeAll(toRemove); // Elimina los actores marcados
        }
        repaint(); // Solicita redibujado
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        // Título
        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String t = "Lectores-Escritores (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
        }

        // Dibuja el "Documento"
        Rectangle doc = new Rectangle(docCenter().x - 150, docCenter().y - 100, 300, 200);
        // Lee variables volátiles una vez para el color
        boolean isWriterActive = this.writerActive;
        int activeReaders = this.readersActive;
        g2.setColor(isWriterActive ? new Color(255, 180, 180) : (activeReaders > 0 ? new Color(190, 235, 190) : new Color(240, 240, 240)));
        g2.fillRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);

        // Título del documento
        g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
        String docTitle = isWriterActive ? "(ESCRIBIENDO)" : (activeReaders > 0 ? "(LEYENDO)" : "(Libre)");
        drawCenteredString(g2, docTitle, docCenter().x, doc.y - 12);

        // Contadores en la parte inferior (leen variables volátiles)
        g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        g2.setColor(new Color(70, 70, 70));
        g2.drawString("Leyendo: " + activeReaders, 20, h - 54);
        g2.drawString("Escritor activo: " + (isWriterActive ? "Sí" : "No"), 20, h - 36);
        g2.drawString("Lectores en espera: " + this.readersWaiting, w - 220, h - 54);
        g2.drawString("Escritores en espera: " + this.writersWaiting, w - 220, h - 36);

        // Dibuja los actores (con copia sincronizada)
        synchronized (actors) {
            List<Actor> actorsCopy = new ArrayList<>(actors); // Copia segura para iterar
            for (Actor a : actorsCopy) {
                if (a != null) { // Chequeo null
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
        int r = 16; // Radio del actor
        int drawX = (int) a.x;
        int drawY = (int) a.y;
        g2.setColor(a.color != null ? a.color : Color.GRAY);
        g2.fillOval(drawX - r, drawY - r, r * 2, r * 2);
        g2.setColor(Color.BLACK);
        g2.drawOval(drawX - r, drawY - r, r * 2, r * 2);
        // Etiqueta (L o E)
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE); // Color de la letra
        drawCenteredString(g2, a.role == Role.READER ? "L" : "E", drawX, drawY);
    }

    // Renombrado para evitar confusión con drawCentered en otras clases
    private void drawCenteredString(Graphics2D g2, String s, int x, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, x - fm.stringWidth(s) / 2, y + fm.getAscent() / 2 - 2); // Ajuste vertical
    }

} // Fin de la clase ReadersWritersSim
