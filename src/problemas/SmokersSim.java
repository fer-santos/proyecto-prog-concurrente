package problemas;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;

import core.DrawingPanel;

public class SmokersSim extends JPanel implements SimPanel {

    public enum Ing {
        TABACO, PAPEL, CERILLOS
    }

    public enum SState {
        ESPERANDO, ARMANDO, FUMANDO
    }

    public final AtomicBoolean running = new AtomicBoolean(false);
    public volatile Ing i1 = null, i2 = null; 
    public volatile int activeSmoker = -1; 
    public final SState[] sstate = {SState.ESPERANDO, SState.ESPERANDO, SState.ESPERANDO};

    private final Timer repaintTimer = new Timer(60, e -> repaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    
    private DrawingPanel drawingPanel = null;

    
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public SmokersSim() {
        setBackground(new Color(238, 238, 238));
        
    }

    private void resetState() {
        i1 = i2 = null;
        activeSmoker = -1;
        for (int i = 0; i < 3; i++) {
            sstate[i] = SState.ESPERANDO;
        }
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
            methodTitle = "Semáforos)";
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
                    drawingPanel.setupSmokersGraph();
                } else if (method == SyncMethod.SEMAPHORES) {
                    drawingPanel.setupSmokersGraph_Semaphore();
                } else if (method == SyncMethod.VAR_COND) {
                    drawingPanel.setupSmokersGraph_Condition();
                } else if (method == SyncMethod.MONITORS) {
                    drawingPanel.setupSmokersGraph_Monitor();
                } else if (method == SyncMethod.BARRIERS) {
                    drawingPanel.setupSmokersGraph_Barrier();
                }

                
                
                

                
            });
        }

        running.set(true); 

        
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new SmokersPureMutexStrategy(this); 
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new SmokersSemaphoreStrategy(this); 
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new SmokersConditionStrategy(this); 
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new SmokersMonitorStrategy(this); 
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new SmokersBarrierStrategy(this); 
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

            if (drawingPanel != null && method != SyncMethod.MUTEX) {
                clearRagGraph();
            }
        }
    }

    
    
    public void updateGraphAgentRequestingLock() {
        if (drawingPanel != null && currentStrategy instanceof SmokersPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentRequestingLock_Smokers());
        }
    }

    public void updateGraphAgentHoldingLock(Ing ing1, Ing ing2) {
        if (drawingPanel != null && currentStrategy instanceof SmokersPureMutexStrategy) {
            String label = formatIngredientPair(ing1, ing2);
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentHoldingLock_Smokers(label));
        }
    }

    public void updateGraphAgentReleasingLock() {
        if (drawingPanel != null && currentStrategy instanceof SmokersPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentReleasingLock_Smokers());
        }
    }

    public void updateGraphSmokerRequestingLock(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerRequestingLock_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerHoldingLock(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersPureMutexStrategy) {
            
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerHoldingLock_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerReleasingLock(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerReleasingLock_Smokers(smokerId));
        }
    }

    
    public void updateGraphAgentWaitingSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentWaitingSemaphore_Smokers());
        }
    }

    public void updateGraphAgentHoldingSemaphore(Ing ing1, Ing ing2) {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            String label = formatIngredientPair(ing1, ing2);
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentHoldingSemaphore_Smokers(label));
        }
    }

    public void updateGraphAgentSignalingSemaphore(int smokerId, Ing ing1, Ing ing2) {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            String label = formatIngredientPair(ing1, ing2);
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentSignalingSemaphore_Smokers(smokerId, label));
        }
    }

    public void updateGraphAgentIdleSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentIdleSemaphore_Smokers());
        }
    }

    public void updateGraphSmokerWaitingSemaphore(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerWaitingSemaphore_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerGrantedSemaphore(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerGrantedSemaphore_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerTakingSemaphore(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerTakingSemaphore_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerFinishedSemaphore(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerFinishedSemaphore_Smokers(smokerId));
        }
    }

    
    public void updateGraphAgentRequestingCondition() {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentRequestingCondition_Smokers());
        }
    }

    public void updateGraphAgentPlacingIngredientsCondition(Ing ing1, Ing ing2) {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            String label = formatIngredientPair(ing1, ing2);
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentPlacingCondition_Smokers(label));
        }
    }

    public void updateGraphAgentSignalingCondition() {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentSignalingCondition_Smokers());
        }
    }

    public void updateGraphAgentIdleCondition() {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentIdleCondition_Smokers());
        }
    }

    public void updateGraphSmokerWaitingCondition(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerWaitingCondition_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerTakingCondition(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerTakingCondition_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerSignalingCondition() {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerSignalingCondition_Smokers());
        }
    }

    public void updateGraphSmokerIdleCondition(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerIdleCondition_Smokers(smokerId));
        }
    }

    
    public void updateGraphAgentRequestingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentRequestingMonitor_Smokers());
        }
    }

    public void updateGraphAgentInsideMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentInsideMonitor_Smokers());
        }
    }

    public void updateGraphAgentPlacingMonitor(Ing ing1, Ing ing2) {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            String label = formatIngredientPair(ing1, ing2);
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentPlacingMonitor_Smokers(label));
        }
    }

    public void updateGraphAgentSignalingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentSignalingMonitor_Smokers());
        }
    }

    public void updateGraphAgentIdleMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentIdleMonitor_Smokers());
        }
    }

    public void updateGraphSmokerWaitingMonitor(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerWaitingMonitor_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerInsideMonitor(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerInsideMonitor_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerTakingMonitor(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerTakingMonitor_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerExitMonitor(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerExitMonitor_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerSignalingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerSignalingMonitor_Smokers());
        }
    }

    public void updateGraphSmokerIdleMonitor(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerIdleMonitor_Smokers(smokerId));
        }
    }

    
    public void updateGraphAgentRequestingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentRequestingBarrier_Smokers());
        }
    }

    public void updateGraphAgentPlacingBarrier(Ing ing1, Ing ing2) {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            String label = formatIngredientPair(ing1, ing2);
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentPlacingBarrier_Smokers(label));
        }
    }

    public void updateGraphAgentTableBusyBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentTableBusyBarrier_Smokers());
        }
    }

    public void updateGraphAgentWaitingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentWaitingBarrier_Smokers());
        }
    }

    public void updateGraphAgentReleasedBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentReleasedBarrier_Smokers());
        }
    }

    public void updateGraphAgentFinishedBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showAgentFinishedBarrier_Smokers());
        }
    }

    public void updateGraphSmokerRequestingBarrier(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerRequestingBarrier_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerTakingBarrier(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerTakingBarrier_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerWaitingBarrier(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerWaitingBarrier_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerReleasedBarrier(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerReleasedBarrier_Smokers(smokerId));
        }
    }

    public void updateGraphSmokerIdleBarrier(int smokerId) {
        if (drawingPanel != null && currentStrategy instanceof SmokersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showSmokerIdleBarrier_Smokers(smokerId));
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
        int cx = w / 2, cy = (int) (h * 0.52);


        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String t = "Fumadores (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
        }


        int tableR = Math.min(w, h) / 3;
        g2.setColor(new Color(245, 245, 245));
        g2.fill(new Ellipse2D.Double(cx - tableR, cy - tableR, tableR * 2, tableR * 2));
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new Ellipse2D.Double(cx - tableR, cy - tableR, tableR * 2, tableR * 2));

        
        g2.setColor(new Color(220, 220, 240));
        g2.fill(new Ellipse2D.Double(cx - 28, cy - 28, 56, 56));
        g2.setColor(Color.BLACK);
        g2.draw(new Ellipse2D.Double(cx - 28, cy - 28, 56, 56));

        
        Ing currentI1 = this.i1;
        Ing currentI2 = this.i2;
        if (currentI1 != null && currentI2 != null) {
            drawIngredient(g2, currentI1, cx - 30, cy - 6);
            drawIngredient(g2, currentI2, cx + 16, cy - 6);
        } else {
            g2.setColor(new Color(160, 160, 160));
            drawCentered(g2, "(Vacío)", cx, cy + 2);
        }


        double angleStep = 2 * Math.PI / 3.0;
        int plateR = (int) (tableR * 0.28); 
        for (int i = 0; i < 3; i++) {
            double ang = -Math.PI / 2 + i * angleStep;
            int px = cx + (int) (Math.cos(ang) * (tableR - plateR - 12));
            int py = cy + (int) (Math.sin(ang) * (tableR - plateR - 12));


            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(px - plateR, py - plateR, plateR * 2, plateR * 2));
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(px - plateR, py - plateR, plateR * 2, plateR * 2));


            Color base;
            SState currentState = this.sstate[i]; 
            switch (currentState) {
                case ARMANDO:
                    base = new Color(255, 200, 80);
                    break;
                case FUMANDO:
                    base = new Color(80, 200, 120);
                    break;
                case ESPERANDO:
                default:
                    switch (i) {
                        case 0:
                            base = new Color(180, 210, 255);
                            break; 
                        case 1:
                            base = new Color(255, 220, 150);
                            break; 
                        default:
                            base = new Color(190, 255, 190);
                            break;
                    }
                    break;
            }
            g2.setColor(base);
            g2.fill(new Ellipse2D.Double(px - 22, py - 22, 44, 44)); 
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(px - 22, py - 22, 44, 44));

            
            g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
            drawCentered(g2, "F" + i, px, py - plateR - 10);


            Ing ingredientOwned = switch (i) {
                case 0 ->
                    Ing.TABACO;
                case 1 ->
                    Ing.PAPEL;
                default ->
                    Ing.CERILLOS;
            };
            drawIngredient(g2, ingredientOwned, px - 8, py - 8);


            if (currentState == SState.FUMANDO) {
                g2.setColor(new Color(200, 200, 200, 180));
                int dx = (int) (Math.cos(ang) * 28), dy = (int) (Math.sin(ang) * 28);
                g2.fillOval(px + dx, py + dy, 10, 10);
                g2.fillOval(px + dx + 12, py + dy - 6, 14, 14);
                g2.fillOval(px + dx + 24, py + dy - 12, 18, 18);
            }
        }
        g2.dispose();
    }

    private void drawCentered(Graphics2D g2, String s, int x, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, x - fm.stringWidth(s) / 2, y + fm.getAscent() / 2);
    }

    private String formatIngredientPair(Ing ing1, Ing ing2) {
        if (ing1 == null || ing2 == null) {
            return "";
        }
        return shortIngredientName(ing1) + "+" + shortIngredientName(ing2);
    }

    private String shortIngredientName(Ing ing) {
        if (ing == null) {
            return "";
        }
        return switch (ing) {
            case TABACO -> "Tabaco";
            case PAPEL -> "Papel";
            case CERILLOS -> "Cerillos";
        };
    }

    private void drawIngredient(Graphics2D g2, Ing ing, int x, int y) {
        if (ing == null) {
            return; 
        }
        switch (ing) {
            case TABACO -> {
                g2.setColor(new Color(120, 70, 40));
                g2.fillRoundRect(x, y + 4, 20, 12, 6, 6);
                g2.setColor(Color.BLACK);
                g2.drawRoundRect(x, y + 4, 20, 12, 6, 6);
            }
            case PAPEL -> {
                g2.setColor(Color.WHITE);
                g2.fillRect(x, y, 22, 8);
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, 22, 8);
            }
            case CERILLOS -> {
                g2.setColor(new Color(220, 40, 40));
                g2.fillRect(x, y, 18, 12);
                g2.setColor(new Color(250, 210, 60));
                g2.fillRect(x + 2, y + 2, 14, 4);
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, 18, 12);
            }
        }
    }
} 
