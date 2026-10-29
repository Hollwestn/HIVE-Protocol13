package ru.hive13.client;

import javafx.scene.canvas.*;
import javafx.scene.control.Tooltip;
import javafx.scene.Cursor;
import javafx.scene.paint.*;
import java.util.*;
import java.util.function.*;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;
import static ru.hive13.client.TerminalDraw.*;

/** Reference-A panel composition. All changing numbers, bars and messages come from the snapshot. */
public final class HudChrome extends SharpCanvas {
    private Campaign state;private int selected;private final NetworkClient client;private String filter="Все";private double timerValue;
    private final Tooltip tip=new Tooltip();
    public IntConsumer chooseActor=i->{};public IntConsumer centerActor=i->{};public Consumer<String> chooseDecision=id->{};
    public Consumer<String> openPanel=id->{};public Consumer<String> journalFilter=id->{};public Consumer<String> centerRoom=id->{};public Runnable heal=()->{};
    public HudChrome(NetworkClient client){super(1536,864);this.client=client;onRasterChanged(()->{draw(state,selected);timer(timerValue);});Tooltip.install(this,tip);
        setOnMouseMoved(e->{boolean hand=e.getX()<244&&e.getY()>98&&e.getY()<540||e.getY()>645&&e.getY()<833||e.getX()>1196&&e.getY()>530;setCursor(hand?Cursor.HAND:Cursor.DEFAULT);if(state!=null&&e.getX()<610&&e.getY()>644&&e.getY()<830){int i=(int)((e.getY()-644)/46);var options=preview();if(i<options.size()){var o=options.get(i);tip.setText(o.description+"\n"+(o.enabled?"Риск: "+o.risk:o.reason));}}else tip.setText("I — инвентарь · J — расследование · M — регион · F — взаимодействие · Esc — меню");});
        setOnMouseClicked(e->{if(state==null)return;double x=e.getX(),y=e.getY();
            for(int i=0;i<3;i++)if(x>=9&&x<=239&&y>=98+i*150&&y<241+i*150){chooseActor.accept(i);if(e.getClickCount()==2)centerActor.accept(i);return;}
            if(x>450&&x<611&&y>=616&&y<640){openPanel.accept("РЕШЕНИЯ");return;}
            if(x<611&&y>=644&&y<831){int i=(int)((y-644)/46);var options=preview();if(i<options.size())chooseDecision.accept(options.get(i).id);return;}
            if(x>=628&&x<1015&&y>=642&&y<667){String[] labels={"Все","Система","Группа","Угрозы","Заметки"};int i=Math.min(4,(int)((x-628)/77));filter=labels[i];journalFilter.accept(filter);draw(state,selected);return;}
            if(x>=620&&x<=1189&&y>=673&&y<=828){var events=visibleEvents();int i=(int)((y-673)/18);if(i>=0&&i<events.size()&&!events.get(i).roomId.isBlank())centerRoom.accept(events.get(i).roomId);return;}
            if(x>1196&&y>709){openPanel.accept("РЕГИОН");return;}
            if(x>1196&&y>=663&&y<701){heal.run();return;}
            if(x>=525&&x<1100&&y>75&&y<105){String[] layers={"СТРУКТУРА","СТРУКТУРА","СТРУКТУРА","ПИТАНИЕ","ВОЗДУХ","БИОЛОГИЯ","БИОЛОГИЯ","СТРУКТУРА"};int i=Math.min(layers.length-1,(int)((x-525)/73));openPanel.accept("СЛОЙ:"+layers[i]);}
        });
    }
    public void draw(Campaign s,int selected){state=s;this.selected=selected;if(s==null)return;var g=getGraphicsContext2D();g.setFill(Color.web("#030a08"));g.fillRect(0,0,1536,864);
        panel(g,4,2,330,56);panel(g,340,2,57,56);panel(g,402,2,119,56);panel(g,529,2,227,56);panel(g,764,2,137,56);panel(g,906,2,210,56);panel(g,1124,2,407,56);
        panel(g,4,65,240,541);panel(g,254,65,934,541);panel(g,1196,65,335,456);panel(g,1196,530,335,172);panel(g,1196,709,335,124);panel(g,4,616,607,217);panel(g,620,616,568,217);
        ReferenceAssets.emblem(g,14,10,32,37);g.setFont(ReferenceAssets.mono(23));g.setFill(TEXT);g.fillText("HIVE: PROTOCOL 13",60,29);tracked(g,"СИСТЕМА УПРАВЛЕНИЯ ОБЪЕКТОМ",61,45,10,1.3,MUTED);
        tracked(g,"ЦИКЛ",354,20,10,1,MUTED);mono(g,String.format("%02d",s.cycle),354,47,24,GREEN);text(g,"22 ФЕВ 2091",413,49,9,MUTED);
        tracked(g,"ТЕКУЩАЯ ФАЗА",549,20,10,1.2,MUTED);tracked(g,s.phase==Phase.GAME_OVER?"ИТОГИ":s.phase==Phase.LOBBY?"ЛОББИ":s.phase==Phase.VOTING?"ГОЛОСОВАНИЕ":"РАСПРОСТРАНЕНИЕ",549,38,13,1,s.phase==Phase.LOBBY||s.phase==Phase.GAME_OVER?GREEN:RED);tracked(g,s.phase==Phase.GAME_OVER?"ЭПИЗОД ЗАВЕРШЁН":s.phase==Phase.LOBBY?"ОЖИДАНИЕ ГОТОВНОСТИ":s.phase==Phase.VOTING?"СИМУЛЯЦИЯ ПРИОСТАНОВЛЕНА":"ИНДИВИДУАЛЬНЫЕ ДЕЙСТВИЯ",549,51,9,.8,MUTED);
        double threat=Math.min(100,s.biomass()*2+Math.max(0,80-s.oxygen()));tracked(g,"УРОВЕНЬ УГРОЗЫ",781,20,9,.8,MUTED);for(int i=0;i<5;i++){g.setFill(i<Math.ceil(threat/20)?RED:Color.web("#254038"));g.fillRect(782+i*8,30,4,14);}text(g,threat>60?"ВЫСОКИЙ":threat>25?"СРЕДНИЙ":"НИЗКИЙ",826,42,11,threat>60?RED:AMBER);
        tracked(g,"ЗАРАЖЕНИЕ ОБЪЕКТА",918,23,9,.8,MUTED);int index=0;for(var r:s.rooms.values()){g.setFill(r.biomass>20?RED:Color.web("#203e32"));g.fillRect(920+index++*6,46-Math.max(2,r.biomass*.20),4,Math.max(2,r.biomass*.20));}right(g,Math.round(s.biomass())+"%",1097,41,25,RED);
        metric(g,"КИСЛОРОД",s.oxygen(),1135,GREEN);metric(g,"ПИТАНИЕ",s.power,1275,AMBER);metric(g,"ВЕНТИЛЯЦИЯ",s.ventilation,1410,GREEN);
        tracked(g,"ОПЕРАТИВНАЯ ГРУППА",14,85,11,1,GREEN);right(g,s.actors.stream().filter(a->a.connected).count()+"/3",232,85,12,GREEN);
        for(int i=0;i<3;i++)actor(g,s.actor(i),98+i*150,i==selected);
        tracked(g,"ТЕКУЩАЯ ЗАДАЧА",14,558,10,.8,GREEN);String goal=s.objective;for(int line=0;line<3&&!goal.isEmpty();line++){int end=Math.min(45,goal.length());if(end<goal.length()){int split=goal.lastIndexOf(" ",end);if(split>16)end=split;}text(g,goal.substring(0,end),14,573+line*12,10,MUTED);goal=goal.substring(end).strip();}
        tracked(g,s.currentAct>=3?ru.hive13.model.CampaignPlan.title(s.currentAct).toUpperCase():s.currentAct==2?"ВЕКТОР / СИГМА":"ОБЪЕКТ-13",268,89,s.currentAct>=3?13:18,1.2,TEXT);tracked(g,s.currentAct>=3?"АКТ "+s.currentAct+" / ДВА ЭТАЖА / P — ЛИЧНОЕ СОГЛАСИЕ":s.currentAct==2?"ПОВЕРХНОСТЬ // АКТ II":"ПОДЗЕМНЫЙ КОМПЛЕКС // УРОВЕНЬ B2",268,104,10,.4,MUTED);legend(g);compass(g);
        medical(g,s.actor(selected));actions(g);journal(g);footer(g);
        if(client.error!=null&&!client.error.isBlank()){g.setFill(Color.web("#111d15"));g.fillRect(267,586,894,17);text(g,client.error,272,599,10,AMBER);}
        if(s.qaSpeed!=1)tracked(g,"ПРОВЕРКА / УСКОРЕНИЕ ×"+(int)s.qaSpeed,862,104,11,.5,AMBER);noise(g,1536,864,1391);g.setFill(Color.color(0,0,0,.06));for(int y=1;y<864;y+=3)g.fillRect(0,y,1536,1);
    }
    public void timer(double seconds){timerValue=seconds;if(state==null)return;var g=getGraphicsContext2D();g.setFill(Color.web("#06100d"));g.fillRect(411,7,103,33);if(state.phase==Phase.GAME_OVER)tracked(g,"КОНЕЦ",416,34,20,1.8,GREEN);else if(state.phase==Phase.LOBBY)tracked(g,"ЛОББИ",416,34,20,1.8,GREEN);else if(state.phase==Phase.VOTING)tracked(g,"ПАУЗА",416,34,21,2.1,AMBER);else mono(g,HudView.formatTime(seconds),413,36,28,seconds<=10?RED:GREEN);}
    private void metric(GraphicsContext g,String title,double value,int x,Color color){tracked(g,title,x,24,9,.7,MUTED);right(g,Math.round(value)+"%",x+104,23,15,color);bar(g,x,35,104,4,value,color);}
    private void actor(GraphicsContext g,Actor a,double y,boolean selected){g.setFill(Color.web("#06110e"));g.fillRect(10,y,228,143);g.setStroke(selected?Color.web("#3a7765"):BORDER);g.setLineWidth(.8);g.strokeRect(10,y,228,143);
        ReferenceAssets.portrait(g,a.slot,12,y+7,57,91);g.setStroke(BORDER);g.strokeRect(11.5,y+6.5,58,92);tracked(g,(a.slot+1)+". "+a.name,80,y+22,14,.6,TEXT);tracked(g,a.profession.replace("-БИОЛОГ",""),80,y+40,11,1,MUTED);
        TerminalDraw.icon(g,a.slot==0?"radio":a.slot==1?"medical":"radio",212,y+13,17,GREEN);
        String[] labels={"ЗДОРОВЬЕ","СТРЕСС","ВЫНОСЛИВОСТЬ","ЗАРАЖЕНИЕ"};double[] values={a.hp,a.stress,a.stamina,a.infection()};for(int i=0;i<4;i++){double yy=y+68+i*20;tracked(g,labels[i],80,yy,11,.65,MUTED);bar(g,174,yy-8,30,5,values[i],i==3&&values[i]>20||i==1&&values[i]>50?RED:i==1?Color.web("#3b8c7d"):i==3?Color.web("#87a79c"):Color.web("#23b783"));right(g,Math.round(values[i])+(i==3?"%":""),234,yy,12,i==3&&values[i]>20?RED:TEXT);}
        g.setStroke(BORDER);g.strokeRect(14,y+104,24,25);g.strokeRect(44,y+104,27,25);mono(g,a.slot==0?"A":a.slot==1?"✚":"C",20,y+122,14,PLAYERS[a.slot]);mono(g,new String[]{"A1","B2","C3"}[a.slot],49,y+122,13,PLAYERS[a.slot]);if(a.life.equals("ECHO"))text(g,"ЭХО / ТЕЛО УТРАЧЕНО",78,y+140,10,CYAN);else if(!a.connected||a.life.equals("EVACUATED"))text(g,a.life.equals("EVACUATED")?"ЭВАКУИРОВАН":a.life.equals("DORMANT")?"В КАПСУЛЕ":"OFFLINE",78,y+140,9,AMBER);
    }
    private void legend(GraphicsContext g){String[] names={"Игрок","Цель","Путь","Дверь","Закрыто","Вентиляция","Рой","Опасность","Терминал"};String[] icons={"danger","target","route","door","closed","vent","bio","danger","terminal"};int[] x={531,591,644,702,766,834,923,979,1061};
        for(int i=0;i<names.length;i++){Color color=i==6||i==7||i==4?RED:GREEN;if(i==2){g.setStroke(GREEN);g.setLineWidth(1.2);g.setLineDashes(4,3);g.strokeLine(x[i],91,x[i]+16,91);g.setLineDashes();}else if(i==4){g.setStroke(RED);g.strokeRect(x[i],87,12,8);}else TerminalDraw.icon(g,icons[i],x[i],84,13,color);text(g,names[i],x[i]+17,94,9,MUTED);}
    }
    private void compass(GraphicsContext g){g.setStroke(Color.web("#567568"));g.setLineWidth(.8);g.strokeOval(1143,97,29,29);g.strokeLine(1157.5,95,1157.5,128);g.strokeLine(1140,111.5,1175,111.5);mono(g,"N",1155,90,10,MUTED);}
    private void medical(GraphicsContext g,Actor a){tracked(g,"МЕДИЦИНСКАЯ ДИАГНОСТИКА",1207,85,11,1,GREEN);ReferenceAssets.portrait(g,a.slot,1207,98,36,44);tracked(g,a.name,1254,112,14,.8,TEXT);tracked(g,a.profession.replace("-БИОЛОГ",""),1254,128,11,.9,MUTED);mono(g,"ID "+a.id,1254,141,8,MUTED);
        int stage=a.infection()<25?1:a.infection()<50?2:a.infection()<75?3:4;g.setStroke(a.infection()>0?Color.web("#713438"):BORDER);g.strokeRect(1440,98,79,42);text(g,a.infection()>0?(a.slot==1?"ЗАРАЖЕНА":"ЗАРАЖЁН"):"НОРМА",1450,114,11,a.infection()>0?RED:GREEN);text(g,"СТАДИЯ "+new String[]{"I","II","III","IV"}[stage-1],1450,131,10,a.infection()>0?RED:MUTED);
        g.setStroke(BORDER);g.strokeLine(1207,410,1519,410);g.strokeLine(1307,413,1307,461);g.strokeLine(1417,413,1417,461);tracked(g,"ПУЛЬС",1215,427,9,.5,MUTED);tracked(g,"ТЕМПЕРАТУРА",1318,427,9,.5,MUTED);tracked(g,"КРОВОПОТЕРЯ",1431,427,9,.4,MUTED);
        double pulse=65+a.stress*.64+(100-a.hp)*.3,temperature=36.6+a.infection()*.043,bleed=a.body.values().stream().mapToDouble(p->p.bleeding).sum();mono(g,Long.toString(Math.round(pulse)),1215,448,19,RED);text(g,"уд/мин",1215,459,8,MUTED);mono(g,String.format(Locale.ROOT,"%.1f°C",temperature),1321,448,19,temperature>37.5?RED:GREEN);text(g,bleed>10?"ВЫСОКАЯ":bleed>0?"УМЕРЕННАЯ":"НЕТ",1431,448,11,bleed>0?RED:MUTED);
        g.setStroke(RED);g.setLineWidth(.9);double[] wave={0,0,1,-1,0,-4,5,-12,9,0,1,-1,0};g.beginPath();for(int i=0;i<wave.length;i++){double x=1260+i*3,y=443+wave[i];if(i==0)g.moveTo(x,y);else g.lineTo(x,y);}g.stroke();
        tracked(g,"ОБЩИЙ УРОВЕНЬ ЗАРАЖЕНИЯ",1207,481,9,.8,MUTED);mono(g,Math.round(a.infection())+"%",1215,509,23,a.infection()>25?GREEN:CYAN);bar(g,1267,498,120,8,a.infection(),GREEN);g.setStroke(BORDER);g.strokeRect(1402,472,117,39);tracked(g,"СТАДИЯ "+new String[]{"I","II","III","IV"}[stage-1],1412,487,10,.7,a.infection()>25?RED:GREEN);text(g,a.hp<25?"КРИТИЧЕСКОЕ СОСТОЯНИЕ":"СИМПТОМЫ ПОД КОНТРОЛЕМ",1412,502,8,MUTED);
        tracked(g,"КЛИНИЧЕСКАЯ КАРТА",1207,546,11,.9,GREEN);right(g,"ID "+a.id,1518,546,9,MUTED);g.setStroke(BORDER);g.strokeLine(1207,553,1519,553);
        String[] keys={"СТАДИЯ","ОЧАГИ","СОСТОЯНИЕ","РИСК ПОЛНОГО ЗАРАЖЕНИЯ"};for(int i=0;i<4;i++)tracked(g,keys[i],1207,569+i*24,9,.5,MUTED);
        text(g,new String[]{"I — КОНТАКТ","II — РАСПРОСТРАНЕНИЕ","III — АДАПТАЦИЯ","IV — КРИТИЧЕСКАЯ"}[stage-1],1318,569,11,RED);
        String focus=a.body.values().stream().filter(p->p.infection>10||p.injury>0).map(p->p.name.toLowerCase()).limit(2).collect(java.util.stream.Collectors.joining(", "));text(g,focus.isBlank()?"нет активных повреждений":focus,1318,593,10,MUTED);
        text(g,a.hp<25?"Критическое":a.hp<60?"Стабильное (ослаблен)":"Стабильное",1318,617,11,a.hp<25?RED:AMBER);text(g,Math.round(Math.min(100,a.infection()*1.8+a.stress*.35))+"% / оценка датчика",1372,641,10,RED);
        g.strokeLine(1207,647,1519,647);tracked(g,"РЕКОМЕНДАЦИЯ",1207,664,9,.5,MUTED);text(g,"Выберите область на схеме тела.",1318,664,10,TEXT);text(g,"Обработать повреждение  [ ЛЕЧИТЬ ]",1318,680,10,GREEN);text(g,"Нужна аптечка, пациент рядом.",1318,693,9,MUTED);
        if(a.life.equals("ECHO")){g.setFill(Color.web("#06110f"));g.fillRect(1207,413,312,49);tracked(g,"ЭХО / ТЕЛО УТРАЧЕНО",1215,432,12,.5,CYAN);text(g,"Биосигнал отсутствует. Терминальный режим [ P ].",1215,451,10,MUTED);g.fillRect(1314,600,205,21);text(g,"Архив тела / не живой пациент",1318,617,10,CYAN);g.fillRect(1207,649,312,48);tracked(g,"ПАМЯТЬ СОХРАНЕНА В СЕТИ",1215,669,11,.5,CYAN);text(g,"Обычное лечение не восстанавливает утраченное тело.",1215,688,9,MUTED);}
        tracked(g,"РЕГИОН: ЗОНА ОТЧУЖДЕНИЯ",1207,725,11,.9,GREEN);String[] legend={"Объект-13","Биосигнал","Контакт роя","Выход"};for(int i=0;i<4;i++){TerminalDraw.icon(g,i==0?"terminal":i==1?"bio":i==2?"danger":"exit",1417,737+i*21,12,i==0||i==3?GREEN:RED);text(g,legend[i],1435,747+i*21,9,MUTED);}
    }
    private List<Option> preview(){return state.options.stream().filter(o->!o.id.equals("defer")).limit(4).toList();}
    private void actions(GraphicsContext g){tracked(g,"ДОСТУПНЫЕ ДЕЙСТВИЯ",15,636,12,1,GREEN);text(g,"ИСТОРИЯ [H]",514,636,10,CYAN);var list=preview();for(int i=0;i<list.size();i++){var o=list.get(i);double y=645+i*46;g.setStroke(BORDER);g.setLineWidth(.75);g.strokeRect(13,y,590,42);g.strokeRect(19,y+6,36,30);var color=o.id.contains("seal")||o.id.contains("sample")?RED:GREEN;icon(g,o.id.contains("generator")?"power":o.id.contains("seal")?"bio":o.id.contains("sample")?"lab":"exit",25,y+10,23,color);tracked(g,o.title.toUpperCase(),71,y+17,12,.3,TEXT);
            String description=o.description.length()>44?o.description.substring(0,43)+"…":o.description;text(g,description,71,y+31,10,MUTED);tracked(g,"РЕШЕНИЕ: 2 ИЗ 3",313,y+14,10,.5,MUTED);text(g,"РИСК: "+o.risk,313,y+30,10,o.risk.contains("ВЫСОК")?RED:AMBER);
            long votes=state.ballots.values().stream().filter(v->v.equals(o.id)).count();g.setFill(Color.web("#071b16"));g.fillRect(439,y+6,155,30);if(votes>0){g.setFill(Color.web("#1b5e49"));g.fillRect(440,y+7,153*votes/3,28);}g.setStroke(o.enabled?Color.web("#347561"):BORDER);g.strokeRect(439,y+6,155,30);tracked(g,"ГОЛОСОВАТЬ ("+votes+"/3)",471,y+25,11,.35,o.enabled?MUTED:Color.web("#3e6051"));}}
    private List<Event> visibleEvents(){var events=state.events.stream().filter(e->filter.equals("Все")||filter.equals("Заметки")&&e.category.equals("ЗАМЕТКА")||e.category.startsWith(filter.equals("Угрозы")?"УГРОЗА":filter.toUpperCase())).toList();return events.subList(Math.max(0,events.size()-8),events.size());}
    private void journal(GraphicsContext g){tracked(g,"РАДИОЖУРНАЛ / СОБЫТИЯ",631,636,11,1,GREEN);String[] tabs={"Все","Система","Группа","Угрозы","Заметки"};for(int i=0;i<tabs.length;i++){double x=628+i*77;g.setFill(filter.equals(tabs[i])?Color.web("#2a8b6f"):Color.web("#07130f"));g.fillRect(x,644,72,20);g.setStroke(BORDER);g.strokeRect(x,644,72,20);tracked(g,tabs[i].toUpperCase(),x+15,658,9,.65,filter.equals(tabs[i])?Color.web("#06231b"):MUTED);}
        var list=visibleEvents();for(int i=0;i<list.size();i++){var e=list.get(i);double y=682+i*18;int t=2*3600+14*60+27+(int)e.time;mono(g,String.format("%02d:%02d:%02d",t/3600%24,t/60%60,t%60),633,y,11,Color.web("#60897a"));var color=e.category.equals("УГРОЗА")?RED:e.category.equals("ГРУППА")?CYAN:Color.web("#6aa391");String line="["+e.category+"] "+e.text;if(line.length()>79)line=line.substring(0,78)+"…";text(g,line,702,y,11,color);}}
    private void footer(GraphicsContext g){g.setStroke(BORDER);g.strokeLine(4,839,1531,839);tracked(g,"HIVE: PROTOCOL 13  v0.7.0 / ТЕРМИНАЛ КОМАНДОВАНИЯ",13,855,10,.5,MUTED);tracked(g,"РЕСУРСЫ:",559,855,9,.7,MUTED);
        String[] keys={"ammo","meds","filters","parts","batteries"},names={"ПАТРОНЫ","МЕДИКАМЕНТЫ","ФИЛЬТРЫ","ИНСТРУМЕНТЫ","БАТАРЕИ"};int[] xs={623,721,848,940,1058};for(int i=0;i<keys.length;i++){icon(g,i==1?"medical":i==2?"filter":i==4?"power":"store",xs[i],844,12,Color.web("#83baa8"));text(g,names[i]+" "+state.supplies.getOrDefault(keys[i],0),xs[i]+19,855,9,MUTED);}right(g,(client.slot==state.hostSlot?"ХОСТ":"КЛИЕНТ")+" / "+client.rtt+" мс",1384,855,10,MUTED);text(g,"СЕТЬ: "+(client.connected()?"ОНЛАЙН":"НЕТ СВЯЗИ"),1406,855,9,client.connected()?GREEN:RED);}
}
