package core;

import javax.swing.*;
import java.awt.*;
// ... (otras importaciones necesarias: MouseEvent, Line2D, ArrayList, Optional) ...
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Optional;

public class DrawingPanel extends JPanel implements MouseListener, MouseMotionListener {

    volatile GraphData data = new GraphData();
    // ... (otros campos: dragging, offX, offY, hoveredTarget, menus, etc.) ...
    private ShapeNode dragging = null;
    private int offX, offY;
    private ShapeNode hoveredTarget = null;
    private final JPopupMenu createMenu = new JPopupMenu();
    private Point createAt = new Point();
    private final JPopupMenu nodeMenu = new JPopupMenu();
    private ShapeNode nodeMenuTarget = null;

    DrawingPanel() {
        // ... (código del constructor igual que antes) ...
        setBackground(Color.WHITE);
        JMenuItem crearProceso = new JMenuItem("Proceso");
        crearProceso.addActionListener(e -> createNode(NodeType.PROCESO, createAt.x, createAt.y));
        JMenuItem crearRecurso = new JMenuItem("Recurso");
        crearRecurso.addActionListener(e -> createNode(NodeType.RECURSO, createAt.x, createAt.y));
        createMenu.add(crearProceso);
        createMenu.add(crearRecurso);
        JMenuItem eliminar = new JMenuItem("Eliminar");
        eliminar.addActionListener(e -> {
            synchronized (this.data) {
                if (nodeMenuTarget != null) {
                    int targetId = nodeMenuTarget.id;
                    if (data.connections != null) {
                        data.connections.removeIf(c -> c != null && (c.fromId == targetId || c.toId == targetId));
                    }
                    if (data.nodes != null) {
                        data.nodes.remove(nodeMenuTarget);
                    }
                    nodeMenuTarget = null;
                }
            }
            repaint();
        });
        nodeMenu.add(eliminar);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    // --- Métodos de manejo de grafo (igual que antes) ---
    synchronized void setData(GraphData g) {
        /* ... código ... */
        this.data = (g != null) ? g : new GraphData();
        dragging = null;
        hoveredTarget = null;
        nodeMenuTarget = null;
        repaint();
    }

    private synchronized void createNode(NodeType type, int x, int y) {
        /* ... código ... */
        if (data == null) {
            data = new GraphData();
        }
        if (data.nodes == null) {
            data.nodes = new ArrayList<>();
        }
        ShapeNode n = new ShapeNode();
        n.id = data.nextId++;
        n.type = type;
        n.size = 100;
        n.x = x;
        n.y = y;
        if (type == NodeType.PROCESO) {
            n.label = "P" + data.nextProceso++;
        } else {
            n.label = "R" + data.nextRecurso++;
        }
        data.nodes.add(n);
        repaint();
    }

    private synchronized Optional<ShapeNode> findNodeAt(int x, int y) {
        /* ... código ... */
        if (data == null || data.nodes == null) {
            return Optional.empty();
        }
        for (int i = data.nodes.size() - 1; i >= 0; i--) {
            ShapeNode n = data.nodes.get(i);
            if (n != null && n.contains(x, y)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    @Override
    protected void paintComponent(Graphics g) {
        /* ... código ... */
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GraphData currentData;
        synchronized (this.data) {
            if (this.data == null) {
                g2.dispose();
                return;
            }
            currentData = new GraphData();
            currentData.nodes = (this.data.nodes != null) ? new ArrayList<>(this.data.nodes) : new ArrayList<>();
            currentData.connections = (this.data.connections != null) ? new ArrayList<>(this.data.connections) : new ArrayList<>();
            currentData.nextId = this.data.nextId;
            currentData.nextProceso = this.data.nextProceso;
            currentData.nextRecurso = this.data.nextRecurso;
        }
        for (Connection c : currentData.connections) {
            if (c == null) {
                continue;
            }
            ShapeNode from = currentData.nodes.stream().filter(n -> n != null && n.id == c.fromId).findFirst().orElse(null);
            ShapeNode to = currentData.nodes.stream().filter(n -> n != null && n.id == c.toId).findFirst().orElse(null);
            if (from == null || to == null) {
                continue;
            }
            drawArrow(g2, from, to);
            int mx = (from.x + to.x) / 2;
            int my = (from.y + to.y) / 2;
            if (c.kind != null) {
                g2.drawString(c.kind, mx + 6, my - 6);
            }
        }
        for (ShapeNode n : currentData.nodes) {
            if (n != null) {
                drawNode(g2, n, n == this.hoveredTarget);
            }
        }
        ShapeNode currentDragging = this.dragging;
        ShapeNode currentHovered = this.hoveredTarget;
        if (currentDragging != null && currentHovered != null && currentHovered != currentDragging) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 6}, 0));
            Point pStart = edgePointTowards(currentDragging, new Point(currentHovered.x, currentHovered.y));
            Point pEnd = edgePointTowards(currentHovered, new Point(currentDragging.x, currentDragging.y));
            if (pStart != null && pEnd != null) {
                g2.draw(new Line2D.Float(pStart.x, pStart.y, pEnd.x, pEnd.y));
            }
        }
        g2.dispose();
    }

    public synchronized void clearGraph() {
        /* ... código ... */
        clearGraphInternal();
        SwingUtilities.invokeLater(this::repaint);
    }

    private synchronized void clearGraphInternal() {
        /* ... código ... */
        if (data != null) {
            if (data.nodes == null) {
                data.nodes = new ArrayList<>();
            } else {
                data.nodes.clear();
            }
            if (data.connections == null) {
                data.connections = new ArrayList<>();
            } else {
                data.connections.clear();
            }
            data.nextId = 1;
            data.nextProceso = 1;
            data.nextRecurso = 1;
            nodeMenuTarget = null;
            dragging = null;
            hoveredTarget = null;
        } else {
            data = new GraphData();
        }
    }

    private synchronized Optional<Integer> findNodeIdByLabel(String label) {
        /* ... código ... */
        if (data == null || data.nodes == null || label == null) {
            return Optional.empty();
        }
        return data.nodes.stream().filter(n -> n != null && label.equals(n.label)).map(n -> n.id).findFirst();
    }

    private synchronized void addNodeIfNotExists(String label, NodeType type, int x, int y) {
        /* ... código ... */
        if (label == null || data == null) {
            return;
        }
        if (data.nodes == null) {
            data.nodes = new ArrayList<>();
        }
        if (findNodeIdByLabel(label).isEmpty()) {
            ShapeNode n = new ShapeNode();
            n.id = data.nextId++;
            n.type = type;
            n.size = 80;
            n.x = x;
            n.y = y;
            n.label = label;
            data.nodes.add(n);
        }
    }

    private synchronized void removeConnectionsInvolving(String nodeLabel) {
        /* ... código ... */
        if (nodeLabel == null || data == null || data.connections == null) {
            return;
        }
        findNodeIdByLabel(nodeLabel).ifPresent(nodeId -> {
            data.connections.removeIf(c -> c != null && (c.fromId == nodeId || c.toId == nodeId));
        });
    }

    private synchronized void removeConnection(String fromLabel, String toLabel) {
        /* ... código ... */
        if (fromLabel == null || toLabel == null || data == null || data.connections == null) {
            return;
        }
        Optional<Integer> fromIdOpt = findNodeIdByLabel(fromLabel);
        Optional<Integer> toIdOpt = findNodeIdByLabel(toLabel);
        if (fromIdOpt.isPresent() && toIdOpt.isPresent()) {
            int fromId = fromIdOpt.get();
            int toId = toIdOpt.get();
            data.connections.removeIf(c -> c != null && c.fromId == fromId && c.toId == toId);
        }
    }

    private synchronized void removeBidirectional(String labelA, String labelB) {
        removeConnection(labelA, labelB);
        removeConnection(labelB, labelA);
    }

    private synchronized void clearProducerConditionLinks() {
        removeBidirectional("P1", "R_Lock");
        removeBidirectional("P1", "R_Buffer");
        removeBidirectional("P1", "Cond_NotFull");
        removeBidirectional("P1", "Cond_NotEmpty");
    }

    private synchronized void clearConsumerConditionLinks() {
        removeBidirectional("C1", "R_Lock");
        removeBidirectional("C1", "R_Buffer");
        removeBidirectional("C1", "Cond_NotFull");
        removeBidirectional("C1", "Cond_NotEmpty");
    }

    private synchronized void clearProducerMonitorLinks() {
        removeBidirectional("P1", "R_Monitor");
        removeBidirectional("P1", "R_Buffer");
        removeBidirectional("P1", "Cond_NotFull_M");
        removeBidirectional("P1", "Cond_NotEmpty_M");
    }

    private synchronized void clearConsumerMonitorLinks() {
        removeBidirectional("C1", "R_Monitor");
        removeBidirectional("C1", "R_Buffer");
        removeBidirectional("C1", "Cond_NotFull_M");
        removeBidirectional("C1", "Cond_NotEmpty_M");
    }

    private synchronized void clearBarrierLinks() {
        removeBidirectional("P1", "R_Barrier");
        removeBidirectional("C1", "R_Barrier");
        removeBidirectional("P1", "R_Buffer");
        removeBidirectional("C1", "R_Buffer");
        removeBidirectional("P1", "R_Token");
        removeBidirectional("C1", "R_Token");
    }

    private synchronized void clearPhilosopherMutexLinks(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        removeConnection(philosopherLabel, "R_Mutex");
        removeConnection("R_Mutex", philosopherLabel);
    }

    private synchronized void clearWaiterLink(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        removeConnection(philosopherLabel, "R_Waiter");
        removeConnection("R_Waiter", philosopherLabel);
    }

