package ru.hive13.client;

/** Canvas pixels follow the final window and fractional Windows DPI.
 * JavaFX Canvas internally uses ceil(max monitor scale) pixels per canvas unit. */
final class CanvasRaster {
    private CanvasRaster() {}
    record Plan(double densityX,double densityY,double width,double height,
                double offsetX,double offsetY,double translateX,double translateY) {}
    static Plan plan(double width,double height,double uiX,double uiY,double originX,double originY,
                     double outputX,double outputY,double nativeScale) {
        double deviceX=Math.max(.01,Math.abs(uiX)*outputX),deviceY=Math.max(.01,Math.abs(uiY)*outputY);
        double fractionX=fraction(originX*outputX),fractionY=fraction(originY*outputY);
        return new Plan(deviceX/nativeScale,deviceY/nativeScale,
                Math.ceil(width*deviceX+fractionX-1e-7)/nativeScale,
                Math.ceil(height*deviceY+fractionY-1e-7)/nativeScale,
                -fractionX/deviceX,-fractionY/deviceY,fractionX/nativeScale,fractionY/nativeScale);
    }
    private static double fraction(double value) {double result=value-Math.floor(value);return result<1e-7||result>1-1e-7?0:result;}
}
