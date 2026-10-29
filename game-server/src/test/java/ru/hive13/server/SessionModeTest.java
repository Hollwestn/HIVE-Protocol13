package ru.hive13.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import java.util.*;
class SessionModeTest {
    private GameEngine duo(){var e=new GameEngine(WorldFactory.create("STORY"));for(int i=0;i<2;i++){e.state.actor(i).connected=true;e.state.actor(i).ready=true;}assertTrue(cmd(e,0,"SESSION",Map.of("players",2)));return e;}
    private boolean cmd(GameEngine e,int slot,String type,Map<String,?> data){return e.command(slot,new Packet(type,UUID.randomUUID().toString(),data)).accepted();}
    @Test void defaultThreePlayerModeStillRejectsTwoPeople(){var e=duo();assertTrue(cmd(e,0,"SESSION",Map.of("players",3)));assertFalse(cmd(e,0,"START",Map.of()));assertEquals(Phase.LOBBY,e.state.phase);}
    @Test void onlyHostCanChooseTwoOrThreeBeforeStart(){var e=duo();assertFalse(cmd(e,1,"SESSION",Map.of("players",2)));assertFalse(cmd(e,0,"SESSION",Map.of("players",1)));assertTrue(cmd(e,0,"START",Map.of()));assertFalse(cmd(e,0,"SESSION",Map.of("players",3)));assertEquals("DORMANT",e.state.actor(2).life);}
    @Test void twoPlayerModeRequiresBothReadyAndARealConnection(){var e=duo();e.state.actor(1).ready=false;assertFalse(cmd(e,0,"START",Map.of()));e.state.actor(1).ready=true;e.state.actor(1).connected=false;assertFalse(cmd(e,0,"START",Map.of()));e.state.actor(1).connected=true;assertTrue(cmd(e,0,"START",Map.of()));assertTrue(cmd(e,0,"MOVE",Map.of("x",250,"y",275,"mode","WALK")));e.advance(.5);assertNotEquals(225,e.state.actor(0).x);assertTrue(e.state.actor(1).path.isEmpty());}
    @Test void twoAgreeingHumansResolveWithoutAnInventedThirdVote(){var e=duo();assertTrue(cmd(e,0,"START",Map.of()));e.advance(60);assertTrue(cmd(e,0,"VOTE",Map.of("cycle",1,"option","seal_b4")));assertEquals(Phase.VOTING,e.state.phase);assertTrue(cmd(e,1,"VOTE",Map.of("cycle",1,"option","seal_b4")));assertEquals(2,e.state.cycle);assertEquals(2,e.state.decisions.getLast().ballots.size());assertFalse(e.state.decisions.getLast().ballots.containsKey(2));assertTrue(e.state.flags.contains("b4_sealed"));}
    @Test void disagreementCannotCreateAnArtificialMajority(){var e=duo();assertTrue(cmd(e,0,"START",Map.of()));e.advance(60);cmd(e,0,"VOTE",Map.of("cycle",1,"option","generator"));cmd(e,1,"VOTE",Map.of("cycle",1,"option","seal_b4"));assertEquals("defer",e.state.decisions.getLast().optionId);assertEquals(79,e.state.water);assertFalse(e.state.flags.contains("b4_sealed"));}
}
