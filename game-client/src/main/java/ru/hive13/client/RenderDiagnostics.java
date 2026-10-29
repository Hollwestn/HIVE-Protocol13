package ru.hive13.client;

import javafx.scene.*;
import java.util.*;

/** QA-only metadata accompanies native-pixel screenshots; never changes game state. */
final class RenderDiagnostics {
    private RenderDiagnostics(){}
    static Map<String,Object> inspect(Scene scene,boolean devicePixels){
        var canvases=new ArrayList<Map<String,Object>>();collect(scene.getRoot(),canvases);
        return Map.of("sceneWidth",scene.getWidth(),"sceneHeight",scene.getHeight(),
                "renderScaleX",scene.getWindow().getRenderScaleX(),"renderScaleY",scene.getWindow().getRenderScaleY(),
                "devicePixelCapture",devicePixels,"canvases",canvases);
    }
    private static void collect(Node node,List<Map<String,Object>> result){
        if(node instanceof SharpCanvas canvas){var p=canvas.rasterPlan();result.add(Map.of("type",canvas.getClass().getSimpleName(),"logicalWidth",canvas.getWidth(),"logicalHeight",canvas.getHeight(),"raster",p));}
        if(node instanceof Parent parent)for(var child:parent.getChildrenUnmodifiable())collect(child,result);
    }
}
