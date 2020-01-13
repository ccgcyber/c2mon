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
package cern.c2mon.client.core.tag;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;

import cern.c2mon.client.common.tag.CommandTag;
import cern.c2mon.client.core.service.impl.CommandServiceImpl;
import cern.c2mon.shared.client.command.CommandTagHandle;
import cern.c2mon.shared.client.command.CommandTagValueException;
import cern.c2mon.shared.client.command.RbacAuthorizationDetails;
import cern.c2mon.shared.common.command.AuthorizationDetails;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;

/**
 * This class is used by the {@link CommandServiceImpl} to cache
 * locally on the C2MON client API information about commands.
 * A command is updated by the {@link CommandTagHandle} object
 * which is sent by the C2MON server.
 *
 * @param <T> The class type of the command value
 * @author Matthias Braeger
 * @see CommandServiceImpl
 */
@Root(name = "CommandTag")
public class CommandTagImpl<T> implements CommandTag<T>, Cloneable {

  /** standard String used for unknown commands */
  private static final String CMD_UNKNOWN = "UNKNOWN";

  /** 
   * The prefix is needed to convert the data type string of the 
   * {@link CommandTagHandle} update into a class type
   */
  private static final String VALUE_TYPE_PREFIX = "java.lang.";

  /**
   * Name of the CommandTag represented by the present CommandTagHandle object.
   */
  @Attribute
  private String name;
  
  /**
   * Unique numeric identifier of the CommandTag represented by the 
   * present CommandTagHandle object.
   */
  @Attribute
  private Long id;
  
  /** The ID of the DAQ process to which the command belongs to */
//  @NotNull @Min(1)
  @Element(required = false)
  //TODO: remove required = false once TIMS-798 is applied on production!
  private Long processId;
  
  /** The ID of the DAQ equipment to which the command belongs to */
//  @NotNull @Min(1)
  @Element(required = false)
  //TODO: remove required = false once TIMS-798 is applied on production!
  private Long equipmentId;

  /**
   * (Optional) free-text description of the CommandTag represented by 
   * the present CommandTagHandle object.
   */
  @Element(required = false)
  private String description;

  /**
   * class type of the present CommandTagHandle object. Only values 
   * of this data type can be set using setValue().
   */
  @Element(required = false)
  private Class< ? > valueType;

  /**
   * Client timeout in milliseconds.
   * When a client sends a CommandTagHandle to the server for execution and 
   * has not received a CommandTagReport after 'clientTimeout' milliseconds,
   * it should consider the command execution as failed.
   */
  @Element 
  private int clientTimeout;

  /**
   * Authorized minimum for the command value. 
   * If the client tries to set a value less than this minimum, the 
   * setValue() method will throw a CommandTagValueException. If the minValue 
   * is null, it is not taken into account. The minValue will always be null
   * for non-numeric commands.
   */
  @Element(required = false)
  private Comparable<T> minValue;

  /**
   * Authorized maximum for the command value. 
   * If the client tries to set a value greater than this maximum, the 
   * setValue() method will throw a CommandTagValueException. If the maxValue 
   * is null, it is not taken into account. The maxValue will always be null
   * for non-numeric commands.
   */
  @Element(required = false)
  private Comparable<T> maxValue;
  
  /** The hardware address of the command that comes with CommandTagHandle update */
//  @NotNull
  @Element(name = "HardwareAddress", required = false)
  // TODO: remove required = false once TIMS-798 is applied on production!
  private HardwareAddress hardwareAddress;

  /**
   * The command's value as set by the user.
   * This field will always be null before the user executes the setValue()
   * method.
   */
  private T value;

  /**
   * Details needed to authorize the command on the client.
   */
  private AuthorizationDetails authorizationDetails;


  /**
   * Public default constructor.
   */
  public CommandTagImpl() {
  }

  /**
   * Default Constructor
   * @param pId The command tag id
   */
  public CommandTagImpl(final Long pId) {
    this.id = pId;
    this.name = CMD_UNKNOWN;
    this.description = CMD_UNKNOWN;
  }

  /**
   * This method is used by the {@link CommandServiceImpl} to update the client command cache
   * object with the information received from the C2MON server.
   * 
   * @param commandTagHandle The update sent by the C2MON server
   * @throws RuntimeException In case that the command value data type class cannot be
   *                          determined.
   */
  public void update(final CommandTagHandle<T> commandTagHandle) {
    if (commandTagHandle != null && commandTagHandle.getId().equals(id) && commandTagHandle.isExistingCommand()) {
      this.name = commandTagHandle.getName();
      this.description = commandTagHandle.getDescription();

      String dataType = commandTagHandle.getDataType();
      if (dataType != null && !dataType.equalsIgnoreCase(CMD_UNKNOWN)) {
        if (!dataType.startsWith(VALUE_TYPE_PREFIX)) {
          dataType = VALUE_TYPE_PREFIX.concat(dataType);
        }
        try {
          valueType = Class.forName(dataType);
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException("Cannot find value type class " + dataType + " for command " + id);
        }
      }

      this.clientTimeout = commandTagHandle.getClientTimeout();
      this.minValue = commandTagHandle.getMinValue();
      this.maxValue = commandTagHandle.getMaxValue();
      if (commandTagHandle.getValue() != null) {
        this.value = commandTagHandle.getValue();
      }
      this.authorizationDetails = commandTagHandle.getAuthorizationDetails();
      this.hardwareAddress = commandTagHandle.getHardwareAddress();
      this.processId = commandTagHandle.getProcessId();
      this.equipmentId = commandTagHandle.getEquipmentId();
    }
  }

