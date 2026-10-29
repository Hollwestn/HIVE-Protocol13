package ru.hive13.client;

import javafx.scene.canvas.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.function.Consumer;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.TerminalDraw.*;

/** Monitoring console: every row and connection is derived from the authoritative rooms/doors. */
public final class SystemsPanel extends Pane {
    private final SharpCanvas canvas=new SharpCanvas(850,430);private Campaign state;private String selected="B2";
    public Consumer<String> locate=id->{};public Runnable operations=()->{};
    public SystemsPanel(){canvas.onRasterChanged(this::draw);getChildren().add(canvas);setPrefSize(850,430);canvas.setOnMouseClicked(e->{double x=e.getX(),y=e.getY();if(state==null)return;if(x<593&&y>=111&&y<391){int index=(int)((y-111)/20);var rooms=new ArrayList<>(state.rooms.values());if(index<rooms.size()){selected=rooms.get(index).id;draw();}}else if(x>614&&y>344&&y<375)locate.accept(selected);else if(x>614&&y>386)operations.run();});}
    public void update(Campaign s){state=s;if(!s.rooms.containsKey(selected))selected=s.rooms.keySet().iterator().next();draw();}
    private void draw(){if(state==null)return;var g=canvas.getGraphicsContext2D();g.clearRect(0,0,850,430);String[] titles={"ПИТАНИЕ","КИСЛОРОД","ВЕНТИЛЯЦИЯ","БИОМАССА"};double[] values={state.power,state.oxygen(),state.ventilation,state.biomass()};for(int i=0;i<4;i++){double x=i*212;panel(g,x,0,202,66);tracked(g,titles[i],x+12,18,11,.8,MUTED);mono(g,Math.round(values[i])+"%",x+12,45,23,i==3?RED:i==0?AMBER:GREEN);bar(g,x+84,36,102,5,values[i],i==3?RED:i==0?AMBER:GREEN);}
        tracked(g,"СЕКТОР / УЗЕЛ",8,97,11,.7,MUTED);text(g,"ЭНЕРГИЯ",318,97,10,MUTED);text(g,"ВОЗДУХ",388,97,10,MUTED);text(g,"O₂",467,97,10,MUTED);text(g,"БИО",540,97,10,MUTED);
        int i=0;for(var r:state.rooms.values()){double y=111+i++*20;if(r.id.equals(selected)){g.setFill(Color.web("#15352a"));g.fillRect(0,y,595,20);}g.setStroke(Color.web("#16352b"));g.strokeLine(0,y+20,595,y+20);text(g,r.id+" / "+r.name,8,y+14,11,TEXT);text(g,r.powered?"ВКЛ":"НЕТ",318,y+14,10,r.powered?GREEN:RED);text(g,r.ventilated?"ПОТОК":"ИЗОЛ.",388,y+14,10,r.ventilated?GREEN:AMBER);mono(g,Math.round(r.oxygen)+"%",467,y+14,11,r.oxygen<35?RED:MUTED);mono(g,Math.round(r.biomass)+"%",540,y+14,11,r.biomass>20?RED:MUTED);}
        panel(g,611,85,239,245);tracked(g,"СХЕМА СОЕДИНЕНИЙ",623,105,11,.7,GREEN);g.save();g.translate(623,120);g.scale(state.currentAct>=3?.11:.23,.39);for(var d:state.doors.values()){var ra=state.rooms.get(d.a);var rb=state.rooms.get(d.b);var a=ra.center();var b=rb.center();g.setStroke(d.passable()?Color.web("#3d8369"):Color.web("#9b3a41"));g.setLineWidth(2);g.strokeLine(a.x(),a.y(),b.x(),b.y());}g.restore();for(var r:state.rooms.values()){var p=r.center();double x=623+p.x()*(state.currentAct>=3?.11:.23),y=120+p.y()*.39;g.setFill(r.id.equals(selected)?GREEN:Color.web("#0a211b"));g.fillRect(x-11,y-7,23,15);g.setStroke(r.powered?GREEN:AMBER);g.strokeRect(x-11,y-7,23,15);text(g,r.id,x-7,y+4,10,r.id.equals(selected)?Color.web("#04130d"):TEXT);}
        var room=state.rooms.get(selected);text(g,selected+" / "+room.name,623,315,11,TEXT);g.setStroke(BORDER);g.strokeRect(614,344,233,30);g.strokeRect(614,386,233,30);tracked(g,"ПОКАЗАТЬ СЕКТОР НА КАРТЕ",626,364,10,.6,GREEN);tracked(g,"ЛОКАЛЬНЫЕ ОПЕРАЦИИ [ F ]",626,406,10,.6,GREEN);text(g,"Выберите сектор. Изменения требуют физического присутствия героя.",8,415,11,MUTED);
    }
}
