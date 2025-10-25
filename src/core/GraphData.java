package core;

import java.io.Serializable;
import java.util.ArrayList;

public class GraphData implements Serializable {
    ArrayList<ShapeNode> nodes = new ArrayList<>();
    ArrayList<Connection> connections = new ArrayList<>();
    int nextProceso = 1, nextRecurso = 1, nextId = 1;
}