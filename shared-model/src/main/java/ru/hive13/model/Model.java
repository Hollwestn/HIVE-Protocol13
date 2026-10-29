package ru.hive13.model;

import java.util.*;

/** Serializable authority data. UI coordinates never enter simulation rules. */
public final class Model {
    private Model() {}
    public enum Phase { LOBBY, ACTION, VOTING, RESOLVE, PAUSED, GAME_OVER, CUTSCENE }
    public enum DoorStatus { OPEN, CLOSED, LOCKED, SEALED, JAMMED, BROKEN }
    public record Point(double x, double y) {
        public double distance(Point p) { return Math.hypot(x-p.x,y-p.y); }
        public Point lerp(Point p,double t) { return new Point(x+(p.x-x)*t,y+(p.y-y)*t); }
    }
    public static class Room {
        public String id,name; public int floor; public double x,y,w,h,biomass,oxygen=95,temperature=19,humidity=60;
        public boolean powered=true,ventilated=true; public double nutrients=100; public String colony="RED";
        public List<Point> outline=new ArrayList<>();
        public Point labelAnchor;
        public Room() {}
        public Room(String id,String name,double x,double y,double w,double h) {this.id=id;this.name=name;this.x=x;this.y=y;this.w=w;this.h=h;}
        public Point center(){return new Point(x+w/2,y+h/2);}
        public boolean contains(double px,double py){
            if(!Double.isFinite(px)||!Double.isFinite(py))return false;
            if(px<x-.001||py<y-.001||px>x+w+.001||py>y+h+.001)return false;
            if(outline.isEmpty())return true;
            boolean inside=false;
            for(int i=0,j=outline.size()-1;i<outline.size();j=i++){
                var a=outline.get(i);var b=outline.get(j);double cross=(px-a.x())*(b.y()-a.y())-(py-a.y())*(b.x()-a.x());
                if(Math.abs(cross)<.01&&px>=Math.min(a.x(),b.x())-.001&&px<=Math.max(a.x(),b.x())+.001&&py>=Math.min(a.y(),b.y())-.001&&py<=Math.max(a.y(),b.y())+.001)return true;
                if((a.y()>py)!=(b.y()>py)&&px<(b.x()-a.x())*(py-a.y())/(b.y()-a.y())+a.x())inside=!inside;
            }return inside;
        }
    }
    public static class Door {
        public String id,a,b; public Point pa,pb,gate; public DoorStatus status=DoorStatus.OPEN;public double bracedUntil;
        public Door() {}
        public Door(String id,String a,String b,Point pa,Point pb,DoorStatus status){this.id=id;this.a=a;this.b=b;this.pa=pa;this.pb=pb;this.status=status;}
        public boolean passable(){return status==DoorStatus.OPEN||status==DoorStatus.BROKEN;}
        public String other(String r){return a.equals(r)?b:a;}
        public Point center(){return gate==null?pa.lerp(pb,.5):gate;}
    }
    public static class BodyPart {
        public String id,name;public double infection,injury,bleeding;public String wound="нет";
        public List<String> treatments=new ArrayList<>();
        public BodyPart(){} public BodyPart(String id,String name){this.id=id;this.name=name;}
    }
    public static class Item {
        public String id,name;public int count;public double weight,condition=100,charge=100;public boolean contaminated;
        public Item(){} public Item(String id,String name,int count,double weight){this.id=id;this.name=name;this.count=count;this.weight=weight;}
    }
    public static class Actor {
        public String id,name,profession,roomId="A2",movement="WALK",life="ALIVE",task="",taskTarget="";
        public int slot;public boolean connected,ready,consent,flashlight=true,hidden; public double x,y,hp=100,stress=12,stamina=100,fatigue,taskProgress,taskTotal,weaponReadyAt;
        public List<Point> path=new ArrayList<>();public List<String> pathDoors=new ArrayList<>();
        public Map<String,BodyPart> body=new LinkedHashMap<>(); public Map<String,Item> inventory=new LinkedHashMap<>();
        public Set<String> knownRooms=new LinkedHashSet<>(),privateEvidence=new LinkedHashSet<>();
        public Set<String> permissions=new LinkedHashSet<>();
        public String echoRoom="";public double echoViewUntil,echoReadyAt;
        public List<PlannedAction> queue=new ArrayList<>();
        public Actor(){}
        public double infection(){return body.values().stream().mapToDouble(b->b.infection).average().orElse(0);}
        public double carriedWeight(){return inventory.values().stream().mapToDouble(i->i.weight*i.count).sum();}
        public boolean alive(){return life.equals("ALIVE")||life.equals("CRITICAL");}
        public Point position(){return new Point(x,y);}
    }
    public static class PlannedAction {public String type,target="",action="",mode="WALK";public double x,y;public PlannedAction(){}}
    public static class Drone {public String id="DR-01",roomId="C1",state="DOCKED",owner="",destination="";public double x=680,y=270,battery=100;public List<Point> path=new ArrayList<>();public List<String> pathDoors=new ArrayList<>();public Drone(){}}
    public static class Creature {
        public String id,type,roomId,state="PATROL",targetRoom="",home="B4",breachDoor="";public double x,y,attackCooldown,searchTime,suppressedUntil,immuneUntil,resistance,breachProgress;
        public double exertion,recoveryUntil;
        public List<Point> path=new ArrayList<>();public List<String> pathDoors=new ArrayList<>();
        public Creature(){}
        public Point positionForDefense(){return new Point(x,y);}
    }
    public static class Noise {public String roomId,source;public double strength,expires;public Noise(){} public Noise(String r,String s,double n,double e){roomId=r;source=s;strength=n;expires=e;}}
    public static class Contact {public String id,type,roomId,certainty,behavior="";public double x,y,observedAt,suppressedUntil,immuneUntil;public Contact(){}}
    public static class TacticalMark {public int owner;public String roomId="";public double expiresAt;public TacticalMark(){}public TacticalMark(int owner,String room,double until){this.owner=owner;roomId=room;expiresAt=until;}}
    public static class Event {
        public long id,tick;public int cycle;public String category,text,roomId;public double time;
        public Event(){}
    }
    public static class Option {
        public String id,title,description,risk;public Map<String,Integer> cost=new LinkedHashMap<>();
        public boolean enabled=true;public String reason="";public List<Integer> consentSlots=new ArrayList<>();
        public Option(){} public Option(String id,String title,String description,String risk){this.id=id;this.title=title;this.description=description;this.risk=risk;}
    }
    public static class Decision {
        public String resolutionId,optionId,title="",consequence="";public int cycle;public Map<Integer,String> ballots=new LinkedHashMap<>();
        public Decision(){}
    }
    public static class Receipt {public boolean accepted;public String reason;public Receipt(){}public Receipt(boolean accepted,String reason){this.accepted=accepted;this.reason=reason;}}
    public static class Npc { public String id,name,location,condition="UNKNOWN",faction;public int trust; public Npc(){} }
    public static class LocationArchive {public Map<String,Room> rooms=new LinkedHashMap<>();public Map<String,Door> doors=new LinkedHashMap<>();public List<Creature> creatures=new ArrayList<>();public double lastActiveTime;public LocationArchive(){}}
    public static class Campaign {
        public int schemaVersion=1,layoutRevision=1,cycle=1,currentAct=1,requiredPlayers=3,hostSlot=0;public String campaignId=UUID.randomUUID().toString(),scenario="STORY",objective="Восстановить питание и найти архив",ending="";
        public List<String> epilogue=new ArrayList<>();public double qaSpeed=1;
        public String location="object13";public Set<Integer> chapterReady=new LinkedHashSet<>();
        public boolean campaignComplete;public String endingId="",finalRoute="";
        public Map<String,LocationArchive> visitedLocations=new LinkedHashMap<>();
        public long seed=13,rngState=13,tick,eventSequence;public double gameTime,remaining=60,voteRemaining=30,power=43,ventilation=61,water=80;
        public Phase phase=Phase.LOBBY,resumePhase=Phase.ACTION;public Map<String,Room> rooms=new LinkedHashMap<>();public Map<String,Door> doors=new LinkedHashMap<>();
        public List<Actor> actors=new ArrayList<>();public List<Creature> creatures=new ArrayList<>();public List<Noise> noises=new ArrayList<>();
        public Map<String,Contact> contacts=new LinkedHashMap<>();public List<Event> events=new ArrayList<>();public Map<String,Integer> supplies=new LinkedHashMap<>();
        public Map<Integer,TacticalMark> tacticalMarks=new LinkedHashMap<>();
        public List<Option> options=new ArrayList<>();public Map<Integer,String> ballots=new LinkedHashMap<>();public List<Decision> decisions=new ArrayList<>();
        public Set<String> flags=new LinkedHashSet<>(),sharedEvidence=new LinkedHashSet<>(),discoveredRegions=new LinkedHashSet<>();
        public Map<String,Npc> npcs=new LinkedHashMap<>();public Map<String,Integer> factionTrust=new LinkedHashMap<>();
        public Map<String,Double> progress=new LinkedHashMap<>();public Map<String,String> locationStates=new LinkedHashMap<>();
        public Set<String> triggeredEvents=new LinkedHashSet<>();
        public Map<String,Receipt> commandReceipts=new LinkedHashMap<>();
        public List<Drone> drones=new ArrayList<>();
        public Campaign(){}
        public double biomass(){return rooms.values().stream().mapToDouble(r->r.biomass).average().orElse(0);}
        public double oxygen(){return rooms.values().stream().mapToDouble(r->r.oxygen).average().orElse(0);}
        public Actor actor(int slot){return actors.get(slot);}
        public long nextRandom(){rngState=rngState*6364136223846793005L+1442695040888963407L;return rngState>>>1;}
    }
}
