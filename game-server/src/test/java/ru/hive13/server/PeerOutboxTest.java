package ru.hive13.server;
import ru.hive13.model.Packet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.*;

class PeerOutboxTest {
    @Test void slowPeerGetsLatestWorldAndEveryCommandConfirmationInOrder()throws Exception{
        try(var queue=new PeerOutbox()){
            for(int i=0;i<10000;i++)assertTrue(queue.offer(Packet.of("SNAPSHOT",Map.of("frame",i))));
            for(int i=0;i<64;i++)assertTrue(queue.offer(new Packet("ACK","command-"+i,Map.of("accepted",true))));
            assertFalse(queue.offer(Packet.of("ACK",Map.of())));
            for(int i=0;i<64;i++)assertEquals("command-"+i,queue.take().id);
            assertEquals(9999,queue.take().integer("frame"));
        }
    }
    @Test void closeWakesIdleWriterAndRejectsNewWork()throws Exception{
        var queue=new PeerOutbox();var result=new CompletableFuture<Packet>();
        var writer=Thread.ofVirtual().start(()->{try{result.complete(queue.take());}catch(InterruptedException e){result.completeExceptionally(e);}});
        queue.close();assertNull(result.get(1,TimeUnit.SECONDS));assertFalse(queue.offer(Packet.of("PONG",Map.of())));writer.join(1000);assertFalse(writer.isAlive());
    }
}
