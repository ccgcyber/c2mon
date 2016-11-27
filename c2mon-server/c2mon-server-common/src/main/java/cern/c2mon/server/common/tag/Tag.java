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
package cern.c2mon.server.common.tag;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Set;

import cern.c2mon.shared.client.expression.Expression;
import cern.c2mon.shared.common.Cacheable;
import cern.c2mon.shared.common.datatag.DataTagQuality;
import cern.c2mon.shared.common.metadata.Metadata;
import cern.c2mon.shared.common.rule.RuleInputValue;

/**
 * The Tag interface is the common interface for all tag objects within the TIM system:
 * data, control and rule tags.
 *
 * <p>It only provides read methods as in general this object should only be modified
 * by the cache modules (with the object residing in the cache).
 *
 * @author Mark Brightwell
 *
 */
public interface Tag extends RuleInputValue, Cacheable {

  /**
   * Get the unique numeric identifier
   * @return returns the id
   **/
  Long getId();

  /**
   * Get the unique tag name
   * @return the name of the cache object
   */
  String getName();

  Object clone() throws CloneNotSupportedException;

  /** Get a free-text description of the tag */
  String getDescription();

  /**
   * Get the data type of the tag's current value.
   */
  String getDataType();

  /**
   * @return the JAPC address on which the data tag is published,
   *         or <code>null</code> if not.
   */
  String getJapcAddress();

  /** Get the mode of the tag.
   * Possible modes are
   * <UL>
   * <LI>MODE_OPERATIONAL</LI>
   * <LI>MODE_TEST</LI>
   * <LI>MODE_MAINTENANCE</LI>
   * </UL>
   */
  short getMode();

  /** Returns true if the mode is MODE_OPERATIONAL */
  boolean isInOperation();

  /** Returns true if the mode is MODE_MAINTENANCE */
  boolean isInMaintenance();

  /** Returns true if the mode is MODE_TEST */
  boolean isInTest();

  /**
   * The cache sets the mode to "UNCONFIGURED" when it first receives and unknown
   * datatag. A datatag in UNCONFIGURED mode will stay in UNCONFIGURED mode until
   * it is configured in the database.
   * @return true if running in UNCONFIGURED mode (i.e. not configured in DB).
   */
  boolean isInUnconfigured();

  /** Returns the tag's current value */
  Object getValue();

  /** Returns a textual description of the tag's current value, if available */
  String getValueDescription();

  /**
   * Returns the tag's data quality
   * @return the quality object
   **/
  DataTagQuality getDataTagQuality();

  /**
   * Returns the tag's unit, if available
   * @return the unit as String
   **/
  String getUnit();

  /**
   * Returns the Meta Data from the Tag
   * @return all meta data as a Map
   */
  Metadata getMetadata();

  /** Returns true if the tag's value is valid */
  boolean isValid();

  /**
   * Returns true if the tag is a regular tag, not a "fake" tag created on a
   * client's request.
   */
  boolean isExistingTag();

  /** Returns true if the tag's current value is the result of a simulation */
  boolean isSimulated();

  /**
   * Returns a collection containing the identifiers of all rules that need to
   * be evaluated when THIS tags changes. If this tag isn't used by any rule,
   * an empty collection is returned (never returns null).
   * @return the collection of Ids
   */
  Collection<Long> getRuleIds();

  /**
   * Returns an own copy of the list of rules that need evaluating when
   * this tag changes.
   * @return list of rule ids
   */
  Collection<Long> getCopyRuleIds();

  /**
   * The server cache timestamp indicates when the change message passed the server
   * @return The server cache timestamp
   */
  Timestamp getCacheTimestamp();

  /**
   * Current implementation returns the "lowest" level timestamp that
   * is set in this Tag object (from lowest: source, daq, cache timestamp).
   * <b>Should never return null.</b>
   * @return The "lowest" level timestamp (source, daq or cache timestamp).
   */
  Timestamp getTimestamp();

  /**
   * Returns the Equipments this Tag depends on (for rules
   * this includes all "parent" equipments). If the Tag has no
   * associated Equipment, the returned list will be empty.
   * Should never return a null list.
   *
   * @return list of Equipment ids; can be empty; never null
   */
  Set<Long> getEquipmentIds();

  /**
   * Returns the Processes this Tag depends on (for rules
   * this includes all "parent" processes). If the Tag has no
   * associated Equipment, the returned list will be empty.
   * Should never return a null list.
   *
   * @return list of Process ids; can be empty; never null
   */
  Set<Long> getProcessIds();

  /**
   * Returns the SubEquipments this Tag depends on (for rules
   * this includes all "parent" equipments). If the Tag has no
   * associated SubEquipment, the returned list will be empty.
   * Should never return a null list.
   *
   * @return list of SubEquipment ids; can be empty; never null
   */
  Set<Long> getSubEquipmentIds();

  /**
   * Returns the ids of the alarms set on this Tag.
   * Never returns null.
   *
   * <p>Modifications to the Collection during reconfiguration
   * need locking on the Tag writeLock.
   *
   * @return a collection of Ids
   */
  Collection<Long> getAlarmIds();

  /**
   * Returns an own copy of the list of alarm set on this Tag.
   *
   * <p>Never returns null
   * @return copy of list of alarm ids
   */
  Collection<Long> getCopyAlarmIds();

  /**
   * Returns the optional address on which this tag is published to DIP.
   * Can be null.
   * @return the DIP address as String; can return null!
   */
  String getDipAddress();

  /**
   * Returns true if the tag should be saved in the history.
   * @return true if needs logging
   */
  boolean isLogged();

  /**
   * @return all expressions which are attached to this tag
   */
  Collection<Expression> getExpressions();
}
