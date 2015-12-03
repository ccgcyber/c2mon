/**
 * Copyright (c) 2015 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.c2mon.publisher.mobicall;

import static org.junit.Assert.*;

import java.sql.Timestamp;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import cern.c2mon.shared.client.alarm.AlarmValue;
import cern.c2mon.shared.client.alarm.AlarmValueImpl;

/**
 * Once the test configuration data is loaded, checks that querying the list of alarms works
 * and that the message building method provides the expected result for a given alarm
 * 
 * @author mbuttner
 */
public class TestConfiguration {

    //
    // --- SETUP test ------------------------------------------------------------------------------
    //
    @BeforeClass
    public static void initClass() throws Exception {
        TestUtil.init();
        TestUtil.startTestPublisher();
    }
 
    @AfterClass
    public static void cleanupClass() throws Exception {
        TestUtil.stopTestPublisher();
    }
    
    //
    // --- TESTS  -----------------------------------------------------------------------------------
    //
    @Test
    public void testConfig() {
        
        // check that existing alarms are found
        assertNotNull(TestUtil.loader.find("FF:FM:1"));
        assertNotNull(TestUtil.loader.find("FF:FM:2"));
        
        // check that if an alarm has no notification id, null wil be returned by the query
        assertNull(TestUtil.loader.find("FF:FM:3"));
        
        // check the triplet building method (system, identifier, fault code -> alarm id "system:identifier:fault_code")
        AlarmValue av = new AlarmValueImpl(1L, 1, "FM", "FF", "Info", 1L, new Timestamp(System.currentTimeMillis()), true);
        assertEquals(MobicallAlarmsPublisher.getAlarmId(av), "FF:FM:1");

        // build the message data for a Mobicall message based on a given alarm and validate it is the one
        // it always was
        String expected ="FF FM 1 [111]  Dummy problem description ACTIVE";
        assertEquals(expected, MobicallAlarmsPublisher.composeTrapMessage(TestUtil.loader.find("FF:FM:1"), av));        
    }
        
}
