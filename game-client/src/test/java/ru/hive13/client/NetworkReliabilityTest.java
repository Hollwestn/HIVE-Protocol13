package ru.hive13.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.hive13.server.*;
import ru.hive13.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

class NetworkReliabilityTest {
    private void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()){assertTrue(System.nanoTime()<end,"Ожидание сети");Thread.sleep(20);}}
    @Test void echoCameraAndSharedMarkUseAuthenticatedSessionAndSurviveReconnect()throws Exception{
        var state=WorldFactory.create("STORY");ExpeditionPlan.install(state);state.hostSlot=1;state.requiredPlayers=2;state.phase=Model.Phase.ACTION;state.power=80;state.flags.add("surface_grid");state.actor(0).life="ECHO";state.actor(0).hp=0;state.actor(2).life="DORMANT";state.creatures.clear();
        state.rooms.get("V1").powered=state.rooms.get("V5").powered=true;
        for(var a:state.actors){a.roomId="V2";a.x=310;a.y=355;a.knownRooms.clear();a.knownRooms.addAll(state.rooms.keySet());}
        var c=new Model.Creature();c.id="VISIBLE_TO_CAMERA";c.type="ГОНЧАЯ";c.roomId=c.home="V5";c.x=775;c.y=180;state.creatures.add(c);
        try(var host=new GameServer("127.0.0.1",0,state,null,"");var medic=new NetworkClient("127.0.0.1",host.port(),1,"","");var echo=new NetworkClient("127.0.0.1",host.port(),0,"","")){
            await(()->medic.state!=null&&echo.state!=null);medic.send("READY",Map.of("ready",true));echo.send("READY",Map.of("ready",true));await(()->medic.state.actor(0).ready&&medic.state.actor(1).ready);medic.send("START",Map.of());await(()->echo.state.phase==Model.Phase.ACTION);
            var acknowledgements=new ConcurrentHashMap<String,Boolean>();echo.listener=p->{if(p.type.equals("ACK"))acknowledgements.put(p.id,p.data.path("accepted").asBoolean());};
            var camera=new Packet("ECHO_ACTION","camera-command",Map.of("action","WATCH","target","V5","owner",2));echo.sendPacket(camera);await(()->acknowledgements.containsKey(camera.id));assertTrue(acknowledgements.get(camera.id),echo.error);
            await(()->echo.state.creatures.stream().anyMatch(x->x.id.equals(c.id)));assertTrue(medic.state.creatures.isEmpty());double power=host.inspect().get().power;echo.sendPacket(camera);Thread.sleep(100);assertEquals(power,host.inspect().get().power);
            await(()->echo.state.gameTime>=echo.state.actor(0).echoReadyAt);String mark=echo.send("ECHO_ACTION",Map.of("action","MARK","target","V1","owner",2));await(()->acknowledgements.containsKey(mark)&&medic.state.tacticalMarks.containsKey(0));assertTrue(acknowledgements.get(mark));assertEquals(0,medic.state.tacticalMarks.get(0).owner);assertFalse(medic.state.tacticalMarks.containsKey(2));
            String token=echo.token;echo.close();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(host.inspect().get().actor(0).connected){assertTrue(System.nanoTime()<deadline);Thread.sleep(20);}
            try(var restored=new NetworkClient("127.0.0.1",host.port(),0,"",token)){await(()->restored.state!=null);assertEquals("ECHO",restored.state.actor(0).life);assertEquals(0,restored.state.actor(0).hp);assertTrue(restored.state.tacticalMarks.containsKey(0));assertEquals(token,restored.token);}
        }
    }
    @Test void medicAndScoutResumeSecondChapterAndReconnectWithoutLosingCommandAcks()throws Exception{
        var state=WorldFactory.create("STORY");state.hostSlot=1;state.requiredPlayers=2;state.actor(0).life="DORMANT";
        ExpeditionPlan.install(state);state.currentAct=2;state.phase=Model.Phase.ACTION;state.resumePhase=Model.Phase.ACTION;state.remaining=2;
        for(var a:state.actors){a.roomId="V0";a.x=state.rooms.get("V0").center().x();a.y=state.rooms.get("V0").center().y();}
        try(var host=new GameServer("127.0.0.1",0,state,null,"");var medic=new NetworkClient("127.0.0.1",host.port(),1,"","");var scout=new NetworkClient("127.0.0.1",host.port(),2,"","")){
            await(()->medic.state!=null&&scout.state!=null);assertEquals(2,scout.state.currentAct);
            var accepted=new ConcurrentHashMap<String,Boolean>();scout.listener=p->{if(p.type.equals("ACK"))accepted.put(p.id,p.data.path("accepted").asBoolean());};
            medic.send("READY",Map.of("ready",true));String s=scout.send("READY",Map.of("ready",true));await(()->accepted.containsKey(s));
            medic.send("START",Map.of());await(()->scout.state.phase==Model.Phase.ACTION);
            var sequence=new ArrayList<String>();for(int i=0;i<20;i++)sequence.add(scout.send("STOP",Map.of()));await(()->sequence.stream().allMatch(accepted::containsKey));
            assertTrue(sequence.stream().allMatch(id->accepted.get(id)));
            await(()->scout.state.phase==Model.Phase.VOTING);int cycle=scout.state.cycle;
            medic.send("VOTE",Map.of("cycle",cycle,"option","defer"));String vote=scout.send("VOTE",Map.of("cycle",cycle,"option","defer"));
            await(()->accepted.containsKey(vote)&&scout.state.cycle>cycle);assertTrue(accepted.get(vote));
            var decision=scout.state.decisions.getLast();assertEquals("defer",decision.optionId);assertEquals(Set.of(1,2),decision.ballots.keySet());
            assertTrue(medic.connected());assertTrue(scout.connected());String token=scout.token;scout.close();
            long disconnectDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(host.inspect().get(1,TimeUnit.SECONDS).actor(2).connected){assertTrue(System.nanoTime()<disconnectDeadline);Thread.sleep(20);}
            try(var again=new NetworkClient("127.0.0.1",host.port(),2,"",token)){await(()->again.state!=null);assertEquals(token,again.token);assertEquals(2,again.slot);assertEquals(2,again.state.currentAct);}
        }
    }
    @Test void callbackFailureDoesNotCloseTransportAndHostCloseIsReported()throws Exception{
        var host=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),null,"");
        try(var client=new NetworkClient("127.0.0.1",host.port(),0,"","")){
            await(()->client.state!=null);var calls=new AtomicInteger();client.listener=p->{if(calls.incrementAndGet()==1)throw new IllegalStateException("test callback");};
            await(()->calls.get()>=3);assertTrue(client.connected());host.close();await(()->!client.connected());assertTrue(client.error.contains("Связь с хостом потеряна"));
        }finally{host.close();}
    }
}
