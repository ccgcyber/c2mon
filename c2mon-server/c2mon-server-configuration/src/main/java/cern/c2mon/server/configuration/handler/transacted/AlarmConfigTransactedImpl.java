/******************************************************************************
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
 *****************************************************************************/
package cern.c2mon.server.configuration.handler.transacted;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cern.c2mon.server.cache.AlarmCache;
import cern.c2mon.server.cache.AlarmFacade;
import cern.c2mon.server.cache.exception.CacheElementNotFoundException;
import cern.c2mon.server.cache.loading.AlarmLoaderDAO;
import cern.c2mon.server.common.alarm.Alarm;
import cern.c2mon.server.configuration.handler.impl.TagConfigGateway;
import cern.c2mon.shared.client.configuration.ConfigurationElement;
import cern.c2mon.shared.client.configuration.ConfigurationElementReport;

/**
 * Implementation of transacted methods.
 *
 * @author Mark Brightwell
 *
 */
@Service
public class AlarmConfigTransactedImpl implements AlarmConfigTransacted {

  /**
   * Class logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmConfigTransactedImpl.class);

  /**
   * Reference to the alarm facade.
   */
  private AlarmFacade alarmFacade;

  /**
   * Reference to the alarm DAO.
   */
  private AlarmLoaderDAO alarmDAO;

  /**
   * Reference to the alarm cache.
   */
  private AlarmCache alarmCache;

  /**
   * Reference to gateway to tag configuration beans.
   */
  @Autowired
  private TagConfigGateway tagConfigGateway;

  /**
   * Autowired constructor.
   * @param alarmFacade the alarm facade bean
   * @param alarmDAO the alarm DAO bean
   * @param alarmCache the alarm cache bean
   */
  @Autowired
  public AlarmConfigTransactedImpl(final AlarmFacade alarmFacade, final AlarmLoaderDAO alarmDAO,
                            final AlarmCache alarmCache) {
    super();
    this.alarmFacade = alarmFacade;
    this.alarmDAO = alarmDAO;
    this.alarmCache = alarmCache;
  }

  /**
   * Creates an alarm object in the server (puts in DB and loads into cache,
   * in that order, and updates the associated tag to point to the new
   * alarm).
   *
   * @param element the details of the new alarm object
   * @throws IllegalAccessException should not throw the {@link IllegalAccessException} (only Tags can).
   */
  @Override
  @Transactional(value = "cacheTransactionManager", propagation = Propagation.REQUIRED)
  public void doCreateAlarm(final ConfigurationElement element) throws IllegalAccessException {
    
    Alarm alarm = null;
    
    alarmCache.acquireWriteLockOnKey(element.getEntityId());
    try {
      LOGGER.trace("Creating alarm " + element.getEntityId());
      alarm = alarmFacade.createCacheObject(element.getEntityId(), element.getElementProperties());
      
      try {
        alarmDAO.insert(alarm);
        alarmCache.putQuiet(alarm);
      } catch (Exception e) {
        LOGGER.error("Exception caught while loading creating alarm with id " + element.getEntityId(), e);
        throw new UnexpectedRollbackException("Unexpected exception while creating an Alarm " + element.getEntityId() + ": rolling back the creation", e);
      }
    } finally {
      alarmCache.releaseWriteLockOnKey(element.getEntityId());
    }
    
    // add alarm to tag in cache (no DB persistence here)
    try {
      tagConfigGateway.addAlarmToTag(alarm.getTagId(), alarm.getId());
    } catch (Exception e) {
      LOGGER.error("Exception caught while adding new Alarm " + alarm.getId() + " to Tag " + alarm.getId(), e);
      alarmCache.remove(alarm.getId());
      tagConfigGateway.removeAlarmFromTag(alarm.getTagId(), alarm.getId());
      throw new UnexpectedRollbackException("Unexpected exception while creating a Alarm " + alarm.getId() + ": rolling back the creation", e);
    }
  }

  /**
   * Updates the Alarm object in the server from the provided Properties.
   * In more detail, updates the cache, then the DB.
   *
   * <p>Note that moving the alarm to a different tag is not allowed. In
   * this case the alarm should be removed and recreated.
   * @param alarmId the id of the alarm
   * @param properties the update details
   */
  @Override
  @Transactional(value = "cacheTransactionManager", propagation = Propagation.REQUIRES_NEW)
  public void doUpdateAlarm(final Long alarmId, final Properties properties) {
    //reject if trying to change datatag it is attached to - not currently allowed
    if (properties.containsKey("dataTagId")) {
      LOGGER.warn("Attempting to change the tag to which an alarm is attached - this is not supported yet!");
      properties.remove("dataTagId");
    }
    
    alarmCache.acquireWriteLockOnKey(alarmId);
    try {
      Alarm alarm = alarmCache.getCopy(alarmId);
      alarmFacade.updateConfig(alarm, properties);
      alarmDAO.updateConfig(alarm);
      alarmCache.putQuiet(alarm);
    } catch (CacheElementNotFoundException ex) {
      throw ex;
    } catch (Exception ex) {
      LOGGER.error("Exception caught while updating alarm" + alarmId, ex);
      throw new UnexpectedRollbackException("Unexpected exception caught while updating Alarm " + alarmId, ex);
    } finally {
      alarmCache.releaseWriteLockOnKey(alarmId);
    }
  }

  @Override
  @Transactional(value = "cacheTransactionManager", propagation=Propagation.REQUIRES_NEW)
  public void doRemoveAlarm(final Long alarmId, final ConfigurationElementReport alarmReport) {
    alarmCache.acquireWriteLockOnKey(alarmId);
    try {
      Alarm alarm = alarmCache.get(alarmId);
      alarmDAO.deleteItem(alarmId);
      try {
        removeDataTagReference(alarm);
      } catch (CacheElementNotFoundException e) {
        LOGGER.warn("Unable to remove Alarm reference from Tag, as could not locate Tag " + alarm.getTagId() + " in cache");
      }
    } catch (CacheElementNotFoundException e) {
      LOGGER.debug("Attempting to remove a non-existent Alarm - no action taken.");
      alarmReport.setWarning("Attempting to remove a non-existent Alarm");
    } catch (Exception ex) {
      LOGGER.error("Exception caught while removing Alarm " + alarmId, ex);
      alarmReport.setFailure("Unable to remove Alarm with id " + alarmId);
      throw new UnexpectedRollbackException("Exception caught while attempting to remove an alarm", ex);
    } finally {
      alarmCache.releaseWriteLockOnKey(alarmId);
    }
  }

  /**
   * Removes the reference to the alarm in the associated Tag object.
   * @param alarm the alarm for which the tag needs updating
   */
  private void removeDataTagReference(final Alarm alarm) {
    tagConfigGateway.removeAlarmFromTag(alarm.getTagId(), alarm.getId());
  }

}
