package problemas;

// import SimPanel;
// import ProyectoPCyP.SyncMethod;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;

public class PhilosophersSim extends JPanel implements SimPanel {

    public static final int N = 5;

    public enum State {
        THINKING, HUNGRY, EATING
    }

    public final AtomicBoolean running = new AtomicBoolean(false);
    public final State[] state = new State[N];
    public final int[] chopstickOwner = new int[N]; // -1 si libre, id del filósofo si ocupado

    private final Timer repaintTimer = new Timer(60, e -> repaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

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
        repaint();
    }

    @Override
    public void startWith(SyncMethod method) {
        stopSimulation();
        resetState();

        // --- Lógica de Título Actualizada ---
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex (1 a la vez)";
        } else if (method == SyncMethod.SEMAPHORES) {
            methodTitle = "Semáforos (Camarero)";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        } else if (method == SyncMethod.MONITORS) { // <-- NUEVO ELSE IF
            methodTitle = "Monitores";             // <-- NUEVO TÍTULO
        }

        running.set(true);

        // --- Lógica de Estrategia Actualizada ---
        if (method == SyncMethod.MUTEX) {
            currentStrategy = new PhilosophersMutexStrategy(this);
        } else if (method == SyncMethod.SEMAPHORES) {
            currentStrategy = new PhilosophersSemaphoreStrategy(this);
        } else if (method == SyncMethod.VAR_COND) {
            currentStrategy = new PhilosophersConditionStrategy(this);
        } else if (method == SyncMethod.MONITORS) { // <-- NUEVO ELSE IF
            // --- ESTA ES LA LÍNEA NUEVA ---
            currentStrategy = new PhilosophersMonitorStrategy(this);
        }

        // Asegurarse de que currentStrategy no sea null
        if (currentStrategy != null) {
            currentStrategy.start();
            repaintTimer.start();
        } else {
            System.err.println("Método de sincronización no implementado para este problema: " + method);
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
            int tw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, px - tw / 2, py - plateR - 8);
            switch (state[i]) {
                case THINKING ->
                    g2.setColor(new Color(120, 180, 255, 70));
                case HUNGRY ->
                    g2.setColor(new Color(255, 165, 0, 70));
                case EATING ->
                    g2.setColor(new Color(0, 200, 120, 90));
            }
            g2.fill(new Ellipse2D.Double(px - dishR, py - dishR, dishR * 2, dishR * 2));
        }
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < N; i++) {
            double midAng = -Math.PI / 2 + i * angleStep + angleStep / 2.0;
            int r = tableR - plateR + 6;
            int x1 = cx + (int) (Math.cos(midAng) * (r - 18));
            int y1 = cy + (int) (Math.sin(midAng) * (r - 18));
            int x2 = cx + (int) (Math.cos(midAng) * (r + 22));
            int y2 = cy + (int) (Math.sin(midAng) * (r + 22));
            boolean free = (chopstickOwner[i] == -1);
            g2.setColor(free ? new Color(60, 160, 60) : new Color(200, 60, 60));
            g2.draw(new Line2D.Double(x1, y1, x2, y2));
        }
        g2.dispose();
    }
}
