package ru.hive13.server;

import java.util.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

public final class DefenseSystem {
    private DefenseSystem(){}
    public static GameEngine.Result fire(GameEngine e,Actor a,String target,String weapon){
        var w=DefenseRules.weapon(weapon);if(w==null)return no("Неизвестное оружие");
        var c=e.state.creatures.stream().filter(x->x.id.equals(target)).findFirst().orElse(null);
        if(c==null)return no("Контакт потерян");
        var view=e.view(a.slot);if(view.creatures.stream().noneMatch(x->x.id.equals(target)))return no("Контакт больше не подтверждён");var contact=view.contacts.get(target);
        String reason=DefenseRules.reason(e.state,a,contact,w);if(!reason.isEmpty())return no(reason);
        if(c.immuneUntil>e.state.gameTime)return no("Тварь адаптировалась. Отходите: повторное сдерживание временно не действует");
        a.inventory.get(w.ammunition()).count--;a.weaponReadyAt=e.state.gameTime+4;a.hidden=false;a.task="";a.queue.clear();
        double duration=w.seconds();if(weapon.equals("flaregun")&&c.type.equals("ГОНЧАЯ"))duration=3;
        suppress(e,c,duration/(1+c.resistance));c.resistance=Math.min(3,c.resistance+.5);
        if(e.state.currentAct>=2)c.exertion=Math.min(110,c.exertion+18);
        e.state.noises.add(new Noise(a.roomId,a.id+" / "+w.name(),w.noise(),e.state.gameTime+15));
        a.stress=Math.min(100,a.stress+3);e.state.progress.put("last_fight",e.state.gameTime);
        e.event("БОЙ",a.name+": "+w.name()+". "+c.type+" задержана на "+(int)Math.ceil(c.suppressedUntil-e.state.gameTime)+" с. Она восстановится. Отходите!",c.roomId);
        return new GameEngine.Result(true,"");
    }
    private static GameEngine.Result no(String reason){return new GameEngine.Result(false,reason);}
    public static void suppress(GameEngine e,Creature c,double seconds){if(c.immuneUntil>e.state.gameTime)return;c.suppressedUntil=e.state.gameTime+Math.max(2,seconds);c.immuneUntil=c.suppressedUntil+4;c.state="SUPPRESSED";c.path.clear();c.pathDoors.clear();c.breachProgress=0;}
    public static void complete(GameEngine e,Actor a,WorldObjects.Fixture f,String action){var s=e.state;
        switch(action){
            case "ARMORY"->{a.privateEvidence.add("defense_issued");item(a,"shotgun","Дробовик",1,2);item(a,"stunner","Электрошок",1,.6);item(a,"flaregun","Ракетница",1,.7);item(a,"shell","Патроны сдерживания",4,.1);item(a,"shock_cell","Заряд электрошока",3,.1);item(a,"flare","Сигнальная ракета",2,.4);}
            case "REARM"->{item(a,"shell","Патроны сдерживания",2,.1);item(a,"shock_cell","Заряд электрошока",2,.1);item(a,"flare","Сигнальная ракета",1,.4);}
            case "HIDE"->{a.hidden=true;a.flashlight=false;}
            case "UV"->{s.progress.put("uv:"+f.room(),s.gameTime+20);s.noises.add(new Noise(f.room(),"УФ-щит",65,s.gameTime+10));}
            case "DECOY"->s.noises.add(new Noise(f.room(),"Громкоговоритель",180,s.gameTime+20));
            case "BARRICADE"->{var d=s.doors.get("D10");d.status=DoorStatus.CLOSED;d.bracedUntil=s.gameTime+25;}
            default->throw new IllegalArgumentException(action);
        }
    }
    private static void item(Actor a,String id,String name,int n,double weight){a.inventory.computeIfAbsent(id,key->new Item(key,name,0,weight)).count+=n;}
    public static void update(GameEngine e,double dt){var s=e.state;
        for(var r:s.rooms.values()){String key="burn_recover:"+r.id;double until=s.progress.getOrDefault(key,0d);if(until>0&&until<=s.gameTime){s.progress.remove(key);r.nutrients=35;r.biomass=8;e.event("УГРОЗА","Датчик "+r.id+": после огня биологическая активность возобновилась. Колония не уничтожена.",r.id);}}
        for(var d:s.doors.values())if(d.bracedUntil>0&&d.bracedUntil<=s.gameTime){d.bracedUntil=0;if(d.status==DoorStatus.CLOSED)d.status=DoorStatus.OPEN;e.event("УГРОЗА","Баррикада "+d.id+" больше не держит проход.",d.a);}
        for(var c:s.creatures){c.resistance=Math.max(0,c.resistance-dt*.015);
            if(s.progress.getOrDefault("uv:"+c.roomId,0d)>s.gameTime&&c.immuneUntil<=s.gameTime)suppress(e,c,c.type.equals("ШЕПТУН")?8:3);}
    }
    /** A closed door buys time. No attacks or movement through its collision boundary. */
    public static boolean breach(GameEngine e,Creature c,double dt){var s=e.state;
        var d=s.doors.values().stream().filter(x->x.status==DoorStatus.CLOSED&&(x.a.equals(c.roomId)||x.b.equals(c.roomId)))
            .filter(x->s.noises.stream().anyMatch(n->n.roomId.equals(x.other(c.roomId))&&n.strength>35&&n.expires>=s.gameTime)).findFirst().orElse(null);
        if(d==null){c.breachDoor="";c.breachProgress=0;return false;}
        var point=d.a.equals(c.roomId)?d.pa:d.pb;
        if(c.positionForDefense().distance(point)>20){var route=Pathfinder.find(s,c.roomId,c.positionForDefense(),c.roomId,point);if(route!=null){c.path=route.points();c.pathDoors=route.doors();}return false;}
        if(!d.id.equals(c.breachDoor)){c.breachDoor=d.id;c.breachProgress=0;e.event("УГРОЗА","Тяжёлый стук в "+d.id+". Обычная дверь долго не выдержит.",c.roomId);}
        c.state="BREACH";c.path.clear();c.breachProgress+=dt;
        if(c.breachProgress>=(d.bracedUntil>s.gameTime?20:12)){d.status=DoorStatus.BROKEN;d.bracedUntil=0;c.breachProgress=0;e.event("УГРОЗА",d.id+": тварь проломила дверь. Проход открыт.",c.roomId);}return true;
    }
}
