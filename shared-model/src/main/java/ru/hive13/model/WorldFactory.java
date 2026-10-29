package ru.hive13.model;
import java.util.*;
import static ru.hive13.model.Model.*;

public final class WorldFactory {
    public static Campaign create(String scenario){
        var s=new Campaign();s.scenario=scenario;
        Object13Plan.install(s);
        s.rooms.get("B4").biomass=64;s.rooms.get("B5").biomass=32;s.rooms.get("B7").biomass=22;
        s.rooms.get("B7").powered=false;
        String[] ids={"A-17","B-04","C-31"},names={"СОКОЛ","ВОЛКОВА","КОРШУН"},jobs={"ИНЖЕНЕР","МЕДИК-БИОЛОГ","РАЗВЕДЧИК"};
        String[][] parts={{"head","Голова"},{"neck","Шея"},{"chest","Грудь"},{"abdomen","Живот"},{"pelvis","Таз"},{"leftShoulder","Левое плечо"},{"rightShoulder","Правое плечо"},{"leftArm","Левая рука"},{"rightArm","Правая рука"},{"leftHand","Левая кисть"},{"rightHand","Правая кисть"},{"leftLeg","Левая нога"},{"rightLeg","Правая нога"}};
        for(int i=0;i<3;i++){var a=new Actor();a.slot=i;a.id=ids[i];a.name=names[i];a.profession=jobs[i];a.x=225+i*20;a.y=255;
            for(var p:parts)a.body.put(p[0],new BodyPart(p[0],p[1]));a.body.get("rightArm").infection=i==1?28:3;
            a.inventory.put("medkit",new Item("medkit","Аптечка",i==1?4:1,.7));a.inventory.put("filter",new Item("filter","Фильтр",2,.3));
            a.inventory.put("battery",new Item("battery","Батарея",2,.5));if(i==0)a.inventory.put("tool",new Item("tool","Инструменты",1,2));
            a.knownRooms.addAll(s.rooms.keySet());s.actors.add(a);}
        s.supplies.putAll(Map.of("fuel",6,"parts",12,"filters",6,"meds",6,"batteries",6,"food",12,"ammo",18));
        creature(s,"CR01","ШЕПТУН","B4",500,80);creature(s,"CR02","ГОНЧАЯ","B5",710,125);
        var lina=new Npc();lina.id="lina";lina.name="Лина";lina.location="C2";lina.faction="Собиратели";s.npcs.put(lina.id,lina);
        s.drones.add(new Drone());s.discoveredRegions.add("object13");s.locationStates.put("object13","База");return s;
    }
    private static void room(Campaign s,String id,String n,double x,double y,double w,double h){s.rooms.put(id,new Room(id,n,x,y,w,h));}
    private static void door(Campaign s,String id,String a,String b,double x,double y,double xx,double yy,DoorStatus st){s.doors.put(id,new Door(id,a,b,new Point(x,y),new Point(xx,yy),st));}
    private static void creature(Campaign s,String id,String type,String r,double x,double y){var c=new Creature();c.id=id;c.type=type;c.roomId=r;c.x=x;c.y=y;s.creatures.add(c);}
}
