package ru.hive13.server;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

/** Only authoritative commands can spend grid power or operate a remote gate. */
public final class EchoSystem {
    private EchoSystem(){}
    public static GameEngine.Result command(GameEngine e,Actor a,String action,String target){
        var s=e.state;var offer=EchoRules.offer(s,a,action,target);if(!offer.enabled())return new GameEngine.Result(false,offer.reason());
        s.power-=offer.power();a.echoReadyAt=s.gameTime+3;String room=target;
        switch(action){
            case "WATCH"->{a.echoRoom=target;a.echoViewUntil=s.gameTime+15;}
            case "MARK"->s.tacticalMarks.put(a.slot,new TacticalMark(a.slot,target,s.gameTime+20));
            case "DECOY"->s.noises.add(new Noise(target,a.id+" / Эхо: громкоговоритель",140,s.gameTime+12));
            case "OPEN","CLOSE"->{var door=s.doors.get(target);door.status=action.equals("OPEN")?DoorStatus.OPEN:DoorStatus.CLOSED;room=a.echoRoom;s.noises.add(new Noise(room,"Эхо: привод "+door.id,45,s.gameTime+8));}
            default->throw new IllegalArgumentException(action);
        }
        e.event("ГРУППА",a.name+" / ЭХО: "+offer.title()+" → "+target+(offer.power()>0?". Питание −"+offer.power()+"%":""),room);
        return new GameEngine.Result(true,"");
    }
    public static void update(GameEngine e){var s=e.state;
        s.tacticalMarks.values().removeIf(mark->mark.expiresAt<=s.gameTime||!s.rooms.containsKey(mark.roomId));
        for(var a:s.actors)if(!a.echoRoom.isEmpty()&&EchoRules.cameraRoom(s,a).isEmpty()){a.echoRoom="";a.echoViewUntil=0;}
    }
}
