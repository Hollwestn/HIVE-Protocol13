import javafx.application.*;
import javafx.animation.AnimationTimer;
import javafx.scene.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import ru.hive13.client.*;
import ru.hive13.model.*;
import ru.hive13.server.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Native HUD + ordinary authenticated TCP commands, using a real failed-play checkpoint. */
public class EchoProbe {
    public static void main(String[] args){System.setProperty("hive.dataDir",Path.of(args[1],"data").toAbsolutePath().toString());Application.launch(Ui.class,args);}
    public static class Ui extends Application {
        private final Map<Integer,NetworkClient> peers=new LinkedHashMap<>();private final Map<String,CompletableFuture<Packet>> pending=new ConcurrentHashMap<>();
        private final List<Map<String,Object>> trace=new ArrayList<>();private final Set<Integer> voted=new HashSet<>();
        private GameServer server;private volatile HudView hud;private NetworkClient focus;private Path output;private AnimationTimer timer;
        private final Pane root=new Pane();private int own;
        public void start(Stage stage)throws Exception{
            output=Path.of(getParameters().getRaw().get(1));Files.createDirectories(output);
            for(String font:List.of("JetBrainsMono-Regular.ttf","RobotoCondensed-Regular.ttf","RobotoCondensed-Bold.ttf","ShareTechMono-Regular.ttf"))Font.loadFont(HudView.class.getResourceAsStream("/assets/fonts/"+font),12);
            var scene=new Scene(root,1536,864);scene.getStylesheets().add(HudView.class.getResource("/ui.css").toExternalForm());stage.setTitle("HIVE — проверка терминала Эхо");stage.setScene(scene);stage.show();
            timer=new AnimationTimer(){long last;public void handle(long now){var view=hud;if(view!=null){if(now-last>180_000_000L){last=now;view.update(focus.state);}view.render();}}};timer.start();
            Thread.ofVirtual().start(()->{var result=new LinkedHashMap<String,Object>();try{run(result);}catch(Throwable e){result.put("passed",false);result.put("error",e.toString());e.printStackTrace();}finally{
                try{Files.write(output.resolve("echo-result.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(result));Files.write(output.resolve("commands.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(trace));}catch(Exception e){e.printStackTrace();}
                Platform.runLater(()->{timer.stop();if(hud!=null)hud.dispose();for(var c:peers.values())c.close();if(server!=null)server.close();Platform.exit();});
            }});
        }
        private void run(Map<String,Object> result)throws Exception{
            var initial=SaveStore.load(Path.of(getParameters().getRaw().getFirst()));int originalCreatures=initial.creatures.size();own=initial.hostSlot;check(initial.actor(own).life.equals("ECHO"),"checkpoint host is Echo");
            server=new GameServer("127.0.0.1",0,initial,null,"");
            for(int slot=0;slot<3;slot++){var c=new NetworkClient("127.0.0.1",server.port(),slot,"","");c.listener=p->{if(p.type.equals("ACK")){var f=pending.get(p.id);if(f!=null)f.complete(p);}};peers.put(slot,c);}
            await(s->peers.values().stream().allMatch(c->c.state!=null),false);for(int slot:peers.keySet())send(slot,"READY",Map.of("ready",true));send(own,"START",Map.of());focus=peers.get(own);await(s->focus.state!=null&&s.phase==Model.Phase.ACTION,false);
            var shown=new CompletableFuture<Void>();Platform.runLater(()->{hud=new HudView(focus,()->{},new Properties(),()->{});root.getChildren().setAll(hud);hud.update(focus.state);shown.complete(null);});shown.get(5,TimeUnit.SECONDS);
            var s=server.inspect().get();double before=s.power;String room="V5";check(EchoRules.offer(s,s.actor(own),"WATCH",room).enabled(),"camera V5 covered and powered");
            var camera=new Packet("ECHO_ACTION","probe-camera",Map.of("action","WATCH","target",room));send(own,camera);double spent=server.inspect().get().power;send(own,camera);check(Math.abs(before-3-spent)<.01&&server.inspect().get().power==spent,"camera charged exactly once");
            await(x->focus.state.actor(own).echoRoom.equals(room),true);shot("01_CAMERA_PANEL",true);double cameraUntil=server.inspect().get().actor(own).echoViewUntil;
            ready();send(own,"ECHO_ACTION",Map.of("action","MARK","target",room));await(x->peers.values().stream().allMatch(c->c.state.tacticalMarks.containsKey(own)),true);shot("02_SHARED_MARK",false);double markUntil=server.inspect().get().tacticalMarks.get(own).expiresAt;
            ready();send(own,"ECHO_ACTION",Map.of("action","DECOY","target",room));check(server.inspect().get().noises.stream().anyMatch(n->n.source.contains("Эхо")&&n.strength==140&&n.roomId.equals(room)),"real decoy noise");
            ready();var current=server.inspect().get();var gate=current.doors.values().stream().filter(d->(d.a.equals(room)||d.b.equals(room))&&d.status==Model.DoorStatus.OPEN&&EchoRules.offer(current,current.actor(own),"CLOSE",d.id).enabled()).findFirst().orElseThrow();send(own,"ECHO_ACTION",Map.of("action","CLOSE","target",gate.id));check(server.inspect().get().doors.get(gate.id).status==Model.DoorStatus.CLOSED,"gate physically closed");
            ready();send(own,"ECHO_ACTION",Map.of("action","OPEN","target",gate.id));check(server.inspect().get().doors.get(gate.id).status==Model.DoorStatus.OPEN,"gate reopened");
            await(x->x.gameTime>Math.max(cameraUntil,markUntil)+.2&&focus.state.actor(own).echoRoom.isEmpty()&&peers.values().stream().allMatch(c->c.state.tacticalMarks.isEmpty()),true);shot("03_EXPIRED",false);
            var end=server.inspect().get();check(end.actor(own).life.equals("ECHO")&&end.actor(own).hp==0,"lost body remains lost");check(peers.values().stream().allMatch(NetworkClient::connected),"all clients connected");check(end.creatures.size()==originalCreatures,"creatures retained");
            result.put("passed",true);result.put("sourceCheckpoint",getParameters().getRaw().getFirst());result.put("players",3);result.put("echoSlot",own);result.put("cameraRoom",room);result.put("gate",gate.id);result.put("powerBefore",before);result.put("powerAfter",end.power);result.put("cameraExpiresAt",cameraUntil);result.put("markExpiresAt",markUntil);result.put("gameSeconds",end.gameTime);result.put("actorOutcomes",end.actors.stream().map(a->Map.of("slot",a.slot,"life",a.life,"hp",a.hp)).toList());result.put("commands",trace.size());result.put("teleportsOrGrantedItems",false);SaveStore.save(output.resolve("verified-echo.json"),end);
        }
        private void ready()throws Exception{await(s->s.phase==Model.Phase.ACTION&&s.gameTime>=s.actor(own).echoReadyAt,true);}
        private void send(int slot,String type,Map<String,?> data)throws Exception{send(slot,new Packet(type,UUID.randomUUID().toString(),data));}
        private void send(int slot,Packet packet)throws Exception{var future=new CompletableFuture<Packet>();pending.put(packet.id,future);peers.get(slot).sendPacket(packet);var ack=future.get(5,TimeUnit.SECONDS);pending.remove(packet.id);trace.add(Map.of("slot",slot,"type",packet.type,"data",packet.data,"accepted",ack.data.path("accepted").asBoolean(),"reason",ack.text("reason")));check(ack.data.path("accepted").asBoolean(),ack.text("reason"));}
        private void await(Predicate<Model.Campaign> condition,boolean vote)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(50);while(System.nanoTime()<until){var s=server.inspect().get(2,TimeUnit.SECONDS);if(condition.test(s))return;if(vote&&s.phase==Model.Phase.VOTING&&voted.add(s.cycle))for(int slot:peers.keySet())send(slot,"VOTE",Map.of("cycle",s.cycle,"option","defer"));if(s.phase==Model.Phase.GAME_OVER)throw new IllegalStateException("unexpected ending");Thread.sleep(35);}throw new IllegalStateException("timeout");}
        private void shot(String name,boolean panel)throws Exception{var done=new CompletableFuture<Void>();Platform.runLater(()->{try{if(panel)hud.openPanel("ТЕРМИНАЛ ЭХО");else hud.closePanel();hud.map.center("V5");hud.update(focus.state);hud.render();root.applyCss();root.layout();javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",output.resolve(name+".png").toFile());done.complete(null);}catch(Throwable error){done.completeExceptionally(error);}});done.get(5,TimeUnit.SECONDS);}
        private void check(boolean valid,String text){if(!valid)throw new IllegalStateException(text);}
    }
}
