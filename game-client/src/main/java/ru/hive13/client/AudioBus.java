package ru.hive13.client;
import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import ru.hive13.model.Model.*;
import static ru.hive13.client.OriginalScore.Cue;

/** One persistent stereo mixer: ambient music, event effects and physical movement footsteps. */
public final class AudioBus implements AutoCloseable {
    private record Voice(Cue cue,long start){}
    private final ConcurrentLinkedQueue<Voice> pending=new ConcurrentLinkedQueue<>();private final AtomicLong clock=new AtomicLong();private volatile boolean running=true;private volatile SourceDataLine output;private long lastEvent;private Campaign previous;private Point lastPosition;private double distance;
    public volatile boolean enabled=true,musicEnabled=true;public volatile double effectsVolume=.65,musicVolume=.32;public volatile String status="Инициализация аудио";private volatile double tension;private volatile int chapter=1;
    public AudioBus(){if(Boolean.getBoolean("hive.mute")){status="Звук отключён для проверки";return;}Thread.ofPlatform().name("hive-audio").daemon(true).start(this::mix);}
    public void cue(Cue cue){if(enabled&&!Boolean.getBoolean("hive.mute")&&pending.size()<12)pending.offer(new Voice(cue,clock.get()));}
    public void cue(boolean danger){cue(danger?Cue.ALERT:Cue.SELECT);}
    public void update(Campaign state,int own){chapter=state.currentAct;tension=Math.clamp(state.biomass()/100+state.actor(own).stress/150+(state.contacts.values().stream().anyMatch(c->"CONFIRMED".equals(c.certainty))?.3:0),0,1);if(previous!=null){for(var event:state.events)if(event.id>lastEvent){String text=event.text;if(event.category.equals("БОЙ"))cue(text.contains("Дробовик")?Cue.SHOT:text.contains("Электрошок")?Cue.SHOCK:Cue.FLARE);else if(text.contains("этаж.")||text.contains("лестничной шахте"))cue(Cue.STAIRS);else if(text.contains("затоплен")||text.contains("насос"))cue(Cue.WATER);else if(text.contains("стук"))cue(Cue.KNOCK);else if(event.category.equals("УГРОЗА"))cue(Cue.ALERT);else if(text.startsWith("РЕШЕНИЕ ПРИНЯТО"))cue(Cue.ACCEPT);else if(text.contains("Время истекло"))cue(Cue.VOTE);else if(text.contains("шлюз"))cue(Cue.DOOR);else if(text.contains("обработана область"))cue(Cue.HEAL);}}lastEvent=state.eventSequence;var p=state.actor(own).position();if(previous!=null&&state.phase==Phase.ACTION&&lastPosition!=null){distance+=Math.min(20,lastPosition.distance(p));if(distance>7){distance=0;cue(Cue.STEP);}}lastPosition=p;previous=state;}
    private void mix(){final int rate=48000,frames=960;var voices=new ArrayList<Voice>();try{var line=AudioSystem.getSourceDataLine(new AudioFormat(rate,16,2,true,false));output=line;line.open(new AudioFormat(rate,16,2,true,false),frames*4*5);line.start();status="СТЕРЕО / 48 кГц";byte[] buffer=new byte[frames*4];long frame=0;while(running){Voice next;while((next=pending.poll())!=null){if(voices.size()>=12)voices.removeFirst();voices.add(next);}long current=frame;voices.removeIf(v->(current-v.start)/(double)rate>OriginalScore.duration(v.cue));for(int i=0;i<frames;i++){double t=(frame+i)/(double)rate;for(int channel=0;channel<2;channel++){double sample=musicEnabled?OriginalScore.music(t,channel,tension,chapter)*musicVolume:0;if(enabled)for(var v:voices)sample+=OriginalScore.effect(v.cue,(frame+i-v.start)/(double)rate,channel)*effectsVolume;short value=OriginalScore.pcm(sample);int offset=i*4+channel*2;buffer[offset]=(byte)value;buffer[offset+1]=(byte)(value>>8);}}line.write(buffer,0,buffer.length);frame+=frames;clock.set(frame);}}catch(Exception e){if(running){status="Аудиовыход недоступен";System.err.println("Аудио: "+e.getClass().getSimpleName()+": "+e.getMessage());}}finally{var line=output;if(line!=null){line.stop();line.close();}}}
    @Override public void close(){running=false;var line=output;if(line!=null){line.stop();line.flush();line.close();}}
    public static void main(String[] args)throws Exception{try(var bus=new AudioBus()){Thread.sleep(1500);System.out.println(bus.status);if(!bus.status.startsWith("СТЕРЕО"))throw new IllegalStateException(bus.status);}}
}
