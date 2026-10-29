package ru.hive13.client;

import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import static ru.hive13.client.HudView.*;
import static ru.hive13.client.Palette.*;
import java.util.*;
import java.util.function.Consumer;

/** Persistent controls: incoming snapshots refresh availability, not the user's selection. */
public final class EchoPanel extends Pane {
    private final NetworkClient client;private Campaign state;
    private final ComboBox<String> rooms=new ComboBox<>(),doors=new ComboBox<>();
    private final Label status=label("",13,CYAN),details=label("",13,TEXT);
    private final Map<String,Button> commands=new LinkedHashMap<>();
    public Consumer<String> locate=id->{};
    public EchoPanel(NetworkClient client){this.client=client;setPrefSize(850,430);
        status.setWrapText(true);place(this,status,0,0,850,50);
        var help=label("Камера видит одно помещение в двух переходах от живой группы. Изоляция и потеря питания прерывают связь. Команды расходуют общее питание.",12,MUTED);help.setWrapText(true);place(this,help,0,54,850,52);
        rooms.setId("echo-room");rooms.setConverter(new StringConverter<>(){public String toString(String id){return state!=null&&state.rooms.containsKey(id)?id+" / "+state.rooms.get(id).name:Objects.toString(id,"");}public String fromString(String text){return text;}});
        place(this,rooms,0,114,410,36);rooms.valueProperty().addListener((o,a,b)->{updateDoors();refresh();});
        int y=166;for(String action:List.of("WATCH","MARK","DECOY")){var button=button("",()->send(action));button.setId("echo-"+action.toLowerCase(Locale.ROOT));commands.put(action,button);place(this,button,0,y,410,45);y+=62;}
        details.setWrapText(true);place(this,details,440,114,400,160);
        doors.setId("echo-door");place(this,doors,440,285,400,36);doors.valueProperty().addListener((o,a,b)->refresh());
        for(String action:List.of("OPEN","CLOSE")){var button=button("",()->send(action));button.setId("echo-"+action.toLowerCase(Locale.ROOT));commands.put(action,button);place(this,button,action.equals("OPEN")?440:645,335,195,44);}
        place(this,button("ПОКАЗАТЬ ВЫБРАННОЕ ПОМЕЩЕНИЕ",()->{if(rooms.getValue()!=null)locate.accept(rooms.getValue());}),440,393,400,32);
    }
    public void select(String room){if(rooms.getItems().contains(room))rooms.setValue(room);}
    public String selectedRoom(){return rooms.getValue();}
    public void update(Campaign value){state=value;
        var ids=new ArrayList<>(value.rooms.keySet());if(!rooms.getItems().equals(ids)){String selected=rooms.getValue();String suggested=EchoRules.coveredRooms(value,value.actor(client.slot)).stream().findFirst().orElse(ids.isEmpty()?null:ids.getFirst());rooms.getItems().setAll(ids);rooms.setValue(ids.contains(selected)?selected:ids.contains(value.actor(client.slot).echoRoom)?value.actor(client.slot).echoRoom:suggested);}
        updateDoors();refresh();
    }
    private void updateDoors(){if(state==null)return;String room=rooms.getValue();var ids=state.doors.values().stream().filter(d->d.a.equals(room)||d.b.equals(room)).map(d->d.id).toList();if(!doors.getItems().equals(ids)){String selected=doors.getValue();doors.getItems().setAll(ids);doors.setValue(ids.contains(selected)?selected:ids.isEmpty()?null:ids.getFirst());}}
    private String target(String action){return Objects.toString(action.equals("OPEN")||action.equals("CLOSE")?doors.getValue():rooms.getValue(),"");}
    private void send(String action){client.error="";client.send("ECHO_ACTION",Map.of("action",action,"target",target(action)));}
    private void refresh(){if(state==null||client.slot<0)return;var actor=state.actor(client.slot);String why=EchoRules.signalReason(state,actor);
        status.setText(why.isEmpty()?"СВЯЗЬ УСТАНОВЛЕНА / "+(state.phase==Phase.ACTION?"ФАЗА ДЕЙСТВИЙ":"ПАУЗА — ГОЛОСОВАНИЕ"):why);
        String camera=EchoRules.cameraRoom(state,actor);details.setText("ПИТАНИЕ: "+Math.round(state.power)+"% / резерв связи 25%\nВОССТАНОВЛЕНИЕ: "+Math.max(0,(int)Math.ceil(actor.echoReadyAt-state.gameTime))+" с\nКАМЕРА: "+(camera.isEmpty()?"ОТКЛЮЧЕНА":camera+" / "+(int)Math.ceil(actor.echoViewUntil-state.gameTime)+" с")+"\nПОКРЫТИЕ: "+EchoRules.coveredRooms(state,actor).size()+" помещений\n\nОтметка видна всем 20 с. Громкоговоритель привлекает тварей, не останавливая их.");
        commands.forEach((action,button)->{var offer=EchoRules.offer(state,actor,action,target(action));String title=action.equals("OPEN")?"Открыть шлюз":action.equals("CLOSE")?"Закрыть шлюз":offer.title();button.setText(title+(offer.power()>0?" / −"+offer.power()+"%":""));button.setDisable(!offer.enabled());button.setTooltip(new Tooltip(offer.description()+(offer.enabled()?"":"\n"+offer.reason())));});
    }
}
