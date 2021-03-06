package cern.c2mon.server.configuration.config;

import cern.c2mon.server.daq.out.ProcessCommunicationManager;
import org.easymock.EasyMock;
import org.springframework.context.annotation.Bean;

/**
 * @author Justin Lewis Salmon
 */
public class ProcessCommunicationManagerMock {

  @Bean
  public ProcessCommunicationManager processCommunicationManagerImpl() {
    return EasyMock.createMock(ProcessCommunicationManager.class);
  }
}
