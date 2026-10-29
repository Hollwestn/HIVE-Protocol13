package ru.hive13.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OriginalScoreTest {
    @Test void eachChapterThemeIsFiniteAndDiffersFromTheBase(){for(int chapter=3;chapter<=6;chapter++){double difference=0;for(int i=0;i<48000;i+=11){double t=i/48000d,value=OriginalScore.music(t,0,.8,chapter);assertTrue(Double.isFinite(value)&&Math.abs(value)<.9);difference+=Math.abs(value-OriginalScore.music(t,0,.8));}assertTrue(difference>1);}}
    @Test void musicIsFiniteStereoAndHasSafeHeadroom(){double energy=0,stereo=0;for(int i=0;i<48000*4;i+=7){double t=i/48000d,a=OriginalScore.music(t,0,.7),b=OriginalScore.music(t,1,.7);assertTrue(Double.isFinite(a)&&Math.abs(a)<.9);energy+=a*a;stereo+=Math.abs(a-b);}assertTrue(energy>1);assertTrue(stereo>1);}
    @Test void allEffectsAreAudibleBoundedAndEndInSilence(){for(var cue:OriginalScore.Cue.values()){double sum=0;for(int i=0;i<48000*OriginalScore.duration(cue);i+=5){double s=OriginalScore.effect(cue,i/48000d,0);assertTrue(Double.isFinite(s)&&Math.abs(s)<1);sum+=s*s;}assertTrue(sum>.1,cue.name());assertEquals(0,OriginalScore.effect(cue,OriginalScore.duration(cue)+.01,0));assertEquals(0,OriginalScore.effect(cue,-.01,0));}}
    @Test void limiterCannotOverflowPcm(){assertTrue(OriginalScore.pcm(10)<Short.MAX_VALUE);assertTrue(OriginalScore.pcm(-10)>Short.MIN_VALUE);assertEquals(0,OriginalScore.pcm(0));}
}
