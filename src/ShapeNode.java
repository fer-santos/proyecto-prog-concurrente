import java.io.Serializable;

public class ShapeNode implements Serializable {
    int id;
    NodeType type;
    int x, y;
    int size;
    String label; // Pn / Rn

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