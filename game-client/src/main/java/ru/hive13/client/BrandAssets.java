package ru.hive13.client;
import javafx.scene.image.Image;
public final class BrandAssets {
    private BrandAssets(){}
    public static final Image ICON=load("hive-icon.png"),MENU=load("menu-background-v1.png"),PORTRAITS=load("portraits-v1.png");
    private static Image load(String name){var stream=BrandAssets.class.getResourceAsStream("/assets/brand/"+name);if(stream==null)throw new IllegalStateException("Missing original brand asset: "+name);return new Image(stream);}
}
