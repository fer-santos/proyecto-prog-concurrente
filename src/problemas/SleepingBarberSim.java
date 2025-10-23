package problemas;

// import SimPanel;
// import ProyectoPCyP.SyncMethod;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;

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

    public final List<Customer> customers = Collections.synchronizedList(new ArrayList<>());
    public final Customer[] seats = new Customer[MAX_WAIT_CHAIRS];
    public volatile Customer inChair = null;
    public volatile BarberState barberState = BarberState.SLEEPING;
    public final AtomicBoolean running = new AtomicBoolean(false);

    private final Timer repaintTimer = new Timer(30, e -> stepAndRepaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    public SleepingBarberSim() {
        setBackground(new Color(238, 238, 238));
    }

    @Override
    public void showSkeleton() {
        stopSimulation();
        methodTitle = "";
        resetState();
        repaint();
    }

    private void resetState() {
        customers.clear();
        for (int i = 0; i < seats.length; i++) {
            seats[i] = null;
        }
        inChair = null;
        barberState = BarberState.SLEEPING;
    }

    @Override
    public void startWith(SyncMethod method) {
        stopSimulation();
        resetState();

        // --- Lógica de Título Actualizada ---
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex (Espera Activa)";
        } else if (method == SyncMethod.SEMAPHORES) {
            methodTitle = "Semáforos";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        } else if (method == SyncMethod.MONITORS) {
            methodTitle = "Monitores";
        } else if (method == SyncMethod.BARRIERS) { // <-- NUEVO ELSE IF
            methodTitle = "Barreras";      // <-- NUEVO TÍTULO
        }

        running.set(true);

        // --- Lógica de Estrategia Actualizada ---
        // Variable temporal
        SynchronizationStrategy tempStrategy = null;

        if (method == SyncMethod.MUTEX) {
            tempStrategy = new SleepingBarberPureMutexStrategy(this);
        } else if (method == SyncMethod.SEMAPHORES) {
            tempStrategy = new SleepingBarberSemaphoreStrategy(this);
        } else if (method == SyncMethod.VAR_COND) {
            tempStrategy = new SleepingBarberConditionStrategy(this);
        } else if (method == SyncMethod.MONITORS) {
            tempStrategy = new SleepingBarberMonitorStrategy(this);
        } else if (method == SyncMethod.BARRIERS) { // <-- NUEVO ELSE IF
            // --- ESTA ES LA LÍNEA NUEVA ---
            tempStrategy = new SleepingBarberBarrierStrategy(this);
        }

        currentStrategy = tempStrategy; // Asigna a la variable de instancia

        // Asegurarse de que currentStrategy no sea null
        if (currentStrategy != null) {
            currentStrategy.start();
            repaintTimer.start();
        } else {
            System.err.println("Método de sincronización no implementado para este problema: " + method);
            methodTitle = "NO IMPLEMENTADO";
            repaint();
        }
    }

    @Override
    public void stopSimulation() {
        running.set(false);
        if (currentStrategy != null) {
            currentStrategy.stop();
        }
        repaintTimer.stop();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    private void stepAndRepaint() {
        List<Customer> toRemove = new ArrayList<>();
        synchronized (customers) {
            for (Customer c : customers) {
                double vx = c.tx - c.x, vy = c.ty - c.y;
                double dist = Math.hypot(vx, vy);
                double speed = 8.0;
                if (dist > 1) {
                    c.x += vx / dist * Math.min(speed, dist);
                    c.y += vy / dist * Math.min(speed, dist);
                } else if (c.state == CustState.LEAVING) {
                    toRemove.add(c);
                    c.state = CustState.DONE;
                }
            }
            customers.removeAll(toRemove);
        }
        repaint();
    }

    public Point seatPos(int idx) {
        int w = getWidth(), h = getHeight();
        return new Point((int) (w * 0.18) + idx * 44, (int) (h * 0.22));
    }

    public Point chairPos() {
        int w = getWidth(), h = getHeight();
        return new Point((int) (w * 0.40), (int) (h * 0.58));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String t = "Barbero Dormilón (" + methodTitle + ")";
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, (int) (h * 0.06));
        }
        int shopX = (int) (w * 0.07), shopY = (int) (h * 0.10);
        int shopW = (int) (w * 0.86), shopH = (int) (h * 0.78);
        g2.setColor(new Color(250, 250, 250));
        g2.fillRoundRect(shopX, shopY, shopW, shopH, 16, 16);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(shopX, shopY, shopW, shopH, 16, 16);

        for (int i = 0; i < MAX_WAIT_CHAIRS; i++) {
            drawChair(g2, seatPos(i).x, seatPos(i).y - 18, 28, 26, seats[i] != null);
        }
        drawBarberChair(g2, chairPos().x, chairPos().y);

        synchronized (customers) {
            for (Customer c : customers) {
                drawCustomer(g2, c);
            }
        }

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
        g2.drawLine(x - w / 2 + 4, y + h / 2, x - w / 2 + 4, y + h / 2 + 10);
        g2.drawLine(x + w / 2 - 4, y + h / 2, x + w / 2 - 4, y + h / 2 + 10);
    }

    private void drawBarberChair(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(x - 28, y - 18, 56, 36, 10, 10);
        g2.setColor(Color.BLACK);
        g2.drawRoundRect(x - 28, y - 18, 56, 36, 10, 10);
        g2.drawLine(x, y + 18, x, y + 32);
        g2.drawOval(x - 20, y + 32, 40, 10);
    }

    private void drawCustomer(Graphics2D g2, Customer c) {
        int r = CUSTOMER_SIZE / 2;
        g2.setColor(c.color);
        g2.fillOval((int) c.x - r, (int) c.y - r, CUSTOMER_SIZE, CUSTOMER_SIZE);
        g2.setColor(Color.BLACK);
        g2.drawOval((int) c.x - r, (int) c.y - r, CUSTOMER_SIZE, CUSTOMER_SIZE);
    }
}