    private synchronized void clearForkLink(String philosopherLabel, String forkLabel) {
        if (philosopherLabel == null || forkLabel == null) {
            return;
        }
        removeConnection(philosopherLabel, forkLabel);
        removeConnection(forkLabel, philosopherLabel);
    }

    private synchronized void clearConditionLockLink(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        removeConnection(philosopherLabel, "R_Lock_Ph");
        removeConnection("R_Lock_Ph", philosopherLabel);
    }

    private synchronized void clearConditionWaitLink(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        String condLabel = "Cond_" + philosopherLabel;
        removeConnection(philosopherLabel, condLabel);
        removeConnection(condLabel, philosopherLabel);
    }

    private synchronized void clearMonitorLockLink(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        removeConnection(philosopherLabel, "R_Monitor_Ph");
        removeConnection("R_Monitor_Ph", philosopherLabel);
    }

    private synchronized void clearMonitorWaitLink(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        String condLabel = "CondM_" + philosopherLabel;
        removeConnection(philosopherLabel, condLabel);
        removeConnection(condLabel, philosopherLabel);
    }

    private synchronized void clearBarrierPhilosopherLinks(String philosopherLabel) {
        if (philosopherLabel == null) {
            return;
        }
        removeConnection(philosopherLabel, "R_Barrier_Ph");
        removeConnection("R_Barrier_Ph", philosopherLabel);
        removeConnection(philosopherLabel, "R_Token_Ph");
        removeConnection("R_Token_Ph", philosopherLabel);
    }

    private synchronized void clearSleepingBarberProcessLinks(String processLabel) {
        if (processLabel == null) {
            return;
        }
        removeConnection(processLabel, "R_Mutex_Barber");
        removeConnection("R_Mutex_Barber", processLabel);
    }

    private synchronized void clearSleepingBarberSemaphoreLinks(String processLabel) {
        if (processLabel == null) {
            return;
        }
        removeConnection(processLabel, "S_AccessSeats");
        removeConnection("S_AccessSeats", processLabel);
        removeConnection(processLabel, "S_Customers");
        removeConnection("S_Customers", processLabel);
        removeConnection(processLabel, "S_Barber");
        removeConnection("S_Barber", processLabel);
        removeConnection(processLabel, "R_WaitRoom");
        removeConnection("R_WaitRoom", processLabel);
    }

    private synchronized void clearSleepingBarberConditionLinks(String processLabel) {
        if (processLabel == null) {
            return;
        }
        removeConnection(processLabel, "R_Lock_SB");
        removeConnection("R_Lock_SB", processLabel);
        removeConnection(processLabel, "Cond_Customers");
        removeConnection("Cond_Customers", processLabel);
        removeConnection(processLabel, "R_WaitRoom");
        removeConnection("R_WaitRoom", processLabel);
    }

    private synchronized void clearSmokersMutexLinks(String processLabel) {
        if (processLabel == null) {
            return;
        }
        removeConnection(processLabel, "R_Mutex_Smokers");
        removeConnection("R_Mutex_Smokers", processLabel);
        removeConnection(processLabel, "R_Table_Smokers");
        removeConnection("R_Table_Smokers", processLabel);
    }

    private synchronized void clearSmokersSemaphoreAgentLinks() {
        removeConnection("Agent", "S_Agent_Smokers");
        removeConnection("S_Agent_Smokers", "Agent");
        removeConnection("Agent", "R_Table_Smokers");
        removeConnection("R_Table_Smokers", "Agent");
        removeConnection("Agent", "S_Smoker_Tabaco");
        removeConnection("Agent", "S_Smoker_Papel");
        removeConnection("Agent", "S_Smoker_Cerillos");
    }

    private synchronized void clearSmokersSemaphoreSmokerLinks(String smokerLabel, String semaphoreLabel) {
        if (smokerLabel == null || semaphoreLabel == null) {
            return;
        }
        removeConnection(smokerLabel, semaphoreLabel);
        removeConnection(semaphoreLabel, smokerLabel);
        removeConnection(smokerLabel, "R_Table_Smokers");
        removeConnection("R_Table_Smokers", smokerLabel);
    }

    private synchronized void clearSleepingBarberBarrierLinks(String processLabel) {
        if (processLabel == null) {
            return;
        }
        removeConnection(processLabel, "R_Barrier_SB");
        removeConnection("R_Barrier_SB", processLabel);
        removeConnection(processLabel, "R_Token_SB");
        removeConnection("R_Token_SB", processLabel);
        removeConnection(processLabel, "R_WaitRoom");
        removeConnection("R_WaitRoom", processLabel);
    }

    private synchronized void addConnectionIfNotExists(String fromLabel, String toLabel, String kind) {
        /* ... código ... */
        if (fromLabel == null || toLabel == null || kind == null || data == null) {
            return;
        }
        if (data.connections == null) {
            data.connections = new ArrayList<>();
        }
        Optional<Integer> fromIdOpt = findNodeIdByLabel(fromLabel);
        Optional<Integer> toIdOpt = findNodeIdByLabel(toLabel);
        if (fromIdOpt.isPresent() && toIdOpt.isPresent()) {
            int fromId = fromIdOpt.get();
            int toId = toIdOpt.get();
            data.connections.removeIf(c -> c != null && ((c.fromId == fromId && c.toId == toId) || (c.fromId == toId && c.toId == fromId)));
            Connection c = new Connection();
            c.fromId = fromId;
            c.toId = toId;
            c.kind = kind;
            data.connections.add(c);
        }
    }

    public synchronized void setupProducerConsumerGraph() {
        /* ... código ... */
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int topY = height / 4;
        int bottomY = 3 * height / 4;
        int midY = height / 2;
        addNodeIfNotExists("P1", NodeType.PROCESO, centerX - 150, midY);
        addNodeIfNotExists("C1", NodeType.PROCESO, centerX + 150, midY);
        addNodeIfNotExists("R_Mutex", NodeType.RECURSO, centerX, topY);
        addNodeIfNotExists("R_Buffer", NodeType.RECURSO, centerX, bottomY);
    }

