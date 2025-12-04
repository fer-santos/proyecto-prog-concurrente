package problemas;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;

import core.DrawingPanel;

public class WaterTankSim extends JPanel implements SimPanel {

    public static final int SLOTS = 20;
    public volatile int level = 0;
    public final AtomicBoolean running = new AtomicBoolean(false);

    private final Timer repaintTimer;
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    
    private DrawingPanel drawingPanel = null;

    
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public WaterTankSim() {
        setBackground(new Color(238, 238, 238));
        repaintTimer = new Timer(60, e -> repaint());
    }

    
    @Override
    public void showSkeleton() {
        stopSimulation(); 
        level = 0;
        methodTitle = "";
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
        level = 0; 

        
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
                    drawingPanel.setupProducerConsumerGraph(); 
                } else if (method == SyncMethod.SEMAPHORES) {
                    drawingPanel.setupProducerConsumerSemaphoreGraph();
                } else if (method == SyncMethod.VAR_COND) {
                    drawingPanel.setupProducerConsumerConditionGraph();
                } else if (method == SyncMethod.MONITORS) {
                    drawingPanel.setupProducerConsumerMonitorGraph();
                } else if (method == SyncMethod.BARRIERS) {
                    drawingPanel.setupProducerConsumerBarrierGraph();
                }
            });
        }

        running.set(true); 

        
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new WaterTankPureMutexStrategy(this); 
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new WaterTankSemaphoreStrategy(this); 
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new WaterTankConditionStrategy(this); 
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new WaterTankMonitorStrategy(this); 
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new WaterTankBarrierStrategy(this); 
        }

        currentStrategy = tempStrategy; 

        if (currentStrategy != null) {
            currentStrategy.start(); 
            repaintTimer.start(); 
        } else {
            System.err.println("Método de sincronización no implementado para este problema: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false); 
            repaint();

            clearRagGraph();
        }
    }

    
    public void updateGraphProducerRequestingMutex() {

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

    public void updateGraphProducerWorkingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWorkingBarrier());
        }
    }

    public void updateGraphProducerWaitingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerWaitingBarrier());
        }
    }

    public void updateGraphProducerReleasedBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerReleasedBarrier());
        }
    }

    public void updateGraphProducerIdleBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showProducerIdleBarrier());
        }
    }

    public void updateGraphConsumerWorkingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWorkingBarrier());
        }
    }

    public void updateGraphConsumerWaitingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerWaitingBarrier());
        }
    }

    public void updateGraphConsumerReleasedBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerReleasedBarrier());
        }
    }

    public void updateGraphConsumerIdleBarrier() {
        if (drawingPanel != null && currentStrategy instanceof WaterTankBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showConsumerIdleBarrier());
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

    
    @Override
    public void stopSimulation() {
        running.set(false); 
        if (currentStrategy != null) {
            currentStrategy.stop(); 
            currentStrategy = null; 
        }
        repaintTimer.stop(); 
        
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    
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


        g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
        if (!methodTitle.isEmpty()) {
            String t = "Productores-Consumidores (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
        }


        g2.setStroke(new BasicStroke(6f));
        g2.setColor(Color.BLACK);
        g2.drawRect(x0, y0, tankW, tankH);
        int slotH = tankH / SLOTS;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.setStroke(new BasicStroke(1f)); 
        for (int i = 1; i < SLOTS; i++) {
            int y = y0 + i * slotH;
            g2.drawLine(x0, y, x0 + tankW, y);
        }

        
        g2.setStroke(new BasicStroke(2f));
        int innerPad = 6;
        int currentLevel = this.level; 
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
} 
