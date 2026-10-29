package ru.hive13.model;

import java.util.*;
import static ru.hive13.model.Model.*;

/** No creature hit points: weapons buy seconds, never a kill. */
public final class DefenseRules {
    public record Weapon(String id,String name,String ammunition,int range,double seconds,double noise){}
    public static final List<Weapon> WEAPONS=List.of(
        new Weapon("shotgun","Дробовик","shell",140,6,180),
        new Weapon("stunner","Электрошок","shock_cell",55,5,45),
        new Weapon("flaregun","Сигнальная ракета","flare",120,12,100));
    public static final Set<String> OBJECT_ACTIONS=Set.of("ARMORY","REARM","HIDE","UV","DECOY","BARRICADE");
    private DefenseRules(){}
    public static Weapon weapon(String id){return WEAPONS.stream().filter(w->w.id.equals(id)).findFirst().orElse(null);}
    public static int count(Actor a,String id){return a.inventory.getOrDefault(id,new Item()).count;}
    public static String reason(Campaign s,Actor a,Contact c,Weapon w){
        if(s.phase!=Phase.ACTION)return "Нужна фаза действий";
        if(!a.alive())return "Нужно живое тело";
        if(c==null||!"CONFIRMED".equals(c.certainty))return "Нужен подтверждённый контакт";
        if(count(a,w.id)<1)return "Получите оружие у шкафа сдерживания";
        if(count(a,w.ammunition)<1)return "Нет зарядов";
        if(a.weaponReadyAt>s.gameTime)return "Оружие готовится: "+(int)Math.ceil(a.weaponReadyAt-s.gameTime)+" с";
        if(c.suppressedUntil>s.gameTime)return "Уже подавлен — используйте время для отхода";
        if(c.immuneUntil>s.gameTime)return "Временно сопротивляется повторному сдерживанию";
        var target=new Point(c.x,c.y);
        if(a.position().distance(target)>w.range)return "Далеко: предел "+w.range;
        if(!lineOfFire(s,a.position(),target))return "Линию огня перекрывает стена или шлюз";
        return "";
    }
    public static boolean lineOfFire(Campaign s,Point a,Point b){
        for(var door:s.doors.values())if(!door.passable()&&distanceToSegment(door.center(),a,b)<7&&a.distance(door.center())<a.distance(b)-1&&b.distance(door.center())>1)return false;
        int steps=Math.max(1,(int)Math.ceil(a.distance(b)/2));
        for(int i=0;i<=steps;i++){var p=a.lerp(b,i/(double)steps);if(s.rooms.values().stream().anyMatch(r->r.contains(p.x(),p.y())))continue;
            if(s.doors.values().stream().noneMatch(d->d.passable()&&distanceToSegment(p,d.pa,d.pb)<5))return false;}
        return true;
    }
    private static double distanceToSegment(Point p,Point a,Point b){double dx=b.x()-a.x(),dy=b.y()-a.y(),length=dx*dx+dy*dy;double t=length==0?0:Math.clamp(((p.x()-a.x())*dx+(p.y()-a.y())*dy)/length,0,1);return p.distance(a.lerp(b,t));}
    public static WorldObjects.Offer objectOffer(Campaign s,Actor a,WorldObjects.Fixture f,String action){
        var cost=switch(action){case "ARMORY"->Map.of("ammo",4,"batteries",1,"fuel",1);case "REARM"->Map.of("ammo",2,"batteries",1,"fuel",1);case "UV","DECOY"->Map.of("batteries",1);case "BARRICADE"->Map.of("parts",2);default->Map.<String,Integer>of();};
        String title=switch(action){case "ARMORY"->"Получить комплект сдерживания";case "REARM"->"Пополнить личные заряды";case "HIDE"->"Укрыться и погасить фонарь";case "UV"->"Включить УФ на 20 секунд";case "DECOY"->"Отвлечь громкоговорителем";default->"Заблокировать D10 на 25 секунд";};
        String description=switch(action){case "ARMORY"->"Дробовик: 4 патрона; электрошок: 3 заряда; ракетница: 2 ракеты. Они не убивают существ.";case "REARM"->"2 патрона, 2 электрических заряда и 1 ракета за общие ресурсы.";case "HIDE"->"Шум может выдать сектор; на близкой дистанции существо найдёт вас. При движении укрытие снимается.";case "UV"->"Временно подавляет контакт в медблоке. После восстановления тварь сопротивляется повторному воздействию.";case "DECOY"->"20 секунд громкого сигнала отвлекают тварей к этому сектору.";default->"Временная баррикада шлюза. Её можно разобрать; обычную дверь тварь способна проломить.";};
        String reason=!f.actions().contains(action)?"Нет такой операции":s.phase!=Phase.ACTION?"Нужна фаза действий":!a.alive()?"Нужно живое тело":!a.knownRooms.contains(f.room())?"Сектор не разведан":"";
        if(reason.isEmpty()&&action.equals("ARMORY")&&a.privateEvidence.contains("defense_issued"))reason="Личный комплект уже получен";
        if(reason.isEmpty()&&action.equals("ARMORY")&&a.carriedWeight()+4.8>18)reason="Недостаточно места: нужно 4,8 кг";
        if(reason.isEmpty()&&action.equals("REARM")&&(!a.privateEvidence.contains("defense_issued")||a.carriedWeight()+.8>18||count(a,"shell")>=8))reason="Нужны оружие, 0,8 кг места и меньше 8 патронов";
        if(reason.isEmpty()&&action.equals("HIDE")&&a.hidden)reason="Вы уже в укрытии";
        if(reason.isEmpty()&&action.equals("UV")&&(!s.rooms.get(f.room()).powered||s.progress.getOrDefault("uv:"+f.room(),0d)>s.gameTime))reason="Нет питания или УФ уже работает";
        if(reason.isEmpty()&&action.equals("BARRICADE")&&(!s.doors.containsKey("D10")||s.doors.get("D10").status==DoorStatus.SEALED||s.doors.get("D10").bracedUntil>s.gameTime))reason="Проход уже изолирован или заблокирован";
        if(reason.isEmpty())for(var c:cost.entrySet())if(s.supplies.getOrDefault(c.getKey(),0)<c.getValue())reason="Недостаточно: "+WorldObjects.resourceName(c.getKey());
        return new WorldObjects.Offer(action,title,description,action.equals("ARMORY")?8:action.equals("BARRICADE")?5:2,cost,reason);
    }
}
