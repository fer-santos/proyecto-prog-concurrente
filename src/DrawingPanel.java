import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.util.Optional;

public class DrawingPanel extends JPanel implements MouseListener, MouseMotionListener {
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
            if (from == null || to == null) continue;
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

    @Override public void mouseMoved(MouseEvent e) {
        setCursor(findNodeAt(e.getX(), e.getY()).isPresent() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}