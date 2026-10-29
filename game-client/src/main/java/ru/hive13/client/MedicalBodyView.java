package ru.hive13.client;

import javafx.scene.canvas.*;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.scene.image.*;
import javafx.scene.layout.Pane;
import java.util.*;
import java.util.function.Consumer;
import ru.hive13.model.Model.*;
import static ru.hive13.client.Palette.*;

/** Neutral scanner artwork and separate, data-driven wounds/infection meshes. */
public class MedicalBodyView extends Pane {
    public String selected="rightArm";public Consumer<String> onSelect=s->{};private Actor actor;private String hovered="";
    private final Map<String,double[]> zones=new LinkedHashMap<>();private Image scan;private int scanKey;private final SharpCanvas annotations=new SharpCanvas(315,278);private final ImageView anatomy=new ImageView();
    private static final Image PLATE=ReferenceAssets.anatomicalPlate();
    public MedicalBodyView(){setPrefSize(315,278);annotations.onRasterChanged(()->draw(actor));anatomy.setLayoutX(90);anatomy.setLayoutY(0);anatomy.setFitWidth(134);anatomy.setFitHeight(277);anatomy.setMouseTransparent(true);getChildren().addAll(anatomy,annotations);
        zones.put("head",new double[]{140,2,36,44});zones.put("neck",new double[]{145,46,29,14});zones.put("rightShoulder",new double[]{113,59,20,29});zones.put("leftShoulder",new double[]{186,59,22,29});
        zones.put("rightArm",new double[]{99,88,29,64});zones.put("leftArm",new double[]{191,88,29,64});zones.put("rightHand",new double[]{92,152,26,37});zones.put("leftHand",new double[]{201,152,26,37});
        zones.put("chest",new double[]{128,60,62,49});zones.put("abdomen",new double[]{132,109,58,36});zones.put("pelvis",new double[]{130,145,59,27});zones.put("rightLeg",new double[]{129,172,30,105});zones.put("leftLeg",new double[]{159,172,29,105});
        setOnMouseMoved(e->{hovered=hit(e.getX(),e.getY());setCursor(hovered.isBlank()?Cursor.DEFAULT:Cursor.HAND);draw(actor);});setOnMouseExited(e->{hovered="";draw(actor);});
        setOnMouseClicked(e->{var hit=hit(e.getX(),e.getY());if(!hit.isEmpty()){selected=hit;onSelect.accept(hit);draw(actor);}});
    }
    private String hit(double x,double y){for(var e:zones.entrySet()){var r=e.getValue();if(x>=r[0]&&x<=r[0]+r[2]&&y>=r[1]&&y<=r[1]+r[3])return e.getKey();}return "";}
    public void draw(Actor a){actor=a;var g=annotations.getGraphicsContext2D();g.clearRect(0,0,315,278);if(a==null){anatomy.setImage(null);return;}int key=1;for(var part:a.body.values())key=31*key+Objects.hash(part.infection,part.injury);if(scan==null||scanKey!=key){scan=scan(a);scanKey=key;anatomy.setImage(scan);}
        String[] visible={"head","chest","rightArm","rightLeg","neck","abdomen","leftArm","leftLeg"};for(int i=0;i<visible.length;i++){var p=a.body.get(visible[i]);if(p==null)continue;boolean right=i>=4;double x=right?282:12,y=31+(i%4)*59;
            if(right){g.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);g.setFont(ReferenceAssets.strong(10));g.setFill(MUTED);g.fillText(p.name.toUpperCase(),x+19,y);TerminalDraw.mono(g,Math.round(p.infection)+"%",x+19,y+17,15,p.infection>20?RED:GREEN);g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);g.setStroke(BORDER);g.strokeLine(x-39,y+6,x+19,y+6);}
            else{TerminalDraw.tracked(g,p.name.toUpperCase(),x,y,10,.2,MUTED);TerminalDraw.mono(g,Math.round(p.infection)+"%",x,y+17,15,p.infection>20?RED:GREEN);g.setStroke(BORDER);g.strokeLine(x,y+6,x+57,y+6);}
        }
        if(!hovered.isEmpty()){var r=zones.get(hovered);g.setStroke(CYAN.deriveColor(0,1,1,.7));g.setLineWidth(.7);g.strokeRect(r[0]-2,r[1]-2,r[2]+4,r[3]+4);}
    }
    /** Source supplies isolated anatomy/vascular linework; infection colour always comes from body telemetry. */
    private Image scan(Actor a){var result=new WritableImage(134,277);var source=PLATE.getPixelReader();var out=result.getPixelWriter();
        for(int y=0;y<277;y++)for(int x=0;x<134;x++){var c=source.getColor(x,y);double max=Math.max(c.getRed(),Math.max(c.getGreen(),c.getBlue()));if(max<.055)continue;double severity=0;String zone="";for(var entry:zones.entrySet()){var r=entry.getValue();if(x+90>=r[0]&&x+90<=r[0]+r[2]&&y>=r[1]&&y<=r[1]+r[3]){var part=a.body.get(entry.getKey());if(part!=null){severity=part.infection+part.injury*.8;zone=entry.getKey();}break;}}
            boolean vascular=c.getRed()>c.getGreen()*1.25&&c.getRed()>.075;double reference=switch(zone){case "chest"->12;case "abdomen"->27;case "rightArm"->38;case "rightLeg"->5;default->40;};
            double tint=vascular?Math.clamp(severity/reference,0,1):Math.clamp(severity/100,0,.65)*Math.pow(Math.max(0,Math.sin(x*.27+Math.sin(y*.17)*3)*Math.cos(y*.29)),7)*.45;
            double neutralR=vascular?max*.52:c.getRed(),neutralG=vascular?max*.86:c.getGreen(),neutralB=vascular?max*.8:c.getBlue();
            out.setColor(x,y,Color.color(Math.clamp(neutralR*(1-tint)+Math.max(c.getRed(),max*.95)*tint,0,1),Math.clamp(neutralG*(1-tint)+Math.min(c.getGreen(),max*.21)*tint,0,1),Math.clamp(neutralB*(1-tint)+Math.min(c.getBlue(),max*.29)*tint,0,1),1));}
        return result;
    }
    private static boolean inSilhouette(double x,double y){double[] p={158,1,170,7,176,20,174,39,168,48,170,55,192,59,201,72,208,96,212,120,219,147,224,163,224,181,218,190,210,181,208,163,200,142,190,108,182,96,182,124,188,148,185,174,183,205,180,232,177,277,163,277,160,242,157,210,153,208,150,239,146,277,132,277,136,237,133,206,130,174,130,150,134,124,132,97,125,104,118,132,111,153,109,176,103,190,94,184,94,164,101,140,108,113,113,89,120,69,128,60,146,54,149,48,142,39,139,22,145,9};boolean inside=false;for(int i=0,j=p.length-2;i<p.length;j=i,i+=2)if((p[i+1]>y)!=(p[j+1]>y)&&x<(p[j]-p[i])*(y-p[i+1])/(p[j+1]-p[i+1])+p[i])inside=!inside;return inside;}
}
