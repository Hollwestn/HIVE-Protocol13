package ru.hive13.client;

import javafx.scene.layout.Pane;
import javafx.scene.image.ImageView;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import static ru.hive13.client.HudView.*;
import static ru.hive13.client.Palette.*;

/** The illustration stays separate from real controls and never impersonates live telemetry. */
public final class MainMenuView extends Pane {
    private static javafx.scene.control.Label menuLabel(String text,int size,Color color){var label=HudView.label(text,size,color);label.setStyle("-fx-font-size:"+size+"px;-fx-text-fill:"+color.toString().replace("0x","#")+";");return label;}
    public MainMenuView(Runnable start,Runnable resume,Runnable join,Runnable guide,Runnable exit){
        setPrefSize(1536,864);setStyle("-fx-background-color:#030b08;");
        var background=new ImageView(BrandAssets.MENU);background.setFitWidth(1536);background.setFitHeight(864);background.setPreserveRatio(false);background.setMouseTransparent(true);getChildren().add(background);
        var shade=new Rectangle(1536,864,new LinearGradient(0,0,1,0,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#020907f5")),new Stop(.44,Color.web("#020907c9")),new Stop(.78,Color.web("#02090720")),new Stop(1,Color.TRANSPARENT)));shade.setMouseTransparent(true);getChildren().add(shade);
        place(this,menuLabel("СИСТЕМА УПРАВЛЕНИЯ / ОБЪЕКТ-13",12,MUTED),96,58,650,28);
        var icon=new ImageView(BrandAssets.ICON);icon.setFitWidth(76);icon.setFitHeight(76);place(this,icon,91,111,76,76);
        var title=menuLabel("HIVE",86,TEXT);title.setFont(ReferenceAssets.strong(86));title.setStyle(title.getStyle()+"-fx-font-weight:bold;");place(this,title,184,90,450,112);
        var protocol=menuLabel("P R O T O C O L   1 3",21,GREEN);place(this,protocol,191,193,500,38);
        place(this,menuLabel("ТРИ ГОЛОСА. ОДНА СИСТЕМА.",18,GREEN),100,263,560,36);
        var story=menuLabel("Бункер проснулся раньше вас.\nНа каждое действие — минута.\nЗа каждое решение платит вся группа.",18,TEXT);story.setLineSpacing(5);place(this,story,100,309,570,104);
        var begin=button("01   /   НАЧАТЬ КАМПАНИЮ",start);begin.setId("menu-new");begin.setStyle("-fx-background-color:#163b2e;-fx-border-color:#65aa8a;-fx-text-fill:#d2ecdb;-fx-alignment:center-left;-fx-font-size:16;-fx-padding:10 20;");place(this,begin,100,438,490,52);
        for(var entry:new Object[][]{{"02   /   ПРОДОЛЖИТЬ СОХРАНЕНИЕ",resume,"menu-continue",504},{"03   /   ПОДКЛЮЧИТЬСЯ К ХОСТУ",join,"menu-join",570}}){var b=button((String)entry[0],(Runnable)entry[1]);b.setId((String)entry[2]);b.setStyle("-fx-alignment:center-left;-fx-font-size:16;-fx-padding:10 20;");place(this,b,100,(int)entry[3],490,52);}
        var help=button("РУКОВОДСТВО",guide);help.setId("menu-guide");place(this,help,100,652,236,40);var quit=button("ВЫХОД",exit);quit.setId("menu-exit");place(this,quit,354,652,236,40);
        place(this,menuLabel("2–3 ИГРОКА / LAN И RADMIN VPN",12,MUTED),100,749,560,25);
        place(this,menuLabel("АЛЬФА 0.7.0  /  ШЕСТЬ ГЛАВ  /  WINDOWS x64",11,MUTED),100,779,660,25);
        place(this,menuLabel("ОБЪЕКТ-13 / ЗОНА КАРАНТИНА",11,Color.web("#5c8673")),1060,800,420,24);
    }
}
