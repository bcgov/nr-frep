package ca.bc.gov.nrs.frep;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
// Enables @Cacheable on the static configuration code lists (default in-memory ConcurrentMapCacheManager).
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class FrepApiApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(FrepApiApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(FrepApiApplication.class, args);
	}

}
