/*******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
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
 ******************************************************************************/
package cern.c2mon.client.core.service.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import cern.c2mon.client.common.service.SessionService;
import cern.c2mon.client.common.tag.CommandTag;
import cern.c2mon.client.core.jms.RequestHandler;
import cern.c2mon.client.core.service.CommandService;
import cern.c2mon.client.core.tag.CommandTagImpl;
import cern.c2mon.shared.client.command.*;

@Service
public class CommandServiceImpl implements CommandService {

  /** Log4j Logger for this class */
  private static final Logger LOG = LoggerFactory.getLogger(CommandServiceImpl.class);

  /** Default for an uninitialized unknown tag id  */
  private static final Long UNKNOWN_TAG_ID = -1L;

  /**
   * The local cache for commands that have already been retrieved from the
   * server.
   */
  private final Map<Long, CommandTagImpl<Object>> commandCache =
    new ConcurrentHashMap<>();

  /**
   * The C2MON session manager
   */
  private SessionService sessionService;

  /**
   * Provides methods for requesting commands information and sending
   * an execute request to the C2MON server.
   */
  private final RequestHandler clientRequestHandler;


  /**
   * Default Constructor, used by Spring to instantiate the Singleton service
   *
   * @param pSessionManager The session Manager which is needed for checking the
   *                        user authorization.
   * @param pRequestHandler
   *          Provides methods for requesting tag information from the C2MON
   *          server
   */
  @Autowired
  protected CommandServiceImpl(final @Qualifier("coreRequestHandler") RequestHandler pRequestHandler) {
    this.clientRequestHandler = pRequestHandler;
  }

  @Override
  public CommandReport executeCommand(final String userName, final Long commandId, final Object value) throws CommandTagValueException {
    if(sessionService != null){
      LOG.info("Executing command with SessionService authentication");

      if (!sessionService.isUserLogged(userName)) {
        return new CommandReportImpl(commandId,
            CommandExecutionStatus.STATUS_AUTHORISATION_FAILED, "No user is logged-in.");
      }
    } else {
      LOG.info("Executing command without SessionService authentication");
    }

    if (!commandCache.containsKey(commandId)) {
      getCommandTag(commandId);
    }

    CommandTagImpl<Object> cct = commandCache.get(commandId);

    if (!cct.isExistingCommand()) {
        return new CommandReportImpl(commandId, CommandExecutionStatus.STATUS_CMD_UNKNOWN, "The command with tagId '" + commandId + "' is not known to the server");
    }

    CommandExecuteRequest<Object> executeRequest = createCommandExecuteRequest(cct, value);

    if (sessionService != null && !isAuthorized(userName, commandId)) {
        return new CommandReportImpl(commandId,
            CommandExecutionStatus.STATUS_AUTHORISATION_FAILED,
            "The logged user has not the priviledges to execute command " + commandId + ".");
    }

    try {
      LOG.info("executeCommand() - Executing command " + commandId + " for authorized user " + userName);
      return clientRequestHandler.executeCommand(executeRequest);
    }
    catch (Exception e) {
      LOG.error("executeCommand() - Catched JMS execption while trying to execute command "
                + commandId + ". ", e);
      return new CommandReportImpl(commandId,
          CommandExecutionStatus.STATUS_SERVER_ERROR,
          "Could not execute the command due to a communication error with the server. Error: " + e.getMessage());
    }

  }


  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public <T> Set<CommandTag<T>> getCommandTags(final Set<Long> pIds) {
    Set<CommandTag<T>> resultSet = new HashSet<>();
    Set<Long> newCommandTagIds = new HashSet<>();

    if (LOG.isDebugEnabled()) {
      StringBuilder str = new StringBuilder("getCommandTags() - creating ");
      str.append(pIds.size());
      str.append(" command tags.");
      LOG.debug(str.toString());
    }

    // Create ClientDataTags for all IDs and keep in hash table
    CommandTagImpl commandTag;
    for (Long commandId : pIds) {
      // skip all fake tags
      if (!commandId.equals(UNKNOWN_TAG_ID)) {
        commandTag = this.commandCache.get(commandId);
        if (commandTag == null) {
          commandTag = new CommandTagImpl(commandId);
          // Add the new tag to the global store
          this.commandCache.put(commandId, commandTag);

          // Add this id to the list to request
          newCommandTagIds.add(commandId);
        }
      }
    }

    if (newCommandTagIds.size() > 0) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(new StringBuilder("getCommandTags() - ").append(newCommandTagIds.size()).append(" commands to be requested.").toString());
      }

