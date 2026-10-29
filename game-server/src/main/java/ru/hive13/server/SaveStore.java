package ru.hive13.server;
import ru.hive13.model.*;
import static ru.hive13.model.Model.*;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
public final class SaveStore {
    public record Envelope(int version,String checksum,com.fasterxml.jackson.databind.JsonNode state){}
    private SaveStore(){}
    private static String hash(com.fasterxml.jackson.databind.JsonNode s)throws IOException{try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.MAPPER.writeValueAsBytes(s)));}catch(NoSuchAlgorithmException e){throw new IOException(e);}}
    public static void save(Path file,Campaign state)throws IOException{
        Files.createDirectories(file.toAbsolutePath().getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp"),bak=file.resolveSibling(file.getFileName()+".bak");
        com.fasterxml.jackson.databind.JsonNode payload=Json.MAPPER.valueToTree(state);
        Files.write(tmp,Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Envelope(1,hash(payload),payload)));load(tmp);
        if(Files.exists(file))Files.copy(file,bak,StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    public static Campaign load(Path file)throws IOException{
        var e=Json.MAPPER.readValue(Files.readAllBytes(file),Envelope.class);if(e.version()!=1||e.state().path("schemaVersion").asInt()!=1)throw new IOException("Неподдерживаемая версия сохранения");if(!hash(e.state()).equals(e.checksum()))throw new IOException("Контрольная сумма сохранения не совпадает");
        var state=Json.MAPPER.treeToValue(e.state(),Campaign.class);
        if(state.actors.size()!=3||state.rooms.isEmpty())throw new IOException("Неполная кампания");Object13Plan.migrate(state);return state;
    }
}
