package problemas;

// ================== IMPORTS CORREGIDOS ==================
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke; // Import faltante
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import synch.ReadersWritersMutexStrategy;     // <-- AÑADIDO: Para poder usar la estrategia Mutex
import synch.ReadersWritersSemaphoreStrategy; // <-- AÑADIDO: Para poder usar la estrategia Semáforo
import synch.ReadersWritersStrategy;          // <-- AÑADIDO: Para poder hacer el 'cast'
import synch.SynchronizationStrategy;         // <-- AÑADIDO: Para la variable 'currentStrategy'
// ========================================================

public class ReadersWritersSim extends JPanel implements SimPanel {
    public enum Role { READER, WRITER }
    public enum AState { ARRIVING, WAITING, READING, WRITING, LEAVING, DONE }

    public static class Actor {
        public Role role;
        public AState state = AState.ARRIVING;
        public double x, y, tx, ty;
        public Color color;
    }

    public final AtomicBoolean running = new AtomicBoolean(false);
    public volatile int readersActive = 0;
    public volatile boolean writerActive = false;
    public volatile int readersWaiting = 0;
    public volatile int writersWaiting = 0;
    public final List<Actor> actors = Collections.synchronizedList(new ArrayList<>());
    
    private final Timer timer = new Timer(30, e -> stepAndRepaint());
    private String methodTitle = "";
    private SynchronizationStrategy currentStrategy;
    
    public ReadersWritersSim() {
        setBackground(new Color(238, 238, 238));
    }
    
    private void resetState() {
        actors.clear();
        readersActive = readersWaiting = writersWaiting = 0;
        writerActive = false;
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
        methodTitle = (method == SyncMethod.MUTEX ? "Mutex" : "Semáforos");
        running.set(true);

        // ESTO AHORA FUNCIONARÁ GRACIAS A LOS IMPORTS
        if (method == SyncMethod.MUTEX) {
            currentStrategy = new ReadersWritersMutexStrategy(this);
        } else {
            currentStrategy = new ReadersWritersSemaphoreStrategy(this);
        }
        
        currentStrategy.start();
        timer.start();
    }

    @Override
    public void stopSimulation() {
        running.set(false);
        if (currentStrategy != null) {
            currentStrategy.stop();
        }
        timer.stop();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    public Point docCenter() {
        return new Point(getWidth() / 2, (int) (getHeight() * 0.45));
    }

    private void stepAndRepaint() {
        List<Actor> toRemove = new ArrayList<>();
        synchronized (actors) {
            for (Actor a : actors) {
                double vx = a.tx - a.x, vy = a.ty - a.y;
                double d = Math.hypot(vx, vy);
                double sp = 8.0;
                if (d > 1) {
                    a.x += vx / d * Math.min(sp, d);
                    a.y += vy / d * Math.min(sp, d);
                } else if (a.state == AState.ARRIVING) {
                    a.state = AState.WAITING;
                    // ESTE CAST AHORA FUNCIONARÁ GRACIAS A LOS IMPORTS
                    ((ReadersWritersStrategy)currentStrategy).requestAccess(a);
                } else if (a.state == AState.LEAVING && d < 2) {
                    a.state = AState.DONE;
                    toRemove.add(a);
                }
            }
            actors.removeAll(toRemove);
        }
        repaint(); // <-- CORREGIDO: movido dentro del método
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (!methodTitle.isEmpty()) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String t = "Lectores-Escritores (" + methodTitle + ")";
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, (int) (h * 0.06));
        }
        Rectangle doc = new Rectangle(docCenter().x - 150, docCenter().y - 100, 300, 200);
        g2.setColor(writerActive ? new Color(255, 180, 180) : (readersActive > 0 ? new Color(190, 235, 190) : new Color(240, 240, 240)));
        g2.fillRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);
        g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
        String title = writerActive ? "(ESCRIBIENDO)" : (readersActive > 0 ? "(LEYENDO)" : "(Libre)");
        drawCentered(g2, title, docCenter().x, doc.y - 12);
        
        g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        g2.setColor(new Color(70, 70, 70));
        g2.drawString("Leyendo: " + readersActive, 20, h - 54);
        g2.drawString("Escritor activo: " + (writerActive ? "Sí" : "No"), 20, h - 36);
        g2.drawString("Lectores en espera: " + readersWaiting, w - 220, h - 54);
        g2.drawString("Escritores en espera: " + writersWaiting, w - 220, h - 36);
        
        synchronized (actors) {
            for (Actor a : actors) {
                drawActor(g2, a);
            }
        }
        g2.dispose();
    }

    private void drawActor(Graphics2D g2, Actor a) {
        int r = 16;
        g2.setColor(a.color);
        g2.fillOval((int) a.x - r, (int) a.y - r, r * 2, r * 2);
        g2.setColor(Color.BLACK);
        g2.drawOval((int) a.x - r, (int) a.y - r, r * 2, r * 2);
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE);
        drawCentered(g2, a.role == Role.READER ? "L" : "E", (int) a.x, (int) a.y);
    }
    
    private void drawCentered(Graphics2D g2, String s, int x, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, x - fm.stringWidth(s) / 2, y + fm.getAscent() / 2 - 2);
    }
}