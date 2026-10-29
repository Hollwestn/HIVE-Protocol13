package ru.hive13.client;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import ru.hive13.model.*;
import ru.hive13.server.*;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual JavaFX raster readback: alternating one-pixel bars must not become grey. */
class SharpCanvasIntegrationTest {
    @Test void stylesheetPreservesRequestedLabelSizeAndLiveStatusColour()throws Exception{fx(()->{var label=HudView.label("СВЯЗЬ УСТАНОВЛЕНА",24,Palette.GREEN);var root=new Pane(label);var scene=new Scene(root,600,100);scene.getStylesheets().add(getClass().getResource("/ui.css").toExternalForm());root.applyCss();root.layout();assertEquals(24,label.getFont().getSize());assertEquals(Palette.GREEN,label.getTextFill());label.setTextFill(Palette.RED);root.applyCss();assertEquals(Palette.RED,label.getTextFill());});}
    @Test void menuButtonsDispatchTheirRealActionsAndPublicAssetsContainNoReferenceHud()throws Exception{fx(()->{
        var calls=new int[5];var menu=new MainMenuView(()->calls[0]++,()->calls[1]++,()->calls[2]++,()->calls[3]++,()->calls[4]++);var scene=new Scene(menu,1536,864);scene.getStylesheets().add(getClass().getResource("/ui.css").toExternalForm());menu.applyCss();menu.layout();
        String[] ids={"menu-new","menu-continue","menu-join","menu-guide","menu-exit"};for(String id:ids)((javafx.scene.control.Button)menu.lookup("#"+id)).fire();assertArrayEquals(new int[]{1,1,1,1,1},calls);assertNull(getClass().getResource("/assets/reference/hud-a-source.png"));assertFalse(BrandAssets.ICON.isError());
        try{javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(menu.snapshot(null,null),null),"png",java.nio.file.Path.of("target/menu-preview.png").toFile());}catch(java.io.IOException e){throw new AssertionError(e);}
    });}
    @BeforeAll static void toolkit()throws Exception{var ready=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});assertTrue(ready.await(15,TimeUnit.SECONDS));}
    @AfterAll static void stop(){Platform.exit();}
    private static void fx(Runnable test)throws Exception{var done=new CompletableFuture<Void>();Platform.runLater(()->{try{test.run();done.complete(null);}catch(Throwable ex){done.completeExceptionally(ex);}});done.get(15,TimeUnit.SECONDS);}
    private static WritableImage image(Pane root){var p=new SnapshotParameters();p.setFill(Color.BLACK);p.setViewport(new Rectangle2D(0,0,200,80));return root.snapshot(p,null);}
    private static void bars(SharpCanvas canvas,double scale,double fraction){var g=canvas.getGraphicsContext2D();g.setFill(Color.BLACK);g.fillRect(0,0,canvas.getWidth(),canvas.getHeight());g.setFill(Color.WHITE);for(int x=2;x<150;x+=2)g.fillRect((x-fraction)/scale,0,1/scale,40/scale);}
    @Test void thinLinesRemainOnePixelAtSmallFullHdAndLargeScales()throws Exception{fx(()->{
        for(double scale:new double[]{5d/6,1.25,2.5}){
            var canvas=new SharpCanvas(180/scale,60/scale);var scaled=new Group(canvas);scaled.getTransforms().add(new Scale(scale,scale,0,0));var root=new Pane(scaled);new Scene(root,200,80);root.applyCss();root.layout();bars(canvas,scale,0);var pixels=image(root).getPixelReader();
            for(int x=2;x<150;x++)assertEquals(x%2==0?0xffffffff:0xff000000,pixels.getArgb(x,10),"scale="+scale+", x="+x);
        }
    });}
    @Test void fractionalPanelOriginDoesNotBlurTheWholeTexture()throws Exception{fx(()->{
        double scale=5d/6,origin=268*scale,fraction=origin-Math.floor(origin);
        var canvas=new SharpCanvas(180/scale,60/scale);var scaled=new Group(canvas);scaled.getTransforms().add(new Scale(scale,scale,0,0));scaled.setLayoutX(origin-200);var root=new Pane(scaled);new Scene(root,200,80);root.applyCss();root.layout();bars(canvas,scale,fraction);var pixels=image(root).getPixelReader();
        for(int x=2;x<150;x++)assertEquals(x%2==0?0xffffffff:0xff000000,pixels.getArgb(23+x,10),"x="+x);
    });}
    @Test void resizingRepaintsImmediatelyAtTheNewDensity()throws Exception{fx(()->{
        var canvas=new SharpCanvas(120,50);var scaled=new Group(canvas);var transform=new Scale(1,1,0,0);scaled.getTransforms().add(transform);var root=new Pane(scaled);new Scene(root,200,80);root.applyCss();root.layout();
        canvas.onRasterChanged(()->bars(canvas,transform.getX(),0));transform.setX(1.25);transform.setY(1.25);root.layout();var pixels=image(root).getPixelReader();
        for(int x=2;x<140;x++)assertEquals(x%2==0?0xffffffff:0xff000000,pixels.getArgb(x,10),"x="+x);
    });}
    @Test void allNewChaptersRenderBothFloorsAndSystemsWithoutOldSectorReferences()throws Exception{fx(()->{for(int act=3;act<=6;act++){var s=WorldFactory.create("STORY");CampaignPlan.install(s,act);s.actor(0).roomId="R0";var map=new MapView();var root=new Pane(map);new Scene(root,910,470);map.resize(910,470);map.setState(s,0);root.applyCss();root.layout();map.render(false);assertEquals(0,map.displayFloor());map.changeFloor(1);map.render(false);assertEquals(1,map.displayFloor());map.center("R1");assertEquals(0,map.displayFloor());var systems=new SystemsPanel();systems.update(s);assertNotNull(root.snapshot(null,null));}});}
    @Test void sealingAnimationCannotReferenceTheOldBunkerAfterChapterTransition()throws Exception{fx(()->{
        var s=WorldFactory.create("STORY");var map=new MapView();var root=new Pane(map);new Scene(root,910,470);map.resize(910,470);map.setState(s,0);
        var sealed=Json.copy(s,Model.Campaign.class);sealed.flags.add("b4_sealed");map.setState(sealed,0);map.render(false);
        var surface=Json.copy(sealed,Model.Campaign.class);ExpeditionPlan.install(surface);for(var a:surface.actors){a.roomId="V0";a.x=100;a.y=350;}surface.tacticalMarks.put(1,new Model.TacticalMark(1,"V0",20));map.setState(surface,0);map.render(false);assertNotNull(root.snapshot(null,null));
    });}
    @Test void echoPanelKeepsSelectionAndControlsWhenSignalDrops()throws Exception{try(var server=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),null,"");var client=new NetworkClient("127.0.0.1",server.port(),0,"","")){
        long until=System.nanoTime()+3_000_000_000L;while(!client.welcomed&&System.nanoTime()<until)Thread.sleep(10);assertTrue(client.welcomed);
        fx(()->{var s=WorldFactory.create("STORY");s.phase=Model.Phase.ACTION;s.flags.add("relay_restored");s.power=80;for(var a:s.actors)a.connected=true;s.actor(0).life="ECHO";s.actor(0).hp=0;
            var panel=new EchoPanel(client);var root=new Pane(panel);root.setStyle("-fx-background-color:#06110f;");var scene=new Scene(root,850,430);scene.getStylesheets().add(getClass().getResource("/ui.css").toExternalForm());panel.update(s);panel.select("A2");root.applyCss();root.layout();var watch=(javafx.scene.control.Button)panel.lookup("#echo-watch");assertNotNull(watch);assertFalse(watch.isDisabled());
            try{javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",java.nio.file.Path.of("target/echo-panel-active.png").toFile());}catch(java.io.IOException error){throw new AssertionError(error);}
            s.power=0;panel.update(s);assertEquals("A2",panel.selectedRoom());assertSame(watch,panel.lookup("#echo-watch"));assertTrue(watch.isDisabled());assertNotNull(root.snapshot(null,null));
        });
    }}
    @Test void finalVoteHasScrollableRoutesAndPreservesButtonInstances()throws Exception{try(var server=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),null,"");var client=new NetworkClient("127.0.0.1",server.port(),0,"","")){long deadline=System.nanoTime()+3_000_000_000L;while(!client.welcomed&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(client.welcomed);fx(()->{var s=WorldFactory.create("STORY");CampaignPlan.install(s,6);s.finalRoute="exodus";new GameEngine(s).prepareOptions();var panel=new VotingPanel(client,id->{});panel.update(s);assertTrue(panel.getChildren().stream().anyMatch(n->n instanceof ScrollPane));var root=new Pane(panel);new Scene(root,720,430);root.applyCss();root.layout();var first=panel.lookup("#vote-route_exodus");assertNotNull(first);panel.update(s);assertSame(first,panel.lookup("#vote-route_exodus"));assertNotNull(panel.lookup("#vote-route_awakening"));});}}

}
