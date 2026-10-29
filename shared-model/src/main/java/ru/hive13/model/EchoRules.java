package ru.hive13.model;

import static ru.hive13.model.Model.*;
import java.util.*;

/** Shared terminal availability. Coverage is anchored to the connected field team. */
public final class EchoRules {
    private EchoRules(){}
    public record Offer(String action,String title,String description,int power,boolean enabled,String reason){}
    public static String signalReason(Campaign s,Actor a){
        if(!a.life.equals("ECHO"))return "Терминал доступен после потери тела";
        if(s.scenario.equals("HARDCORE"))return "В Hardcore терминальный режим недоступен";
        if(!a.connected)return "Сессия Эхо отключена";
        if(s.power<25)return "Связь потеряна: питание ниже 25%";
        boolean relay=s.currentAct==1?s.flags.contains("relay_restored"):s.currentAct==2?s.flags.contains("surface_grid"):s.flags.contains("relay_restored")||s.flags.contains("surface_grid");
        if(!relay)return s.currentAct==2?"Нужна работающая подстанция V1":"Нужно восстановить реле связи";
        if(s.actors.stream().noneMatch(b->b.connected&&b.alive()))return "Нет подключённого живого полевого героя";
        return "";
    }
    public static Set<String> coveredRooms(Campaign s,Actor a){
        var result=new LinkedHashSet<String>();if(!signalReason(s,a).isEmpty())return result;
        for(var body:s.actors)if(body.connected&&body.alive()){
            var distance=new LinkedHashMap<String,Integer>();var queue=new ArrayDeque<String>();queue.add(body.roomId);distance.put(body.roomId,0);
            while(!queue.isEmpty()){
                String id=queue.remove();int depth=distance.get(id);var room=s.rooms.get(id);if(room==null)continue;
                if(room.powered&&body.knownRooms.contains(id))result.add(id);
                if(depth==2)continue;
                for(var door:s.doors.values())if(door.status!=DoorStatus.SEALED&&(door.a.equals(id)||door.b.equals(id))){String next=door.other(id);if(!distance.containsKey(next)){distance.put(next,depth+1);queue.add(next);}}
            }
        }
        return result;
    }
    public static String cameraRoom(Campaign s,Actor a){return a.echoViewUntil>s.gameTime&&coveredRooms(s,a).contains(a.echoRoom)?a.echoRoom:"";}
    public static Offer offer(Campaign s,Actor a,String action,String target){
        String title,description;int cost;
        switch(action){
            case "WATCH"->{title="Подключить камеру / 15 с";description="Показывает Эхо одно помещение в зоне связи.";cost=3;}
            case "MARK"->{title="Отметить для группы / 20 с";description="Общая отметка маршрута; заменяет вашу прежнюю отметку.";cost=0;}
            case "DECOY"->{title="Включить громкоговоритель / 12 с";description="Настоящий шум отвлекает существ; путь отхода остаётся нужен.";cost=4;}
            case "OPEN","CLOSE"->{title=action.equals("OPEN")?"Удалённо открыть шлюз":"Удалённо закрыть шлюз";description="Обычный шлюз у действующей камеры; ключи и изоляция не обходятся.";cost=2;}
            default->{return new Offer(action,"Неизвестная команда","",0,false,"Нет такой команды Эхо");}
        }
        String why=signalReason(s,a);
        if(why.isEmpty()&&s.phase!=Phase.ACTION)why="Терминал работает только в фазе действий";
        if(why.isEmpty()&&!a.task.isEmpty())why="Сначала завершите текущую терминальную процедуру";
        if(why.isEmpty()&&a.echoReadyAt>s.gameTime)why="Терминал восстанавливается: "+(int)Math.ceil(a.echoReadyAt-s.gameTime)+" с";
        if(why.isEmpty()&&s.power<25+cost)why="Нужно сохранить 25% питания связи; цена команды "+cost;
        if(action.equals("OPEN")||action.equals("CLOSE")){
            var d=s.doors.get(target);String camera=cameraRoom(s,a);
            if(why.isEmpty()&&d==null)why="Шлюз не найден";
            if(why.isEmpty()&&(camera.isEmpty()||!d.a.equals(camera)&&!d.b.equals(camera)))why="Сначала подключите камеру у этого шлюза";
            if(why.isEmpty()&&(d.status!=DoorStatus.OPEN&&d.status!=DoorStatus.CLOSED))why="Недоступно: изоляция, замок или повреждение шлюза";
            if(why.isEmpty()&&d.passable()==action.equals("OPEN"))why="Шлюз уже в выбранном состоянии";
            if(why.isEmpty()&&action.equals("CLOSE")&&s.actors.stream().anyMatch(b->b.alive()&&!b.path.isEmpty()&&b.pathDoors.contains(d.id)))why="Маршрут живого героя проходит через этот шлюз";
        }else{
            if(why.isEmpty()&&!coveredRooms(s,a).contains(target))why="Помещение вне зоны связи или обесточено";
            if(why.isEmpty()&&action.equals("DECOY")&&!cameraRoom(s,a).equals(target))why="Сначала подключите камеру этого помещения";
        }
        return new Offer(action,title,description,cost,why.isEmpty(),why);
    }
    public static void reset(Campaign s){s.tacticalMarks.clear();for(var a:s.actors){a.echoRoom="";a.echoViewUntil=0;a.echoReadyAt=0;}}
}
