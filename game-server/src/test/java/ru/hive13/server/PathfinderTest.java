package ru.hive13.server;
import org.junit.jupiter.api.Test;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import static org.junit.jupiter.api.Assertions.*;
class PathfinderTest {
    @Test void reachesKnownRoomThroughOpenDoors(){var s=WorldFactory.create("STORY");assertNotNull(Pathfinder.find(s,"A2",new Point(245,255),"B4",s.rooms.get("B4").center()));}
    @Test void isolatedRoomHasNoPath(){var s=WorldFactory.create("STORY");s.doors.values().stream().filter(d->d.a.equals("B4")||d.b.equals("B4")).forEach(d->d.status=DoorStatus.SEALED);assertNull(Pathfinder.find(s,"A2",s.rooms.get("A2").center(),"B4",s.rooms.get("B4").center()));}
    @Test void movementConsumesDistanceWithoutTeleport(){var path=new java.util.ArrayList<>(java.util.List.of(new Point(100,0),new Point(100,100)));assertEquals(new Point(100,25),Pathfinder.advance(new Point(0,0),path,125));assertEquals(1,path.size());}
    @Test void biomassBlocksRoom(){var s=WorldFactory.create("STORY");s.rooms.get("B4").biomass=99;assertNull(Pathfinder.find(s,"A2",s.rooms.get("A2").center(),"B4",s.rooms.get("B4").center()));}
    @Test void campaignRoundTripKeepsLocalInfectionAndSeed()throws Exception{var s=WorldFactory.create("STORY");var restored=Json.MAPPER.readValue(Json.MAPPER.writeValueAsBytes(s),Campaign.class);assertEquals(28,restored.actor(1).body.get("rightArm").infection);assertEquals(s.nextRandom(),restored.nextRandom());}
}
