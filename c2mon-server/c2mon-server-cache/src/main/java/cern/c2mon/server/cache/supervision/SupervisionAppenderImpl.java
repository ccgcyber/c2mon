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
package cern.c2mon.server.cache.supervision;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cern.c2mon.server.cache.EquipmentCache;
import cern.c2mon.server.cache.EquipmentFacade;
import cern.c2mon.server.cache.ProcessCache;
import cern.c2mon.server.cache.ProcessFacade;
import cern.c2mon.server.common.tag.Tag;
import cern.c2mon.shared.client.supervision.SupervisionEvent;
import cern.c2mon.shared.common.datatag.TagQualityStatus;

/**
 * Helper bean for adding the supervision status to
 * Tags.
 *
 * @author Mark Brightwell
 */
@Service
@Slf4j
public class SupervisionAppenderImpl implements SupervisionAppender {

  /**
   * Process and Equipment bean interfaces.
   */
  private ProcessFacade processFacade;
  private ProcessCache processCache;
  private EquipmentFacade equipmentFacade;
  private EquipmentCache equipmentCache;

  /**
   * Autowired constructor.
   *
   * @param processFacade   the process facade bean
   * @param processCache    the process cache
   * @param equipmentFacade the equipment facade bean
   * @param equipmentCache  the equipment cache
   */
  @Autowired
  public SupervisionAppenderImpl(final ProcessFacade processFacade, final ProcessCache processCache,
                                 final EquipmentFacade equipmentFacade, final EquipmentCache equipmentCache) {
    super();
    this.processFacade = processFacade;
    this.processCache = processCache;
    this.equipmentFacade = equipmentFacade;
    this.equipmentCache = equipmentCache;
  }

  @Override
  public <T extends Tag> void addSupervisionQuality(final T tagCopy, final SupervisionEvent event) {
    TagQualityStatus tagSupervisionStatus = null;
    String message = event.getMessage(); //will be overwritten below if RUNNING

    switch (event.getEntity()) {

      case PROCESS:
        switch (event.getStatus()) {
          case DOWN:
            tagSupervisionStatus = TagQualityStatus.PROCESS_DOWN;
            break;
          case STOPPED:
            tagSupervisionStatus = TagQualityStatus.PROCESS_DOWN;
            break;
          case RUNNING:
            message = "DAQ process " + event.getEntityId() + " has recovered.";
            break;
          case RUNNING_LOCAL:
            message = "DAQ process " + event.getEntityId() + " has recovered and is running on a local configuration.";
            break;
          default:
            log.error("Unexpected supervision status: " + event.getEntity());
            break;
        }
        break;

      case EQUIPMENT:
        switch (event.getStatus()) {
          case DOWN:
            tagSupervisionStatus = TagQualityStatus.EQUIPMENT_DOWN;
            break;
          case STOPPED:
            tagSupervisionStatus = TagQualityStatus.EQUIPMENT_DOWN;
            break;
          case RUNNING:
            message = "Equipment " + event.getEntityId() + " has recovered.";
            break;
          default:
            log.error("Unexpected supervision status: " + event.getEntity());
            break;
        }
        break;

      case SUBEQUIPMENT:
        switch (event.getStatus()) {
          case DOWN:
            tagSupervisionStatus = TagQualityStatus.SUBEQUIPMENT_DOWN;
            break;
          case STOPPED:
            tagSupervisionStatus = TagQualityStatus.SUBEQUIPMENT_DOWN;
            break;
          case RUNNING:
            message = "SubEquipment " + event.getEntityId() + " has recovered.";
            break;
          default:
            log.error("Unexpected supervision status: " + event.getEntity());
            break;
        }
        break;

      default:
        break;
    }

    if (tagSupervisionStatus != null) {
      tagCopy.getDataTagQuality().addInvalidStatus(tagSupervisionStatus, message);
    }
  }
}