package ca.bc.gov.nrs.frep.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class GlobalConfiguration {

  /**
   * The application's {@link ObjectMapper}, with every incoming string trimmed.
   *
   * <p>Trimming at the deserialization boundary rather than per field: the alternative is a
   * {@code .trim()} on each of the hundreds of text values that reach a proc or an entity, which is
   * a rule no one can enforce and which was already inconsistent — CHR photo descriptions were
   * trimmed, Biodiversity attachment descriptions were not.
   *
   * <p>It also protects the byte limits. The columns are {@code VARCHAR2(n BYTE)} and the frontend
   * counters measure bytes, so trailing whitespace is spent budget: a value that fits only because
   * the user did not notice the newline they pasted after it still fails with ORA-12899. Trimming
   * first means the length the database sees is the length the text actually needs.
   *
   * <p>Deserialization only — responses are untouched — and no field in this schema treats leading
   * or trailing whitespace as meaningful. Multipart form parts do not pass through Jackson, so the
   * two upload descriptions are trimmed explicitly at their services.
   */
  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    SimpleModule trimming = new SimpleModule();
    trimming.addDeserializer(String.class, new TrimmingStringDeserializer());
    return builder.build().registerModule(trimming);
  }

  /** Delegates to Jackson's own string handling, then trims. Null stays null; blank stays blank. */
  private static final class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

    private static final long serialVersionUID = 1L;

    TrimmingStringDeserializer() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      String value = StringDeserializer.instance.deserialize(parser, context);
      return value == null ? null : value.trim();
    }
  }
}
