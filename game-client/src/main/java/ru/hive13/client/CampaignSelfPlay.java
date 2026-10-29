package ru.hive13.client;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import ru.hive13.model.*;
import ru.hive13.server.*;
import static ru.hive13.model.Model.*;

/** Explicit accelerated QA. Uses only ordinary authenticated TCP commands: no teleports,
 * inventory grants, cleared monsters, forced decisions or edited campaign prerequisites. */
final class CampaignSelfPlay {
    private static final class BodyLost extends Exception {BodyLost(String text){super(text);}}
    private final GameServer server;private final Map<Integer,NetworkClient> peers;private final Path output;
    private final Consumer<String> screenshot;private final String branch;
    private final Map<String,CompletableFuture<Packet>> acknowledgements=new ConcurrentHashMap<>();
    private final List<Map<String,Object>> trace=new ArrayList<>();private final Set<Integer> voted=new HashSet<>();
    private double nextEchoDecoy;
    private String finalRoute="exodus",sourceCheckpoint="";
    public void checkpointUsed(String path){sourceCheckpoint=path;}
    private final long started=System.nanoTime();private int photo;
    CampaignSelfPlay(GameServer server,Map<Integer,NetworkClient> peers,Path output,Consumer<String> screenshot,String branch){
        this.server=server;this.peers=peers;this.output=output;this.screenshot=screenshot;this.branch=branch;
        if(!Set.of("biofilter","burn_hive").contains(branch))throw new IllegalArgumentException("branch");
        for(var peer:peers.values())peer.listener=p->{if(p.type.equals("ACK")){var future=acknowledgements.get(p.id);if(future!=null)future.complete(p);}};
    }
    private Campaign state()throws Exception{return server.inspect().get(3,TimeUnit.SECONDS);}
    private int physicalSlot(int preferred){return peers.containsKey(preferred)?preferred:peers.keySet().stream().filter(slot->slot!=1).findFirst().orElse(1);}
    private boolean send(int slot,String type,Map<String,?> data)throws Exception{
        slot=physicalSlot(slot);
        String id=UUID.randomUUID().toString();var future=new CompletableFuture<Packet>();acknowledgements.put(id,future);
        peers.get(slot).sendPacket(new Packet(type,id,data));var p=future.get(4,TimeUnit.SECONDS);acknowledgements.remove(id);
        var s=state();trace.add(Map.of("type",type,"slot",slot,"data",data,"accepted",p.data.path("accepted").asBoolean(),"reason",p.text("reason"),"cycle",s.cycle,"gameTime",s.gameTime));flush();
        if(!p.data.path("accepted").asBoolean()){
            String reason=p.text("reason");
            if(reason.startsWith("Действия доступны только")&&Set.of("MOVE","INTERACT","HEAL","FIRE").contains(type))return false;
            // A live operator can cross the coverage boundary before a support command arrives.
            if(type.equals("ECHO_ACTION")&&List.of("Помещение вне зоны","Терминал","Сначала подключите","Маршрут живого","Связь потеряна","Нет подключённого").stream().anyMatch(reason::startsWith))return false;
            // A moving target can leave range between the snapshot and authority processing.
            // Keep the rejected attempt in the trace; it never counts as a successful shot.
            if(type.equals("FIRE")&&List.of("Далеко:","Линию огня", "Контакт", "Тварь адаптировалась", "Уже подавлен", "Оружие готовится", "Временно сопротивляется", "Действия доступны").stream().anyMatch(reason::startsWith))return false;
            if(type.equals("INTERACT")&&"CLOSE".equals(data.get("action"))&&reason.startsWith("Подойдите к панели"))return false;
            if(reason.startsWith("Эхо:"))throw new BodyLost(type+" / "+slot+" / "+reason);throw new IllegalStateException(type+" "+data+": "+reason);
        }
        return true;
    }
    private void flush()throws Exception{Files.write(output.resolve("command-trace.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(trace));}
    private void voteIfNeeded(Campaign s)throws Exception{
        if(s.phase!=Phase.VOTING||!voted.add(s.cycle))return;
        double time=s.gameTime,bio=s.biomass();Thread.sleep(180);var frozen=state();check(time==frozen.gameTime&&bio==frozen.biomass(),"world frozen during vote");
        var preferred=s.currentAct>=3?List.of("hydro_keep","route_"+finalRoute,"defer"):s.currentAct==2?List.of("rescue_authorized","archive_authorized","surface_air","defer"):List.of("lina_quarantine",s.flags.contains("hive_core_studied")?branch:"sample_goal","seal_b4","exit_supplies","surface_goal","generator","defer");
        String choice=preferred.stream().filter(id->s.options.stream().anyMatch(o->o.id.equals(id)&&o.enabled)).findFirst().orElse("defer");
        for(int slot:peers.keySet())send(slot,"CONSENT",Map.of("consent",true));
        for(int slot:peers.keySet())send(slot,"VOTE",Map.of("cycle",s.cycle,"option",choice));
        System.out.println("SELFPLAY vote cycle="+s.cycle+" choice="+choice);
    }
    private Campaign waitFor(Predicate<Campaign> condition,String label,double maxSeconds)throws Exception{
        long until=System.nanoTime()+(long)(maxSeconds*1e9);
        while(System.nanoTime()<until){var s=state();if(condition.test(s))return s;if(s.currentAct>=2&&s.phase==Phase.ACTION){try{defend(s);}catch(BodyLost lost){trace.add(Map.of("bodyLostDuringDefense",lost.getMessage()));flush();}s=state();}if(condition.test(s))return s;
            if(s.phase==Phase.GAME_OVER)throw new IllegalStateException("Premature ending / "+label+": "+s.ending);
            voteIfNeeded(s);Thread.sleep(35);
        }throw new IllegalStateException("Timeout: "+label);
    }
    private void action()throws Exception{waitFor(s->s.phase==Phase.ACTION&&s.remaining>3,"action phase",18);}
    private void idle(int slot,String label)throws Exception{int actor=physicalSlot(slot);waitFor(s->{var a=s.actor(actor);return a.path.isEmpty()&&a.queue.isEmpty()&&a.task.isEmpty();},label,25);}
    private void door(int slot,String id,String operation)throws Exception{slot=physicalSlot(slot);action();send(slot,"USE_DOOR",Map.of("target",id,"action",operation));idle(slot,id);check(state().doors.get(id).passable()==operation.equals("OPEN"),id+" operation");}
    private void routeTo(int slot,WorldObjects.Fixture f)throws Exception{
        slot=physicalSlot(slot);
        for(int attempt=0;attempt<12;attempt++){
            action();var s=state();var a=s.actor(slot);if(!a.alive())throw new BodyLost("route / "+slot);if(s.currentAct>=3&&CampaignPlan.floor(s,a.roomId)!=CampaignPlan.floor(s,f.room())){chapterUse(slot,CampaignPlan.floor(s,a.roomId)==0?"stairs_up":"stairs_down",CampaignPlan.floor(s,a.roomId)==0?"STAIRS_UP":"STAIRS_DOWN");continue;}if(Pathfinder.find(s,a.roomId,a.position(),f.room(),f.position())!=null)return;
            var previous=new LinkedHashMap<String,Door>();var queue=new ArrayDeque<String>();queue.add(a.roomId);var seen=new HashSet<String>();seen.add(a.roomId);
            while(!queue.isEmpty()){String room=queue.remove();if(room.equals(f.room()))break;for(var d:s.doors.values())if(d.status!=DoorStatus.SEALED&&(d.status!=DoorStatus.LOCKED||s.flags.contains("archive_access"))&&(d.a.equals(room)||d.b.equals(room))&&seen.add(d.other(room))){previous.put(d.other(room),d);queue.add(d.other(room));}}
            if(!seen.contains(f.room()))throw new IllegalStateException("No legal room path to "+f.id());
            var path=new ArrayList<Door>();String cursor=f.room();while(!cursor.equals(a.roomId)){var d=previous.get(cursor);path.add(d);cursor=d.other(cursor);}Collections.reverse(path);
            var blocked=path.stream().filter(d->!d.passable()).findFirst().orElseThrow();door(slot,blocked.id,"OPEN");
        }throw new IllegalStateException("Cannot approach "+f.id());
    }
    private void use(int slot,String id,String operation)throws Exception{
        slot=physicalSlot(slot);
        if(state().currentAct==2){
            if(operation.equals("FIELD_EVAC")&&!state().actor(slot).alive())return;
            if(Set.of("GRID_RESET","CARAVAN_RESCUE","FIELD_SUPPLY","MEDICINE","SIGMA_TUNE","SCIENTIST","STATION_ARCHIVE").contains(operation)&&surfaceComplete(state(),slot,operation))return;
            for(int attempt=0;attempt<peers.size();attempt++)try{int worker=worker(slot);useSurface(worker,id,operation);return;}catch(BodyLost lost){trace.add(Map.of("surfaceBodyLost",lost.getMessage(),"operation",operation));flush();}
            throw new IllegalStateException("No physical operator for "+operation);
        }
        useSurface(slot,id,operation);
    }
    private int worker(int preferred)throws Exception{var s=state();if(peers.containsKey(preferred)&&s.actor(preferred).alive())return preferred;return s.actors.stream().filter(a->a.alive()&&peers.containsKey(a.slot)).max(Comparator.comparingDouble(a->a.hp)).map(a->a.slot).orElseThrow(()->new IllegalStateException("No living field operator"));}
    private void useSurface(int slot,String id,String operation)throws Exception{
        var f=WorldObjects.get(state(),id);if(state().currentAct==2){if(!operation.equals("FIELD_EVAC")){int cover=state().actors.stream().filter(a->a.alive()&&a.slot!=slot&&peers.containsKey(a.slot)).mapToInt(a->a.slot).findFirst().orElse(slot);if(cover!=slot){routeTo(cover,f);action();send(cover,"MOVE",Map.of("x",f.x()-12,"y",f.y(),"mode","RUN"));}}routeTo(slot,f);action();send(slot,"MOVE",Map.of("x",f.x(),"y",f.y(),"mode","RUN"));idle(slot,"run and cover before operation");}for(int attempt=0;attempt<8;attempt++){routeTo(slot,f);action();send(slot,"USE_OBJECT",Map.of("target",id,"action",operation));idle(slot,id+" / "+operation);if(state().currentAct!=2||surfaceComplete(state(),slot,operation))break;if(attempt==7)throw new IllegalStateException("Operation repeatedly interrupted: "+operation);}
        var s=state();trace.add(Map.of("completed",operation,"object",id,"slot",slot,"gameTime",s.gameTime,"room",s.actor(slot).roomId,"supplies",new LinkedHashMap<>(s.supplies)));flush();System.out.println("SELFPLAY completed "+slot+" "+id+" / "+operation);
    }
    private boolean surfaceComplete(Campaign s,int slot,String action){String flag=switch(action){case "GRID_RESET"->"surface_grid";case "CARAVAN_RESCUE"->"caravan_saved";case "FIELD_SUPPLY"->"surface_store";case "MEDICINE"->"surface_medicine";case "SIGMA_TUNE"->"sigma_online";case "SCIENTIST"->"scientist_saved";case "STATION_ARCHIVE"->"station_archive_found";default->"";};return action.equals("FIELD_EVAC")?s.actor(slot).life.equals("EVACUATED"):flag.isEmpty()||s.flags.contains(flag);}
    private void defend(Campaign s)throws Exception{if(s.remaining<2)return;if(echoSupport(s))return;
        for(var peer:peers.entrySet()){var a=s.actor(peer.getKey());if(!a.alive())continue;var view=peer.getValue().state;if(view==null)continue;
            if(!s.flags.contains("attuned_"+a.slot)&&a.hp<(s.currentAct>=3?40:80)&&a.path.isEmpty()&&view.contacts.values().stream().anyMatch(c->"CONFIRMED".equals(c.certainty)&&c.roomId.equals(a.roomId)&&new Point(c.x,c.y).distance(a.position())<100)){
                var escape=s.doors.values().stream().filter(d->d.passable()&&(d.a.equals(a.roomId)||d.b.equals(a.roomId))).map(d->s.rooms.get(d.other(a.roomId))).filter(r->view.contacts.values().stream().noneMatch(c->"CONFIRMED".equals(c.certainty)&&c.roomId.equals(r.id))).filter(r->Pathfinder.find(s,a.roomId,a.position(),r.id,r.center())!=null).min(Comparator.comparingDouble(r->r.center().distance(a.position()))).orElse(null);
                if(escape!=null){send(peer.getKey(),"MOVE",Map.of("x",escape.center().x(),"y",escape.center().y(),"mode","RUN"));return;}
            }
            for(var d:s.doors.values()){if(s.flags.contains("attuned_"+a.slot)||!a.path.isEmpty()||!d.passable()||!(d.a.equals(a.roomId)||d.b.equals(a.roomId)))continue;String outside=d.other(a.roomId);if(view.contacts.values().stream().noneMatch(c->"CONFIRMED".equals(c.certainty)&&c.roomId.equals(outside)))continue;if(s.actors.stream().anyMatch(b->b.alive()&&(b.roomId.equals(outside)||!b.path.isEmpty()&&b.pathDoors.contains(d.id))))continue;if(Math.min(a.position().distance(d.pa),a.position().distance(d.pb))<=45){send(peer.getKey(),"INTERACT",Map.of("target",d.id,"action","CLOSE"));return;}}
            if(a.hp<75&&a.task.isEmpty()&&a.path.isEmpty()&&DefenseRules.count(a,"medkit")>0&&view.contacts.values().stream().noneMatch(c->!s.flags.contains("attuned_"+a.slot)&&"CONFIRMED".equals(c.certainty)&&!"RECOVERING".equals(c.behavior)&&!(c.type.equals("ШЕПТУН")&&a.flashlight)&&new Point(c.x,c.y).distance(a.position())<140&&DefenseRules.lineOfFire(view,a.position(),new Point(c.x,c.y)))){var part=a.body.values().stream().filter(p->p.injury>0||p.bleeding>0).max(Comparator.comparingDouble(p->p.injury+p.bleeding*2)).orElse(null);if(part!=null){send(peer.getKey(),"HEAL",Map.of("recipient",peer.getKey(),"part",part.id));return;}}
        }
        var ordered=new ArrayList<>(peers.entrySet());ordered.sort(Comparator.comparingInt(p->{var a=s.actor(p.getKey());return a.task.isEmpty()&&a.path.isEmpty()?0:1;}));for(var peer:ordered){var view=peer.getValue().state;if(view==null)continue;var a=view.actor(peer.getKey());if(!a.alive())continue;for(var c:view.contacts.values())for(String id:c.type.equals("ШЕПТУН")?List.of("flaregun","shotgun","stunner"):List.of("shotgun","stunner","flaregun")){var w=DefenseRules.weapon(id);if(s.flags.contains("attuned_"+a.slot)&&s.actors.stream().filter(Actor::alive).allMatch(b->s.flags.contains("attuned_"+b.slot))||"RECOVERING".equals(c.behavior)||c.type.equals("ШЕПТУН")&&a.flashlight)continue;if(c.suppressedUntil+1>s.gameTime||c.immuneUntil+.7>s.gameTime||a.position().distance(new Point(c.x,c.y))>w.range()*.65)continue;if(DefenseRules.reason(view,a,c,w).isEmpty()){send(peer.getKey(),"FIRE",Map.of("target",c.id,"weapon",id));return;}}}}
    private boolean echoSupport(Campaign s)throws Exception{
        if(s.gameTime<nextEchoDecoy||s.power<40)return false;
        Contact threat=null;for(var peer:peers.values())if(peer.state!=null&&peer.state.actor(peer.slot).alive()){var body=peer.state.actor(peer.slot);threat=peer.state.contacts.values().stream().filter(c->"CONFIRMED".equals(c.certainty)&&!"RECOVERING".equals(c.behavior)&&body.position().distance(new Point(c.x,c.y))<160).findFirst().orElse(null);if(threat!=null)break;}
        if(threat==null)return false;var seen=threat;
        for(int slot:peers.keySet()){var echo=s.actor(slot);if(!echo.life.equals("ECHO")||echo.echoReadyAt>s.gameTime)continue;String focus=EchoRules.cameraRoom(s,echo);
            if(!focus.isEmpty()&&s.actors.stream().noneMatch(a->a.alive()&&a.roomId.equals(focus))&&EchoRules.offer(s,echo,"DECOY",focus).enabled()&&send(slot,"ECHO_ACTION",Map.of("action","DECOY","target",focus))){nextEchoDecoy=s.gameTime+18;return true;}
            var room=EchoRules.coveredRooms(s,echo).stream().map(s.rooms::get).filter(r->s.actors.stream().noneMatch(a->a.alive()&&a.roomId.equals(r.id))).filter(r->Pathfinder.find(s,seen.roomId,new Point(seen.x,seen.y),r.id,r.center())!=null).max(Comparator.comparingDouble(r->s.actors.stream().filter(Actor::alive).mapToDouble(a->a.position().distance(r.center())).min().orElse(0))).orElse(null);
            if(room!=null&&EchoRules.offer(s,echo,"WATCH",room.id).enabled()&&send(slot,"ECHO_ACTION",Map.of("action","WATCH","target",room.id)))return true;
        }return false;
    }
    private void treat()throws Exception{action();var s=state();var medic=s.actor(1);if(!medic.alive()||DefenseRules.count(medic,"medkit")==0)return;for(int slot:peers.keySet()){var patient=s.actor(slot);if(patient.alive()&&patient.hp<85&&medic.position().distance(patient.position())<50){var part=patient.body.values().stream().filter(p->p.injury>0||p.infection>0||p.bleeding>0).max(Comparator.comparingDouble(p->p.injury+p.bleeding*2+p.infection)).orElse(null);if(part!=null){send(1,"HEAL",Map.of("recipient",slot,"part",part.id));idle(1,"cover and heal");return;}}}}
    public void surface()throws Exception{
        if(state().currentAct==1)for(int slot:peers.keySet())send(slot,"CONTINUE_CHAPTER",Map.of());action();check(state().currentAct==2,"real surface transition");shot("SURFACE_START");
        use(0,"field_grid","GRID_RESET");use(1,"caravan","CARAVAN_RESCUE");treat();use(0,"field_store","FIELD_SUPPLY");treat();use(1,"pharmacy","MEDICINE");treat();shot("SURFACE_ACTIVITIES");
        use(0,"sigma","SIGMA_TUNE");shot("SIGMA_CONTACT");waitFor(s->s.flags.contains("rescue_authorized")||s.flags.contains("scientist_lost"),"rescue vote or deadline",30);if(!state().flags.contains("scientist_lost"))try{use(0,"scientist","SCIENTIST");}catch(IllegalStateException failedRescue){if(!state().flags.contains("scientist_lost"))throw failedRescue;trace.add(Map.of("missedRescueDeadline",true));flush();}
        waitFor(s->s.flags.contains("archive_authorized"),"archive vote",30);use(1,"station_archive","STATION_ARCHIVE");shot("STATION_CONSEQUENCES");
        for(int slot:peers.keySet())use(slot,"extraction","FIELD_EVAC");var end=state();check(end.phase==Phase.GAME_OVER&&end.currentAct==2&&!end.campaignComplete,"surface episode completion");check(peers.keySet().stream().allMatch(slot->Set.of("EVACUATED","ECHO").contains(end.actor(slot).life))&&end.actors.stream().anyMatch(a->a.life.equals("EVACUATED")),"remaining bodies returned; Echo preserved");check(end.flags.containsAll(List.of("caravan_saved","ai_lies_proved")),"surface consequences");check(trace.stream().anyMatch(c->"FIRE".equals(c.get("type"))&&Boolean.TRUE.equals(c.get("accepted"))),"actual weapon use against a creature");check(end.creatures.size()==2,"creatures were delayed, never killed");shot("SURFACE_ENDING");
        var save=output.resolve("verified-surface-ending.json");SaveStore.save(save,end);check(SaveStore.load(save).currentAct==2,"surface save roundtrip");var report=new LinkedHashMap<String,Object>();report.put("passed",true);report.put("players",peers.size());report.put("currentAct",end.currentAct);report.put("qaSpeed",end.qaSpeed);report.put("gameSeconds",end.gameTime);report.put("wallSeconds",(System.nanoTime()-started)/1e9);report.put("epilogue",end.epilogue);report.put("flags",end.flags);report.put("creaturesStillInWorld",end.creatures.size());report.put("teleportsOrGrantedItems",false);report.put("fullSixActCampaignCompleted",false);Files.write(output.resolve("surface-result.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));System.out.println("SURFACE PASS: "+peers.size()+" clients");
    }

    public void restored()throws Exception{for(int slot:peers.keySet())send(slot,"READY",Map.of("ready",true));send(1,"START",Map.of());waitFor(s->s.phase!=Phase.LOBBY,"restored state",8);}
    public void inspectChapter()throws Exception{if(state().phase==Phase.GAME_OVER&&state().currentAct<6)nextChapter();}
    private void nextChapter()throws Exception{for(int slot:peers.keySet())send(slot,"CONTINUE_CHAPTER",Map.of());action();shot("ACT_"+state().currentAct+"_START");}
    private void chapterUse(int slot,String id,String operation)throws Exception{
        for(int attempt=0;attempt<4;attempt++)try{chapterUseAttempt(slot,id,operation);return;}catch(BodyLost lost){if(operation.startsWith("STAIRS")||state().actors.stream().noneMatch(Actor::alive))throw lost;trace.add(Map.of("lostBodyWhileWorking",lost.getMessage(),"operation",operation));flush();}
        throw new IllegalStateException("No remaining physical operator for "+operation);
    }
    private void chapterUseAttempt(int slot,String id,String operation)throws Exception{
        slot=physicalSlot(slot);
        var initial=state();var f=WorldObjects.get(initial,id);var op=CampaignPlan.operation(operation);check(f!=null,"fixture "+id);String already=CampaignPlan.doneFlag(initial,op);
        if(!already.isEmpty()&&initial.flags.contains(already)||operation.equals("CH_LEAVE")&&!initial.actor(slot).alive())return;
        if(initial.actor(slot).life.equals("ECHO")&&(operation.startsWith("E_KEY_")||operation.startsWith("E_DECLINE_")||initial.flags.contains("echo_anchor")&&(operation.startsWith("N_SYNC_")||operation.equals("N_ATTUNE")))){
            action();send(slot,"ECHO_INTERFACE",Map.of("action",operation));int owner=slot;waitFor(s->s.actor(owner).task.isEmpty(),"echo terminal "+operation,20);check(already.isEmpty()||state().flags.contains(already),"echo committed "+operation);return;
        }
        if(!initial.actor(slot).alive())slot=initial.actors.stream().filter(Actor::alive).filter(a->peers.containsKey(a.slot)).mapToInt(a->a.slot).max().orElseThrow();
        if(!operation.startsWith("STAIRS")&&!Set.of("CH_LEAVE","CH_KIT","CH_REST","N_SYNC_A","N_SYNC_B","H_VALVE_A","H_VALVE_B","H_PUMP","CH_DEPLOY").contains(operation)){
            int worker=slot;int cover=state().actors.stream().filter(a->a.alive()&&a.slot!=worker&&peers.containsKey(a.slot)).mapToInt(a->a.slot).findFirst().orElse(worker);routeTo(slot,f);if(cover!=worker){routeTo(cover,f);action();send(cover,"MOVE",Map.of("x",f.x()+18,"y",f.y(),"mode","RUN"));}action();send(slot,"MOVE",Map.of("x",f.x(),"y",f.y(),"mode","RUN"));idle(slot,"joint approach");
        }
        for(int attempt=0;attempt<12;attempt++){
            routeTo(slot,f);action();send(slot,"MOVE",Map.of("x",f.x(),"y",f.y(),"mode","RUN"));idle(slot,"approach "+id);action();
            send(slot,"USE_OBJECT",Map.of("target",id,"action",operation));idle(slot,id+" / "+operation);
            var s=state();String flag=CampaignPlan.doneFlag(s,op);boolean done=!flag.isEmpty()?s.flags.contains(flag):operation.equals("CH_LEAVE")?s.actor(slot).life.equals("EVACUATED"):operation.equals("N_ATTUNE")?s.flags.contains("attuned_"+slot):operation.equals("STAIRS_UP")?CampaignPlan.floor(s,s.actor(slot).roomId)==1:operation.equals("STAIRS_DOWN")?CampaignPlan.floor(s,s.actor(slot).roomId)==0:operation.startsWith("H_VALVE")?s.progress.getOrDefault(operation.endsWith("A")?"valve_a_until":"valve_b_until",0d)>s.gameTime:operation.startsWith("N_SYNC")?s.progress.getOrDefault(operation.endsWith("A")?"sync_owner_A":"sync_owner_B",-1d).intValue()==slot:true;
            if(done){trace.add(Map.of("completed",operation,"object",id,"slot",slot,"gameTime",s.gameTime,"supplies",new LinkedHashMap<>(s.supplies)));flush();System.out.println("CHAPTER completed "+s.currentAct+" / "+slot+" / "+operation);if(Set.of("H_SAMPLE","E_ORIGINAL","C_COUNCIL","N_OFFLINE","N_STOP","N_HEAT","N_ATTUNE").contains(operation))shot("ACT_"+s.currentAct+"_"+operation);return;}
        }throw new IllegalStateException("Interrupted chapter operation "+operation);
    }
    private boolean expectedDebrief(Campaign s){return s.phase==Phase.GAME_OVER&&s.actors.stream().anyMatch(a->a.life.equals("EVACUATED"))&&(!s.campaignComplete||s.endingId.equals(finalRoute));}
    private void leaveChapter()throws Exception{
        for(int attempt=0;attempt<4;attempt++)treat();
        for(int slot:peers.keySet()){
            if(expectedDebrief(state()))break;
            try{chapterUse(slot,"chapter_exit","CH_LEAVE");}catch(Exception stopped){if(!expectedDebrief(state()))throw stopped;trace.add(Map.of("completedDebriefDuringEvacuation",true,"slot",slot,"detail",stopped.toString()));flush();}
        }
        check(expectedDebrief(state()),"chapter debrief with evacuation");shot("ACT_"+state().currentAct+"_DEBRIEF");SaveStore.save(output.resolve("verified-act-"+state().currentAct+".json"),state());
    }
    private void standby(int slot,String id)throws Exception{if(!state().actor(slot).alive())return;var f=WorldObjects.get(state(),id);routeTo(slot,f);action();send(slot,"MOVE",Map.of("x",f.x(),"y",f.y(),"mode","RUN"));idle(slot,"standby "+id);}
    private void resupply()throws Exception{resupply(false);}
    private void resupply(boolean ammunitionOnly)throws Exception{
        for(int slot:peers.keySet()){
            if(!state().actor(slot).alive())continue;
            while(state().actor(slot).alive()&&DefenseRules.count(state().actor(slot),"shell")<(ammunitionOnly?2:4)){var current=state();if(!WorldObjects.offer(current,current.actor(slot),WorldObjects.get(current,"camp_armory"),"REARM").enabled())break;use(slot,"camp_armory","REARM");}
            if(ammunitionOnly)continue;
            while(state().actor(slot).alive()&&DefenseRules.count(state().actor(slot),"medkit")<2){var current=state();if(!WorldObjects.offer(current,current.actor(slot),WorldObjects.get(current,"camp_med"),"CH_KIT").enabled())break;chapterUse(slot,"camp_med","CH_KIT");}
            for(int attempt=0;attempt<3;attempt++){var a=state().actor(slot);var part=a.body.values().stream().filter(b->b.injury>0||b.bleeding>0).max(Comparator.comparingDouble(b->b.injury+b.bleeding*10)).orElse(null);if(part==null)break;action();send(slot,"HEAL",Map.of("recipient",slot,"part",part.id));idle(slot,"field treatment");}
            while(state().actor(slot).alive()&&state().actor(slot).hp<90){var current=state();if(!WorldObjects.offer(current,current.actor(slot),WorldObjects.get(current,"camp_med"),"CH_REST").enabled())break;chapterUse(slot,"camp_med","CH_REST");}
        }
    }
    public void full(String route)throws Exception{finalRoute=route;check(CampaignPlan.ROUTES.contains(route),"route");if(state().currentAct==2&&state().phase!=Phase.GAME_OVER)surface();if(state().phase==Phase.GAME_OVER&&state().currentAct<6)nextChapter();
        if(state().currentAct==3){resupply();chapterUse(0,"camp_gate","CH_DEPLOY");chapterUse(0,"chapter_cache","CH_CACHE");resupply(true);standby(1,"valve_b");chapterUse(0,"valve_a","H_VALVE_A");chapterUse(1,"valve_b","H_VALVE_B");chapterUse(0,"hydro_pump","H_PUMP");chapterUse(1,"hydro_sample","H_SAMPLE");chapterUse(0,"eco_archive","H_ARCHIVE");for(int slot:peers.keySet())send(slot,"PERSONAL_CONSENT",Map.of("procedure","CONTACT","consent",true));chapterUse(1,"resonator","H_CONTACT");waitFor(s->s.flags.contains("hydro_policy"),"hydro policy",30);leaveChapter();nextChapter();}
        if(state().currentAct==4){resupply();chapterUse(0,"camp_gate","CH_DEPLOY");chapterUse(0,"chapter_cache","CH_CACHE");resupply(true);chapterUse(0,"eden_register","E_REGISTER");for(int slot:peers.keySet()){send(slot,"PERSONAL_CONSENT",Map.of("procedure","MEMORY","consent",true));chapterUse(slot,"personal_"+slot,"E_KEY_"+slot);}if(peers.size()==2)chapterUse(0,"dormant_archive","E_PROXY");chapterUse(0,"eden_origin","E_ORIGINAL");chapterUse(1,"eva_copy","E_EVA");chapterUse(1,"eva_copy","E_SAVE_EVA");leaveChapter();nextChapter();}
        if(state().currentAct==5){resupply();chapterUse(0,"camp_gate","CH_DEPLOY");chapterUse(1,"clinic","C_CLINIC");chapterUse(0,"chapter_cache","CH_CACHE");resupply(true);chapterUse(0,"kovcheg","C_KOVCHEG");chapterUse(0,"aion","C_AION");chapterUse(1,"followers","C_FOLLOWERS");chapterUse(0,"city_archive","C_DATES");chapterUse(0,"d00","C_D00");chapterUse(1,"council","C_COUNCIL");for(int slot:peers.keySet())for(String permission:List.of("INTEGRATION","STOP_CYCLES"))send(slot,"PERSONAL_CONSENT",Map.of("procedure",permission,"consent",true));waitFor(s->s.finalRoute.equals(route),"final route vote",30);leaveChapter();nextChapter();}
        check(state().currentAct==6,"node");resupply();chapterUse(0,"camp_gate","CH_DEPLOY");chapterUse(0,"chapter_cache","CH_CACHE");resupply(true);waitFor(s->s.finalRoute.equals(route),"node route confirmation",30);
        switch(route){
            case "exodus"->{chapterUse(0,"node_tunnel","N_TUNNEL");chapterUse(1,"node_beacon","N_BEACON");chapterUse(0,"node_control","N_CONVOY");}
            case "bastion"->{chapterUse(0,"node_tunnel","N_PRESSURE");chapterUse(1,"node_beacon","N_VENT");standby(1,"node_b");chapterUse(0,"node_a","N_SYNC_A");chapterUse(1,"node_b","N_SYNC_B");check(state().flags.contains("node_synced"),"two owner sync");}
            case "cleansing"->{chapterUse(0,"node_tunnel","N_CUT");chapterUse(1,"node_beacon","N_RUNOFF");chapterUse(0,"node_control","N_HEAT");}
            case "symbiosis"->{chapterUse(0,"node_control","N_RESONANCE");if(state().actors.stream().anyMatch(a->a.life.equals("ECHO")))chapterUse(1,"node_anchor","N_ANCHOR");chapterUse(1,"node_bio","N_BLOOD");for(int slot:peers.keySet())chapterUse(slot,"node_bio","N_ATTUNE");}
            case "protocol"->{chapterUse(0,"node_control","N_AUDIT");chapterUse(1,"node_final","N_OFFLINE");chapterUse(0,"node_final","N_PROTOCOL");}
            case "awakening"->{chapterUse(0,"node_control","N_KEYS");chapterUse(1,"node_anchor","N_ANCHOR");standby(1,"node_b");chapterUse(0,"node_a","N_SYNC_A");chapterUse(1,"node_b","N_SYNC_B");check(state().flags.contains("node_synced"),"two owner sync");chapterUse(0,"node_final","N_STOP");}
        }
        check(CampaignPlan.finalReady(state()),"actual final work");leaveChapter();var end=state();check(end.campaignComplete&&end.endingId.equals(route),"campaign ending");check(peers.values().stream().allMatch(NetworkClient::connected),"all TCP clients connected at ending");check(peers.keySet().stream().allMatch(slot->Set.of("EVACUATED","ECHO").contains(end.actor(slot).life))&&end.actors.stream().anyMatch(a->a.life.equals("EVACUATED")),"all remaining bodies evacuated; lost bodies stay Echo");check(end.creatures.size()==2&&end.visitedLocations.size()==5,"all archived maps and unkillable threats");
        var report=new LinkedHashMap<String,Object>();report.put("passed",true);report.put("players",peers.size());report.put("endingId",end.endingId);report.put("actorOutcomes",end.actors.stream().map(a->Map.of("slot",a.slot,"life",a.life,"hp",a.hp)).toList());report.put("echoSupportCommands",trace.stream().filter(c->"ECHO_ACTION".equals(c.get("type"))&&Boolean.TRUE.equals(c.get("accepted"))).count());report.put("connectedClientsAtEnding",peers.values().stream().filter(NetworkClient::connected).count());report.put("echoCommands",trace.stream().filter(c->"ECHO_INTERFACE".equals(c.get("type"))&&Boolean.TRUE.equals(c.get("accepted"))).count());report.put("epilogue",end.epilogue);report.put("gameSeconds",end.gameTime);report.put("wallSeconds",(System.nanoTime()-started)/1e9);report.put("commands",trace.size());report.put("teleportsOrGrantedItems",false);report.put("startedFromFreshCampaign",sourceCheckpoint.isEmpty());report.put("checkpoint",sourceCheckpoint);report.put("continuedStoryToEnding",true);report.put("sixActsRouteCompleted",sourceCheckpoint.isEmpty());report.put("sixToTenHoursVerified",false);report.put("flags",end.flags);Files.write(output.resolve("full-campaign-result.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));System.out.println("FULL CAMPAIGN PASS "+route);
    }

    private void shot(String name)throws Exception{screenshot.accept(String.format("WALK_%02d_%s",photo++,name));Thread.sleep(300);}
    private static void check(boolean condition,String label){if(!condition)throw new IllegalStateException("Assertion: "+label);}
    public void run()throws Exception{
        send(1,"SESSION",Map.of("players",peers.size()));for(int slot:peers.keySet())send(slot,"READY",Map.of("ready",true));send(1,"START",Map.of());action();shot("START");
        door(0,"D03","CLOSE");door(1,"D10","CLOSE");
        for(int slot:peers.keySet())use(slot,"armory","ARMORY");
        use(0,"generator","GENERATOR");use(0,"switchboard","REPAIR");check(state().flags.contains("power_stable"),"physical repair");shot("OBJECT_OPERATION");
        use(1,"capsules","CAPSULES");use(1,"medcab","MEDKIT");use(0,"relay","RELAY");use(0,"archive","ARCHIVE");
        use(0,"rations","RATIONS");use(0,"bunks","REST");use(0,"store","LOOT");use(0,"filter","FILTER");
        waitFor(s->s.flags.contains("sample_goal"),"research vote",30);check(state().rooms.get("B7").powered,"vote powers lab");shot("RESEARCH_AUTHORIZED");
        use(1,"sample","COLLECT");check(state().actor(1).inventory.get("hive_sample").count==1,"sample enters personal inventory");shot("SAMPLE_IN_INVENTORY");
        use(1,"sample","ANALYSE");check(state().flags.contains("hive_core_studied")&&state().sharedEvidence.contains("water_report"),"analysis changes story");shot("ANALYSIS_RESULT");
        action();var safe=state().rooms.get("C1").center();send(1,"MOVE",Map.of("x",safe.x(),"y",safe.y(),"mode","WALK"));idle(1,"leave laboratory before decision");
        waitFor(s->s.flags.contains(branch.equals("biofilter")?"biofilter_installed":"hive_burned"),"research consequence vote",30);shot("VOTE_CONSEQUENCES");shot("HISTORY");
        if(branch.equals("biofilter")){use(1,"isolation","RESCUE");use(1,"isolation","DISINFECT");}else check(state().npcs.get("lina").condition.equals("DEAD"),"burn has irreversible NPC consequence");
        action();send(1,"HEAL",Map.of("recipient",1,"part","rightArm"));idle(1,"treatment");
        door(0,"D13","OPEN");use(0,"exit","PRESSURIZE");check(state().flags.contains("exit_ready"),"usable exit");shot("EXIT_READY");
        for(int slot:peers.keySet())use(slot,"exit","ASCEND");
        var end=state();check(end.phase==Phase.GAME_OVER,"episode ending");check(peers.keySet().stream().allMatch(slot->end.actor(slot).life.equals("EVACUATED")),"all human characters evacuated");check(!end.epilogue.isEmpty(),"epilogue");shot("ENDING");
        var save=output.resolve("verified-ending.json");SaveStore.save(save,end);var restored=SaveStore.load(save);check(restored.ending.equals(end.ending)&&restored.epilogue.equals(end.epilogue),"ending save roundtrip");
        var report=new LinkedHashMap<String,Object>();report.put("passed",true);report.put("scenario","STORY");report.put("players",peers.size());report.put("hostCharacter",1);report.put("branch",branch);report.put("qaSpeed",end.qaSpeed);report.put("wallSeconds",(System.nanoTime()-started)/1e9);report.put("gameSeconds",end.gameTime);report.put("cycle",end.cycle);report.put("ending",end.ending);report.put("epilogue",end.epilogue);report.put("flags",end.flags);report.put("decisions",end.decisions);report.put("commands",trace.size());report.put("teleportsOrGrantedItems",false);report.put("fullSixActCampaignCompleted",false);Files.write(output.resolve("walkthrough-result.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
        System.out.println("SELFPLAY PASS: "+peers.size()+" clients, "+branch+", "+end.ending);
    }
}
