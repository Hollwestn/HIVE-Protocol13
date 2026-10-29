import ru.hive13.model.*;
import ru.hive13.server.*;
import ru.hive13.client.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class NetworkSoak {
  static final class Proxy implements AutoCloseable {
    final ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
    volatile Socket local,remote;final AtomicLong bytes=new AtomicLong();volatile boolean paused;
    Proxy(int target,int rate,int pause)throws Exception{
      Thread.ofPlatform().daemon().start(()->{try{
        local=listener.accept();remote=new Socket("127.0.0.1",target);remote.setReceiveBufferSize(65536);local.setTcpNoDelay(true);
        Thread.ofPlatform().daemon().start(()->{try{local.getInputStream().transferTo(remote.getOutputStream());}catch(IOException ignored){}});
        long start=System.nanoTime();byte[] buf=new byte[2048];int n;
        while((n=remote.getInputStream().read(buf))>=0){
          if(!paused&&pause>0&&System.nanoTime()-start>TimeUnit.SECONDS.toNanos(20)){paused=true;Thread.sleep(pause*1000L);}
          local.getOutputStream().write(buf,0,n);local.getOutputStream().flush();bytes.addAndGet(n);
          Thread.sleep(0,0);TimeUnit.NANOSECONDS.sleep((long)n*1_000_000_000L/rate);
        }
      }catch(Exception ignored){}finally{close();}});
    }
    public void close(){try{listener.close();}catch(IOException ignored){}for(var s:new Socket[]{local,remote})if(s!=null)try{s.close();}catch(IOException ignored){}}
  }
  static void await(java.util.function.BooleanSupplier f)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!f.getAsBoolean()){if(System.nanoTime()>end)throw new IOException("await timeout");Thread.sleep(20);}}
  public static void main(String[] args)throws Exception{
    Path report=Path.of(args[1]);int seconds=Integer.parseInt(args[2]),humans=Integer.parseInt(args[3]),rate=Integer.parseInt(args[4]),pause=Integer.parseInt(args[5]);
    var initial=SaveStore.load(Path.of(args[0]));int sourceAct=initial.currentAct;initial.hostSlot=1;initial.requiredPlayers=humans;
    var clients=new LinkedHashMap<Integer,NetworkClient>();var acks=new ConcurrentHashMap<String,Boolean>();var sent=new ArrayList<String>();var frames=new ConcurrentHashMap<Integer,AtomicInteger>();
    var result=new LinkedHashMap<String,Object>();boolean passed=false;
    try(var host=new GameServer("127.0.0.1",0,initial,null,"");var proxy=new Proxy(host.port(),rate,pause)){
      for(int slot:humans==2?new int[]{1,2}:new int[]{1,0,2}){
        var c=new NetworkClient("127.0.0.1",slot==2?proxy.listener.getLocalPort():host.port(),slot,"","");clients.put(slot,c);frames.put(slot,new AtomicInteger());
        c.listener=p->{if(p.type.equals("ACK"))acks.put(p.id,p.data.path("accepted").asBoolean());if(p.type.equals("SNAPSHOT"))frames.get(slot).incrementAndGet();};
      }
      await(()->clients.values().stream().allMatch(c->c.state!=null));
      for(var c:clients.values())sent.add(c.send("READY",Map.of("ready",true)));await(()->sent.stream().allMatch(acks::containsKey));
      sent.add(clients.get(1).send("START",Map.of()));await(()->clients.get(2).state.phase!=Model.Phase.LOBBY);
      var restored=host.inspect().get(2,TimeUnit.SECONDS);
      if(restored.phase==Model.Phase.GAME_OVER&&!restored.campaignComplete&&restored.actors.stream().anyMatch(a->a.life.equals("EVACUATED"))){
        for(var c:clients.values())sent.add(c.send("CONTINUE_CHAPTER",Map.of()));
        await(()->clients.get(2).state.currentAct>restored.currentAct&&clients.get(2).state.phase==Model.Phase.ACTION);
      }
      var running=host.inspect().get(2,TimeUnit.SECONDS);
      result.put("sourceAct",sourceAct);result.put("runningAct",running.currentAct);result.put("phaseAtStart",running.phase.toString());
      long start=System.nanoTime(),lastChat=0;var voted=new HashSet<Integer>();var cycles=new LinkedHashSet<Integer>();double maxFreshness=0;
      while(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(seconds)){
        var state=host.inspect().get(2,TimeUnit.SECONDS);cycles.add(state.cycle);
        if(state.phase==Model.Phase.VOTING&&voted.add(state.cycle))for(var c:clients.values())sent.add(c.send("VOTE",Map.of("cycle",state.cycle,"option","defer")));
        if(System.nanoTime()-lastChat>TimeUnit.SECONDS.toNanos(3)){lastChat=System.nanoTime();for(var c:clients.values())sent.add(c.send("CHAT",Map.of("text","Проверка соединения "+sent.size())));}
        for(var c:clients.values())if(!c.connected())throw new IOException("Disconnected slot="+c.slot+" "+c.error);
        maxFreshness=Math.max(maxFreshness,(System.nanoTime()-clients.get(2).receivedAt)/1e9);Thread.sleep(40);
      }
      await(()->sent.stream().allMatch(acks::containsKey));
      passed=sent.stream().allMatch(id->Boolean.TRUE.equals(acks.get(id)))&&clients.values().stream().allMatch(c->c.connected()&&c.error.isEmpty());
      result.put("passed",passed);result.put("act",initial.currentAct);result.put("seconds",seconds);result.put("players",humans);result.put("rateBytesPerSecond",rate);result.put("readerPauseSeconds",pause);result.put("pauseExecuted",proxy.paused);result.put("proxyBytes",proxy.bytes.get());result.put("commandsSent",sent.size());result.put("commandsAcknowledged",sent.stream().filter(acks::containsKey).count());result.put("cycles",cycles);result.put("maxSnapshotAgeSeconds",maxFreshness);
      result.put("clients",clients.entrySet().stream().map(e->Map.of("slot",e.getKey(),"connected",e.getValue().connected(),"error",e.getValue().error,"frames",frames.get(e.getKey()).get(),"rtt",e.getValue().rtt)).toList());
    }catch(Exception e){result.put("passed",false);result.put("error",e.toString());throw e;}
    finally{for(var c:clients.values())c.close();Files.write(report,Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(result));}
    if(!passed)throw new AssertionError(result.toString());
  }
}
