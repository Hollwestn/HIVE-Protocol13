package ru.hive13.client;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Original production assets; private reference screenshots are never loaded or shipped. */
public final class ReferenceAssets {
    private static final Image MEDICAL=load("/assets/medical/neutral-wireframe-v1.png"),REGION=load("/assets/terrain/region-neutral-v1.png");
    private static Image plate,architecture;
    private ReferenceAssets(){}
    private static Image load(String path){var stream=ReferenceAssets.class.getResourceAsStream(path);if(stream==null)throw new IllegalStateException("Missing original visual asset: "+path);return new Image(stream);}
    public static Font text(double size){return Font.font("Roboto Condensed",FontWeight.NORMAL,size);}
    public static Font strong(double size){return Font.font("Roboto Condensed",FontWeight.BOLD,size);}
    public static Font mono(double size){return Font.font("Share Tech Mono",size);}
    public static void portrait(GraphicsContext g,int slot,double x,double y,double w,double h){double width=BrandAssets.PORTRAITS.getWidth()/3;g.drawImage(BrandAssets.PORTRAITS,Math.clamp(slot,0,2)*width,0,width,BrandAssets.PORTRAITS.getHeight(),x,y,w,h);}
    public static void emblem(GraphicsContext g,double x,double y,double w,double h){g.drawImage(BrandAssets.ICON,x,y,w,h);}
    public static Image anatomicalPlate(){
        if(plate!=null)return plate;
        var result=new WritableImage(134,277);var reader=MEDICAL.getPixelReader();var writer=result.getPixelWriter();
        double left=MEDICAL.getWidth()*.176,span=MEDICAL.getWidth()*.648;
        for(int y=0;y<277;y++)for(int x=0;x<134;x++){
            double red=0,green=0,blue=0;
            for(int sy=0;sy<3;sy++)for(int sx=0;sx<3;sx++){
                int px=Math.clamp((int)(left+(x+(sx+.5)/3)*span/134),0,(int)MEDICAL.getWidth()-1),py=Math.clamp((int)((y+(sy+.5)/3)*MEDICAL.getHeight()/277),0,(int)MEDICAL.getHeight()-1);
                var c=reader.getColor(px,py);red+=c.getRed();green+=c.getGreen();blue+=c.getBlue();
            }
            writer.setColor(x,y,Color.color(red/9,green/9,blue/9));
        }
        plate=result;return result;
    }
    /** Room geometry and furniture are drawn by BlueprintMapView, without screenshot underlays. */
    public static Image architecture(){if(architecture==null)architecture=new WritableImage(910,470);return architecture;}
    public static Image terrain(){return REGION;}
    public static Image regionalTerrain(){return REGION;}
    public static void item(GraphicsContext g,String id,double x,double y,double w,double h){
        String icon=switch(id){case "medkit"->"medical";case "battery"->"power";case "filter"->"filter";case "tool"->"terminal";default->"store";};
        double size=Math.min(w,h);TerminalDraw.icon(g,icon,x+(w-size)/2,y+(h-size)/2,size,Color.web("#a3c4ad"));
    }
}