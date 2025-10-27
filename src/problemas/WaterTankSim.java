package problemas;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;
// --- IMPORTACIÓN CORRECTA ---
import core.DrawingPanel;

public class WaterTankSim extends JPanel implements SimPanel {

    public static final int SLOTS = 20;
    public volatile int level = 0;
    public final AtomicBoolean running = new AtomicBoolean(false);

    private final Timer repaintTimer;
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    // --- NUEVO CAMPO ---
    private DrawingPanel drawingPanel = null;

    // --- NUEVO MÉTODO IMPLEMENTADO ---
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public WaterTankSim() {
        setBackground(new Color(238, 238, 238));
        repaintTimer = new Timer(60, e -> repaint());
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void showSkeleton() {
        stopSimulation(); // Detiene hilos y limpia estrategia
        level = 0;
        methodTitle = "";
        clearRagGraph(); // Limpia el grafo asociado
        repaint(); // Redibuja este panel (WaterTankSim)
    }

    // --- NUEVO MÉTODO AUXILIAR ---
    private void clearRagGraph() {
        if (drawingPanel != null) {
            // Llamar desde el EDT para seguridad
            SwingUtilities.invokeLater(() -> drawingPanel.clearGraph());
        }
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void startWith(SyncMethod method) {
        // stopSimulation() ya no es necesario aquí si showSkeleton() lo hace
        // y showSkeleton se llama desde selectProblem

        // Limpia el grafo antes de configurar el nuevo estado
        clearRagGraph();
        level = 0; // Reinicia nivel

        // --- Lógica de Título Actualizada ---
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex (Espera Activa)";
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

        // --- Configuración Inicial del Grafo RAG ---
        if (drawingPanel != null) {
            // Llama al setup ANTES de iniciar la estrategia
            // Usamos invokeLater para asegurar que ocurra en el EDT
            SwingUtilities.invokeLater(() -> {
                if (method == SyncMethod.MUTEX) {
                    drawingPanel.setupProducerConsumerGraph(); // Configura para Mutex
                } else if (method == SyncMethod.SEMAPHORES) {
                    drawingPanel.setupProducerConsumerSemaphoreGraph();
                } else if (method == SyncMethod.VAR_COND) {
                    drawingPanel.setupProducerConsumerConditionGraph();
                } else if (method == SyncMethod.MONITORS) {
                    drawingPanel.setupProducerConsumerMonitorGraph();
                }
            });
        }

        running.set(true); // Marcar como corriendo ANTES de crear hilos

        // --- Lógica de Estrategia Actualizada ---
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new WaterTankPureMutexStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new WaterTankSemaphoreStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new WaterTankConditionStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new WaterTankMonitorStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new WaterTankBarrierStrategy(this); // Pasa 'this'
        }

        currentStrategy = tempStrategy; // Asigna a la variable de instancia (importante)

        if (currentStrategy != null) {
            currentStrategy.start(); // Inicia los hilos de la estrategia
            repaintTimer.start(); // Inicia el timer para este panel (WaterTankSim)
        } else {
            System.err.println("Método de sincronización no implementado para este problema: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false); // Asegura que no quede corriendo
            repaint();
            // Limpia el grafo si no se encontró estrategia
            clearRagGraph();
        }
    }

    // --- NUEVOS MÉTODOS para ser llamados por la ESTRATEGIA ---
    public void updateGraphProducerRequestingMutex() {
        // Solo actualiza si el panel existe y la estrategia actual es la correcta
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerRequestingMutex());
        }
    }

