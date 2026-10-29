package ru.hive13.client;
public final class Launcher {
    public static void main(String[] args) {
        if(System.getProperty("hive.dataDir")==null&&java.util.Arrays.asList(args).contains("--qa=true")){
            String capture=java.util.Arrays.stream(args).filter(a->a.startsWith("--capture=")).map(a->a.substring(10)).findFirst().orElse("artifacts/qa");
            System.setProperty("hive.dataDir",java.nio.file.Path.of(capture).resolve("data").toAbsolutePath().toString());
        }
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->org.slf4j.LoggerFactory.getLogger(Launcher.class).error("Необработанная ошибка thread={}",thread.getName(),error));
        javafx.application.Application.launch(HiveApplication.class, args);
    }
}
