package ru.hive13.server;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import java.util.*;
import java.nio.file.Path;

class EchoTest {
    private GameEngine engine(){
        var s=WorldFactory.create("STORY");s.currentAct=2;s.phase=Phase.ACTION;s.remaining=60;s.power=80;s.flags.add("surface_grid");s.rooms.clear();s.doors.clear();s.creatures.clear();
        for(int i=0;i<5;i++){String id=""+(char)('A'+i);s.rooms.put(id,new Room(id,id,i*120,0,100,100));if(i>0){String from=""+(char)('A'+i-1);var d=new Door(from+id,from,id,new Point(i*120-20,50),new Point(i*120,50),DoorStatus.OPEN);s.doors.put(d.id,d);}}
        for(var a:s.actors){a.connected=a.slot!=2;a.life=a.slot==0?"ECHO":a.slot==1?"ALIVE":"DORMANT";a.roomId=a.slot==0?"E":"A";var p=s.rooms.get(a.roomId).center();a.x=p.x();a.y=p.y();a.knownRooms.clear();a.knownRooms.addAll(s.rooms.keySet());}
        return new GameEngine(s);
    }
    private Packet packet(String action,String target){return new Packet("ECHO_ACTION",UUID.randomUUID().toString(),Map.of("action",action,"target",target));}
    private void accepted(GameEngine e,String action,String target){var r=e.command(0,packet(action,target));assertTrue(r.accepted(),r.reason());}
    @Test void cameraCostsPowerAndDoesNotUseDeadBodyOrRevealDistantThreats(){
        var e=engine();for(String room:List.of("C","E")){var c=new Creature();c.id=room;c.type="ГОНЧАЯ";c.home=c.roomId=room;c.x=e.state.rooms.get(room).center().x();c.y=50;e.state.creatures.add(c);}
        assertTrue(e.view(0).creatures.isEmpty());assertFalse(e.command(0,packet("WATCH","E")).accepted());
        accepted(e,"WATCH","C");assertEquals(77,e.state.power);assertEquals(List.of("C"),e.view(0).creatures.stream().map(c->c.id).toList());assertTrue(e.view(1).creatures.isEmpty());
        e.state.gameTime=16;EchoSystem.update(e);assertTrue(e.view(0).creatures.isEmpty());assertEquals("LAST_KNOWN",e.view(0).contacts.get("C").certainty);
    }
    @Test void coverageNeedsKnownPoweredRoomsALiveTeamAndCommunication(){
        var e=engine();assertEquals(Set.of("A","B","C"),EchoRules.coveredRooms(e.state,e.state.actor(0)));
        e.state.doors.get("AB").status=DoorStatus.SEALED;assertEquals(Set.of("A"),EchoRules.coveredRooms(e.state,e.state.actor(0)));
        e.state.doors.get("AB").status=DoorStatus.OPEN;e.state.rooms.get("C").powered=false;assertFalse(EchoRules.coveredRooms(e.state,e.state.actor(0)).contains("C"));
        e.state.actor(1).knownRooms.remove("B");assertFalse(e.command(0,packet("WATCH","B")).accepted());e.state.actor(1).connected=false;assertFalse(e.command(0,packet("WATCH","A")).accepted());
        e.state.actor(1).connected=true;e.state.flags.remove("surface_grid");assertFalse(e.command(0,packet("WATCH","A")).accepted());e.state.flags.add("surface_grid");e.state.power=27;assertFalse(e.command(0,packet("WATCH","A")).accepted());
    }
    @Test void liveOrDisconnectedActorsCannotForgeEchoAndBodyCommandsStayBlocked(){
        var e=engine();assertFalse(e.command(1,packet("WATCH","A")).accepted());
        assertFalse(e.command(0,new Packet("MOVE","move",Map.of("x",50,"y",50,"mode","RUN"))).accepted());
        e.state.actor(0).connected=false;assertFalse(e.command(0,packet("MARK","A")).accepted());e.state.actor(0).connected=true;e.state.scenario="HARDCORE";assertFalse(e.command(0,packet("MARK","A")).accepted());assertEquals(80,e.state.power);
    }
    @Test void retryCannotSpendTwiceAndCooldownPreventsSpam(){var e=engine();var p=packet("WATCH","B");assertTrue(e.command(0,p).accepted());assertTrue(e.command(0,p).accepted());assertEquals(77,e.state.power);assertFalse(e.command(0,packet("MARK","B")).accepted());e.state.gameTime=3;accepted(e,"MARK","B");assertEquals(1,e.state.tacticalMarks.size());}
    @Test void decoyActuallyDrawsAnUnseenCreatureAndCannotDamageOrDeleteIt(){
        var e=engine();var c=new Creature();c.id="H";c.type="ГОНЧАЯ";c.roomId=c.home="C";c.x=290;c.y=50;e.state.creatures.add(c);
        assertFalse(e.command(0,packet("DECOY","B")).accepted());accepted(e,"WATCH","B");e.state.gameTime=3;accepted(e,"DECOY","B");assertEquals(73,e.state.power);e.advance(.1);assertEquals("B",c.targetRoom);assertEquals("INVESTIGATE_SOUND",c.state);assertEquals(1,e.state.creatures.size());assertEquals(0,c.suppressedUntil);assertEquals(140,e.state.noises.getFirst().strength);
    }
    @Test void ordinaryDoorNeedsCameraAndCannotCloseOnCrossingPlayerOrBypassIsolation(){
        var e=engine();assertFalse(e.command(0,packet("CLOSE","AB")).accepted());accepted(e,"WATCH","B");e.state.gameTime=3;
        e.state.actor(1).path.add(new Point(170,50));e.state.actor(1).pathDoors.add("AB");assertFalse(e.command(0,packet("CLOSE","AB")).accepted());e.state.actor(1).path.clear();e.state.actor(1).pathDoors.clear();
        accepted(e,"CLOSE","AB");assertEquals(DoorStatus.CLOSED,e.state.doors.get("AB").status);assertEquals(75,e.state.power);e.state.gameTime=6;accepted(e,"OPEN","AB");
        for(var status:List.of(DoorStatus.LOCKED,DoorStatus.SEALED,DoorStatus.JAMMED,DoorStatus.BROKEN)){e.state.gameTime=9;e.state.doors.get("AB").status=status;assertFalse(e.command(0,packet("OPEN","AB")).accepted());assertEquals(status,e.state.doors.get("AB").status);}
    }
    @Test void marksAreSharedBoundedExpireAndCannotBeAttributedToAnotherPlayer(){
        var e=engine();accepted(e,"MARK","B");assertEquals(0,e.view(1).tacticalMarks.get(0).owner);e.state.gameTime=3;accepted(e,"MARK","C");assertEquals(1,e.state.tacticalMarks.size());assertEquals("C",e.state.tacticalMarks.get(0).roomId);e.state.gameTime=24;EchoSystem.update(e);assertTrue(e.state.tacticalMarks.isEmpty());
    }
    @Test void votingFreezesAllTerminalTimersAndCosts(){var e=engine();accepted(e,"WATCH","B");e.state.gameTime=3;accepted(e,"MARK","C");var a=e.state.actor(0);double time=e.state.gameTime,until=a.echoViewUntil,ready=a.echoReadyAt;e.state.phase=Phase.VOTING;e.state.voteRemaining=30;e.advance(10);assertEquals(time,e.state.gameTime);assertEquals(until,a.echoViewUntil);assertEquals(ready,a.echoReadyAt);assertEquals(1,e.state.tacticalMarks.size());assertFalse(e.command(0,packet("WATCH","B")).accepted());assertEquals(77,e.state.power);}
    @Test void cameraDropsImmediatelyWithPowerAndSaveRoundTripKeepsLimits(@TempDir Path dir)throws Exception{
        var e=engine();accepted(e,"WATCH","B");e.state.gameTime=3;accepted(e,"MARK","B");Path save=dir.resolve("echo.json");SaveStore.save(save,e.state);var restored=SaveStore.load(save);assertEquals("B",restored.actor(0).echoRoom);assertEquals(15,restored.actor(0).echoViewUntil);assertEquals(1,restored.tacticalMarks.size());
        restored.power=20;var engine=new GameEngine(restored);EchoSystem.update(engine);assertEquals("",restored.actor(0).echoRoom);assertEquals(0,restored.actor(0).echoViewUntil);
        ExpeditionPlan.install(e.state);assertTrue(e.state.tacticalMarks.isEmpty());assertEquals("",e.state.actor(0).echoRoom);assertEquals("ECHO",e.state.actor(0).life);
        e.state.actor(0).echoRoom="V1";CampaignPlan.install(e.state,3);assertEquals("",e.state.actor(0).echoRoom);
    }
}
