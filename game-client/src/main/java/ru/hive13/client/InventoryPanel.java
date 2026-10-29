package ru.hive13.client;

import javafx.scene.canvas.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.control.*;
import javafx.scene.Cursor;
import java.util.*;
import java.util.function.Consumer;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.TerminalDraw.*;

/** Reference-B inventory proportions, with real item selection, treatment and transfer. */
public final class InventoryPanel extends Pane {
    private final SharpCanvas canvas=new SharpCanvas(532,242);private final NetworkClient client;private Campaign state;private String selected="medkit",tab="ПРЕДМЕТЫ";
    public Runnable close=()->{},useMedical=()->{};public Consumer<String> open=kind->{};
    public InventoryPanel(NetworkClient client){this.client=client;canvas.onRasterChanged(this::draw);getChildren().add(canvas);setPrefSize(532,242);canvas.setCursor(Cursor.HAND);
        canvas.setOnMouseClicked(e->{double x=e.getX(),y=e.getY();if(x>510&&y<27){close.run();return;}if(y<28&&x>235){if(x<324)tab="ПРЕДМЕТЫ";else if(x<430){open.accept("РАССЛЕДОВАНИЕ");return;}else tab="ОБРАЗЦЫ";draw();return;}
            if(x>=97&&x<309&&y>=52&&y<215){int col=(int)((x-97)/72),row=y>=137?1:0,index=row*3+col;var list=items();if(index<list.size()){selected=list.get(index).id;draw();}return;}
            if(x>=324&&y>=169&&y<193){var item=item();if(item!=null&&item.count>0){if(item.id.equals("medkit"))useMedical.run();else open.accept("ВЗАИМОДЕЙСТВИЕ");}return;}
            if(x>=324&&y>=196&&y<222){transfer(e);}
        });
    }
    public void update(Campaign state){this.state=state;draw();}
    private List<Item> items(){if(state==null)return List.of();return state.actor(client.slot).inventory.values().stream().filter(i->tab.equals("ПРЕДМЕТЫ")||i.contaminated||i.id.equals("sample")).limit(6).toList();}
    private Item item(){var list=items();return list.stream().filter(i->i.id.equals(selected)).findFirst().orElse(list.isEmpty()?null:list.getFirst());}
    private void transfer(javafx.scene.input.MouseEvent e){var item=item();if(item==null||item.count==0)return;var menu=new ContextMenu();var own=state.actor(client.slot);for(var a:state.actors)if(a.slot!=client.slot){var entry=new MenuItem(a.name+" / "+a.roomId);entry.setDisable(!a.alive()||own.position().distance(a.position())>55);entry.setOnAction(ev->client.send("TRADE",Map.of("recipient",a.slot,"item",item.id)));menu.getItems().add(entry);}menu.show(canvas,e.getScreenX(),e.getScreenY());}
    private void draw(){if(state==null)return;var a=state.actor(client.slot);var g=canvas.getGraphicsContext2D();g.clearRect(0,0,532,242);panel(g,0,0,532,242);tracked(g,"ИНВЕНТАРЬ: "+a.id,11,18,12,.6,GREEN);String[] tabs={"ПРЕДМЕТЫ","ДОКУМЕНТЫ","ОБРАЗЦЫ"};for(int i=0;i<3;i++){double x=239+i*88;g.setStroke(BORDER);g.strokeRect(x,5,84,18);text(g,tabs[i],x+9,18,10,tab.equals(tabs[i])?GREEN:MUTED);}text(g,"×",516,18,12,MUTED);g.setStroke(BORDER);g.strokeLine(0,28,532,28);g.strokeLine(309,35,309,233);
        ReferenceAssets.portrait(g,a.slot,11,35,76,94);tracked(g,a.profession.replace("-БИОЛОГ",""),13,148,12,.5,MUTED);String[] metrics={"HP","СТРЕСС","ЗАРАЖЕНИЕ"};double[] values={a.hp,a.stress,a.infection()};for(int i=0;i<3;i++){text(g,metrics[i],13,174+i*21,9,MUTED);bar(g,45,166+i*21,30,4,values[i],i==2?RED:GREEN);right(g,Long.toString(Math.round(values[i])),88,175+i*21,10,TEXT);}text(g,String.format(Locale.ROOT,"%.1f / 18 кг",a.carriedWeight()),11,235,10,MUTED);
        tracked(g,"СНАРЯЖЕНИЕ",99,45,10,.7,MUTED);var list=items();for(int i=0;i<6;i++){double x=97+(i%3)*72,y=i<3?54:138;var item=i<list.size()?list.get(i):null;g.setStroke(item!=null&&item.id.equals(selected)?Color.web("#3a8b71"):BORDER);g.strokeRect(x,y,67,76);
            if(item!=null){text(g,"×"+item.count,x+5,y+12,9,MUTED);if(item.id.equals("medkit")||item.id.equals("filter"))ReferenceAssets.item(g,item.id,x+8,y+18,51,36);else icon(g,item.id.equals("hive_sample")?"lab":item.id.equals("battery")?"power":"store",x+21,y+22,25,Color.web("#8aa399"));text(g,item.name,x+5,y+68,10,item.count>0?TEXT:MUTED);}else{g.setStroke(Color.web("#163126"));g.strokeLine(x+28,y+36,x+39,y+36);g.strokeLine(x+33.5,y+30.5,x+33.5,y+41.5);}}
        var item=item();if(item==null){text(g,"НЕТ ПРЕДМЕТОВ",324,58,11,MUTED);return;}tracked(g,item.name.toUpperCase(),324,49,11,.4,TEXT);text(g,item.contaminated?"Биологически опасный предмет.":"Индивидуальное снаряжение.",324,76,10,MUTED);text(g,"Состояние: "+Math.round(item.condition)+"%",324,94,10,MUTED);text(g,"Масса: "+item.weight+" кг/шт",324,109,10,MUTED);text(g,"В наличии: "+item.count,324,124,10,MUTED);text(g,item.contaminated?"Риск заражения: высокий":"Состояние подтверждено",324,147,10,item.contaminated?RED:GREEN);
        g.setStroke(BORDER);g.strokeRect(324,169,195,23);g.strokeRect(324,196,195,23);icon(g,"medical",332,174,13,GREEN);text(g,item.id.equals("medkit")?"ИСПОЛЬЗОВАТЬ АПТЕЧКУ":"КОНТЕКСТ НА КАРТЕ [F]",352,185,10,item.count>0?GREEN:MUTED);icon(g,"radio",332,201,13,MUTED);text(g,"ПЕРЕДАТЬ СОСЕДНЕМУ ИГРОКУ",352,212,9,MUTED);text(g,"Передача требует близости 55 ед.",324,235,9,MUTED);
    }
}