    // --- Métodos específicos P-C Mutex (SIN DELAYS, CON LOGGING) ---
    public synchronized void showProducerRequestingMutex() {
        String from = "P1";
        String to = "R_Mutex";
        String kind = "Solicitud";
        removeConnectionsInvolving(from);
        addConnectionIfNotExists(from, to, kind);
        System.out.println("GRAPH: P1 -> R_Mutex (Solicitud). Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerHoldingMutex() {
        String from = "R_Mutex";
        String to = "P1";
        String kind = "Asignado";
        removeConnectionsInvolving(to); // Limpia P1
        addConnectionIfNotExists(from, to, kind);
        System.out.println("GRAPH: R_Mutex -> P1 (Asignado). Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerBlockedByBuffer() {
        String holder = "P1";
        String blockedBy = "R_Buffer";
        String kind = "Solicita Espacio";
        addConnectionIfNotExists("R_Mutex", holder, "Asignado"); // Asegura asignación mutex
        // Limpia otras conexiones del buffer
        removeConnection("C1", blockedBy);
        removeConnection(blockedBy, "C1");
        addConnectionIfNotExists(holder, blockedBy, kind);
        System.out.println("GRAPH: P1 -> R_Buffer (Bloqueado). Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerReleasingMutex() {
        String releaser = "P1";
        removeConnectionsInvolving(releaser);
        System.out.println("GRAPH: P1 libera R_Mutex. Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerRequestingMutex() {
        String from = "C1";
        String to = "R_Mutex";
        String kind = "Solicitud";
        removeConnectionsInvolving(from);
        addConnectionIfNotExists(from, to, kind);
        System.out.println("GRAPH: C1 -> R_Mutex (Solicitud). Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerHoldingMutex() {
        String from = "R_Mutex";
        String to = "C1";
        String kind = "Asignado";
        removeConnectionsInvolving(to); // Limpia C1
        addConnectionIfNotExists(from, to, kind);
        System.out.println("GRAPH: R_Mutex -> C1 (Asignado). Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerBlockedByBuffer() {
        String holder = "C1";
        String blockedBy = "R_Buffer";
        String kind = "Solicita Item";
        addConnectionIfNotExists("R_Mutex", holder, "Asignado"); // Asegura asignación mutex
        // Limpia otras conexiones del buffer
        removeConnection("P1", blockedBy);
        removeConnection(blockedBy, "P1");
        addConnectionIfNotExists(holder, blockedBy, kind);
        System.out.println("GRAPH: C1 -> R_Buffer (Bloqueado). Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerReleasingMutex() {
        String releaser = "C1";
        removeConnectionsInvolving(releaser);
        System.out.println("GRAPH: C1 libera R_Mutex. Connections: " + data.connections.size()); // LOGGING
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos específicos P-C Semáforos ---
    public synchronized void setupProducerConsumerSemaphoreGraph() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int topY = height / 5;
        int midY = height / 2;
        int bottomY = (int) (height * 0.78);
        addNodeIfNotExists("P1", NodeType.PROCESO, centerX - 220, midY);
        addNodeIfNotExists("C1", NodeType.PROCESO, centerX + 220, midY);
        addNodeIfNotExists("S_Empty", NodeType.RECURSO, centerX - 150, topY);
        addNodeIfNotExists("S_Mutex", NodeType.RECURSO, centerX, topY);
        addNodeIfNotExists("S_Full", NodeType.RECURSO, centerX + 150, topY);
        addNodeIfNotExists("R_Buffer", NodeType.RECURSO, centerX, bottomY);
    }

    public synchronized void showProducerWaitingEmptySemaphore() {
        removeBidirectional("P1", "S_Mutex");
        removeBidirectional("P1", "R_Buffer");
        removeBidirectional("P1", "S_Full");
        removeConnection("S_Empty", "P1");
        addConnectionIfNotExists("P1", "S_Empty", "Espera");
        System.out.println("GRAPH SEM: P1 espera S_Empty");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerAcquiredEmptySemaphore() {
        removeConnection("P1", "S_Empty");
        addConnectionIfNotExists("S_Empty", "P1", "Permiso");
        System.out.println("GRAPH SEM: S_Empty -> P1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerWaitingMutexSemaphore() {
        removeConnection("S_Mutex", "P1");
        removeBidirectional("P1", "R_Buffer");
        addConnectionIfNotExists("P1", "S_Mutex", "Espera");
        System.out.println("GRAPH SEM: P1 espera S_Mutex");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerHoldingMutexSemaphore() {
        removeConnection("P1", "S_Mutex");
        addConnectionIfNotExists("S_Mutex", "P1", "Permiso");
        System.out.println("GRAPH SEM: S_Mutex -> P1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerAccessingBufferSemaphore() {
        removeConnection("S_Empty", "P1");
        addConnectionIfNotExists("P1", "R_Buffer", "Produce");
        System.out.println("GRAPH SEM: P1 produce en R_Buffer");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerReleasingMutexSemaphore() {
        removeConnection("S_Mutex", "P1");
        removeConnection("P1", "R_Buffer");
        System.out.println("GRAPH SEM: P1 libera S_Mutex");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerSignalingFullSemaphore() {
        addConnectionIfNotExists("P1", "S_Full", "Senal");
        System.out.println("GRAPH SEM: P1 senaliza S_Full");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerIdleSemaphore() {
        removeConnectionsInvolving("P1");
        System.out.println("GRAPH SEM: P1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingFullSemaphore() {
        removeBidirectional("C1", "S_Mutex");
        removeBidirectional("C1", "R_Buffer");
        removeBidirectional("C1", "S_Empty");
        removeConnection("S_Full", "C1");
        addConnectionIfNotExists("C1", "S_Full", "Espera");
        System.out.println("GRAPH SEM: C1 espera S_Full");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerAcquiredFullSemaphore() {
        removeConnection("C1", "S_Full");
        addConnectionIfNotExists("S_Full", "C1", "Permiso");
        System.out.println("GRAPH SEM: S_Full -> C1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingMutexSemaphore() {
        removeConnection("S_Mutex", "C1");
        removeBidirectional("C1", "R_Buffer");
        addConnectionIfNotExists("C1", "S_Mutex", "Espera");
        System.out.println("GRAPH SEM: C1 espera S_Mutex");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerHoldingMutexSemaphore() {
        removeConnection("C1", "S_Mutex");
        addConnectionIfNotExists("S_Mutex", "C1", "Permiso");
        System.out.println("GRAPH SEM: S_Mutex -> C1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerAccessingBufferSemaphore() {
        removeConnection("S_Full", "C1");
        addConnectionIfNotExists("C1", "R_Buffer", "Consume");
        System.out.println("GRAPH SEM: C1 consume de R_Buffer");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerReleasingMutexSemaphore() {
        removeConnection("S_Mutex", "C1");
        removeConnection("C1", "R_Buffer");
        System.out.println("GRAPH SEM: C1 libera S_Mutex");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerSignalingEmptySemaphore() {
        addConnectionIfNotExists("C1", "S_Empty", "Senal");
        System.out.println("GRAPH SEM: C1 senaliza S_Empty");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerIdleSemaphore() {
        removeConnectionsInvolving("C1");
        System.out.println("GRAPH SEM: C1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos específicos P-C Variable Condición ---
    public synchronized void setupProducerConsumerConditionGraph() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int topY = height / 5;
        int midY = height / 2;
        int condY = topY + height / 10;
        int bottomY = (int) (height * 0.78);
        addNodeIfNotExists("P1", NodeType.PROCESO, centerX - 220, midY);
        addNodeIfNotExists("C1", NodeType.PROCESO, centerX + 220, midY);
        addNodeIfNotExists("R_Lock", NodeType.RECURSO, centerX, topY);
        addNodeIfNotExists("Cond_NotFull", NodeType.RECURSO, centerX - 160, condY);
        addNodeIfNotExists("Cond_NotEmpty", NodeType.RECURSO, centerX + 160, condY);
        addNodeIfNotExists("R_Buffer", NodeType.RECURSO, centerX, bottomY);
    }

    public synchronized void showProducerWaitingLockCondition() {
        clearProducerConditionLinks();
        addConnectionIfNotExists("P1", "R_Lock", "Espera");
        System.out.println("GRAPH COND: P1 espera lock");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerHoldingLockCondition() {
        clearProducerConditionLinks();
        addConnectionIfNotExists("R_Lock", "P1", "Asignado");
        System.out.println("GRAPH COND: R_Lock -> P1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerWaitingNotFullCondition() {
        clearProducerConditionLinks();
        addConnectionIfNotExists("P1", "Cond_NotFull", "Espera");
        System.out.println("GRAPH COND: P1 espera Cond_NotFull");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerSignaledByNotFullCondition() {
        clearProducerConditionLinks();
        addConnectionIfNotExists("Cond_NotFull", "P1", "Aviso");
        System.out.println("GRAPH COND: Cond_NotFull -> P1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerProducingCondition() {
        clearProducerConditionLinks();
        addConnectionIfNotExists("R_Lock", "P1", "Asignado");
        addConnectionIfNotExists("P1", "R_Buffer", "Produce");
        System.out.println("GRAPH COND: P1 produce");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerSignalingNotEmptyCondition() {
        clearProducerConditionLinks();
        addConnectionIfNotExists("R_Lock", "P1", "Asignado");
        addConnectionIfNotExists("P1", "Cond_NotEmpty", "Senal");
        System.out.println("GRAPH COND: P1 senaliza Cond_NotEmpty");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerReleasingLockCondition() {
        clearProducerConditionLinks();
        System.out.println("GRAPH COND: P1 libera lock");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerIdleCondition() {
        clearProducerConditionLinks();
        System.out.println("GRAPH COND: P1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingLockCondition() {
        clearConsumerConditionLinks();
        addConnectionIfNotExists("C1", "R_Lock", "Espera");
        System.out.println("GRAPH COND: C1 espera lock");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerHoldingLockCondition() {
        clearConsumerConditionLinks();
        addConnectionIfNotExists("R_Lock", "C1", "Asignado");
        System.out.println("GRAPH COND: R_Lock -> C1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingNotEmptyCondition() {
        clearConsumerConditionLinks();
        addConnectionIfNotExists("C1", "Cond_NotEmpty", "Espera");
        System.out.println("GRAPH COND: C1 espera Cond_NotEmpty");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerSignaledByNotEmptyCondition() {
        clearConsumerConditionLinks();
        addConnectionIfNotExists("Cond_NotEmpty", "C1", "Aviso");
        System.out.println("GRAPH COND: Cond_NotEmpty -> C1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerConsumingCondition() {
        clearConsumerConditionLinks();
        addConnectionIfNotExists("R_Lock", "C1", "Asignado");
        addConnectionIfNotExists("C1", "R_Buffer", "Consume");
        System.out.println("GRAPH COND: C1 consume");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerSignalingNotFullCondition() {
        clearConsumerConditionLinks();
        addConnectionIfNotExists("R_Lock", "C1", "Asignado");
        addConnectionIfNotExists("C1", "Cond_NotFull", "Senal");
        System.out.println("GRAPH COND: C1 senaliza Cond_NotFull");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerReleasingLockCondition() {
        clearConsumerConditionLinks();
        System.out.println("GRAPH COND: C1 libera lock");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerIdleCondition() {
        clearConsumerConditionLinks();
        System.out.println("GRAPH COND: C1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos específicos P-C Monitores ---
    public synchronized void setupProducerConsumerMonitorGraph() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int topY = height / 5;
        int midY = height / 2;
        int condY = topY + height / 10;
        int bottomY = (int) (height * 0.78);
        addNodeIfNotExists("P1", NodeType.PROCESO, centerX - 220, midY);
        addNodeIfNotExists("C1", NodeType.PROCESO, centerX + 220, midY);
        addNodeIfNotExists("R_Monitor", NodeType.RECURSO, centerX, topY);
        addNodeIfNotExists("Cond_NotFull_M", NodeType.RECURSO, centerX - 160, condY);
        addNodeIfNotExists("Cond_NotEmpty_M", NodeType.RECURSO, centerX + 160, condY);
        addNodeIfNotExists("R_Buffer", NodeType.RECURSO, centerX, bottomY);
    }

    public synchronized void showProducerWaitingMonitor() {
        clearProducerMonitorLinks();
        addConnectionIfNotExists("P1", "R_Monitor", "Espera");
        System.out.println("GRAPH MON: P1 espera Monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerInMonitor() {
        clearProducerMonitorLinks();
        addConnectionIfNotExists("R_Monitor", "P1", "Dentro");
        System.out.println("GRAPH MON: Monitor -> P1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerWaitingNotFullMonitor() {
        clearProducerMonitorLinks();
        addConnectionIfNotExists("P1", "Cond_NotFull_M", "Wait");
        System.out.println("GRAPH MON: P1 espera Cond_NotFull_M");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerSignaledNotFullMonitor() {
        clearProducerMonitorLinks();
        addConnectionIfNotExists("Cond_NotFull_M", "P1", "Signal");
        System.out.println("GRAPH MON: Cond_NotFull_M -> P1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerProducingMonitor() {
        clearProducerMonitorLinks();
        addConnectionIfNotExists("R_Monitor", "P1", "Dentro");
        addConnectionIfNotExists("P1", "R_Buffer", "Produce");
        System.out.println("GRAPH MON: P1 produce");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerSignalNotEmptyMonitor() {
        clearProducerMonitorLinks();
        addConnectionIfNotExists("R_Monitor", "P1", "Dentro");
        addConnectionIfNotExists("P1", "Cond_NotEmpty_M", "Signal");
        System.out.println("GRAPH MON: P1 signal Cond_NotEmpty_M");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerExitMonitor() {
        clearProducerMonitorLinks();
        System.out.println("GRAPH MON: P1 sale Monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerIdleMonitor() {
        clearProducerMonitorLinks();
        System.out.println("GRAPH MON: P1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingMonitor() {
        clearConsumerMonitorLinks();
        addConnectionIfNotExists("C1", "R_Monitor", "Espera");
        System.out.println("GRAPH MON: C1 espera Monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerInMonitor() {
        clearConsumerMonitorLinks();
        addConnectionIfNotExists("R_Monitor", "C1", "Dentro");
        System.out.println("GRAPH MON: Monitor -> C1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingNotEmptyMonitor() {
        clearConsumerMonitorLinks();
        addConnectionIfNotExists("C1", "Cond_NotEmpty_M", "Wait");
        System.out.println("GRAPH MON: C1 espera Cond_NotEmpty_M");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerSignaledNotEmptyMonitor() {
        clearConsumerMonitorLinks();
        addConnectionIfNotExists("Cond_NotEmpty_M", "C1", "Signal");
        System.out.println("GRAPH MON: Cond_NotEmpty_M -> C1");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerConsumingMonitor() {
        clearConsumerMonitorLinks();
        addConnectionIfNotExists("R_Monitor", "C1", "Dentro");
        addConnectionIfNotExists("C1", "R_Buffer", "Consume");
        System.out.println("GRAPH MON: C1 consume");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerSignalNotFullMonitor() {
        clearConsumerMonitorLinks();
        addConnectionIfNotExists("R_Monitor", "C1", "Dentro");
        addConnectionIfNotExists("C1", "Cond_NotFull_M", "Signal");
        System.out.println("GRAPH MON: C1 signal Cond_NotFull_M");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerExitMonitor() {
        clearConsumerMonitorLinks();
        System.out.println("GRAPH MON: C1 sale Monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerIdleMonitor() {
        clearConsumerMonitorLinks();
        System.out.println("GRAPH MON: C1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos específicos P-C Barreras ---
    public synchronized void setupProducerConsumerBarrierGraph() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int midY = height / 2;
        int topY = height / 5;
        int bottomY = (int) (height * 0.78);
        addNodeIfNotExists("P1", NodeType.PROCESO, centerX - 220, midY);
        addNodeIfNotExists("C1", NodeType.PROCESO, centerX + 220, midY);
        addNodeIfNotExists("R_Buffer", NodeType.RECURSO, centerX, bottomY);
        addNodeIfNotExists("R_Barrier", NodeType.RECURSO, centerX, topY);
        addNodeIfNotExists("R_Token", NodeType.RECURSO, centerX, midY - height / 6);
    }

    public synchronized void showProducerWorkingBarrier() {
        clearBarrierLinks();
        addConnectionIfNotExists("P1", "R_Buffer", "Produce");
        System.out.println("GRAPH BAR: P1 produce");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerWaitingBarrier() {
        clearBarrierLinks();
        addConnectionIfNotExists("P1", "R_Barrier", "Espera");
        System.out.println("GRAPH BAR: P1 espera barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerReleasedBarrier() {
        clearBarrierLinks();
        addConnectionIfNotExists("R_Barrier", "P1", "Avanza");
        addConnectionIfNotExists("R_Token", "P1", "Turno");
        System.out.println("GRAPH BAR: P1 cruza barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showProducerIdleBarrier() {
        clearBarrierLinks();
        System.out.println("GRAPH BAR: P1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWorkingBarrier() {
        clearBarrierLinks();
        addConnectionIfNotExists("C1", "R_Buffer", "Consume");
        System.out.println("GRAPH BAR: C1 consume");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerWaitingBarrier() {
        clearBarrierLinks();
        addConnectionIfNotExists("C1", "R_Barrier", "Espera");
        System.out.println("GRAPH BAR: C1 espera barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerReleasedBarrier() {
        clearBarrierLinks();
        addConnectionIfNotExists("R_Barrier", "C1", "Avanza");
        addConnectionIfNotExists("R_Token", "C1", "Turno");
        System.out.println("GRAPH BAR: C1 cruza barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showConsumerIdleBarrier() {
        clearBarrierLinks();
        System.out.println("GRAPH BAR: C1 inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos específicos Filósofos Mutex ---
    public synchronized void setupPhilosophersGraph_Mutex() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = (int) (Math.min(width, height) * 0.32);
        int philosophers = 5;
        double step = 2 * Math.PI / philosophers;
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int px = centerX + (int) Math.round(Math.cos(ang) * radius);
            int py = centerY + (int) Math.round(Math.sin(ang) * radius);
            addNodeIfNotExists("P" + i, NodeType.PROCESO, px, py);
        }
        addNodeIfNotExists("R_Mutex", NodeType.RECURSO, centerX, centerY);
    }

    public synchronized void showPhilosopherRequestingLock_Mutex(String philosopherLabel) {
        clearPhilosopherMutexLinks(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "R_Mutex", "Solicitud");
        System.out.println("GRAPH PHILO MUTEX: " + philosopherLabel + " solicita R_Mutex");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherHoldingLock_Mutex(String philosopherLabel) {
        clearPhilosopherMutexLinks(philosopherLabel);
        addConnectionIfNotExists("R_Mutex", philosopherLabel, "Asignado");
        System.out.println("GRAPH PHILO MUTEX: R_Mutex -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasingLock_Mutex(String philosopherLabel) {
        clearPhilosopherMutexLinks(philosopherLabel);
        System.out.println("GRAPH PHILO MUTEX: " + philosopherLabel + " libera R_Mutex");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupPhilosophersGraph_Semaphore() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int philosophers = 5;
        int radius = (int) (Math.min(width, height) * 0.33);
        double step = 2 * Math.PI / philosophers;
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int px = centerX + (int) Math.round(Math.cos(ang) * radius);
            int py = centerY + (int) Math.round(Math.sin(ang) * radius);
            addNodeIfNotExists("P" + i, NodeType.PROCESO, px, py);
        }
        addNodeIfNotExists("R_Waiter", NodeType.RECURSO, centerX, centerY - (int) (Math.min(width, height) * 0.08));
        int forkRadius = (int) (radius * 1.25);
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step + step / 2.0;
            int fx = centerX + (int) Math.round(Math.cos(ang) * forkRadius);
            int fy = centerY + (int) Math.round(Math.sin(ang) * forkRadius);
            addNodeIfNotExists("F" + i, NodeType.RECURSO, fx, fy);
        }
    }

    public synchronized void showPhilosopherRequestingWaiter_Sem(String philosopherLabel) {
        clearWaiterLink(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "R_Waiter", "Solicitud");
        System.out.println("GRAPH PHILO SEM: " + philosopherLabel + " solicita R_Waiter");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherGrantedWaiter_Sem(String philosopherLabel) {
        clearWaiterLink(philosopherLabel);
        addConnectionIfNotExists("R_Waiter", philosopherLabel, "Permiso");
        System.out.println("GRAPH PHILO SEM: R_Waiter -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherRequestingFork_Sem(String philosopherLabel, String forkLabel) {
        clearForkLink(philosopherLabel, forkLabel);
        addConnectionIfNotExists(philosopherLabel, forkLabel, "Solicitud");
        System.out.println("GRAPH PHILO SEM: " + philosopherLabel + " solicita " + forkLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherHoldingFork_Sem(String philosopherLabel, String forkLabel) {
        clearForkLink(philosopherLabel, forkLabel);
        addConnectionIfNotExists(forkLabel, philosopherLabel, "Asignado");
        System.out.println("GRAPH PHILO SEM: " + forkLabel + " -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherEating_Sem(String philosopherLabel, String leftFork, String rightFork) {
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        addConnectionIfNotExists("R_Waiter", philosopherLabel, "Permiso");
        addConnectionIfNotExists(leftFork, philosopherLabel, "Uso");
        addConnectionIfNotExists(rightFork, philosopherLabel, "Uso");
        System.out.println("GRAPH PHILO SEM: " + philosopherLabel + " comiendo con " + leftFork + ", " + rightFork);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasingResources_Sem(String philosopherLabel, String leftFork, String rightFork) {
        clearWaiterLink(philosopherLabel);
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        System.out.println("GRAPH PHILO SEM: " + philosopherLabel + " libera recursos");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupPhilosophersGraph_Condition() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int philosophers = 5;
        int outerRadius = (int) (Math.min(width, height) * 0.34);
        int innerRadius = (int) (outerRadius * 0.55);
        double step = 2 * Math.PI / philosophers;
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int px = centerX + (int) Math.round(Math.cos(ang) * outerRadius);
            int py = centerY + (int) Math.round(Math.sin(ang) * outerRadius);
            addNodeIfNotExists("P" + i, NodeType.PROCESO, px, py);
        }
        addNodeIfNotExists("R_Lock_Ph", NodeType.RECURSO, centerX, centerY);
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int cx = centerX + (int) Math.round(Math.cos(ang) * innerRadius);
            int cy = centerY + (int) Math.round(Math.sin(ang) * innerRadius);
            addNodeIfNotExists("Cond_P" + i, NodeType.RECURSO, cx, cy);
        }
        int forkRadius = (int) (outerRadius * 1.25);
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step + step / 2.0;
            int fx = centerX + (int) Math.round(Math.cos(ang) * forkRadius);
            int fy = centerY + (int) Math.round(Math.sin(ang) * forkRadius);
            addNodeIfNotExists("F" + i, NodeType.RECURSO, fx, fy);
        }
    }

    public synchronized void showPhilosopherRequestingLock_Cond(String philosopherLabel) {
        clearConditionLockLink(philosopherLabel);
        clearConditionWaitLink(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "R_Lock_Ph", "Solicitud");
        System.out.println("GRAPH PHILO COND: " + philosopherLabel + " solicita R_Lock_Ph");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherHoldingLock_Cond(String philosopherLabel) {
        clearConditionLockLink(philosopherLabel);
        addConnectionIfNotExists("R_Lock_Ph", philosopherLabel, "Dentro");
        System.out.println("GRAPH PHILO COND: R_Lock_Ph -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherWaitingCondition_Cond(String philosopherLabel) {
        clearConditionLockLink(philosopherLabel);
        clearConditionWaitLink(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "Cond_" + philosopherLabel, "Espera");
        System.out.println("GRAPH PHILO COND: " + philosopherLabel + " espera Cond");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherSignaledCondition_Cond(String philosopherLabel) {
        clearConditionWaitLink(philosopherLabel);
        addConnectionIfNotExists("Cond_" + philosopherLabel, philosopherLabel, "Signal");
        System.out.println("GRAPH PHILO COND: Cond -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherEating_Cond(String philosopherLabel, String leftFork, String rightFork) {
        clearConditionWaitLink(philosopherLabel);
        addConnectionIfNotExists("R_Lock_Ph", philosopherLabel, "Dentro");
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        addConnectionIfNotExists(leftFork, philosopherLabel, "Uso");
        addConnectionIfNotExists(rightFork, philosopherLabel, "Uso");
        System.out.println("GRAPH PHILO COND: " + philosopherLabel + " come con " + leftFork + ", " + rightFork);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasing_Cond(String philosopherLabel, String leftFork, String rightFork) {
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        clearConditionLockLink(philosopherLabel);
        clearConditionWaitLink(philosopherLabel);
        System.out.println("GRAPH PHILO COND: " + philosopherLabel + " libera recursos");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasingLock_Cond(String philosopherLabel) {
        clearConditionLockLink(philosopherLabel);
        System.out.println("GRAPH PHILO COND: " + philosopherLabel + " libera R_Lock_Ph");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherIdle_Cond(String philosopherLabel, String leftFork, String rightFork) {
        clearConditionLockLink(philosopherLabel);
        clearConditionWaitLink(philosopherLabel);
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        System.out.println("GRAPH PHILO COND: " + philosopherLabel + " inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupPhilosophersGraph_Monitor() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int philosophers = 5;
        int outerRadius = (int) (Math.min(width, height) * 0.34);
        int innerRadius = (int) (outerRadius * 0.55);
        double step = 2 * Math.PI / philosophers;
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int px = centerX + (int) Math.round(Math.cos(ang) * outerRadius);
            int py = centerY + (int) Math.round(Math.sin(ang) * outerRadius);
            addNodeIfNotExists("P" + i, NodeType.PROCESO, px, py);
        }
        addNodeIfNotExists("R_Monitor_Ph", NodeType.RECURSO, centerX, centerY);
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int cx = centerX + (int) Math.round(Math.cos(ang) * innerRadius);
            int cy = centerY + (int) Math.round(Math.sin(ang) * innerRadius);
            addNodeIfNotExists("CondM_P" + i, NodeType.RECURSO, cx, cy);
        }
        int forkRadius = (int) (outerRadius * 1.25);
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step + step / 2.0;
            int fx = centerX + (int) Math.round(Math.cos(ang) * forkRadius);
            int fy = centerY + (int) Math.round(Math.sin(ang) * forkRadius);
            addNodeIfNotExists("F" + i, NodeType.RECURSO, fx, fy);
        }
    }

    public synchronized void showPhilosopherRequestingMonitor(String philosopherLabel) {
        clearMonitorLockLink(philosopherLabel);
        clearMonitorWaitLink(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "R_Monitor_Ph", "Solicitud");
        System.out.println("GRAPH PHILO MON: " + philosopherLabel + " solicita R_Monitor_Ph");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherInsideMonitor(String philosopherLabel) {
        clearMonitorLockLink(philosopherLabel);
        addConnectionIfNotExists("R_Monitor_Ph", philosopherLabel, "Dentro");
        System.out.println("GRAPH PHILO MON: R_Monitor_Ph -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherWaitingMonitor(String philosopherLabel) {
        clearMonitorLockLink(philosopherLabel);
        clearMonitorWaitLink(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "CondM_" + philosopherLabel, "Wait");
        System.out.println("GRAPH PHILO MON: " + philosopherLabel + " espera CondM");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherSignaledMonitor(String philosopherLabel) {
        clearMonitorWaitLink(philosopherLabel);
        addConnectionIfNotExists("CondM_" + philosopherLabel, philosopherLabel, "Signal");
        System.out.println("GRAPH PHILO MON: CondM -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherEatingMonitor(String philosopherLabel, String leftFork, String rightFork) {
        clearMonitorWaitLink(philosopherLabel);
        addConnectionIfNotExists("R_Monitor_Ph", philosopherLabel, "Dentro");
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        addConnectionIfNotExists(leftFork, philosopherLabel, "Uso");
        addConnectionIfNotExists(rightFork, philosopherLabel, "Uso");
        System.out.println("GRAPH PHILO MON: " + philosopherLabel + " come con " + leftFork + ", " + rightFork);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasingMonitor(String philosopherLabel, String leftFork, String rightFork) {
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        clearMonitorWaitLink(philosopherLabel);
        System.out.println("GRAPH PHILO MON: " + philosopherLabel + " libera tenedores");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherExitMonitor(String philosopherLabel) {
        clearMonitorLockLink(philosopherLabel);
        System.out.println("GRAPH PHILO MON: " + philosopherLabel + " sale del monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherIdleMonitor(String philosopherLabel, String leftFork, String rightFork) {
        clearMonitorLockLink(philosopherLabel);
        clearMonitorWaitLink(philosopherLabel);
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        System.out.println("GRAPH PHILO MON: " + philosopherLabel + " inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupPhilosophersGraph_Barrier() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int philosophers = 5;
        int outerRadius = (int) (Math.min(width, height) * 0.34);
        double step = 2 * Math.PI / philosophers;
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step;
            int px = centerX + (int) Math.round(Math.cos(ang) * outerRadius);
            int py = centerY + (int) Math.round(Math.sin(ang) * outerRadius);
            addNodeIfNotExists("P" + i, NodeType.PROCESO, px, py);
        }
        addNodeIfNotExists("R_Barrier_Ph", NodeType.RECURSO, centerX, centerY - (int) (Math.min(width, height) * 0.1));
        addNodeIfNotExists("R_Token_Ph", NodeType.RECURSO, centerX, centerY + (int) (Math.min(width, height) * 0.08));
        int forkRadius = (int) (outerRadius * 1.25);
        for (int i = 0; i < philosophers; i++) {
            double ang = -Math.PI / 2 + i * step + step / 2.0;
            int fx = centerX + (int) Math.round(Math.cos(ang) * forkRadius);
            int fy = centerY + (int) Math.round(Math.sin(ang) * forkRadius);
            addNodeIfNotExists("F" + i, NodeType.RECURSO, fx, fy);
        }
    }

    public synchronized void showPhilosopherThinkingBarrier(String philosopherLabel, String leftFork, String rightFork) {
        clearBarrierPhilosopherLinks(philosopherLabel);
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        System.out.println("GRAPH PHILO BAR: " + philosopherLabel + " pensando");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherWaitingBarrier(String philosopherLabel) {
        clearBarrierPhilosopherLinks(philosopherLabel);
        addConnectionIfNotExists(philosopherLabel, "R_Barrier_Ph", "Espera");
        System.out.println("GRAPH PHILO BAR: " + philosopherLabel + " espera barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasedBarrier(String philosopherLabel) {
        clearBarrierPhilosopherLinks(philosopherLabel);
        addConnectionIfNotExists("R_Barrier_Ph", philosopherLabel, "Cruza");
        addConnectionIfNotExists("R_Token_Ph", philosopherLabel, "Turno");
        System.out.println("GRAPH PHILO BAR: " + philosopherLabel + " cruza barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherRequestingForkBarrier(String philosopherLabel, String forkLabel) {
        clearForkLink(philosopherLabel, forkLabel);
        addConnectionIfNotExists(philosopherLabel, forkLabel, "Solicitud");
        System.out.println("GRAPH PHILO BAR: " + philosopherLabel + " solicita " + forkLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherHoldingForkBarrier(String philosopherLabel, String forkLabel) {
        clearForkLink(philosopherLabel, forkLabel);
        addConnectionIfNotExists(forkLabel, philosopherLabel, "Asignado");
        System.out.println("GRAPH PHILO BAR: " + forkLabel + " -> " + philosopherLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherEatingBarrier(String philosopherLabel, String leftFork, String rightFork) {
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        addConnectionIfNotExists("R_Token_Ph", philosopherLabel, "Sesión");
        addConnectionIfNotExists(leftFork, philosopherLabel, "Uso");
        addConnectionIfNotExists(rightFork, philosopherLabel, "Uso");
        System.out.println("GRAPH PHILO BAR: " + philosopherLabel + " comiendo con " + leftFork + ", " + rightFork);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showPhilosopherReleasingBarrier(String philosopherLabel, String leftFork, String rightFork) {
        clearBarrierPhilosopherLinks(philosopherLabel);
        clearForkLink(philosopherLabel, leftFork);
        clearForkLink(philosopherLabel, rightFork);
        System.out.println("GRAPH PHILO BAR: " + philosopherLabel + " libera recursos");
        SwingUtilities.invokeLater(this::repaint);
    }

    private String smokerNodeLabel(int smokerId) {
        switch (smokerId) {
            case 0:
                return "Smoker_Tabaco";
            case 1:
                return "Smoker_Papel";
            case 2:
                return "Smoker_Cerillos";
            default:
                return "Smoker_" + smokerId;
        }
    }

    private String smokerSemaphoreLabel(int smokerId) {
        switch (smokerId) {
            case 0:
                return "S_Smoker_Tabaco";
            case 1:
                return "S_Smoker_Papel";
            case 2:
                return "S_Smoker_Cerillos";
            default:
                return "S_Smoker_" + smokerId;
        }
    }

    public synchronized void setupSmokersGraph() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int agentY = centerY - (int) (height * 0.24);
        int smokersY = centerY + (int) (height * 0.26);
        int mutexX = centerX - (int) (width * 0.2);
        int tableX = centerX + (int) (width * 0.2);
        int offsetSmoker = (int) (width * 0.28);

        addNodeIfNotExists("Agent", NodeType.PROCESO, centerX, agentY);
        addNodeIfNotExists("Smoker_Tabaco", NodeType.PROCESO, centerX - offsetSmoker, smokersY);
        addNodeIfNotExists("Smoker_Papel", NodeType.PROCESO, centerX, smokersY);
        addNodeIfNotExists("Smoker_Cerillos", NodeType.PROCESO, centerX + offsetSmoker, smokersY);
        addNodeIfNotExists("R_Mutex_Smokers", NodeType.RECURSO, mutexX, centerY);
        addNodeIfNotExists("R_Table_Smokers", NodeType.RECURSO, tableX, centerY);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupSmokersGraph_Semaphore() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int agentY = centerY - (int) (height * 0.24);
        int smokersY = centerY + (int) (height * 0.26);
        int agentSemX = centerX - (int) (width * 0.22);
        int tableX = centerX;
        int semRowY = centerY;
        int smokerOffset = (int) (width * 0.28);
        int semOffsetX = (int) (width * 0.18);

        addNodeIfNotExists("Agent", NodeType.PROCESO, centerX, agentY);
        addNodeIfNotExists("Smoker_Tabaco", NodeType.PROCESO, centerX - smokerOffset, smokersY);
        addNodeIfNotExists("Smoker_Papel", NodeType.PROCESO, centerX, smokersY);
        addNodeIfNotExists("Smoker_Cerillos", NodeType.PROCESO, centerX + smokerOffset, smokersY);
        addNodeIfNotExists("S_Agent_Smokers", NodeType.RECURSO, agentSemX, semRowY);
        addNodeIfNotExists("R_Table_Smokers", NodeType.RECURSO, tableX, semRowY);
        addNodeIfNotExists("S_Smoker_Tabaco", NodeType.RECURSO, centerX - semOffsetX, semRowY + (int) (height * 0.08));
        addNodeIfNotExists("S_Smoker_Papel", NodeType.RECURSO, centerX, semRowY + (int) (height * 0.08));
        addNodeIfNotExists("S_Smoker_Cerillos", NodeType.RECURSO, centerX + semOffsetX, semRowY + (int) (height * 0.08));
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentRequestingLock_Smokers() {
        clearSmokersMutexLinks("Agent");
        addConnectionIfNotExists("Agent", "R_Mutex_Smokers", "Solicitud");
        System.out.println("GRAPH SMOKERS MUTEX: Agent solicita R_Mutex_Smokers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentHoldingLock_Smokers(String ingredientsLabel) {
        clearSmokersMutexLinks("Agent");
        addConnectionIfNotExists("R_Mutex_Smokers", "Agent", "Asignado");
        if (ingredientsLabel != null && !ingredientsLabel.isEmpty()) {
            addConnectionIfNotExists("Agent", "R_Table_Smokers", ingredientsLabel);
        }
        System.out.println("GRAPH SMOKERS MUTEX: Agent coloca " + (ingredientsLabel == null ? "" : ingredientsLabel));
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentReleasingLock_Smokers() {
        clearSmokersMutexLinks("Agent");
        System.out.println("GRAPH SMOKERS MUTEX: Agent libera R_Mutex_Smokers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerRequestingLock_Smokers(int smokerId) {
        String label = smokerNodeLabel(smokerId);
        clearSmokersMutexLinks(label);
        addConnectionIfNotExists(label, "R_Mutex_Smokers", "Solicitud");
        System.out.println("GRAPH SMOKERS MUTEX: " + label + " solicita R_Mutex_Smokers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerHoldingLock_Smokers(int smokerId) {
        String label = smokerNodeLabel(smokerId);
        clearSmokersMutexLinks(label);
        addConnectionIfNotExists("R_Mutex_Smokers", label, "Asignado");
        addConnectionIfNotExists(label, "R_Table_Smokers", "Toma");
        System.out.println("GRAPH SMOKERS MUTEX: " + label + " toma ingredientes");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerReleasingLock_Smokers(int smokerId) {
        String label = smokerNodeLabel(smokerId);
        clearSmokersMutexLinks(label);
        System.out.println("GRAPH SMOKERS MUTEX: " + label + " libera R_Mutex_Smokers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentWaitingSemaphore_Smokers() {
        clearSmokersSemaphoreAgentLinks();
        addConnectionIfNotExists("Agent", "S_Agent_Smokers", "Espera");
        System.out.println("GRAPH SMOKERS SEM: Agent espera S_Agent_Smokers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentHoldingSemaphore_Smokers(String ingredientsLabel) {
        clearSmokersSemaphoreAgentLinks();
        addConnectionIfNotExists("S_Agent_Smokers", "Agent", "Permiso");
        if (ingredientsLabel != null && !ingredientsLabel.isEmpty()) {
            addConnectionIfNotExists("Agent", "R_Table_Smokers", ingredientsLabel);
        }
        System.out.println("GRAPH SMOKERS SEM: Agent coloca " + (ingredientsLabel == null ? "" : ingredientsLabel));
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentSignalingSemaphore_Smokers(int smokerId, String ingredientsLabel) {
        clearSmokersSemaphoreAgentLinks();
        addConnectionIfNotExists("S_Agent_Smokers", "Agent", "Permiso");
        if (ingredientsLabel != null && !ingredientsLabel.isEmpty()) {
            addConnectionIfNotExists("Agent", "R_Table_Smokers", ingredientsLabel);
        }
        String semaphoreLabel = smokerSemaphoreLabel(smokerId);
        addConnectionIfNotExists("Agent", semaphoreLabel, "Signal");
        System.out.println("GRAPH SMOKERS SEM: Agent signal a " + semaphoreLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showAgentIdleSemaphore_Smokers() {
        clearSmokersSemaphoreAgentLinks();
        System.out.println("GRAPH SMOKERS SEM: Agent inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerWaitingSemaphore_Smokers(int smokerId) {
        String nodeLabel = smokerNodeLabel(smokerId);
        String semaphoreLabel = smokerSemaphoreLabel(smokerId);
        clearSmokersSemaphoreSmokerLinks(nodeLabel, semaphoreLabel);
        addConnectionIfNotExists(nodeLabel, semaphoreLabel, "Espera");
        System.out.println("GRAPH SMOKERS SEM: " + nodeLabel + " espera " + semaphoreLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerGrantedSemaphore_Smokers(int smokerId) {
        String nodeLabel = smokerNodeLabel(smokerId);
        String semaphoreLabel = smokerSemaphoreLabel(smokerId);
        clearSmokersSemaphoreSmokerLinks(nodeLabel, semaphoreLabel);
        addConnectionIfNotExists(semaphoreLabel, nodeLabel, "Permiso");
        System.out.println("GRAPH SMOKERS SEM: " + semaphoreLabel + " -> " + nodeLabel);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerTakingSemaphore_Smokers(int smokerId) {
        String nodeLabel = smokerNodeLabel(smokerId);
        String semaphoreLabel = smokerSemaphoreLabel(smokerId);
        clearSmokersSemaphoreSmokerLinks(nodeLabel, semaphoreLabel);
        addConnectionIfNotExists("R_Table_Smokers", nodeLabel, "Toma");
        System.out.println("GRAPH SMOKERS SEM: " + nodeLabel + " toma ingredientes");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showSmokerFinishedSemaphore_Smokers(int smokerId) {
        String nodeLabel = smokerNodeLabel(smokerId);
        String semaphoreLabel = smokerSemaphoreLabel(smokerId);
        clearSmokersSemaphoreSmokerLinks(nodeLabel, semaphoreLabel);
        System.out.println("GRAPH SMOKERS SEM: " + nodeLabel + " inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos específicos Barbero Dormilón Mutex ---
    public synchronized void setupSleepingBarberGraph() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int resourceY = centerY - (int) (height * 0.22);
        int processY = centerY + (int) (height * 0.22);
        int offsetX = (int) (width * 0.28);
        addNodeIfNotExists("Generator", NodeType.PROCESO, centerX - offsetX, processY);
        addNodeIfNotExists("Barber", NodeType.PROCESO, centerX + offsetX, processY);
        addNodeIfNotExists("R_Mutex_Barber", NodeType.RECURSO, centerX, resourceY);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorRequestingLock_Barber() {
        clearSleepingBarberProcessLinks("Generator");
        addConnectionIfNotExists("Generator", "R_Mutex_Barber", "Solicitud");
        System.out.println("GRAPH BARBER MUTEX: Generator solicita R_Mutex_Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorHoldingLock_Barber() {
        clearSleepingBarberProcessLinks("Generator");
        addConnectionIfNotExists("R_Mutex_Barber", "Generator", "Asignado");
        System.out.println("GRAPH BARBER MUTEX: R_Mutex_Barber -> Generator");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorReleasingLock_Barber() {
        clearSleepingBarberProcessLinks("Generator");
        System.out.println("GRAPH BARBER MUTEX: Generator libera R_Mutex_Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberRequestingLock_Barber() {
        clearSleepingBarberProcessLinks("Barber");
        addConnectionIfNotExists("Barber", "R_Mutex_Barber", "Solicitud");
        System.out.println("GRAPH BARBER MUTEX: Barber solicita R_Mutex_Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberHoldingLock_Barber() {
        clearSleepingBarberProcessLinks("Barber");
        addConnectionIfNotExists("R_Mutex_Barber", "Barber", "Asignado");
        System.out.println("GRAPH BARBER MUTEX: R_Mutex_Barber -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberReleasingLock_Barber() {
        clearSleepingBarberProcessLinks("Barber");
        System.out.println("GRAPH BARBER MUTEX: Barber libera R_Mutex_Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupSleepingBarberGraph_Semaphore() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int offsetX = (int) (width * 0.28);
        int processY = centerY + (int) (height * 0.24);
        int upperY = centerY - (int) (height * 0.22);
        int midOffsetX = (int) (width * 0.16);
        addNodeIfNotExists("Customer", NodeType.PROCESO, centerX - offsetX, processY);
        addNodeIfNotExists("Barber", NodeType.PROCESO, centerX + offsetX, processY);
        addNodeIfNotExists("S_AccessSeats", NodeType.RECURSO, centerX, upperY);
        addNodeIfNotExists("S_Customers", NodeType.RECURSO, centerX - midOffsetX, centerY);
        addNodeIfNotExists("S_Barber", NodeType.RECURSO, centerX + midOffsetX, centerY);
        addNodeIfNotExists("R_WaitRoom", NodeType.RECURSO, centerX, centerY + (int) (height * 0.05));
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerRequestingAccessSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        addConnectionIfNotExists("Customer", "S_AccessSeats", "Espera");
        System.out.println("GRAPH BARBER SEM: Customer espera S_AccessSeats");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerHoldingAccessSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        addConnectionIfNotExists("S_AccessSeats", "Customer", "Permiso");
        System.out.println("GRAPH BARBER SEM: S_AccessSeats -> Customer");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerReleasingAccessSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        System.out.println("GRAPH BARBER SEM: Customer libera S_AccessSeats");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerQueueFullSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        addConnectionIfNotExists("Customer", "R_WaitRoom", "Lleno");
        System.out.println("GRAPH BARBER SEM: Customer sin asiento");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerSignalingCustomersSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        addConnectionIfNotExists("Customer", "S_Customers", "Signal");
        System.out.println("GRAPH BARBER SEM: Customer signal S_Customers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerWaitingBarberSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        addConnectionIfNotExists("Customer", "S_Barber", "Espera");
        System.out.println("GRAPH BARBER SEM: Customer espera S_Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerGrantedBarberSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        addConnectionIfNotExists("S_Barber", "Customer", "Permiso");
        System.out.println("GRAPH BARBER SEM: S_Barber -> Customer");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerIdleSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Customer");
        System.out.println("GRAPH BARBER SEM: Customer inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberWaitingCustomersSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        addConnectionIfNotExists("Barber", "S_Customers", "Espera");
        System.out.println("GRAPH BARBER SEM: Barber espera S_Customers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberAcquiredCustomersSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        addConnectionIfNotExists("S_Customers", "Barber", "Permiso");
        System.out.println("GRAPH BARBER SEM: S_Customers -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberRequestingAccessSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        addConnectionIfNotExists("Barber", "S_AccessSeats", "Espera");
        System.out.println("GRAPH BARBER SEM: Barber espera S_AccessSeats");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberHoldingAccessSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        addConnectionIfNotExists("S_AccessSeats", "Barber", "Permiso");
        System.out.println("GRAPH BARBER SEM: S_AccessSeats -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberReleasingAccessSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        System.out.println("GRAPH BARBER SEM: Barber libera S_AccessSeats");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberSignalingBarberSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        addConnectionIfNotExists("Barber", "S_Barber", "Signal");
        System.out.println("GRAPH BARBER SEM: Barber signal S_Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberIdleSemaphore_Barber() {
        clearSleepingBarberSemaphoreLinks("Barber");
        System.out.println("GRAPH BARBER SEM: Barber inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupSleepingBarberGraph_Condition() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int offsetX = (int) (width * 0.28);
        int processY = centerY + (int) (height * 0.24);
        int lockY = centerY - (int) (height * 0.22);
        int conditionY = centerY;
        int waitRoomY = centerY + (int) (height * 0.06);
        addNodeIfNotExists("Customer", NodeType.PROCESO, centerX - offsetX, processY);
        addNodeIfNotExists("Barber", NodeType.PROCESO, centerX + offsetX, processY);
        addNodeIfNotExists("R_Lock_SB", NodeType.RECURSO, centerX, lockY);
        addNodeIfNotExists("Cond_Customers", NodeType.RECURSO, centerX + (int) (width * 0.12), conditionY);
        addNodeIfNotExists("R_WaitRoom", NodeType.RECURSO, centerX - (int) (width * 0.12), waitRoomY);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerRequestingLockCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        addConnectionIfNotExists("Customer", "R_Lock_SB", "Espera");
        System.out.println("GRAPH BARBER COND: Customer espera R_Lock_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerHoldingLockCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        addConnectionIfNotExists("R_Lock_SB", "Customer", "Dentro");
        System.out.println("GRAPH BARBER COND: R_Lock_SB -> Customer");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerSeatedCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        addConnectionIfNotExists("R_Lock_SB", "Customer", "Dentro");
        addConnectionIfNotExists("Customer", "R_WaitRoom", "Silla");
        System.out.println("GRAPH BARBER COND: Customer ocupa silla");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerQueueFullCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        addConnectionIfNotExists("Customer", "R_WaitRoom", "Lleno");
        System.out.println("GRAPH BARBER COND: Customer sin silla");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerSignalingCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        addConnectionIfNotExists("R_Lock_SB", "Customer", "Dentro");
        addConnectionIfNotExists("Customer", "Cond_Customers", "Signal");
        System.out.println("GRAPH BARBER COND: Customer signal Cond_Customers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerReleasingLockCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        System.out.println("GRAPH BARBER COND: Customer libera R_Lock_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerIdleCondition_Barber() {
        clearSleepingBarberConditionLinks("Customer");
        System.out.println("GRAPH BARBER COND: Customer inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberRequestingLockCondition_Barber() {
        clearSleepingBarberConditionLinks("Barber");
        addConnectionIfNotExists("Barber", "R_Lock_SB", "Espera");
        System.out.println("GRAPH BARBER COND: Barber espera R_Lock_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberHoldingLockCondition_Barber() {
        clearSleepingBarberConditionLinks("Barber");
        addConnectionIfNotExists("R_Lock_SB", "Barber", "Dentro");
        System.out.println("GRAPH BARBER COND: R_Lock_SB -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberWaitingCondition_Barber() {
        clearSleepingBarberConditionLinks("Barber");
        addConnectionIfNotExists("Barber", "Cond_Customers", "Wait");
        System.out.println("GRAPH BARBER COND: Barber espera Cond_Customers");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberSignaledCondition_Barber() {
        clearSleepingBarberConditionLinks("Barber");
        addConnectionIfNotExists("Cond_Customers", "Barber", "Signal");
        System.out.println("GRAPH BARBER COND: Cond_Customers -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberReleasingLockCondition_Barber() {
        clearSleepingBarberConditionLinks("Barber");
        System.out.println("GRAPH BARBER COND: Barber libera R_Lock_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberIdleCondition_Barber() {
        clearSleepingBarberConditionLinks("Barber");
        System.out.println("GRAPH BARBER COND: Barber inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupSleepingBarberGraph_Monitor() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int offsetX = (int) (width * 0.28);
        int processY = centerY + (int) (height * 0.24);
        int monitorY = centerY - (int) (height * 0.22);
        int condY = centerY;
        int waitRoomY = centerY + (int) (height * 0.06);
        addNodeIfNotExists("Customer", NodeType.PROCESO, centerX - offsetX, processY);
        addNodeIfNotExists("Barber", NodeType.PROCESO, centerX + offsetX, processY);
        addNodeIfNotExists("R_Monitor_SB", NodeType.RECURSO, centerX, monitorY);
        addNodeIfNotExists("Cond_Customers_M", NodeType.RECURSO, centerX + (int) (width * 0.12), condY);
        addNodeIfNotExists("R_WaitRoom", NodeType.RECURSO, centerX - (int) (width * 0.12), waitRoomY);
        SwingUtilities.invokeLater(this::repaint);
    }

    private synchronized void clearSleepingBarberMonitorLinks(String processLabel) {
        if (processLabel == null) {
            return;
        }
        removeConnection(processLabel, "R_Monitor_SB");
        removeConnection("R_Monitor_SB", processLabel);
        removeConnection(processLabel, "Cond_Customers_M");
        removeConnection("Cond_Customers_M", processLabel);
        removeConnection(processLabel, "R_WaitRoom");
        removeConnection("R_WaitRoom", processLabel);
    }

    public synchronized void showCustomerRequestingMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        addConnectionIfNotExists("Customer", "R_Monitor_SB", "Solicitud");
        System.out.println("GRAPH BARBER MON: Customer solicita R_Monitor_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerInsideMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        addConnectionIfNotExists("R_Monitor_SB", "Customer", "Dentro");
        System.out.println("GRAPH BARBER MON: R_Monitor_SB -> Customer");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerSeatedMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        addConnectionIfNotExists("R_Monitor_SB", "Customer", "Dentro");
        addConnectionIfNotExists("Customer", "R_WaitRoom", "Silla");
        System.out.println("GRAPH BARBER MON: Customer ocupa silla");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerQueueFullMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        addConnectionIfNotExists("Customer", "R_WaitRoom", "Lleno");
        System.out.println("GRAPH BARBER MON: Customer sin silla");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerSignalingMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        addConnectionIfNotExists("R_Monitor_SB", "Customer", "Dentro");
        addConnectionIfNotExists("Customer", "Cond_Customers_M", "Signal");
        System.out.println("GRAPH BARBER MON: Customer signal Cond_Customers_M");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerExitMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        System.out.println("GRAPH BARBER MON: Customer sale del monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showCustomerIdleMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Customer");
        System.out.println("GRAPH BARBER MON: Customer inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberRequestingMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Barber");
        addConnectionIfNotExists("Barber", "R_Monitor_SB", "Solicitud");
        System.out.println("GRAPH BARBER MON: Barber solicita R_Monitor_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberInsideMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Barber");
        addConnectionIfNotExists("R_Monitor_SB", "Barber", "Dentro");
        System.out.println("GRAPH BARBER MON: R_Monitor_SB -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberWaitingMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Barber");
        addConnectionIfNotExists("Barber", "Cond_Customers_M", "Wait");
        System.out.println("GRAPH BARBER MON: Barber espera Cond_Customers_M");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberSignaledMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Barber");
        addConnectionIfNotExists("Cond_Customers_M", "Barber", "Signal");
        System.out.println("GRAPH BARBER MON: Cond_Customers_M -> Barber");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberExitMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Barber");
        System.out.println("GRAPH BARBER MON: Barber sale del monitor");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberIdleMonitor_Barber() {
        clearSleepingBarberMonitorLinks("Barber");
        System.out.println("GRAPH BARBER MON: Barber inactivo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void setupSleepingBarberGraph_Barrier() {
        clearGraphInternal();
        int width = getWidth() > 0 ? getWidth() : 600;
        int height = getHeight() > 0 ? getHeight() : 400;
        int centerX = width / 2;
        int centerY = height / 2;
        int offsetX = (int) (width * 0.28);
        int processY = centerY + (int) (height * 0.24);
        int barrierY = centerY - (int) (height * 0.22);
        int tokenY = centerY;
        int waitRoomY = centerY + (int) (height * 0.06);
        addNodeIfNotExists("Generator", NodeType.PROCESO, centerX - offsetX, processY);
        addNodeIfNotExists("Barber", NodeType.PROCESO, centerX + offsetX, processY);
        addNodeIfNotExists("R_Barrier_SB", NodeType.RECURSO, centerX, barrierY);
        addNodeIfNotExists("R_Token_SB", NodeType.RECURSO, centerX + (int) (width * 0.12), tokenY);
        addNodeIfNotExists("R_WaitRoom", NodeType.RECURSO, centerX - (int) (width * 0.12), waitRoomY);
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorRequestingBarrier_Barber() {
        clearSleepingBarberBarrierLinks("Generator");
        addConnectionIfNotExists("Generator", "R_Barrier_SB", "Solicitud");
        System.out.println("GRAPH BARBER BAR: Generator solicita R_Barrier_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorWaitingBarrier_Barber() {
        clearSleepingBarberBarrierLinks("Generator");
        addConnectionIfNotExists("Generator", "R_Barrier_SB", "Espera");
        System.out.println("GRAPH BARBER BAR: Generator espera barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorReleasedBarrier_Barber() {
        clearSleepingBarberBarrierLinks("Generator");
        addConnectionIfNotExists("R_Barrier_SB", "Generator", "Cruza");
        addConnectionIfNotExists("R_Token_SB", "Generator", "Turno");
        System.out.println("GRAPH BARBER BAR: Generator cruza barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showGeneratorFinishedCycle_Barber() {
        clearSleepingBarberBarrierLinks("Generator");
        System.out.println("GRAPH BARBER BAR: Generator ciclo listo");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberRequestingBarrier_Barber() {
        clearSleepingBarberBarrierLinks("Barber");
        addConnectionIfNotExists("Barber", "R_Barrier_SB", "Solicitud");
        System.out.println("GRAPH BARBER BAR: Barber solicita R_Barrier_SB");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberWaitingBarrier_Barber() {
        clearSleepingBarberBarrierLinks("Barber");
        addConnectionIfNotExists("Barber", "R_Barrier_SB", "Espera");
        System.out.println("GRAPH BARBER BAR: Barber espera barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberReleasedBarrier_Barber() {
        clearSleepingBarberBarrierLinks("Barber");
        addConnectionIfNotExists("R_Barrier_SB", "Barber", "Cruza");
        addConnectionIfNotExists("R_Token_SB", "Barber", "Turno");
        System.out.println("GRAPH BARBER BAR: Barber cruza barrera");
        SwingUtilities.invokeLater(this::repaint);
    }

    public synchronized void showBarberFinishedCycle_Barber() {
        clearSleepingBarberBarrierLinks("Barber");
        System.out.println("GRAPH BARBER BAR: Barber ciclo listo");
        SwingUtilities.invokeLater(this::repaint);
    }

    // --- Métodos de dibujo y listeners (sin cambios) ---
    private void drawNode(Graphics2D g2, ShapeNode n, boolean highlight) {
        /* ... código ... */
        if (n == null || n.label == null) {
            return;
        }
        int h = n.size / 2;
        if (n.type == NodeType.PROCESO) {
            if (highlight) {
                g2.setColor(new Color(0, 128, 255, 60));
            } else {
                g2.setColor(getBackground()); // Fondo para borrar highlight anterior
            }
            if (highlight) {
                g2.fillOval(n.x - h, n.y - h, n.size, n.size);
            }
            g2.setColor(Color.BLACK);
            g2.drawOval(n.x - h, n.y - h, n.size, n.size);
        } else { // RECURSO
            if (highlight) {
                g2.setColor(new Color(0, 128, 255, 60));
            } else {
                g2.setColor(getBackground());
            }
            if (highlight) {
                g2.fillRect(n.x - h, n.y - h, n.size, n.size);
            }
            g2.setColor(Color.BLACK);
            g2.drawRect(n.x - h, n.y - h, n.size, n.size);
        }
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(n.label);
        int th = fm.getAscent();
        g2.setColor(Color.BLACK); // Asegura color negro para etiqueta
        g2.drawString(n.label, n.x - tw / 2, n.y + th / 4);
    }

    private void drawArrow(Graphics2D g2, ShapeNode from, ShapeNode to) {
        /* ... código ... */
        if (from == null || to == null) {
            return;
        }
        Point start = edgePointTowards(from, new Point(to.x, to.y));
        Point end = edgePointTowards(to, new Point(from.x, from.y));
        if (start == null || end == null || start.equals(end)) {
            return;
        }
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(start.x, start.y, end.x, end.y);
        drawArrowHead(g2, start, end);
    }

    private Point edgePointTowards(ShapeNode n, Point target) {
        /* ... código ... */
        if (n == null || target == null) {
            return null;
        }
        double dx = target.x - n.x, dy = target.y - n.y;
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
            return new Point(n.x, n.y);
        }
        double ang = Math.atan2(dy, dx);
        int h = n.size / 2;
        if (n.type == NodeType.PROCESO) {
            int ex = n.x + (int) Math.round(Math.cos(ang) * h);
            int ey = n.y + (int) Math.round(Math.sin(ang) * h);
            return new Point(ex, ey);
        } else { // RECURSO
            double cos = Math.cos(ang), sin = Math.sin(ang);
            double t = Math.max(Math.abs(cos), Math.abs(sin));
            if (t < 1e-6) {
                return new Point(n.x, n.y);
            }
            int ex = n.x + (int) Math.round((cos / t) * h);
            int ey = n.y + (int) Math.round((sin / t) * h);
            return new Point(ex, ey);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point from, Point to) {
        /* ... código ... */
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        double phi = Math.toRadians(25);
        int barb = 12;
        double dy = to.y - from.y, dx = to.x - from.x;
        double theta = Math.atan2(dy, dx);
        double x1 = to.x - barb * Math.cos(theta + phi);
        double y1 = to.y - barb * Math.sin(theta + phi);
        g2.draw(new Line2D.Double(to.x, to.y, x1, y1));
        double x2 = to.x - barb * Math.cos(theta - phi);
        double y2 = to.y - barb * Math.sin(theta - phi);
        g2.draw(new Line2D.Double(to.x, to.y, x2, y2));
    }

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        /* ... código ... */
        if (SwingUtilities.isRightMouseButton(e)) {
            Optional<ShapeNode> hit = findNodeAt(e.getX(), e.getY());
            if (hit.isPresent()) {
                nodeMenuTarget = hit.get();
                nodeMenu.show(this, e.getX(), e.getY());
            } else {
                nodeMenuTarget = null;
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
        hoveredTarget = null;
        repaint();
    }

    @Override
    public synchronized void mouseDragged(MouseEvent e) {
        /* ... código ... */
        if (dragging != null) {
            dragging.x = e.getX() - offX;
            dragging.y = e.getY() - offY;
            Optional<ShapeNode> over = findNodeAt(e.getX(), e.getY());
            hoveredTarget = over.filter(n -> n != dragging).orElse(null);
            repaint();
        }
    }

    @Override
    public synchronized void mouseReleased(MouseEvent e) {
        /* ... código ... */
        ShapeNode currentDragging = this.dragging;
        ShapeNode currentHovered = this.hoveredTarget;
        if (currentDragging != null && currentHovered != null) {
            ShapeNode a = currentDragging;
            ShapeNode b = currentHovered;
            if (a.type == NodeType.PROCESO && b.type == NodeType.RECURSO) {
                addConnectionIfNotExists(a.label, b.label, "Solicitud");
            } else if (a.type == NodeType.RECURSO && b.type == NodeType.PROCESO) {
                addConnectionIfNotExists(a.label, b.label, "Asignado");
            }
        }
        dragging = null;
        hoveredTarget = null;
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        /* ... código ... */
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

} // Fin de la clase DrawingPanel
