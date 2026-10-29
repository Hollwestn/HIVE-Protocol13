package ru.hive13.model;

import java.util.*;
import static ru.hive13.model.Model.*;

/** The first surface expedition has its own topology and physical mission objects. */
public final class ExpeditionPlan {
    private static final Map<String,WorldObjects.Fixture> FIXTURES=new LinkedHashMap<>();
    static {
        fixture("field_armory","Контейнер сдерживания","V0",90,375,"weapon","ARMORY","REARM");
        fixture("extraction","Эвакуационная площадка","V0",175,390,"exit","FIELD_EVAC");
        fixture("field_grid","Подстанция Вектора","V1",130,225,"power","GRID_RESET");
        fixture("caravan","Раненые караванщики","V2",345,390,"medical","CARAVAN_RESCUE");
        fixture("field_store","Лебёдка складского контейнера","V3",365,195,"store","FIELD_SUPPLY");
        fixture("pharmacy","Аптечный пункт","V4",560,365,"medical","MEDICINE");
        fixture("sigma","Передатчик Сигма","V5",775,180,"radio","SIGMA_TUNE");
        fixture("scientist","Учёный станции 47","V6",730,395,"medical","SCIENTIST");
        fixture("station_archive","Сервер станции 47","V6",820,380,"terminal","STATION_ARCHIVE");
        fixture("field_hide","Укрытие под эстакадой","V2",290,385,"shield","HIDE");
    }
    private ExpeditionPlan(){}
    private static void fixture(String id,String name,String room,double x,double y,String icon,String... actions){FIXTURES.put(id,new WorldObjects.Fixture(id,name,room,x,y,icon,List.of(actions)));}
    public static Collection<WorldObjects.Fixture> objects(){return Collections.unmodifiableCollection(FIXTURES.values());}
    public static WorldObjects.Fixture object(String id){return FIXTURES.get(id);}
    public static void install(Campaign s){
        EchoRules.reset(s);
        s.rooms.clear();s.doors.clear();s.creatures.clear();s.contacts.clear();s.noises.clear();s.drones.clear();s.location="vector_sigma";s.currentAct=2;s.layoutRevision=102;
        room(s,"V0","ЛИФТ / ЛАГЕРЬ",65,330,140,90);room(s,"V1","ПОДСТАНЦИЯ",65,150,140,115);
        room(s,"V2","КАРАВАН",270,310,145,110);room(s,"V3","СКЛАД ВЕКТОРА",270,130,150,100);
        room(s,"V4","АПТЕЧНЫЙ ПУНКТ",480,285,150,115);room(s,"V5","ВЫШКА СИГМА",680,90,170,140);room(s,"V6","СТАНЦИЯ 47",680,315,170,110);
        door(s,"E01","V0","V1",130,330,130,265);door(s,"E02","V0","V2",205,355,270,355);
        door(s,"E03","V1","V3",205,185,270,185);door(s,"E04","V2","V3",330,310,330,230);
        door(s,"E05","V2","V4",415,350,480,350);door(s,"E06","V3","V5",420,170,680,170);
        door(s,"E07","V4","V6",630,350,680,350);door(s,"E08","V5","V6",775,230,775,315);
        s.doors.get("E08").status=DoorStatus.CLOSED;s.rooms.get("V4").biomass=32;s.rooms.get("V6").biomass=18;
        spawn(s,"SF-HOUND","ГОНЧАЯ","V4",525,315);spawn(s,"SF-WHISPER","ШЕПТУН","V6",815,335);
    }
    private static void room(Campaign s,String id,String name,double x,double y,double w,double h){var r=new Room(id,name,x,y,w,h);r.powered=false;s.rooms.put(id,r);}
    private static void door(Campaign s,String id,String a,String b,double x,double y,double xx,double yy){s.doors.put(id,new Door(id,a,b,new Point(x,y),new Point(xx,yy),DoorStatus.OPEN));}
    private static void spawn(Campaign s,String id,String type,String room,double x,double y){var c=new Creature();c.id=id;c.type=type;c.roomId=c.home=room;c.x=x;c.y=y;s.creatures.add(c);}
    public static WorldObjects.Offer offer(Campaign s,Actor a,WorldObjects.Fixture f,String action){
        var cost=switch(action){case "GRID_RESET"->Map.of("parts",2);case "CARAVAN_RESCUE"->Map.of("meds",2);case "SIGMA_TUNE"->Map.of("batteries",2);case "SCIENTIST"->Map.of("meds",2);default->Map.<String,Integer>of();};
        String title=switch(action){case "GRID_RESET"->"Запитать линию Сигмы";case "CARAVAN_RESCUE"->"Перевязать раненых караванщиков";case "FIELD_SUPPLY"->"Вскрыть складской контейнер";case "MEDICINE"->"Найти лекарства и журнал поставок";case "SIGMA_TUNE"->"Настроить передатчик";case "SCIENTIST"->"Стабилизировать и эвакуировать учёного";case "STATION_ARCHIVE"->"Скопировать архив станции";default->"Вернуться к подъёмнику";};
        String description=switch(action){case "GRID_RESET"->"Включает настоящую линию питания V1 → V5. Аварийный шум.";case "CARAVAN_RESCUE"->"Спасённые люди передают воду, припасы и свидетельство о живом городе.";case "FIELD_SUPPLY"->"+4 детали, +6 патронов, +2 топлива, +3 батареи. Лебёдка громко работает.";case "MEDICINE"->"2 личные аптечки, +4 медикамента и +2 фильтра; независимые даты поставок.";case "SIGMA_TUNE"->"Нужна подстанция. Станция 47 выйдет на связь; учёному останется 180 секунд до критического состояния.";case "SCIENTIST"->"Нужны общее решение о спасении и ещё живой учёный. Его знания сохранятся для следующих актов.";case "STATION_ARCHIVE"->"Нужно общее решение об архиве. Независимые записи позволяют сопоставить ложь Куратора.";default->"После возвращения всех живых участников — сводка экспедиции. Не завершает всю историю.";};
        String flag=switch(action){case "GRID_RESET"->"surface_grid";case "CARAVAN_RESCUE"->"caravan_saved";case "FIELD_SUPPLY"->"surface_store";case "MEDICINE"->"surface_medicine";case "SIGMA_TUNE"->"sigma_online";case "SCIENTIST"->"scientist_saved";case "STATION_ARCHIVE"->"station_archive_found";default->"";};
        String reason=!f.actions().contains(action)?"Нет такой операции":s.phase!=Phase.ACTION?"Нужна фаза действий":!a.alive()?"Нужно живое тело":!a.knownRooms.contains(f.room())?"Сектор ещё не разведан":!flag.isEmpty()&&s.flags.contains(flag)?"Операция уже завершена":"";
        if(reason.isEmpty()&&action.equals("MEDICINE")&&a.carriedWeight()+1.4>18)reason="Нужно 1,4 кг места для двух аптечек";
        if(reason.isEmpty()&&action.equals("SIGMA_TUNE")&&!s.flags.contains("surface_grid"))reason="Сначала запитайте подстанцию V1";
        if(reason.isEmpty()&&action.equals("SCIENTIST")&&(!s.flags.contains("rescue_authorized")||!s.npcs.containsKey("scientist")||!s.npcs.get("scientist").condition.equals("CRITICAL")))reason="Нужны решение о спасении и живой учёный";
        if(reason.isEmpty()&&action.equals("STATION_ARCHIVE")&&!s.flags.contains("archive_authorized"))reason="Нужно решение о сохранении архива";
        if(reason.isEmpty()&&action.equals("FIELD_EVAC")&&(!s.flags.contains("sigma_online")||!s.flags.contains("station_archive_found")&&!s.flags.contains("scientist_saved")))reason="Нужна связь Сигмы и хотя бы одна завершённая операция станции 47";
        if(reason.isEmpty())for(var c:cost.entrySet())if(s.supplies.getOrDefault(c.getKey(),0)<c.getValue())reason="Недостаточно: "+WorldObjects.resourceName(c.getKey());
        return new WorldObjects.Offer(action,title,description,action.equals("FIELD_EVAC")?4:action.equals("GRID_RESET")&&a.slot==0?10:action.equals("MEDICINE")?8:12,cost,reason);
    }
}
