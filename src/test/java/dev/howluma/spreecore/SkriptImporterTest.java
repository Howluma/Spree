package dev.howluma.spreecore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkriptImporterTest {
    @Test void parsesQuotedCsv() {
        assertEquals(3,SkriptImporter.parseCsv("\"spree.name::uuid\",String,\"User, With Comma\"").size());
        assertEquals("User, With Comma",SkriptImporter.parseCsv("name,String,\"User, With Comma\"").get(2));
    }
}
