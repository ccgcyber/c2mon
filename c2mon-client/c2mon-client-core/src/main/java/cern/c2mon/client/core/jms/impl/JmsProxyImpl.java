/******************************************************************************
 * Copyright (C) 2010-2019 CERN. All rights not expressly granted are reserved.
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
package cern.c2mon.client.core.jms.impl;

import java.io.Serializable;
import java.lang.IllegalStateException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.PreDestroy;
import javax.jms.*;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import com.google.gson.JsonSyntaxException;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import cern.c2mon.client.common.listener.ClientRequestReportListener;
import cern.c2mon.client.core.config.C2monClientProperties;
import cern.c2mon.client.core.jms.*;
import cern.c2mon.client.core.listener.TagUpdateListener;
import cern.c2mon.shared.client.request.ClientRequestReport;
import cern.c2mon.shared.client.request.ClientRequestResult;
import cern.c2mon.shared.client.request.JsonRequest;

/**
 * Implementation of the JmsProxy singleton bean. Also see the interface for
 * documentation.
 * <p>
 * A new session (thread) is created for every Topic subscription. For this
 * reason, the number of different topics across all Tags should be kept to a
 * reasonable number (say around 50) and adjusted upwards if the number of
 * incoming updates is causing performance problems. These sessions are created
 * when needed and closed when possible (i.e. when no more update listeners
 * registered on the topic).
 * <p>
 * Separate sessions are used for listening to supervision events and sending
 * requests to the server (the latter created at request time).
 * <p>
 * Notice this component requires an explicit start of the Spring context (or
 * direct call to the start method).
 * <p>
 * Connect and disconnect methods are synchronized to prevent them running in
 * parallel.
 *
 * @author Mark Brightwell
 */
@Slf4j
@Component("jmsProxy")
@ManagedResource(objectName = "cern.c2mon:type=JMS,name=JmsProxy")
public final class JmsProxyImpl implements JmsProxy {

  /**
   * Time between reconnection attempts if the first attempt fails (in
   * milliseconds).
   */
  private static final long SLEEP_BETWEEN_CONNECTION_ATTEMPTS = 5000;

  /**
   * Timeout used for all messages sent to the server: notice this needs to be
   * quite large to account for unsynchronized clients.
   */
  private static final int JMS_MESSAGE_TIMEOUT = 600000;

  /**
   * Buffer before warnings for slow consumers are sent. Value for topics with
   * few updates (heartbeat, admin message)
   */
  private static final int DEFAULT_LISTENER_QUEUE_SIZE = 100;

  /**
   * Buffer before warnings for slow consumers are sent, for tags, alarms (where
   * larger buffer is desirable).
   */
  private static final int HIGH_LISTENER_QUEUE_SIZE = 10000;

  /**
   * The JMS connection factory.
   */
  private ConnectionFactory jmsConnectionFactory;

  /**
   * The unique JMS connection used.
   */
  @Getter
  private ActiveMQConnection connection;

  /**
   * Indicates which {@link MessageListenerWrapper} is listening to a given
   * topic (each wrapper listens to a single topic).
   * <p>
   * Each wrapper has its own internal thread that needs stop/starting when the
   * wrapper is removed or created.
   */
  private Map<String, MessageListenerWrapper> topicToWrapper;

  /**
   * Points to the JMS Session started for a given wrapper. Only the sessions
   * used for Tag update subscriptions are referenced here.
   */
  private Map<MessageListenerWrapper, Session> sessions;

  /**
   * 1-1 correspondence between ServerUpdateListeners and the Tags they are
   * receiving updates for (usually both are the same object).
   */
  private Map<TagUpdateListener, TopicRegistrationDetails> registeredListeners;

  /**
   * Listener exclusive lock, to prevent concurrent subscription/unsubscription of listeners.
   */
  private ReentrantReadWriteLock.WriteLock listenerLock;

  /**
   * Listeners that need informing about JMS connection and disconnection
   * events.
   */
  private Collection<ConnectionListener> connectionListeners;
  private ReentrantReadWriteLock connectionListenersLock;