  /**
   * Get the unique numeric identifier of the CommandTag represented by the 
   * present CommandTagHandle object.
   */
  public Long getId() {
    return this.id;
  }

  /**
   * Get the name of the CommandTag represented by the present CommandTagHandle
   * object.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Get the (optional) free-text description of the CommandTag represented by 
   * the present CommandTagHandle object.
   */
  public String getDescription() {
    return this.description;
  }

  /**
   * Get the client timeout in milliseconds.
   * When a client sends a CommandTagHandle to the server for execution and 
   * has not received a CommandTagReport after 'clientTimeout' milliseconds,
   * it should consider the command execution as failed.
   */
  public int getClientTimeout() {
    return this.clientTimeout;
  }

  /**
   * Get the authorized maximum for the command value. 
   * If the client tries to set a value greater than this maximum, the 
   * setValue() method will throw a CommandTagValueException. If the maxValue 
   * is null, it is not taken into account. The maxValue will always be null
   * for non-numeric commands.
   */
  public Comparable<T> getMaxValue() {
    return this.maxValue;
  }

  /**
   * Get the authorized minimum for the command value. 
   * If the client tries to set a value less than this minimum, the 
   * setValue() method will throw a CommandTagValueException. If the minValue 
   * is null, it is not taken into account. The minValue will always be null
   * for non-numeric commands.
   */
  public Comparable<T> getMinValue() {
    return this.minValue;
  }

  /**
   * Check whether the present CommandTagHandle object represents a CommandTag
   * that exists on the server. If not, the client will not be able to 
   * execute the command. Preferably, clients should check isExistingCommand()
   * BEFORE they call the setValue() method. If the command doesn't exist,
   * setValue() will throw a CommandTagValueException.
   */
  public boolean isExistingCommand() {
    return (!name.equals(CommandTagHandle.CMD_UNKNOWN));
  }

  /**
   * Set the command value
   * This method must be called before CommandTagHandle objects are sent to the
   * server for command execution. The method will throw a CommandTagValueException
   * if one of the following conditions is met:
   * <UL>
   * <LI>the set value is null
   * <LI>the user is not authorized to execute this command
   * <LI>the present CommandTagHandle object does not represent a CommandTag that
   * exists on the server
   * <LI>the set value is not between the authorized minimum and maximum values
   */
  public void setValue(T value) throws CommandTagValueException {
    if (isExistingCommand()) {
      if (value == null) {
        throw new NullPointerException("It not possible to set the value to null");
      }
      if (value.getClass() == valueType) {
        this.value = value;
      }
      else {
        throw new CommandTagValueException(
            "Wrong value type! Only values of type " + valueType.getName() + " are allowed.");
      }
    }
    else {
      throw new IllegalStateException("The command " + id + " is not known to the system.");
    }
  }

  @Override
  public T getValue() {
    return this.value;
  }


  @Override
  public Class< ? > getValueType() {
    return valueType;
  }

  /**
   * Returns the authorizations details for this command. Please notice
   * that the authorizations details have to be casted into the specific
   * implementation. In case of CERN it will be casted into an
   * {@link RbacAuthorizationDetails} object.
   * @return The authorizations details for this command.
   */
  public AuthorizationDetails getAuthorizationDetails() {
    return authorizationDetails;
  }

  /**
   * This method overwrites the standard clone method. It generates a clone
   * of this class instance but leaves the authorizationDetails to null.
   * This should prevent that anybody outside of the C2MON Client API is
   * able to read and/or manipulate it.
   * 
   * @throws CloneNotSupportedException In case the implementation
   *         of that interface is not supporting the clone method.
   */
  @SuppressWarnings("unchecked")
  @Override
  public CommandTag<T> clone() throws CloneNotSupportedException {
    CommandTagImpl<T> clone = (CommandTagImpl<T>) super.clone();
    clone.authorizationDetails = null;
    return clone;
  }

  @Override
  public String getXml() {
    Serializer serializer = new Persister(new AnnotationStrategy());
    StringWriter fw = null;
    String result = null;

    try {
      fw = new StringWriter();
      serializer.write(this, fw);
      result = fw.toString();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (fw != null) {
        try {
          fw.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return result;
  }

  public static CommandTagImpl fromXml(final String xml) throws Exception {

    CommandTagImpl commandTag = null;
    StringReader sr = null;
    Serializer serializer = new Persister(new AnnotationStrategy());

    try {
      sr = new StringReader(xml);
      commandTag = serializer.read(CommandTagImpl.class, new StringReader(xml), false);
    } finally {

      if (sr != null) {
        sr.close();
      }
    }

    return commandTag;
  }

  @Override
  public String toString() {
    return this.getXml();
  }

  @Override
  public final HardwareAddress getHardwareAddress() {
    return hardwareAddress;
  }

  @Override
  public final Long getProcessId() {
    return processId;
  }
  
  @Override
  public final Long getEquipmentId() {
    return equipmentId;
  }
}
