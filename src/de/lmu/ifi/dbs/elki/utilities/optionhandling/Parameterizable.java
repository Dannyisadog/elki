package de.lmu.ifi.dbs.elki.utilities.optionhandling;

import java.util.List;

import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;


/**
 * Interface to define the required methods for command line interaction.
 *
 * @author Arthur Zimek
 */
public interface Parameterizable {
  /**
   * Returns a description of the class and the required parameters.
   * <p/>
   * This description should be suitable for a usage description.
   *
   * @return String a description of the class and the required parameters
   */
  String parameterDescription();
  
  /**
   * Sets the attributes of the class accordingly to the given parameters.
   * Returns a new String array containing those entries of the
   * given array that are neither expected nor used by this
   * Parameterizable.
   *
   * @param args parameters to set the attributes accordingly to
   * @return String[] an array containing the unused parameters
   * @throws ParameterException in case of wrong parameter-setting
   */
  String[] setParameters(String[] args) throws ParameterException;
  
  /**
   * Returns the parameter array as given to the last call
   * of {@link #setParameters(String[]) setParameters(String[])}
   * but without unnecessary entries.
   * The provided array should be suitable to call
   * {@link #setParameters(String[]) setParameters(String[])}
   * with it resulting in the identical parameterization.
   * 
   * @return the parameter array as given to the last call
   * of {@link #setParameters(String[]) setParameters(String[])}
   * but without unnecessary entries
   */
  String[] getParameters();

  /**
   * Checks if all global parameter constraints are kept
   *
   * @throws ParameterException if the parameters don't satisfy the parameter constraints
   */
  void checkGlobalParameterConstraints() throws ParameterException;
  
  /**
   * Fill the given collection with a list of available options.
   *  
   * @param collection collection to fill
   */
  public void collectOptions(List<Pair<Parameterizable, Option<?>>> collection);  
}
