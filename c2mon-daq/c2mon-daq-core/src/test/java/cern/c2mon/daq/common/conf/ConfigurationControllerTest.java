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
package cern.c2mon.daq.common.conf;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Test;

import cern.c2mon.daq.common.conf.core.ConfigurationController;
import cern.c2mon.daq.common.conf.core.DefaultCommandTagChanger;
import cern.c2mon.daq.common.conf.core.DefaultEquipmentConfigurationChanger;
import cern.c2mon.daq.common.conf.core.EquipmentConfigurationFactory;
import cern.c2mon.daq.common.conf.equipment.ICommandTagChanger;
import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.common.conf.equipment.IEquipmentConfigurationChanger;
import cern.c2mon.daq.common.timer.FreshnessMonitor;
import cern.c2mon.daq.config.DaqProperties;
import cern.c2mon.shared.common.ConfigurationException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.command.SourceCommandTag;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.common.process.EquipmentConfiguration;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.common.process.ProcessConfiguration;
import cern.c2mon.shared.daq.config.*;
import cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE;

import static org.junit.Assert.*;


public class ConfigurationControllerTest {

    private static final long TEST_COMMAND_TAG_ID = 2L;

    private static final long TEST_DATA_TAG_ID = 1L;

    private static final long TEST_NOT_EXIST_ID = 1337L;

    private static final long TEST_EQUIPMENT_ID = 1L;

    private static final long TEST_SUB_EQUIPMENT_ID = 2L;

    private static final String DEFAULT_NAME = "default";

    private static final long TEST_PROCESS_ID = 31415926L;

    private ConfigurationController configurationController;

    private ProcessConfiguration processConfiguration;

    @Before
    public void setUp() throws ParserConfigurationException {
        configurationController = new ConfigurationController();
        configurationController.setEquipmentConfigurationFactory(new EquipmentConfigurationFactory());
        configurationController.setFreshnessMonitor(new FreshnessMonitor(new DaqProperties()));
        processConfiguration = new ProcessConfiguration();
        processConfiguration.setProcessID(TEST_PROCESS_ID);
        configurationController.setProcessConfiguration(processConfiguration);
        IEquipmentConfigurationChanger equipmentConfigurationChanger = new DefaultEquipmentConfigurationChanger();
        ICommandTagChanger commandTagChanger = new DefaultCommandTagChanger();
        EquipmentConfiguration equipmentConfiguration = new EquipmentConfiguration();
        equipmentConfiguration.setId(TEST_EQUIPMENT_ID);
        processConfiguration.getEquipmentConfigurations().put(TEST_EQUIPMENT_ID, equipmentConfiguration);

        appendSourceDataTag(TEST_DATA_TAG_ID, equipmentConfiguration);
        appendSourceCommandTag(TEST_COMMAND_TAG_ID, equipmentConfiguration);
        configurationController.putImplementationCommandTagChanger(TEST_EQUIPMENT_ID, commandTagChanger);
        configurationController.putImplementationDataTagChanger(TEST_EQUIPMENT_ID, new IDataTagChanger() {
            @Override
            public void onUpdateDataTag(ISourceDataTag sourceDataTag, ISourceDataTag oldSourceDataTag,
                    ChangeReport changeReport) {
                changeReport.setState(CHANGE_STATE.SUCCESS);
            }

            @Override
            public void onRemoveDataTag(ISourceDataTag sourceDataTag, ChangeReport changeReport) {
                changeReport.setState(CHANGE_STATE.SUCCESS);
            }

            @Override
            public void onAddDataTag(ISourceDataTag sourceDataTag, ChangeReport changeReport) {
                changeReport.setState(CHANGE_STATE.SUCCESS);
            }
        });
        configurationController.putImplementationEquipmentConfigurationChanger(TEST_EQUIPMENT_ID,
                equipmentConfigurationChanger);
    }

