package ru.hive13.client;

import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import java.util.*;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.HudView.*;

/** Personal evidence remains private until the player explicitly shares it over the network. */
public final class EvidencePanel extends Pane {
    private final NetworkClient client;private Campaign state;private String selected="",filter="ВСЕ";private Set<String> listed=Set.of();
    private final Pane list=new Pane();private final Label identity=label("",12,CYAN),document=label("",14,TEXT);private final Button share=button("ПЕРЕДАТЬ КОМАНДЕ",this::share);
    public EvidencePanel(NetworkClient client){this.client=client;setPrefSize(850,430);for(int i=0;i<3;i++){String tab=new String[]{"ВСЕ","ЛИЧНЫЕ","ОБЩИЕ"}[i];place(this,button(tab,()->{filter=tab;listed=Set.of();refresh();}),i*86,0,80,25);}var listScroll=new ScrollPane(list);listScroll.setFitToWidth(true);place(this,listScroll,0,39,248,354);place(this,identity,271,0,575,27);document.setWrapText(true);document.setPrefWidth(550);var scroll=new ScrollPane(document);scroll.setFitToWidth(true);scroll.setStyle("-fx-border-color:#244b40;-fx-padding:12;");place(this,scroll,271,38,575,337);place(this,share,271,388,280,33);place(this,label("Публикуется только выбранная улика.",11,MUTED),565,388,281,33);}
    public void update(Campaign state){this.state=state;refresh();}
    private List<String> known(){if(state==null)return List.of();var all=new LinkedHashSet<>(state.actor(client.slot).privateEvidence);all.addAll(state.sharedEvidence);return all.stream().filter(id->!id.equals("defense_issued")).filter(id->filter.equals("ВСЕ")||filter.equals("ОБЩИЕ")==state.sharedEvidence.contains(id)).toList();}
    private void refresh(){var ids=known();if(!ids.contains(selected))selected=ids.isEmpty()?"":ids.getFirst();var changed=new LinkedHashSet<>(ids);changed.add("selection:"+selected);changed.add("filter:"+filter);if(state!=null)changed.add("access:"+state.sharedEvidence.hashCode());if(!listed.equals(changed)){listed=changed;list.getChildren().clear();int y=0;for(String id:ids){var b=button((state.sharedEvidence.contains(id)?"◈  ":"◇  ")+title(id),()->{selected=id;refresh();});b.setStyle("-fx-alignment:center-left;"+(id.equals(selected)?"-fx-border-color:#53d8a5;":""));place(list,b,0,y,248,37);y+=43;}list.setPrefHeight(Math.max(354,y));if(ids.isEmpty()){var empty=label("УЛИКИ НЕ ОБНАРУЖЕНЫ\n\nОсмотрите капсулы A2\nи документы архива B6.",13,MUTED);place(list,empty,0,10,245,140);}}
        if(selected.isEmpty()){identity.setText("АРХИВ ПУСТ");document.setText("Документы появляются после реального исследования мира. Личные сведения других героев здесь не отображаются.");share.setDisable(true);}else{boolean shared=state.sharedEvidence.contains(selected);identity.setText((shared?"ОБЩИЙ ДОСТУП":"ЛИЧНЫЙ ДОСТУП / "+state.actor(client.slot).id)+"   //   "+selected.toUpperCase());document.setText(EvidenceLibrary.text(selected));share.setDisable(shared);}}
    private void share(){if(state!=null&&!selected.isEmpty())client.send("SHARE",Map.of("evidence",selected));}
    private static String title(String id){return switch(id){case "caravan_testimony"->"СВИДЕТЕЛЬСТВО КАРАВАНА";case "surface_timeline"->"ЖУРНАЛ ПОСТАВОК";case "station47_logs"->"АРХИВ СТАНЦИИ 47";case "conservation"->"ЖУРНАЛ КОНСЕРВАЦИИ";case "capsule_marks"->"МЕТКИ НА КАПСУЛАХ";case "body_mismatch"->"МЕДИЦИНСКОЕ РАСХОЖДЕНИЕ";case "protocol_lock"->"БЛОКИРОВКА ПРОТОКОЛА";case "engineer_order"->"ПРИКАЗ ИНЖЕНЕРУ";case "medical_consent"->"ЛИСТ СОГЛАСИЯ";case "expedition_photo"->"ПОЛЕВАЯ ЗАПИСЬ";case "water_report"->"ОТЧЁТ БИОФИЛЬТРАЦИИ";default->id;};}
}
