package ru.hive13.client;

import javafx.scene.canvas.*;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.function.Consumer;
import ru.hive13.model.Model.Campaign;
import static ru.hive13.client.Palette.*;

/** Geography is a map drawing. Only discovered campaign nodes are displayed. */
public final class RegionView extends SharpCanvas {
    private Campaign state;private final boolean compact;
    public Runnable onOpen=()->{};
    public Consumer<String> chooseNode=id->{};private final Map<String,double[]> screenNodes=new LinkedHashMap<>();
    public RegionView(double w,double h,boolean compact){super(w,h);this.compact=compact;onRasterChanged(this::draw);setOnMouseClicked(e->{for(var entry:screenNodes.entrySet()){var p=entry.getValue();if(Math.hypot(e.getX()-p[0],e.getY()-p[1])<18){chooseNode.accept(entry.getKey());if(compact)onOpen.run();return;}}onOpen.run();});}
    public void update(Campaign s){state=s;draw();}
    private void draw(){var g=getGraphicsContext2D();double w=getWidth(),h=getHeight();g.setFill(Color.web("#050c0b"));g.fillRect(0,0,w,h);g.drawImage(ReferenceAssets.regionalTerrain(),0,0,w,h);var random=new Random(1309);
        for(int i=0;i<20;i++){double cx=random.nextDouble()*w,cy=random.nextDouble()*h,rad=8+random.nextDouble()*45;g.setStroke(Color.web(i%4==0?"#29403888":"#182d2544"));g.setLineWidth(.35);g.beginPath();for(int j=0;j<25;j++){double a=j*Math.PI*2/24,r=rad*(.6+random.nextDouble()*.3);double x=cx+Math.cos(a)*r,y=cy+Math.sin(a)*r;if(j==0)g.moveTo(x,y);else g.lineTo(x,y);}g.stroke();}
        g.setStroke(Color.web("#275047"));g.setLineWidth(1.5);g.beginPath();g.moveTo(w*.12,h);g.bezierCurveTo(w*.48,h*.5,w*.2,h*.45,w*.48,0);g.stroke();
        if(state==null)return;
        var nodes=new LinkedHashMap<String,double[]>();nodes.put("object13",new double[]{.34,.51});nodes.put("sigma",new double[]{.55,.15});nodes.put("vector",new double[]{.68,.49});nodes.put("station47",new double[]{.81,.24});nodes.put("chapter_3",new double[]{.18,.28});nodes.put("chapter_4",new double[]{.27,.74});nodes.put("chapter_5",new double[]{.65,.78});nodes.put("chapter_6",new double[]{.85,.59});
        if(compact&&state.currentAct<3)nodes.replaceAll((id,p)->new double[]{(p[0]-.34)*2+.565,(p[1]-.51)*1.4+.46});
        var base=nodes.get("object13");for(var id:state.discoveredRegions){var p=nodes.get(id);if(p==null)continue;g.setStroke(GREEN.deriveColor(0,1,1,.6));g.setLineDashes(3,4);g.strokeLine(base[0]*w,base[1]*h,p[0]*w,p[1]*h);g.setLineDashes();}
        double danger=state.biomass()/100,diameter=(.25+danger*.8)*w;g.setFill(Color.color(.55,.06,.09,.16+danger*.3));g.fillOval(base[0]*w-diameter/2,base[1]*h-diameter/2,diameter,diameter);g.setStroke(RED.deriveColor(0,1,1,.6));g.strokeOval(base[0]*w-diameter/2,base[1]*h-diameter/2,diameter,diameter);
        screenNodes.clear();for(var id:state.discoveredRegions){var p=nodes.get(id);if(p==null)continue;double x=p[0]*w,y=p[1]*h;if(x<5||y<5||x>w-5||y>h-5)continue;screenNodes.put(id,new double[]{x,y});g.setStroke(GREEN);g.strokeRect(x-5,y-5,10,10);g.strokeRect(x-8,y-8,16,16);if(!compact)TerminalDraw.text(g,switch(id){case "object13"->"ОБЪЕКТ-13";case "sigma"->"СИГМА";case "vector"->"ВЕКТОР";case "chapter_3"->"ГЭС";case "chapter_4"->"ЭДЕМ-09";case "chapter_5"->"НОВЫЙ РАССВЕТ";case "chapter_6"->"ПЕРВИЧНЫЙ УЗЕЛ";default->"СТАНЦИЯ 47";},x+12,y+4,13,TEXT);}
        TerminalDraw.mono(g,"N",9,13,9,MUTED);g.setStroke(MUTED);g.strokeLine(12,17,12,34);g.strokeLine(9,20,12,17);g.strokeLine(15,20,12,17);TerminalDraw.noise(g,w,h,92);
    }
}
