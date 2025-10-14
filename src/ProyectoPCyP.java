
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.*;

public class ProyectoPCyP extends JFrame {

    enum Problem {
        NONE, PRODUCERS, PHILOSOPHERS, BARBER, SMOKERS, READERS_WRITERS
    }

    enum SyncMethod {
        NONE, MUTEX, SEMAPHORES, VAR_COND, MONITORS, BARRIERS
    }

    private Problem selectedProblem = Problem.NONE;
    private SyncMethod selectedMethod = SyncMethod.NONE;

    // ===== Menús =====
    private JMenuBar barra;
    private JMenu archivo, synch, problemas;
    private JMenuItem nuevo, abrir, guardar, cerrar;
    private JMenuItem mutex, semaforos, varCon, monitores, barreras;
    private JMenuItem prodConsum, cenaFilosofos, barberoDormilon, fumadores, lectoresEscritores;

    // ===== Modelo nodos (panel derecho) =====
    enum NodeType {
        PROCESO, RECURSO
    }

    static class ShapeNode implements Serializable {

        int id;
        NodeType type;
        int x, y;
        int size;
        String label;  // Pn / Rn

        boolean contains(int px, int py) {
            int h = size / 2;
            if (type == NodeType.PROCESO) {
                int dx = px - x, dy = py - y;
                return dx * dx + dy * dy <= h * h;
            } else {
                return px >= x - h && px <= x + h && py >= y - h && py <= y + h;
            }
        }
    }

    static class Connection implements Serializable {

        int fromId;
        int toId;
        String kind;
    }

    static class GraphData implements Serializable {

        ArrayList<ShapeNode> nodes = new ArrayList<>();
        ArrayList<Connection> connections = new ArrayList<>();
        int nextProceso = 1, nextRecurso = 1, nextId = 1;
    }

    // ===== UI general =====
    private final JSplitPane split;
    private final JPanel leftPanel;
    private DrawingPanel drawing = null;
    private final JFileChooser chooser = new JFileChooser();

    interface SimPanel {

        void showSkeleton();

        void startWith(SyncMethod method);

        void stopSimulation();

