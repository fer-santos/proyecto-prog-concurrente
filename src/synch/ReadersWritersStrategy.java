package synch;

import problemas.ReadersWritersSim.Actor;


public interface ReadersWritersStrategy extends SynchronizationStrategy {
    void requestAccess(Actor a);
}