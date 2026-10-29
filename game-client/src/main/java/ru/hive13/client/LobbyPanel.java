package ru.hive13.client;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.HudView.*;

/** Controls survive snapshots so an update cannot destroy the button between press and release. */
public final class LobbyPanel extends Pane {
    private final NetworkClient client;private Campaign state;private final Label[] actors=new Label[3];private final Label summary=label("",13,AMBER),mode=label("",12,GREEN);private final Button ready,start,two,three;
    private final Label connection=label("",12,CYAN);private final Button copy;
    public LobbyPanel(NetworkClient client){this.client=client;setPrefSize(850,430);
        two=button("2 ИГРОКА",()->client.send("SESSION",java.util.Map.of("players",2)));three=button("3 ИГРОКА",()->client.send("SESSION",java.util.Map.of("players",3)));two.setId("lobby-mode-two");three.setId("lobby-mode-three");place(this,two,0,0,160,30);place(this,three,170,0,160,30);place(this,mode,355,0,495,30);
        for(int i=0;i<3;i++){actors[i]=label("",17,MUTED);place(this,actors[i],0,42+i*53,850,42);}
        connection.setWrapText(true);connection.setId("lobby-connection");place(this,connection,0,205,600,46);copy=button("СКОПИРОВАТЬ ДЛЯ ДРУГА",()->{var content=new javafx.scene.input.ClipboardContent();content.putString(ConnectionHints.invitation(client.joinAddresses,state.actor(client.slot).name));javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);});copy.setId("lobby-copy-connection");place(this,copy,610,205,240,42);
        ready=button("ГОТОВ",()->client.send("READY",java.util.Map.of("ready",!state.actor(client.slot).ready)));ready.setId("lobby-ready");start=button("ЗАПУСТИТЬ КАМПАНИЮ",()->{client.error="";client.send("START",java.util.Map.of());});start.setId("lobby-start");place(this,ready,0,265,400,45);place(this,start,435,265,415,45);summary.setWrapText(true);place(this,summary,0,325,850,92);
    }
    public void update(Campaign s){state=s;long connected=s.actors.stream().filter(a->a.connected).count();boolean allReady=s.actors.stream().filter(a->a.connected).allMatch(a->a.ready);for(int i=0;i<3;i++){var a=s.actor(i);actors[i].setText(a.id+" / "+a.name+" / "+(a.connected?"ПОДКЛЮЧЁН":"ОЖИДАНИЕ")+" / "+(a.connected?(a.ready?"ГОТОВ":"НЕ ГОТОВ"):"СВОБОДНО"));actors[i].setTextFill(a.connected&&a.ready?GREEN:MUTED);}two.setDisable(client.slot!=s.hostSlot);three.setDisable(client.slot!=s.hostSlot);two.setStyle(s.requiredPlayers==2?"-fx-border-color:#53d8a5;":"");three.setStyle(s.requiredPlayers==3?"-fx-border-color:#53d8a5;":"");mode.setText("РЕЖИМ: "+s.requiredPlayers+" ИГРОКА / ПОДКЛЮЧЕНО "+connected+"/3");ready.setText(s.actor(client.slot).ready?"ОТМЕНИТЬ ГОТОВНОСТЬ":"ГОТОВ");start.setVisible(client.slot==s.hostSlot);start.setDisable(connected<s.requiredPlayers||!allReady);
        boolean host=client.slot==s.hostSlot;copy.setVisible(host);copy.setDisable(client.joinAddresses.isEmpty());connection.setText(host?(client.joinAddresses.isEmpty()?"IP хоста: укажите адрес общей LAN / Radmin VPN. TCP-порт — из формы создания.":"ДЛЯ ДРУГА: "+String.join("  /  ",client.joinAddresses)+"\nПароль из формы передайте отдельно; персонажи должны отличаться."):"Используйте IP и порт хоста. Для общей игры нажмите «Готов».");
        String reason=connected<s.requiredPlayers?"Ожидается ещё "+(s.requiredPlayers-connected)+" игрок. Для партии вдвоём хост выбирает «2 игрока».":!allReady?"Все подключённые участники должны нажать «Готов».":client.slot==s.hostSlot?"Все готовы. Нажмите «Запустить кампанию».":"Все готовы. Ожидается запуск хостом.";summary.setText(reason+"\n"+(s.requiredPlayers==2?"Свободный третий герой остаётся в капсуле. Третий игрок может присоединиться позднее. Для решения нужны два совпадающих голоса.":"Три человека управляют разными героями. Решение принимается большинством 2 из 3.")+(client.error.isBlank()?"":"\n"+client.error));
    }
}