  /**
   * Subscribes to the Supervision topic and notifies any registered listeners.
   * Thread start once and lives until final stop.
   */
  private SupervisionListenerWrapper supervisionListenerWrapper;

  /**
   * Subscribes to the admin messages topic and notifies any registered
   * listeners. Thread start once and lives until final stop.
   */
  private BroadcastMessageListenerWrapper broadcastMessageListenerWrapper;

  /**
   * Subscribes to the Supervision topic and notifies any registered listeners.
   * Thread start once and lives until final stop.
   */
  private HeartbeatListenerWrapper heartbeatListenerWrapper;

  /**
   * Subscribes to the alarm topic and notifies any registered listeners. Thread
   * start once and lives until final stop.
   */
  private AlarmListenerWrapper alarmListenerWrapper;

  /**
   * Notified on slow consumer detection.
   */
  private SlowConsumerListener slowConsumerListener;

  /**
   * Alarm Session.
   */
  private Session alarmSession;

  /**
   * Alarm Consumer.
   */
  private MessageConsumer alarmConsumer;

  /**
   * Recording connection status to JMS. Only set in connect and
   * startReconnectThread methods.
   */
  private volatile boolean connected;

  /**
   * Track start/stop status.
   */
  private volatile boolean running;

  /**
   * A final shutdown of this JmsProxy has been requested.
   */
  private volatile boolean shutdownRequested;

  /**
   * A lock used to prevent multiple reconnection threads being started.
   */
  private ReentrantReadWriteLock.WriteLock connectingWriteLock;

  /**
   * No refresh of the subscriptions is allowed at the same time as
   * registrations or unregistrations, since a refresh involves cleaning all the
   * internal maps and re-subscribing consumers on the various sessions.
   */
  private ReentrantReadWriteLock refreshLock;

  /**
   * Topic on which supervision events arrive from server.
   */
  private Destination supervisionTopic;

  /**
   * Topic on which admin messages are being sent, and on which it arrives
   */
  private Destination adminMessageTopic;

  /**
   * Topic on which server heartbeat messages are arriving.
   */
  private Destination heartbeatTopic;

  /**
   * Topic on which server alarm messages are arriving.
   */
  private Destination alarmTopic;

  /**
   * Threads used for polling topic queues.
   */
  private ExecutorService topicPollingExecutor;

  @Autowired
  public JmsProxyImpl(@Qualifier("clientJmsConnectionFactory") final ConnectionFactory connectionFactory,
                      final SlowConsumerListener slowConsumerListener,
                      final C2monClientProperties properties) {
    this.jmsConnectionFactory = connectionFactory;
    this.supervisionTopic = new ActiveMQTopic(properties.getJms().getSupervisionTopic());;
    this.heartbeatTopic = new ActiveMQTopic(properties.getJms().getHeartbeatTopic());;
    this.alarmTopic = new ActiveMQTopic(properties.getJms().getAlarmTopic());;
    this.adminMessageTopic = null;
    this.slowConsumerListener = slowConsumerListener;

    connected = false;
    running = false;
    shutdownRequested = false;
    connectingWriteLock = new ReentrantReadWriteLock().writeLock();
    refreshLock = new ReentrantReadWriteLock();

    topicPollingExecutor = Executors.newCachedThreadPool(new ThreadFactory() {

      ThreadFactory defaultFactory = Executors.defaultThreadFactory();

      @Override
      public Thread newThread(final Runnable r) {
        Thread returnThread = defaultFactory.newThread(r);
        returnThread.setDaemon(true);
        return returnThread;
      }
    });

    sessions = new ConcurrentHashMap<>();
    topicToWrapper = new ConcurrentHashMap<>();
    registeredListeners = new ConcurrentHashMap<>();
    listenerLock = new ReentrantReadWriteLock().writeLock();
    connectionListeners = new ArrayList<>();
    connectionListenersLock = new ReentrantReadWriteLock();
    supervisionListenerWrapper = new SupervisionListenerWrapper(HIGH_LISTENER_QUEUE_SIZE, slowConsumerListener, topicPollingExecutor);
    supervisionListenerWrapper.start();
    broadcastMessageListenerWrapper = new BroadcastMessageListenerWrapper(DEFAULT_LISTENER_QUEUE_SIZE, slowConsumerListener, topicPollingExecutor);
    broadcastMessageListenerWrapper.start();
    heartbeatListenerWrapper = new HeartbeatListenerWrapper(DEFAULT_LISTENER_QUEUE_SIZE, slowConsumerListener, topicPollingExecutor);
    heartbeatListenerWrapper.start();
    alarmListenerWrapper = new AlarmListenerWrapper(HIGH_LISTENER_QUEUE_SIZE, slowConsumerListener, topicPollingExecutor);
    alarmListenerWrapper.start();
  }

