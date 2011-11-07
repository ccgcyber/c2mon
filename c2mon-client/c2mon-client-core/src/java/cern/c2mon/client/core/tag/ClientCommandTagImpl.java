package cern.c2mon.client.core.tag;

import cern.c2mon.client.common.tag.ClientCommandTag;
import cern.tim.shared.client.command.CommandTagHandle;
import cern.tim.shared.client.command.CommandTagValueException;
import cern.tim.shared.client.command.RbacAuthorizationDetails;
import cern.tim.shared.common.command.AuthorizationDetails;

public class ClientCommandTagImpl<T> implements ClientCommandTag<T>, Cloneable {

  static final String CMD_UNKNOWN = "UNKNOWN";

  /**
   * Unique numeric identifier of the CommandTag represented by the 
   * present CommandTagHandle object.
   */
  private Long id;

  /**
   * Name of the CommandTag represented by the present CommandTagHandle object.
   */
  private String name;

  /**
   * (Optional) free-text description of the CommandTag represented by 
   * the present CommandTagHandle object.
   */
  private String description;

  /**
   * Name of the data type of the present CommandTagHandle object. Only values 
   * of this data type can be set using setValue().
   */
  private String dataType;

  /**
   * Client timeout in milliseconds.
   * When a client sends a CommandTagHandle to the server for execution and 
   * has not received a CommandTagReport after 'clientTimeout' milliseconds,
   * it should consider the command execution as failed.
   */
  private int clientTimeout;

  /**
   * Authorized minimum for the command value. 
   * If the client tries to set a value less than this minimum, the 
   * setValue() method will throw a CommandTagValueException. If the minValue 
   * is null, it is not taken into account. The minValue will always be null
   * for non-numeric commands.
   */
  private Comparable<T> minValue;

  /**
   * Authorized maximum for the command value. 
   * If the client tries to set a value greater than this maximum, the 
   * setValue() method will throw a CommandTagValueException. If the maxValue 
   * is null, it is not taken into account. The maxValue will always be null
   * for non-numeric commands.
   */
  private Comparable<T> maxValue;

  /**
   * The command's value as set by the user.
   * This field will always be null before the user executes the setValue()
   * method.
   */
  private T value;
  
  /**
   * Details needed to authorise the command on the client.
   */
  private AuthorizationDetails authorizationDetails;

  /**
   * Public default constructor.
   */
  public ClientCommandTagImpl() {    
  }
  
  /**
   * Default Constructor
   * @param pId The command tag id
   */
  public ClientCommandTagImpl(final Long pId) {
    this.id = pId;
    this.name = CMD_UNKNOWN;
    this.description = CMD_UNKNOWN;
    this.dataType = CMD_UNKNOWN;
  }
  
  public void update(final CommandTagHandle<T> commandTagHandle) {
    if (commandTagHandle != null && commandTagHandle.getId().equals(id)) {
      this.name = commandTagHandle.getName();
      this.description = commandTagHandle.getDescription();
      this.dataType = commandTagHandle.getDataType();
      this.clientTimeout = commandTagHandle.getClientTimeout();
      this.minValue = commandTagHandle.getMinValue();
      this.maxValue = commandTagHandle.getMaxValue();
      if (commandTagHandle.getValue() != null) {
        this.value = commandTagHandle.getValue();
      }
      this.authorizationDetails = commandTagHandle.getAuthorizationDetails();
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
   * Get the name of the data type of the present CommandTagHandle object.
   * Only values of this data type can be set using setValue().
   */
  public String getDataType() {
    return this.dataType;
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
    this.value = value;
  }

  @Override
  public T getValue() {
    return this.value;
  }


  @Override
  public Class< ? > getType() {
    if (value == null) {
      return null;
    }
    
    return value.getClass();
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
  public ClientCommandTag<T> clone() throws CloneNotSupportedException {
    ClientCommandTagImpl<T> clone = (ClientCommandTagImpl<T>) super.clone();
    clone.authorizationDetails = null;
    return clone;
  }
}
