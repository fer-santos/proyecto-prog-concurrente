package problemas;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;
import core.DrawingPanel;

public class PhilosophersSim extends JPanel implements SimPanel {

    public static final int N = 5;

    public enum State {
        THINKING, HUNGRY, EATING
    }

    public final AtomicBoolean running = new AtomicBoolean(false);
    public final State[] state = new State[N]; 
    public final int[] chopstickOwner = new int[N]; 

    private final Timer repaintTimer = new Timer(60, e -> repaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    private DrawingPanel drawingPanel = null;

    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public PhilosophersSim() {
        setBackground(new Color(238, 238, 238));
        resetState(); 
    }

    private void resetState() {
        for (int i = 0; i < N; i++) {
            state[i] = State.THINKING;
            chopstickOwner[i] = -1;
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
            methodTitle = "Semáforo";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        } else if (method == SyncMethod.MONITORS) {
            methodTitle = "Monitores";
        } else if (method == SyncMethod.BARRIERS) {
            methodTitle = "Barreras";
        } else if (method == SyncMethod.PHIL_DEADLOCK) {
            methodTitle = "Deadlock";
        } else if (method == SyncMethod.PHIL_HOARE) {
            methodTitle = "Solución";
        } else {
            methodTitle = "Desconocido";
        }

        
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> {
                if (method == SyncMethod.MUTEX) {
                    
                    drawingPanel.setupPhilosophersGraph_Mutex();
                } else if (method == SyncMethod.SEMAPHORES) {
                    drawingPanel.setupPhilosophersGraph_Semaphore();
                } else if (method == SyncMethod.VAR_COND) {
                    drawingPanel.setupPhilosophersGraph_Condition();
                } else if (method == SyncMethod.MONITORS) {
                    drawingPanel.setupPhilosophersGraph_Monitor();
                } else if (method == SyncMethod.BARRIERS) {
                    drawingPanel.setupPhilosophersGraph_Barrier();
                } else if (method == SyncMethod.PHIL_DEADLOCK) {
                    drawingPanel.setupPhilosophersGraph_DeadlockDemo();
                } else if (method == SyncMethod.PHIL_HOARE) {
                    drawingPanel.setupPhilosophersGraph_HoareDemo();
                }

                
                
            });
        }