    public void updateGraphProducerHoldingMutex() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerHoldingMutex());
        }
    }

    public void updateGraphProducerBlockedByBuffer() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerBlockedByBuffer());
        }
    }

    public void updateGraphProducerReleasingMutex() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerReleasingMutex());
        }
    }

    public void updateGraphConsumerRequestingMutex() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerRequestingMutex());
        }
    }

    public void updateGraphProducerWaitingEmptySemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingEmptySemaphore());
        }
    }

    public void updateGraphProducerAcquiredEmptySemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerAcquiredEmptySemaphore());
        }
    }

    public void updateGraphProducerWaitingMutexSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingMutexSemaphore());
        }
    }

    public void updateGraphProducerHoldingMutexSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerHoldingMutexSemaphore());
        }
    }

    public void updateGraphProducerAccessingBufferSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerAccessingBufferSemaphore());
        }
    }

    public void updateGraphProducerReleasingMutexSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerReleasingMutexSemaphore());
        }
    }

    public void updateGraphProducerSignalingFullSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerSignalingFullSemaphore());
        }
    }

    public void updateGraphProducerIdleSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerIdleSemaphore());
        }
    }

    public void updateGraphConsumerWaitingFullSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingFullSemaphore());
        }
    }

    public void updateGraphConsumerAcquiredFullSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerAcquiredFullSemaphore());
        }
    }

    public void updateGraphConsumerWaitingMutexSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingMutexSemaphore());
        }
    }

    public void updateGraphConsumerHoldingMutexSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerHoldingMutexSemaphore());
        }
    }

    public void updateGraphConsumerAccessingBufferSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerAccessingBufferSemaphore());
        }
    }

    public void updateGraphConsumerReleasingMutexSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerReleasingMutexSemaphore());
        }
    }

    public void updateGraphConsumerSignalingEmptySemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerSignalingEmptySemaphore());
        }
    }

    public void updateGraphConsumerIdleSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerIdleSemaphore());
        }
    }

    public void updateGraphProducerWaitingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingLockCondition());
        }
    }

    public void updateGraphProducerHoldingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerHoldingLockCondition());
        }
    }

    public void updateGraphProducerWaitingNotFullCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingNotFullCondition());
        }
    }

    public void updateGraphProducerSignaledByNotFullCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerSignaledByNotFullCondition());
        }
    }

    public void updateGraphProducerProducingCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerProducingCondition());
        }
    }

    public void updateGraphProducerSignalingNotEmptyCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerSignalingNotEmptyCondition());
        }
    }

    public void updateGraphProducerReleasingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerReleasingLockCondition());
        }
    }

    public void updateGraphProducerIdleCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerIdleCondition());
        }
    }

    public void updateGraphConsumerWaitingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingLockCondition());
        }
    }

    public void updateGraphConsumerHoldingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerHoldingLockCondition());
        }
    }

    public void updateGraphConsumerWaitingNotEmptyCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingNotEmptyCondition());
        }
    }

    public void updateGraphConsumerSignaledByNotEmptyCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerSignaledByNotEmptyCondition());
        }
    }

    public void updateGraphConsumerConsumingCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerConsumingCondition());
        }
    }

    public void updateGraphConsumerSignalingNotFullCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerSignalingNotFullCondition());
        }
    }

    public void updateGraphConsumerReleasingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerReleasingLockCondition());
        }
    }

    public void updateGraphConsumerIdleCondition() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerIdleCondition());
        }
    }

    public void updateGraphProducerWaitingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingMonitor());
        }
    }

    public void updateGraphProducerInMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerInMonitor());
        }
    }

    public void updateGraphProducerWaitingNotFullMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingNotFullMonitor());
        }
    }

    public void updateGraphProducerSignaledNotFullMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerSignaledNotFullMonitor());
        }
    }

    public void updateGraphProducerProducingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerProducingMonitor());
        }
    }

    public void updateGraphProducerSignalNotEmptyMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerSignalNotEmptyMonitor());
        }
    }

    public void updateGraphProducerExitMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerExitMonitor());
        }
    }

    public void updateGraphProducerIdleMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerIdleMonitor());
        }
    }

    public void updateGraphConsumerWaitingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingMonitor());
        }
    }

    public void updateGraphConsumerInMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerInMonitor());
        }
    }

    public void updateGraphConsumerWaitingNotEmptyMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingNotEmptyMonitor());
        }
    }

    public void updateGraphConsumerSignaledNotEmptyMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerSignaledNotEmptyMonitor());
        }
    }

    public void updateGraphConsumerConsumingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerConsumingMonitor());
        }
    }

    public void updateGraphConsumerSignalNotFullMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerSignalNotFullMonitor());
        }
    }

    public void updateGraphConsumerExitMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerExitMonitor());
        }
    }

    public void updateGraphConsumerIdleMonitor() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerIdleMonitor());
        }
    }

    public void updateGraphConsumerHoldingMutex() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerHoldingMutex());
        }
    }

    public void updateGraphConsumerBlockedByBuffer() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerBlockedByBuffer());
        }
    }

    public void updateGraphConsumerReleasingMutex() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerReleasingMutex());
        }
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void stopSimulation() {
        running.set(false); // Detiene los bucles while en los hilos
        if (currentStrategy != null) {
            currentStrategy.stop(); // Llama a interrupt() en los hilos
            currentStrategy = null; // Libera la referencia a la estrategia
        }
        repaintTimer.stop(); // Detiene el timer de redibujado de este panel
        // No limpiamos el grafo aquí, se hará al seleccionar nuevo problema/método
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    // --- MÉTODO MODIFICADO (uso de copia local de 'level') ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int tankW = Math.min((int) (w * 0.6), 420);
        int tankH = Math.min((int) (h * 0.75), 760);
        int x0 = (w - tankW) / 2;
        int y0 = (int) (h * 0.12);

        // Dibuja título
        g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
        if (!methodTitle.isEmpty()) {
            String t = "Productores-Consumidores (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
        }

        // Dibuja tanque y marcas
        g2.setStroke(new BasicStroke(6f));
        g2.setColor(Color.BLACK);
        g2.drawRect(x0, y0, tankW, tankH);
        int slotH = tankH / SLOTS;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.setStroke(new BasicStroke(1f)); // Líneas más finas para las marcas
        for (int i = 1; i < SLOTS; i++) {
            int y = y0 + i * slotH;
            g2.drawLine(x0, y, x0 + tankW, y);
        }

        // Dibuja nivel de agua (usando copia local)
        g2.setStroke(new BasicStroke(2f));
        int innerPad = 6;
        int currentLevel = this.level; // Lee el valor volátil una vez
        for (int i = 0; i < currentLevel; i++) {
            int slotBottom = y0 + tankH - i * slotH;
            int yy = slotBottom - slotH + innerPad / 2;
            int xx = x0 + innerPad;
            int ww = tankW - innerPad * 2;
            int hh = slotH - innerPad;
            g2.setColor(new Color(0, 200, 255));
            g2.fillRoundRect(xx, yy, ww, hh, 16, 16);
            g2.setColor(Color.BLACK);
            g2.drawRoundRect(xx, yy, ww, hh, 16, 16);
        }

        // Dibuja texto de porcentaje (usando copia local)
        double percentage = (double) currentLevel / SLOTS * 100.0;
        String percentageText = String.format("%.0f%%", percentage);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.setColor(Color.DARK_GRAY);
        FontMetrics fm = g2.getFontMetrics();
        int textX = x0 + (tankW - fm.stringWidth(percentageText)) / 2;
        int textY = y0 + tankH + fm.getAscent() + 10;
        g2.drawString(percentageText, textX, textY);

        g2.dispose();
    }
} // Fin de la clase WaterTankSim
