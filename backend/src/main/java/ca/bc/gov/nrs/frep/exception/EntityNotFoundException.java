package ca.bc.gov.nrs.frep.exception;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/** A requested resource does not exist. Mapped to HTTP 404 by {@link RestExceptionHandler}. */
public class EntityNotFoundException extends RuntimeException {

  /** Plain human-readable message (e.g. {@code "Checklist 123 was not found."}). */
  public EntityNotFoundException(String message) {
    super(message);
  }

  /**
   * nr-fspts-style: builds {@code "<Entity> was not found for parameters {key=value,…}"} from a class
   * and alternating {@code key, value} search params (must be an even number of params).
   */
  public EntityNotFoundException(Class<?> clazz, String... searchParamsMap) {
    super(EntityNotFoundException.generateMessage(clazz.getSimpleName(), toMap(String.class, String.class, searchParamsMap)));
  }

  private static String generateMessage(String entity, Map<String, String> searchParams) {
    return StringUtils.capitalize(entity) +
      " was not found for parameters " +
      searchParams;
  }

  private static <K, V> Map<K, V> toMap(
    Class<K> keyType, Class<V> valueType, Object... entries) {
    if (entries.length % 2 == 1)
      throw new IllegalArgumentException("Invalid entries");
    return IntStream.range(0, entries.length / 2).map(i -> i * 2)
      .collect(HashMap::new,
        (m, i) -> m.put(keyType.cast(entries[i]), valueType.cast(entries[i + 1])),
        Map::putAll);
  }
}
