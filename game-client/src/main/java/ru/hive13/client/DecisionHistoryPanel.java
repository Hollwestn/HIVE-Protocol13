package ru.hive13.client;

import javafx.scene.layout.*;
import javafx.scene.control.*;
import java.util.*;
import ru.hive13.model.Model.*;
import static ru.hive13.client.HudView.*;
import static ru.hive13.client.Palette.*;

/** Readable, persistent receipts of actual committed votes and their effects. */
final class DecisionHistoryPanel extends Pane {
    private final VBox entries=new VBox(12);private int count=-1;
    DecisionHistoryPanel(){setPrefSize(850,430);var scroll=new ScrollPane(entries);scroll.setFitToWidth(true);place(this,scroll,0,0,850,430);}
    void update(Campaign state){if(count==state.decisions.size())return;count=state.decisions.size();entries.getChildren().clear();var recent=new ArrayList<>(state.decisions);Collections.reverse(recent);
        if(recent.isEmpty())entries.getChildren().add(label("Решений ещё нет. Голосование открывается после минутной фазы действий.",14,MUTED));
        for(var d:recent){var card=new VBox(8);card.setStyle("-fx-background-color:#071812;-fx-border-color:#244b40;-fx-padding:12;");card.getChildren().add(label("ЦИКЛ "+d.cycle+" / "+(d.title.isBlank()?choice(d.optionId):d.title),15,GREEN));var result=label(d.consequence.isBlank()?"Для старого сохранения подробный чек отсутствует. См. радиожурнал.":d.consequence,13,TEXT);result.setWrapText(true);result.setMaxWidth(785);card.getChildren().add(result);var votes=label(d.ballots.entrySet().stream().map(e->state.actor(e.getKey()).name+": "+choice(e.getValue())).collect(java.util.stream.Collectors.joining(" / ")),11,MUTED);votes.setWrapText(true);card.getChildren().add(votes);entries.getChildren().add(card);}
    }
    private static String choice(String id){return switch(id){case "generator"->"резервный генератор";case "seal_b4"->"изоляция B4";case "surface_goal"->"поиск выхода";case "sample_goal"->"исследование образца";case "biofilter"->"биофильтр";case "burn_hive"->"выжигание колонии";case "air_purge"->"продувка вентиляции";case "exit_supplies"->"запасы экспедиции";case "lina_admit"->"допуск Лины";case "lina_quarantine"->"карантин Лины";case "lina_refuse"->"отказ в допуске";case "lina_drone"->"разведка дроном";case "defer"->"отсрочка";default->"неизвестный вариант";};}

}
