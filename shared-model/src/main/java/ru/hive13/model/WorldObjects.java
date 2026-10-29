package ru.hive13.model;

import java.util.*;
import static ru.hive13.model.Model.*;

/** Physical fixtures shared by server interaction rules and the 2D blueprint. */
public final class WorldObjects {
    public record Fixture(String id,String name,String room,double x,double y,String icon,List<String> actions){public Point position(){return new Point(x,y);}}
    public record Offer(String action,String title,String description,double seconds,Map<String,Integer> cost,String reason){public boolean enabled(){return reason.isEmpty();}}
    private static final Map<String,Fixture> OBJECTS=new LinkedHashMap<>();
    static {
        add("capsules","Капсулы восстановления","A2",280,294,"bed","CAPSULES");
        add("medcab","Медицинский шкаф","A2",242,303,"medical","MEDKIT","DISINFECT");
        add("switchboard","Основной щит","B2",239,148,"power","REPAIR");
        add("generator","Резервный генератор","B1",95,145,"power","GENERATOR");
        add("relay","Серверный ретранслятор","B1",145,156,"radio","RELAY");
        add("filter","Фильтровальная установка","B3",407,164,"vent","FILTER");
        add("archive","Архивный терминал","B6",320,415,"terminal","ARCHIVE");
        add("sample","Лабораторный анализатор","B7",490,405,"lab","COLLECT","ANALYSE");
        add("store","Шкаф снабжения","B8",615,402,"store","LOOT");
        add("decon","Установка дезактивации","C1",667,297,"filter","DISINFECT");
        add("exit","Эвакуационный подъёмник","C2",818,309,"exit","PRESSURIZE","ASCEND");
        add("bunks","Койки для отдыха","A1",116,300,"bed","REST");
        add("rations","Раздаточная","A3",135,387,"store","RATIONS");
        add("isolation","Карантинная камера","B5",730,175,"medical","DISINFECT","RESCUE");
        add("armory","Шкаф сдерживания","A1",163,240,"weapon","ARMORY","REARM");
        add("hide","Укрытие за шкафами","A1",107,287,"shield","HIDE");
        add("uv","Щит УФ-облучения","A2",301,242,"power","UV");
        add("speaker","Аварийный громкоговоритель","B3",348,176,"radio","DECOY");
        add("brace","Затвор аварийной баррикады","H",395,282,"shield","BARRICADE");
    }
    private WorldObjects(){}
    private static void add(String id,String name,String room,double x,double y,String icon,String...actions){OBJECTS.put(id,new Fixture(id,name,room,x,y,icon,List.of(actions)));}
    public static Collection<Fixture> all(){return Collections.unmodifiableCollection(OBJECTS.values());}
    public static Fixture get(String id){return OBJECTS.get(id);}
    public static Collection<Fixture> all(Campaign s){return s.currentAct>=3?CampaignPlan.objects(s.currentAct):s.currentAct==2?ExpeditionPlan.objects():all();}
    public static Fixture get(Campaign s,String id){return s.currentAct>=3?CampaignPlan.object(s.currentAct,id):s.currentAct==2?ExpeditionPlan.object(id):get(id);}
    public static String title(String action){var op=CampaignPlan.operation(action);if(op!=null)return op.title();return switch(action){case "CAPSULES"->"Осмотреть капсулы";case "MEDKIT"->"Взять аптечку";case "REPAIR"->"Восстановить основной щит";case "GENERATOR"->"Запустить резерв";case "RELAY"->"Восстановить ретранслятор";case "FILTER"->"Заменить фильтр";case "ARCHIVE"->"Прочитать архив и забрать ключ";case "COLLECT"->"Отобрать биологический образец";case "ANALYSE"->"Провести анализ образца";case "LOOT"->"Забрать снабжение";case "DISINFECT"->"Обеззаразить сектор";case "PRESSURIZE"->"Подготовить подъёмник";case "ASCEND"->"Подняться на поверхность";case "REST"->"Отдохнуть";case "RATIONS"->"Взять паёк и воду";case "RESCUE"->"Завершить карантин Лины";case "ARMORY"->"Получение оружия";case "REARM"->"Пополнение зарядов";case "HIDE"->"Укрытие";case "UV"->"УФ-щит";case "DECOY"->"Громкоговоритель";case "BARRICADE"->"Баррикада";case "GRID_RESET"->"Подстанция Сигмы";case "CARAVAN_RESCUE"->"Помощь каравану";case "FIELD_SUPPLY"->"Складской контейнер";case "MEDICINE"->"Поиск лекарств";case "SIGMA_TUNE"->"Настройка передатчика";case "SCIENTIST"->"Спасение Резника";case "STATION_ARCHIVE"->"Копирование архива";case "FIELD_EVAC"->"Возвращение к лифту";default->action;};}
    public static String resourceName(String key){return switch(key){case "parts"->"детали";case "filters"->"фильтры";case "food"->"пайки";case "meds"->"медикаменты";case "batteries"->"батареи";case "fuel"->"топливо";case "ammo"->"патроны";default->key;};}
    public static List<Offer> offers(Campaign s,Actor a,Fixture f){return f.actions.stream().map(action->offer(s,a,f,action)).toList();}
    public static Offer offer(Campaign s,Actor a,Fixture f,String action){
        if(DefenseRules.OBJECT_ACTIONS.contains(action))return DefenseRules.objectOffer(s,a,f,action);
        if(s.currentAct>=3)return CampaignPlan.offer(s,a,f,action);
        if(s.currentAct==2)return ExpeditionPlan.offer(s,a,f,action);
        Map<String,Integer> cost=switch(action){case "REPAIR"->Map.of("parts",2);case "GENERATOR"->Map.of("fuel",1);case "RELAY"->Map.of("parts",1);case "FILTER","DISINFECT","PRESSURIZE","RESCUE"->Map.of("filters",1);case "MEDKIT"->Map.of("meds",1);case "RATIONS"->Map.of("food",1);default->Map.of();};
        double duration=switch(action){case "REPAIR"->a.slot==0?12:24;case "ANALYSE"->a.slot==1?10:20;case "ARCHIVE","RELAY","PRESSURIZE","REST"->8;case "ASCEND"->4;default->6;};
        String description=switch(action){case "CAPSULES"->"Личные записи о восстановлении сознания.";case "MEDKIT"->"Один медикамент из общего запаса превращается в личную аптечку.";case "REPAIR"->"Питание 85%, лаборатория включена; ремонт создаёт шум.";case "GENERATOR"->"Питание +25%, высокий шум. Резерв запускается один раз.";case "RELAY"->"Питание +5%, восстановленная радиолиния и координаты Сигмы.";case "FILTER"->"Вентиляция +20%, кислород в связанных секторах +12%.";case "ARCHIVE"->"Ключ открывает D13; приказ хранится в личном архиве.";case "COLLECT"->"Настоящий образец помещается в инвентарь; запас ограничен.";case "ANALYSE"->"Расходует личный образец, открывает биофильтр и решение о Рое.";case "LOOT"->"+4 детали, +2 медикамента, +2 фильтра. Шкаф опустеет.";case "DISINFECT"->"Биомасса в секторе −20%, кислород +8%.";case "PRESSURIZE"->"Подготовка доступного выхода: питание, архивный ключ и открытый D13.";case "ASCEND"->"Герой покидает бункер. После выхода группы — итоги эпизода.";case "REST"->"Выносливость +35, стресс −15, здоровье +5.";case "RATIONS"->"Паёк и 1 воды: здоровье +8, выносливость +25, стресс −8.";case "RESCUE"->"После анализа Лина завершает карантин и передаёт пропуск станции 47.";default->"";};
        String reason="";
        if(!f.actions.contains(action))reason="Такой операции у объекта нет";
        else if(s.phase!=Phase.ACTION)reason="Операции доступны в фазе действий";
        else if(!a.alive())reason=a.life.equals("EVACUATED")?"Герой уже на поверхности":"Нужно живое тело";
        else if(!a.knownRooms.contains(f.room))reason="Сектор ещё не разведан";
        else if(Set.of("MEDKIT","COLLECT").contains(action)&&a.carriedWeight()+(action.equals("MEDKIT")?.7:.4)>18)reason="Нет места в инвентаре (18 кг)";
        else if(action.equals("REPAIR")&&s.flags.contains("power_stable"))reason="Щит уже восстановлен";
        else if(action.equals("GENERATOR")&&(s.flags.contains("reserve_started")||s.power>=100))reason="Резерв уже запущен или питание полное";
        else if(action.equals("RELAY")&&s.flags.contains("relay_restored"))reason="Связь восстановлена";
        else if(action.equals("CAPSULES")&&a.privateEvidence.contains("conservation"))reason="Капсулы уже осмотрены этим героем";
        else if(action.equals("ARCHIVE")&&a.privateEvidence.contains("protocol_lock"))reason="Ключ и личный приказ уже получены";
        else if(action.equals("LOOT")&&s.flags.contains("looted_"+f.room))reason="Шкаф пуст";
        else if(action.equals("COLLECT")&&(!s.flags.contains("sample_goal")||s.flags.contains("hive_core_studied")))reason=s.flags.contains("hive_core_studied")?"Исследование завершено":"Нужно общее решение об исследовании";
        else if(action.equals("COLLECT")&&(a.inventory.getOrDefault("hive_sample",new Item()).count>0||s.progress.getOrDefault("sample_stock",3d)<1))reason="Образец уже взят или запас исчерпан";
        else if(action.equals("ANALYSE")&&s.flags.contains("hive_core_studied"))reason="Анализ уже выполнен";
        else if(action.equals("ANALYSE")&&a.inventory.getOrDefault("hive_sample",new Item()).count<1)reason="Сначала отберите образец на этом анализаторе";
        else if(Set.of("COLLECT","ANALYSE").contains(action)&&!s.rooms.get(f.room).powered)reason="Нет питания лаборатории";
        else if(action.equals("DISINFECT")&&s.rooms.get(f.room).biomass<.5)reason="Активной биомассы нет";
        else if(action.equals("REST")&&a.stamina>=99&&a.stress<=1&&a.hp>=100)reason="Герой уже восстановился";
        else if(action.equals("RATIONS")&&a.stamina>=99&&a.stress<=1&&a.hp>=100)reason="Восстановление не требуется";
        else if(action.equals("RATIONS")&&s.water<1)reason="Нет питьевой воды";
        else if(action.equals("RESCUE")&&(!s.flags.contains("lina_safe")||!s.flags.contains("hive_core_studied")||s.flags.contains("lina_saved")||!s.npcs.get("lina").condition.equals("QUARANTINE")))reason="Нужны карантин Лины и анализ; повторное завершение недоступно";
        else if(Set.of("PRESSURIZE","ASCEND").contains(action)&&(!s.flags.contains("archive_access")||!s.doors.get("D13").passable()||s.power<20))reason="Нужны архивный ключ, открытый D13 и питание от 20%";
        else if(action.equals("PRESSURIZE")&&s.flags.contains("exit_ready"))reason="Подъёмник уже подготовлен — выберите подъём";
        else if(action.equals("ASCEND")&&!s.flags.contains("exit_ready"))reason="Сначала подготовьте подъёмник";
        if(reason.isEmpty())for(var e:cost.entrySet())if(s.supplies.getOrDefault(e.getKey(),0)<e.getValue()){reason="Недостаточно ресурса: "+resourceName(e.getKey());break;}
        return new Offer(action,title(action),description,duration,cost,reason);
    }
}
