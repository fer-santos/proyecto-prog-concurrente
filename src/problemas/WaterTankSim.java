package problemas;

// import SimPanel;
// import ProyectoPCyP.SyncMethod;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import synch.*;

public class WaterTankSim extends JPanel implements SimPanel {

    public static final int SLOTS = 20;
    public volatile int level = 0;
    public final AtomicBoolean running = new AtomicBoolean(false);

    private final Timer repaintTimer;
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;

    public WaterTankSim() {
        setBackground(new Color(238, 238, 238));
        repaintTimer = new Timer(60, e -> repaint());
    }

    @Override
    public void showSkeleton() {
        stopSimulation();
        level = 0;
        methodTitle = "";
        repaint();
    }

// ... dentro de WaterTankSim.java ...
    @Override
    public void startWith(SyncMethod method) {
        stopSimulation();

        // --- Lógica de Título Actualizada ---
        if (method == SyncMethod.MUTEX) {
            methodTitle = "Mutex (Espera Activa)";
        } else if (method == SyncMethod.SEMAPHORES) {
            methodTitle = "Semáforos";
        } else if (method == SyncMethod.VAR_COND) {
            methodTitle = "Variable Condición";
        }

        running.set(true);

        // --- Lógica de Estrategia Actualizada ---
        if (method == SyncMethod.MUTEX) {
            currentStrategy = new WaterTankPureMutexStrategy(this);
        } else if (method == SyncMethod.SEMAPHORES) {
            currentStrategy = new WaterTankSemaphoreStrategy(this);
        } else if (method == SyncMethod.VAR_COND) {
            // --- ESTA ES LA LÍNEA NUEVA ---
            currentStrategy = new WaterTankConditionStrategy(this);
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
        for (int i = 1; i < SLOTS; i++) {
            int y = y0 + i * slotH;
            g2.drawLine(x0, y, x0 + tankW, y);
        }
        g2.setStroke(new BasicStroke(2f));
        int innerPad = 6;
        for (int i = 0; i < level; i++) {
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
        double percentage = (double) level / SLOTS * 100.0;
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
