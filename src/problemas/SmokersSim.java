package problemas;

// import SimPanel;
// import ProyectoPCyP.SyncMethod;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;

public class SmokersSim extends JPanel implements SimPanel {

    public enum Ing {
        TABACO, PAPEL, CERILLOS
    }

    public enum SState {
        ESPERANDO, ARMANDO, FUMANDO
    }

    public final AtomicBoolean running = new AtomicBoolean(false);
    public volatile Ing i1 = null, i2 = null;
    public volatile int activeSmoker = -1; // -1: nadie, 0: T, 1: P, 2: C
    public final SState[] sstate = {SState.ESPERANDO, SState.ESPERANDO, SState.ESPERANDO};

    private final Timer repaintTimer = new Timer(60, e -> repaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

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
        repaint();
    }

    @Override
    public void startWith(SyncMethod method) {
        stopSimulation();
        resetState();

        // --- Lógica de Título Actualizada ---
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex (Espera Activa)";
        } else if (method == SyncMethod.SEMAPHORES) {
            methodTitle = "Semáforos (Agente)";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        }

        running.set(true);

        // --- Lógica de Estrategia Actualizada ---
        if (method == SyncMethod.MUTEX) {
            currentStrategy = new SmokersPureMutexStrategy(this);
        } else if (method == SyncMethod.SEMAPHORES) {
            currentStrategy = new SmokersSemaphoreStrategy(this);
        } else if (method == SyncMethod.VAR_COND) {
            // --- ESTA ES LA LÍNEA NUEVA ---
            currentStrategy = new SmokersConditionStrategy(this);
        }

        currentStrategy.start();
        repaintTimer.start();
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
        int cx = w / 2, cy = (int) (h * 0.52);
        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String t = "Fumadores (" + methodTitle + ")";
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, (int) (h * 0.06));
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
        if (i1 != null && i2 != null) {
            drawIngredient(g2, i1, cx - 30, cy - 6);
            drawIngredient(g2, i2, cx + 16, cy - 6);
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
            Color base = switch (i) {
                case 0 ->
                    new Color(180, 210, 255);
                case 1 ->
                    new Color(255, 220, 150);
                default ->
                    new Color(190, 255, 190);
            };
            if (sstate[i] == SState.FUMANDO) {
                base = new Color(80, 200, 120);
            } else if (sstate[i] == SState.ARMANDO) {
                base = new Color(255, 200, 80);
            }
            g2.setColor(base);
            g2.fill(new Ellipse2D.Double(px - 22, py - 22, 44, 44));
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(px - 22, py - 22, 44, 44));
            g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
            drawCentered(g2, "F" + i, px, py - plateR - 10);
            drawIngredient(g2, switch (i) {
                case 0 ->
                    Ing.TABACO;
                case 1 ->
                    Ing.PAPEL;
                default ->
                    Ing.CERILLOS;
            }, px - 8, py - 8);
            if (sstate[i] == SState.FUMANDO) {
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

    private void drawIngredient(Graphics2D g2, Ing ing, int x, int y) {
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
