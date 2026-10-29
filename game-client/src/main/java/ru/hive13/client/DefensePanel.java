package ru.hive13.client;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import ru.hive13.model.*;
import ru.hive13.model.Model.*;
import static ru.hive13.client.HudView.*;
import static ru.hive13.client.Palette.*;

/** Persistent controls: snapshots update availability without swallowing a mouse click. */
final class DefensePanel extends VBox {
    private final NetworkClient client;private final Label stock=label("",14,CYAN),hint=label("",13,MUTED);private final VBox targets=new VBox(7);
    private final Map<String,Button> buttons=new LinkedHashMap<>();private String signature="";
    DefensePanel(NetworkClient client){this.client=client;setSpacing(15);getChildren().addAll(label("СДЕРЖИВАНИЕ / K",16,GREEN),stock,label("Оружие не убивает. Можно стрелять на ходу; работа прервётся, маршрут сохранится.\nШум привлекает тварей. Отходите, закрывайте проход и помогайте товарищу.",13,MUTED),hint,targets);}
    void update(Campaign s){var a=s.actor(client.slot);stock.setText("ПАТРОНЫ "+DefenseRules.count(a,"shell")+" / ЭЛЕКТРОЗАРЯДЫ "+DefenseRules.count(a,"shock_cell")+" / РАКЕТЫ "+DefenseRules.count(a,"flare")+(a.hidden?" / В УКРЫТИИ":""));
        hint.setText(s.contacts.values().stream().noneMatch(c->"CONFIRMED".equals(c.certainty))?"Подтверждённых контактов нет. Получите комплект у шкафа сдерживания.\nПКМ по красному контакту на карте также открывает выбор оружия.":"Выбирайте подтверждённую цель. Дальность и стены проверяет сервер.");
        String key=String.join(",",s.contacts.keySet());if(!key.equals(signature)){signature=key;targets.getChildren().clear();buttons.clear();for(var c:s.contacts.values())for(var w:DefenseRules.WEAPONS){var b=button("",()->{client.error="";client.send("FIRE",Map.of("target",c.id,"weapon",w.id()));});b.setPrefSize(825,34);targets.getChildren().add(b);buttons.put(c.id+":"+w.id(),b);}}
        for(var c:s.contacts.values())for(var w:DefenseRules.WEAPONS){var b=buttons.get(c.id+":"+w.id());String reason=DefenseRules.reason(s,a,c,w);b.setDisable(!reason.isEmpty());b.setText(c.type+("RECOVERING".equals(c.behavior)?" / ВОССТАНАВЛИВАЕТСЯ":"")+" / "+c.roomId+" — "+w.name()+" / "+w.range()+" ед."+(reason.isEmpty()?" / ОГОНЬ":" — "+reason));}
    }
}