    // TODO The test are commented out because they wont work through the ugly System.exit in the loader.
    // @Test
    // public void testConfigurationRejected() {
    // String path = ProcessConfigurationLoaderTest.class.getResource(PROCESS_CONFIGURATION_REJECTED_XML).getPath();
    // String[] array = {"-c", path, "-processName", "TST"};
    // CommandParamsHandler commandParamsHandler = new CommandParamsHandler(array);
    // ProcessRequestSender processRequestSender = createMock(ProcessRequestSender.class);
    // configurationController = new ConfigurationController(null, null);
    // configurationController.setProcessRequestSender(processRequestSender);
    // configurationController.setCommandParamsHandler(commandParamsHandler);
    // configurationController.setProcessConfigurationLoader(processConfigurationLoader);
    //
    // processRequestSender.sendProcessDisconnection();
    // replay(processRequestSender);
    // configurationController.loadProcessConfiguration();
    // verify(processRequestSender);
    // assertEquals(configurationController.getProcessConfiguration().getProcessName(), "TST");
    // assertEquals(configurationController.getProcessConfiguration().getProcessID().longValue(), -1L);
    // }
    //
    // @Test
    // public void testConfigurationUnknown() {
    // String path = ProcessConfigurationLoaderTest.class.getResource(PROCESS_CONFIGURATION_UNKNOWN_TYPE_XML).getPath();
    // String[] array = {"-c", path, "-processName", "TST"};
    // CommandParamsHandler commandParamsHandler = new CommandParamsHandler(array);
    // ProcessRequestSender processRequestSender = createMock(ProcessRequestSender.class);
    // configurationController = new ConfigurationController(null, null);
    // configurationController.setProcessRequestSender(processRequestSender);
    // configurationController.setCommandParamsHandler(commandParamsHandler);
    // configurationController.setProcessConfigurationLoader(processConfigurationLoader);
    //
    // processRequestSender.sendProcessDisconnection();
    // replay(processRequestSender);
    // configurationController.loadProcessConfiguration();
    // verify(processRequestSender);
    // assertEquals(configurationController.getProcessConfiguration().getProcessName(), "TST");
    // assertEquals(configurationController.getProcessConfiguration().getProcessID().longValue(), -1L);
    // }

    private void appendSourceDataTag(long tagId, EquipmentConfiguration equipmentConfiguration) {
        SourceDataTag sourceDataTag = new SourceDataTag(tagId, DEFAULT_NAME, false);
        equipmentConfiguration.getDataTags().put(tagId, sourceDataTag);
    }

    private void appendSourceCommandTag(long tagId, EquipmentConfiguration equipmentConfiguration) {
        SourceCommandTag sourceCommandTag = new SourceCommandTag(tagId, DEFAULT_NAME);
        equipmentConfiguration.getCommandTags().put(tagId, sourceCommandTag);
    }

