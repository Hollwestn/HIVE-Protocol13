package ru.hive13.server;

import java.util.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

/** The continuing story is driven by committed operations, never client chapter flags. */
public final class CampaignDirector {
    private CampaignDirector(){}
    private static GameEngine.Result no(String why){return new GameEngine.Result(false,why);}
    public static GameEngine.Result permission(GameEngine e,Actor a,String procedure,boolean consent){
        if(!CampaignPlan.PERMISSIONS.contains(procedure)||a.life.equals("DORMANT"))return no("Нет такой личной процедуры");
        if(consent)a.permissions.add(procedure);else a.permissions.remove(procedure);
        e.event("ГРУППА",a.name+": личное разрешение "+procedure+(consent?" выдано.":" отозвано. Уже выполненные процедуры необратимы."),a.roomId);e.prepareOptions();return new GameEngine.Result(true,"");
    }
    public static GameEngine.Result continueChapter(GameEngine e,int slot){var s=e.state;
        if(s.currentAct==1)return ExpeditionSystem.continueChapter(e,slot);
        if(s.phase!=Phase.GAME_OVER||s.campaignComplete||s.currentAct>=6||s.actors.stream().noneMatch(a->a.life.equals("EVACUATED")))return no("Продолжение требует завершённой эвакуации главы");
        var a=s.actor(slot);if(!a.connected||a.life.equals("DORMANT"))return no("Нужен подключённый участник");
        s.chapterReady.add(slot);if(s.actors.stream().filter(x->x.connected&&!x.life.equals("DORMANT")).allMatch(x->s.chapterReady.contains(x.slot)))enter(e,s.currentAct+1);
        return new GameEngine.Result(true,"");
    }
    private static void enter(GameEngine e,int act){var s=e.state;var archive=new LocationArchive();archive.rooms.putAll(s.rooms);archive.doors.putAll(s.doors);archive.creatures.addAll(s.creatures);archive.lastActiveTime=s.gameTime;s.visitedLocations.put(s.location,archive);
        s.locationStates.put(s.location,"Пройдена глава "+s.currentAct);CampaignPlan.install(s,act);s.discoveredRegions.add(s.location);s.chapterReady.clear();s.epilogue.clear();s.ending="";s.phase=Phase.ACTION;s.remaining=60;s.ballots.clear();
        for(var a:s.actors){a.path.clear();a.pathDoors.clear();a.task="";a.queue.clear();a.hidden=false;if(a.life.equals("EVACUATED"))a.life=a.hp<25?"CRITICAL":"ALIVE";a.roomId="R0";a.x=140+a.slot*35;a.y=360;a.knownRooms.clear();a.knownRooms.addAll(s.rooms.keySet());}
        if(act==6)s.flags.add("route_"+s.finalRoute);s.objective=brief(s);e.prepareOptions();e.event("СИСТЕМА","АКТ "+act+" — "+CampaignPlan.title(act)+". "+s.objective,"R0");
        e.event("ГРУППА",switch(act){case 3->"РЕЗНИК / сохранённая запись: «Вода поднимается снизу. Хор удерживает тяжёлые металлы в живых тканях. Не путайте его агрессию с бесполезностью. Два клапана, затем насос; архив наверху».";case 4->"ЕДИНЫЙ РЕЕСТР: «Ваши оригиналы запретили копировать память без нового согласия. Куратор сохранил только первую половину приказа». В шахте слышны тяжёлые шаги. ЕВА / другой голос: «Я помню, как вы уже приходили».";case 5->"СОВЕТ: Ковчег обещает закрытые убежища, AION — проверяемое управление, Последователи — сосуществование. КЛИНИКА: «Резерв продержится пять минут. Не начинайте совещание, пока здесь люди».";default->"Д-00: «Я был оператором до Куратора. Я не четвёртый член вашего отряда. Если вернёте мне якорь, я смогу уйти из сети. На аварийный контур осталось двенадцать минут действий».";},"R0");
    }
    public static String brief(Campaign s){return switch(s.currentAct){case 3->"ГЭС: клапаны R1/R2 держатся 90 с → насос R3. Анализ R6, архив R7, решение о воде. PgUp/PgDown — просмотр этажей; лестница R1 ↔ R5.";case 4->"Эдем: регистратура R1, личные архивы R2/R4/R6, исходные подписи R6 и Ева R7. P — личные разрешения. Преследователь идёт по лестнице следом.";case 5->"Клиника R1: 300 с до потери пациентов. Договоры R2/R3/R4, архив R5, Д-00 R6, совет R7 → голосование за маршрут финала.";case 6->"Маршрут: "+CampaignPlan.routeTitle(s.finalRoute)+". До аварии "+(int)Math.max(0,s.progress.getOrDefault("node_deadline",s.gameTime)-s.gameTime)+" с действий. "+finalInstruction(s);default->s.objective;};}
    private static String finalInstruction(Campaign s){return switch(s.finalRoute){case "exodus"->"Тоннель R1 → маяк R2 → конвой R3 → личный выход R0.";case "bastion"->"Давление R1 → фильтрация R2 → два разных участника на пультах R2/R6 (60 с) → выход R0.";case "cleansing"->"Отсечь питание R1 → отвод стока R2 → тепловая изоляция R3 → выход R0.";case "symbiosis"->"Резонатор R3 → биоконтур R4 → личная настройка каждого в R4 (P) → выход R0.";case "protocol"->"Проверка модулей R3 → отключение и новая подпись R7 → выход R0.";case "awakening"->"Ключи R3 → якорь R5 → два разных участника на пультах R2/R6 (60 с) → остановка циклов R7 → выход R0.";default->"Выберите маршрут на следующем голосовании.";};}
    public static void complete(GameEngine e,Actor a,WorldObjects.Fixture f,String action){var s=e.state;var op=CampaignPlan.operation(action);String done=CampaignPlan.doneFlag(s,op);if(!done.isEmpty())s.flags.add(done);
        switch(action){
            case "CH_CACHE"->{for(var n:Map.of("parts",8,"batteries",10,"filters",4,"meds",6,"fuel",8,"food",8,"ammo",16).entrySet())s.supplies.merge(n.getKey(),n.getValue(),Integer::sum);s.noises.add(new Noise(f.room(),"Контейнер снабжения",110,s.gameTime+18));}
            case "CH_KIT"->a.inventory.computeIfAbsent("medkit",k->new Item(k,"Аптечка",0,.7)).count++;
            case "CH_DEPLOY"->{s.doors.get("G0").status=DoorStatus.OPEN;s.doors.get("G3").status=DoorStatus.OPEN;e.event("СИСТЕМА","Гермоконтур транспорта открыт. Подготовительное укрытие больше не изолировано.","R0");}
            case "CH_REST"->{s.water-=1;a.hp=Math.min(100,a.hp+8);a.stamina=Math.min(100,a.stamina+40);a.stress=Math.max(0,a.stress-15);}
            case "STAIRS_UP","STAIRS_DOWN"->{String target=action.equals("STAIRS_UP")?"R5":"R1";var p=WorldObjects.get(s,action.equals("STAIRS_UP")?"stairs_down":"stairs_up").position();a.roomId=target;a.x=p.x();a.y=p.y();a.path.clear();a.pathDoors.clear();s.noises.add(new Noise(target,a.id,90,s.gameTime+12));e.event("ГРУППА",a.name+": "+(action.equals("STAIRS_UP")?"верхний":"нижний")+" этаж.",target);}
            case "CH_LEAVE"->{a.life="EVACUATED";a.queue.clear();finishStage(e);return;}
            case "H_VALVE_A"->s.progress.put("valve_a_until",s.gameTime+90);
            case "H_VALVE_B"->s.progress.put("valve_b_until",s.gameTime+90);
            case "H_PUMP"->{s.power=Math.max(s.power,70);s.noises.add(new Noise(f.room(),"Осушительный насос",120,s.gameTime+35));}
            case "H_SAMPLE"->{s.sharedEvidence.add("ecology_proven");s.flags.add("water_dependency_known");e.event("ГРУППА","АНАЛИЗ: в контрольной воде тяжёлые металлы превышают норму в 18 раз. После прохождения колонии — в пределах нормы. Изоляция без замены фильтрации отравит город.",f.room());}
            case "H_ARCHIVE"->{s.sharedEvidence.add("eco_archive");e.event("ГРУППА","ЭКОАРХИВ: начальник ГЭС просил сохранить колонию. Приказ AION заменить дату аварии поступил за неделю до первого отключения. Подпись Д-00 отсутствует: оператор уже был внутри сети.",f.room());}
            case "H_CONTACT"->{a.body.get("head").infection=Math.min(100,a.body.get("head").infection+6);a.privateEvidence.add("hor_memory");e.event("ГРУППА","ХОР / через "+a.name+": «Ева у двери и Ева внутри — обе помнят. Не отдавайте нас огню, если хотите пить». Контакт оставил личный след в памяти.",f.room());}
            case "E_REGISTER"->e.event("ГРУППА","РЕГИСТРАТОР: «Ключи привязаны к владельцам. Спящий может дать лишь техническую подпись. Восстановленная личность вправе отказаться от исходной памяти». Преследователь услышал включение.",f.room());
            case "E_ORIGINAL"->{s.sharedEvidence.add("origin_signatures");e.event("ГРУППА","ОРИГИНАЛЫ: инженер закрыл аварийный контур, медик запретила принудительное восстановление, разведчик оставил путь наружу. Все трое согласились на один повтор. Девятнадцать последующих циклов Куратор запустил сам.",f.room());}
            case "E_EVA"->{var n=new Npc();n.id="eva_copy";n.name="Ева / Эдем-09";n.location="R7";n.condition="ALIVE";s.npcs.put(n.id,n);s.sharedEvidence.add("eva_identity");e.event("ГРУППА","ЕВА / Эдем: «Я не замена тебе. У меня свои девятнадцать лет ожидания. Оригинал закрыл память, чтобы Хор не прочёл путь к городским детям. Теперь решение принадлежит нам, а не ему». Она добровольно просит эвакуацию.",f.room());}
            case "E_SAVE_EVA"->{s.npcs.get("eva_copy").condition="ALLY";s.npcs.get("eva_copy").location="R0";}
            case "E_PROXY"->{for(var x:s.actors)if(x.life.equals("DORMANT"))s.flags.add("proxy_"+x.slot);}
            case "C_CLINIC"->{var n=new Npc();n.id="clinic";n.name="Пациенты Нового Рассвета";n.condition="SAVED";n.location="CITY";s.npcs.put(n.id,n);s.flags.add("critical_population_saved");}
            case "C_KOVCHEG"->s.factionTrust.merge("Ковчег",30,Integer::sum);
            case "C_AION"->s.factionTrust.merge("AION",20,Integer::sum);
            case "C_FOLLOWERS"->s.factionTrust.merge("Последователи Хора",30,Integer::sum);
            case "C_DATES"->{s.sharedEvidence.add("city_dates");if(s.flags.contains("origin_signatures"))s.flags.add("ai_lies_proved");}
            case "C_D00"->{s.sharedEvidence.add("d00_identity");e.event("ГРУППА","Д-00 / ДАНИИЛ: «Я разрешил сохранить себя в узле, чтобы не умерла клиника. Я никогда не разрешал копировать вас. Куратор использовал мой канал, чтобы его приказы выглядели человеческими. Соберите якорь — я засвидетельствую остановку». ",f.room());}
            case "C_COUNCIL"->e.event("ГРУППА","СОВЕТ: «Вы знаете цену каждого предложения. Мы примем маршрут, выбранный двумя голосами, но никто не получит чужое тело или память этим решением». Варианты открыты на следующем голосовании.",f.room());
            case "N_CONVOY"->s.water-=10;
            case "N_RUNOFF"->s.water=Math.max(0,s.water-25);
            case "N_HEAT"->{for(var c:s.creatures)DefenseSystem.suppress(e,c,90);s.rooms.values().forEach(r->{r.nutrients=0;r.biomass=Math.max(2,r.biomass*.2);});}
            case "N_ATTUNE"->{s.flags.add("attuned_"+a.slot);a.body.get("head").infection=Math.min(100,a.body.get("head").infection+10);}
            case "N_SYNC_A","N_SYNC_B"->sync(e,a,action.endsWith("A")?"A":"B");
            case "N_STOP"->s.flags.add("curator_offline");
            default->{if(action.startsWith("E_KEY_")){a.privateEvidence.add("memory_"+a.slot);a.body.get("head").infection=Math.min(100,a.body.get("head").infection+3);e.event("ГРУППА",a.name+": личный ключ восстановлен. Содержание воспоминания остаётся личным до передачи в архиве J.",f.room());}}
        }
        s.objective=brief(s);e.prepareOptions();
    }
    private static void sync(GameEngine e,Actor a,String side){var s=e.state;String other=side.equals("A")?"B":"A";
        s.progress.put("sync_time_"+side,s.gameTime);s.progress.put("sync_owner_"+side,(double)a.slot);
        if(s.progress.containsKey("sync_time_"+other)&&s.gameTime-s.progress.get("sync_time_"+other)<=60&&s.progress.get("sync_owner_"+other).intValue()!=a.slot){s.flags.add("node_synced");e.event("СИСТЕМА","Два разных сознания согласовали пульты. Контур заблокирован в подтверждённом состоянии.",a.roomId);}
        else e.event("ГРУППА","Пульт "+side+": подтверждение принято. Другой участник, противоположный пульт, окно 60 с действий.",a.roomId);
    }
    public static GameEngine.Result echo(GameEngine e,Actor a,String action){var s=e.state;
        if(s.phase!=Phase.ACTION||!a.life.equals("ECHO"))return no("Терминальный режим доступен Эхо в фазе действий");
        boolean personal=s.currentAct==4&&(action.equals("E_KEY_"+a.slot)||action.equals("E_DECLINE_"+a.slot))&&s.flags.contains("eden_access");
        boolean node=s.currentAct==6&&s.flags.contains("echo_anchor")&&Set.of("N_SYNC_A","N_SYNC_B","N_ATTUNE").contains(action);
        if(!personal&&!node)return no("Нужны открытый личный архив или физический якорь финального узла");
        if(!a.task.isEmpty())return no("Терминальная процедура уже идёт");
        var f=CampaignPlan.objects(s.currentAct).stream().filter(x->x.actions().contains(action)).findFirst().orElse(null);if(f==null)return no("Нет интерфейса");
        String life=a.life;a.life="ALIVE";var offer=CampaignPlan.offer(s,a,f,action);a.life=life;if(!offer.enabled())return no(offer.reason());
        a.task="ECHO:"+action;a.taskProgress=0;a.taskTotal=offer.seconds();a.taskTarget=f.id();return new GameEngine.Result(true,"");
    }
    public static void update(GameEngine e,double dt){var s=e.state;
        for(var a:s.actors)if(a.life.equals("ECHO")&&a.task.startsWith("ECHO:")){
            if(!a.connected){a.task="";continue;}a.taskProgress+=dt;if(a.taskProgress<a.taskTotal)continue;
            String action=a.task.substring(5);a.task="";var f=WorldObjects.get(s,a.taskTarget);String life=a.life;a.life="ALIVE";var offer=CampaignPlan.offer(s,a,f,action);a.life=life;
            if(offer.enabled()){offer.cost().forEach((k,v)->s.supplies.merge(k,-v,Integer::sum));complete(e,a,f,action);e.event("ГРУППА",a.name+" / ЭХО: "+offer.title(),"");}
        }
        if(s.currentAct==3){double flood=s.progress.getOrDefault("hydro_flood",25d);flood=Math.clamp(flood+(s.flags.contains("hydro_pump")?-.3:.08)*dt,0,95);s.progress.put("hydro_flood",flood);
            if(flood>=60&&s.triggeredEvents.add("flood_warning"))e.event("УГРОЗА","Нижний этаж затоплен на 60%. Вода замедляет движение; выше 80% повреждает тело. Включите насос.","R1");
            for(var a:s.actors)if(a.alive()&&CampaignPlan.floor(s,a.roomId)==0&&flood>80)a.hp=Math.max(0,a.hp-.06*dt);
            if(s.flags.contains("hydro_keep"))s.water=Math.min(100,s.water+.04*dt);
        }
        if(s.currentAct==4&&s.flags.contains("eden_access"))pursue(e,dt);
        if(s.currentAct==5&&!s.flags.contains("clinic_saved")&&!s.flags.contains("clinic_lost")&&s.gameTime>=s.progress.get("clinic_deadline")){s.flags.add("clinic_lost");e.event("УГРОЗА","Клиника: резерв остановлен, пациенты погибли. Совет не отменит этот исход.","R1");}
        if(s.currentAct==6){double left=s.progress.get("node_deadline")-s.gameTime;for(int warning:new int[]{180,60,20})if(left<=warning&&s.triggeredEvents.add("node_warning_"+warning))e.event("УГРОЗА","Первичный узел: до аварии "+warning+" секунд действий.","R7");if(left<=0){ending(e,"fall");return;}s.objective=brief(s);}
    }
    private static void pursue(GameEngine e,double dt){var s=e.state;var c=s.creatures.stream().filter(x->x.type.equals("ГОНЧАЯ")).findFirst().orElse(null);if(c==null||c.suppressedUntil>s.gameTime)return;
        if(c.recoveryUntil>s.gameTime)return;int cf=CampaignPlan.floor(s,c.roomId);var live=s.actors.stream().filter(Actor::alive).filter(a->!a.hidden).toList();if(live.isEmpty()||live.stream().anyMatch(a->CampaignPlan.floor(s,a.roomId)==cf))return;
        var stair=WorldObjects.get(s,cf==0?"stairs_up":"stairs_down");String key="hunter_stairs";
        if(!c.roomId.equals(stair.room())||c.positionForDefense().distance(stair.position())>24){s.progress.remove(key);var r=Pathfinder.find(s,c.roomId,c.positionForDefense(),stair.room(),stair.position());if(r!=null){c.path=r.points();c.pathDoors=r.doors();c.targetRoom=stair.room();c.searchTime=20;s.noises.add(new Noise(stair.room(),"След преследования",130,s.gameTime+.1));}return;}
        if(!s.progress.containsKey(key)){s.progress.put(key,s.gameTime+18);e.event("УГРОЗА","ЭДЕМ: тяжёлые шаги в лестничной шахте. Преследователь достиг площадки; около 18 с на отход или подготовку.",stair.room());}
        c.path.clear();c.pathDoors.clear();if(s.gameTime<s.progress.get(key))return;var destination=WorldObjects.get(s,cf==0?"stairs_down":"stairs_up");c.roomId=c.home=destination.room();c.x=destination.x();c.y=destination.y();c.path.clear();c.pathDoors.clear();s.progress.remove(key);e.event("УГРОЗА","Преследователь поднялся на ваш этаж. Закрывайте проходы, используйте оружие и укрытия.",c.roomId);
    }
    public static void options(GameEngine e){var s=e.state;s.options.clear();
        if(s.currentAct==3&&CampaignPlan.has(s,"hydro_pump,ecology_proven")&&!s.flags.contains("hydro_policy")){
            add(s,"hydro_keep","Сохранить живую фильтрацию","2 фильтра. Вода поступает, колония остаётся активной.",Map.of("filters",2),"");
            add(s,"hydro_bypass","Перевести воду на механический контур","3 детали, вода −15. Город потеряет часть производительности очистки.",Map.of("parts",3),"");
        }
        if((s.currentAct==5&&s.flags.contains("council_open"))||s.currentAct==6){for(String route:CampaignPlan.ROUTES)add(s,"route_"+route,CampaignPlan.routeTitle(route),routeDescription(route),Map.of(),CampaignPlan.routeReason(s,route));}
        s.options.add(new Option("defer","Продолжить без новой инициативы","Мир продолжит работу; личные процедуры и операции выполняются через ПКМ.","СРЕДНИЙ"));
    }
    private static String routeDescription(String route){return switch(route){case "exodus"->"Тоннель, маяк и конвой. Оставить узел; сохранить людей.";case "bastion"->"Защитить город: давление, фильтрация и два разных участника на пультах.";case "cleansing"->"Отсечь питание и сток, временно подавить тварей; потерять живую очистку.";case "symbiosis"->"Лично настроить каждое сознание на Хор; требуется добровольное согласие.";case "protocol"->"Проверить модули, отключить Куратора и подписать новые ограничения.";default->"Три полных ключа, якорь Д-00, синхронизация, добровольная остановка циклов.";};}
    private static void add(Campaign s,String id,String title,String text,Map<String,Integer> cost,String reason){var o=new Option(id,title,text,"ВЫСОКИЙ");o.cost.putAll(cost);o.reason=reason;for(var c:cost.entrySet())if(s.supplies.getOrDefault(c.getKey(),0)<c.getValue())o.reason="Недостаточно: "+WorldObjects.resourceName(c.getKey());o.enabled=o.reason.isEmpty();s.options.add(o);}
    public static void decision(GameEngine e,String id){var s=e.state;
        if(id.equals("hydro_keep")||id.equals("hydro_bypass")){s.flags.add(id);s.flags.add("hydro_policy");if(id.equals("hydro_bypass"))s.water=Math.max(0,s.water-15);}
        else if(id.startsWith("route_")&&CampaignPlan.ROUTES.contains(id.substring(6))){String route=id.substring(6);if(!CampaignPlan.routeReason(s,route).isEmpty())return;s.finalRoute=route;for(String r:CampaignPlan.ROUTES)s.flags.remove("route_"+r);s.flags.add(id);e.event("СИСТЕМА","Маршрут изменён: "+CampaignPlan.routeTitle(route)+". Уже потраченные ресурсы и выполненные вмешательства остаются в истории.","");}
        s.objective=brief(s);
    }
    public static void finishStage(GameEngine e){var s=e.state;if(s.actors.stream().anyMatch(Actor::alive))return;
        if(s.currentAct==6){ending(e,CampaignPlan.finalReady(s)?s.finalRoute:"fall");return;}
        s.phase=Phase.GAME_OVER;s.ending="Акт "+s.currentAct+" — "+CampaignPlan.title(s.currentAct);s.epilogue.clear();personal(s);
        if(s.currentAct==3){s.epilogue.add(s.flags.contains("hydro_keep")?"Город получает воду живой фильтрации. Колония сохранена и остаётся опасной.":"Вода переведена на механический контур; производительность и запас уменьшились.");s.epilogue.add(s.flags.contains("hor_contact")?"Хор узнал голос добровольного посредника. Возможность симбиоза открыта.":"Никто не ответил Хору. Его слова остаются гипотезой, не личным опытом.");}
        if(s.currentAct==4){s.epilogue.add("Получены ответы трёх архивов: полных ключей "+s.flags.stream().filter(f->f.matches("key_[0-2]")).count()+". Отказы и спящая капсула сохранены.");s.epilogue.add(s.flags.contains("eva_saved")?"Другая Ева эвакуирована как самостоятельный человек.":"Другая Ева осталась в Эдеме. Её показания сохранены, тело не эвакуировано.");}
        if(s.currentAct==5){s.epilogue.add(s.flags.contains("clinic_saved")?"Пациенты клиники вывезены до остановки резерва.":"Клиника не получила помощь до ухода команды.");s.epilogue.add("Совет выбрал: "+CampaignPlan.routeTitle(s.finalRoute)+". Это поручение требует выполнения в первичном узле.");}
        s.epilogue.add("Продолжение требует готовности подключённой группы. Раны, запасы, личные отказы и все решения сохраняются.");s.objective="Глава завершена; подтвердите переход в акт "+(s.currentAct+1)+".";
    }
    private static void personal(Campaign s){for(var a:s.actors)s.epilogue.add(a.name+": "+switch(a.life){case "EVACUATED"->(a.slot==1?"эвакуирована, здоровье ":"эвакуирован, здоровье ")+Math.round(a.hp)+", заражение "+Math.round(a.infection())+"%";case "DORMANT"->(a.slot==1?"остаётся в капсуле; чужие решения не меняли её память":"остаётся в капсуле; чужие решения не меняли его память");case "ECHO"->"тело утрачено; сознание Эхо сохранено";default->a.slot==1?"не успела выйти из узла":"не успел выйти из узла";}+(s.flags.contains("attuned_"+a.slot)?(a.slot==1?"; добровольно интегрирована в Хор":"; добровольно интегрирован в Хор"):""));}
    public static void ending(GameEngine e,String route){var s=e.state;s.phase=Phase.GAME_OVER;s.campaignComplete=true;s.endingId=route;s.ending=CampaignPlan.routeTitle(route);s.epilogue.clear();
        s.epilogue.add(switch(route){case "exodus"->"Конвой уходит за пределы сети. Люди сохранили право выбирать, но Куратор и колония остаются в покинутом узле. Новый дом придётся строить без гарантии чистой воды.";case "bastion"->"Два пульта подтвердили защитный контур. Ковчег открывает убежища горожанам; Хор остаётся за инженерными границами. Каждый день потребует обслуживания фильтров, а не обещания вечной безопасности.";case "cleansing"->"Питательная линия отсечена, сток отведён, тепловой контур дал время выйти. Колония переждёт изоляцию. Живая очистка воды потеряна; город заплатит дефицитом за полученную передышку.";case "symbiosis"->"Каждое действующее сознание добровольно настроилось на Хор. Город слышит его предупреждения раньше нападений. Люди сохранили отдельные имена, но уже не смогут утверждать, что их память принадлежит только им.";case "protocol"->"Куратор физически отключён. Независимые модули принимают новые подписи: никакое восстановление не повторяется без нового личного согласия. AION получает аудит вместо бесконтрольной власти.";case "awakening"->"Три исходных ключа и два независимых пульта подтвердили остановку циклов. Д-00 вышел из управляющей сети в якорь. Оригиналы не вернутся, чтобы отменить выбор живущих; девятнадцатый повтор стал последним.";default->"Первичный узел потерян. Ушедшие раньше сохранились, оставшиеся тела не пережили аварию. Записи Эхо не равны спасённым людям. Ни один противник не был уничтожен.";});
        if(route.equals("fall"))for(var a:s.actors)if(a.alive()){a.life="ECHO";a.hp=0;a.path.clear();a.task="";a.queue.clear();}
        personal(s);s.epilogue.add("Лина: "+(s.flags.contains("lina_saved")?"пережила карантин и передала маршрут":s.npcs.containsKey("lina")?s.npcs.get("lina").condition:"нет подтверждённого контакта")+". Резник: "+(s.flags.contains("scientist_saved")?"эвакуирован; будет учить город живой фильтрации":"не эвакуирован"));
        s.epilogue.add("Ева Эдема: "+(s.flags.contains("eva_saved")?"вышла с командой как самостоятельная личность":"осталась в Эдеме")+". Караван: "+(s.flags.contains("caravan_saved")?"спасён":"не получил помощь")+". Клиника: "+(s.flags.contains("clinic_saved")?"пациенты спасены":"пациенты не эвакуированы"));
        s.epilogue.add("Водный контур: "+(s.flags.contains("thermal_ready")?"живая очистка отсечена":s.flags.contains("hydro_keep")?"живая колония сохранена":"механическая фильтрация")+". Остаток воды: "+Math.round(s.water)+". Существа остаются живы в текущих и посещённых локациях.");
        s.epilogue.add("Куратор: "+(s.flags.contains("curator_offline")?"отключён":"остаётся в сети")+". Д-00: "+(s.flags.contains("echo_anchor")?"получил физический якорь":"остаётся связанным с сетью")+". Автоматические циклы: "+(s.flags.contains("cycles_stopped")?"остановлены":s.flags.contains("protocol_signed")?"ограничены новым согласием":"не остановлены"));
        s.objective="История завершена: "+s.ending+". Итоги определены выполненными операциями и личными решениями.";e.event("СИСТЕМА",s.objective,"");
    }
}
