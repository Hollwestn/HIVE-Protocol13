package ru.hive13.client;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import java.util.*;
import java.util.function.Consumer;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.TerminalDraw.*;
import static ru.hive13.client.HudView.*;

public final class VotingPanel extends Pane {
    private final NetworkClient client;private final Consumer<String> cast;
    private final Label countdown=label("",12,AMBER),votes=label("",10,CYAN);
    private final CheckBox consent=new CheckBox("Разрешаю решение, затрагивающее сектор с моим героем");
    private final Pane rows=new Pane();private final Map<String,Button> buttons=new LinkedHashMap<>();
    public VotingPanel(NetworkClient client,Consumer<String> cast){this.client=client;this.cast=cast;setPrefSize(680,370);place(this,countdown,0,0,680,25);var scroll=new ScrollPane(rows);scroll.setFitToWidth(true);place(this,scroll,0,32,680,275);consent.setId("vote-consent");consent.setOnAction(e->client.send("CONSENT",Map.of("consent",consent.isSelected())));place(this,consent,0,314,680,23);place(this,votes,0,345,680,25);}
    public void update(Campaign s){countdown.setText("Осталось "+formatTime(s.voteRemaining)+" / большинство: 2 голоса. Все варианты — в списке с прокруткой.");
        var ids=s.options.stream().map(o->o.id).toList();buttons.entrySet().removeIf(entry->{if(ids.contains(entry.getKey()))return false;rows.getChildren().remove(entry.getValue());return true;});
        int i=0;for(var o:s.options){var b=buttons.get(o.id);if(b==null){b=button("",()->cast.accept(o.id));b.setId("vote-"+o.id);b.setWrapText(true);b.setStyle("-fx-alignment:center-left;-fx-padding:6 10;-fx-font-size:11;");buttons.put(o.id,b);place(rows,b,0,i*88,648,82);}else b.relocate(0,i*88);b.setAccessibleText(o.title);b.setText(o.title.toUpperCase()+"  ["+s.ballots.values().stream().filter(v->v.equals(o.id)).count()+"]\n"+o.description+"\n"+(o.enabled?"Цена: "+costTitle(o.cost):o.reason));b.setTooltip(new Tooltip(o.description+"\nЦена: "+costTitle(o.cost)+"\n"+o.reason));b.setDisable(!o.enabled);i++;}rows.setPrefHeight(i*88);consent.setSelected(s.actor(client.slot).consent);
        votes.setText(s.actors.stream().filter(a->!a.life.equals("DORMANT")).map(a->{String ballot=s.ballots.get(a.slot);String title=ballot==null?"—":s.options.stream().filter(o->o.id.equals(ballot)).map(o->o.title).findFirst().orElse("—");return a.name+": "+title;}).collect(java.util.stream.Collectors.joining(" / ")));}
}
