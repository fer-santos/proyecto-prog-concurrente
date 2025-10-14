package synch;

import problemas.ReadersWritersSim.Actor;

// Interfaz que extiende la base para añadir un método específico de este problema
public interface ReadersWritersStrategy extends SynchronizationStrategy {
    void requestAccess(Actor a);
}