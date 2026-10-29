package ru.hive13.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasRasterTest {
    @Test void fullHdRenders1920PixelsInsteadOfStretching1536(){
        var p=CanvasRaster.plan(1536,864,1.25,1.25,0,0,1,1,1);
        assertEquals(1920,p.width());assertEquals(1080,p.height());assertEquals(1.25,p.densityX());
    }
    @Test void smallerWindowRasterizesAt1280WithoutSecondDownsample(){
        var p=CanvasRaster.plan(1536,864,5d/6,5d/6,0,0,1,1,1);
        assertEquals(1280,p.width());assertEquals(720,p.height());
    }
    @Test void fractionalDpiAccountsForJavaFxInternalTwoXBuffer(){
        for(double dpi:new double[]{1.25,1.5,1.75,2}){
            var p=CanvasRaster.plan(1536,864,1,1,0,0,dpi,dpi,2);
            assertEquals(1536*dpi,p.width()*2);assertEquals(864*dpi,p.height()*2);
            assertEquals(dpi,p.densityX()*2);
        }
    }
    @Test void insetMapTextureOriginLandsOnDevicePixel(){
        double scale=5d/6,originX=268*scale,originY=125*scale;
        var p=CanvasRaster.plan(910,470,scale,scale,originX,originY,1,1,1);
        assertEquals(Math.rint(originX+p.offsetX()*scale),originX+p.offsetX()*scale,1e-8);
        assertEquals(Math.rint(originY+p.offsetY()*scale),originY+p.offsetY()*scale,1e-8);
        assertEquals(originX,(originX+p.offsetX()*scale)+p.translateX(),1e-8);
        assertTrue(p.width()>=910*scale+p.translateX());
    }
    @Test void densityNeverChangesLogicalInputCoordinates(){
        for(double scale:new double[]{.75,5d/6,1,1.25,2.5}){
            var p=CanvasRaster.plan(910,470,scale,scale,268*scale,125*scale,1.5,1.5,2);
            double x=427;
            double displayed=p.offsetX()+(x*p.densityX()+p.translateX())/p.densityX();
            assertEquals(x,displayed,1e-8);
        }
    }
}
