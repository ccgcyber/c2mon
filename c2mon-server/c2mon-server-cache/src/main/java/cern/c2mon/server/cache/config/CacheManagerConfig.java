package cern.c2mon.server.cache.config;

import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.Resource;
import java.io.IOException;

import static java.lang.String.format;

/**
 * @author Justin Lewis Salmon
 */
@Configuration
public class CacheManagerConfig {

  @Resource
  private CacheProperties properties;

  @Bean
  public EhCacheManagerFactoryBean ehCacheManagerFactoryBean() throws IOException {
    String cacheMode = properties.getMode();

    switch (cacheMode) {
      case "single-nonpersistent":
        return getEhCacheManagerFactoryBean("ehcache/ehcache-single-nonpersistent.xml");
      case "single":
        return getEhCacheManagerFactoryBean("ehcache/ehcache-single.xml");
      case "multi":
        return getEhCacheManagerFactoryBean("ehcache/ehcache-multi.xml");
      default:
        throw new IOException(format("Unsupported cache mode specified: '%s'", cacheMode));
    }

    
  }

  private EhCacheManagerFactoryBean getEhCacheManagerFactoryBean(String configLocation) {
    EhCacheManagerFactoryBean bean = new EhCacheManagerFactoryBean();
    bean.setConfigLocation(new ClassPathResource(configLocation));
    bean.setShared(true);
    return bean;
  }
}
