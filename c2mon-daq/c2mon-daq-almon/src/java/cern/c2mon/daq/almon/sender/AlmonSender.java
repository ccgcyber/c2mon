/**
 * Copyright (c) 2014 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.c2mon.daq.almon.sender;

import cern.c2mon.daq.almon.address.AlarmTripplet;
import cern.c2mon.daq.almon.address.UserProperties;
import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.daq.datatag.ISourceDataTag;

/**
 * This interface defines the operations common to all alarm senders
 * 
 * @author wbuczak
 */
public interface AlmonSender {

    void activate(ISourceDataTag sdt, IEquipmentMessageSender ems, AlarmTripplet alarmTripplet, long userTimestamp,
            UserProperties userProperties);

    void terminate(ISourceDataTag sdt, IEquipmentMessageSender ems, AlarmTripplet alarmTripplet, long userTimestamp);

    void update(ISourceDataTag sdt, IEquipmentMessageSender ems, AlarmTripplet alarmTripplet, long userTimestamp,
            UserProperties userProperties);

}