        running.set(true); 

        
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new PhilosophersMutexStrategy(this);
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new PhilosophersSemaphoreStrategy(this);
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new PhilosophersConditionStrategy(this);
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new PhilosophersMonitorStrategy(this);
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new PhilosophersBarrierStrategy(this);
        } else if (method == SyncMethod.PHIL_DEADLOCK) {
            tempStrategy = new PhilosophersDeadlockStrategy(this);
        } else if (method == SyncMethod.PHIL_HOARE) {
            tempStrategy = new PhilosophersHoareStrategy(this);
        }

        currentStrategy = tempStrategy;

        if (currentStrategy != null) {
            currentStrategy.start(); 
            repaintTimer.start(); 
        } else {
            System.err.println("Synchronization method not implemented: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false);
            repaint();
            clearRagGraph(); 
        }
    }

    
    
    public void updateGraphPhilosopherRequestingLock(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingLock_Mutex("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherHoldingLock(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherHoldingLock_Mutex("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherReleasingLock(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasingLock_Mutex("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherRequestingWaiter(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingWaiter_Sem("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherGrantedWaiter(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherGrantedWaiter_Sem("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherRequestingFork(int philosopherId, int forkId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingFork_Sem("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherHoldingFork(int philosopherId, int forkId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherHoldingFork_Sem("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherEatingSemaphore(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherEating_Sem("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherReleasingSemaphore(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasingResources_Sem("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherRequestingLockCondition(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingLock_Cond("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherHoldingLockCondition(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherHoldingLock_Cond("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherWaitingCondition(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherWaitingCondition_Cond("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherSignaledCondition(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherSignaledCondition_Cond("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherEatingCondition(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherEating_Cond("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherReleasingCondition(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasing_Cond("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherReleasingLockCondition(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasingLock_Cond("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherIdleCondition(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherIdle_Cond("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherRequestingMonitor(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingMonitor("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherInsideMonitor(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherInsideMonitor("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherWaitingMonitor(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherWaitingMonitor("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherSignaledMonitor(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherSignaledMonitor("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherEatingMonitor(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherEatingMonitor("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherReleasingMonitor(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasingMonitor("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherExitMonitor(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherExitMonitor("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherIdleMonitor(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherIdleMonitor("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherThinkingBarrier(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherThinkingBarrier("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherWaitingBarrier(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherWaitingBarrier("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherReleasedBarrier(int philosopherId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasedBarrier("P" + philosopherId));
        }
    }

    public void updateGraphPhilosopherRequestingForkBarrier(int philosopherId, int forkId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingForkBarrier("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherHoldingForkBarrier(int philosopherId, int forkId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherHoldingForkBarrier("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherEatingBarrier(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherEatingBarrier("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherReleasingBarrier(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleasingBarrier("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherThinkingDemo(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && (currentStrategy instanceof PhilosophersDeadlockStrategy || currentStrategy instanceof PhilosophersHoareStrategy)) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherThinkingDemo("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherRequestingForkDemo(int philosopherId, int forkId) {
        if (drawingPanel != null && (currentStrategy instanceof PhilosophersDeadlockStrategy || currentStrategy instanceof PhilosophersHoareStrategy)) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherRequestingForkDemo("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherWaitingForkDemo(int philosopherId, int forkId) {
        if (drawingPanel != null && currentStrategy instanceof PhilosophersDeadlockStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherWaitingForkDemo("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherHoldingForkDemo(int philosopherId, int forkId) {
        if (drawingPanel != null && (currentStrategy instanceof PhilosophersDeadlockStrategy || currentStrategy instanceof PhilosophersHoareStrategy)) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherHoldingForkDemo("P" + philosopherId, "F" + forkId));
        }
    }

    public void updateGraphPhilosopherEatingDemo(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && (currentStrategy instanceof PhilosophersDeadlockStrategy || currentStrategy instanceof PhilosophersHoareStrategy)) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherEatingDemo("P" + philosopherId, "F" + leftFork, "F" + rightFork));
        }
    }

    public void updateGraphPhilosopherReleasingForksDemo(int philosopherId, int leftFork, int rightFork) {
        if (drawingPanel != null && (currentStrategy instanceof PhilosophersDeadlockStrategy || currentStrategy instanceof PhilosophersHoareStrategy)) {
            SwingUtilities.invokeLater(() -> drawingPanel.showPhilosopherReleaseForksDemo("P" + philosopherId, "F" + leftFork, "F" + rightFork));
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
        int cx = w / 2, cy = (int) (h * 0.47);


        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String title = "Cena de los Filósofos (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, (w - tw) / 2, (int) (h * 0.06));
        }


        int tableR = Math.min(w, h) / 3;
        g2.setColor(new Color(245, 245, 245));
        g2.fill(new Ellipse2D.Double(cx - tableR, cy - tableR, tableR * 2, tableR * 2));
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new Ellipse2D.Double(cx - tableR, cy - tableR, tableR * 2, tableR * 2));


        int bowlR = (int) (tableR * 0.22);
        g2.setColor(new Color(230, 220, 150));
        g2.fill(new Ellipse2D.Double(cx - bowlR, cy - bowlR, bowlR * 2, bowlR * 2));
        g2.setColor(Color.DARK_GRAY);
        g2.draw(new Ellipse2D.Double(cx - bowlR, cy - bowlR, bowlR * 2, bowlR * 2));

        
        double angleStep = 2 * Math.PI / N;
        int plateR = (int) (tableR * 0.25);
        int dishR = (int) (plateR * 0.55); 
        for (int i = 0; i < N; i++) {
            double ang = -Math.PI / 2 + i * angleStep; 
            int px = cx + (int) (Math.cos(ang) * (tableR - plateR - 10)); 
            int py = cy + (int) (Math.sin(ang) * (tableR - plateR - 10));


            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(px - plateR, py - plateR, plateR * 2, plateR * 2));
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(px - plateR, py - plateR, plateR * 2, plateR * 2));
            g2.draw(new Ellipse2D.Double(px - dishR, py - dishR, dishR * 2, dishR * 2)); 

            g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
            String label = "P" + i;
            int labelW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, px - labelW / 2, py - plateR - 8); 

            State currentState = this.state[i];
            switch (currentState) {
                case THINKING:
                    g2.setColor(new Color(120, 180, 255, 70));
                    break; 
                case HUNGRY:
                    g2.setColor(new Color(255, 165, 0, 70));
                    break; 
                case EATING:
                    g2.setColor(new Color(0, 200, 120, 90));
                    break; 
            }
            g2.fill(new Ellipse2D.Double(px - dishR, py - dishR, dishR * 2, dishR * 2)); 
        }


        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < N; i++) {
            double midAng = -Math.PI / 2 + i * angleStep - angleStep / 2.0;
            int radius = tableR - plateR + 6; 
            int x1 = cx + (int) (Math.cos(midAng) * (radius - 18)); 
            int y1 = cy + (int) (Math.sin(midAng) * (radius - 18));
            int x2 = cx + (int) (Math.cos(midAng) * (radius + 22)); 
            int y2 = cy + (int) (Math.sin(midAng) * (radius + 22));

            boolean isFree = (this.chopstickOwner[i] == -1);
            g2.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        g2.dispose();
    }

} 
