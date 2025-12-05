package problemas;

import core.DrawingPanel;
import core.DrawingPanel.ChartKind;
import synch.VirtualAssistantsBarrierStrategy;
import synch.VirtualAssistantsConditionStrategy;
import synch.VirtualAssistantsMonitorStrategy;
import synch.VirtualAssistantsMutexStrategy;
import synch.VirtualAssistantsSemaphoreStrategy;
import synch.VirtualAssistantsStrategy;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualAssistantsSim extends JPanel implements SimPanel {

    private static final int ASSISTANT_COUNT = readPositiveSystemProperty("va.assistants", 8);
    private static final int SERVER_SLOTS = readPositiveSystemProperty("va.slots", 3);
    private static final int PRIORITY_TOKENS = readPositiveSystemProperty("va.tokens", 2);
    private static final Color HIGH_PRIORITY_COLOR = new Color(0x6C, 0x63, 0xF5);
    private static final Color LOW_PRIORITY_COLOR = new Color(0x1A, 0x9C, 0x82);
    private static final Color SERVER_COLOR = new Color(0x24, 0x3C, 0x5A);
    private static final Color TOKEN_COLOR = new Color(0xFF, 0xA0, 0x27);
    private static final long TOKEN_PULSE_DURATION_MS = 900;

    public enum AssistantState {
        IDLE, WAITING_TOKEN, HAS_TOKEN, WAITING_SLOT, PROCESSING, RESPONDING, RESTING
    }

    public static class AssistantAgent {
        private final int id;
        private final boolean highPriority;
        private final int laneIndex;
        private volatile AssistantState state = AssistantState.IDLE;
        private volatile double x;
        private volatile double y;
        private volatile double targetX;
        private volatile double targetY;
        private volatile int assignedSlot = -1;
        private volatile int assignedToken = -1;

        public AssistantAgent(int id, boolean highPriority, int laneIndex) {
            this.id = id;
            this.highPriority = highPriority;
            this.laneIndex = laneIndex;
        }

        public String getLabel() {
            return "AV" + id;
        }

        public boolean isHighPriority() {
            return highPriority;
        }

        public AssistantState getState() {
            return state;
        }

        public void setState(AssistantState state) {
            this.state = state;
        }

        public int getId() {
            return id;
        }
    }

    private final List<AssistantAgent> agents = new ArrayList<>();
    private final List<Thread> agentThreads = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Timer animationTimer;
    private final Timer performanceTimer;
    private final Random random = new Random();
    private final EnumSet<SyncMethod> trackedChartMethods = EnumSet.noneOf(SyncMethod.class);
    private final ChartSimulationPool chartPool = new ChartSimulationPool();
    private final List<TokenPulse> tokenPulses = Collections.synchronizedList(new ArrayList<>());

    private DrawingPanel drawingPanel;
    private VirtualAssistantsStrategy currentStrategy;
    private SyncMethod currentMethod = SyncMethod.NONE;
    private String methodTitle = "Asistentes Virtuales";
    private boolean skeletonVisible = true;
    private boolean chartActive = false;
    private ChartKind chartKind = null;

    public VirtualAssistantsSim() {
        setBackground(new Color(246, 248, 255));
        setOpaque(true);
        animationTimer = new Timer(32, e -> advanceAgents());
        animationTimer.setCoalesce(true);
        performanceTimer = new Timer(900, e -> pushPerformanceSample());
        performanceTimer.setCoalesce(true);
        performanceTimer.setInitialDelay(900);
        createAgents();
    }

    private static int readPositiveSystemProperty(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void createAgents() {
        agents.clear();
        int highCount = ASSISTANT_COUNT / 2;
        for (int i = 0; i < ASSISTANT_COUNT; i++) {
            boolean highPriority = i < highCount;
            int laneIndex = highPriority ? i : i - highCount;
            AssistantAgent agent = new AssistantAgent(i + 1, highPriority, laneIndex);
            Point2D fallback = fallbackIdlePosition(agent);
            agent.x = fallback.getX();
            agent.y = fallback.getY();
            agent.targetX = agent.x;
            agent.targetY = agent.y;
            agents.add(agent);
        }
    }

    private List<AssistantAgent> buildChartAgents() {
        List<AssistantAgent> roster = new ArrayList<>();
        int highCount = ASSISTANT_COUNT / 2;
        for (int i = 0; i < ASSISTANT_COUNT; i++) {
            boolean highPriority = i < highCount;
            int laneIndex = highPriority ? i : i - highCount;
            roster.add(new AssistantAgent(100 + i + 1, highPriority, laneIndex));
        }
        return roster;
    }

    @Override
    public void showSkeleton() {
        stopSimulation();
        skeletonVisible = true;
        methodTitle = "Asistentes Virtuales";
        resetAgentsToIdle();
        tokenPulses.clear();
        if (drawingPanel != null) {
            drawingPanel.setupVirtualAssistantsGraph(ASSISTANT_COUNT, SERVER_SLOTS, PRIORITY_TOKENS);
        }
        repaint();
        updatePerformanceTimer();
    }

    @Override
    public void startWith(SyncMethod method) {
        if (!supports(method)) {
            JOptionPane.showMessageDialog(this, "Este método de sincronización no aplica a los Asistentes Virtuales.", "Método no disponible", JOptionPane.WARNING_MESSAGE);
            return;
        }
        stopSimulation();
        skeletonVisible = false;
        methodTitle = describeMethod(method);
        currentMethod = method;
        currentStrategy = instantiateStrategy(method);
        if (currentStrategy == null) {
            JOptionPane.showMessageDialog(this, "La estrategia " + method + " aún no está implementada.", "Sin implementar", JOptionPane.ERROR_MESSAGE);
            currentMethod = SyncMethod.NONE;
            return;
        }
        resetAgentsToIdle();
        currentStrategy.start();
        running.set(true);
        enableMethodTracking(method);
        startAgents();
        animationTimer.start();
        updatePerformanceTimer();
        if (drawingPanel != null) {
            drawingPanel.setupVirtualAssistantsGraph(ASSISTANT_COUNT, SERVER_SLOTS, PRIORITY_TOKENS);
        }
    }

    @Override
    public void stopSimulation() {
        running.set(false);
        animationTimer.stop();
        performanceTimer.stop();
        for (Thread t : agentThreads) {
            t.interrupt();
        }
        agentThreads.clear();
        if (currentStrategy != null) {
            currentStrategy.stop();
            currentStrategy = null;
        }
        currentMethod = SyncMethod.NONE;
        tokenPulses.clear();
        updatePerformanceTimer();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public void handleChartSelection(ChartKind kind) {
        if (drawingPanel == null) {
            return;
        }
        chartKind = kind;
        chartActive = kind != null;
        if (chartActive) {
            drawingPanel.showVirtualAssistantsChart(kind);
            chartPool.ensureRunning(trackedChartMethods);
            chartPool.resetCounters();
        } else {
            drawingPanel.hideChart();
            chartPool.stopAll();
        }
        updatePerformanceTimer();
    }

    private void startAgents() {
        agentThreads.clear();
        for (AssistantAgent agent : agents) {
            Thread worker = new Thread(() -> runAssistant(agent), "VA-Agent-" + agent.getLabel());
            worker.start();
            agentThreads.add(worker);
        }
    }

    private void runAssistant(AssistantAgent agent) {
        Random local = new Random(agent.id * 31L + System.nanoTime());
        while (running.get() && currentStrategy != null) {
            int tokenIndex = -1;
            int slotIndex = -1;
            try {

                transition(agent, AssistantState.IDLE);
                Thread.sleep(350 + local.nextInt(500));

                transition(agent, AssistantState.WAITING_TOKEN);
                notifyGraphQueued(agent);
                tokenIndex = currentStrategy.acquirePriorityToken(agent);
                if (tokenIndex < 0) {
                    continue;
                }
                agent.assignedToken = tokenIndex;
                transition(agent, AssistantState.HAS_TOKEN);
                notifyGraphTokenGranted(agent);

                transition(agent, AssistantState.WAITING_SLOT);
                notifyGraphRequestingSlot(agent);
                slotIndex = currentStrategy.acquireServerSlot(agent);
                if (slotIndex < 0) {
                    continue;
                }
                agent.assignedSlot = slotIndex;
                transition(agent, AssistantState.PROCESSING);
                notifyGraphProcessing(agent);

                Thread.sleep(450 + local.nextInt(agent.isHighPriority() ? 450 : 700));
                transition(agent, AssistantState.RESPONDING);
                Thread.sleep(260 + local.nextInt(240));

                currentStrategy.releaseResources(agent, agent.assignedToken, agent.assignedSlot);
                agent.assignedSlot = -1;
                agent.assignedToken = -1;
                notifyGraphFinished(agent);
                transition(agent, AssistantState.RESTING);
                Thread.sleep(280 + local.nextInt(280));
            } catch (InterruptedException ex) {
                if (currentStrategy != null && (tokenIndex >= 0 || slotIndex >= 0)) {
                    currentStrategy.releaseResources(agent, tokenIndex, slotIndex);
                }
                Thread.currentThread().interrupt();
                break;
            }
        }
        transition(agent, AssistantState.IDLE);
    }

    private void updatePerformanceTimer() {
        if (chartActive && !trackedChartMethods.isEmpty()) {
            if (!performanceTimer.isRunning()) {
                performanceTimer.start();
            }
        } else {
            performanceTimer.stop();
        }
    }

    private void pushPerformanceSample() {
        if (!chartActive || drawingPanel == null || trackedChartMethods.isEmpty()) {
            return;
        }
        chartPool.ensureRunning(trackedChartMethods);
        double timePoint = drawingPanel.advanceVirtualAssistantTimeline();
        for (SyncMethod method : trackedChartMethods) {
            int completed = chartPool.drainCompleted(method);
            double base = performanceMultiplier(method);
            double noise = random.nextGaussian() * 0.25;
            double value = Math.max(0.1, completed * base + noise);
            drawingPanel.appendVirtualAssistantPerformanceSample(method, value, timePoint);
        }
    }

    private void enableMethodTracking(SyncMethod method) {
        if (method == null || method == SyncMethod.NONE) {
            return;
        }
        if (trackedChartMethods.add(method) && chartActive) {
            chartPool.ensureRunning(method);
        }
    }

    private double performanceMultiplier(SyncMethod method) {
        return switch (method) {
            case MONITORS -> 1.8;
            case VAR_COND -> 1.45;
            case SEMAPHORES -> 1.25;
            case BARRIERS -> 1.10;
            case MUTEX -> 0.95;
            default -> 1.0;
        };
    }

    private void transition(AssistantAgent agent, AssistantState state) {
        agent.setState(state);
    }

    private void resetAgentsToIdle() {
        for (AssistantAgent agent : agents) {
            agent.assignedSlot = -1;
            agent.assignedToken = -1;
            agent.setState(AssistantState.IDLE);
            Point2D fallback = fallbackIdlePosition(agent);
            agent.x = fallback.getX();
            agent.y = fallback.getY();
            agent.targetX = agent.x;
            agent.targetY = agent.y;
        }
    }

    private boolean supports(SyncMethod method) {
        return method == SyncMethod.MUTEX
                || method == SyncMethod.SEMAPHORES
                || method == SyncMethod.VAR_COND
                || method == SyncMethod.MONITORS
                || method == SyncMethod.BARRIERS;
    }

    private String describeMethod(SyncMethod method) {
        return switch (method) {
            case MUTEX -> "Mutex";
            case SEMAPHORES -> "Semáforos";
            case VAR_COND -> "Variables de Condición";
            case MONITORS -> "Monitores";
            case BARRIERS -> "Barreras";
            default -> "Método desconocido";
        };
    }

    private VirtualAssistantsStrategy instantiateStrategy(SyncMethod method) {
        return switch (method) {
            case MUTEX -> new VirtualAssistantsMutexStrategy(SERVER_SLOTS, PRIORITY_TOKENS);
            case SEMAPHORES -> new VirtualAssistantsSemaphoreStrategy(SERVER_SLOTS, PRIORITY_TOKENS);
            case VAR_COND -> new VirtualAssistantsConditionStrategy(SERVER_SLOTS, PRIORITY_TOKENS);
            case MONITORS -> new VirtualAssistantsMonitorStrategy(SERVER_SLOTS, PRIORITY_TOKENS);
            case BARRIERS -> new VirtualAssistantsBarrierStrategy(SERVER_SLOTS, PRIORITY_TOKENS);
            default -> null;
        };
    }

    private void advanceAgents() {
        Dimension size = getSize();
        int width = Math.max(size.width, 600);
        int height = Math.max(size.height, 420);
        for (AssistantAgent agent : agents) {
            Point2D target = computeTarget(agent, width, height);
            agent.targetX = target.getX();
            agent.targetY = target.getY();
            agent.x += (agent.targetX - agent.x) * 0.12;
            agent.y += (agent.targetY - agent.y) * 0.12;
        }
        repaint();
    }

    private Point2D computeTarget(AssistantAgent agent, int width, int height) {
        double startQueueX = width * 0.12;
        double highQueueY = height * 0.25;
        double lowQueueY = height * 0.65;
        double queueSpacing = Math.max(36, height * 0.05);
        double tokenX = width * 0.38;
        double slotX = width * 0.58;
        double serverX = width * 0.70;
        double restY = height * 0.88;

        return switch (agent.getState()) {
            case WAITING_TOKEN -> new Point2D.Double(tokenX - 80, (agent.isHighPriority() ? highQueueY : lowQueueY) + agent.laneIndex * queueSpacing);
            case HAS_TOKEN -> new Point2D.Double(tokenX, (agent.isHighPriority() ? highQueueY - 50 : lowQueueY + 50));
            case WAITING_SLOT -> new Point2D.Double(slotX - 40, height * 0.45 + agent.laneIndex * 12);
            case PROCESSING -> {
                double slotSpacing = 60;
                double baseY = height * 0.42;
                int slot = Math.max(0, agent.assignedSlot);
                yield new Point2D.Double(serverX + Math.cos(slot) * 30, baseY + slot * slotSpacing);
            }
            case RESPONDING -> new Point2D.Double(serverX + 70, height * 0.45);
            case RESTING -> new Point2D.Double(width * 0.2 + agent.laneIndex * 32, restY);
            case IDLE -> fallbackIdlePosition(agent);
        };
    }

    private Point2D fallbackIdlePosition(AssistantAgent agent) {
        double baseX = agent.isHighPriority() ? 80 : 110;
        double baseY = agent.isHighPriority() ? 120 : 260;
        return new Point2D.Double(baseX + agent.laneIndex * 36, baseY + agent.laneIndex * 12);
    }

    private void notifyGraphQueued(AssistantAgent agent) {
        if (drawingPanel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> drawingPanel.showVirtualAssistantQueued(agent.getLabel(), agent.isHighPriority()));
    }

    private void notifyGraphTokenGranted(AssistantAgent agent) {
        if (drawingPanel == null) {
            return;
        }
        tokenPulses.add(new TokenPulse(agent));
        SwingUtilities.invokeLater(() -> drawingPanel.showVirtualAssistantTokenGranted(agent.getLabel()));
    }

    private void notifyGraphRequestingSlot(AssistantAgent agent) {
        if (drawingPanel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> drawingPanel.showVirtualAssistantRequestingSlot(agent.getLabel()));
    }

    private void notifyGraphProcessing(AssistantAgent agent) {
        if (drawingPanel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> drawingPanel.showVirtualAssistantProcessing(agent.getLabel()));
    }

    private void notifyGraphFinished(AssistantAgent agent) {
        if (drawingPanel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> drawingPanel.showVirtualAssistantFinished(agent.getLabel()));
    }

    private final class ChartSimulationPool {
        private final EnumMap<SyncMethod, MethodChartSimulation> simulations = new EnumMap<>(SyncMethod.class);

        synchronized void ensureRunning(SyncMethod method) {
            if (method == null || method == SyncMethod.NONE) {
                return;
            }
            MethodChartSimulation simulation = simulations.computeIfAbsent(method, MethodChartSimulation::new);
            simulation.start();
        }

        synchronized void ensureRunning(Iterable<SyncMethod> methods) {
            for (SyncMethod method : methods) {
                ensureRunning(method);
            }
        }

        synchronized int drainCompleted(SyncMethod method) {
            MethodChartSimulation simulation = simulations.get(method);
            return simulation != null ? simulation.drainCompletedWindow() : 0;
        }

        synchronized void resetCounters() {
            for (MethodChartSimulation simulation : simulations.values()) {
                simulation.resetCounter();
            }
        }

        synchronized void stopAll() {
            for (MethodChartSimulation simulation : simulations.values()) {
                simulation.stop();
            }
            simulations.clear();
        }
    }

    private final class MethodChartSimulation {
        private final SyncMethod method;
        private final VirtualAssistantsStrategy strategy;
        private final List<AssistantAgent> chartAgents = new ArrayList<>();
        private final List<Thread> workers = new ArrayList<>();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicInteger windowCounter = new AtomicInteger(0);

        MethodChartSimulation(SyncMethod method) {
            this.method = method;
            this.strategy = instantiateStrategy(method);
            if (this.strategy != null) {
                this.chartAgents.addAll(buildChartAgents());
            }
        }

        void start() {
            if (strategy == null) {
                return;
            }
            if (!running.compareAndSet(false, true)) {
                return;
            }
            strategy.start();
            workers.clear();
            for (AssistantAgent agent : chartAgents) {
                agent.assignedSlot = -1;
                agent.assignedToken = -1;
                agent.setState(AssistantState.IDLE);
            }
            for (AssistantAgent agent : chartAgents) {
                Thread worker = new Thread(() -> runLoop(agent), "VA-Chart-" + method + "-" + agent.getLabel());
                worker.setDaemon(true);
                worker.start();
                workers.add(worker);
            }
        }

        void stop() {
            if (!running.compareAndSet(true, false)) {
                return;
            }
            for (Thread worker : workers) {
                worker.interrupt();
            }
            for (Thread worker : workers) {
                try {
                    worker.join(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            workers.clear();
            windowCounter.set(0);
            strategy.stop();
        }

        int drainCompletedWindow() {
            return windowCounter.getAndSet(0);
        }

        void resetCounter() {
            windowCounter.set(0);
        }

        private void runLoop(AssistantAgent agent) {
            Random local = new Random((long) agent.getId() * 97L + System.nanoTime());
            while (running.get()) {
                int tokenIndex = -1;
                int slotIndex = -1;
                try {
                    Thread.sleep(240 + local.nextInt(360));
                    tokenIndex = strategy.acquirePriorityToken(agent);
                    if (tokenIndex < 0) {
                        continue;
                    }
                    agent.assignedToken = tokenIndex;
                    Thread.sleep(60 + local.nextInt(120));
                    slotIndex = strategy.acquireServerSlot(agent);
                    if (slotIndex < 0) {
                        strategy.releaseResources(agent, tokenIndex, -1);
                        agent.assignedToken = -1;
                        continue;
                    }
                    agent.assignedSlot = slotIndex;
                    Thread.sleep(320 + local.nextInt(agent.isHighPriority() ? 320 : 460));
                    strategy.releaseResources(agent, tokenIndex, slotIndex);
                    agent.assignedToken = -1;
                    agent.assignedSlot = -1;
                    windowCounter.incrementAndGet();
                    Thread.sleep(160 + local.nextInt(240));
                } catch (InterruptedException ex) {
                    if (tokenIndex >= 0 || slotIndex >= 0) {
                        strategy.releaseResources(agent, tokenIndex, slotIndex);
                    }
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension size = getSize();
        int width = Math.max(size.width, 600);
        int height = Math.max(size.height, 420);

        drawServer(g2, width, height);
        drawTokens(g2, width, height);
        drawQueues(g2, width, height);
        drawAgents(g2);
        drawHeader(g2, width);

        g2.dispose();
    }

    private void drawServer(Graphics2D g2, int width, int height) {
        double serverWidth = width * 0.22;
        double serverHeight = height * 0.32;
        double x = width * 0.60;
        double y = height * 0.30;
        RoundRectangle2D serverBox = new RoundRectangle2D.Double(x, y, serverWidth, serverHeight, 28, 28);
        g2.setColor(new Color(0xE8, 0xED, 0xFF));
        g2.fill(serverBox);
        g2.setColor(SERVER_COLOR);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(serverBox);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
        g2.drawString("Servidor de Respuestas", (int) x + 16, (int) y + 26);
    }

    private void drawTokens(Graphics2D g2, int width, int height) {
        int boxWidth = 32;
        int spacing = 12;
        int startX = (int) (width * 0.34);
        int y = (int) (height * 0.18);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("Tokens de prioridad", startX, y - 10);
        List<Point2D> centers = new ArrayList<>();
        for (int i = 0; i < PRIORITY_TOKENS; i++) {
            int bx = startX + i * (boxWidth + spacing);
            g2.setColor(TOKEN_COLOR);
            g2.fillRoundRect(bx, y, boxWidth, boxWidth, 10, 10);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRoundRect(bx, y, boxWidth, boxWidth, 10, 10);
            g2.drawString("T" + (i + 1), bx + 8, y + boxWidth / 2 + 4);
            centers.add(new Point2D.Double(bx + boxWidth / 2.0, y + boxWidth / 2.0));
        }
        drawTokenPulses(g2, centers);
    }

    private void drawQueues(Graphics2D g2, int width, int height) {
        g2.setStroke(new BasicStroke(1.2f));
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
        int highY = (int) (height * 0.25);
        int lowY = (int) (height * 0.65);
        int queueWidth = (int) (width * 0.18);
        int queueHeight = 60;
        int x = (int) (width * 0.10);

        g2.setColor(new Color(0xE9, 0xE3, 0xFF));
        g2.fillRoundRect(x, highY, queueWidth, queueHeight, 16, 16);
        g2.setColor(HIGH_PRIORITY_COLOR.darker());
        g2.drawRoundRect(x, highY, queueWidth, queueHeight, 16, 16);
        g2.drawString("Cola prioridad alta", x + 12, highY + 36);

        g2.setColor(new Color(0xD6, 0xF5, 0xEE));
        g2.fillRoundRect(x, lowY, queueWidth, queueHeight, 16, 16);
        g2.setColor(LOW_PRIORITY_COLOR.darker());
        g2.drawRoundRect(x, lowY, queueWidth, queueHeight, 16, 16);
        g2.drawString("Cola prioridad baja", x + 12, lowY + 36);
    }

    private void drawAgents(Graphics2D g2) {
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
        for (AssistantAgent agent : agents) {
            Color fill = agent.isHighPriority() ? HIGH_PRIORITY_COLOR : LOW_PRIORITY_COLOR;
            g2.setColor(fill);
            int size = 26;
            int drawX = (int) Math.round(agent.x) - size / 2;
            int drawY = (int) Math.round(agent.y) - size / 2;
            g2.fillOval(drawX, drawY, size, size);
            g2.setColor(Color.WHITE);
            g2.drawString(agent.getLabel(), drawX - 4, drawY + size + 14);
        }
    }

    private void drawHeader(Graphics2D g2, int width) {
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 20f));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(methodTitle);
        g2.setColor(new Color(0x14, 0x1E, 0x3C));
        g2.drawString(methodTitle, (width - textWidth) / 2, 34);
        if (skeletonVisible) {
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
        }
    }

    private void drawTokenPulses(Graphics2D g2, List<Point2D> tokenCenters) {
        if (tokenCenters.isEmpty()) {
            return;
        }
        List<TokenPulse> snapshot;
        long now = System.currentTimeMillis();
        synchronized (tokenPulses) {
            if (tokenPulses.isEmpty()) {
                return;
            }
            tokenPulses.removeIf(pulse -> now - pulse.createdAt > TOKEN_PULSE_DURATION_MS);
            if (tokenPulses.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(tokenPulses);
        }
        g2.setStroke(new BasicStroke(2f));
        for (TokenPulse pulse : snapshot) {
            int tokenIndex = pulse.agent.assignedToken;
            if (tokenIndex < 0 || tokenIndex >= tokenCenters.size()) {
                continue;
            }
            Point2D tokenPoint = tokenCenters.get(tokenIndex);
            Point2D agentPoint = new Point2D.Double(pulse.agent.x, pulse.agent.y);
            float alpha = (float) Math.max(0.0, 1.0 - (now - pulse.createdAt) / (double) TOKEN_PULSE_DURATION_MS);
            Color arrowColor = new Color(255, 160, 39, (int) (alpha * 255));
            drawArrow(g2, agentPoint, tokenPoint, arrowColor);
        }
    }

    private void drawArrow(Graphics2D g2, Point2D from, Point2D to, Color color) {
        if (from == null || to == null) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double distance = Math.hypot(dx, dy);
        if (distance < 1.0) {
            return;
        }
        double ux = dx / distance;
        double uy = dy / distance;
        double startX = from.getX() + ux * 14;
        double startY = from.getY() + uy * 14;
        double endX = to.getX() - ux * 18;
        double endY = to.getY() - uy * 18;
        g2.setColor(color);
        g2.drawLine((int) startX, (int) startY, (int) endX, (int) endY);
        double arrowSize = 8;
        double angle = Math.atan2(endY - startY, endX - startX);
        double x1 = endX - arrowSize * Math.cos(angle - Math.PI / 6);
        double y1 = endY - arrowSize * Math.sin(angle - Math.PI / 6);
        double x2 = endX - arrowSize * Math.cos(angle + Math.PI / 6);
        double y2 = endY - arrowSize * Math.sin(angle + Math.PI / 6);
        g2.fillPolygon(new int[]{(int) endX, (int) x1, (int) x2}, new int[]{(int) endY, (int) y1, (int) y2}, 3);
    }

    private static final class TokenPulse {
        final AssistantAgent agent;
        final long createdAt;

        TokenPulse(AssistantAgent agent) {
            this.agent = agent;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
