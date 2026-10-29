package ru.hive13.client;

import javafx.scene.canvas.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.*;
import javafx.scene.input.*;
import java.util.*;
import java.util.function.*;
import ru.hive13.model.Model.*;
import ru.hive13.model.WorldObjects;
import ru.hive13.model.CampaignPlan;
import ru.hive13.model.EchoRules;
import javafx.scene.control.Tooltip;
import static ru.hive13.client.Palette.*;

/** Detailed, layered world-space plan. Every playable room and gate comes from server geometry. */
public class BlueprintMapView extends Region {
    private final SharpCanvas structure=new SharpCanvas(910,470),dynamic=new SharpCanvas(910,470);
    private Campaign state;private int own,displayFloor,lastOwnFloor;private double zoom=1,panX,panY,dragX,dragY,pulseTime,pulseX=-100,pulseY=-100;
    private String layer="СТРУКТУРА";private long lastRender,lastAlert;private int geometryHash;private double sealUntil;
    private final Map<Integer,Point> positions=new HashMap<>();
    private final Tooltip objectTip=new Tooltip();private String hoveredObject="";
    public interface Click{void run(double x,double y,boolean run,boolean sneak);}
    public Click move=(x,y,r,s)->{};public BiConsumer<String,MouseEvent> context=(s,e)->{};public Consumer<String> select=s->{};
    public BlueprintMapView(){getChildren().addAll(structure,dynamic);setMinSize(0,0);structure.onRasterChanged(()->geometryHash=0);Tooltip.install(dynamic,objectTip);
        dynamic.setOnMouseMoved(e->{if(state==null)return;var p=world(e.getX(),e.getY());var f=WorldObjects.all(state).stream().filter(o->o.position().distance(p)<19).min(Comparator.comparingDouble(o->o.position().distance(p))).orElse(null);hoveredObject=f==null?"":f.id();objectTip.setText(f==null?"ПКМ по объекту или двери — действия":f.name()+" / "+f.room()+"\nПКМ — действия, герой подойдёт сам");dynamic.setCursor(f==null?javafx.scene.Cursor.DEFAULT:javafx.scene.Cursor.HAND);});
        dynamic.setOnMousePressed(e->{dragX=e.getX();dragY=e.getY();});
        dynamic.setOnMouseDragged(e->{if(e.isMiddleButtonDown()){panX+=(e.getX()-dragX)*910/getWidth();panY+=(e.getY()-dragY)*470/getHeight();dragX=e.getX();dragY=e.getY();geometryHash=0;}});
        dynamic.setOnScroll(e->{var before=world(e.getX(),e.getY());zoom=Math.clamp(zoom*(e.getDeltaY()>0?1.12:.89),.7,2.6);panX=e.getX()*910/getWidth()-(before.x()-displayFloor*1000)*zoom;panY=e.getY()*470/getHeight()-before.y()*zoom;geometryHash=0;e.consume();});
        dynamic.setOnMouseClicked(e->{if(state==null||e.getButton()==MouseButton.MIDDLE)return;var p=world(e.getX(),e.getY());
            if(e.getButton()==MouseButton.SECONDARY){var contact=state.contacts.values().stream().filter(c->"CONFIRMED".equals(c.certainty)&&new Point(c.x,c.y).distance(p)<18).findFirst().orElse(null);if(contact!=null){context.accept("CONTACT:"+contact.id,e);return;}var fixture=WorldObjects.all(state).stream().filter(o->o.position().distance(p)<19).min(Comparator.comparingDouble(o->o.position().distance(p))).orElse(null);if(fixture!=null){context.accept(fixture.id(),e);return;}var door=state.doors.values().stream().min(Comparator.comparingDouble(d->d.center().distance(p))).orElse(null);String target=door!=null&&door.center().distance(p)<17?door.id:room(p);if(!target.isEmpty())context.accept(target,e);}
            else{String id=room(p);if(!id.isEmpty()){select.accept(id);move.run(p.x(),p.y(),e.isShiftDown(),e.isControlDown());pulseX=p.x();pulseY=p.y();pulseTime=System.nanoTime()/1e9;}}
        });
    }
    public void setLayer(String value){layer=value;geometryHash=0;}public String layer(){return layer;}
    public void setState(Campaign s,int own){if(state!=null&&state.currentAct!=s.currentAct){panX=panY=0;zoom=1;positions.clear();geometryHash=0;sealUntil=0;}if(state!=null){if(state.currentAct==1&&s.currentAct==1&&!state.flags.contains("b4_sealed")&&s.flags.contains("b4_sealed"))sealUntil=s.gameTime+2.8;for(var e:s.events)if(e.id>lastAlert&&e.category.equals("УГРОЗА")&&s.rooms.containsKey(e.roomId)){var p=s.rooms.get(e.roomId).center();pulseX=p.x();pulseY=p.y();pulseTime=System.nanoTime()/1e9;}}int ownFloor=CampaignPlan.floor(s,s.actor(own).roomId);if(state==null||state.currentAct!=s.currentAct||ownFloor!=lastOwnFloor){displayFloor=ownFloor;panX=panY=0;zoom=1;positions.clear();geometryHash=0;}lastOwnFloor=ownFloor;lastAlert=s.eventSequence;state=s;this.own=own;}
    public void center(String id){if(state==null||!state.rooms.containsKey(id))return;var p=state.rooms.get(id).center();displayFloor=CampaignPlan.floor(state,id);panX=(455-(p.x()-displayFloor*1000))*zoom;panY=(235-p.y())*zoom;geometryHash=0;}
    public double zoom(){return zoom;}
    public void changeFloor(int delta){if(state==null||state.currentAct<3)return;displayFloor=Math.clamp(displayFloor+delta,0,1);panX=panY=0;zoom=1;geometryHash=0;}
    public int displayFloor(){return displayFloor;}
    private boolean onFloor(String room){return CampaignPlan.floor(state,room)==displayFloor;}
    private Point world(double x,double y){return new Point((x*910/getWidth()-panX)/zoom+displayFloor*1000,(y*470/getHeight()-panY)/zoom);}
    private String room(Point p){return state.rooms.values().stream().filter(r->r.contains(p.x(),p.y())).map(r->r.id).findFirst().orElse("");}
    @Override protected void layoutChildren(){for(var c:List.of(structure,dynamic))c.resize(getWidth(),getHeight());geometryHash=0;}
    private void transform(GraphicsContext g){g.scale(getWidth()/910,getHeight()/470);g.translate(panX,panY);g.scale(zoom,zoom);g.translate(-displayFloor*1000,0);}
    public void render(boolean motion){if(state==null)return;long now=System.nanoTime();double dt=lastRender==0?.016:Math.min(.1,(now-lastRender)/1e9);lastRender=now;
        int hash=Objects.hash(zoom,panX,panY,getWidth(),getHeight(),layer,state.layoutRevision,displayFloor);for(var r:state.rooms.values())hash=31*hash+Objects.hash(r.id,r.x,r.y,r.w,r.h,r.powered);
        if(hash!=geometryHash){drawStructure();geometryHash=hash;}
        var g=dynamic.getGraphicsContext2D();g.clearRect(0,0,getWidth(),getHeight());g.save();transform(g);
        for(var r:state.rooms.values()){if(!onFloor(r.id))continue;
            if(state.currentAct==3&&r.floor==0){g.setFill(Color.color(.1,.4,.5,Math.min(.3,state.progress.getOrDefault("hydro_flood",0d)/350)));g.fillRect(r.x,r.y,r.w,r.h);}
            if(r.biomass>.15)biomass(g,r,motion?state.gameTime:0);
            if(layer.equals("ПИТАНИЕ")){g.save();path(g,r);g.clip();g.setFill(r.powered?Color.web("#42c78b19"):Color.web("#d049492c"));g.fillRect(r.x,r.y,r.w,r.h);g.restore();}
            if(layer.equals("ВОЗДУХ")){g.save();path(g,r);g.clip();g.setFill(Color.color(.1,.5,.6,Math.clamp((100-r.oxygen)/150,0,.6)));g.fillRect(r.x,r.y,r.w,r.h);g.restore();}
        }
        for(var d:state.doors.values())if(onFloor(d.a))door(g,d);for(var r:state.rooms.values())if(onFloor(r.id))labels(g,r,r.id.equals("B4")&&state.flags.contains("b4_sealed"));
        if(sealUntil>state.gameTime&&state.rooms.containsKey("B4")){double alpha=Math.min(1,(sealUntil-state.gameTime)*.65);g.setStroke(GREEN.deriveColor(0,1,1,alpha));g.setLineWidth(2);path(g,state.rooms.get("B4"));g.stroke();}
        drawObjects(g);
        String camera=EchoRules.cameraRoom(state,state.actor(own));if(!camera.isEmpty()&&onFloor(camera)){g.setStroke(CYAN);g.setLineWidth(1.5);g.setLineDashes(5,3);path(g,state.rooms.get(camera));g.stroke();g.setLineDashes();var r=state.rooms.get(camera);TerminalDraw.text(g,"КАМЕРА ЭХО / "+(int)Math.ceil(state.actor(own).echoViewUntil-state.gameTime)+"с",r.x+10,r.y+28,10,CYAN);}
        for(var mark:state.tacticalMarks.values())if(mark.expiresAt>state.gameTime&&state.rooms.containsKey(mark.roomId)&&onFloor(mark.roomId)){var p=state.rooms.get(mark.roomId).center();g.setStroke(CYAN);g.setLineWidth(1.5);g.strokePolygon(new double[]{p.x(),p.x()+11,p.x(),p.x()-11},new double[]{p.y()-11,p.y(),p.y()+11,p.y()},4);TerminalDraw.text(g,"ЭХО "+state.actor(mark.owner).id+" / "+(int)Math.ceil(mark.expiresAt-state.gameTime)+"с",p.x()+15,p.y()-14,10,CYAN);}
        for(var a:state.actors){if(!onFloor(a.roomId))continue;if(a.life.equals("ECHO")){TerminalDraw.icon(g,"terminal",a.x-8,a.y-8,16,CYAN);TerminalDraw.text(g,"ЭХО / "+a.id,a.x+12,a.y-8,10,CYAN);continue;}if(a.life.equals("DORMANT")||a.life.equals("EVACUATED"))continue;Color color=PLAYERS[a.slot];g.setStroke(color.deriveColor(0,.9,.85,.7));g.setLineWidth(1.5);g.setLineDashes(4,4);g.setLineDashOffset(motion?-state.gameTime*6:0);g.beginPath();g.moveTo(a.x,a.y);for(var p:a.path)g.lineTo(p.x(),p.y());g.stroke();g.setLineDashes();g.setLineDashOffset(0);
            Point old=positions.getOrDefault(a.slot,a.position());var p=motion&&state.phase==Phase.ACTION?old.lerp(a.position(),1-Math.exp(-dt*18)):a.position();positions.put(a.slot,p);marker(g,p.x(),p.y(),a.slot,color,a.connected,a.slot==own);if(a.hidden)TerminalDraw.text(g,"УКРЫТ",p.x()+12,p.y()+13,9,MUTED);
        }
        for(var c:state.contacts.values()){if(!onFloor(c.roomId))continue;boolean stale=!c.certainty.equals("CONFIRMED");var color=stale?AMBER:RED;TerminalDraw.icon(g,"danger",c.x-8,c.y-8,16,color);if(!stale&&"RECOVERING".equals(c.behavior))TerminalDraw.text(g,"ОТХОДИТ / НЕ УНИЧТОЖЕНА",c.x+10,c.y-24,10,CYAN);if(c.suppressedUntil>state.gameTime)TerminalDraw.text(g,"ЗАДЕРЖАНА "+(int)Math.ceil(c.suppressedUntil-state.gameTime)+"с",c.x+10,c.y-10,10,CYAN);if(stale)TerminalDraw.text(g,"? "+(int)(state.gameTime-c.observedAt)+"с",c.x+10,c.y,9,AMBER);}
        for(var drone:state.drones){if(!onFloor(drone.roomId))continue;var color=drone.state.equals("LOST")?RED:CYAN;g.setStroke(color);g.setLineWidth(.9);g.strokeRect(drone.x-4,drone.y-4,8,8);g.strokeLine(drone.x-7,drone.y,drone.x+7,drone.y);g.strokeLine(drone.x,drone.y-7,drone.x,drone.y+7);if(!drone.state.equals("DOCKED"))TerminalDraw.text(g,drone.id+" "+Math.round(drone.battery)+"%",drone.x+10,drone.y,9,color);}
        if(state.currentAct>=3){TerminalDraw.text(g,"ЭТАЖ "+(displayFloor==0?"НИЖНИЙ":"ВЕРХНИЙ")+" / PgUp · PgDown / Home — свой герой",displayFloor*1000+30,24,12,CYAN);if(state.currentAct==3)TerminalDraw.text(g,"УРОВЕНЬ ВОДЫ: "+Math.round(state.progress.getOrDefault("hydro_flood",0d))+"%",displayFloor*1000+600,24,12,AMBER);}
        if(motion&&state.phase==Phase.ACTION){double age=now/1e9-pulseTime;if(age<2.4){double radius=12+age*32;g.setStroke(Color.color(.25,.78,.65,(1-age/2.4)*.6));g.setLineWidth(.8);g.strokeOval(pulseX-radius,pulseY-radius,radius*2,radius*2);}}g.restore();
    }
    private void drawStructure(){var g=structure.getGraphicsContext2D();g.setFill(Color.web("#040c09"));g.fillRect(0,0,getWidth(),getHeight());g.save();transform(g);
        g.setStroke(Color.web("#18332a"));g.setLineWidth(.35);for(int x=0;x<920;x+=24)g.strokeLine(x,0,x,470);for(int y=12;y<470;y+=24)g.strokeLine(0,y,910,y);
        g.setStroke(Color.web("#20403644"));g.setLineWidth(.7);for(int x=44;x<910;x+=96)g.strokeLine(x,12,x,450);for(int y=28;y<460;y+=96)g.strokeLine(35,y,900,y);
        for(int i=0;i<17;i++)TerminalDraw.mono(g,String.format("%02d",i+1),53+i*48,7,9,Color.web("#648379"));for(int i=0;i<8;i++)TerminalDraw.mono(g,String.valueOf((char)('A'+i)),0,44+i*49,10,MUTED);
        if(state.currentAct==1)tunnels(g);
        for(var d:state.doors.values()){if(!onFloor(d.a))continue;
            g.setStroke(Color.web("#283c34"));g.setLineWidth(22);g.strokeLine(d.pa.x(),d.pa.y(),d.pb.x(),d.pb.y());g.setStroke(Color.web("#08140e"));g.setLineWidth(17);g.strokeLine(d.pa.x(),d.pa.y(),d.pb.x(),d.pb.y());g.setStroke(Color.web("#10281d"));g.setLineWidth(11);g.strokeLine(d.pa.x(),d.pa.y(),d.pb.x(),d.pb.y());
            g.setStroke(Color.web("#7b877344"));g.setLineWidth(.65);double dx=Math.abs(d.pa.x()-d.pb.x())<1?8:0,dy=dx==0?8:0;g.strokeLine(d.pa.x()-dx,d.pa.y()-dy,d.pb.x()-dx,d.pb.y()-dy);g.strokeLine(d.pa.x()+dx,d.pa.y()+dy,d.pb.x()+dx,d.pb.y()+dy);
        }
        for(var r:state.rooms.values()){if(!onFloor(r.id))continue;
            g.setFill(r.powered?Color.web("#05100c"):Color.web("#040a08"));path(g,r);g.fill();g.setStroke(Color.web("#273a30"));g.setLineWidth(3);path(g,r);g.stroke();g.setStroke(Color.web("#71847555"));g.setLineWidth(.75);path(g,r);g.stroke();
            g.save();path(g,r);g.clip();g.setStroke(Color.web("#385348"));g.setLineWidth(.6);g.strokeRect(r.x+5,r.y+5,r.w-10,r.h-10);g.strokeRect(r.x+8,r.y+8,r.w-16,r.h-16);furniture(g,r);g.restore();
        }if(state.currentAct==1)g.drawImage(ReferenceAssets.architecture(),0,0);TerminalDraw.noise(g,910,470,13013);g.restore();
    }
    private void drawObjects(GraphicsContext g){for(var f:WorldObjects.all(state)){if(!onFloor(f.room()))continue;
        boolean active=WorldObjects.offers(state,state.actor(own),f).stream().anyMatch(WorldObjects.Offer::enabled);var color=f.id().equals("exit")&&state.flags.contains("exit_ready")?GREEN:active?Color.web("#79bda0"):Color.web("#526f62");
        g.setFill(Color.web("#06130feb"));g.fillRect(f.x()-14,f.y()-14,28,28);g.setStroke(f.id().equals(hoveredObject)?CYAN:color.deriveColor(0,1,1,.55));g.setLineWidth(f.id().equals(hoveredObject)?1.5:.7);g.strokeRect(f.x()-14,f.y()-14,28,28);TerminalDraw.icon(g,f.icon(),f.x()-10,f.y()-10,20,color);
        if(f.id().equals("sample")&&state.flags.contains("sample_goal")&&!state.flags.contains("hive_core_studied")||f.id().equals("exit")&&state.flags.contains("archive_access")){g.setStroke(AMBER);g.setLineWidth(.8);g.strokeRect(f.x()-17,f.y()-17,34,34);}
    }}
    private static void path(GraphicsContext g,Room r){g.beginPath();if(r.outline.isEmpty())g.rect(r.x,r.y,r.w,r.h);else{g.moveTo(r.outline.getFirst().x(),r.outline.getFirst().y());for(var p:r.outline)g.lineTo(p.x(),p.y());g.closePath();}}
    private static void tunnels(GraphicsContext g){g.setStroke(Color.web("#43564a88"));g.setLineWidth(.7);
        double[][] paths={{36,26,36,49,11,49,11,447,78,447,78,457,890,457,890,127,862,127,862,103,804,103,804,56,647,56},{46,26,46,39,25,39,25,432,68,432,68,446,879,446,879,139,850,139,850,116,792,116,792,69,650,69},{73,184,73,213,79,213,79,326,59,326,59,424,214,424,214,448},{305,34,305,81,314,81,314,205,304,205,304,224},{758,188,776,188,776,221,903,221,903,347,760,347,760,427,709,427}};
        for(var pts:paths){g.beginPath();g.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)g.lineTo(pts[i],pts[i+1]);g.stroke();}
        var random=new Random(19);for(var pts:paths)for(int i=2;i<pts.length;i+=2){double x=pts[i],y=pts[i+1];for(int j=0;j<4;j++){double ox=random.nextDouble()*18,oy=random.nextDouble()*16;g.strokeRect(x-ox,y-oy,5+ox,4+oy);}}
        TerminalDraw.tracked(g,"ТЕХНИЧЕСКИЕ ТОННЕЛИ",61,28,9,.8,MUTED);TerminalDraw.tracked(g,"СЛУЖЕБНЫЕ ТОННЕЛИ",59,451,9,.8,MUTED);
        g.setStroke(MUTED);g.setLineWidth(1);g.strokeLine(43,22,43,43);g.strokeLine(39,27,43,22);g.strokeLine(47,27,43,22);g.strokeLine(43,449,43,465);g.strokeLine(39,460,43,465);g.strokeLine(47,460,43,465);
        TerminalDraw.icon(g,"danger",797,49,15,RED);TerminalDraw.tracked(g,"К СЕКТОРУ C",819,51,10,.6,MUTED);TerminalDraw.tracked(g,"НЕРАЗВЕДАНО",819,63,9,.6,MUTED);
        g.setStroke(RED);g.strokeLine(742,400,754,412);g.strokeLine(754,400,742,412);TerminalDraw.text(g,"АВАРИЙНЫЙ ВЫХОД",764,406,9,MUTED);TerminalDraw.text(g,"(ЗАВАЛ)",764,418,9,MUTED);
        TerminalDraw.text(g,"МАСШТАБ ×1.00",794,447,9,MUTED);g.setStroke(MUTED);g.strokeLine(748,463,898,463);for(int i=0;i<4;i++){g.strokeLine(748+i*50,459,748+i*50,465);TerminalDraw.mono(g,Integer.toString(i*5),745+i*50,456,8,MUTED);}TerminalDraw.text(g,"м",903,456,8,MUTED);
    }
    private static void furniture(GraphicsContext g,Room r){var random=new Random(r.id.hashCode()*13L);g.setStroke(Color.web("#58746266"));g.setLineWidth(.6);
        for(int i=0;i<r.w/14;i++){double x=r.x+9+i*14;g.strokeRect(x,r.y+4,7,7);g.strokeLine(x+3,r.y+4,x+3,r.y+9);g.strokeRect(x,r.y+r.h-12,8,7);}
        for(int i=0;i<r.h/18;i++){double y=r.y+10+i*18;g.strokeRect(r.x+4,y,8,9);g.strokeRect(r.x+r.w-12,y,8,9);}
        for(int i=0;i<12;i++){double w=5+random.nextInt(10),h=5+random.nextInt(13),x=r.x+10+random.nextDouble()*(r.w-25),y=r.y+10+random.nextDouble()*(r.h-25);if(x>r.x+20&&x<r.x+r.w-30&&y>r.y+25&&y<r.y+r.h-30)continue;g.strokeRect(x,y,w,h);g.strokeLine(x+2,y+2,x+w-2,y+2);g.strokeLine(x+2,y+h-2,x+w-2,y+h-2);}
        if(r.id.equals("B1"))for(int i=0;i<4;i++){double x=r.x+20+i*24;g.strokeRect(x,r.y+13,12,16);g.strokeLine(x+3,r.y+18,x+9,r.y+18);g.strokeLine(x+3,r.y+23,x+9,r.y+23);}
        if(r.id.equals("B2")){g.strokeRect(r.x+30,r.y+24,44,36);g.strokeRect(r.x+34,r.y+28,36,28);for(int i=0;i<5;i++)g.strokeLine(r.x+38+i*6,r.y+30,r.x+38+i*6,r.y+53);}
        if(r.id.equals("B7"))for(int i=0;i<3;i++){double x=r.x+55+i*28;g.strokeRect(x,r.y+r.h-29,18,15);g.strokeOval(x+4,r.y+r.h-25,7,7);}
        if(r.id.equals("C2")){g.strokeRect(r.x+14,r.y+14,r.w-28,12);g.strokeRect(r.x+14,r.y+30,12,36);g.strokeRect(r.x+r.w-26,r.y+30,12,36);}
    }
    private static void biomass(GraphicsContext g,Room r,double time){g.save();path(g,r);g.clip();g.setGlobalAlpha(time==0?1:.88+.12*Math.sin(time*.7+r.id.hashCode()));var random=new Random(r.id.hashCode()*7147L);double rootX=r.x+r.w*.6,rootY=r.id.equals("B4")?r.y+95:r.y+r.h*.55;
        g.setFill(new RadialGradient(0,0,rootX,rootY,Math.max(r.w,r.h)*.8,false,CycleMethod.NO_CYCLE,new Stop(0,Color.color(.43,.07,.10,Math.min(.46,r.biomass/155))),new Stop(1,Color.color(.27,.035,.05,.02))));g.fillRect(r.x,r.y,r.w,r.h);
        for(int i=0;i<(int)(r.biomass*3.1);i++){double x=r.x+random.nextDouble()*r.w,y=r.y+random.nextDouble()*r.h;if(r.id.equals("B4")&&y>r.y+172&&random.nextDouble()>.25)continue;double angle=random.nextDouble()*Math.PI*2;g.setStroke(Color.color(.48+random.nextDouble()*.22,.09+random.nextDouble()*.06,.13+random.nextDouble()*.05,.25+random.nextDouble()*.43));g.setLineWidth(.35+random.nextDouble()*.65);g.beginPath();g.moveTo(x,y);
            for(int j=0;j<5;j++){double length=5+random.nextDouble()*13;angle+=random.nextDouble()*1.7-.85;double nx=x+Math.cos(angle)*length,ny=y+Math.sin(angle)*length;g.bezierCurveTo(x+Math.sin(angle)*7,y-Math.cos(angle)*7,nx-Math.sin(angle)*7,ny+Math.cos(angle)*7,nx,ny);x=nx;y=ny;}g.stroke();if(i%5==0){g.strokeOval(x-2,y-2,4,4);g.strokeLine(x,y,x+random.nextDouble()*18-9,y+random.nextDouble()*18-9);}}
        if(r.biomass>30){g.setStroke(Color.web("#903d43"));g.setLineWidth(.9);path(g,r);g.stroke();}g.restore();
    }
    private void labels(GraphicsContext g,Room r,boolean isolated){var p=r.labelAnchor==null?new Point(r.x+12,r.y+24):r.labelAnchor;var c=r.biomass>45?RED:Color.web("#a8c8b9");if(!r.id.equals("H"))TerminalDraw.mono(g,r.id,p.x(),p.y(),14,c);
        String[] lines=switch(r.id){case "B2"->new String[]{"СИЛОВОЙ","БЛОК"};case "B4"->new String[]{"УЛЕЙ",isolated?"(ИЗОЛИРОВАН)":"(АКТИВНЫЙ)"};case "H"->new String[]{"ЦЕНТРАЛЬНЫЙ","КОРИДОР"};case "C1"->new String[]{"ДЕЗАКТИВАЦИЯ"};case "C2"->new String[]{"ЭВАКУАЦИОННЫЙ","ШАХТ"};case "B8"->new String[]{"ХРАНИЛИЩЕ","ОБРАЗЦОВ"};default->new String[]{r.name};};
        for(int i=0;i<lines.length;i++)TerminalDraw.tracked(g,lines[i],p.x(),p.y()+(r.id.equals("H")?0:17)+i*12,11,.55,r.biomass>45?RED:MUTED);
        double[] anchor=switch(r.id){case "B1"->new double[]{75,125};case "B2"->new double[]{223,136};case "B3"->new double[]{399,151};case "B4"->new double[]{535,75};case "B5"->new double[]{674,102};case "B6"->new double[]{306,406};case "B8"->new double[]{596,390};case "A1"->new double[]{105,278};case "A3"->new double[]{105,374};case "C1"->new double[]{651,284};default->new double[]{p.x(),Math.min(r.y+r.h-27,p.y()+22+lines.length*12)};};
        String icon=switch(r.id){case "B1","B6"->"terminal";case "B2"->"power";case "B3"->"vent";case "B4"->"danger";case "B5"->"bio";case "B7"->"lab";case "B8"->"store";case "A1"->"bed";case "A2"->"medical";case "C1"->"filter";case "C2"->"exit";default->"terminal";};if(!r.id.equals("H")&&WorldObjects.all(state).stream().noneMatch(f->f.room().equals(r.id)))TerminalDraw.icon(g,icon,anchor[0],anchor[1],23,r.biomass>20?RED:Color.web("#6ba08a"));
    }
    private static void door(GraphicsContext g,Door d){var p=d.center();boolean vertical=Math.abs(d.pa.x()-d.pb.x())>=Math.abs(d.pa.y()-d.pb.y());g.save();g.translate(p.x(),p.y());if(!vertical)g.rotate(90);var color=d.passable()?Color.web("#52c887"):d.status==DoorStatus.LOCKED?AMBER:RED;
        g.setStroke(Color.web("#728677"));g.setLineWidth(.7);g.strokeRect(-6,-14,12,28);g.setFill(Color.web("#07120f"));g.fillRect(-3,-12,6,24);g.setFill(color);g.fillRect(-1.5,-12,3,24);g.setStroke(color.deriveColor(0,1,1,.35));g.setLineWidth(3);g.strokeLine(0,-10,0,10);g.setLineWidth(.7);g.strokeLine(-5,-8,5,-8);g.strokeLine(-5,8,5,8);if(d.status==DoorStatus.SEALED){g.strokeLine(-7,-6,7,6);g.strokeLine(7,-6,-7,6);}g.restore();}
    private static void marker(GraphicsContext g,double x,double y,int slot,Color color,boolean online,boolean own){g.save();g.translate(x,y);g.setFill(color.deriveColor(0,.9,.75,.2));g.fillOval(-15,-15,30,30);g.setStroke(color);g.setLineWidth(1);
        if(slot==0){g.fillPolygon(new double[]{0,-10,10},new double[]{-15,8,8},3);g.strokePolygon(new double[]{0,-10,10},new double[]{-15,8,8},3);TerminalDraw.icon(g,"power",-6,-10,12,color);}else{g.setFill(color);g.beginPath();g.moveTo(0,3);g.bezierCurveTo(-20,-16,-14,-32,0,-32);g.bezierCurveTo(14,-32,20,-16,0,3);g.fill();g.setFill(Color.web("#08201a"));g.setFont(ReferenceAssets.strong(15));g.fillText(slot==1?"B2":"C3",-8,-13);}
        if(own){g.setStroke(color.deriveColor(0,1,1,.5));g.strokeOval(-16,-17,32,32);}if(!online)TerminalDraw.text(g,"OFFLINE",11,5,9,AMBER);g.restore();}
}