  /**
   * Sets the prefix id on the JMS connection factory. Only works if JMS
   * connection factory is ActiveMQ.
   */
  private void setActiveMQConnectionPrefix() {
    String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

    String hostname = null;
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      log.info("Couldn't get hostname", e);
    }

    String clientIdPrefix = "C2MON-CLIENT-" + System.getProperty("user.name") + "@" + hostname + "[" + pid + "]";

    if (jmsConnectionFactory instanceof ActiveMQConnectionFactory) {
      ((ActiveMQConnectionFactory) jmsConnectionFactory).setClientIDPrefix(clientIdPrefix);
    }
  }

  /**
   * Runs until (re)connected. Should only be called by one thread. Use
   * startReconnectThread to run in another thread (only one thread will be
   * started however many calls are made).
   * <p>
   * Listeners are notified of connection once the connection is reestablished
   * and all topic subscriptions are back.
   */
  private synchronized void connect() {
    while (!connected && !shutdownRequested) {
      try {
        connection = (ActiveMQConnection) jmsConnectionFactory.createConnection();
        connection.start();
        connection.addTransportListener((ActiveMQTransportListener) this::startReconnectThread);
        refreshSubscriptions();
        connected = true;
      } catch (Exception e) {
        log.error("Exception caught while trying to refresh the JMS connection; sleeping 5s before retrying.", e);
        try {
          wait(SLEEP_BETWEEN_CONNECTION_ATTEMPTS);
        } catch (InterruptedException interEx) {
          log.error("InterruptedException caught while waiting to reconnect.", interEx);
        }
      }
    }
    if (connected) {
      notifyConnectionListenerOnConnection();
    }
  }

  private void ensureConnection() {
    if (!running) {
      init();
    }
  }

  /**
   * Notifies all {@link ConnectionListener}s of a connection.
   */
  private void notifyConnectionListenerOnConnection() {
    connectionListenersLock.readLock().lock();
    try {
      for (ConnectionListener listener : connectionListeners) {
        listener.onConnection();
      }
    } finally {
      connectionListenersLock.readLock().unlock();
    }
  }

  /**
   * Notifies all {@link ConnectionListener}s on a disconnection.
   */
  private void notifyConnectionListenerOnDisconnection() {
    connectionListenersLock.readLock().lock();
    try {
      for (ConnectionListener listener : connectionListeners) {
        listener.onDisconnection();
      }
    } finally {
      connectionListenersLock.readLock().unlock();
    }
  }

  /**
   * Disconnects and notifies listeners of disconnection.
   */
  private synchronized void disconnect() {
    disconnectQuietly();
    notifyConnectionListenerOnDisconnection();
  }

  /**
   * Stops all topic listeners and disconnects; connection listeners are not
   * notified. Used at shutdown.
   */
  private synchronized void disconnectQuietly() {
    connected = false;
    // these listeners are re-created
    for (Map.Entry<String, MessageListenerWrapper> entry : topicToWrapper.entrySet()) {
      entry.getValue().stop();
    }
    if (connection != null && !connection.isClosed() && connection.isTransportFailed()) {
      try {
        connection.close(); // closes all consumers and sessions also
      } catch (JMSException e) {
        String message = "Exception caught while attempting to disconnect from JMS: " + e.getMessage();
        log.error(message);
        log.debug(message, e);
      }
    }
  }

  /**
   * Only starts the thread if it is not already started (thread will always be
   * started if connected has been set to false).
   */
  private void startReconnectThread() {
    // lock to prevent multiple threads from starting (before connected flag is
    // modified)
    connectingWriteLock.lock();
    try {
      if (connected) {
        disconnect(); // notifies listeners
        new Thread(new Runnable() {
          @Override
          public void run() {
            connect();
          }
        }).start();
      }
    } finally {
      connectingWriteLock.unlock();
    }

  }

  /**
   * Refreshes all the Topic subscriptions. Assumes previous connection/sessions
   * are now inactive.
   *
   * @throws JMSException
   *           if problem subscribing
   */
  private void refreshSubscriptions() throws JMSException {
    refreshLock.writeLock().lock();
    try {
      if (!registeredListeners.isEmpty()) {
        sessions.clear();
        topicToWrapper.clear();
        // refresh all registered listeners for Tag updates
        for (Map.Entry<TagUpdateListener, TopicRegistrationDetails> entry : registeredListeners.entrySet()) {
          registerUpdateListener(entry.getKey(), entry.getValue());
        }
      }

      if (alarmListenerWrapper.getListenerCount() > 0) {
        subscribeToAlarmTopic();
      }

      // refresh supervision subscription
      subscribeToSupervisionTopic();
      subscribeToHeartbeatTopic();
      subscribeToAdminMessageTopic();
    } catch (JMSException e) {
      log.error("Did not manage to refresh Topic subscriptions", e);
      throw e;
    } finally {
      refreshLock.writeLock().unlock();
    }
  }

  /**
   * Subscribes to the alarm topic.
   * @throws JMSException if problem subscribing
   */
  private void subscribeToAlarmTopic() throws JMSException {
    alarmSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    alarmConsumer = alarmSession.createConsumer(alarmTopic);
    alarmConsumer.setMessageListener(alarmListenerWrapper);
    log.debug("Successfully subscribed to alarm topic");
  }

  /**
   * Unsubscribes from the alarm topic.
   * @throws JMSException if problem subscribing
   */
  private void unsubscribeFromAlarmTopic() throws JMSException {

    alarmSession.close();
    alarmSession = null;
    alarmConsumer.close();
    alarmConsumer = null;
    log.debug("Successfully unsubscribed from alarm topic");
  }

  /**
   * Subscribes to the heartbeat topic. Called when refreshing all
   * subscriptions.
   * @throws JMSException if problem subscribing
   */
  private void subscribeToHeartbeatTopic() throws JMSException {
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    MessageConsumer consumer = session.createConsumer(heartbeatTopic);
    consumer.setMessageListener(heartbeatListenerWrapper);
  }

  /**
   * Called when refreshing subscriptions at start up and again if the
   * connection goes down.
   *
   * @throws JMSException
   *           if unable to subscribe
   */
  private void subscribeToAdminMessageTopic() throws JMSException {
    if (adminMessageTopic != null) {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final MessageConsumer consumer = session.createConsumer(adminMessageTopic);
      consumer.setMessageListener(broadcastMessageListenerWrapper);
    }
  }

  /**
   * Called when refreshing subscriptions at start up and again if the
   * connection goes down.
   *
   * @throws JMSException
   *           if unable to subscribe
   */
  private void subscribeToSupervisionTopic() throws JMSException {
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    MessageConsumer consumer = session.createConsumer(supervisionTopic);
    consumer.setMessageListener(supervisionListenerWrapper);
  }

  @Override
  public boolean isRegisteredListener(final TagUpdateListener serverUpdateListener) {
    if (serverUpdateListener == null) {
      throw new NullPointerException("isRegisteredListener() method called with null parameter!");
    }
    return registeredListeners.containsKey(serverUpdateListener);
  }

  @Override
  public void registerUpdateListener(final TagUpdateListener serverUpdateListener, final TopicRegistrationDetails topicRegistrationDetails) throws JMSException {
    if (topicRegistrationDetails == null) {
      throw new NullPointerException("Trying to register a TagUpdateListener with null RegistrationDetails!");
    }

    if (serverUpdateListener == null) {
      throw new NullPointerException("TagUpdateListener must not be null!");
    }

    ensureConnection();

    refreshLock.readLock().lock();
    try {
      listenerLock.lock();
      try {
        boolean refreshSubscriptions = refreshLock.isWriteLocked();

        if (refreshSubscriptions || !isRegisteredListener(serverUpdateListener)) { // throw exception if
          // TagUpdateListener
          // null
          try {
            if (refreshSubscriptions || connected) {
              String topicName = topicRegistrationDetails.getTopicName();
              if (topicToWrapper.containsKey(topicName)) {
                topicToWrapper.get(topicName).addListener(serverUpdateListener, topicRegistrationDetails.getId());
              } else {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(topicRegistrationDetails.getTopicName());
                MessageConsumer consumer = session.createConsumer(topic);
                MessageListenerWrapper wrapper = new MessageListenerWrapper(topicRegistrationDetails.getId(), serverUpdateListener, HIGH_LISTENER_QUEUE_SIZE,
                    slowConsumerListener, topicPollingExecutor);
                wrapper.start();
                consumer.setMessageListener(wrapper);
                topicToWrapper.put(topicName, wrapper);
                sessions.put(wrapper, session);
              }

              if (!refreshSubscriptions) {
                registeredListeners.put(serverUpdateListener, topicRegistrationDetails);
              }
            } else {
              throw new JMSException("Not currently connected - will attempt to subscribe on reconnection. Attempting to reconnect.");
            }
          } catch (JMSException e) {
            log.error("Failed to subscribe to topic - will do so on reconnection.", e);
            if (!refreshSubscriptions) {
              registeredListeners.put(serverUpdateListener, topicRegistrationDetails);
            }
            throw e;
          }
        } else {
          log.debug("Update listener already registered; skipping registration (for Tag " + topicRegistrationDetails.getId() + ")");
        }
      } finally {
        listenerLock.unlock();
      }
    } finally {
      refreshLock.readLock().unlock();
    }
  }

  @Override
  public void replaceListener(final TagUpdateListener registeredListener, final TagUpdateListener replacementListener) {
    if (registeredListener == null && replacementListener == null) {
      throw new NullPointerException("replaceListener(..) method called with null argument");
    }
    refreshLock.readLock().lock();
    try {
      listenerLock.lock();
      try {
        TopicRegistrationDetails tag = registeredListeners.get(registeredListener);
        topicToWrapper.get(tag.getTopicName()).addListener(replacementListener, tag.getId());
        registeredListeners.put(replacementListener, registeredListeners.remove(registeredListener));
      } finally {
        listenerLock.unlock();
      }
    } finally {
      refreshLock.readLock().unlock();
    }
  }

  @Override
  public void publish(final String message, final String topicName, final long timeToLive) throws JMSException {
    if (topicName == null) {
      throw new NullPointerException("publish(..) method called with null queue name argument");
    }
    if (message == null) {
      throw new NullPointerException("publish(..) method called with null message argument");
    }
    if (connected) {
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
        final Message messageObj = session.createTextMessage(message);

        final MessageProducer producer = session.createProducer(new ActiveMQTopic(topicName));
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        producer.setTimeToLive(JMS_MESSAGE_TIMEOUT);
        producer.send(messageObj);
      } finally {
        session.close();
      }
    } else {
      throw new JMSException("Not currently connected: unable to send message at this time.");
    }
  }

  @Override
  public <T extends ClientRequestResult> Collection<T> sendRequest(
      final JsonRequest<T> jsonRequest, final String queueName, final int timeout,
      final ClientRequestReportListener reportListener) throws JMSException {

    if (queueName == null) {
      throw new NullPointerException("sendRequest(..) method called with null queue name argument");
    }
    if (jsonRequest == null) {
      throw new NullPointerException("sendRequest(..) method called with null request argument");
    }

    ensureConnection();

    if (connected) {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {

        Message message = null;

        if (jsonRequest.isObjectRequest()) { // used for EXECUTE_COMMAND_REQUESTS

          // send only the object
          //CommandExecuteRequest o = (CommandExecuteRequest) jsonRequest.getObjectParameter();
          message = session.createObjectMessage((Serializable) jsonRequest.getObjectParameter());

        } else { // used for all other request types

          // send the Client Request as a Json Text Message
          message = session.createTextMessage(jsonRequest.toJson());
        }

        TemporaryQueue replyQueue = session.createTemporaryQueue();
        MessageConsumer consumer = session.createConsumer(replyQueue);
        try {
          message.setJMSReplyTo(replyQueue);
          MessageProducer producer = session.createProducer(new ActiveMQQueue(queueName));
          producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
          producer.setTimeToLive(JMS_MESSAGE_TIMEOUT);
          producer.send(message);

          while (connected && !shutdownRequested) { // until we receive the result
            // (it is possible to receive progress and / or error reports during this process)

            Message replyMessage = consumer.receive(timeout);
            if (replyMessage == null) {
              log.error("No reply received from server on ClientRequest. I was waiting for " + timeout + " milliseconds..");
              throw new RuntimeException("No reply received from server - possible timeout?");
            }

            if (replyMessage instanceof ObjectMessage) {
              return (Collection<T>) ((ObjectMessage) replyMessage).getObject();
            }
            else {
              // replyMessage is an instanceof TextMessage (json)
              TextMessage textMessage = (TextMessage) (replyMessage);

              Collection<T> resultCollection = handleJsonResponse(textMessage, jsonRequest, reportListener);
              if (resultCollection != null)
                return resultCollection;
            }
          }
          throw new RuntimeException("Disconnected from JMS, so unable to process request.");
        } finally {
          if (consumer != null) {
            consumer.close();
          }
          replyQueue.delete();
        }
      } finally {
        session.close();
      }
    } else {
      throw new JMSException("Not currently connected: unable to send request at this time.");
    }
  }

  /**
   * ActiveMQ-specific implementation since need to create topic.
   * @return a Collection of ClientRequestResults
   */
  @Override
  public <T extends ClientRequestResult> Collection<T> sendRequest(
      final JsonRequest<T> jsonRequest, final String queueName, final int timeout)
      throws JMSException {

    ClientRequestReportListener reportListener = null; // we don't care about reports!
    return sendRequest(jsonRequest, queueName, timeout, reportListener);
  }

  /**
   * In case a JsonResponse has been received.
   * This can either be the final Result or a Report on the progress of the request.
   * @param jsonMessage the received message.
   * @param jsonRequest the original request. useful to decode the message
   * @param reportListener informed in case a Report is received. Can be null
   * in case no one cares about the progress of this request.
   * @param <T> type returned depends on the ClientRequest type.
   * @return a Collection of ClientRequestResults.
   * @throws JMSException if problem subscribing
   */
  private <T extends ClientRequestResult> Collection<T> handleJsonResponse(
      final TextMessage jsonMessage, final JsonRequest<T> jsonRequest, final ClientRequestReportListener reportListener)
      throws JsonSyntaxException, JMSException {

    Collection<T> resultCollection = jsonRequest.fromJsonResponse(jsonMessage.getText());
    if (resultCollection.isEmpty()) // if the result is empty ->
      return resultCollection; // we cannot do much with it

    ClientRequestResult result = resultCollection.iterator().next(); // lets take the first element and check if it is a report
    if ((result instanceof ClientRequestReport)) { // this can either be a Report or the actual Result
      ClientRequestReport report = (ClientRequestReport) result;
      if (isResult(report)) // received the result!
        return resultCollection; // bye - bye!
      else { // received a report ->
        handleJsonReportResponse(report, reportListener); // let's handle the report! still waiting for the result though
        return null;
      }
    }
    return resultCollection;
  }

  /**
   * Informs the listener in case a report is received.
   * @param report the received report.
   * @param reportListener the listener to be informed. Can be null in case no one cares about this report.
   */
  private void handleJsonReportResponse(final ClientRequestReport report, final ClientRequestReportListener reportListener) {

    if (reportListener == null) { // is someone waiting for the report?
      log.debug("handleJsonReportResponse(): Received a report, but no reportListener is registered. Ignoring..");
      return;
    }

    if (report.isErrorReport()) {
      log.debug("handleJsonReportResponse(): Received an error report. Informing listener.");
      reportListener.onErrorReportReceived(report);
      throw new RuntimeException("Error report received from server on client request: " + report.getErrorMessage());
    }
    else if (report.isProgressReport()) {
      log.debug("handleJsonReportResponse(): Received a progress report. Informing listener.");
      reportListener.onProgressReportReceived(report);
    }
    else
      log.warn("handleJsonReportResponse(): Received a report of unknown type. Ignoring..");
  }

  /**
   * The server's response can either be a report or the actual result.
   * @param clientRequestReport the response to be checked
   * @return True if the final result is received, false in case a report has been received.
   */
  private boolean isResult(final ClientRequestReport clientRequestReport) {

    return clientRequestReport.isResult();
  }

  @Override
  public void unregisterUpdateListener(final TagUpdateListener serverUpdateListener) {
    refreshLock.readLock().lock();
    try {
      listenerLock.lock();
      try {
        if (isRegisteredListener(serverUpdateListener)) {
          TopicRegistrationDetails subsribedToTag = registeredListeners.get(serverUpdateListener);
          MessageListenerWrapper wrapper = topicToWrapper.get(subsribedToTag.getTopicName());
          wrapper.removeListener(subsribedToTag.getId());
          if (wrapper.isEmpty()) { // no subscribed listeners, so close session
            log.trace("No listeners registered to topic " + subsribedToTag.getTopicName() + " so closing down MessageListenerWrapper");
            try {
              Session session = sessions.get(wrapper);
              session.close();
            } catch (JMSException ex) {
              log.error("Failed to unregister properly from a Tag update; subscriptions will be refreshed.");
              startReconnectThread();
            } finally {
              wrapper.stop();
              sessions.remove(wrapper);
              topicToWrapper.remove(subsribedToTag.getTopicName());
            }
          }
          registeredListeners.remove(serverUpdateListener);
        }
      } finally {
        listenerLock.unlock();
      }
    } finally {
      refreshLock.readLock().unlock();
    }
  }

  @Override
  public void registerConnectionListener(final ConnectionListener connectionListener) {
    if (connectionListener == null) {
      throw new NullPointerException("registerConnectionListener(..) method called with null listener argument");
    }

    ensureConnection();

    connectionListenersLock.writeLock().lock();
    try {
      connectionListeners.add(connectionListener);
      if (connected) {
        connectionListener.onConnection();
      }
      else {
        connectionListener.onDisconnection();
      }
    } finally {
      connectionListenersLock.writeLock().unlock();
    }
  }

  @Override
  public void registerSupervisionListener(final SupervisionListener supervisionListener) {
    if (supervisionListener == null) {
      throw new NullPointerException("Trying to register null Supervision listener with JmsProxy.");
    }

    ensureConnection();
    supervisionListenerWrapper.addListener(supervisionListener);
  }

  @Override
  public void unregisterSupervisionListener(final SupervisionListener supervisionListener) {
    if (supervisionListener == null) {
      throw new NullPointerException("Trying to unregister null Supervision listener from JmsProxy.");
    }
    supervisionListenerWrapper.removeListener(supervisionListener);
  }

  @Override
  public void registerBroadcastMessageListener(final BroadcastMessageListener broadcastMessageListener) {
    if (broadcastMessageListener == null) {
      throw new NullPointerException("Trying to register null BroadcastMessage listener with JmsProxy.");
    }
    if (adminMessageTopic == null) {
      throw new IllegalStateException(String.format("Cannot register '%s' without having the admin message topic", BroadcastMessageListener.class.getSimpleName()));
    }
    broadcastMessageListenerWrapper.addListener(broadcastMessageListener);
  }

  @Override
  public void unregisterBroadcastMessageListener(final BroadcastMessageListener broadcastMessageListener) {
    if (broadcastMessageListener == null) {
      throw new NullPointerException("Trying to unregister null BroadcastMessage listener from JmsProxy.");
    }
    broadcastMessageListenerWrapper.removeListener(broadcastMessageListener);
  }

  @Override
  public void registerAlarmListener(final AlarmListener alarmListener) throws JMSException {
    if (alarmListener == null) {
      throw new NullPointerException("Trying to register null alarm listener with JmsProxy.");
    }

    ensureConnection();

    if (alarmListenerWrapper.getListenerCount() == 0) { // this is our first
      // listener!
      // -> it's time to subscribe to the alarm topic
      try {
        subscribeToAlarmTopic();
      } catch (JMSException e) {
        log.error("Did not manage to subscribe To Alarm Topic.", e);
        throw e;
      }
    }
    alarmListenerWrapper.addListener(alarmListener);
  }

  @Override
  public void unregisterAlarmListener(final AlarmListener alarmListener) throws JMSException {
    if (alarmListener == null) {
      throw new NullPointerException("Trying to unregister null alarm listener from JmsProxy.");
    }

    if (alarmListenerWrapper.getListenerCount() == 1) { // this is our last
      // listener!
      // -> it's time to unsubscribe from the topic
      try {
        unsubscribeFromAlarmTopic();
      } catch (JMSException e) {
        log.error("Did not manage to subscribe To Alarm Topic.", e);
        throw e;
      }
    }

    alarmListenerWrapper.removeListener(alarmListener);
  }

  @Override
  public void registerHeartbeatListener(final HeartbeatListener heartbeatListener) {
    if (heartbeatListener == null) {
      throw new NullPointerException("Trying to register null Heartbeat listener with JmsProxy.");
    }

    ensureConnection();
    heartbeatListenerWrapper.addListener(heartbeatListener);
  }

  @Override
  public void unregisterHeartbeatListener(final HeartbeatListener heartbeatListener) {
    if (heartbeatListener == null) {
      throw new NullPointerException("Trying to unregister null Heartbeat listener from JmsProxy.");
    }
    heartbeatListenerWrapper.removeListener(heartbeatListener);
  }

  @Override
  public void setBroadcastMessageTopic(final Destination adminMessageTopic) {
    if (this.adminMessageTopic != null) {
      throw new IllegalStateException("Cannot set the admin message topic more than one time");
    }
    this.adminMessageTopic = adminMessageTopic;
    if (connected) {
      try {
        subscribeToAdminMessageTopic();
      } catch (JMSException e) {
        log.error("Unable to subscribe to the admin message topic, this functionality may not function properly.", e);
      }
    }
  }

  public void init() {
    running = true;
    shutdownRequested = false;
    setActiveMQConnectionPrefix();
    connect();
  }

  /**
   * Shuts down the JmsProxy. Connection listeners are not notified.
   */
  @PreDestroy
  public void stop() {
    log.debug("Stopping JmsProxy and dependent listeners");
    shutdownRequested = true;
    supervisionListenerWrapper.stop();
    alarmListenerWrapper.stop();
    broadcastMessageListenerWrapper.stop();
    heartbeatListenerWrapper.stop();
    topicPollingExecutor.shutdown();
    disconnectQuietly();
    connectionListenersLock.writeLock().lock();
    try {
      connectionListeners.clear();
    } finally {
      connectionListenersLock.writeLock().unlock();
    }
    running = false;
  }

  @ManagedOperation(description = "Get size of current internal listener queues")
  public Map<String, Integer> getQueueSizes() {
    Map<String, Integer> returnMap = new HashMap<>();
    for (Map.Entry<String, MessageListenerWrapper> entry : topicToWrapper.entrySet()) {
      returnMap.put(entry.getKey(), entry.getValue().getQueueSize());
    }

    returnMap.put(supervisionTopic.toString(), supervisionListenerWrapper.getQueueSize());
    returnMap.put(alarmTopic.toString(), alarmListenerWrapper.getQueueSize());
    if (adminMessageTopic != null) {
      returnMap.put(adminMessageTopic.toString(), broadcastMessageListenerWrapper.getQueueSize());
    }
    returnMap.put(heartbeatTopic.toString(), heartbeatListenerWrapper.getQueueSize());
    return returnMap;
  }
}
