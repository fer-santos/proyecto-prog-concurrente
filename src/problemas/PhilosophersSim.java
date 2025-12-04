package problemas;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;
// --- IMPORTACIÓN CORRECTA ---
import core.DrawingPanel;

public class PhilosophersSim extends JPanel implements SimPanel {

    public static final int N = 5; // Number of philosophers/chopsticks

    public enum State {
        THINKING, HUNGRY, EATING
    }

    // --- Simulation State ---
    public final AtomicBoolean running = new AtomicBoolean(false);
    public final State[] state = new State[N]; // State of each philosopher
    public final int[] chopstickOwner = new int[N]; // -1 if free, philosopher ID if taken

    // --- UI and Strategy ---
    private final Timer repaintTimer = new Timer(60, e -> repaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    // --- NUEVO CAMPO ---
    private DrawingPanel drawingPanel = null;

    // --- NUEVO MÉTODO IMPLEMENTADO ---
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public PhilosophersSim() {
        setBackground(new Color(238, 238, 238));
        resetState(); // Initialize state on creation
    }

    private void resetState() {
        for (int i = 0; i < N; i++) {
            state[i] = State.THINKING;
            chopstickOwner[i] = -1;
        }
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void showSkeleton() {
        stopSimulation(); // Stop threads, clear strategy
        methodTitle = "";
        resetState();    // Reset logic state
        clearRagGraph(); // Clear associated RAG graph
        repaint();       // Repaint this simulation panel
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
        stopSimulation();
        clearRagGraph(); // Clear graph at the beginning
        resetState();    // Reset logic state

        // --- Logic for Title ---
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

        // --- Setup Initial RAG Graph ---
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> {
                if (method == SyncMethod.MUTEX) {
                    // Call a specific setup method (to be created in DrawingPanel)
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
                // Add setups for other methods later
                // else if (method == SyncMethod.SEMAPHORES) { drawingPanel.setupPhilosophersGraph_Semaphore(); }
                // etc...
            });
        }

        running.set(true); // Mark as running

        // --- Strategy Logic ---
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
            currentStrategy.start(); // Start strategy threads
            repaintTimer.start(); // Start repaint timer for this panel
        } else {
            System.err.println("Synchronization method not implemented: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false);
            repaint();
            clearRagGraph(); // Clean graph if no strategy found
        }
    }

    // --- NEW METHODS to be called by the STRATEGY (Mutex) ---
    // These will call corresponding methods in DrawingPanel (to be created)
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
    // For other strategies, we'll need different update methods (e.g., requesting/holding individual forks)

    // --- MÉTODO MODIFICADO ---
    @Override
    public void stopSimulation() {
        running.set(false); // Stops the while loops in threads
        if (currentStrategy != null) {
            currentStrategy.stop(); // Calls interrupt() on threads
            currentStrategy = null; // Releases the strategy
        }
        repaintTimer.stop(); // Stops repainting this panel
        // Do not clear graph here
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    // --- paintComponent method WITHOUT CHANGES ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int cx = w / 2, cy = (int) (h * 0.47);

        // Title
        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String title = "Cena de los Filósofos (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, (w - tw) / 2, (int) (h * 0.06));
        }

        // Table
        int tableR = Math.min(w, h) / 3;
        g2.setColor(new Color(245, 245, 245));
        g2.fill(new Ellipse2D.Double(cx - tableR, cy - tableR, tableR * 2, tableR * 2));
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new Ellipse2D.Double(cx - tableR, cy - tableR, tableR * 2, tableR * 2));

        // Center Bowl
        int bowlR = (int) (tableR * 0.22);
        g2.setColor(new Color(230, 220, 150));
        g2.fill(new Ellipse2D.Double(cx - bowlR, cy - bowlR, bowlR * 2, bowlR * 2));
        g2.setColor(Color.DARK_GRAY);
        g2.draw(new Ellipse2D.Double(cx - bowlR, cy - bowlR, bowlR * 2, bowlR * 2));

        // Philosophers' Plates and States
        double angleStep = 2 * Math.PI / N;
        int plateR = (int) (tableR * 0.25);
        int dishR = (int) (plateR * 0.55); // Inner circle for state color
        for (int i = 0; i < N; i++) {
            double ang = -Math.PI / 2 + i * angleStep; // Start from top
            int px = cx + (int) (Math.cos(ang) * (tableR - plateR - 10)); // Position on table edge
            int py = cy + (int) (Math.sin(ang) * (tableR - plateR - 10));

            // Plate
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(px - plateR, py - plateR, plateR * 2, plateR * 2));
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(px - plateR, py - plateR, plateR * 2, plateR * 2));
            g2.draw(new Ellipse2D.Double(px - dishR, py - dishR, dishR * 2, dishR * 2)); // Inner rim

            // Label (P0, P1, ...)
            g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
            String label = "P" + i;
            int labelW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, px - labelW / 2, py - plateR - 8); // Above plate

            // State color - read volatile array once per philosopher
            State currentState = this.state[i];
            switch (currentState) {
                case THINKING:
                    g2.setColor(new Color(120, 180, 255, 70));
                    break; // Light Blue
                case HUNGRY:
                    g2.setColor(new Color(255, 165, 0, 70));
                    break; // Orange
                case EATING:
                    g2.setColor(new Color(0, 200, 120, 90));
                    break; // Green
            }
            g2.fill(new Ellipse2D.Double(px - dishR, py - dishR, dishR * 2, dishR * 2)); // Fill inner circle
        }

        // Chopsticks
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < N; i++) {
            // Position between philosopher i and (i+N-1)%N
            double midAng = -Math.PI / 2 + i * angleStep - angleStep / 2.0;
            int radius = tableR - plateR + 6; // Just outside the plates
            int x1 = cx + (int) (Math.cos(midAng) * (radius - 18)); // Inner end
            int y1 = cy + (int) (Math.sin(midAng) * (radius - 18));
            int x2 = cx + (int) (Math.cos(midAng) * (radius + 22)); // Outer end
            int y2 = cy + (int) (Math.sin(midAng) * (radius + 22));

            // Color based on owner - read volatile array once per chopstick
            boolean isFree = (this.chopstickOwner[i] == -1);
            g2.setColor(isFree ? new Color(60, 160, 60) : new Color(200, 60, 60)); // Green if free, Red if taken
            g2.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        g2.dispose();
    }

} // Fin de la clase PhilosophersSim
