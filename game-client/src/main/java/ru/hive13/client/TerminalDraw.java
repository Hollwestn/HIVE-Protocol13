package ru.hive13.client;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.scene.shape.ArcType;
import java.util.*;
import static ru.hive13.client.Palette.*;

public final class TerminalDraw {
    private static final Map<String,Double> WIDTHS=new HashMap<>();
    private TerminalDraw(){}
    public static void text(GraphicsContext g,String s,double x,double y,double size,Color color){g.setFill(color);g.setFont(ReferenceAssets.text(size));g.fillText(s,x,y);}
    public static void mono(GraphicsContext g,String s,double x,double y,double size,Color color){g.setFill(color);g.setFont(ReferenceAssets.mono(size));g.fillText(s,x,y);}
    public static void tracked(GraphicsContext g,String s,double x,double y,double size,double tracking,Color color){
        g.setFill(color);g.setFont(ReferenceAssets.strong(size));for(int i=0;i<s.length();i++){String glyph=s.substring(i,i+1);g.fillText(glyph,x,y);double width=WIDTHS.computeIfAbsent(size+":bold:"+glyph,k->{var metric=new Text(glyph);metric.setFont(ReferenceAssets.strong(size));return metric.getLayoutBounds().getWidth();});x+=width+tracking;}
    }
    public static void right(GraphicsContext g,String s,double x,double y,double size,Color color){g.setTextAlign(TextAlignment.RIGHT);mono(g,s,x,y,size,color);g.setTextAlign(TextAlignment.LEFT);}
    public static void panel(GraphicsContext g,double x,double y,double w,double h){
        g.setFill(new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#020c0a")),new Stop(.45,Color.web("#010907")),new Stop(1,Color.web("#010807"))));
        g.fillRoundRect(x,y,w,h,4,4);g.setStroke(Color.web("#244a40"));g.setLineWidth(.9);g.strokeRoundRect(x+.5,y+.5,w-1,h-1,4,4);
        g.setStroke(Color.web("#102a22"));g.setLineWidth(.5);g.strokeRoundRect(x+3,y+3,w-6,h-6,3,3);
    }
    public static void bar(GraphicsContext g,double x,double y,double w,double h,double value,Color color){g.setFill(Color.web("#17372d"));g.fillRect(x,y,w,h);g.setFill(color);g.fillRect(x,y,w*Math.clamp(value,0,100)/100,h);g.setStroke(color.deriveColor(0,1,1,.35));g.setLineWidth(.5);g.strokeRect(x-1,y-1,w+2,h+2);}
    public static void icon(GraphicsContext g,String id,double x,double y,double size,Color color){g.save();g.translate(x,y);g.scale(size/24,size/24);g.setStroke(color);g.setFill(color);g.setLineWidth(1.2);
        switch(id){
            case "weapon"->{g.strokeLine(3,8,21,8);g.strokeLine(3,11,19,11);g.strokeRect(7,11,7,4);g.strokeLine(5,11,3,20);g.strokeLine(9,15,7,21);g.strokeLine(14,15,16,18);}
            case "shield"->{g.strokePolygon(new double[]{12,3,5,12,19,21},new double[]{2,6,16,22,16,6},6);g.strokeLine(8,11,11,15);g.strokeLine(11,15,17,8);}
            case "power"->{g.strokeRect(3,2,18,20);g.fillPolygon(new double[]{13,7,12,10,18,13},new double[]{4,13,13,21,10,10},6);}
            case "medical"->{g.strokeRect(3,3,18,18);g.fillRect(10,6,4,12);g.fillRect(6,10,12,4);}
            case "terminal"->{g.strokeRect(3,2,18,20);for(int yy=6;yy<19;yy+=4){g.strokeLine(6,yy,8,yy);g.strokeLine(11,yy,18,yy);}g.strokeLine(4,23,20,23);}
            case "exit"->{g.strokeRect(3,3,18,20);g.strokeLine(9,21,9,9);g.strokeLine(15,21,15,9);g.strokeLine(5,5,19,5);g.strokeLine(7,12,9,10);g.strokeLine(17,12,15,10);g.strokeLine(8,17,10,17);g.strokeLine(14,17,16,17);}
            case "bio"->{g.strokeOval(8,8,8,8);for(int i=0;i<3;i++){g.save();g.translate(12,12);g.rotate(i*120);g.strokeArc(-9,-11,18,15,10,155,ArcType.OPEN);g.strokeLine(0,-2,0,-10);g.restore();}}
            case "danger"->{g.strokePolygon(new double[]{12,2,22},new double[]{2,22,22},3);g.strokeLine(12,8,12,15);g.strokeOval(11.5,18,1,1);}
            case "filter"->{g.strokeRect(5,2,14,20);for(int i=8;i<19;i+=4)g.strokeLine(i,5,i,18);g.strokeOval(7,0,10,4);}
            case "lab"->{g.strokeRect(3,2,18,20);g.strokeLine(9,6,15,6);g.strokeLine(11,6,11,11);g.strokeLine(14,6,14,11);g.strokeLine(11,11,7,18);g.strokeLine(14,11,18,18);g.strokeLine(7,18,18,18);}
            case "bed"->{g.strokeRect(2,10,20,9);g.strokeRect(4,7,6,4);g.strokeLine(2,7,2,23);g.strokeLine(22,10,22,23);}
            case "vent"->{g.strokeOval(2,2,20,20);for(int i=0;i<4;i++){g.save();g.translate(12,12);g.rotate(i*90);g.strokeArc(-1,-8,9,9,30,180,ArcType.OPEN);g.restore();}g.fillOval(10.5,10.5,3,3);}
            case "radio"->{g.strokeLine(12,8,12,22);g.fillOval(10,5,4,4);g.strokeArc(3,0,18,18,135,90,ArcType.OPEN);g.strokeArc(6,3,12,12,135,90,ArcType.OPEN);g.strokeArc(3,0,18,18,-45,90,ArcType.OPEN);g.strokeArc(6,3,12,12,-45,90,ArcType.OPEN);}
            case "door"->{g.strokeRect(8,2,8,20);g.strokeLine(4,6,20,6);g.strokeLine(4,18,20,18);g.strokeLine(12,1,12,23);}
            case "store"->{g.strokeRoundRect(3,5,18,16,2,2);g.strokeRect(7,2,10,4);g.strokeRect(9,10,6,9);}
            default->{g.strokeOval(4,4,16,16);g.strokeLine(12,0,12,24);g.strokeLine(0,12,24,12);}
        }g.restore();
    }
    public static void noise(GraphicsContext g,double w,double h,long seed){var random=new Random(seed);g.setFill(Color.color(.45,.65,.56,.035));for(int i=0;i<(int)(w*h/80);i++)g.fillRect(random.nextDouble()*w,random.nextDouble()*h,1,1);}
}