      Collection<CommandTagHandle> commandTagHandles = clientRequestHandler.requestCommandTagHandles(newCommandTagIds);
      if (commandTagHandles != null) {
        CommandTagImpl cct = null;
        for (CommandTagHandle tagHandle : commandTagHandles) {
          cct = this.commandCache.get(tagHandle.getId());
          // update ClientCommandTag object
          if (cct != null) { // found the ClientCommandTag object
            cct.update(tagHandle);
          }
          else {
            LOG.error("getCommandTags() - Received unknown command tag: " + tagHandle.getId());
          }
        }
      }
    }
    else {
      LOG.debug("getCommandTags() - No commands to be requested from the server.");
    }

    // Clone command tags for result set
    for (Long commandId : pIds) {
      // skip all fake tags
      if (!commandId.equals(UNKNOWN_TAG_ID)) {
        try {
          resultSet.add((CommandTag<T>) commandCache.get(commandId).clone());
        }
        catch (CloneNotSupportedException e) {
          LOG.error("getCommandTags() - Error while cloning command tag with id " + commandId);
          throw new RuntimeException("Cloning not supported by CommandTagImpl with id " + commandId, e);
        }
      }
    }

    return resultSet;
  }



  /**
   * Inner method for creating a command execution request.
   * @param <T> The value type of the command
   * @param commandTag The command tag for which the request shall be created
   * @param value The value that shall be used for the command execution
   * @return An instance of {@link CommandExecuteRequest}
   * @throws CommandTagValueException Thrown in case an incompatible value type.
   */
  private <T> CommandExecuteRequest<T> createCommandExecuteRequest(final CommandTagImpl<T> commandTag, T value) throws CommandTagValueException {
    // Check if value is NOT NULL
    if (value == null) {
      throw new CommandTagValueException("Null value : command values cannot be set to null");
    }

    if (!commandTag.isExistingCommand()) {
        throw new CommandTagValueException("Unknown command : " + commandTag.getId() + " is not known to the server.");
    }

    if (commandTag.getValueType() == null) {
        throw new CommandTagValueException("Null value : command value type cannot be set to null");
    }

    if (value.getClass() != commandTag.getValueType()) {
      throw new CommandTagValueException("Data type : " + commandTag.getValueType() + " expected but got type " + value.getClass().getName() + ".");
    }

    try {
      if ((commandTag.getMinValue() != null) && commandTag.getMinValue().compareTo(value) > 0) {
        throw new CommandTagValueException("Out of range : " + value + " is less than the authorized minimum value " + commandTag.getMinValue() + ".");
      }
    }
    catch (ClassCastException ce) {
      throw new CommandTagValueException("CONFIGURATION ERROR: The minValue for the command is of type " + commandTag.getValueType().getName()
          + ". It cannot be compared to a value of type " + value.getClass().getName() + ". Contact the configuration responsible for correcting this problem");
    }

    try {
      if ((commandTag.getMaxValue() != null) && commandTag.getMaxValue().compareTo(value) < 0) {
        throw new CommandTagValueException("Out of range : " + value + " is greater than the authorized maximum value " + commandTag.getMaxValue() + ".");
      }
    }
    catch (ClassCastException ce) {
      throw new CommandTagValueException("CONFIGURATION ERROR: The minValue for the command is of type " + commandTag.getValueType().getName()
          + ". It cannot be compared to a value of type " + value.getClass().getName() + ". Contact the configuration responsible for correcting this problem");
    }

    String hostname;
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.warn("UnknownHostException caught while creating command request - set to unknown", e);
      hostname = "unknown-host";
    }

    return new CommandExecuteRequestImpl<>(commandTag.getId(), value,
                                  commandTag.getClientTimeout(), System.getProperty("user.home"), hostname);
  }

  @Override
  public <T> CommandTag<T> getCommandTag(final Long commandId) {
    Set<Long> commandTagIds = new HashSet<>();
    commandTagIds.add(commandId);
    Set<CommandTag<T>> commandTags = getCommandTags(commandTagIds);

    return commandTags.iterator().next();
  }



  @Override
  public boolean isAuthorized(final String userName, final Long commandId) {
    if (!commandCache.containsKey(commandId)) {
      getCommandTag(commandId);
    }

    if (sessionService == null) {
      return false;
    }

    if (sessionService.isUserLogged(userName)) {
      CommandTagImpl<Object> cct = commandCache.get(commandId);
      if (cct.isExistingCommand()) {
        if (cct.getAuthorizationDetails() != null) {
          return sessionService.isAuthorized(userName, cct.getAuthorizationDetails());
        }
        else {
          LOG.warn("isAuthorized() - No authorization details received for command "
              + commandId + ". Please contact the support team to solve this problem!");
        }
      }
    }

    return false;
  }

  @Override
  public void registerSessionService(SessionService sessionService) {
    if(sessionService == null){
      LOG.warn("No SessionService to to the CommandManager set. Service is null.");
    }

    if(this.sessionService != null){
      LOG.warn("SessionService were already set. Overriding of the service!");
    }

    this.sessionService = sessionService;
  }

  @Override
  public void refreshCommandCache() {
    Set<Long> commandIds = commandCache.keySet();
    commandCache.clear();
    getCommandTags(commandIds);
  }
}
