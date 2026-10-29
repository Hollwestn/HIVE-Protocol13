package ru.hive13.server;

import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.*;

public final class GameServer implements AutoCloseable {
    private static final Logger LOG=LoggerFactory.getLogger(GameServer.class);
    private final GameEngine engine;private final ServerSocketChannel listener;private final double clockRate;
    private final ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("authority").factory());
    private final ExecutorService saves=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("save-writer").factory());
    private final Set<Peer> peers=ConcurrentHashMap.newKeySet();private final Map<String,Integer> identities=new HashMap<>();
    private final ArrayBlockingQueue<Runnable> commands=new ArrayBlockingQueue<>(4096);
    private final Path saveFile;private final String password;private long previous=System.nanoTime(),broadcastTick;private int savedCycle;private volatile boolean closed;
    private final AtomicBoolean saving=new AtomicBoolean();
    public GameServer(String bind,int port,Campaign state,Path saveFile,String password)throws IOException{
        this(bind,port,state,saveFile,password,1);
    }
    public static GameServer acceleratedForQa(String bind,int port,Campaign state,Path saveFile,double rate)throws IOException{
        if(!Boolean.getBoolean("hive.devMode")||!Double.isFinite(rate)||rate<1||rate>30)throw new IllegalArgumentException("Ускорение доступно только явному dev mode, от 1 до 30");
        return new GameServer(bind,port,state,saveFile,"",rate);
    }
    private GameServer(String bind,int port,Campaign state,Path saveFile,String password,double clockRate)throws IOException{
        this.clockRate=clockRate;state.qaSpeed=clockRate;
        this.engine=new GameEngine(state);this.saveFile=saveFile;this.password=password==null?"":password;
        if(state.phase!=Phase.LOBBY){state.resumePhase=state.phase;state.phase=Phase.LOBBY;state.flags.add("resume_pending");}
        for(var a:state.actors){a.connected=false;a.ready=false;}
        if(saveFile!=null){Path auth=authFile();if(Files.exists(auth)){try{var n=Json.MAPPER.readTree(Files.readAllBytes(auth));n.fields().forEachRemaining(e->identities.put(e.getKey(),e.getValue().asInt()));}catch(IOException e){throw new IOException("Повреждён файл сетевых идентификаторов",e);}}}
        listener=ServerSocketChannel.open();listener.bind(new InetSocketAddress(bind,port));
        LOG.info("Сервер {}:{} protocol={}",bind,port(),Packet.VERSION);
        Thread.ofVirtual().name("accept").start(this::accept);
        loop.scheduleAtFixedRate(this::tick,0,50,TimeUnit.MILLISECONDS);
    }
    public int port()throws IOException{return ((InetSocketAddress)listener.getLocalAddress()).getPort();}
    public CompletableFuture<Campaign> inspect(){var f=new CompletableFuture<Campaign>();if(!commands.offer(()->f.complete(Json.copy(engine.state,Campaign.class))))f.completeExceptionally(new IOException("Очередь переполнена"));return f;}
    private void accept(){while(!closed){try{var channel=listener.accept();if(peers.size()>=8){channel.close();continue;}var p=new Peer(channel);peers.add(p);Thread.ofVirtual().name("peer-read").start(p::read);Thread.ofPlatform().daemon(true).name("peer-write").start(p::write);}catch(IOException e){if(!closed)LOG.warn("Ошибка приёма: {}",e.getMessage());}}}
    private void tick(){try{
        long now=System.nanoTime();double dt=(now-previous)/1e9;previous=now;engine.advance(dt*clockRate);
        for(int i=0;i<200;i++){var task=commands.poll();if(task==null)break;task.run();}
        if(++broadcastTick%4==0)for(var p:peers)if(p.slot>=0&&p.open)p.send(Packet.of("SNAPSHOT",Map.of("slot",p.slot,"serverMillis",System.currentTimeMillis(),"state",engine.view(p.slot))));
        if(saveFile!=null&&(savedCycle!=engine.state.cycle||broadcastTick%200==0)&&engine.state.phase!=Phase.LOBBY)queueSave();
    }catch(Exception e){LOG.error("Ошибка authority tick",e);for(var p:peers)p.send(Packet.of("ERROR",Map.of("message","Ошибка симуляции: "+e.getClass().getSimpleName())));}}
    private void queueSave(){if(!saving.compareAndSet(false,true))return;var copy=Json.copy(engine.state,Campaign.class);savedCycle=copy.cycle;saves.execute(()->{try{SaveStore.save(saveFile,copy);}catch(IOException e){LOG.error("Сохранение не записано",e);commands.offer(()->engine.event("УГРОЗА","ОШИБКА СОХРАНЕНИЯ: "+e.getMessage(),""));}finally{saving.set(false);}});}
    private Path authFile(){return saveFile.resolveSibling(saveFile.getFileName()+".auth");}
    private void persistIdentities(){if(saveFile==null)return;var copy=new LinkedHashMap<>(identities);saves.execute(()->{try{Files.createDirectories(authFile().toAbsolutePath().getParent());Files.write(authFile(),Json.MAPPER.writeValueAsBytes(copy));}catch(IOException e){LOG.warn("Идентификаторы не записаны: {}",e.getMessage());}});}
    private void hello(Peer p,Packet packet){
        if(!p.open)return;if(p.slot>=0){p.error("HELLO уже выполнен");return;}
        if(packet.integer("version")!=Packet.VERSION){p.error("Версия протокола не совпадает: хост "+Packet.VERSION+", клиент "+packet.integer("version")+". Обновите игру у всех участников.");return;}
        if(!password.equals(packet.text("password"))){p.error("Неверный пароль сессии");return;}
        String token=packet.text("token");Integer returning=token.isEmpty()?null:identities.get(token);int requested=packet.integer("slot");
        if(!token.isEmpty()&&returning==null&&(engine.state.phase!=Phase.LOBBY||identities.containsValue(requested))){p.error("Токен восстановления не принадлежит этой кампании. Создайте новое подключение.");return;}
        int slot=returning==null?requested:returning;
        if(slot<0||slot>2){p.error("Нет такого персонажа");return;}
        if(peers.stream().anyMatch(x->x!=p&&x.open&&x.slot==slot)){p.error("Этот персонаж уже подключён");return;}
        if(returning==null&&identities.containsValue(slot)){p.error("Слот зарезервирован. Нужен токен прежнего игрока.");return;}
        if(returning==null&&engine.state.phase!=Phase.LOBBY&&!(engine.state.requiredPlayers==2&&engine.state.actor(slot).life.equals("DORMANT"))){p.error("Кампания уже идёт; разрешено только переподключение");return;}
        if(returning==null){token=UUID.randomUUID().toString();identities.put(token,slot);persistIdentities();}
        p.slot=slot;var actor=engine.state.actor(slot);actor.connected=true;actor.ready=engine.state.phase!=Phase.LOBBY;if(actor.life.equals("DORMANT")){actor.life="ALIVE";engine.event("ГРУППА",actor.name+": третий субъект пробуждён из капсулы.","A2");}
        p.send(Packet.of("WELCOME",Map.of("slot",slot,"token",token,"version",Packet.VERSION,"campaignId",engine.state.campaignId)));
        engine.event("СИСТЕМА",engine.state.actor(slot).name+": соединение установлено","");LOG.info("HELLO принят slot={}",slot);
    }
    private final class Peer {
        final SocketChannel channel;final DataInputStream in;final DataOutputStream out;final PeerOutbox outgoing=new PeerOutbox();
        final AtomicBoolean disconnecting=new AtomicBoolean();
        volatile int slot=-1;volatile boolean open=true;long window=System.nanoTime();int count;
        Peer(SocketChannel channel)throws IOException{this.channel=channel;channel.socket().setSoTimeout(15000);channel.socket().setTcpNoDelay(true);channel.socket().setSendBufferSize(32768);in=new DataInputStream(channel.socket().getInputStream());out=new DataOutputStream(channel.socket().getOutputStream());}
        void read(){try{while(open){var msg=Packet.read(in);long now=System.nanoTime();if(now-window>1_000_000_000L){window=now;count=0;}if(++count>60)throw new IOException("Лимит 60 команд/с");
            if(msg.type==null)throw new IOException("Отсутствует тип");if(msg.type.equals("PING")){send(new Packet("PONG",msg.id,Map.of("serverMillis",System.currentTimeMillis())));continue;}
            if(!commands.offer(()->{if(!open)return;if(msg.type.equals("INFO")){send(Packet.of("SERVER_INFO",Map.of("campaignId",engine.state.campaignId,"version",Packet.VERSION)));return;}if(msg.type.equals("HELLO")){hello(this,msg);return;}if(slot<0){error("Сначала HELLO");return;}var r=engine.command(slot,msg);send(new Packet("ACK",msg.id,Map.of("accepted",r.accepted(),"reason",r.reason())));} ))throw new IOException("Переполнена очередь команд");
        }}catch(IOException e){disconnect("чтение: "+e.getClass().getSimpleName()+": "+e.getMessage());}finally{disconnect();}}
        void write(){try{while(open){var p=outgoing.take();if(p!=null)Packet.write(out,p);}}catch(IOException e){disconnect("запись: "+e.getClass().getSimpleName()+": "+e.getMessage());}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{disconnect();}}
        void send(Packet p){if(open&&!outgoing.offer(p))disconnect("переполнена очередь подтверждений команд");}
        void error(String text){send(Packet.of("ERROR",Map.of("message",text)));}
        void disconnect(){disconnect("соединение завершено");}
        void disconnect(String reason){if(!disconnecting.compareAndSet(false,true))return;open=false;outgoing.close();try{channel.close();}catch(IOException ignored){}peers.remove(this);if(!closed){LOG.info("Соединение закрыто slot={} reason={}",slot,reason);commands.offer(()->{if(slot>=0&&peers.stream().noneMatch(x->x.open&&x.slot==slot)){var a=engine.state.actor(slot);a.connected=false;a.ready=false;a.path.clear();a.task="";engine.event("СИСТЕМА",a.name+": OFFLINE. Роль сохранена для переподключения.",a.roomId);}});}}
    }
    @Override public void close(){if(closed)return;closed=true;try{listener.close();}catch(IOException ignored){}for(var p:List.copyOf(peers))p.disconnect();loop.shutdown();try{loop.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        if(saveFile!=null){var copy=Json.copy(engine.state,Campaign.class);saves.execute(()->{try{SaveStore.save(saveFile,copy);}catch(IOException e){LOG.error("Финальное сохранение",e);}});}saves.shutdown();try{saves.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    public static void main(String[] args)throws Exception{int port=args.length>0?Integer.parseInt(args[0]):27713;Path file=Path.of(System.getProperty("user.home"),".hive13","campaign.json");var state=Files.exists(file)?SaveStore.load(file):WorldFactory.create("STORY");var server=new GameServer("0.0.0.0",port,state,file,"");Runtime.getRuntime().addShutdownHook(new Thread(server::close));Thread.currentThread().join();}
}
