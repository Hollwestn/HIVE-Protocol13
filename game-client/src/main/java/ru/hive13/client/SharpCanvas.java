package ru.hive13.client;

import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.transform.Affine;
import javafx.stage.Screen;
import javafx.stage.Window;

/** Logical HUD/input coordinates, with rasterization directly into device pixels.
 * Texture size and origin follow the window, monitor DPI and ancestor scale. */
public class SharpCanvas extends Region {
    private final Canvas pixels=new Canvas();
    private final double nativeScale;
    private CanvasRaster.Plan plan;
    private Runnable redraw=()->{};
    private Window window;
    private final ChangeListener<Number> outputChanged=(o,a,b)->refresh();
    private final ChangeListener<Window> windowChanged=(o,a,b)->watchWindow(b);

    public SharpCanvas(double width,double height) {
        nativeScale=Math.ceil(Screen.getScreens().stream().mapToDouble(s->Math.max(s.getOutputScaleX(),s.getOutputScaleY())).max().orElse(1));
        pixels.setManaged(false);pixels.setMouseTransparent(true);getChildren().add(pixels);
        setPickOnBounds(true);setPrefSize(width,height);setMinSize(USE_PREF_SIZE,USE_PREF_SIZE);setMaxSize(USE_PREF_SIZE,USE_PREF_SIZE);resize(width,height);
        localToSceneTransformProperty().addListener((o,a,b)->refresh());
        sceneProperty().addListener((o,a,b)->watchScene(a,b));refresh();
    }
    public final GraphicsContext getGraphicsContext2D(){return pixels.getGraphicsContext2D();}
    public final void onRasterChanged(Runnable action){redraw=action;}
    private void watchScene(Scene previous,Scene next) {
        if(previous!=null)previous.windowProperty().removeListener(windowChanged);
        if(next!=null)next.windowProperty().addListener(windowChanged);
        watchWindow(next==null?null:next.getWindow());
    }
    private void watchWindow(Window next) {
        if(window!=null){window.renderScaleXProperty().removeListener(outputChanged);window.renderScaleYProperty().removeListener(outputChanged);}
        window=next;
        if(window!=null){window.renderScaleXProperty().addListener(outputChanged);window.renderScaleYProperty().addListener(outputChanged);}
        refresh();
    }
    @Override protected void layoutChildren(){refresh();}
    private void refresh() {
        if(getWidth()<=0||getHeight()<=0)return;
        var tx=getLocalToSceneTransform();
        var next=CanvasRaster.plan(getWidth(),getHeight(),tx.getMxx(),tx.getMyy(),tx.getTx(),tx.getTy(),
                window==null?1:window.getRenderScaleX(),window==null?1:window.getRenderScaleY(),nativeScale);
        if(next.equals(plan))return;plan=next;
        pixels.setWidth(next.width());pixels.setHeight(next.height());
        pixels.setLayoutX(next.offsetX());pixels.setLayoutY(next.offsetY());
        pixels.getTransforms().setAll(new Affine(1/next.densityX(),0,0,0,1/next.densityY(),0));
        var g=getGraphicsContext2D();g.setTransform(1,0,0,1,0,0);g.clearRect(0,0,pixels.getWidth(),pixels.getHeight());
        g.setTransform(next.densityX(),0,0,next.densityY(),next.translateX(),next.translateY());
        redraw.run();
    }
    final CanvasRaster.Plan rasterPlan(){return plan;}
}
