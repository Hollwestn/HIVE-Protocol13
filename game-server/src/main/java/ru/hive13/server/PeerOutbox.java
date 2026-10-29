package ru.hive13.server;

import ru.hive13.model.Packet;
import java.util.ArrayDeque;

/** Commands are reliable FIFO; snapshots replace obsolete state while a peer catches up. */
final class PeerOutbox implements AutoCloseable {
    private final ArrayDeque<Packet> reliable=new ArrayDeque<>();
    private Packet snapshot;
    private boolean closed;
    synchronized boolean offer(Packet packet){
        if(closed)return false;
        if("SNAPSHOT".equals(packet.type))snapshot=packet;
        else{if(reliable.size()>=64)return false;reliable.addLast(packet);}
        notifyAll();return true;
    }
    synchronized Packet take()throws InterruptedException{
        while(!closed&&reliable.isEmpty()&&snapshot==null)wait();
        if(closed)return null;
        if(!reliable.isEmpty())return reliable.removeFirst();
        var latest=snapshot;snapshot=null;return latest;
    }
    @Override public synchronized void close(){closed=true;reliable.clear();snapshot=null;notifyAll();}
}
