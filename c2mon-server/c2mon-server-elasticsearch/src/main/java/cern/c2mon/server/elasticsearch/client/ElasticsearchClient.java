/******************************************************************************
 * Copyright (C) 2010-2017 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.server.elasticsearch.client;

import cern.c2mon.server.elasticsearch.config.ElasticsearchProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Wrapper around {@link Client}. Connects asynchronously, but also provides
 * methods to block until a healthy connection is established.
 *
 * @author Alban Marguet
 * @author Justin Lewis Salmon
 * @author James Hamilton
 */
@Slf4j
@Service
public class ElasticsearchClient {

  @Getter
  private ElasticsearchProperties properties;

  @Getter
  private RestHighLevelClient restClient;

  @Getter
  private RestClient lowLevelRestClient;

  @Getter
  private Client client;

  @Getter
  private boolean isClusterYellow;

  //static because we should only ever start 1 embedded node
  private static Node embeddedNode = null;

  @Autowired
  public ElasticsearchClient(ElasticsearchProperties properties) throws NodeValidationException {
    this.properties = properties;
    if (client == null) {
      client = createClient();

      if (properties.isEmbedded()) {
        startEmbeddedNode();
      }

      connectAsynchronously();
    }

    this.restClient = this.createRestClient();
  }

  private RestHighLevelClient createRestClient() {
    this.lowLevelRestClient = RestClient.builder(
      new HttpHost(properties.getHost(), properties.getHttpPort(), "http")
    ).build();

    RestHighLevelClient restHighLevelClient = new RestHighLevelClient(this.lowLevelRestClient);

    return restHighLevelClient;
  }

  /**
   * Creates a {@link Client} to communicate with the Elasticsearch cluster.
   *
   * @return the {@link Client} instance
   */
  private Client createClient() {
    final Settings.Builder settingsBuilder = Settings.builder();

    settingsBuilder.put("node.name", properties.getNodeName())
        .put("cluster.name", properties.getClusterName())
        .put("http.enabled", properties.isHttpEnabled());

    TransportClient client = new PreBuiltTransportClient(settingsBuilder.build());
    try {
      client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(properties.getHost()), properties.getPort()));
    } catch (UnknownHostException e) {
      log.error("Error connecting to the Elasticsearch cluster at {}:{}", properties.getHost(), properties.getPort(), e);
      return null;
    }

    return client;
  }

  /**
   * Connect to the cluster in a separate thread.
   */
  private void connectAsynchronously() {
    log.info("Trying to connect to Elasticsearch cluster {} at {}:{}",
        properties.getClusterName(), properties.getHost(), properties.getPort());

    new Thread(() -> {
      log.info("Connected to Elasticsearch cluster {}", properties.getClusterName());
      waitForYellowStatus();

    }, "EsClusterFinder").start();
  }

  /**
   * Block and wait for the cluster to become yellow.
   */
  public void waitForYellowStatus() {
    while (!isClusterYellow) {
      log.debug("Waiting for yellow status of Elasticsearch cluster...");

      try {
        ClusterHealthStatus status = getClusterHealth().getStatus();
        if (status.equals(ClusterHealthStatus.YELLOW) || status.equals(ClusterHealthStatus.GREEN)) {
          isClusterYellow = true;
          break;
        }
      } catch (Exception e) {
        log.trace("Elasticsearch cluster not yet ready: {}", e.getMessage());
      }

      try {
        Thread.sleep(100L);
      } catch (InterruptedException ignored) {}
    }

    log.debug("Elasticsearch cluster is yellow");
  }

  public ClusterHealthResponse getClusterHealth() {
    return client.admin().cluster().prepareHealth()
        .setWaitForYellowStatus()
        .setTimeout(TimeValue.timeValueMillis(100))
        .get();
  }

  //@TODO "using Node directly within an application is not officially supported"
  //https://www.elastic.co/guide/en/elasticsearch/reference/5.5/breaking_50_java_api_changes.html
  //@TODO Embedded ES is no longer supported
  public void startEmbeddedNode() throws NodeValidationException {
    if (this.embeddedNode != null) {
      log.info("Embedded Elasticsearch cluster already running");
      return;
    }
    log.info("Launching an embedded Elasticsearch cluster: {}", properties.getClusterName());

    Collection<Class<? extends Plugin>> plugins = Arrays.asList(Netty4Plugin.class);
    embeddedNode = new PluginConfigurableNode(Settings.builder()
     .put("path.home", properties.getEmbeddedStoragePath())
     .put("cluster.name", properties.getClusterName())
     .put("node.name", properties.getNodeName())
     .put("transport.type", "netty4")
     .put("node.data", true)
     .put("node.master", true)
     .put("network.host", "0.0.0.0")
     .put("http.type", "netty4")
     .put("http.enabled", true)
     .put("http.cors.enabled", true)
     .put("http.cors.allow-origin", "/.*/")
     .build(), plugins);

      embeddedNode.start();
  }

  //solution from here: https://github.com/elastic/elasticsearch-hadoop/blob/fefcf8b191d287aca93a04144c67b803c6c81db5/mr/src/itest/java/org/elasticsearch/hadoop/EsEmbeddedServer.java
  private static class PluginConfigurableNode extends Node {
    public PluginConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins);
    }
  }

  public void close() throws IOException {
    if (client != null) {
      client.close();
      this.lowLevelRestClient.close();
      log.info("Closed client {}", client.settings().get("node.name"));
      client = null;
      this.isClusterYellow = false;
    }
  }

  public void closeEmbeddedNode() throws IOException {
    if(embeddedNode != null) {
      embeddedNode.close();
      this.close();
    }
  }
}
