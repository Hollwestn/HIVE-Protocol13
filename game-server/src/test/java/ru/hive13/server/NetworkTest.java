package ru.hive13.server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import ru.hive13.model.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
class NetworkTest {
    @Test void twoPlayersStartAndThirdCanJoinTheVacantCapsule()throws Exception{try(var server=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),null,"");var a=new Client(server.port(),0,"");var b=new Client(server.port(),1,"")){a.send("SESSION",Map.of("players",2));assertTrue(a.until("ACK").data.path("accepted").asBoolean());a.send("READY",Map.of("ready",true));a.until("ACK");b.send("READY",Map.of("ready",true));b.until("ACK");a.send("START",Map.of());assertTrue(a.until("ACK").data.path("accepted").asBoolean());assertEquals("DORMANT",server.inspect().get(2,TimeUnit.SECONDS).actor(2).life);try(var c=new Client(server.port(),2,"")){var s=server.inspect().get(2,TimeUnit.SECONDS);assertEquals("ALIVE",s.actor(2).life);assertTrue(s.actor(2).connected);assertEquals(ru.hive13.model.Model.Phase.ACTION,s.phase);}}}
    static final class Client implements AutoCloseable {
        Socket socket;DataInputStream in;DataOutputStream out;String token;int slot;
        Client(int port,int slot,String token)throws Exception{this.slot=slot;socket=new Socket("127.0.0.1",port);socket.setSoTimeout(4000);in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());send("HELLO",Map.of("version",Packet.VERSION,"slot",slot,"password","","token",token));var p=until("WELCOME");this.token=p.text("token");}
        void send(String type,Object data)throws IOException{Packet.write(out,new Packet(type,UUID.randomUUID().toString(),data));}
        Packet until(String type)throws IOException{for(int i=0;i<100;i++){var p=Packet.read(in);if(p.type.equals("ERROR"))throw new IOException(p.text("message"));if(p.type.equals(type))return p;}throw new IOException("Не получен "+type);}
        @Override public void close()throws IOException{socket.close();}
    }
    @Test void threeIndependentSessionsStartAndReconnect()throws Exception{
        try(var server=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),null,"");var a=new Client(server.port(),0,"");var b=new Client(server.port(),1,"");var c=new Client(server.port(),2,"")){
            assertNotEquals(a.token,b.token);assertNotEquals(b.token,c.token);a.send("READY",Map.of("ready",true));b.send("READY",Map.of("ready",true));c.send("READY",Map.of("ready",true));
            a.until("ACK");b.until("ACK");c.until("ACK");a.send("START",Map.of());assertTrue(a.until("ACK").data.path("accepted").asBoolean());
            a.send("MOVE",Map.of("x",240,"y",100,"mode","WALK"));assertTrue(a.until("ACK").data.path("accepted").asBoolean());
            var s=server.inspect().get(2,TimeUnit.SECONDS);assertEquals(ru.hive13.model.Model.Phase.ACTION,s.phase);assertFalse(s.actor(0).path.isEmpty());assertTrue(s.actor(1).path.isEmpty());
            String token=b.token;b.close();Thread.sleep(200);try(var again=new Client(server.port(),1,token)){assertEquals(token,again.token);assertEquals(1,again.until("SNAPSHOT").integer("slot"));}
            assertThrows(IOException.class,()->{try(var duplicate=new Client(server.port(),0,"")){} });
        }
    }
    @Test void restartRestoresCampaignAndReservedRoles(@TempDir Path dir)throws Exception{
        Path file=dir.resolve("campaign.json");String[] tokens=new String[3];
        try(var server=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),file,"");var a=new Client(server.port(),0,"");var b=new Client(server.port(),1,"");var c=new Client(server.port(),2,"")){
            tokens[0]=a.token;tokens[1]=b.token;tokens[2]=c.token;for(var client:List.of(a,b,c)){client.send("READY",Map.of("ready",true));client.until("ACK");}a.send("START",Map.of());a.until("ACK");Thread.sleep(200);
        }
        var restored=SaveStore.load(file);assertTrue(restored.remaining<60);double remaining=restored.remaining;
        try(var server=new GameServer("127.0.0.1",0,restored,file,"");var a=new Client(server.port(),0,tokens[0]);var b=new Client(server.port(),1,tokens[1]);var c=new Client(server.port(),2,tokens[2])){
            for(var client:List.of(a,b,c)){client.send("READY",Map.of("ready",true));client.until("ACK");}a.send("START",Map.of());a.until("ACK");var s=server.inspect().get(2,TimeUnit.SECONDS);assertEquals(ru.hive13.model.Model.Phase.ACTION,s.phase);assertTrue(s.remaining<=remaining);assertEquals(1,s.cycle);assertEquals(tokens[1],b.token);
        }
    }
}
