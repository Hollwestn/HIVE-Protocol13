package ru.hive13.client;

import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import java.util.*;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.TerminalDraw.*;

/** Brief presentation of real server events; no timer, vote or world-state mutation lives here. */
public final class PhaseOverlay extends SharpCanvas {
    private Campaign state;private int decisionCycle=-1;private long decisionAt,threatAt,lastEvent;private Event threat;public String status="";public int own;
    public PhaseOverlay(){super(1536,864);setMouseTransparent(true);}
    public void update(Campaign s){long now=System.nanoTime();if(state!=null){for(var event:s.events)if(event.id>lastEvent&&event.category.equals("УГРОЗА")){threat=event;threatAt=now;}}lastEvent=s.eventSequence;
        if(!s.decisions.isEmpty()&&s.decisions.getLast().cycle!=decisionCycle){decisionCycle=s.decisions.getLast().cycle;decisionAt=now;}state=s;}
    public void render(boolean dialogOpen,boolean motion){var g=getGraphicsContext2D();g.clearRect(0,0,1536,864);if(state==null)return;long now=System.nanoTime();
        if(state.phase==Phase.VOTING&&!dialogOpen){g.setFill(Color.color(0,0,0,.4));g.fillRect(4,65,1527,768);panel(g,515,335,470,52);g.setFill(Color.web("#154632aa"));g.fillRect(518,338,464,46);tracked(g,"ВРЕМЯ ИСТЕКЛО  //  ГОЛОСОВАНИЕ",541,367,16,.8,GREEN);}
        if(state.phase==Phase.ACTION&&threat!=null&&(now-threatAt)/1e9<3.4){double elapsed=(now-threatAt)/1e9;g.setGlobalAlpha(motion?Math.min(1,(3.4-elapsed)*2):1);g.setFill(Color.web("#300b11ee"));g.fillRect(288,548,475,47);g.setStroke(Color.web("#8b343f"));g.strokeRect(288.5,548.5,474,46);tracked(g,"ТРЕВОГА: БИОЛОГИЧЕСКАЯ АКТИВНОСТЬ",302,566,12,.5,RED);String line=threat.text.length()>83?threat.text.substring(0,82)+"…":threat.text;text(g,line,302,584,11,MUTED);g.setGlobalAlpha(1);}
        if(state.phase==Phase.ACTION&&!state.decisions.isEmpty()&&(now-decisionAt)/1e9<2.8){var d=state.decisions.getLast();long votes=d.ballots.values().stream().filter(v->v.equals(d.optionId)).count();String title=switch(d.optionId){case "seal_b4"->"ГЕРМЕТИЗИРОВАТЬ СЕКТОР B4";case "generator"->"ЗАПУСТИТЬ РЕЗЕРВНЫЙ ГЕНЕРАТОР";case "surface_goal"->"ИСКАТЬ ВЫХОД НА ПОВЕРХНОСТЬ";case "sample_goal"->"ИЗУЧИТЬ ОБРАЗЕЦ";case "defer"->"РЕШЕНИЕ ОТЛОЖЕНО";default->state.events.stream().filter(e->e.text.startsWith("РЕШЕНИЕ ПРИНЯТО")).reduce((a,b)->b).map(e->e.text.substring("РЕШЕНИЕ ПРИНЯТО: ".length())).orElse("КОЛЛЕКТИВНЫЙ ВЫБОР");};panel(g,465,547,700,49);g.setFill(Color.web("#123d2bc0"));g.fillRect(468,550,694,43);tracked(g,"РЕШЕНИЕ ПРИНЯТО  ["+votes+"/3]",481,568,14,.9,GREEN);text(g,d.consequence.isBlank()?title:d.consequence.substring(0,Math.min(118,d.consequence.length())),481,586,11,TEXT);}
        if(state.phase==Phase.ACTION&&own>=0&&own<state.actors.size()){
            var a=state.actor(own);String activity="";double progress=0;
            if(a.life.equals("EVACUATED"))activity="ВЫ НА ПОВЕРХНОСТИ / ожидайте выхода остальных участников";
            else if(!a.task.isEmpty()){var f=ru.hive13.model.WorldObjects.get(state,a.taskTarget);String action=a.task.startsWith("OBJECT:")?(f==null?ru.hive13.model.WorldObjects.title(a.task.substring(7)):ru.hive13.model.WorldObjects.offer(state,a,f,a.task.substring(7)).title()):(a.task.startsWith("ECHO:")?ru.hive13.model.WorldObjects.title(a.task.substring(5)):ru.hive13.server.GameEngine.actionTitle(a.task));activity=action.toUpperCase()+" / "+(f==null?a.taskTarget:f.name())+" / "+Math.round(a.taskProgress)+" из "+Math.round(a.taskTotal)+" с / ПРОБЕЛ — ОТМЕНА";progress=a.taskTotal>0?a.taskProgress/a.taskTotal:0;}
            else if(!a.path.isEmpty()){var next=a.queue.isEmpty()?null:ru.hive13.model.WorldObjects.get(state,a.queue.getFirst().target);activity="МАРШРУТ: "+(next==null?"выбранная точка":next.name()+" → "+ru.hive13.model.WorldObjects.title(a.queue.getFirst().action))+" / ПРОБЕЛ — ОСТАНОВИТЬ";}
            if(!activity.isEmpty()){g.setFill(Color.web("#071c17f2"));g.fillRect(268,576,910,28);text(g,activity,278,593,11,CYAN);g.setFill(GREEN);g.fillRect(268,602,910*Math.clamp(progress,0,1),2);}
        }
        if(status!=null&&!status.isBlank()){g.setFill(Color.web("#112016f5"));g.fillRect(268,576,910,28);g.setStroke(AMBER.deriveColor(0,1,1,.6));g.strokeRect(268.5,576.5,909,27);text(g,status,278,594,12,AMBER);}
    }
}