        JComponent getComponent();
    }
    private SimPanel currentSim = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ProyectoPCyP fr = new ProyectoPCyP();
            fr.setVisible(true);
            fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        });
    }

    public ProyectoPCyP() {
        setSize(1800, 1000);
        setTitle("Proyecto Programación Concurrente y Paralela Otoño 2025");
        setLocationRelativeTo(null);

        // ---- Barra de menús ----
        barra = new JMenuBar();
        archivo = new JMenu("Archivo");
        nuevo = new JMenuItem("Nuevo");
        abrir = new JMenuItem("Abrir");
        guardar = new JMenuItem("Guardar");
        cerrar = new JMenuItem("Cerrar");
        archivo.add(nuevo);
        archivo.add(abrir);
        archivo.add(guardar);
        archivo.addSeparator();
        archivo.add(cerrar);

        synch = new JMenu("Synch");
        mutex = new JMenuItem("Mutex");
        semaforos = new JMenuItem("Semáforos");
        varCon = new JMenuItem("Variable Condición");
        monitores = new JMenuItem("Monitores");
        barreras = new JMenuItem("Barreras");
        synch.add(mutex);
        synch.add(semaforos);
        synch.add(varCon);
        synch.add(monitores);
        synch.add(barreras);

        problemas = new JMenu("Problemas");
        prodConsum = new JMenuItem("Productores-Consumidores");
        cenaFilosofos = new JMenuItem("Cena de los Filosofos");
        barberoDormilon = new JMenuItem("Barbero Dormilón");
        fumadores = new JMenuItem("Fumadores");
        lectoresEscritores = new JMenuItem("Lectores-Escritores");
        problemas.add(prodConsum);
        problemas.add(cenaFilosofos);
        problemas.add(barberoDormilon);
        problemas.add(fumadores);
        problemas.add(lectoresEscritores);

        barra.add(archivo);
        barra.add(synch);
        barra.add(problemas);
        setJMenuBar(barra);

        // Acciones Archivo
        nuevo.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this, "¿Iniciar nuevo documento? Se perderán cambios no guardados.", "Nuevo", JOptionPane.OK_CANCEL_OPTION);
            if (r == JOptionPane.OK_OPTION) {
                drawing.setData(new GraphData());
            }
        });
        guardar.addActionListener(e -> saveToFile());
        abrir.addActionListener(e -> openFromFile());
        cerrar.addActionListener(e -> dispose());
        chooser.setFileFilter(new FileNameExtensionFilter("Diagramas (*.diag)", "diag"));

        // ---- 30% | 70% ----
        leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(240, 240, 240));
        drawing = new DrawingPanel();
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, drawing);
        split.setResizeWeight(0.30);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        // ---- Menú Problemas → cargar "esqueleto" sin animación ----
        prodConsum.addActionListener(e -> selectProblem(Problem.PRODUCERS, new WaterTankSim()));
        cenaFilosofos.addActionListener(e -> selectProblem(Problem.PHILOSOPHERS, new PhilosophersSim()));
        barberoDormilon.addActionListener(e -> selectProblem(Problem.BARBER, new SleepingBarberSim()));
        fumadores.addActionListener(e -> selectProblem(Problem.SMOKERS, new SmokersSim()));
        lectoresEscritores.addActionListener(e -> selectProblem(Problem.READERS_WRITERS, new ReadersWritersSim()));

        // ---- Menú Synch ----
        mutex.addActionListener(e -> selectMethod(SyncMethod.MUTEX));
        semaforos.addActionListener(e -> selectMethod(SyncMethod.SEMAPHORES));
        varCon.addActionListener(e -> methodNotImplementedYet("Variable Condición"));
        monitores.addActionListener(e -> methodNotImplementedYet("Monitores"));
        barreras.addActionListener(e -> methodNotImplementedYet("Barreras"));
    }

    private void methodNotImplementedYet(String name) {
        if (selectedProblem == Problem.NONE) {
            JOptionPane.showMessageDialog(this, "Primero selecciona un problema.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, name + " aún no está implementado. Usa por ahora Mutex o Semáforos.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
    }

    private void selectProblem(Problem problem, SimPanel sim) {
        if (currentSim != null) {
            currentSim.stopSimulation();
            leftPanel.remove(currentSim.getComponent());
        } else {
            leftPanel.removeAll();
        }
        selectedProblem = problem;
        selectedMethod = SyncMethod.NONE;
        currentSim = sim;
        leftPanel.add(currentSim.getComponent(), BorderLayout.CENTER);
        leftPanel.revalidate();
        leftPanel.repaint();
        currentSim.showSkeleton();
    }

    private void selectMethod(SyncMethod method) {
        if (selectedProblem == Problem.NONE || currentSim == null) {
            JOptionPane.showMessageDialog(this, "Primero selecciona un problema (menú Problemas).", "Selecciona un problema", JOptionPane.WARNING_MESSAGE);
            return;
        }
        selectedMethod = method;
        currentSim.stopSimulation();
        currentSim.startWith(method);
    }

    // ===== Guardar/Abrir nodos =====
    private void saveToFile() {
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".diag")) {
                f = new File(f.getParentFile(), f.getName() + ".diag");
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
                oos.writeObject(drawing.data);
                JOptionPane.showMessageDialog(this, "Guardado:\n" + f.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openFromFile() {
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                GraphData g = (GraphData) ois.readObject();
                drawing.setData(g);
                JOptionPane.showMessageDialog(this, "Abierto:\n" + f.getAbsolutePath());
            } catch (IOException | ClassNotFoundException ex) {
                JOptionPane.showMessageDialog(this, "Error al abrir: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ======================================================================
    // =======================    SIMULACIONES    ============================
    // ======================================================================
    // ===== Productores–Consumidores (tanque) =====
    static class WaterTankSim extends JPanel implements SimPanel {

        private static final int SLOTS = 20;
        private volatile int level = 0;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread producer, consumer;
        private final Timer repaintTimer;
        private String methodTitle = "";
        // MUTEX + Conditions
        private ReentrantLock mtxPC = null;
        private Condition notEmpty = null, notFull = null;
        // SEMÁFOROS
        private Semaphore semEmpty = null, semFull = null;
        private ReentrantLock semMutex = null;

        WaterTankSim() {
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

        @Override
        public void startWith(SyncMethod method) {
            stopSimulation();
            methodTitle = (method == SyncMethod.MUTEX ? "Mutex" : "Semáforos");
            running.set(true);
            repaintTimer.start();
            if (method == SyncMethod.MUTEX) {
                mtxPC = new ReentrantLock(true);
                notEmpty = mtxPC.newCondition();
                notFull = mtxPC.newCondition();
                producer = new Thread(() -> {
                    while (running.get()) {
                        try {
                            mtxPC.lock();
                            try {
                                while (level == SLOTS) {
                                    notFull.await();
                                }
                                level++;
                                notEmpty.signal();
                            } finally {
                                mtxPC.unlock();
                            }
                            Thread.sleep(180 + (int) (Math.random() * 220));
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "Producer-Mutex");
                consumer = new Thread(() -> {
                    while (running.get()) {
                        try {
                            mtxPC.lock();
                            try {
                                while (level == 0) {
                                    notEmpty.await();
                                }
                                level--;
                                notFull.signal();
                            } finally {
                                mtxPC.unlock();
                            }
                            Thread.sleep(180 + (int) (Math.random() * 220));
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "Consumer-Mutex");
            } else {
                semEmpty = new Semaphore(SLOTS);
                semFull = new Semaphore(0);
                semMutex = new ReentrantLock(true);
                producer = new Thread(() -> {
                    while (running.get()) {
                        try {
                            semEmpty.acquire();
                            semMutex.lock();
                            try {
                                if (level < SLOTS) {
                                    level++;
                                }
                            } finally {
                                semMutex.unlock();
                            }
                            semFull.release();
                            Thread.sleep(200 + (int) (Math.random() * 250));
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "Producer-Sem");
                consumer = new Thread(() -> {
                    while (running.get()) {
                        try {
                            semFull.acquire();
                            semMutex.lock();
                            try {
                                if (level > 0) {
                                    level--;
                                }
                            } finally {
                                semMutex.unlock();
                            }
                            semEmpty.release();
                            Thread.sleep(200 + (int) (Math.random() * 250));
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "Consumer-Sem");
            }
            producer.setDaemon(true);
            consumer.setDaemon(true);
            producer.start();
            consumer.start();
        }

        @Override
        public void stopSimulation() {
            running.set(false);
            if (producer != null) {
                producer.interrupt();
            }
            if (consumer != null) {
                consumer.interrupt();
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
                String t = methodTitle;
                int tw = g2.getFontMetrics().stringWidth(t);
                g2.drawString(t, (w - tw) / 2, (int) (h * 0.06));
            }
            g2.setStroke(new BasicStroke(6f));
            g2.setColor(Color.BLACK);
            g2.drawRect(x0, y0, tankW, tankH);
            int slotH = tankH / SLOTS, innerPad = 6;
            g2.setColor(new Color(0, 0, 0, 120));
            for (int i = 1; i < SLOTS; i++) {
                int y = y0 + i * slotH;
                g2.drawLine(x0, y, x0 + tankW, y);
            }
            g2.setStroke(new BasicStroke(2f));
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

    // ===== Cena de los Filósofos =====
    static class PhilosophersSim extends JPanel implements SimPanel {

        private static final int N = 5;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final Timer repaintTimer = new Timer(60, e -> repaint());
        private final Thread[] threads = new Thread[N];

        enum State {
            THINKING, HUNGRY, EATING
        }
        private final State[] state = new State[N];
        private final int[] chopstickOwner = new int[N];
        private String methodTitle = "";
        private ReentrantLock diningMutex = null;
        private Semaphore[] fork = null;
        private Semaphore waiter = null;

        PhilosophersSim() {
            setBackground(new Color(238, 238, 238));
            for (int i = 0; i < N; i++) {
                state[i] = State.THINKING;
                chopstickOwner[i] = -1;
            }
        }

        @Override
        public void showSkeleton() {
            stopSimulation();
            methodTitle = "";
            for (int i = 0; i < N; i++) {
                state[i] = State.THINKING;
                chopstickOwner[i] = -1;
            }
            repaint();
        }

        @Override
        public void startWith(SyncMethod method) {
            stopSimulation();
            methodTitle = (method == SyncMethod.MUTEX ? "Mutex" : "Semáforos");
            running.set(true);
            repaintTimer.start();
            if (method == SyncMethod.MUTEX) {
                diningMutex = new ReentrantLock(true);
            } else {
                fork = new Semaphore[N];
                for (int i = 0; i < N; i++) {
                    fork[i] = new Semaphore(1, true);
                }
                waiter = new Semaphore(N - 1, true);
            }
            for (int i = 0; i < N; i++) {
                final int id = i;
                threads[i] = new Thread(() -> life(id, method), "Philosopher-" + id);
                threads[i].setDaemon(true);
                threads[i].start();
            }
        }

        private void life(int id, SyncMethod method) {
            while (running.get()) {
                state[id] = State.THINKING;
                sleepRand(400, 1000);
                state[id] = State.HUNGRY;
                int left = id;
                int right = (id + 1) % N;
                try {
                    if (method == SyncMethod.MUTEX) {
                        diningMutex.lock();
                        try {
                            chopstickOwner[left] = chopstickOwner[right] = id;
                            state[id] = State.EATING;
                            sleepRand(500, 900);
                            chopstickOwner[left] = chopstickOwner[right] = -1;
                        } finally {
                            diningMutex.unlock();
                        }
                    } else {
                        waiter.acquire();
                        fork[left].acquire();
                        fork[right].acquire();
                        chopstickOwner[left] = chopstickOwner[right] = id;
                        state[id] = State.EATING;
                        sleepRand(500, 900);
                        chopstickOwner[left] = chopstickOwner[right] = -1;
                        fork[right].release();
                        fork[left].release();
                        waiter.release();
                    }
                } catch (InterruptedException ignored) {
                }
                sleepRand(200, 500);
            }
        }

        @Override
        public void stopSimulation() {
            running.set(false);
            for (Thread t : threads) {
                if (t != null) {
                    t.interrupt();
                }
            }
            repaintTimer.stop();
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        private void sleepRand(int minMs, int maxMs) {
            try {
                Thread.sleep(minMs + (int) (Math.random() * (maxMs - minMs)));
            } catch (InterruptedException ignored) {
            }
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
                int tw = g2.getFontMetrics().stringWidth(methodTitle);
                g2.drawString(methodTitle, (w - tw) / 2, (int) (h * 0.06));
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

    // ===== Fumadores =====
    static class SmokersSim extends JPanel implements SimPanel {

        enum Ing {
            TABACO, PAPEL, CERILLOS
        }

        enum SState {
            ESPERANDO, ARMANDO, FUMANDO
        }
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final Timer repaintTimer = new Timer(60, e -> repaint());
        private Thread agentThread;
        private final Thread[] smokerThreads = new Thread[3];
        private volatile Ing i1 = null, i2 = null;
        private volatile int activeSmoker = -1;
        private final SState[] sstate = {SState.ESPERANDO, SState.ESPERANDO, SState.ESPERANDO};
        private String methodTitle = "";
        // MUTEX + Conditions
        private ReentrantLock mtx = null;
        private Condition tableEmpty = null, tableFull = null;
        private Condition smokerDone = null;
        // SEMÁFOROS
        private Semaphore agent = null;
        private final Semaphore[] smokerSem = {null, null, null};

        private void sleepRand(int a, int b) {
            try {
                Thread.sleep(a + (int) (Math.random() * (b - a)));
            } catch (InterruptedException ignored) {
            }
        }

        SmokersSim() {
            setBackground(new Color(238, 238, 238));
        }

        @Override
        public void showSkeleton() {
            stopSimulation();
            methodTitle = "";
            i1 = i2 = null;
            activeSmoker = -1;
            for (int i = 0; i < 3; i++) {
                sstate[i] = SState.ESPERANDO;
            }
            repaint();
        }

        @Override
        public void startWith(SyncMethod method) {
            stopSimulation();
            methodTitle = (method == SyncMethod.MUTEX ? "Mutex" : "Semáforos");
            running.set(true);
            repaintTimer.start();
            if (method == SyncMethod.MUTEX) {
                mtx = new ReentrantLock(true);
                tableEmpty = mtx.newCondition();
                tableFull = mtx.newCondition();
                smokerDone = mtx.newCondition();
                agentThread = new Thread(() -> {
                    while (running.get()) {
                        try {
                            mtx.lock();
                            try {
                                while (!tableIsEmpty() || activeSmoker != -1) {
                                    tableEmpty.await();
                                }
                                putRandomPair();
                                tableFull.signalAll();
                            } finally {
                                mtx.unlock();
                            }
                            sleepRand(80, 140);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "AgentMutex");
                agentThread.setDaemon(true);
                agentThread.start();
                for (int id = 0; id < 3; id++) {
                    final int who = id;
                    smokerThreads[id] = new Thread(() -> {
                        while (running.get()) {
                            try {
                                mtx.lock();
                                try {
                                    while (!canSmoke(who) || activeSmoker != -1) {
                                        tableFull.await();
                                    }
                                    // tomar la mesa
                                    i1 = i2 = null;
                                    activeSmoker = who;
                                    sstate[who] = SState.ARMANDO;
                                } finally {
                                    mtx.unlock();
                                }
                                sleepRand(500, 900);
                                mtx.lock();
                                try {
                                    sstate[who] = SState.FUMANDO;
                                } finally {
                                    mtx.unlock();
                                }
                                sleepRand(800, 1400);
                                mtx.lock();
                                try {
                                    sstate[who] = SState.ESPERANDO;
                                    activeSmoker = -1;
                                    tableEmpty.signalAll();
                                    smokerDone.signalAll();
                                } finally {
                                    mtx.unlock();
                                }
                                Thread.sleep(120 + (int) (Math.random() * 220));
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }, "SmokerMutex-" + who);
                    smokerThreads[id].setDaemon(true);
                    smokerThreads[id].start();
                }
            } else {
                agent = new Semaphore(1, true);
                for (int i = 0; i < 3; i++) {
                    smokerSem[i] = new Semaphore(0, true);
                }
                agentThread = new Thread(() -> {
                    while (running.get()) {
                        try {
                            agent.acquire();
                            int pick = (int) (Math.random() * 3);
                            switch (pick) {
                                case 0 -> {
                                    i1 = Ing.PAPEL;
                                    i2 = Ing.CERILLOS;
                                    smokerSem[0].release();
                                }
                                case 1 -> {
                                    i1 = Ing.TABACO;
                                    i2 = Ing.CERILLOS;
                                    smokerSem[1].release();
                                }
                                default -> {
                                    i1 = Ing.TABACO;
                                    i2 = Ing.PAPEL;
                                    smokerSem[2].release();
                                }
                            }
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "AgentSem");
                agentThread.setDaemon(true);
                agentThread.start();
                for (int id = 0; id < 3; id++) {
                    final int who = id;
                    smokerThreads[id] = new Thread(() -> {
                        while (running.get()) {
                            try {
                                smokerSem[who].acquire();
                                activeSmoker = who;
                                sstate[who] = SState.ARMANDO;
                                sleepRand(500, 900);
                                sstate[who] = SState.FUMANDO;
                                sleepRand(800, 1400);
                                sstate[who] = SState.ESPERANDO;
                                activeSmoker = -1;
                                i1 = i2 = null;
                                agent.release();
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }, "SmokerSem-" + who);
                    smokerThreads[id].setDaemon(true);
                    smokerThreads[id].start();
                }
            }
        }

        private boolean tableIsEmpty() {
            return i1 == null && i2 == null;
        }

        private void putRandomPair() {
            int pick = (int) (Math.random() * 3);
            switch (pick) {
                case 0 -> {
                    i1 = Ing.PAPEL;
                    i2 = Ing.CERILLOS;
                }
                case 1 -> {
                    i1 = Ing.TABACO;
                    i2 = Ing.CERILLOS;
                }
                default -> {
                    i1 = Ing.TABACO;
                    i2 = Ing.PAPEL;
                }
            }
        }

        private boolean canSmoke(int me) {
            return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS)) || (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS)) || (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));
        }

        private boolean pairIs(Ing a, Ing b) {
            return (i1 == a && i2 == b) || (i1 == b && i2 == a);
        }

        @Override
        public void stopSimulation() {
            running.set(false);
            if (agentThread != null) {
                agentThread.interrupt();
            }
            for (Thread t : smokerThreads) {
                if (t != null) {
                    t.interrupt();
                }
            }
            repaintTimer.stop();
            i1 = i2 = null;
            activeSmoker = -1;
            for (int i = 0; i < 3; i++) {
                sstate[i] = SState.ESPERANDO;
            }
            repaint();
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
            int cx = (int) (w * 0.50), cy = (int) (h * 0.52);
            if (!methodTitle.isEmpty()) {
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                String t = methodTitle;
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
                }, px - 32, py - 8);
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
            g2.drawString(s, x - fm.stringWidth(s) / 2, y);
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
                    g2.fillRoundRect(x, y, 22, 8, 6, 6);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(x, y, 22, 8, 6, 6);
                    g2.drawLine(x + 6, y, x + 10, y - 6);
                }
                case CERILLOS -> {
                    g2.setColor(new Color(220, 40, 40));
                    g2.fillRoundRect(x, y, 18, 12, 4, 4);
                    g2.setColor(new Color(250, 210, 60));
                    g2.fillRect(x + 2, y + 2, 14, 4);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(x, y, 18, 12, 4, 4);
                }
            }
        }
    }

    // ===== Barbero Dormilón =====
    static class SleepingBarberSim extends JPanel implements SimPanel {

        private static final int MAX_WAIT_CHAIRS = 5;
        private static final int CUSTOMER_SIZE = 34;

        private enum BarberState {
            SLEEPING, CUTTING, IDLE
        }

        private enum CustState {
            ENTERING, WAITING, TO_CHAIR, CUTTING, LEAVING, DONE
        }

        private static class Customer {

            double x, y, tx, ty;
            CustState state;
            int seatIndex = -1;
            Color color;
        }
        private final List<Customer> customers = new ArrayList<>();
        private final Customer[] seats = new Customer[MAX_WAIT_CHAIRS];
        private Customer inChair = null;
        private BarberState barber = BarberState.SLEEPING;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread generator, barberLoop;
        private final Timer repaintTimer = new Timer(60, e -> stepAndRepaint());
        private String methodTitle = "";
        // MUTEX + Conditions (para cola)
        private ReentrantLock mutex = null;
        private Condition seatsChanged = null;
        // SEMÁFOROS
        private Semaphore customersSem = null, barberSem = null;
        private ReentrantLock accessSeats = null;
        private int waiting = 0;

        SleepingBarberSim() {
            setBackground(new Color(238, 238, 238));
        }

        @Override
        public void showSkeleton() {
            stopSimulation();
            methodTitle = "";
            customers.clear();
            for (int i = 0; i < seats.length; i++) {
                seats[i] = null;
            }
            inChair = null;
            barber = BarberState.SLEEPING;
            repaint();
        }

        @Override
        public void startWith(SyncMethod method) {
            stopSimulation();
            methodTitle = (method == SyncMethod.MUTEX ? "Mutex" : "Semáforos");
            running.set(true);
            repaintTimer.start();
            if (method == SyncMethod.MUTEX) {
                mutex = new ReentrantLock(true);
                seatsChanged = mutex.newCondition();
                generator = new Thread(() -> {
                    while (running.get()) {
                        Customer c = spawnCustomer();
                        mutex.lock();
                        try {
                            int idx = nextFreeSeat();
                            if (idx >= 0) {
                                seats[idx] = c;
                                c.seatIndex = idx;
                                Point p = seatPos(idx);
                                setTarget(c, p.x, p.y);
                                c.state = CustState.WAITING;
                                seatsChanged.signalAll();
                            } else {
                                c.state = CustState.LEAVING;
                                setTarget(c, getWidth() + 60, c.y);
                            }
                        } finally {
                            mutex.unlock();
                        }
                        sleepRand(600, 1500);
                    }
                }, "GeneratorMutex");
                generator.setDaemon(true);
                generator.start();

                barberLoop = new Thread(() -> {
                    while (running.get()) {
                        try {
                            mutex.lock();
                            try {
                                while (inChair != null && running.get()) {
                                    seatsChanged.await();
                                }
                                if (inChair == null) {
                                    int idx = nextOccupiedSeat();
                                    if (idx >= 0) {
                                        Customer c = seats[idx];
                                        seats[idx] = null;
                                        shiftSeatsLeft(idx + 1);
                                        moveCustomerToChair(c);
                                        inChair = c;
                                        barber = BarberState.CUTTING;
                                        seatsChanged.signalAll();
                                    } else {
                                        barber = BarberState.SLEEPING;
                                    }
                                }
                            } finally {
                                mutex.unlock();
                            }
                            Thread.sleep(120 + (int) (Math.random() * 220));
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "BarberMutex");
                barberLoop.setDaemon(true);
                barberLoop.start();

            } else {
                customersSem = new Semaphore(0, true);
                barberSem = new Semaphore(0, true);
                accessSeats = new ReentrantLock(true);
                waiting = 0;
                generator = new Thread(() -> {
                    while (running.get()) {
                        Customer c = spawnCustomer();
                        accessSeats.lock();
                        try {
                            if (waiting < MAX_WAIT_CHAIRS) {
                                seats[waiting] = c;
                                waiting++;
                                Point p = seatPos(waiting - 1);
                                setTarget(c, p.x, p.y);
                                c.state = CustState.WAITING;
                                customersSem.release();
                            } else {
                                c.state = CustState.LEAVING;
                                setTarget(c, getWidth() + 60, c.y);
                            }
                        } finally {
                            accessSeats.unlock();
                        }
                        sleepRand(600, 1500);
                    }
                }, "GeneratorSem");
                generator.setDaemon(true);
                generator.start();

                barberLoop = new Thread(() -> {
                    while (running.get()) {
                        try {
                            customersSem.acquire();
                            accessSeats.lock();
                            Customer c = null;
                            if (waiting > 0) {
                                c = seats[0];
                                for (int i = 1; i < waiting; i++) {
                                    seats[i - 1] = seats[i];
                                }
                                seats[waiting - 1] = null;
                                waiting--;
                            }
                            accessSeats.unlock();
                            if (c != null) {
                                moveCustomerToChair(c);
                                inChair = c;
                                barber = BarberState.CUTTING;
                                Thread.sleep(900 + (int) (Math.random() * 500));
                                inChair = null;
                                barberSem.release();
                            }
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "BarberSem");
                barberLoop.setDaemon(true);
                barberLoop.start();
            }
        }

        @Override
        public void stopSimulation() {
            running.set(false);
            if (generator != null) {
                generator.interrupt();
            }
            if (barberLoop != null) {
                barberLoop.interrupt();
            }
            repaintTimer.stop();
            customers.clear();
            for (int i = 0; i < seats.length; i++) {
                seats[i] = null;
            }
            inChair = null;
            barber = BarberState.SLEEPING;
            repaint();
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        private Customer spawnCustomer() {
            Customer c = new Customer();
            c.x = -40;
            c.y = getHeight() * 0.65;
            c.state = CustState.ENTERING;
            c.color = new Color(50 + rnd(180), 50 + rnd(180), 50 + rnd(180));
            synchronized (customers) {
                customers.add(c);
            }
            setTarget(c, 40, getHeight() * 0.65);
            return c;
        }

        private void moveCustomerToChair(Customer c) {
            c.state = CustState.TO_CHAIR;
            Point p = chairPos();
            setTarget(c, p.x, p.y);
            new Thread(() -> {
                sleepRand(900, 1500);
                c.state = CustState.LEAVING;
                setTarget(c, getWidth() + 60, c.y);
                inChair = null;
                try {
                    mutex.lock();
                    try {
                        if (seatsChanged != null) {
                            seatsChanged.signalAll();
                        }
                    } finally {
                        mutex.unlock();
                    }
                } catch (Exception ignored) {
                }
            }, "CutTimer").start();
        }

        private int nextOccupiedSeat() {
            for (int i = 0; i < seats.length; i++) {
                if (seats[i] != null) {
                    return i;
                }
            }
            return -1;
        }

        private int nextFreeSeat() {
            for (int i = 0; i < seats.length; i++) {
                if (seats[i] == null) {
                    return i;
                }
            }
            return -1;
        }

        private void shiftSeatsLeft(int from) {
            for (int i = from; i < seats.length; i++) {
                if (seats[i] != null) {
                    seats[i - 1] = seats[i];
                    seats[i - 1].seatIndex = i - 1;
                    Point p = seatPos(i - 1);
                    setTarget(seats[i - 1], p.x, p.y);
                    seats[i] = null;
                }
            }
        }

        private void setTarget(Customer c, double tx, double ty) {
            c.tx = tx;
            c.ty = ty;
        }

        private int rnd(int n) {
            return (int) (Math.random() * n);
        }

        private void sleepRand(int a, int b) {
            try {
                Thread.sleep(a + rnd(b - a));
            } catch (InterruptedException ignored) {
            }
        }

        private void stepAndRepaint() {
            List<Customer> toRemove = new ArrayList<>();
            synchronized (customers) {
                for (Customer c : customers) {
                    double vx = c.tx - c.x, vy = c.ty - c.y;
                    double dist = Math.hypot(vx, vy);
                    double speed = 6.0;
                    if (dist > 0.1) {
                        c.x += vx / dist * Math.min(speed, dist);
                        c.y += vy / dist * Math.min(speed, dist);
                    } else {
                        if (c.state == CustState.LEAVING) {
                            toRemove.add(c);
                            c.state = CustState.DONE;
                        }
                    }
                }
                customers.removeAll(toRemove);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (!methodTitle.isEmpty()) {
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                String t = methodTitle;
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

        private Point seatPos(int idx) {
            int w = getWidth(), h = getHeight();
            return new Point((int) (w * 0.18) + idx * 44, (int) (h * 0.22));
        }

        private Point chairPos() {
            int w = getWidth(), h = getHeight();
            return new Point((int) (w * 0.40), (int) (h * 0.58));
        }
    }

    // ===== Lectores–Escritores (RW) =====
    static class ReadersWritersSim extends JPanel implements SimPanel {

        enum Role {
            READER, WRITER
        }

        enum AState {
            ARRIVING, WAITING, READING, WRITING, LEAVING, DONE
        }

        static class Actor {

            Role role;
            AState state = AState.ARRIVING;
            double x, y, tx, ty;
            Color color;
        }
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final Timer timer = new Timer(60, e -> stepAndRepaint());
        private Thread spawner;
        private volatile int readersActive = 0;
        private volatile boolean writerActive = false;
        private volatile int readersWaiting = 0;
        private volatile int writersWaiting = 0;
        private final List<Actor> actors = new ArrayList<>();
        private String methodTitle = "";
        // MUTEX exclusivo
        private ReentrantLock mutex = null; // para modo Mutex (exclusión total)
        // SEMÁFOROS (lectores concurrentes)
        private Semaphore rw_mutex = null;
        private ReentrantLock rcountMutex = null;
        // POOL para evitar crear demasiados hilos (corrige OutOfMemoryError)
        private ExecutorService exec;

        ReadersWritersSim() {
            setBackground(new Color(238, 238, 238));
        }

        @Override
        public void showSkeleton() {
            stopSimulation();
            methodTitle = "";
            synchronized (actors) {
                actors.clear();
            }
            readersActive = readersWaiting = writersWaiting = 0;
            writerActive = false;
            repaint();
        }

        @Override
        public void startWith(SyncMethod method) {
            stopSimulation();
            methodTitle = (method == SyncMethod.MUTEX ? "Mutex" : "Semáforos");
            running.set(true);
            timer.start();
            // pool pequeño y daemon para tareas de acceso
            exec = Executors.newFixedThreadPool(6, r -> {
                Thread t = new Thread(r, "RW-actor");
                t.setDaemon(true);
                return t;
            });
            if (method == SyncMethod.MUTEX) {
                mutex = new ReentrantLock(true);
            } else {
                rw_mutex = new Semaphore(1, true);
                rcountMutex = new ReentrantLock(true);
            }
            spawner = new Thread(() -> {
                while (running.get()) {
                    if (actors.size() < 120) {
                        spawn(Math.random() < 0.65 ? Role.READER : Role.WRITER);
                    }
                    sleepRand(350, 900);
                }
            }, "Spawner");
            spawner.setDaemon(true);
            spawner.start();
        }

        @Override
        public void stopSimulation() {
            running.set(false);
            if (spawner != null) {
                spawner.interrupt();
            }
            if (exec != null) {
                exec.shutdownNow();
                exec = null;
            }
            timer.stop();
            synchronized (actors) {
                actors.clear();
            }
            readersActive = readersWaiting = writersWaiting = 0;
            writerActive = false;
            repaint();
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        private void spawn(Role r) {
            Actor a = new Actor();
            a.role = r;
            a.color = (r == Role.READER) ? new Color(90, 160, 255) : new Color(230, 90, 90);
            if (r == Role.READER) {
                a.x = getWidth() + 40;
                a.y = readerLaneY();
                setTarget(a, getWidth() - 40, a.y);
            } else {
                a.x = -40;
                a.y = writerLaneY();
                setTarget(a, 40, a.y);
            }
            synchronized (actors) {
                actors.add(a);
            }
        }

        private void requestAccess(Actor a) {
            if (exec == null) {
                return; // safety
            }
            exec.submit(() -> {
                try {
                    if (methodTitle.equals("Mutex")) {
                        mutex.lock();
                        try {
                            if (a.role == Role.READER) {
                                readersActive = 1;
                                writerActive = false;
                                a.state = AState.READING;
                                setTarget(a, docCenter().x + 70, docCenter().y);
                                Thread.sleep(700 + (int) (Math.random() * 500));
                                a.state = AState.LEAVING;
                                setTarget(a, getWidth() + 60, a.y);
                                readersActive = 0;
                            } else {
                                writerActive = true;
                                a.state = AState.WRITING;
                                setTarget(a, docCenter().x - 70, docCenter().y);
                                Thread.sleep(900 + (int) (Math.random() * 500));
                                a.state = AState.LEAVING;
                                setTarget(a, -60, a.y);
                                writerActive = false;
                            }
                        } finally {
                            mutex.unlock();
                        }
                    } else {
                        if (a.role == Role.READER) {
                            rcountMutex.lock();
                            try {
                                readersWaiting++;
                                if (readersActive == 0) {
                                    rw_mutex.acquire();
                                }
                                readersWaiting--;
                                readersActive++;
                            } finally {
                                rcountMutex.unlock();
                            }
                            writerActive = false;
                            a.state = AState.READING;
                            setTarget(a, docCenter().x + 70 + (Math.random() > 0.5 ? 15 : -15), docCenter().y);
                            Thread.sleep(700 + (int) (Math.random() * 500));
                            a.state = AState.LEAVING;
                            setTarget(a, getWidth() + 60, a.y);
                            rcountMutex.lock();
                            try {
                                readersActive--;
                                if (readersActive == 0) {
                                    rw_mutex.release();
                                }
                            } finally {
                                rcountMutex.unlock();
                            }
                        } else {
                            writersWaiting++;
                            rw_mutex.acquire();
                            writersWaiting--;
                            writerActive = true;
                            a.state = AState.WRITING;
                            setTarget(a, docCenter().x - 70, docCenter().y);
                            Thread.sleep(900 + (int) (Math.random() * 500));
                            a.state = AState.LEAVING;
                            setTarget(a, -60, a.y);
                            writerActive = false;
                            rw_mutex.release();
                        }
                    }
                } catch (InterruptedException ignored) {
                }
            });
        }

        private void setTarget(Actor a, double tx, double ty) {
            a.tx = tx;
            a.ty = ty;
        }

        private void stepAndRepaint() {
            List<Actor> toRemove = new ArrayList<>();
            synchronized (actors) {
                for (Actor a : actors) {
                    double vx = a.tx - a.x, vy = a.ty - a.y;
                    double d = Math.hypot(vx, vy);
                    double sp = 6.0;
                    if (d > 0.1) {
                        a.x += vx / d * Math.min(sp, d);
                        a.y += vy / d * Math.min(sp, d);
                    } else if (a.state == AState.ARRIVING) {
                        requestAccess(a);
                    } else if (a.state == AState.LEAVING) {
                        a.state = AState.DONE;
                        toRemove.add(a);
                    }
                }
                actors.removeAll(toRemove);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (!methodTitle.isEmpty()) {
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                String t = methodTitle;
                g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, (int) (h * 0.06));
            }
            Rectangle doc = docRect();
            g2.setColor(writerActive ? new Color(255, 180, 180) : (readersActive > 0 ? new Color(190, 235, 190) : new Color(240, 240, 240)));
            g2.fillRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(doc.x, doc.y, doc.width, doc.height, 12, 12);
            g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
            String title = writerActive ? "(ESCRITURA)" : (readersActive > 0 ? "(LECTURA)" : "(libre)");
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
            g2.drawString(a.role == Role.READER ? "L" : "E", (int) a.x - 4, (int) a.y + 4);
        }

        private Rectangle docRect() {
            Point c = docCenter();
            int w = (int) (getWidth() * 0.40), h = (int) (getHeight() * 0.36);
            return new Rectangle(c.x - w / 2, c.y - h / 2, w, h);
        }

        private Point docCenter() {
            return new Point((int) (getWidth() * 0.50), (int) (getHeight() * 0.55));
        }

        private int readerQueueY() {
            return (int) (getHeight() * 0.28);
        }

        private int writerQueueY() {
            return (int) (getHeight() * 0.28);
        }

        private int writerLaneY() {
            return (int) (getHeight() * 0.75);
        }

        private int readerLaneY() {
            return (int) (getHeight() * 0.75);
        }

        private void drawCentered(Graphics2D g2, String s, int x, int y) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, x - fm.stringWidth(s) / 2, y);
        }

        private void sleepRand(int a, int b) {
            try {
                Thread.sleep(a + (int) (Math.random() * (b - a)));
            } catch (InterruptedException ignored) {
            }
        }
    }

    // ===== Panel de NODOS (70%) =====
    static class DrawingPanel extends JPanel implements MouseListener, MouseMotionListener {

        GraphData data = new GraphData();
        private ShapeNode dragging = null;
        private int offX, offY;
        private ShapeNode hoveredTarget = null;
        private final JPopupMenu createMenu = new JPopupMenu();
        private Point createAt = new Point();
        private final JPopupMenu nodeMenu = new JPopupMenu();
        private ShapeNode nodeMenuTarget = null;

        DrawingPanel() {
            setBackground(Color.WHITE);
            JMenuItem crearProceso = new JMenuItem("Proceso");
            crearProceso.addActionListener(e -> createNode(NodeType.PROCESO, createAt.x, createAt.y));
            JMenuItem crearRecurso = new JMenuItem("Recurso");
            crearRecurso.addActionListener(e -> createNode(NodeType.RECURSO, createAt.x, createAt.y));
            createMenu.add(crearProceso);
            createMenu.add(crearRecurso);
            JMenuItem eliminar = new JMenuItem("Eliminar");
            eliminar.addActionListener(e -> {
                if (nodeMenuTarget != null) {
                    data.connections.removeIf(c -> c.fromId == nodeMenuTarget.id || c.toId == nodeMenuTarget.id);
                    data.nodes.remove(nodeMenuTarget);
                    nodeMenuTarget = null;
                    repaint();
                }
            });
            nodeMenu.add(eliminar);
            addMouseListener(this);
            addMouseMotionListener(this);
        }

        void setData(GraphData g) {
            data = g;
            dragging = hoveredTarget = nodeMenuTarget = null;
            repaint();
        }

        private void createNode(NodeType type, int x, int y) {
            ShapeNode n = new ShapeNode();
            n.id = data.nextId++;
            n.type = type;
            n.size = 100;
            n.x = x;
            n.y = y;
            n.label = (type == NodeType.PROCESO ? "P" + data.nextProceso++ : "R" + data.nextRecurso++);
            data.nodes.add(n);
            repaint();
        }

        private Optional<ShapeNode> findNodeAt(int x, int y) {
            for (int i = data.nodes.size() - 1; i >= 0; i--) {
                if (data.nodes.get(i).contains(x, y)) {
                    return Optional.of(data.nodes.get(i));
                }
            }
            return Optional.empty();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Connection c : data.connections) {
                ShapeNode from = data.nodes.stream().filter(n -> n.id == c.fromId).findFirst().orElse(null);
                ShapeNode to = data.nodes.stream().filter(n -> n.id == c.toId).findFirst().orElse(null);
                if (from == null || to == null) {
                    continue;
                }
                drawArrow(g2, from, to);
                int mx = (from.x + to.x) / 2;
                int my = (from.y + to.y) / 2;
                g2.drawString(c.kind, mx + 6, my - 6);
            }
            for (ShapeNode n : data.nodes) {
                drawNode(g2, n, n == hoveredTarget);
            }
            if (dragging != null && hoveredTarget != null && hoveredTarget != dragging) {
                g2.setColor(new Color(0, 0, 0, 120));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 6}, 0));
                Point pStart = edgePointTowards(dragging, new Point(hoveredTarget.x, hoveredTarget.y));
                Point pEnd = edgePointTowards(hoveredTarget, new Point(dragging.x, dragging.y));
                g2.draw(new Line2D.Float(pStart.x, pStart.y, pEnd.x, pEnd.y));
            }
            g2.dispose();
        }

        private void drawNode(Graphics2D g2, ShapeNode n, boolean highlight) {
            int h = n.size / 2;
            if (n.type == NodeType.PROCESO) {
                if (highlight) {
                    g2.setColor(new Color(0, 128, 255, 60));
                    g2.fillOval(n.x - h, n.y - h, n.size, n.size);
                }
                g2.setColor(Color.BLACK);
                g2.drawOval(n.x - h, n.y - h, n.size, n.size);
            } else {
                if (highlight) {
                    g2.setColor(new Color(0, 128, 255, 60));
                    g2.fillRect(n.x - h, n.y - h, n.size, n.size);
                }
                g2.setColor(Color.BLACK);
                g2.drawRect(n.x - h, n.y - h, n.size, n.size);
            }
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(n.label);
            int th = fm.getAscent();
            g2.drawString(n.label, n.x - tw / 2, n.y + th / 4);
        }

        private void drawArrow(Graphics2D g2, ShapeNode from, ShapeNode to) {
            Point start = edgePointTowards(from, new Point(to.x, to.y));
            Point end = edgePointTowards(to, new Point(from.x, from.y));
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(start.x, start.y, end.x, end.y);
            drawArrowHead(g2, start, end);
        }

        private Point edgePointTowards(ShapeNode n, Point target) {
            double dx = target.x - n.x, dy = target.y - n.y;
            double ang = Math.atan2(dy, dx);
            int h = n.size / 2;
            if (n.type == NodeType.PROCESO) {
                int ex = n.x + (int) Math.round(Math.cos(ang) * h);
                int ey = n.y + (int) Math.round(Math.sin(ang) * h);
                return new Point(ex, ey);
            } else {
                double cos = Math.cos(ang), sin = Math.sin(ang);
                double t = Math.max(Math.abs(cos), Math.abs(sin));
                int ex = n.x + (int) Math.round((cos / t) * h);
                int ey = n.y + (int) Math.round((sin / t) * h);
                return new Point(ex, ey);
            }
        }

        private void drawArrowHead(Graphics2D g2, Point from, Point to) {
            double phi = Math.toRadians(25);
            int barb = 12;
            double dy = to.y - from.y, dx = to.x - from.x;
            double theta = Math.atan2(dy, dx);
            for (int j = 0; j < 2; j++) {
                double rho = theta + (j == 0 ? phi : -phi);
                double x = to.x - barb * Math.cos(rho);
                double y = to.y - barb * Math.sin(rho);
                g2.draw(new Line2D.Double(to.x, to.y, x, y));
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                Optional<ShapeNode> hit = findNodeAt(e.getX(), e.getY());
                if (hit.isPresent()) {
                    nodeMenuTarget = hit.get();
                    nodeMenu.show(this, e.getX(), e.getY());
                } else {
                    createAt = e.getPoint();
                    createMenu.show(this, e.getX(), e.getY());
                }
                return;
            }
            findNodeAt(e.getX(), e.getY()).ifPresent(n -> {
                dragging = n;
                offX = e.getX() - n.x;
                offY = e.getY() - n.y;
            });
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragging != null) {
                dragging.x = e.getX() - offX;
                dragging.y = e.getY() - offY;
                Optional<ShapeNode> over = findNodeAt(e.getX(), e.getY());
                hoveredTarget = (over.isPresent() && over.get() != dragging) ? over.get() : null;
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging != null) {
                Optional<ShapeNode> over = findNodeAt(e.getX(), e.getY());
                if (over.isPresent() && over.get() != dragging) {
                    ShapeNode a = dragging, b = over.get();
                    if (a.type == NodeType.PROCESO && b.type == NodeType.RECURSO) {
                        addConnection(a, b, "Solicitud");
                    } else if (a.type == NodeType.RECURSO && b.type == NodeType.PROCESO) {
                        addConnection(a, b, "Asignación");
                    }
                }
            }
            dragging = null;
            hoveredTarget = null;
            repaint();
        }

        private void addConnection(ShapeNode from, ShapeNode to, String kind) {
            Connection c = new Connection();
            c.fromId = from.id;
            c.toId = to.id;
            c.kind = kind;
            data.connections.add(c);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            setCursor(findNodeAt(e.getX(), e.getY()).isPresent() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }

        @Override
        public void mouseClicked(MouseEvent e) {
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }
    }
}