    @Test
    public void testAddSourceDataTagSuccess() throws ConfigurationException {
        SourceDataTag sourceDataTag = new SourceDataTag(2323L, "none", false);
        DataTagAddress dataTagAddress = new DataTagAddress();
        HardwareAddress hwAddress = new OPCHardwareAddressImpl("asd");
        dataTagAddress.setHardwareAddress(hwAddress);
        sourceDataTag.setAddress(dataTagAddress);
        DataTagAdd dataTagAdd = new DataTagAdd(25L, TEST_EQUIPMENT_ID, sourceDataTag);
        ChangeReport report = configurationController.onDataTagAdd(dataTagAdd);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.getSourceDataTags().size() == 2);
        assertTrue(report.isSuccess());
    }

    @Test
    public void testAddSourceDataTagNoSuccess() {
        DataTagAdd dataTagAdd = new DataTagAdd(25L, TEST_EQUIPMENT_ID, new SourceDataTag(TEST_DATA_TAG_ID, "none",
                false));
        ChangeReport report = configurationController.onDataTagAdd(dataTagAdd);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.getSourceDataTags().size() == 1);
        assertFalse(report.isSuccess());
    }

    @Test
    public void testUpdateDataTagSuccess() {
        DataTagUpdate dataTagUpdate = new DataTagUpdate(435L, TEST_DATA_TAG_ID, TEST_EQUIPMENT_ID);
        String newName = "newName";
        dataTagUpdate.setName(newName);
        DataTagAddressUpdate dataTagAddressUpdate = new DataTagAddressUpdate();
        dataTagAddressUpdate.setGuaranteedDelivery(true);
        dataTagUpdate.setDataTagAddressUpdate(dataTagAddressUpdate);
        EquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        SourceDataTag sourceDataTag = equipmentConfiguration.getDataTags().get(TEST_DATA_TAG_ID);
        assertTrue(sourceDataTag.getName().equals(DEFAULT_NAME));
        ChangeReport report = configurationController.onDataTagUpdate(dataTagUpdate);
        assertTrue(sourceDataTag.getName().equals(newName));
        assertTrue(report.isSuccess());
    }

    @Test
    public void testUpdateDataTagNotExistant() {
        DataTagUpdate dataTagUpdate = new DataTagUpdate(435L, TEST_NOT_EXIST_ID, TEST_EQUIPMENT_ID);
        String newName = "newName";
        dataTagUpdate.setName(newName);
        DataTagAddressUpdate dataTagAddressUpdate = new DataTagAddressUpdate();
        dataTagAddressUpdate.setGuaranteedDelivery(true);
        dataTagUpdate.setDataTagAddressUpdate(dataTagAddressUpdate);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertFalse(equipmentConfiguration.hasSourceDataTag(TEST_NOT_EXIST_ID));
        ChangeReport report = configurationController.onDataTagUpdate(dataTagUpdate);
        assertFalse(report.isSuccess());
        assertNotNull(report.getErrorMessage());
    }

    @Test
    public void testRemoveNonExistentSourceDataTag() {
        DataTagRemove dataTagRemove = new DataTagRemove(3434L, TEST_NOT_EXIST_ID, TEST_EQUIPMENT_ID);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertFalse(equipmentConfiguration.hasSourceDataTag(TEST_NOT_EXIST_ID));
        ChangeReport report = configurationController.onDataTagRemove(dataTagRemove);
        assertFalse(equipmentConfiguration.hasSourceDataTag(TEST_NOT_EXIST_ID));
        assertTrue(report.isSuccess());
        assertNotNull(report.getWarnMessage());
    }

    @Test
    public void testRemoveExistingSourceDataTag() {
        DataTagRemove dataTagRemove = new DataTagRemove(3434L, TEST_DATA_TAG_ID, TEST_EQUIPMENT_ID);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.hasSourceDataTag(TEST_DATA_TAG_ID));
        ChangeReport report = configurationController.onDataTagRemove(dataTagRemove);
        assertFalse(equipmentConfiguration.hasSourceDataTag(TEST_DATA_TAG_ID));
        assertTrue(report.isSuccess());
        assertNotNull(report.getInfoMessage());
    }

    @Test
    public void testAddSourceCommandTagSuccess() throws ConfigurationException {
        SourceCommandTag sourceCommandTag = new SourceCommandTag(233L, "none");
        CommandTagAdd commandTagAdd = new CommandTagAdd(25L, TEST_EQUIPMENT_ID, sourceCommandTag);
        HardwareAddress hwAddress = new OPCHardwareAddressImpl("asd");
        sourceCommandTag.setHardwareAddress(hwAddress);
        ChangeReport report = configurationController.onCommandTagAdd(commandTagAdd);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.getSourceCommandTags().size() == 2);
        assertTrue(report.isReboot());
    }

    @Test
    public void testAddSourceCommandTagNoSuccess() {
        CommandTagAdd commandTagAdd = new CommandTagAdd(25L, TEST_EQUIPMENT_ID, new SourceCommandTag(
                TEST_COMMAND_TAG_ID, "none"));
        ChangeReport report = configurationController.onCommandTagAdd(commandTagAdd);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.getSourceCommandTags().size() == 1);
        assertFalse(report.isSuccess());
    }

    @Test
    public void testRemoveExistentCommandDataTag() {
        CommandTagRemove commandTagRemove = new CommandTagRemove(3434L, TEST_COMMAND_TAG_ID, TEST_EQUIPMENT_ID);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.hasSourceCommandTag(TEST_COMMAND_TAG_ID));
        ChangeReport report = configurationController.onCommandTagRemove(commandTagRemove);
        assertFalse(equipmentConfiguration.hasSourceCommandTag(TEST_COMMAND_TAG_ID));
        assertTrue(report.isReboot());
    }

    @Test
    public void testRemoveNonExistentCommandDataTag() {
        CommandTagRemove commandTagRemove = new CommandTagRemove(3434L, TEST_NOT_EXIST_ID, TEST_EQUIPMENT_ID);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertFalse(equipmentConfiguration.hasSourceCommandTag(TEST_NOT_EXIST_ID));
        ChangeReport report = configurationController.onCommandTagRemove(commandTagRemove);
        assertFalse(equipmentConfiguration.hasSourceCommandTag(TEST_NOT_EXIST_ID));
        assertTrue(report.isSuccess());
        assertNotNull(report.getWarnMessage());
    }

    @Test
    public void testNoCommandTagChangerForImplementationLayer() {
        // remove default changer
        configurationController.putImplementationCommandTagChanger(TEST_EQUIPMENT_ID, null);

        CommandTagRemove commandTagRemove = new CommandTagRemove(3434L, TEST_COMMAND_TAG_ID, TEST_EQUIPMENT_ID);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.hasSourceCommandTag(TEST_COMMAND_TAG_ID));
        ChangeReport report = configurationController.onCommandTagRemove(commandTagRemove);
        assertFalse(equipmentConfiguration.hasSourceCommandTag(TEST_COMMAND_TAG_ID));
        assertTrue(report.getState() == CHANGE_STATE.REBOOT);
        assertNotNull(report.getErrorMessage());
    }

    @Test
    public void testNoDataTagChangerForImplementationLayer() {
        configurationController.putImplementationDataTagChanger(TEST_EQUIPMENT_ID, null);
        DataTagRemove dataTagRemove = new DataTagRemove(3434L, TEST_DATA_TAG_ID, TEST_EQUIPMENT_ID);
        IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(
                TEST_EQUIPMENT_ID);
        assertTrue(equipmentConfiguration.hasSourceDataTag(TEST_DATA_TAG_ID));
        ChangeReport report = configurationController.onDataTagRemove(dataTagRemove);
        assertFalse(equipmentConfiguration.hasSourceCommandTag(TEST_DATA_TAG_ID));
        assertTrue(report.isReboot()); // getState() == CHANGE_STATE.REBOOT);
    }

    @Test
    public void testNoExistantEquipment() {
        CommandTagRemove commandTagRemove = new CommandTagRemove(3434L, TEST_COMMAND_TAG_ID, TEST_NOT_EXIST_ID);
        ChangeReport report = configurationController.onCommandTagRemove(commandTagRemove);
        assertEquals(CHANGE_STATE.FAIL, report.getState());
        assertNotNull(report.getErrorMessage());
        assertTrue(report.getErrorMessage().contains(Long.toString(TEST_NOT_EXIST_ID)));
    }

    @Test
    public void testEquipmentConfigurationUpdate() {
        EquipmentConfigurationUpdate equipmentConfigurationUpdate = new EquipmentConfigurationUpdate(28342L,
                TEST_EQUIPMENT_ID);
        equipmentConfigurationUpdate.setName("asd");
        // equipmentConfigurationUpdate.setAliveInterval(1000L);
        ChangeReport report = configurationController.onEquipmentConfigurationUpdate(equipmentConfigurationUpdate);
        assertEquals(report.getErrorMessage(), CHANGE_STATE.SUCCESS, report.getState());
        assertNotNull(report.getInfoMessage());
    }

    @Test
    public void testProcessConfigurationUpdate() {
        ProcessConfigurationUpdate processConfigurationUpdate = new ProcessConfigurationUpdate(7843543L,
                TEST_PROCESS_ID);
        ChangeReport report = configurationController.onProcessConfigurationUpdate(processConfigurationUpdate);
        assertEquals(CHANGE_STATE.SUCCESS, report.getState());
        assertNotNull(report.getInfoMessage());
    }

    @Test
    public void testFindDataTagSuccess() {
        ISourceDataTag dataTag = configurationController.findDataTag(TEST_DATA_TAG_ID);
        assertEquals(TEST_DATA_TAG_ID, dataTag.getId().longValue());
    }

    @Test
    public void testFindDataTagFailure() {
        ISourceDataTag dataTag = configurationController.findDataTag(TEST_NOT_EXIST_ID);
        assertNull(dataTag);
    }

    @Test
    public void testFindCommandTagSuccess() {
        ISourceCommandTag commandTag = configurationController.findCommandTag(TEST_COMMAND_TAG_ID);
        assertEquals(TEST_COMMAND_TAG_ID, commandTag.getId().longValue());
    }

    @Test
    public void testFindCommandTagFailure() {
        ISourceCommandTag commandTag = configurationController.findCommandTag(TEST_NOT_EXIST_ID);
        assertNull(commandTag);
    }

	@Test
	public void testAddSubEquipmentUnitSuccess() throws ConfigurationException {
		String xml = "<SubEquipmentUnit  id=\"" + TEST_SUB_EQUIPMENT_ID + "\" name=\"E_TEST\">"
				+ "<commfault-tag-id>201498</commfault-tag-id>" + "<commfault-tag-value>false</commfault-tag-value>"
				+ "</SubEquipmentUnit>";

		ChangeReport report = configurationController
				.onSubEquipmentUnitAdd(new SubEquipmentUnitAdd(100L, TEST_SUB_EQUIPMENT_ID, TEST_EQUIPMENT_ID, xml));
		assertFalse(report.isFail());
	}

    //@Test
    public void testAddEquipmentUnitSuccess() throws ConfigurationException {

        EquipmentConfigurationUpdate econfUpdate = new EquipmentConfigurationUpdate();
        econfUpdate.setAliveInterval(60000L);
        econfUpdate.setAliveTagId(4234234L);
        econfUpdate.setChangeId(100L);
        econfUpdate.setCommfaultTagId(555555L);
        econfUpdate.setCommfaultTagValue(true);
        econfUpdate.setEquipmentId(2L);
        econfUpdate.setName("NEW_EQUIPMMENT_2");

        SourceDataTag[] dtags = null;
        SourceCommandTag[] ctags = null;

        //EquipmentUnitAdd eqUnitAdd = new EquipmentUnitAdd(100L, econfUpdate, dtags, ctags);

        //ChangeReport report = configurationController.onEquipmentUnitAdd(eqUnitAdd);

        //IEquipmentConfiguration equipmentConfiguration = processConfiguration.getEquipmentConfigurations().get(2L);

        // assertNotNull(equipmentConfiguration);
        // assertTrue(equipmentConfiguration.getSourceDataTags().size() == 2);

        //assertTrue(report.isSuccess());
    }

}
