package ru.hive13.model;

import java.util.*;
import static ru.hive13.model.Model.*;

/** One world-space blueprint shared by navigation, rendering and save migration.
 * Origin matches Reference A's map viewport (268,125); one world unit is one base pixel. */
public final class Object13Plan {
    public static final int REVISION=2;
    private Object13Plan(){}
    public static void install(Campaign s){
        room(s,"B1","СЕРВЕРНАЯ",38,62,132,122,75,96,38,62,68,62,68,68,119,68,119,72,170,72,170,184,38,184);
        room(s,"B2","СИЛОВОЙ БЛОК",185,65,108,124,224,91);
        room(s,"B3","ВЕНТИЛЯЦИЯ",333,94,104,96,349,121);
        room(s,"B4","УЛЕЙ / АКТИВНЫЙ",479,28,148,244,502,99,479,28,620,28,620,76,627,76,627,151,615,151,615,195,599,195,599,272,479,272);
        room(s,"B5","КАРАНТИН",647,72,114,123,672,145);
        room(s,"A1","ЖИЛОЙ БЛОК",87,182,97,140,102,250,138,182,184,182,184,322,87,322,87,227,138,227);
        room(s,"A2","МЕДБЛОК",219,225,100,102,249,257);
        room(s,"H","ЦЕНТРАЛЬНЫЙ КОРИДОР",324,249,154,51,336,267);
        room(s,"C1","ДЕЗАКТИВАЦИЯ",638,234,110,90,651,253);
        room(s,"C2","ЭВАКУАЦИОННЫЙ ШЛЮЗ",782,234,108,100,790,252);
        room(s,"A3","СТОЛОВАЯ",91,328,113,87,103,351);
        room(s,"B6","АРХИВ",274,352,113,93,314,378);
        room(s,"B7","ЛАБОРАТОРИЯ",394,303,181,141,465,358,394,303,441,303,441,322,575,322,575,444,428,444,428,350,394,350);
        room(s,"B8","ХРАНИЛИЩЕ ОБРАЗЦОВ",584,336,113,94,603,357);
        door(s,"D01","B1","B2",170,99,185,99,177,99,DoorStatus.CLOSED);
        door(s,"D02","B2","B3",293,102,333,102,324,102,DoorStatus.OPEN);
        door(s,"D03","B3","B4",437,96,479,96,462,96,DoorStatus.OPEN);
        door(s,"D04","B4","B5",627,150,647,150,637,150,DoorStatus.OPEN);
        door(s,"D05","A1","B1",125,227,125,184,125,207,DoorStatus.OPEN);
        door(s,"D06","A2","B2",280,225,280,189,280,192,DoorStatus.OPEN);
        door(s,"D07","A1","A2",184,269,219,269,204,269,DoorStatus.OPEN);
        door(s,"D08","A2","H",319,254,324,254,322,254,DoorStatus.OPEN);
        door(s,"D09","H","B3",374,249,374,190,374,219,DoorStatus.OPEN);
        door(s,"D10","H","B4",478,270,479,270,478.5,270,DoorStatus.OPEN);
        door(s,"D11","H","C1",478,290,638,290,622,290,DoorStatus.OPEN);
        door(s,"D12","C1","B5",694,234,694,195,694,213,DoorStatus.CLOSED);
        door(s,"D13","C1","C2",748,275,782,275,753,275,DoorStatus.LOCKED);
        door(s,"D14","A1","A3",145,322,145,328,145,325,DoorStatus.OPEN);
        door(s,"D15","A2","B6",285,327,285,352,285,340,DoorStatus.OPEN);
        door(s,"D16","H","B7",455,300,455,322,455,311,DoorStatus.CLOSED);
        door(s,"D17","B6","B7",387,395,428,395,395,395,DoorStatus.CLOSED);
        door(s,"D18","B7","B8",575,372,584,372,579,372,DoorStatus.CLOSED);
        door(s,"D19","B8","C1",665,336,665,324,665,330,DoorStatus.OPEN);
        s.layoutRevision=REVISION;
    }
    private static void room(Campaign s,String id,String name,double x,double y,double w,double h,double labelX,double labelY,double...vertices){
        var r=new Room(id,name,x,y,w,h);r.labelAnchor=new Point(labelX,labelY);
        for(int i=0;i<vertices.length;i+=2)r.outline.add(new Point(vertices[i],vertices[i+1]));s.rooms.put(id,r);
    }
    private static void door(Campaign s,String id,String a,String b,double x,double y,double xx,double yy,double gx,double gy,DoorStatus status){var d=new Door(id,a,b,new Point(x,y),new Point(xx,yy),status);d.gate=new Point(gx,gy);s.doors.put(id,d);}

    /** Changes geometry only, preserving identities, systems, narrative and decision transactions. */
    public static void migrate(Campaign state){
        if(state.layoutRevision>=REVISION)return;
        var oldRooms=new LinkedHashMap<>(state.rooms);var oldDoors=new LinkedHashMap<>(state.doors);
        var plan=new Campaign();install(plan);
        java.util.function.Function<Point,Point> transform=p->{
            for(var old:oldDoors.values()){var to=plan.doors.get(old.id);if(to==null)continue;if(p.distance(old.pa)<.01)return to.pa;if(p.distance(old.pb)<.01)return to.pb;if(p.distance(old.center())<.01)return to.center();}
            for(var old:oldRooms.values())if(old.contains(p.x(),p.y())){
                var to=plan.rooms.get(old.id);if(to==null)return p;
                var result=new Point(to.x+(p.x()-old.x)/old.w*to.w,to.y+(p.y()-old.y)/old.h*to.h);
                return to.contains(result.x(),result.y())?result:to.center();
            }
            for(var old:oldDoors.values())if(Math.abs(old.pa.distance(p)+old.pb.distance(p)-old.pa.distance(old.pb))<1){
                var to=plan.doors.get(old.id);double t=old.pa.distance(p)/Math.max(.001,old.pa.distance(old.pb));return to.pa.lerp(to.pb,t);
            }return p;
        };
        for(var a:state.actors){var p=transform.apply(a.position());a.x=p.x();a.y=p.y();a.path.replaceAll(transform::apply);}
        for(var c:state.creatures){var p=transform.apply(new Point(c.x,c.y));c.x=p.x();c.y=p.y();c.path.replaceAll(transform::apply);}
        for(var d:state.drones){var p=transform.apply(new Point(d.x,d.y));d.x=p.x();d.y=p.y();d.path.replaceAll(transform::apply);}
        for(var c:state.contacts.values()){var p=transform.apply(new Point(c.x,c.y));c.x=p.x();c.y=p.y();}
        plan.rooms.forEach((id,geometry)->{var r=state.rooms.get(id);if(r==null){state.rooms.put(id,geometry);return;}r.x=geometry.x;r.y=geometry.y;r.w=geometry.w;r.h=geometry.h;r.outline=geometry.outline;r.labelAnchor=geometry.labelAnchor;});
        plan.doors.forEach((id,geometry)->{var old=state.doors.get(id);if(old!=null)geometry.status=old.status;state.doors.put(id,geometry);});
        state.layoutRevision=REVISION;
    }
}
