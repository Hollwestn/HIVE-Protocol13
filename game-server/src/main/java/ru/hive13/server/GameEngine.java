package ru.hive13.server;

import java.util.*;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;

/** Single-threaded authoritative simulation; all mutation passes through this class. */
public final class GameEngine {
    public Campaign state;
    public record Result(boolean accepted,String reason){}
    public GameEngine(Campaign state){this.state=state;}
    public void event(String category,String text,String room){var e=new Event();e.id=++state.eventSequence;e.tick=state.tick;e.cycle=state.cycle;e.time=state.gameTime;e.category=category;e.text=text;e.roomId=room;state.events.add(e);if(state.events.size()>200)state.events.removeFirst();}
    public Result command(int slot,Packet p){
        if(slot<0||slot>=3)return no("Недопустимая сессия");
        if(p.type==null||p.type.length()>32||p.data==null||!p.data.isObject())return no("Неверный формат команды");
        if(p.id==null||p.id.isBlank()||p.id.length()>100)return no("Нужен commandId");String key=slot+":"+p.id;
        var old=state.commandReceipts.get(key);if(old!=null)return new Result(old.accepted,old.reason);
        Result result;
        try{result=dispatch(slot,p);}catch(IllegalArgumentException e){result=no("Некорректные параметры команды");}
        state.commandReceipts.put(key,new Receipt(result.accepted(),result.reason()));while(state.commandReceipts.size()>6144)state.commandReceipts.remove(state.commandReceipts.keySet().iterator().next());return result;
    }
    private Result dispatch(int slot,Packet p){
        var a=state.actor(slot);
        if(p.type.equals("CONTINUE_CHAPTER"))return CampaignDirector.continueChapter(this,slot);
        if(p.type.equals("PERSONAL_CONSENT"))return CampaignDirector.permission(this,a,p.text("procedure"),p.data.path("consent").asBoolean());
        if(p.type.equals("ECHO_INTERFACE"))return CampaignDirector.echo(this,a,p.text("action"));
        if(p.type.equals("ECHO_ACTION"))return EchoSystem.command(this,a,p.text("action"),p.text("target"));
        if(p.type.equals("SESSION")){int players=p.integer("players");if(slot!=state.hostSlot||state.phase!=Phase.LOBBY)return no("Режим меняет хост до запуска кампании");if(players!=2&&players!=3)return no("Выберите 2 или 3 игроков");state.requiredPlayers=players;return ok();}
        if(p.type.equals("READY")){if(state.phase!=Phase.LOBBY)return no("Кампания уже идёт");a.ready=p.data.path("ready").asBoolean();return ok();}
        if(p.type.equals("START")){if(slot!=state.hostSlot)return no("Запуск доступен хосту");if(state.phase!=Phase.LOBBY)return no("Уже запущено");long connected=state.actors.stream().filter(x->x.connected).count();if(connected<state.requiredPlayers)return no("Подключено "+connected+". Для выбранного режима нужно "+state.requiredPlayers+" игроков.");if(state.actors.stream().anyMatch(x->x.connected&&!x.ready))return no("Все подключённые игроки должны нажать «Готов»");start();return ok();}
        if(p.type.equals("CONSENT")){a.consent=p.data.path("consent").asBoolean();return ok();}
        if(p.type.equals("SHARE")){String id=p.text("evidence");if(!a.privateEvidence.contains(id))return no("Улика не найдена");state.sharedEvidence.add(id);event("ГРУППА",a.name+": передана улика «"+id+"»",a.roomId);return ok();}
        if(p.type.equals("CHAT")){String t=p.text("text").strip();if(t.isBlank()||t.length()>280)return no("Сообщение: 1–280 символов");event("ГРУППА",a.name+": "+t,a.roomId);return ok();}
        if(p.type.equals("VOTE"))return vote(slot,p.text("option"),p.integer("cycle"));
        if(state.phase!=Phase.ACTION)return no("Действия доступны только в минутной фазе");
        if(!a.alive())return no("Эхо: терминал E, журнал, улики и голосование; тело недоступно");
        return switch(p.type){
            case "FIRE"->DefenseSystem.fire(this,a,p.text("target"),p.text("weapon"));
            case "USE_OBJECT"->ObjectActions.order(this,a,p.text("target"),p.text("action"));
            case "USE_DOOR"->ObjectActions.door(this,a,p.text("target"),p.text("action"));
            case "MOVE"->{var result=move(a,p.number("x"),p.number("y"),p.text("mode"));if(result.accepted())a.queue.clear();yield result;}
            case "STOP"->{a.path.clear();a.task="";a.queue.clear();yield ok();}
            case "INTERACT"->{var result=interact(a,p.text("target"),p.text("action"));if(result.accepted()&&!(p.text("target").equals("B7")&&p.text("action").equals("SAMPLE")))a.queue.clear();yield result;}
            case "QUEUE_ACTIONS"->queue(a,p);
            case "HEAL"->heal(a,p.integer("recipient"),p.text("part"));
            case "TRADE"->trade(a,p.integer("recipient"),p.text("item"));
            case "LIGHT"->{a.flashlight=!a.flashlight;yield ok();}
            default->no("Неизвестная команда");
        };
    }
    private Result queue(Actor a,Packet p){var list=p.data.path("actions");if(!list.isArray()||list.isEmpty()||list.size()>4)return no("Очередь: от 1 до 4 операций");var actions=new ArrayList<PlannedAction>();for(var node:list){var action=Json.MAPPER.convertValue(node,PlannedAction.class);if(action.type==null||!Set.of("MOVE","INTERACT").contains(action.type))return no("В очереди допустимы MOVE и INTERACT");if(action.type.equals("MOVE")&&(!Double.isFinite(action.x)||!Double.isFinite(action.y)||Pathfinder.roomAt(state,action.x,action.y).isEmpty()))return no("Недопустимая цель маршрута");if(action.type.equals("INTERACT")&&!state.rooms.containsKey(action.target)&&!state.doors.containsKey(action.target))return no("Неизвестный объект");actions.add(action);}a.queue=actions;a.path.clear();a.task="";return ok();}
    public void start(){if(state.flags.remove("resume_pending")){state.phase=state.resumePhase;event("СИСТЕМА","Кампания восстановлена. Продолжаем с сохранённого состояния.","");return;}state.phase=Phase.ACTION;state.remaining=60;state.ballots.clear();prepareOptions();
        if(state.requiredPlayers==2)state.actors.stream().filter(a->!a.connected).forEach(a->a.life="DORMANT");
        event("СИСТЕМА","КУРАТОР: Процедура восстановления завершена. Активных субъектов: "+state.actors.stream().filter(a->a.connected).count()+". Память не синхронизирована.","A2");
        event("СИСТЕМА","Проход через C2 ограничен. Осмотрите капсулы, восстановите щит B2, исследуйте архив B6.","C2");}
    private Result move(Actor a,double x,double y,String mode){
        if(!Double.isFinite(x)||!Double.isFinite(y))return no("Координаты должны быть конечны");
        String target=Pathfinder.roomAt(state,x,y);if(target.isEmpty()||!a.knownRooms.contains(target))return no("Нужен известный участок пола");
        if(!Set.of("WALK","RUN","SNEAK","CRAWL").contains(mode))return no("Неизвестный режим движения");
        var route=Pathfinder.find(state,a.roomId,a.position(),target,new Point(x,y));if(route==null)return no("Нет прохода: проверьте двери и биомассу");
        a.hidden=false;a.path=route.points();a.pathDoors=route.doors();a.movement=mode;a.task="";return ok();
    }
    private Result interact(Actor a,String target,String action){
        if(target.equals("B7")&&action.equals("SAMPLE"))return ObjectActions.order(this,a,"sample","ANALYSE");
        if(state.doors.containsKey(target)){
            var d=state.doors.get(target);if(Math.min(a.position().distance(d.center()),Math.min(a.position().distance(d.pa),a.position().distance(d.pb)))>80)return no("Подойдите к панели шлюза (до 80 единиц)");
            if(action.equals("OPEN")){if(d.status==DoorStatus.SEALED)return no("Герметизация требует командного снятия изоляции");if(d.status==DoorStatus.LOCKED&&!state.flags.contains("archive_access"))return no("Требуется архивный ключ из B6");if(state.power<20)return no("Недостаточно питания");d.bracedUntil=0;d.status=DoorStatus.OPEN;noise(a,18);event("СИСТЕМА","Открыт шлюз "+d.id,d.a);return ok();}
            if(action.equals("CLOSE")){d.status=DoorStatus.CLOSED;event("СИСТЕМА","Закрыт шлюз "+d.id,d.a);return ok();}
            return no("Доступны OPEN/CLOSE");
        }
        if(!target.equals(a.roomId))return no("Действие требует присутствия в секторе");
        String required=switch(action){case "REPAIR"->"B2";case "FILTER"->"B3";case "SAMPLE"->"B7";case "ARCHIVE"->"B6";case "CAPSULES"->"A2";case "LOOT"->"B8";case "POWER"->"B1";case "DRONE"->"C1";default->target;};
        if(!target.equals(required))return no("Операция недоступна здесь");
        if(!Set.of("REPAIR","FILTER","SAMPLE","ARCHIVE","CAPSULES","LOOT","POWER","DRONE","SCAN","LURE").contains(action))return no("Нет такой операции");
        if(action.equals("REPAIR")&&(state.supplies.get("parts")<2||state.flags.contains("power_stable")))return no("Нужны 2 комплектующие; щит ремонтируется один раз");
        if(action.equals("FILTER")&&state.supplies.get("filters")<1)return no("Нужен фильтр");
        if(action.equals("SAMPLE")&&!state.rooms.get(target).powered)return no("Лаборатория обесточена. Восстановите B2");
        if(action.equals("DRONE")&&(!droneAvailable()||state.supplies.get("batteries")<1))return no("Нужны батарея, дрон на базе и открытый маршрут C1 → C2");
        if(action.equals("LURE")&&state.supplies.get("parts")<1)return no("Нужна комплектующая");
        a.path.clear();a.task=action;a.taskTarget=target;
        a.taskProgress=state.progress.getOrDefault(a.id+":"+action,0d);event("ГРУППА",a.name+": начато «"+actionTitle(action)+"»",target);return ok();
    }
    private Result heal(Actor a,int recipient,String part){
        if(recipient<0||recipient>2)return no("Нет такого игрока");var b=state.actor(recipient);var loc=b.body.get(part);
        if(loc==null)return no("Неизвестная область тела");if(!b.alive())return no("Тело недоступно для обычного лечения");
        if(a.position().distance(b.position())>55)return no("Пациент должен быть рядом (55 единиц)");
        var kit=a.inventory.get("medkit");if(kit==null||kit.count<1)return no("Нет аптечки");
        if(loc.injury==0&&loc.infection==0&&loc.bleeding==0)return no("Эта область не нуждается в лечении");
        a.path.clear();a.task="HEAL";a.taskTarget=recipient+":"+part;a.taskProgress=0;return ok();
    }
    private Result trade(Actor a,int recipient,String item){
        if(recipient<0||recipient>2||recipient==a.slot)return no("Выберите другого игрока");var b=state.actor(recipient);
        if(a.position().distance(b.position())>55||!b.alive())return no("Получатель должен быть рядом и жив");var source=a.inventory.get(item);
        if(source==null||source.count<1)return no("Предмет отсутствует");if(b.carriedWeight()+source.weight>18)return no("Лимит груза получателя 18 кг");
        var dest=b.inventory.computeIfAbsent(item,k->new Item(item,source.name,0,source.weight));source.count--;dest.count++;event("ГРУППА",a.name+" передал "+source.name+" → "+b.name,a.roomId);return ok();
    }
    private Result vote(int slot,String option,int cycle){
        if(state.phase!=Phase.VOTING||cycle!=state.cycle)return no("Голосование закрыто или устарело");var opt=state.options.stream().filter(o->o.id.equals(option)).findFirst().orElse(null);
        if(opt==null||!opt.enabled)return no(opt==null?"Нет такого варианта":opt.reason);
        boolean changed=state.ballots.containsKey(slot);state.ballots.put(slot,option);event("ГРУППА",state.actor(slot).name+(changed?": голос изменён → ":": голос → ")+opt.title,"");
        if(state.ballots.size()==3||state.requiredPlayers==2&&state.actors.stream().filter(x->x.connected).count()>=2&&state.actors.stream().filter(x->x.connected).allMatch(x->state.ballots.containsKey(x.slot)))resolveVote();return ok();
    }
    public void advance(double elapsed){
        if(!Double.isFinite(elapsed)||elapsed<0)throw new IllegalArgumentException("elapsed");
        state.tick++;
        if(state.phase==Phase.ACTION){double dt=Math.min(elapsed,state.remaining);double left=dt;
            while(left>1e-8&&state.phase==Phase.ACTION){double step=Math.min(.05,left);simulate(step);left-=step;}state.remaining=Math.max(0,state.remaining-dt);
            if(state.remaining>0)for(int warning:new int[]{10,5})if(state.remaining<=warning&&state.triggeredEvents.add("timer:"+state.cycle+":"+warning))event("СИСТЕМА","До паузы осталось "+warning+" секунд.","");
            if(state.phase==Phase.GAME_OVER)return;
            if(state.actors.stream().noneMatch(Actor::alive)){if(state.actors.stream().anyMatch(a->a.life.equals("EVACUATED"))){if(state.currentAct>=3)CampaignDirector.finishStage(this);else if(state.currentAct==2)ExpeditionSystem.finish(this);else ObjectActions.finish(this);return;}CampaignDirector.ending(this,"fall");}
            else if(state.remaining<=1e-6){state.phase=Phase.VOTING;state.voteRemaining=30;state.ballots.clear();prepareOptions();event("СИСТЕМА","Время истекло. ПАУЗА / ГОЛОСОВАНИЕ. Мир остановлен.","");}
        }else if(state.phase==Phase.VOTING){state.voteRemaining=Math.max(0,state.voteRemaining-elapsed);if(state.voteRemaining==0)resolveVote();}
    }
    private void simulate(double dt){
        state.gameTime+=dt;
        EchoSystem.update(this);
        for(var a:state.actors){if(!a.alive())continue;
            if(!a.connected){a.path.clear();a.task="";a.queue.clear();}
            if(a.connected&&a.path.isEmpty()&&a.task.isEmpty()&&!a.queue.isEmpty()){var action=a.queue.removeFirst();var result=action.type.equals("MOVE")?move(a,action.x,action.y,action.mode):action.type.equals("OBJECT")?ObjectActions.order(this,a,action.target,action.action):interact(a,action.target,action.action);if(!result.accepted()){a.queue.clear();event("СИСТЕМА",a.name+": очередь отменена — "+result.reason(),a.roomId);}}
            if(Pathfinder.blocked(state,a.pathDoors)){a.path.clear();a.pathDoors.clear();event("СИСТЕМА",a.name+": маршрут прерван закрытым шлюзом",a.roomId);}
            boolean walking=!a.path.isEmpty();if(walking||!a.task.isEmpty())a.hidden=false;double speed=switch(a.movement){case "RUN"->a.stamina>5?46:24;case "SNEAK"->16;case "CRAWL"->10;default->29;};
            if(a.life.equals("CRITICAL"))speed*=.45;speed*=1-Math.min(.5,a.body.get("leftLeg").injury/150+a.body.get("rightLeg").injury/150);
            if(state.currentAct==3&&CampaignPlan.floor(state,a.roomId)==0&&state.progress.getOrDefault("hydro_flood",0d)>60)speed*=.7;
            var pos=Pathfinder.advance(a.position(),a.path,speed*dt);a.x=pos.x();a.y=pos.y();String room=Pathfinder.ownerAt(state,a.x,a.y);if(!room.isEmpty())a.roomId=room;
            a.stamina=Math.max(0,Math.min(100,a.stamina+(walking&&a.movement.equals("RUN")?-5:2)*dt));
            if(walking){double strength=a.movement.equals("RUN")?80:a.movement.equals("SNEAK")?8:25;if(a.slot==2)strength*=.6;noise(a,strength);}
            var r=state.rooms.get(a.roomId);if(r.biomass>8){var part=a.body.get("rightArm");part.infection=Math.min(100,part.infection+r.biomass*.0009*dt);a.stress=Math.min(100,a.stress+.05*dt);}
            if(r.oxygen<40)a.hp=Math.max(0,a.hp-(40-r.oxygen)*.004*dt);
            double bleed=a.body.values().stream().mapToDouble(b->b.bleeding).sum();a.hp=Math.max(0,a.hp-bleed*.01*dt);
            if(a.hp==0){a.life="ECHO";a.path.clear();a.task="";event("УГРОЗА",a.name+": тело утрачено. Терминальный режим ЭХО.",a.roomId);}
            else if(a.hp<25)a.life="CRITICAL";
            if(!a.task.isEmpty())advanceTask(a,dt);
        }
        if(state.phase==Phase.GAME_OVER)return;
        DefenseSystem.update(this,dt);advanceEnvironment(dt);advanceCreatures(dt);advanceDrones(dt);if(state.currentAct==1)StoryDirector.update(this);else if(state.currentAct==2)ExpeditionSystem.update(this);else CampaignDirector.update(this,dt);if(state.phase!=Phase.GAME_OVER)TensionDirector.update(this);
        state.noises.removeIf(n->n.expires<state.gameTime);
    }
    private void advanceTask(Actor a,double dt){
        if(a.task.startsWith("OBJECT:")){ObjectActions.advance(this,a,dt);return;}
        String task=a.task;double duration=switch(task){case "REPAIR"->a.slot==0?12:24;case "ARCHIVE"->8;case "HEAL"->a.slot==1?5:10;case "SAMPLE"->a.slot==1?10:20;case "DRONE"->15;default->6;};
        a.taskTotal=duration;a.taskProgress+=dt;state.progress.put(a.id+":"+task,a.taskProgress);
        if(a.taskProgress<duration)return;String target=a.taskTarget;a.task="";a.taskProgress=0;state.progress.remove(a.id+":"+task);
        switch(task){
            case "REPAIR"->{if(state.flags.contains("power_stable")||!spend("parts",2))return;state.flags.add("power_stable");state.power=85;state.rooms.values().forEach(r->r.powered=true);noise(a,95);event("СИСТЕМА","Щит B2 восстановлен. Лаборатория, камеры и шлюзы получили питание. Шум возрос.","B2");}
            case "FILTER"->{if(!spend("filters",1))return;state.ventilation=Math.min(100,state.ventilation+20);state.rooms.values().stream().filter(r->r.ventilated).forEach(r->r.oxygen=Math.min(100,r.oxygen+12));event("СИСТЕМА","Фильтр заменён; качество воздуха восстановлено.","B3");}
            case "CAPSULES"->{a.privateEvidence.add("conservation");a.privateEvidence.add(a.slot==1?"body_mismatch":"capsule_marks");event("ГРУППА",a.name+": найдена запись капсул. Подробности в личном архиве.","A2");}
            case "ARCHIVE"->{state.flags.add("archive_access");a.privateEvidence.add("protocol_lock");a.privateEvidence.add(a.slot==0?"engineer_order":a.slot==1?"medical_consent":"expedition_photo");event("ГРУППА",a.name+": архивный ключ восстановлен. C2 можно открыть локально.","B6");}
            case "LOOT"->{String key="looted_"+target;if(state.flags.add(key)){state.supplies.merge("parts",4,Integer::sum);state.supplies.merge("meds",2,Integer::sum);event("ГРУППА","Склад осмотрен: +4 комплектующие, +2 медикамента.",target);}else event("СИСТЕМА","Склад уже пуст.",target);}
            case "SAMPLE"->{a.privateEvidence.add("water_report");state.flags.add("hive_core_studied");state.flags.add("water_dependency_known");if(state.flags.contains("sample_goal"))state.objective="Образец B7 исследован. Найдите независимые данные о биофильтрации и судьбе колоний.";event("ГРУППА","Образец фильтрует токсин. Уничтожение всей сети может лишить людей воды.","B7");}
            case "POWER"->{var r=state.rooms.get("B7");r.powered=!r.powered;event("СИСТЕМА","Контур лаборатории: "+(r.powered?"ВКЛЮЧЁН":"ОТКЛЮЧЁН"),"B7");}
            case "DRONE"->{if(droneAvailable()&&spend("batteries",1))sendDrone(a.id);else event("СИСТЕМА","Дрон не вылетел: маршрут закрыт или нет батареи.","C1");}
            case "LURE"->{if(spend("parts",1)){var n=new Noise(target,"Приманка",100,state.gameTime+20);state.noises.add(n);event("ГРУППА","Шумовая приманка установлена на 20 секунд.",target);}}
            case "SCAN"->{state.progress.put("scan:"+target,state.gameTime);event("СИСТЕМА","Скан "+target+": биомасса "+Math.round(state.rooms.get(target).biomass)+"%, O2 "+Math.round(state.rooms.get(target).oxygen)+"%",target);}
            case "HEAL"->{var bits=target.split(":");var patient=state.actor(Integer.parseInt(bits[0]));var part=patient.body.get(bits[1]);var kit=a.inventory.get("medkit");
                if(!patient.alive()||kit==null||kit.count<1||a.position().distance(patient.position())>55){event("СИСТЕМА","Лечение отменено: тело утрачено, пациент отошёл или нет аптечки.",a.roomId);return;}
                kit.count--;part.infection=Math.max(0,part.infection-(a.slot==1?18:10));part.injury=Math.max(0,part.injury-25);part.bleeding=0;part.treatments.add("Цикл "+state.cycle+": "+a.id);patient.hp=Math.min(100,patient.hp+15);if(patient.hp>=25)patient.life="ALIVE";
                event("ГРУППА",a.name+": обработана область «"+part.name+"» у "+patient.name,patient.roomId);}
        }
    }
    private void advanceEnvironment(double dt){
        if(state.flags.contains("biofilter_installed"))state.water=Math.min(100,state.water+dt*.04);
        var growth=new LinkedHashMap<String,Double>();
        for(var r:state.rooms.values()){
            if(r.biomass>0&&r.nutrients>0){double g=(state.flags.contains("biofilter_installed")?.008:.035)*(r.humidity/60)*(r.ventilated?1:.25)*dt;r.biomass=Math.min(100,r.biomass+g);r.nutrients=Math.max(0,r.nutrients-g*.8);
                if(r.biomass>55)for(var d:state.doors.values())if(d.passable()&&(d.a.equals(r.id)||d.b.equals(r.id))){var next=state.rooms.get(d.other(r.id));if(next.nutrients>0)growth.merge(next.id,.018*dt,Double::sum);}}
            r.oxygen=Math.max(0,Math.min(100,r.oxygen+(r.ventilated&&state.ventilation>40?.004:-.025)*dt-r.biomass*.00012*dt));
        }
        growth.forEach((id,n)->state.rooms.get(id).biomass=Math.min(100,state.rooms.get(id).biomass+n));
        if(state.currentAct!=1)return;
        if(state.gameTime>15&&state.rooms.get("B3").powered&&state.power>20&&state.rooms.get("B3").biomass>.05&&state.triggeredEvents.add("biomass_alert"))event("УГРОЗА","Датчик B3: повышена биологическая активность. Возможен рост из B4.","B4");
        if(state.rooms.get("B3").powered&&state.power>20&&state.rooms.get("B3").biomass>.05){double level=Math.floor(state.rooms.get("B4").biomass),previous=state.progress.getOrDefault("sensor:biomass:B4",level);if(level>previous)event("УГРОЗА","Датчик B3 / сектор B4: биомасса "+(int)previous+"% → "+(int)level+"%.","B4");state.progress.put("sensor:biomass:B4",level);}
    }
    private void advanceCreatures(double dt){
        for(var c:state.creatures){c.attackCooldown=Math.max(0,c.attackCooldown-dt);
            if(state.currentAct>=2&&c.recoveryUntil>state.gameTime){c.state="RECOVERING";
                if(Pathfinder.blocked(state,c.pathDoors)){c.path.clear();c.pathDoors.clear();}
                var rest=Pathfinder.advance(c.positionForDefense(),c.path,24*dt);c.x=rest.x();c.y=rest.y();String owner=Pathfinder.ownerAt(state,c.x,c.y);if(!owner.isEmpty())c.roomId=owner;continue;}
            if(c.suppressedUntil>state.gameTime){c.state="SUPPRESSED";continue;}
            if(state.currentAct==4&&c.type.equals("ГОНЧАЯ")&&state.progress.containsKey("hunter_stairs")){c.state="CLIMBING";continue;}
            if(c.state.equals("SUPPRESSED")){c.state="SEARCH";event("УГРОЗА",c.type+": движение возобновилось. Средство сдерживания больше не действует.",c.roomId);}
            var candidates=state.actors.stream().filter(Actor::alive).filter(a->!state.flags.contains("attuned_"+a.slot)).filter(a->a.roomId.equals(c.roomId)).filter(a->!a.hidden||a.position().distance(new Point(c.x,c.y))<28).sorted(Comparator.comparingDouble(a->a.position().distance(new Point(c.x,c.y)))).toList();
            Actor seenActor=candidates.isEmpty()?null:candidates.getFirst();
            if(c.type.equals("ШЕПТУН")&&seenActor!=null&&seenActor.flashlight){seenActor=null;c.state="RETREAT";c.targetRoom=c.home;}
            if(seenActor!=null){if(state.currentAct>=2){c.exertion+=dt*2;if(c.exertion>=100){recover(c);continue;}}if(state.triggeredEvents.add("chase:"+seenActor.id+":"+c.id))event("УГРОЗА",c.type+" преследует "+seenActor.name+". Бег, закрытый проход и оружие дадут время для отхода.",c.roomId);c.state="CHASE";c.targetRoom=seenActor.roomId;c.searchTime=8;
                if(new Point(c.x,c.y).distance(seenActor.position())<16&&c.attackCooldown==0){c.state="ATTACK";c.attackCooldown=4.5;if(state.currentAct>=2)c.exertion+=18;seenActor.stress=Math.min(100,seenActor.stress+12);seenActor.hp=Math.max(0,seenActor.hp-(c.type.equals("ГОНЧАЯ")?12:8));var part=seenActor.body.get("leftLeg");part.injury=Math.min(100,part.injury+8);part.bleeding=Math.min(30,part.bleeding+1);event("УГРОЗА",c.type+" атакует "+seenActor.name+". Повреждена левая нога.",c.roomId);}
                var route=Pathfinder.find(state,c.roomId,new Point(c.x,c.y),seenActor.roomId,seenActor.position());if(route!=null){c.path=route.points();c.pathDoors=route.doors();}
            }else{
                c.exertion=Math.max(0,c.exertion-dt*.1);
                if(DefenseSystem.breach(this,c,dt))continue;
                if(c.searchTime>0)c.searchTime-=dt;
                Noise best=null;double loudest=0;
                for(var n:state.noises){var route=Pathfinder.find(state,c.roomId,new Point(c.x,c.y),n.roomId,state.rooms.get(n.roomId).center());if(route==null)continue;double loudness=n.strength/(1+route.doors().size()*1.5);if(loudness>loudest&&loudness>12){best=n;loudest=loudness;}}
                if(best!=null){c.state="INVESTIGATE_SOUND";if(!best.roomId.equals(c.targetRoom)||c.path.isEmpty()){c.targetRoom=best.roomId;var r=Pathfinder.find(state,c.roomId,new Point(c.x,c.y),best.roomId,state.rooms.get(best.roomId).center());c.path=r.points();c.pathDoors=r.doors();c.searchTime=5;}}
                else if(c.path.isEmpty()&&c.searchTime<=0){c.state="PATROL";c.targetRoom=c.home;if(!c.roomId.equals(c.home)){var r=Pathfinder.find(state,c.roomId,new Point(c.x,c.y),c.home,state.rooms.get(c.home).center());if(r!=null){c.path=r.points();c.pathDoors=r.doors();}}}
            }
            if(Pathfinder.blocked(state,c.pathDoors)){c.path.clear();c.pathDoors.clear();c.state="LOST_TARGET_SEARCH";}
            var p=Pathfinder.advance(new Point(c.x,c.y),c.path,(c.type.equals("ГОНЧАЯ")?34:18)*dt);c.x=p.x();c.y=p.y();String room=Pathfinder.ownerAt(state,c.x,c.y);if(!room.isEmpty())c.roomId=room;
        }
    }
    private void recover(Creature c){c.exertion=0;c.recoveryUntil=state.gameTime+35;c.state="RECOVERING";c.path.clear();c.pathDoors.clear();
        var destination=state.doors.values().stream().filter(d->d.passable()&&(d.a.equals(c.roomId)||d.b.equals(c.roomId))).map(d->state.rooms.get(d.other(c.roomId))).filter(r->state.actors.stream().noneMatch(a->a.alive()&&a.roomId.equals(r.id))).findFirst().orElse(state.rooms.get(c.roomId));
        var route=Pathfinder.find(state,c.roomId,c.positionForDefense(),destination.id,destination.center());if(route!=null){c.path=route.points();c.pathDoors=route.doors();}event("УГРОЗА",c.type+": судороги дыхательного мешка. Отходит на восстановление примерно на 35 с. Используйте окно для работы или отступления.",c.roomId);
    }
    public void prepareOptions(){
        if(state.currentAct>=3){CampaignDirector.options(this);return;}
        if(state.currentAct==2){ExpeditionSystem.options(this);return;}
        state.options.clear();
        if(!state.flags.contains("reserve_started")&&!state.flags.contains("power_stable")&&state.power<100)addOption("generator","Запустить резервный генератор","Питание +25%, 1 топлива, шум в B2. Повторный запуск недоступен.","ВЫСОКИЙ","fuel",1);
        if(!state.flags.contains("b4_sealed")&&!state.flags.contains("hive_burned")){
            var seal=addOption("seal_b4","Герметизировать сектор B4","Три шлюза закрываются, вентиляция B4 выключается, рост замедляется.","СРЕДНИЙ","parts",2);
            if(state.power<30){seal.enabled=false;seal.reason="Питание ниже 30%";}
            state.actors.stream().filter(x->x.alive()&&x.roomId.equals("B4")).forEach(x->seal.consentSlots.add(x.slot));
        }
        if(!state.flags.contains("surface_goal")&&!state.flags.contains("exit_ready"))addOption("surface_goal","Искать выход на поверхность","Разведать выход C2, проверить питание подъёмника и назначить маршрут группы.","ВЫСОКИЙ",null,0);
        if(!state.flags.contains("sample_goal")&&!state.flags.contains("hive_core_studied"))addOption("sample_goal","Изучить образец","1 батарея: питание анализатора B7 и разрешение отбора образца. Затем ПКМ по анализатору.","КРИТИЧЕСКИЙ","batteries",1);
        if(state.flags.contains("hive_core_studied")&&!state.flags.contains("biofilter_installed")&&!state.flags.contains("hive_burned")){
            addOption("biofilter","Сохранить Рой как биофильтр","2 фильтра: вода +15 и дальнейшая очистка; рост биомассы замедляется. Улей остаётся жив.","СРЕДНИЙ","filters",2);
            var burn=addOption("burn_hive","Выжечь колонию Роя","2 топлива: биомасса B4/B5/B7 подавлена на 90 с, существа переждут огонь; вода −25. Они вернутся.","ВЫСОКИЙ","fuel",2);
            if(state.actors.stream().anyMatch(x->x.alive()&&Set.of("B4","B5","B7").contains(x.roomId))){burn.enabled=false;burn.reason="Сначала выведите живых героев из B4, B5 и B7";}if(state.npcs.get("lina").location.equals("B5"))burn.description+=" Лина в B5 погибнет.";
        }
        if(state.flags.contains("b4_sealed")&&state.ventilation<60)addOption("air_purge","Очистить вентиляционный контур","1 фильтр: вентиляция +20%, кислород +12%, биомасса в продуваемых секторах −5%.","СРЕДНИЙ","filters",1);
        if(state.flags.contains("archive_access")&&!state.flags.contains("expedition_supplies"))addOption("exit_supplies","Подготовить запасы экспедиции","2 пайка обмениваются на 2 фильтра и 1 аптечку каждому участнику. Приоритет — выход C2.","НИЗКИЙ","food",2);
        if(state.cycle>=2&&!state.flags.contains("lina_decided")){
            state.options.clear();
            addOption("lina_admit","Впустить человека за дверью","Лина знает путь к станции; на одежде споры.","ВЫСОКИЙ",null,0);
            addOption("lina_quarantine","Карантин для Лины","Один фильтр, питание ≥30%; безопасное обследование.","СРЕДНИЙ","filters",1);
            if(state.power<30){state.options.getLast().enabled=false;state.options.getLast().reason="Нужно питание 30%";}
            addOption("lina_refuse","Отказать в доступе","Лина уйдёт к Собирателям; доступ к станции придётся искать в архиве.","НЕИЗВЕСТЕН",null,0);
            addOption("lina_drone","Отправить дрон","Батарея, отсрочка решения; получить подтверждение состояния Лины.","СРЕДНИЙ","batteries",1);
        }
        if(state.flags.contains("hive_core_studied")&&(state.flags.contains("lina_decided")||state.cycle<2)){var priority=List.of("biofilter","burn_hive","exit_supplies","air_purge","seal_b4","generator","surface_goal");state.options.sort(Comparator.comparingInt(o->{int i=priority.indexOf(o.id);return i<0?99:i;}));}
        if(state.options.size()>4)state.options.subList(4,state.options.size()).clear();
        addOption("defer","Отложить решение","Без большинства: безопасная отсрочка, расход 1 воды; улей продолжит рост в новом цикле.","НИЗКИЙ",null,0);
        state.options.stream().filter(o->o.id.equals("lina_drone")&&!droneAvailable()).forEach(o->{o.enabled=false;o.reason="Откройте D13: дрону нужен реальный проход C1 → C2";});
    }
    private Option addOption(String id,String title,String desc,String risk,String resource,int amount){var o=new Option(id,title,desc,risk);if(resource!=null){o.cost.put(resource,amount);if(state.supplies.getOrDefault(resource,0)<amount){o.enabled=false;o.reason="Недостаточно ресурса: "+resource;}}state.options.add(o);return o;}
    public void resolveVote(){
        if(state.phase!=Phase.VOTING)return;String resolution=state.campaignId+":"+state.cycle;
        if(state.decisions.stream().anyMatch(d->d.resolutionId.equals(resolution)))return;
        var counts=new LinkedHashMap<String,Integer>();state.ballots.values().forEach(v->counts.merge(v,1,Integer::sum));String winner=counts.entrySet().stream().filter(e->e.getValue()>=2).map(Map.Entry::getKey).findFirst().orElse("defer");
        String proposed=winner;var option=state.options.stream().filter(o->o.id.equals(proposed)).findFirst().orElse(null);
        if(option==null||!option.enabled||option.cost.entrySet().stream().anyMatch(e->state.supplies.getOrDefault(e.getKey(),0)<e.getValue())||option.consentSlots.stream().anyMatch(i->!state.actor(i).consent)){
            winner="defer";event("СИСТЕМА","Решение отложено: условия или личное согласие не выполнены.","");
        }
        var suppliesBefore=new LinkedHashMap<>(state.supplies);state.phase=Phase.RESOLVE;
        if(!winner.equals("defer")){var finalWinner=winner;state.options.stream().filter(o->o.id.equals(finalWinner)).findFirst().ifPresent(o->o.cost.forEach((r,n)->state.supplies.merge(r,-n,Integer::sum)));}
        double powerBefore=state.power,waterBefore=state.water,bioBefore=state.biomass();applyDecision(winner);var d=new Decision();d.resolutionId=resolution;d.optionId=winner;d.cycle=state.cycle;d.ballots.putAll(state.ballots);
        String committedTitle=winner;d.title=state.options.stream().filter(o->o.id.equals(committedTitle)).map(o->o.title).findFirst().orElse("Отложить решение");String costs=suppliesBefore.entrySet().stream().filter(entry->!entry.getValue().equals(state.supplies.get(entry.getKey()))).map(entry->resourceTitle(entry.getKey())+" "+entry.getValue()+"→"+state.supplies.get(entry.getKey())).collect(java.util.stream.Collectors.joining(", "));d.consequence=state.objective+" / "+(costs.isEmpty()?"":costs+"; ")+"питание "+Math.round(powerBefore)+"→"+Math.round(state.power)+"; вода "+Math.round(waterBefore)+"→"+Math.round(state.water)+"; биомасса "+Math.round(bioBefore)+"→"+Math.round(state.biomass());state.decisions.add(d);event("СИСТЕМА","ПОСЛЕДСТВИЯ: "+d.consequence,"");
        String committed=winner;String title=state.options.stream().filter(o->o.id.equals(committed)).map(o->o.title).findFirst().orElse("Отложить решение");
        event("СИСТЕМА","РЕШЕНИЕ ПРИНЯТО: "+title+" ["+counts.getOrDefault(winner,0)+"/3]. Цикл завершён.", winner.equals("seal_b4")?"B4":"");
        state.cycle++;state.remaining=60;state.phase=Phase.ACTION;state.ballots.clear();state.actors.forEach(a->a.consent=false);prepareOptions();
    }
    private void applyDecision(String id){if(state.currentAct>=3){CampaignDirector.decision(this,id);return;}if(state.currentAct==2){ExpeditionSystem.decision(this,id);return;}switch(id){
        case "seal_b4"->{state.doors.values().stream().filter(d->d.a.equals("B4")||d.b.equals("B4")).forEach(d->d.status=DoorStatus.SEALED);state.rooms.get("B4").ventilated=false;state.ventilation=Math.max(0,state.ventilation-8);state.flags.add("b4_sealed");state.objective="B4 изолирован. Проверьте воздух B3 и найдите архив B6.";}
        case "generator"->{state.flags.add("reserve_started");state.power=Math.min(100,state.power+25);state.noises.add(new Noise("B2","Генератор",95,state.gameTime+35));state.objective="Резерв работает временно. Восстановите основной щит B2.";}
        case "surface_goal"->{state.flags.add("surface_goal");state.discoveredRegions.add("sigma");state.flags.add("exit_surveyed");state.objective="Выход C2 разведан. Получите ключ в B6, откройте D13 и подготовьте подъёмник через ПКМ.";}
        case "sample_goal"->{state.flags.add("sample_goal");state.rooms.get("B7").powered=true;state.objective="Анализатор B7 запитан от выделенной батареи. ПКМ по нему: отобрать образец, затем провести анализ.";}
        case "biofilter"->{state.flags.add("biofilter_installed");state.water=Math.min(100,state.water+15);state.rooms.values().forEach(r->r.biomass=Math.max(0,r.biomass-5));state.objective="Биофильтр подключён: вода поступает, рост Роя замедлен. Подготовьте подъёмник C2.";}
        case "burn_hive"->{state.flags.add("hive_burned");for(String room:List.of("B4","B5","B7")){state.rooms.get(room).biomass=0;state.rooms.get(room).nutrients=0;}for(var c:state.creatures)if(Set.of("B4","B5","B7").contains(c.roomId))DefenseSystem.suppress(this,c,90);for(String r:List.of("B4","B5","B7"))state.progress.put("burn_recover:"+r,state.gameTime+90);state.water=Math.max(0,state.water-25);if(state.npcs.get("lina").location.equals("B5")){state.npcs.get("lina").condition="DEAD";state.flags.add("lina_lost");}state.objective="Огонь подавил колонию на 90 секунд. Твари вернутся; вода потеряна. Используйте время для отхода.";}
        case "air_purge"->{state.ventilation=Math.min(100,state.ventilation+20);state.rooms.values().stream().filter(r->r.ventilated).forEach(r->{r.oxygen=Math.min(100,r.oxygen+12);r.biomass=Math.max(0,r.biomass-5);});state.objective="Контур продут: воздух восстановлен, биомасса в вентиляции уменьшена.";}
        case "exit_supplies"->{state.flags.add("expedition_supplies");state.supplies.merge("filters",2,Integer::sum);state.actors.stream().filter(a->!a.life.equals("DORMANT")).forEach(a->a.inventory.get("medkit").count++);state.objective="Запасы экспедиции выданы. Откройте D13, подготовьте подъёмник и поднимитесь каждым героем.";}
        case "lina_admit"->{state.flags.add("lina_decided");var n=state.npcs.get("lina");n.condition="ALLY";n.location="A2";n.trust=20;state.objective="Лина допущена в медблок: на одежде споры. Обеззаразьте A2 через медицинский шкаф.";state.rooms.get("A2").biomass+=8;state.discoveredRegions.add("station47");event("ГРУППА","ЛИНА: Я видела ваши позывные на старой станции. Вы уже выходили отсюда. На моей куртке споры — не подходите.","A2");}
        case "lina_quarantine"->{state.flags.add("lina_decided");state.flags.add("lina_safe");var n=state.npcs.get("lina");n.condition="QUARANTINE";n.location="B5";n.trust=5;state.objective="Лина помещена в B5. После анализа образца завершите её карантин через ПКМ по камере.";state.discoveredRegions.add("station47");event("ГРУППА","ЛИНА: Только не выключайте воздух. Я расскажу о станции, когда вы принесёте анализ.","B5");}
        case "lina_refuse"->{state.flags.add("lina_decided");state.npcs.get("lina").condition="LEFT";state.npcs.get("lina").location="SURFACE";state.npcs.get("lina").trust=-20;state.objective="Лина ушла к Собирателям. Координаты станции придётся восстановить самостоятельно.";event("ГРУППА","ЛИНА: Я уйду. Если найдёте станцию — не говорите, что никто не просил о помощи.","C2");}
        case "lina_drone"->{sendDrone("GROUP");state.objective="Разведывательный дрон следует к C2. Подтверждённые данные появятся после прибытия.";}
        default->state.water=Math.max(0,state.water-1);
    }}
    private boolean droneAvailable(){return state.drones.stream().anyMatch(d->d.state.equals("DOCKED")&&d.battery>15)&&Pathfinder.find(state,"C1",state.rooms.get("C1").center(),"C2",state.rooms.get("C2").center())!=null;}
    private void sendDrone(String owner){var d=state.drones.stream().filter(x->x.state.equals("DOCKED")&&x.battery>15).findFirst().orElse(null);if(d==null)return;var route=Pathfinder.find(state,d.roomId,new Point(d.x,d.y),"C2",state.rooms.get("C2").center());if(route==null)return;d.owner=owner;d.state="SCOUT";d.destination="C2";d.path=route.points();d.pathDoors=route.doors();event("СИСТЕМА","DR-01: маршрут C1 → C2 подтверждён. Начата разведка.","C1");}
    private void advanceDrones(double dt){for(var d:state.drones){if(d.state.equals("DOCKED")||d.state.equals("LOST"))continue;d.battery=Math.max(0,d.battery-dt*.12);if(d.battery==0){d.state="LOST";d.path.clear();event("УГРОЗА","DR-01: батарея исчерпана. Дрон потерян в "+d.roomId,d.roomId);continue;}if(state.power<25||Pathfinder.blocked(state,d.pathDoors)){d.state="NO_LINK";continue;}if(d.state.equals("NO_LINK"))d.state=d.destination.equals("C2")?"SCOUT":"RETURN";
            var p=Pathfinder.advance(new Point(d.x,d.y),d.path,42*dt);d.x=p.x();d.y=p.y();String room=Pathfinder.ownerAt(state,d.x,d.y);if(!room.isEmpty())d.roomId=room;
            if(d.path.isEmpty()&&d.state.equals("SCOUT")){var npc=state.npcs.get("lina");if(npc.location.equals("C2")&&!state.flags.contains("lina_decided")){state.flags.add("lina_scanned");npc.condition="SCANNED";event("СИСТЕМА","DR-01 / C2: Лина жива, одежда заражена. Второй источник движения снаружи.","C2");}else event("СИСТЕМА","DR-01 / C2: Лины у шлюза нет. Подтверждён внешний источник движения.","C2");var route=Pathfinder.find(state,d.roomId,p,"C1",state.rooms.get("C1").center());if(route!=null){d.path=route.points();d.pathDoors=route.doors();d.destination="C1";d.state="RETURN";}}
            else if(d.path.isEmpty()&&d.state.equals("RETURN")){d.state="DOCKED";event("СИСТЕМА","DR-01: возвращён на базу, заряд "+Math.round(d.battery)+"%","C1");}
        }}
    public Campaign view(int slot){
        var v=Json.copy(state,Campaign.class);var own=state.actor(slot);
        Set<String> visible=new HashSet<>();
        if(own.life.equals("ECHO")){String camera=EchoRules.cameraRoom(state,own);if(!camera.isEmpty())visible.add(camera);}
        else{visible.add(own.roomId);for(var d:state.doors.values())if(d.passable()&&(d.a.equals(own.roomId)||d.b.equals(own.roomId)))visible.add(d.other(own.roomId));if(state.currentAct==1&&state.power>=50)state.rooms.values().stream().filter(r->r.powered).forEach(r->visible.add(r.id));}
        v.creatures.removeIf(c->!visible.contains(c.roomId));
        for(var c:v.creatures){var contact=new Contact();contact.id=c.id;contact.roomId=c.roomId;contact.type=c.type;contact.behavior=c.state;contact.x=c.x;contact.y=c.y;contact.observedAt=state.gameTime;contact.suppressedUntil=c.suppressedUntil;contact.immuneUntil=c.immuneUntil;contact.certainty="CONFIRMED";state.contacts.put(slot+":"+c.id,contact);}
        v.contacts.clear();state.contacts.forEach((k,c)->{if(k.startsWith(slot+":")){var copy=Json.copy(c,Contact.class);if(v.creatures.stream().noneMatch(cn->cn.id.equals(copy.id)))copy.certainty="LAST_KNOWN";v.contacts.put(c.id,copy);}});
        for(var a:v.actors)if(a.slot!=slot)a.privateEvidence.clear();
        v.rngState=0;v.commandReceipts.clear();v.visitedLocations.clear();return v;
    }
    private static String resourceTitle(String id){return switch(id){case "batteries"->"батареи";case "fuel"->"топливо";case "parts"->"детали";case "filters"->"фильтры";case "food"->"пайки";case "meds"->"медикаменты";default->id;};}
    private boolean spend(String resource,int n){if(state.supplies.getOrDefault(resource,0)<n)return false;state.supplies.merge(resource,-n,Integer::sum);return true;}
    private void noise(Actor a,double strength){var old=state.noises.stream().filter(n->n.source.equals(a.id)).findFirst().orElse(null);if(old==null)state.noises.add(new Noise(a.roomId,a.id,strength,state.gameTime+1));else{old.roomId=a.roomId;old.strength=strength;old.expires=state.gameTime+1;}}
    public static String actionTitle(String a){return switch(a){case "REPAIR"->"Ремонт щита";case "FILTER"->"Замена фильтра";case "CAPSULES"->"Осмотр капсул";case "ARCHIVE"->"Чтение архива";case "SAMPLE"->"Анализ образца";case "POWER"->"Переключение контура B7";case "LOOT"->"Обыск склада";case "SCAN"->"Сканирование";case "DRONE"->"Разведка дроном";case "HEAL"->"Лечение";case "LURE"->"Установка приманки";default->a;};}
    private static Result ok(){return new Result(true,"");}private static Result no(String s){return new Result(false,s);}
}
