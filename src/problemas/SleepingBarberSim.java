package problemas;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;
// --- IMPORTACIÓN CORRECTA ---
import core.DrawingPanel;

public class SleepingBarberSim extends JPanel implements SimPanel {

    public static final int MAX_WAIT_CHAIRS = 5;
    private static final int CUSTOMER_SIZE = 34;

    public enum BarberState {
        SLEEPING, CUTTING
    }

    public enum CustState {
        ENTERING, WAITING, TO_CHAIR, CUTTING, LEAVING, DONE
    }

    public static class Customer {

        public double x, y, tx, ty;
        public CustState state;
        public int seatIndex = -1;
        public Color color;
    }

    // --- Estado de la Simulación ---
    public final List<Customer> customers = Collections.synchronizedList(new ArrayList<>());
    public final Customer[] seats = new Customer[MAX_WAIT_CHAIRS]; // Sillas de espera
    public volatile Customer inChair = null; // Cliente en la silla del barbero
    public volatile BarberState barberState = BarberState.SLEEPING; // Estado del barbero
    public final AtomicBoolean running = new AtomicBoolean(false); // Controla si la simulación corre

    // --- UI y Estrategia ---
    private final Timer repaintTimer = new Timer(30, e -> stepAndRepaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    // --- NUEVO CAMPO ---
    private DrawingPanel drawingPanel = null;

    // --- NUEVO MÉTODO IMPLEMENTADO ---
    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public SleepingBarberSim() {
        setBackground(new Color(238, 238, 238));
        // No resetear estado aquí
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

    private void resetState() {
        // Usa synchronized para seguridad si se accediera desde múltiples hilos (aunque aquí no aplica tanto)
        synchronized (customers) {
            customers.clear();
        }
        synchronized (seats) { // Sincroniza acceso a seats
            for (int i = 0; i < seats.length; i++) {
                seats[i] = null;
            }
        }
        inChair = null;
        barberState = BarberState.SLEEPING;
    }

    // --- MÉTODO MODIFICADO ---
    @Override
    public void startWith(SyncMethod method) {
        clearRagGraph(); // Limpia grafo al inicio
        resetState();    // Reinicia estado lógico

        // --- Lógica de Título ---
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

        // --- Configuración Inicial del Grafo RAG ---
        if (drawingPanel != null) {
            SwingUtilities.invokeLater(() -> {
                if (method == SyncMethod.MUTEX) {
                    // Llama a un método específico para barbero (a crear en DrawingPanel)
                    drawingPanel.setupSleepingBarberGraph();
                } else if (method == SyncMethod.SEMAPHORES) {
                    drawingPanel.setupSleepingBarberGraph_Semaphore();
                } else if (method == SyncMethod.VAR_COND) {
                    drawingPanel.setupSleepingBarberGraph_Condition();
                } else if (method == SyncMethod.MONITORS) {
                    drawingPanel.setupSleepingBarberGraph_Monitor();
                } else if (method == SyncMethod.BARRIERS) {
                    drawingPanel.setupSleepingBarberGraph_Barrier();
                }
                // Añadiremos setups para otros métodos después
                // else if (method == SyncMethod.SEMAPHORES) { ... }
                // etc...
            });
        }

        running.set(true); // Marcar como corriendo

        // --- Lógica de Estrategia ---
        SynchronizationStrategy tempStrategy = null;
        if (method == SyncMethod.MUTEX) {
            tempStrategy = new SleepingBarberPureMutexStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new SleepingBarberSemaphoreStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new SleepingBarberConditionStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new SleepingBarberMonitorStrategy(this); // Pasa 'this'
        } else if (method == SyncMethod.BARRIERS) {
            tempStrategy = new SleepingBarberBarrierStrategy(this); // Pasa 'this'
        }

        currentStrategy = tempStrategy;

        if (currentStrategy != null) {
            currentStrategy.start();
            repaintTimer.start();
        } else {
            System.err.println("Método de sincronización no implementado: " + method);
            methodTitle = "NO IMPLEMENTADO";
            running.set(false);
            repaint();
            clearRagGraph(); // Limpia si no se encontró
        }
    }

    // --- NUEVOS MÉTODOS para ser llamados por la ESTRATEGIA (Mutex Puro) ---
    public void updateGraphGeneratorRequestingLock() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorRequestingLock_Barber());
        }
    }

    public void updateGraphGeneratorHoldingLock() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorHoldingLock_Barber());
        }
    }

    public void updateGraphGeneratorReleasingLock() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorReleasingLock_Barber());
        }
    }

    public void updateGraphBarberRequestingLock() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberRequestingLock_Barber());
        }
    }

    public void updateGraphBarberHoldingLock() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberHoldingLock_Barber());
        }
    }

    public void updateGraphBarberReleasingLock() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberPureMutexStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberReleasingLock_Barber());
        }
    }

    // --- NUEVOS MÉTODOS (Semáforos) ---
    public void updateGraphCustomerRequestingAccessSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerRequestingAccessSemaphore_Barber());
        }
    }

    public void updateGraphCustomerHoldingAccessSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerHoldingAccessSemaphore_Barber());
        }
    }

    public void updateGraphCustomerReleasingAccessSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerReleasingAccessSemaphore_Barber());
        }
    }

    public void updateGraphCustomerQueueFullSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerQueueFullSemaphore_Barber());
        }
    }

    public void updateGraphCustomerSignalingCustomersSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerSignalingCustomersSemaphore_Barber());
        }
    }

    public void updateGraphCustomerWaitingBarberSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerWaitingBarberSemaphore_Barber());
        }
    }

    public void updateGraphCustomerGrantedBarberSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerGrantedBarberSemaphore_Barber());
        }
    }

    public void updateGraphCustomerIdleSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerIdleSemaphore_Barber());
        }
    }

    public void updateGraphBarberWaitingCustomersSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberWaitingCustomersSemaphore_Barber());
        }
    }

    public void updateGraphBarberAcquiredCustomersSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberAcquiredCustomersSemaphore_Barber());
        }
    }

    public void updateGraphBarberRequestingAccessSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberRequestingAccessSemaphore_Barber());
        }
    }

    public void updateGraphBarberHoldingAccessSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberHoldingAccessSemaphore_Barber());
        }
    }

    public void updateGraphBarberReleasingAccessSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberReleasingAccessSemaphore_Barber());
        }
    }

    public void updateGraphBarberSignalingBarberSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberSignalingBarberSemaphore_Barber());
        }
    }

    public void updateGraphBarberIdleSemaphore() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberSemaphoreStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberIdleSemaphore_Barber());
        }
    }

    // --- NUEVOS MÉTODOS (Variable Condición) ---
    public void updateGraphCustomerRequestingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerRequestingLockCondition_Barber());
        }
    }

    public void updateGraphCustomerHoldingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerHoldingLockCondition_Barber());
        }
    }

    public void updateGraphCustomerSeatedCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerSeatedCondition_Barber());
        }
    }

    public void updateGraphCustomerQueueFullCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerQueueFullCondition_Barber());
        }
    }

    public void updateGraphCustomerSignalingCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerSignalingCondition_Barber());
        }
    }

    public void updateGraphCustomerReleasingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerReleasingLockCondition_Barber());
        }
    }

    public void updateGraphCustomerIdleCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerIdleCondition_Barber());
        }
    }

    public void updateGraphBarberRequestingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberRequestingLockCondition_Barber());
        }
    }

    public void updateGraphBarberHoldingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberHoldingLockCondition_Barber());
        }
    }

    public void updateGraphBarberWaitingCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberWaitingCondition_Barber());
        }
    }

    public void updateGraphBarberSignaledCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberSignaledCondition_Barber());
        }
    }

    public void updateGraphBarberReleasingLockCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberReleasingLockCondition_Barber());
        }
    }

    public void updateGraphBarberIdleCondition() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberConditionStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberIdleCondition_Barber());
        }
    }

    // --- NUEVOS MÉTODOS (Monitores) ---
    public void updateGraphCustomerRequestingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerRequestingMonitor_Barber());
        }
    }

    public void updateGraphCustomerInsideMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerInsideMonitor_Barber());
        }
    }

    public void updateGraphCustomerSeatedMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerSeatedMonitor_Barber());
        }
    }

    public void updateGraphCustomerQueueFullMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerQueueFullMonitor_Barber());
        }
    }

    public void updateGraphCustomerSignalingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerSignalingMonitor_Barber());
        }
    }

    public void updateGraphCustomerExitMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerExitMonitor_Barber());
        }
    }

    public void updateGraphCustomerIdleMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showCustomerIdleMonitor_Barber());
        }
    }

    public void updateGraphBarberRequestingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberRequestingMonitor_Barber());
        }
    }

    public void updateGraphBarberInsideMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberInsideMonitor_Barber());
        }
    }

    public void updateGraphBarberWaitingMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberWaitingMonitor_Barber());
        }
    }

    public void updateGraphBarberSignaledMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberSignaledMonitor_Barber());
        }
    }

    public void updateGraphBarberExitMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberExitMonitor_Barber());
        }
    }

    public void updateGraphBarberIdleMonitor() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberMonitorStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberIdleMonitor_Barber());
        }
    }

    // --- NUEVOS MÉTODOS (Barreras) ---
    public void updateGraphGeneratorRequestingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorRequestingBarrier_Barber());
        }
    }

    public void updateGraphGeneratorWaitingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorWaitingBarrier_Barber());
        }
    }

    public void updateGraphGeneratorReleasedBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorReleasedBarrier_Barber());
        }
    }

    public void updateGraphGeneratorFinishedCycle() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showGeneratorFinishedCycle_Barber());
        }
    }

    public void updateGraphBarberRequestingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberRequestingBarrier_Barber());
        }
    }

    public void updateGraphBarberWaitingBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberWaitingBarrier_Barber());
        }
    }

    public void updateGraphBarberReleasedBarrier() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberReleasedBarrier_Barber());
        }
    }

    public void updateGraphBarberFinishedCycle() {
        if (drawingPanel != null && currentStrategy instanceof SleepingBarberBarrierStrategy) {
            SwingUtilities.invokeLater(() -> drawingPanel.showBarberFinishedCycle_Barber());
        }
    }
    // Podríamos añadir más estados si quisiéramos refinar el grafo (ej. cliente->silla, barber->cliente)

    // --- MÉTODO MODIFICADO ---
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

    // --- stepAndRepaint, Posiciones, paintComponent y métodos de dibujo SIN CAMBIOS ---
    // (Asegúrate de que el código que tenías para estos métodos esté aquí)
    private void stepAndRepaint() {
        List<Customer> toRemove = new ArrayList<>();
        // Sincroniza el acceso a la lista de clientes durante la iteración y modificación
        synchronized (customers) {
            for (Customer c : customers) {
                if (c == null) {
                    continue; // Seguridad extra
                }
                double vx = c.tx - c.x, vy = c.ty - c.y;
                double dist = Math.hypot(vx, vy);
                double speed = 8.0;
                if (dist > 1) {
                    c.x += vx / dist * Math.min(speed, dist);
                    c.y += vy / dist * Math.min(speed, dist);
                } else if (c.state == CustState.LEAVING && dist <= 1) { // Condición más precisa
                    toRemove.add(c);
                    c.state = CustState.DONE; // Marcar como hecho antes de remover
                }
            }
            customers.removeAll(toRemove);
        }
        repaint(); // Solicita redibujado (ocurrirá en el EDT)
    }

    public Point seatPos(int idx) {
        int w = getWidth() > 0 ? getWidth() : 600; // Valores por defecto
        int h = getHeight() > 0 ? getHeight() : 400;
        return new Point((int) (w * 0.18) + idx * 44, (int) (h * 0.22));
    }

    public Point chairPos() {
        int w = getWidth() > 0 ? getWidth() : 600;
        int h = getHeight() > 0 ? getHeight() : 400;
        return new Point((int) (w * 0.40), (int) (h * 0.58));
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
            String t = "Barbero Dormilón (" + methodTitle + ")";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
        }

        // Tienda
        int shopX = (int) (w * 0.07), shopY = (int) (h * 0.10);
        int shopW = (int) (w * 0.86), shopH = (int) (h * 0.78);
        g2.setColor(new Color(250, 250, 250));
        g2.fillRoundRect(shopX, shopY, shopW, shopH, 16, 16);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(shopX, shopY, shopW, shopH, 16, 16);

        // Sillas de espera (accede a 'seats' de forma segura)
        synchronized (seats) {
            for (int i = 0; i < MAX_WAIT_CHAIRS; i++) {
                drawChair(g2, seatPos(i).x, seatPos(i).y - 18, 28, 26, seats[i] != null);
            }
        }

        // Silla del barbero
        drawBarberChair(g2, chairPos().x, chairPos().y);

        // Clientes (accede a 'customers' de forma segura)
        synchronized (customers) {
            // Crea una copia para evitar ConcurrentModificationException si stepAndRepaint modifica la lista
            List<Customer> customersCopy = new ArrayList<>(customers);
            for (Customer c : customersCopy) {
                if (c != null) { // Chequeo null
                    drawCustomer(g2, c);
                }
            }
        }

        // Estado Zzz... (lee variable volátil)
        if (barberState == BarberState.SLEEPING) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Zzz...", chairPos().x + 35, chairPos().y - 15);
        }

        g2.dispose();
    }

    private void drawChair(Graphics2D g2, int x, int y, int w, int h, boolean occupied) {
        g2.setColor(occupied ? new Color(200, 230, 255) : new Color(245, 245, 245));
        g2.fillRoundRect(x - w / 2, y - h / 2, w, h, 6, 6);
        g2.setColor(Color.BLACK);
        g2.drawRoundRect(x - w / 2, y - h / 2, w, h, 6, 6);
        // Patas
        g2.drawLine(x - w / 2 + 4, y + h / 2, x - w / 2 + 4, y + h / 2 + 10);
        g2.drawLine(x + w / 2 - 4, y + h / 2, x + w / 2 - 4, y + h / 2 + 10);
    }

    private void drawBarberChair(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(x - 28, y - 18, 56, 36, 10, 10); // Asiento
        g2.setColor(Color.BLACK);
        g2.drawRoundRect(x - 28, y - 18, 56, 36, 10, 10);
        g2.drawLine(x, y + 18, x, y + 32); // Poste
        g2.drawOval(x - 20, y + 32, 40, 10); // Base
    }

    private void drawCustomer(Graphics2D g2, Customer c) {
        if (c == null) {
            return; // Chequeo null
        }
        int r = CUSTOMER_SIZE / 2;
        // Usa las coordenadas actuales del cliente
        int drawX = (int) c.x;
        int drawY = (int) c.y;
        g2.setColor(c.color != null ? c.color : Color.GRAY); // Color por defecto si es null
        g2.fillOval(drawX - r, drawY - r, CUSTOMER_SIZE, CUSTOMER_SIZE);
        g2.setColor(Color.BLACK);
        g2.drawOval(drawX - r, drawY - r, CUSTOMER_SIZE, CUSTOMER_SIZE);
    }

} // Fin de la clase SleepingBarberSim
