package ru.hive13.client;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;

/** Original deterministic dark ambient score and effects; no recordings or third-party compositions. */
public final class OriginalScore {
    public enum Cue { SELECT, STEP, DOOR, HEAL, ALERT, VOTE, ACCEPT, SHOT, SHOCK, FLARE, KNOCK, WATER, STAIRS }
    private static final double TAU=Math.PI*2;
    private OriginalScore(){}
    private static double sin(double hz,double t){return Math.sin(TAU*hz*t);}
    public static double music(double t,int channel,double tension){
        double[] roots={73.4162,58.2705,65.4064,55};double pad=0;for(int i=0;i<4;i++){double phase=(t-i*16+64)%64;double envelope=phase<20?Math.pow(Math.sin(Math.PI*phase/20),2):0;double root=roots[i],drift=(channel==0?-1:1)*.11;
            pad+=envelope*(sin(root+drift,t)*.10+sin(root*1.5-drift,t)*.055+sin(root*2+drift*.7,t)*.045+sin(root*2.3784-drift,t)*.024);}
        double[] notes={293.6648,440,349.2282,329.6276,261.6256,220,293.6648,164.8138};int index=(int)(t/4)%notes.length;double age=t%4,env=(1-Math.exp(-age*8))*Math.exp(-age*1.1);double bell=(sin(notes[index],age)+.24*sin(notes[index]*2.003,age))*env*.045*(channel==0?.8:1);
        double breath=(sin(.19,t)*.5+.5)*sin(41.2034+(channel==0?-.03:.03),t)*.035;double pulse=Math.pow(.5+.5*sin(.55,t),10)*sin(49,t)*(.02+tension*.035);return pad+bell+breath+pulse;
    }
    public static double music(double t,int channel,double tension,int chapter){double bed=music(t,channel,tension);return bed+switch(chapter){case 3->sin(36.7,t)*.018*(.5+.5*sin(.31,t));case 4->sin(46.25,t)*.026*Math.pow(.5+.5*sin(1.3+tension,t),8);case 5->(sin(98,t)+sin(103.8,t))*.012;case 6->sin(32.7,t)*.035*Math.pow(.5+.5*sin(.8+tension*.4,t),6);default->0;};}
    public static double duration(Cue cue){return switch(cue){case SELECT->.10;case STEP->.13;case DOOR->.65;case HEAL->.7;case ALERT->.85;case VOTE->1;case ACCEPT->1.3;case SHOT->.65;case SHOCK->.6;case FLARE->1.2;case KNOCK->1.4;case WATER->1.5;case STAIRS->1.1;};}
    private static double noise(double t){long x=(long)(t*48000);x=(x^(x>>>13))*1274126177L;return ((x>>>16)&65535)/32767.5-1;}
    public static double effect(Cue cue,double t,int channel){if(t<0||t>=duration(cue))return 0;double release=Math.pow(1-t/duration(cue),2),attack=Math.min(1,t/.008);return attack*release*switch(cue){
        case SELECT->sin(750-1300*t,t)*.13;
        case WATER->noise(t)*.1*(.5+.5*sin(7,t))+sin(86+20*sin(3,t),t)*.07;
        case STAIRS->(sin(105,t)+sin(247,t)*.25)*.12*Math.exp(-(t%.28)*20)+noise(t)*.04;
        case SHOT->noise(t)*.5*Math.exp(-t*18)+sin(57,t)*.23*Math.exp(-t*8);
        case SHOCK->noise(t)*.16*Math.exp(-t*4)+sin(1800+300*sin(37,t),t)*.08;
        case FLARE->noise(t)*.2*Math.exp(-t*2)+sin(280-100*t,t)*.1;
        case KNOCK->sin(58,t)*.18*Math.exp(-(t%.45)*25)+noise(t)*.06*Math.exp(-(t%.45)*40);
        case STEP->(sin(68-110*t,t)*.2+noise(t)*Math.exp(-t*42)*.09)*Math.exp(-t*8);
        case DOOR->noise(t)*.13*Math.exp(-t*4)+(sin(130,t)+sin(237.5,t)*.5)*.09;
        case HEAL->(sin(430+110*t,t)+sin(860+70*t,t)*.25)*.10*(.5+.5*sin(9,t));
        case ALERT->(sin(210+90*sin(2,t),t)+sin(420,t)*.18)*.19*(.5+.5*sin(7,t));
        case VOTE->(sin(293.6648,t)+sin(440,t)*.6)*.12;
        case ACCEPT->(sin(146.8324,t)+sin(220,t)*.6+sin(293.6648,t)*.35)*.13;
    };}
    public static short pcm(double value){return (short)Math.round(Math.tanh(value)*28000);}
    public static void main(String[] args)throws Exception{int rate=48000,seconds=args.length>1?Integer.parseInt(args[1]):12;byte[] pcm=new byte[rate*seconds*4];var cues=Cue.values();for(int i=0;i<rate*seconds;i++){double t=(double)i/rate;for(int ch=0;ch<2;ch++){int cue=(int)(t/1.5);double v=music(t,ch,.6)*.7+(cue<cues.length?effect(cues[cue],t-cue*1.5,ch)*.7:0);short s=pcm(v);int offset=i*4+ch*2;pcm[offset]=(byte)s;pcm[offset+1]=(byte)(s>>8);}}Path path=Path.of(args[0]);Files.createDirectories(path.toAbsolutePath().getParent());try(var stream=new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(rate,16,2,true,false),rate*(long)seconds)){AudioSystem.write(stream,AudioFileFormat.Type.WAVE,path.toFile());}System.out.println("Original ambient score and "+Cue.values().length+" effects: "+path);}
}
