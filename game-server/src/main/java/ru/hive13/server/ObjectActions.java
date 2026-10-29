package ru.hive13.server;

import java.util.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

/** Approach, operate, commit: physical fixtures never grant an effect at menu-click time. */
public final class ObjectActions {
    private ObjectActions(){}
    private static GameEngine.Result no(String text){return new GameEngine.Result(false,text);}
    private static GameEngine.Result ok(){return new GameEngine.Result(true,"");}
    public static GameEngine.Result order(GameEngine e,Actor a,String id,String action){
        var f=WorldObjects.get(e.state,id);if(f==null)return no("Объект не найден");var offer=WorldObjects.offer(e.state,a,f,action);if(!offer.enabled())return no(offer.reason());
        if(!a.roomId.equals(f.room())||a.position().distance(f.position())>24){
            if(e.state.currentAct>=3&&CampaignPlan.floor(e.state,a.roomId)!=CampaignPlan.floor(e.state,f.room()))return no("Объект на другом этаже. Сначала используйте лестницу R1 ↔ R5 через ПКМ; PgUp/PgDown меняют только просмотр.");
            var route=Pathfinder.find(e.state,a.roomId,a.position(),f.room(),f.position());
            if(route==null)return no("Проход к «"+f.name()+"» закрыт. Откройте шлюзы через ПКМ; операция ещё не начата.");
            a.hidden=false;a.queue.clear();a.path=route.points();a.pathDoors=route.doors();a.movement="WALK";a.task="";
            var pending=new PlannedAction();pending.type="OBJECT";pending.target=id;pending.action=action;a.queue.add(pending);
            e.event("ГРУППА",a.name+": идёт к «"+f.name()+"», затем — "+offer.title(),f.room());return ok();
        }
        a.hidden=false;a.queue.clear();a.path.clear();a.pathDoors.clear();a.task="OBJECT:"+action;a.taskTarget=id;a.taskTotal=offer.seconds();
        a.taskProgress=e.state.progress.getOrDefault(key(a),0d);e.event("ГРУППА",a.name+": "+offer.title()+" / "+(int)offer.seconds()+" с",f.room());return ok();
    }
    public static GameEngine.Result door(GameEngine e,Actor a,String id,String action){
        var d=e.state.doors.get(id);if(d==null||!Set.of("OPEN","CLOSE").contains(action))return no("Нет такой операции шлюза");
        if(action.equals("OPEN")&&(d.status==DoorStatus.SEALED||d.status==DoorStatus.LOCKED&&!e.state.flags.contains("archive_access")))return no(d.status==DoorStatus.SEALED?"Нужно командное снятие изоляции":"Сначала получите ключ в архиве B6");
        if(action.equals("OPEN")&&e.state.power<20)return no("Нужно питание от 20% для открытия шлюза");
        Pathfinder.Route best=null;double length=Double.POSITIVE_INFINITY;
        for(var target:List.of(Map.entry(d.a,d.pa),Map.entry(d.b,d.pb))){var route=Pathfinder.find(e.state,a.roomId,a.position(),target.getKey(),target.getValue());if(route==null)continue;double size=0;var p=a.position();for(var next:route.points()){size+=p.distance(next);p=next;}if(size<length){best=route;length=size;}}
        if(best==null)return no("Нет пути к доступной стороне шлюза");
        a.queue.clear();a.path=best.points();a.pathDoors=best.doors();a.task="";a.movement="WALK";var pending=new PlannedAction();pending.type="INTERACT";pending.target=id;pending.action=action;a.queue.add(pending);return ok();
    }
    private static String key(Actor a){return a.id+":"+a.task+":"+a.taskTarget;}
    public static void advance(GameEngine e,Actor a,double dt){
        String action=a.task.substring("OBJECT:".length());var f=WorldObjects.get(e.state,a.taskTarget);String progressKey=key(a);
        if(f==null||!a.roomId.equals(f.room())||a.position().distance(f.position())>24){a.task="";return;}
        a.taskProgress+=dt;e.state.progress.put(progressKey,a.taskProgress);if(a.taskProgress+1e-6<a.taskTotal)return;
        var offer=WorldObjects.offer(e.state,a,f,action);a.task="";a.taskProgress=0;a.taskTotal=0;e.state.progress.remove(progressKey);
        if(!offer.enabled()){e.event("СИСТЕМА",a.name+": операция отменена — "+offer.reason(),f.room());return;}
        offer.cost().forEach((resource,n)->e.state.supplies.merge(resource,-n,Integer::sum));
        var s=e.state;var room=s.rooms.get(f.room());
        if(DefenseRules.OBJECT_ACTIONS.contains(action)){DefenseSystem.complete(e,a,f,action);e.event("ГРУППА",a.name+": "+offer.title()+". "+offer.description(),f.room());return;}
        if(s.currentAct>=3){CampaignDirector.complete(e,a,f,action);e.event("ГРУППА",a.name+": "+offer.title()+". "+offer.description(),f.room());return;}
        if(s.currentAct==2){ExpeditionSystem.complete(e,a,f,action);e.event("ГРУППА",a.name+": "+offer.title()+". "+offer.description(),f.room());return;}
        switch(action){
            case "CAPSULES"->{a.privateEvidence.add("conservation");a.privateEvidence.add(a.slot==1?"body_mismatch":"capsule_marks");}
            case "REPAIR"->{s.flags.add("power_stable");s.power=85;s.rooms.values().forEach(r->r.powered=true);s.noises.add(new Noise(f.room(),"Ремонт щита",95,s.gameTime+25));}
            case "GENERATOR"->{s.flags.add("reserve_started");s.power=Math.min(100,s.power+25);s.noises.add(new Noise(f.room(),"Резерв",95,s.gameTime+35));}
            case "RELAY"->{s.flags.add("relay_restored");s.power=Math.min(100,s.power+5);s.discoveredRegions.add("sigma");}
            case "FILTER"->{s.flags.add("filter_serviced");s.ventilation=Math.min(100,s.ventilation+20);s.rooms.values().stream().filter(r->r.ventilated).forEach(r->r.oxygen=Math.min(100,r.oxygen+12));}
            case "ARCHIVE"->{s.flags.add("archive_access");a.privateEvidence.add("protocol_lock");a.privateEvidence.add(a.slot==0?"engineer_order":a.slot==1?"medical_consent":"expedition_photo");s.objective="Ключ получен. Откройте D13 через ПКМ, подготовьте подъёмник C2 и поднимитесь всей группой.";}
            case "MEDKIT"->a.inventory.computeIfAbsent("medkit",id->new Item(id,"Аптечка",0,.7)).count++;
            case "LOOT"->{s.flags.add("looted_"+f.room());s.supplies.merge("parts",4,Integer::sum);s.supplies.merge("meds",2,Integer::sum);s.supplies.merge("filters",2,Integer::sum);}
            case "DISINFECT"->{room.biomass=Math.max(0,room.biomass-20);room.oxygen=Math.min(100,room.oxygen+8);s.flags.add("decontaminated_"+f.room());}
            case "COLLECT"->{var sample=a.inventory.computeIfAbsent("hive_sample",id->new Item(id,"Образец Роя",0,.4));sample.contaminated=true;sample.count++;s.progress.put("sample_stock",s.progress.getOrDefault("sample_stock",3d)-1);s.objective="Образец в инвентаре "+a.id+". ПКМ по анализатору B7 → провести анализ.";}
            case "ANALYSE"->{a.inventory.get("hive_sample").count--;a.privateEvidence.add("water_report");s.sharedEvidence.add("water_report");s.flags.add("hive_core_studied");s.flags.add("water_dependency_known");s.objective="Анализ доказал водную зависимость. На следующем голосовании доступны биофильтр и уничтожение Роя.";}
            case "REST"->{a.stamina=Math.min(100,a.stamina+35);a.stress=Math.max(0,a.stress-15);a.hp=Math.min(100,a.hp+5);}
            case "RATIONS"->{s.water-=1;a.hp=Math.min(100,a.hp+8);a.stamina=Math.min(100,a.stamina+25);a.stress=Math.max(0,a.stress-8);}
            case "RESCUE"->{s.flags.add("lina_saved");var n=s.npcs.get("lina");n.condition="ALLY";n.location="A2";n.trust+=25;s.discoveredRegions.add("station47");}
            case "PRESSURIZE"->{s.flags.add("exit_ready");room.oxygen=95;s.objective="Подъёмник готов. Каждый живой участник: ПКМ по подъёмнику C2 → подняться на поверхность.";}
            case "ASCEND"->{a.life="EVACUATED";a.queue.clear();s.flags.add("surface_reached");s.discoveredRegions.addAll(List.of("vector","sigma"));e.event("ГРУППА",a.name+(a.slot==1?": поднялась на поверхность.":": поднялся на поверхность.")+" Остальным нужно воспользоваться подъёмником C2.","C2");finish(e);}
            default->throw new IllegalStateException(action);
        }
        e.event("СИСТЕМА",a.name+": завершено «"+offer.title()+"». "+offer.description(),f.room());
    }
    public static void finish(GameEngine e){var s=e.state;if(s.actors.stream().anyMatch(Actor::alive))return;
        s.phase=Phase.GAME_OVER;s.ending="Эпизод I — Выход из Объекта-13";s.objective="Группа покинула бункер. Итоги зависят от ваших действий и решений.";s.epilogue.clear();
        for(var a:s.actors)s.epilogue.add(a.name+" / "+a.profession+": "+(a.life.equals("EVACUATED")?(a.slot==1?"выбралась":"выбрался")+" на поверхность, заражение "+Math.round(a.infection())+"%":a.life.equals("DORMANT")?(a.slot==1?"оставлена":"оставлен")+" в капсуле восстановления":"тело утрачено; осталась запись сознания"));
        s.epilogue.add(s.flags.contains("hive_burned")?"Огонь временно подавил улей. Колония восстанавливается; чистая вода потеряна.":s.flags.contains("biofilter_installed")?"Исследованный Рой сохранён как биофильтр. Вода поступает в резервуары.":s.flags.contains("b4_sealed")?"B4 изолирован; вентиляция нуждается в обслуживании.":"Улей остался активен и продолжает распространяться по бункеру.");
        s.epilogue.add(s.flags.contains("lina_saved")?"Лина пережила карантин и передала пропуск станции 47.":"Лина: "+switch(s.npcs.get("lina").condition){case "DEAD"->"погибла при выжигании колонии";case "LEFT"->"ушла к Собирателям";case "QUARANTINE"->"осталась в карантинной камере";case "ALLY"->"допущена в медблок";default->"контакт не установлен";}+". Её судьба определена вашим решением.");
        s.epilogue.add("Питание "+Math.round(s.power)+"%, вода "+Math.round(s.water)+", деталей "+s.supplies.get("parts")+". Можно продолжить в акт II: подтвердите готовность всей подключённой группой.");
    }
}
