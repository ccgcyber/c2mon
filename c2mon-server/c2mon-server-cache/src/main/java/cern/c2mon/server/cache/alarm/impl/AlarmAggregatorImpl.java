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
package cern.c2mon.server.cache.alarm.impl;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cern.c2mon.server.cache.C2monCacheListener;
import cern.c2mon.server.cache.CacheRegistrationService;
import cern.c2mon.server.cache.CacheSupervisionListener;
import cern.c2mon.server.cache.TagFacadeGateway;
import cern.c2mon.server.cache.alarm.AlarmAggregator;
import cern.c2mon.server.cache.alarm.AlarmAggregatorListener;
import cern.c2mon.server.common.alarm.Alarm;
import cern.c2mon.server.common.tag.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link AlarmAggregator} (a singleton bean in the server
 * context).
 * 
 * <p>
 * This implementation registers for synchronous notifications from the cache
 * (i.e. on original JMS update thread). These calls are passed through to the
 * client module on the same thread (may need adjusting).
 * 
 * @author Mark Brightwell
 *
 */
@Slf4j
@Service
public class AlarmAggregatorImpl implements AlarmAggregator, C2monCacheListener<Tag>, CacheSupervisionListener<Tag> {

  /**
   * List of registered listeners.
   */
  private List<AlarmAggregatorListener> listeners;

  /**
   * Used to register the aggregator as Tag update listener.
   */
  private CacheRegistrationService cacheRegistrationService;

  /**
   * The gateway to all Tag facades.
   */
  private TagFacadeGateway tagFacadeGateway;

  /**
   * Autowired constructor.
   * 
   * @param cacheRegistrationService
   *          the cache registration service (for registration to cache update
   *          notifications)
   * @param tagFacadeGateway
   *          the Tag Facade gateway (for access to all Tag Facade beans)
   * @param tagLocationService
   *          the Tag location service
   */
  @Autowired
  public AlarmAggregatorImpl(final CacheRegistrationService cacheRegistrationService, final TagFacadeGateway tagFacadeGateway) {
    super();
    this.cacheRegistrationService = cacheRegistrationService;
    this.tagFacadeGateway = tagFacadeGateway;
    listeners = new ArrayList<>();
  }

  /**
   * Init method called on bean creation. Registers to cache updates
   * (synchronous to guarantee evaluation of all alarms )
   */
  @PostConstruct
  public void init() {
    // notice: if change to non-synchronous, need to revisit evaluation method,
    // which assumes a lock is held on the tag; else one update could overtake
    // another
    // one at this stage
    cacheRegistrationService.registerSynchronousToAllTags(this);
    cacheRegistrationService.registerForSupervisionChanges(this);
  }

  @Override
  public void registerForTagUpdates(final AlarmAggregatorListener aggregatorListener) {
    listeners.add(aggregatorListener);
  }

  /**
   * When an update to a Tag is received from the cache, evaluates the
   * associated Alarms and notifies the (alarm + tag) listeners.
   * 
   * <p>
   * Notice that received Tag is a clone, but since the cache notification is
   * synchronous a lock is already held on this tag, which can therefore not be
   * modified during this call.
   * 
   * @param tag
   *          a clone of the updated Tag received from the cache
   */
  @Override
  public void notifyElementUpdated(final Tag tag) {
    List<Alarm> alarmList = evaluateAlarms(tag);
    notifyListeners(tag, alarmList);
  }

  /**
   * Notify the listeners of a tag update with associated alarms.
   * 
   * @param tag
   *          the Tag that has been updated
   * @param alarmList
   *          the associated list of evaluated alarms
   */
  private void notifyListeners(final Tag tag, final List<Alarm> alarmList) {
    for (AlarmAggregatorListener listener : listeners) {
      try {
        listener.notifyOnUpdate((Tag) tag.clone(), alarmList);
      } catch (CloneNotSupportedException e) {
        log.error("Unexpected exception caught: clone should be implemented for this class! " + "Alarm & tag listener was not notified: " + listener.getClass().getSimpleName());
      }
    }
  }

  @Override
  public void onSupervisionChange(final Tag tag) {
    log.trace("Evaluating alarm for tag " + tag.getId() + " due to supervision status notification.");

    evaluateAlarms(tag);
  }

  private List<Alarm> evaluateAlarms(final Tag tag) {
    List<Alarm> alarmList = null;
    if (!tag.getAlarmIds().isEmpty()) {
      try {
        alarmList = tagFacadeGateway.evaluateAlarms(tag);
        if (alarmList.isEmpty()) {
          log.warn("Empty alarm list returned when evaluating alarms for tag " + tag.getId() + " - this should not be happening (possible timestamp filtering problem)");
        }

      } catch (Exception e) {
        log.error("Exception caught when attempting to evaluate the alarms for tag " + tag.getId() + " - publishing to the client with no attached alarms.", e);
      }
    }
    return alarmList;
  }

  @Override
  public void confirmStatus(Tag tag) {
    // do not take any action here: a new evaluation here does not use the
    // current supervision status of the tag,
    // so will override any current TERMINATED alarms due to supervision status
    // down
    // During a server recovery start, a callback will be provided on the
    // onSupervisionChange method, which will
    // re-evaluate all alarms.
  }

}
