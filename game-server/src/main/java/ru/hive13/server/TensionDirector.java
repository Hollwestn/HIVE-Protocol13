package ru.hive13.server;

import java.util.*;
import static ru.hive13.model.Model.*;

/** Seeded pacing changes real infrastructure; it never spawns a visible enemy as a scare. */
public final class TensionDirector {
    private TensionDirector(){}
    public static void update(GameEngine e){var s=e.state;if(!s.scenario.equals("STORY"))return;
        for(var r:s.rooms.values()){String key="blackout:"+r.id;double until=s.progress.getOrDefault(key,0d);if(until>0&&until<=s.gameTime){s.progress.remove(key);r.powered=true;e.event("СИСТЕМА","Датчик "+r.id+": питание восстановлено.",r.id);}}
        double next=s.progress.getOrDefault("director_next",45d);
        if(s.gameTime<next)return;
        if(s.gameTime-s.progress.getOrDefault("last_fight",-100d)<22||s.actors.stream().filter(Actor::alive).anyMatch(a->a.hp<30)){s.progress.put("director_next",s.gameTime+35);return;}
        s.progress.put("director_next",s.gameTime+45+s.nextRandom()%25);
        var live=s.actors.stream().filter(Actor::alive).toList();if(live.isEmpty())return;var a=live.get((int)(s.nextRandom()%live.size()));
        int kind=(int)(s.nextRandom()%4);
        if(kind==0){var r=s.rooms.get(a.roomId);if(r.powered){r.powered=false;s.progress.put("blackout:"+r.id,s.gameTime+18);e.event("УГРОЗА","Сбой датчика "+r.id+". Контакты за пределами прямого наблюдения больше не подтверждаются. Резерв восстановится через 18 с.",r.id);}}
        else if(kind==1&&s.ventilation>45){s.ventilation=Math.max(35,s.ventilation-6);e.event("УГРОЗА","Фильтр забит спорами: вентиляция −6%. Это реальное повреждение; замените фильтр при первой возможности.",a.roomId);}
        else if(kind==2){var c=s.creatures.stream().filter(x->s.doors.values().stream().anyMatch(d->(d.a.equals(a.roomId)&&d.b.equals(x.roomId))||(d.b.equals(a.roomId)&&d.a.equals(x.roomId)))).findFirst().orElse(null);
            if(c!=null){e.event("УГРОЗА","Микрофон "+a.roomId+": скрежет с соседнего прохода. Источник не подтверждён.",a.roomId);a.stress=Math.min(100,a.stress+4);}}
        else if(!s.flags.contains("curator_offline")){a.stress=Math.min(100,a.stress+2);e.event("СИСТЕМА","КУРАТОР / потеря синхронизации: «Не отвечайте голосу, который повторяет ваши слова с задержкой». Канал закрыт вручную изнутри сети.",a.roomId);}
    }
}
