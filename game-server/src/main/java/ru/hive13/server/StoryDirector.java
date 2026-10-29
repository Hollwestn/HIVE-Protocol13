package ru.hive13.server;
import static ru.hive13.model.Model.*;
public final class StoryDirector {
    private StoryDirector(){}
    public static void update(GameEngine e){var s=e.state;
        once(e,"first_voice",s.gameTime>3,"СИСТЕМА","КУРАТОР: Оставайтесь в медицинском блоке. Калибровка памяти не завершена.","A2");
        once(e,"knock",s.gameTime>35,"ГРУППА","НЕПОДТВЕРЖДЁННЫЙ / C2: Кто-нибудь слышит? Меня зовут Лина. Я вижу ваш свет.","C2");
        once(e,"archive_hint",s.flags.contains("archive_access"),"СИСТЕМА","PR-13/LOCK-04: блокировка автозапуска установлена по просьбе субъектов. Последнее изменение: A-17.","B6");
        once(e,"air_cost",s.flags.contains("b4_sealed")&&s.cycle>=3&&s.ventilation<60,"УГРОЗА","После изоляции B4 снизилась пропускная способность воздуха. Фильтр B3 требует замены.","B3");
        once(e,"lina_delayed",s.npcs.get("lina").condition.equals("ALLY")&&!s.flags.contains("lina_safe")&&!s.npcs.get("lina").condition.equals("DEAD")&&s.cycle>=4,"УГРОЗА","На одежде Лины сохранились споры. Медблок требует обработки; колония получила питательную среду.","A2");
        once(e,"lina_quarantine_followup",s.flags.contains("lina_safe")&&!s.npcs.get("lina").condition.equals("DEAD")&&s.cycle>=4,"ГРУППА","ЛИНА: У меня пропуск станции 47. Возьмите его. И проверьте воду до того, как решите сжигать колонии.","B5");
        once(e,"exit_instruction",s.flags.contains("archive_access")&&s.doors.get("D13").passable(),"СИСТЕМА","D13 открыт. ПКМ по подъёмнику C2 → подготовить подъёмник → подняться на поверхность каждым героем.","C2");
    }
    private static void once(GameEngine e,String id,boolean condition,String category,String text,String room){if(condition&&e.state.triggeredEvents.add(id))e.event(category,text,room);}
}
