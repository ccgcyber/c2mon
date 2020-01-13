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
package cern.c2mon.client.core.service;

import java.util.Collection;

import cern.c2mon.client.core.jms.ClientHealthListener;
import cern.c2mon.client.core.jms.ConnectionListener;
import cern.c2mon.client.core.listener.HeartbeatListener;
import cern.c2mon.shared.client.supervision.SupervisionEvent;

/**
 * This interface describes the methods which are provided by
 * the C2MON supervision manager singleton. The supervision
 * manager allows registering listeners to get informed about
 * the connection state to the JMS brokers and the heartbeat
 * of the C2MON server.
 *
 * @author Matthias Braeger
 */
public interface SupervisionService {
  /**
   * Registers a heartbeat listener in order to receive event notifications from
   * the heartbeat manager.
   *
   * @param pListener the listerner instance to register at the
   *        <code>HeartbeatManager</code>
   */
  void addHeartbeatListener(final HeartbeatListener pListener);

  /**
   * Removes a heartbeat listener from the heartbeat manager.
   *
   * @param pListener the listerner instance to remove from the
   *        <code>HeartbeatManager</code>
   */
  void removeHeartbeatListener(final HeartbeatListener pListener);

  /**
   * Registers the given connection listener at the <code>JmsProxy</code>
   * instance. <code>ConnectionListener</code> instances are notified about the
   * event of a disconnection from the JMS broker as well as when the connection
   * is reestablished.
   *
   * @param pListener the listener instance to register
   */
  void addConnectionListener(final ConnectionListener pListener);


  /**
   * @return <code>true</code>, if the supervision manager is connected to the
   * server and was able to initialize correctly all <code>SupervisionEvent</code>
   * states.
   */
  boolean isServerConnectionWorking();

  /**
   * Register to be notified of detected problems with the processing
   * by the client application of incoming data from the server.
   *
   * <p>In general, these notifications indicate a serious problem with
   * possible data loss, so the client should take some appropriate
   * action on receiving these callbacks (e.g. notify the user).
   *
   * @param clientHealthListener the listener to notify
   */
  void addClientHealthListener(ClientHealthListener clientHealthListener);

  /**
   * @return The names of all known processes in C2MON
   */
  Collection<String> getAllProcessNames();

  /**
   * @return The names of all known equipments in C2MON
   */
  Collection<String> getAllEquipmentNames();

  /**
   * @return The names of all known sub-equipments in C2MON
   */
  Collection<String> getAllSubEquipmentNames();

  /**
   * @param processId The process ID
   * @return the process name or "UNKNOWN"
   */
  String getProcessName(Long processId);

  /**
   * @param equipmentId The equipment ID
   * @return the equipment name or "UNKNOWN"
   */
  String getEquipmentName(Long equipmentId);

  /**
   * @param subEquipmentId The sub-equipment ID
   * @return the sub-equipment name or "UNKNOWN"
   */
  String getSubEquipmentName(Long subEquipmentId);

  /**
   * @param processId The process ID
   * @return the process {@link SupervisionEvent} or <code>null</code> if unknown
   */
  SupervisionEvent getProcessSupervisionEvent(Long processId);

  /**
   * @param equipmentId The equipment ID
   * @return the equipment {@link SupervisionEvent} or <code>null</code> if unknown
   */
  SupervisionEvent getEquipmentSupervisionEvent(Long equipmentId);

  /**
   * @param subEquipmentId The sub-equipment ID
   * @return the sub-equipment {@link SupervisionEvent} or <code>null</code> if unknown
   */
  SupervisionEvent getSubEquipmentSupervisionEvent(Long subEquipmentId);
}
