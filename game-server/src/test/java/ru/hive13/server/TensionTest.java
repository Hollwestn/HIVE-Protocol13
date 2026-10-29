package ru.hive13.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

class TensionTest {
    @Test void seededDirectorIsReproducibleAndDoesNotSpawnEnemies(){var a=new GameEngine(WorldFactory.create("STORY"));var b=new GameEngine(Json.copy(a.state,Campaign.class));a.state.gameTime=b.state.gameTime=50;TensionDirector.update(a);TensionDirector.update(b);assertEquals(a.state.rngState,b.state.rngState);assertEquals(a.state.progress,b.state.progress);assertEquals(a.state.ventilation,b.state.ventilation);assertEquals(Json.MAPPER.valueToTree(a.state.events),Json.MAPPER.valueToTree(b.state.events));assertEquals(2,a.state.creatures.size());}
    @Test void severeInjuryAndRecentCombatGiveARespite(){var e=new GameEngine(WorldFactory.create("STORY"));e.state.gameTime=100;e.state.actor(0).hp=20;TensionDirector.update(e);assertTrue(e.state.events.isEmpty());assertEquals(135,e.state.progress.get("director_next"));e.state.actor(0).hp=100;e.state.gameTime=140;e.state.progress.put("last_fight",135d);TensionDirector.update(e);assertTrue(e.state.events.isEmpty());}
    @Test void aTimedSensorOutageRestoresTheActualSensor(){var e=new GameEngine(WorldFactory.create("STORY"));e.state.rooms.get("A2").powered=false;e.state.progress.put("blackout:A2",8d);e.state.gameTime=7;TensionDirector.update(e);assertFalse(e.state.rooms.get("A2").powered);e.state.gameTime=8;TensionDirector.update(e);assertTrue(e.state.rooms.get("A2").powered);assertFalse(e.state.progress.containsKey("blackout:A2"));}
}
