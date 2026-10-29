package ru.hive13.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class ConnectionHintsTest {
    @Test void explicitBindKeepsTheChosenPort(){assertEquals(List.of("26.10.20.30:27713"),ConnectionHints.forHost("26.10.20.30",27713));}
    @Test void invitationDoesNotContainAuthenticationTokensOrPasswords(){String text=ConnectionHints.invitation(List.of("192.168.1.10:27713"),"ВОЛКОВА");assertTrue(text.contains("192.168.1.10:27713"));assertTrue(text.contains("ВОЛКОВА"));assertTrue(text.contains("отдельно"));assertFalse(text.contains("token"));}
}
