import javafx.application.*;
import javafx.scene.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import ru.hive13.client.*;
import ru.hive13.model.*;
import ru.hive13.server.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Menu and lobby screenshots. Documentation IPs are explicit examples, not personal addresses. */
public class PresentationCapture {
    public static void main(String[] args){System.setProperty("hive.dataDir",Path.of("artifacts/release070/presentation-data").toAbsolutePath().toString());Application.launch(Ui.class,args);}
    public static class Ui extends Application {
        private final Pane root=new Pane();private Path out;
        public void start(Stage stage)throws Exception{
            out=Path.of("docs/images");Files.createDirectories(out);
            for(String name:List.of("JetBrainsMono-Regular.ttf","RobotoCondensed-Regular.ttf","RobotoCondensed-Bold.ttf","ShareTechMono-Regular.ttf"))Font.loadFont(HudView.class.getResourceAsStream("/assets/fonts/"+name),12);
            var scene=new Scene(root,1536,864);scene.getStylesheets().add(HudView.class.getResource("/ui.css").toExternalForm());stage.getIcons().add(BrandAssets.ICON);stage.setScene(scene);stage.setTitle("HIVE — снимки руководства");stage.show();
            root.getChildren().setAll(new MainMenuView(()->{},()->{},()->{},()->{},()->{}));shot("menu.png");
            Thread.ofVirtual().start(()->{try(var server=new GameServer("127.0.0.1",0,WorldFactory.create("STORY"),null,"");var a=new NetworkClient("127.0.0.1",server.port(),0,"","");var b=new NetworkClient("127.0.0.1",server.port(),1,"","");var c=new NetworkClient("127.0.0.1",server.port(),2,"","")){
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(a.state==null||a.state.actors.stream().filter(x->x.connected).count()!=3){if(System.nanoTime()>end)throw new IllegalStateException("lobby timeout");Thread.sleep(30);}
                b.send("READY",Map.of("ready",true));Thread.sleep(300);a.joinAddresses=List.of("192.168.1.10:27713","26.10.20.30:27713");
                var done=new CompletableFuture<Void>();Platform.runLater(()->{var hud=new HudView(a,()->{},new Properties(),()->{});try{root.getChildren().setAll(hud);hud.update(a.state);hud.openPanel("ЛОББИ");hud.render();shot("lobby-example.png");done.complete(null);}catch(Throwable t){done.completeExceptionally(t);}finally{hud.dispose();}});done.get(8,TimeUnit.SECONDS);
            }catch(Exception error){error.printStackTrace();}finally{Platform.runLater(Platform::exit);}});
        }
        private void shot(String name)throws Exception{root.applyCss();root.layout();javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",out.resolve(name).toFile());}
    }
}
