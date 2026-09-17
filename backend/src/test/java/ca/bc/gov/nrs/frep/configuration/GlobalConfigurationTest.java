package ca.bc.gov.nrs.frep.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Every string arriving as JSON is trimmed before it can reach a proc or an entity.
 *
 * <p>The columns are {@code VARCHAR2(n BYTE)}, so trailing whitespace is spent budget — a value
 * that would otherwise fit fails with ORA-12899 because of characters the user cannot see.
 */
class GlobalConfigurationTest {

  private record Payload(String description, String code, List<String> notes) {}

  private final ObjectMapper mapper =
      new GlobalConfiguration().objectMapper(new Jackson2ObjectMapperBuilder());

  @Test
  void trimsLeadingAndTrailingWhitespaceOnEveryStringField() throws Exception {
    Payload p = mapper.readValue(
        "{\"description\":\"  a site visit  \",\"code\":\"\\tDCK\\n\"}", Payload.class);

    assertEquals("a site visit", p.description());
    assertEquals("DCK", p.code());
  }

  @Test
  void trimsTheTrailingNewlineThatSpendsTheByteBudget() throws Exception {
    // The failure this exists to stop: 2000 bytes of text plus a newline the user pasted without
    // noticing is 2001 bytes in a VARCHAR2(2000 BYTE) column.
    String body = "{\"description\":\"" + "x".repeat(2000) + "\\n\"}";

    Payload p = mapper.readValue(body, Payload.class);

    assertEquals(2000, p.description().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
  }

  @Test
  void leavesInteriorWhitespaceAlone() throws Exception {
    Payload p = mapper.readValue("{\"description\":\"  one\\n\\ntwo  \"}", Payload.class);

    // Only the ends are trimmed — a paragraph break inside the text is the user's content.
    assertEquals("one\n\ntwo", p.description());
  }

  @Test
  void trimsInsideCollectionsToo() throws Exception {
    Payload p = mapper.readValue("{\"notes\":[\"  first \",\" second  \"]}", Payload.class);

    assertEquals(List.of("first", "second"), p.notes());
  }

  @Test
  void nullStaysNullAndBlankStaysBlank() throws Exception {
    Payload p = mapper.readValue("{\"description\":null,\"code\":\"   \"}", Payload.class);

    assertNull(p.description());
    // Blank collapses to empty rather than null: callers use isBlank() checks, and turning "" into
    // null here would change which of those branches they take.
    assertEquals("", p.code());
  }
}
