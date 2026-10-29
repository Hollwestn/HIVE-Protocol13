package ru.hive13.model;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.Map;
import java.util.zip.*;
public class Packet {
    public static final int VERSION=5,MAX_BYTES=262144,MAX_SNAPSHOT_BYTES=2*1024*1024;
    public String type,id=""; public JsonNode data;
    public Packet(){}
    public Packet(String type,String id,Object data){this.type=type;this.id=id;this.data=Json.MAPPER.valueToTree(data);}
    public static Packet of(String type,Object data){return new Packet(type,"",data);}
    public String text(String key){return data==null?"":data.path(key).asText("");}
    public int integer(String key){return data==null?0:data.path(key).asInt();}
    public double number(String key){return data==null?Double.NaN:data.path(key).asDouble(Double.NaN);}
    public static Packet read(DataInputStream in)throws IOException{
        int header=in.readInt();boolean compressed=header<0;
        if(header==Integer.MIN_VALUE)throw new IOException("Недопустимый размер сообщения");
        int n=Math.abs(header);if(n<2||n>MAX_BYTES)throw new IOException("Недопустимый размер сообщения: "+n);
        byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException("Неполное сетевое сообщение");
        if(compressed)try(var inflater=new InflaterInputStream(new ByteArrayInputStream(bytes))){
            bytes=inflater.readNBytes(MAX_SNAPSHOT_BYTES+1);
            if(bytes.length>MAX_SNAPSHOT_BYTES)throw new IOException("Слишком большое распакованное состояние");
        }
        Packet p=Json.MAPPER.readValue(bytes,Packet.class);
        if(compressed&&!"SNAPSHOT".equals(p.type))throw new IOException("Сжатие разрешено только для состояния мира");
        return p;
    }
    public static void write(DataOutputStream out,Packet p)throws IOException{
        byte[] bytes=Json.MAPPER.writeValueAsBytes(p);boolean compressed=false;
        if("SNAPSHOT".equals(p.type)&&bytes.length>=1024){
            if(bytes.length>MAX_SNAPSHOT_BYTES)throw new IOException("Слишком большое состояние мира: "+bytes.length);
            var buffer=new ByteArrayOutputStream();var deflater=new Deflater(Deflater.DEFAULT_COMPRESSION);
            try{try(var stream=new DeflaterOutputStream(buffer,deflater)){stream.write(bytes);}}finally{deflater.end();}
            byte[] packed=buffer.toByteArray();if(packed.length<bytes.length){bytes=packed;compressed=true;}
        }
        if(bytes.length>MAX_BYTES)throw new IOException("Слишком большое сетевое сообщение: "+bytes.length);
        byte[] frame=java.nio.ByteBuffer.allocate(4+bytes.length).putInt(compressed?-bytes.length:bytes.length).put(bytes).array();
        out.write(frame);out.flush();
    }
}
