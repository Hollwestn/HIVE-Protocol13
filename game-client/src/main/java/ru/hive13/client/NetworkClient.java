package ru.hive13.client;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import org.slf4j.*;
public final class NetworkClient implements AutoCloseable {
    private static final Logger LOG=LoggerFactory.getLogger(NetworkClient.class);
    private final Socket socket=new Socket();private final DataOutputStream out;private volatile boolean open=true;
    private final AtomicBoolean closing=new AtomicBoolean();
    private final ArrayBlockingQueue<Packet> outgoing=new ArrayBlockingQueue<>(128);
    private final Thread writer;
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    public volatile Campaign state;public volatile int slot=-1;public volatile String token="",error="",campaignId="";public volatile long receivedAt,rtt;public volatile boolean welcomed;
    public volatile Consumer<Packet> listener=p->{};
    public volatile List<String> joinAddresses=List.of();
    public NetworkClient(String host,int port,int requested,String password,String token)throws IOException{this(host,port,requested,password,token,id->token);}
    public NetworkClient(String host,int port,int requested,String password,String token,java.util.function.Function<String,String> tokenLookup)throws IOException{
        try{socket.connect(new InetSocketAddress(host,port),5000);socket.setTcpNoDelay(true);socket.setSoTimeout(15000);out=new DataOutputStream(socket.getOutputStream());}
        catch(IOException e){socket.close();heartbeat.shutdownNow();throw e;}
        writer=Thread.ofVirtual().name("client-write").unstarted(()->{
            try{while(open)Packet.write(out,outgoing.take());}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(IOException e){failed("запись",e);}
        });writer.start();
        send("INFO",Map.of());
        heartbeat.scheduleAtFixedRate(()->{try{sendPacket(new Packet("PING",Long.toString(System.currentTimeMillis()),Map.of()));}catch(IOException e){failed("проверка связи",e);}},1,2,TimeUnit.SECONDS);
        Thread.ofVirtual().name("client-read").start(()->{try{var in=new DataInputStream(socket.getInputStream());while(open){var p=Packet.read(in);
            switch(p.type){case "SERVER_INFO"->{sendPacket(new Packet("HELLO",UUID.randomUUID().toString(),Map.of("version",Packet.VERSION,"slot",requested,"password",password,"token",tokenLookup.apply(p.text("campaignId")))));}
                case "WELCOME"->{this.slot=p.integer("slot");this.token=p.text("token");this.campaignId=p.text("campaignId");welcomed=true;}
                case "SNAPSHOT"->{state=Json.MAPPER.treeToValue(p.data.get("state"),Campaign.class);receivedAt=System.nanoTime();}
                case "ERROR"->error=p.text("message");case "ACK"->{if(!p.data.path("accepted").asBoolean())error=p.text("reason");}
                case "PONG"->{try{rtt=System.currentTimeMillis()-Long.parseLong(p.id);}catch(NumberFormatException ignored){}}
            }notifyListener(p);
        }}catch(Exception e){failed("чтение",e);}finally{close();}});
    }
    private void notifyListener(Packet p){try{listener.accept(p);}catch(RuntimeException e){LOG.error("Ошибка обработчика сетевого события type={} slot={}",p.type,slot,e);}}
    private void failed(String operation,Exception cause){
        if(!closing.compareAndSet(false,true))return;
        String detail=cause instanceof EOFException?"хост закрыл соединение":cause instanceof SocketTimeoutException?"хост не отвечает 15 секунд":cause.getClass().getSimpleName()+": "+Objects.toString(cause.getMessage(),operation);
        error="Связь с хостом потеряна: "+detail;
        LOG.warn("Сетевой сбой operation={} slot={}",operation,slot,cause);
        stopTransport();notifyListener(Packet.of("ERROR",Map.of("message",error)));
    }
    public String send(String type,Object data){var id=UUID.randomUUID().toString();try{sendPacket(new Packet(type,id,data));}catch(IOException e){failed("отправка команды",e);}return id;}
    public void sendPacket(Packet p)throws IOException{if(!open)throw new IOException("Соединение закрыто");if(!outgoing.offer(p))throw new IOException("Очередь отправки команд переполнена");}
    public boolean connected(){return open&&welcomed;}
    private void stopTransport(){open=false;heartbeat.shutdownNow();try{socket.close();}catch(IOException ignored){}outgoing.clear();writer.interrupt();}
    @Override public void close(){if(closing.compareAndSet(false,true))stopTransport();}
}
