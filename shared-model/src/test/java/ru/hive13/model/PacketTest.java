package ru.hive13.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

class PacketTest {
    private byte[] encode(Packet p)throws IOException{var b=new ByteArrayOutputStream();Packet.write(new DataOutputStream(b),p);return b.toByteArray();}
    private Packet decode(byte[] b)throws IOException{return Packet.read(new DataInputStream(new ByteArrayInputStream(b)));}
    private byte[] compressed(byte[] raw)throws IOException{
        var b=new ByteArrayOutputStream();try(var z=new DeflaterOutputStream(b)){z.write(raw);}
        var framed=new ByteArrayOutputStream();var d=new DataOutputStream(framed);d.writeInt(-b.size());d.write(b.toByteArray());return framed.toByteArray();
    }
    @Test void compressesLargeRussianSnapshotAndKeepsFollowingAckFramed()throws Exception{
        var p=Packet.of("SNAPSHOT",Map.of("journal","Вторая глава: дверь открыта. ".repeat(15000)));
        byte[] encoded=encode(p);assertTrue(new DataInputStream(new ByteArrayInputStream(encoded)).readInt()<0);assertTrue(encoded.length<20000);
        assertEquals(p.data,decode(encoded).data);
        var stream=new ByteArrayOutputStream();stream.write(encoded);stream.write(encode(new Packet("ACK","vote-1",Map.of("accepted",true))));
        var in=new DataInputStream(new ByteArrayInputStream(stream.toByteArray()));assertEquals("SNAPSHOT",Packet.read(in).type);assertEquals("vote-1",Packet.read(in).id);
    }
    @Test void handshakeAndCommandsRemainPlain()throws Exception{
        byte[] b=encode(Packet.of("HELLO",Map.of("version",Packet.VERSION)));assertTrue(new DataInputStream(new ByteArrayInputStream(b)).readInt()>0);assertEquals("HELLO",decode(b).type);
    }
    @Test void rejectsInvalidHeadersTruncatedFramesAndCompressionBombs()throws Exception{
        for(int n:new int[]{0,1,Integer.MIN_VALUE,Packet.MAX_BYTES+1,-Packet.MAX_BYTES-1}){
            var b=new ByteArrayOutputStream();new DataOutputStream(b).writeInt(n);assertThrows(IOException.class,()->decode(b.toByteArray()));
        }
        assertThrows(EOFException.class,()->decode(new byte[]{0,0,0,4,123,125}));
        assertThrows(IOException.class,()->decode(compressed(new byte[Packet.MAX_SNAPSHOT_BYTES+1])));
        assertThrows(IOException.class,()->decode(compressed(Json.MAPPER.writeValueAsBytes(Packet.of("MOVE",Map.of("x",12))))));
    }
}
