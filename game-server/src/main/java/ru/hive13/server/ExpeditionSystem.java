package ru.hive13.server;

import java.util.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

public final class ExpeditionSystem {
    private ExpeditionSystem(){}
    public static GameEngine.Result continueChapter(GameEngine e,int slot){var s=e.state;
        if(s.phase!=Phase.GAME_OVER||s.currentAct!=1||!s.flags.contains("surface_reached"))return new GameEngine.Result(false,"Продолжение доступно после эвакуации из Объекта-13");
        if(!s.actor(slot).connected||s.actor(slot).life.equals("DORMANT"))return new GameEngine.Result(false,"Нужен участник экспедиции");
        s.chapterReady.add(slot);e.event("ГРУППА",s.actor(slot).name+": готов продолжить экспедицию.","");
        if(s.actors.stream().filter(a->a.connected&&!a.life.equals("DORMANT")).allMatch(a->s.chapterReady.contains(a.slot)))enter(e);
        return new GameEngine.Result(true,"");
    }
    private static void enter(GameEngine e){var s=e.state;
        var archive=new LocationArchive();archive.rooms.putAll(s.rooms);archive.doors.putAll(s.doors);archive.creatures.addAll(s.creatures);archive.lastActiveTime=s.gameTime;s.visitedLocations.put(s.location,archive);
        s.locationStates.put("object13",s.flags.contains("biofilter_installed")?"Биофильтр сохранён":s.flags.contains("hive_burned")?"Колония временно подавлена огнём":"Колония активна");
        ExpeditionPlan.install(s);s.flags.add("act2_started");s.chapterReady.clear();s.epilogue.clear();s.ending="";s.phase=Phase.ACTION;s.remaining=60;s.ballots.clear();
        for(var a:s.actors){a.path.clear();a.pathDoors.clear();a.task="";a.queue.clear();a.hidden=false;if(a.life.equals("EVACUATED"))a.life=a.hp<25?"CRITICAL":"ALIVE";a.roomId="V0";a.x=115+a.slot*20;a.y=355;a.knownRooms.clear();a.knownRooms.addAll(s.rooms.keySet());}
        s.discoveredRegions.addAll(List.of("vector","sigma","station47"));s.objective="Акт II: запитайте подстанцию V1, доберитесь до Сигмы V5. Караван и лекарства — ваши дополнительные цели.";
        s.locationStates.put("vector","Экспедиция на поверхности");e.prepareOptions();
        e.event("ГРУППА","ЛИНА / записанный маршрут: «На подстанции ищите жёлтый кабель. Сигма слышит живой город. Куратор запретил мне произносить его название». Запись сохранилась независимо от её судьбы.","V0");
        e.event("СИСТЕМА","Акт II — Внешний мир. Вооружение лишь задерживает тварей. Сохранены раны, предметы и решения бункера.","V0");
    }
    public static void complete(GameEngine e,Actor a,WorldObjects.Fixture f,String action){var s=e.state;
        switch(action){
            case "GRID_RESET"->{s.flags.add("surface_grid");s.rooms.get("V1").powered=true;s.rooms.get("V5").powered=true;s.power=Math.min(100,s.power+10);s.noises.add(new Noise(f.room(),"Контактор подстанции",100,s.gameTime+20));s.objective="Сигма получила питание. ПКМ по передатчику V5; лекарства находятся в V4.";}
            case "CARAVAN_RESCUE"->{s.flags.add("caravan_saved");s.flags.add("critical_population_saved");s.water=Math.min(100,s.water+10);s.supplies.merge("food",6,Integer::sum);s.factionTrust.merge("Собиратели",20,Integer::sum);s.sharedEvidence.add("caravan_testimony");e.event("ГРУППА","КАРАВАНЩИК: «Дети пьют воду из биофильтров, а не из резервуаров Ковчега. Город не мёртв. Мы ходим туда каждую неделю». Спасённые ушли к подъёмнику.","V2");}
            case "FIELD_SUPPLY"->{s.flags.add("surface_store");s.supplies.merge("batteries",3,Integer::sum);s.supplies.merge("parts",4,Integer::sum);s.supplies.merge("ammo",6,Integer::sum);s.supplies.merge("fuel",2,Integer::sum);s.noises.add(new Noise(f.room(),"Лебёдка склада",180,s.gameTime+25));}
            case "MEDICINE"->{s.flags.add("surface_medicine");a.inventory.computeIfAbsent("medkit",key->new Item(key,"Аптечка",0,.7)).count+=2;s.supplies.merge("meds",4,Integer::sum);s.supplies.merge("filters",2,Integer::sum);s.sharedEvidence.add("surface_timeline");e.event("ГРУППА","ЖУРНАЛ ПОСТАВОК: последние лекарства доставлены из Нового Рассвета 14.02.2091. Печать, партия и подпись фармацевта не совпадают с хронологией Куратора.","V4");}
            case "SIGMA_TUNE"->{s.flags.add("sigma_online");var n=new Npc();n.id="scientist";n.name="Илья Резник";n.location="V6";n.condition="CRITICAL";n.faction="Независимые";s.npcs.put(n.id,n);s.progress.put("scientist_deadline",s.gameTime+180);s.objective="Станция 47: учёному осталось 180 с. На голосовании выберите спасение или архив; затем выполните операцию в V6.";e.event("ГРУППА","РЕЗНИК / Станция 47: «Я заперт в клинике. На сервере — логи нейронных копий. Если не успеете за мной, хотя бы заберите их. Вода здесь стала чистой после появления Хора». До критического исхода 180 с.","V6");e.prepareOptions();}
            case "SCIENTIST"->{s.flags.add("scientist_saved");s.npcs.get("scientist").condition="ALLY";s.npcs.get("scientist").location="V0";s.objective="Резник эвакуирован. Заберите архив или возвращайтесь к площадке V0 всей группой.";e.event("ГРУППА","РЕЗНИК: «В Эдеме есть другая Ева. Она не самозванка. Ваши оригиналы сами закрыли память — Хор учится по тому, что помнит носитель». Координаты Убежища-09 сохранены.","V6");s.discoveredRegions.add("eden09");}
            case "STATION_ARCHIVE"->{s.flags.add("station_archive_found");s.sharedEvidence.add("station47_logs");if(s.flags.contains("surface_medicine")){s.flags.add("ai_lies_proved");s.flags.add("original_logs_found");}s.objective="Архив станции сохранён. Спасите Резника, если успеваете, или вернитесь всей группой в V0.";e.event("ГРУППА","АРХИВ 47: Куратор 19 раз восстанавливал троих субъектов. Команда блокировки памяти подписана самими оригиналами. Независимые даты поставок позволяют доказать подмену хронологии.","V6");}
            case "FIELD_EVAC"->{a.life="EVACUATED";a.queue.clear();finish(e);}
            default->throw new IllegalArgumentException(action);
        }
    }
    public static void update(GameEngine e){var s=e.state;
        if(s.flags.contains("station_archive_found")&&s.flags.contains("surface_medicine")&&s.flags.add("ai_lies_proved")){s.flags.add("original_logs_found");e.event("ГРУППА","Независимые даты поставок и логи станции совпали. Подмена хронологии Куратором доказана.","V6");}
        if(s.flags.contains("sigma_online")&&!s.flags.contains("scientist_saved")&&!s.flags.contains("scientist_lost")&&s.gameTime>=s.progress.getOrDefault("scientist_deadline",Double.MAX_VALUE)){s.flags.add("scientist_lost");s.npcs.get("scientist").condition="DEAD";e.event("УГРОЗА","Станция 47: биосигнал Резника прекратился. Архив ещё можно забрать.","V6");}
    }
    public static void options(GameEngine e){var s=e.state;s.options.clear();
        add(s,"rescue_authorized","Выделить средства на спасение Резника","После решения: ПКМ по учёному V6, ещё 2 медикамента и 12 секунд. Сигнал должен оставаться живым.",Map.of("meds",1),s.flags.contains("sigma_online")&&!s.flags.contains("rescue_authorized")&&!s.flags.contains("scientist_lost"));
        add(s,"archive_authorized","Подключить архив станции 47","После решения: ПКМ по серверу V6. Батарея даёт резерв питания для копирования.",Map.of("batteries",1),s.flags.contains("sigma_online")&&!s.flags.contains("archive_authorized"));
        add(s,"surface_air","Подготовить чистые фильтры","+2 фильтра за 2 пайка; одноразовая подготовка к проходу через споры.",Map.of("food",2),!s.flags.contains("surface_air"));
        s.options.add(new Option("defer","Сохранить ресурсы","Продолжить операции на поверхности без новой инициативы.","СРЕДНИЙ"));
    }
    private static void add(Campaign s,String id,String name,String text,Map<String,Integer> cost,boolean available){if(!available)return;var o=new Option(id,name,text,"ВЫСОКИЙ");o.cost.putAll(cost);for(var c:cost.entrySet())if(s.supplies.getOrDefault(c.getKey(),0)<c.getValue()){o.enabled=false;o.reason="Недостаточно: "+WorldObjects.resourceName(c.getKey());}s.options.add(o);}
    public static void decision(GameEngine e,String id){var s=e.state;if(id.equals("defer"))return;s.flags.add(id);if(id.equals("surface_air"))s.supplies.merge("filters",2,Integer::sum);s.objective=id.equals("rescue_authorized")?"Спасение разрешено: ПКМ по учёному V6. Таймер болезни не останавливается после решения.":id.equals("archive_authorized")?"Архив запитан: ПКМ по серверу V6. Результат требует физического копирования.":"Фильтры подготовлены. Продолжайте экспедицию.";}
    public static void finish(GameEngine e){var s=e.state;if(s.actors.stream().anyMatch(Actor::alive))return;s.phase=Phase.GAME_OVER;s.ending="Акт II — Сигнал с живой поверхности";s.epilogue.clear();
        for(var a:s.actors)s.epilogue.add(a.name+": "+(a.life.equals("EVACUATED")?"вернулся к подъёмнику":a.life.equals("DORMANT")?"остался в капсуле":"сохранён как Эхо"));
        s.epilogue.add(s.flags.contains("caravan_saved")?"Караван спасён. Собиратели готовы свидетельствовать против изоляции города.":"Караван остался без помощи. Свидетельство о городе получено только по радио.");
        s.epilogue.add(s.flags.contains("scientist_saved")?"Резник спасён и указал Убежище-09 с другой Евой.":"Резник не эвакуирован. Копия Евы остаётся неподтверждённой версией.");
        s.epilogue.add(s.flags.contains("ai_lies_proved")?"Архив и журнал поставок доказывают подмену хронологии Куратором.":s.flags.contains("station_archive_found")?"Архив получен, но его датам ещё нужна независимая проверка.":"Архив утрачен. У команды остались слова выжившего, но нет серверных логов.");
        s.epilogue.add("Следующий путь — сердце колонии, Эдем и три личных ключа. Подтвердите продолжение всей подключённой группой.");s.objective="Экспедиция завершена. Решения, спасённые люди и доказательства сохранены.";
    }
}
