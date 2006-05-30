package de.lmu.ifi.dbs.algorithm.outlier;

import de.lmu.ifi.dbs.data.DatabaseObject;
import de.lmu.ifi.dbs.database.AssociationID;
import de.lmu.ifi.dbs.database.Database;
import de.lmu.ifi.dbs.database.ObjectAndAssociations;
import de.lmu.ifi.dbs.database.connection.AbstractDatabaseConnection;
import de.lmu.ifi.dbs.distance.DoubleDistance;
import de.lmu.ifi.dbs.parser.ObjectAndLabels;
import de.lmu.ifi.dbs.parser.Parser;
import de.lmu.ifi.dbs.parser.ParsingResult;
import de.lmu.ifi.dbs.parser.RealVectorLabelParser;
import de.lmu.ifi.dbs.properties.Properties;
import de.lmu.ifi.dbs.utilities.Description;
import de.lmu.ifi.dbs.utilities.QueryResult;
import de.lmu.ifi.dbs.utilities.UnableToComplyException;
import de.lmu.ifi.dbs.utilities.Util;
import de.lmu.ifi.dbs.utilities.optionhandling.OptionHandler;
import de.lmu.ifi.dbs.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.utilities.optionhandling.WrongParameterValueException;
import de.lmu.ifi.dbs.algorithm.result.LOFResult;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Online algorithm to efficiently update density-based local
 * outlier factors in a database after insertion or deletion of new objects.
 *
 * @author Elke Achtert (<a href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public class OnlineLOF<O extends DatabaseObject> extends LOF<O> {
  /**
   * Holds the class specific debug status.
   */
  @SuppressWarnings({"UNUSED_SYMBOL"})
//  private static final boolean DEBUG = LoggingConfiguration.DEBUG;
  private static final boolean DEBUG = true;

  /**
   * The logger of this class.
   */
  private Logger logger = Logger.getLogger(this.getClass().getName());

  /**
   * Parameter lof.
   */
  public static final String LOF_P = "lof";

  /**
   * Description for parameter lof.
   */
  public static final String LOF_D = "<filename>file that contains the LOFs of the input file.";

  /**
   * Parameter nn.
   */
  public static final String NN_P = "nn";

  /**
   * Description for parameter n.
   */
  public static final String NN_D = "<filename>file that contains the nearest neighbors of the input file.";

  /**
   * Parameter lof.
   */
  public static final String INSERTIONS_P = "insertions";

  /**
   * Description for parameter lof.
   */
  public static final String INSERTIONS_D = "<filename>file that contains the objects to be inserted.";

  /**
   * Default parser.
   */
  public final static String DEFAULT_PARSER = RealVectorLabelParser.class.getName();

  /**
   * Parameter parser.
   */
  public final static String PARSER_P = "parser";

  /**
   * Description of parameter parser.
   */
  public final static String PARSER_D = "<class>a parser to parse the insertion and/or deletion files " +
                                        Properties.KDD_FRAMEWORK_PROPERTIES.restrictionString(Parser.class) +
                                        ". Default: " + DEFAULT_PARSER;

  /**
   * The objects to be inserted.
   */
  private List<ObjectAndAssociations<O>> insertions;

  /**
   * Sets minimum points to the optionhandler additionally to the
   * parameters provided by super-classes. Since LOF is a non-abstract
   * class, finally optionHandler is initialized.
   */
  public OnlineLOF() {
    super();
    parameterToDescription.put(LOF_P + OptionHandler.EXPECTS_VALUE, LOF_D);
    parameterToDescription.put(NN_P + OptionHandler.EXPECTS_VALUE, NN_D);
    parameterToDescription.put(INSERTIONS_P + OptionHandler.EXPECTS_VALUE, INSERTIONS_D);
    parameterToDescription.put(PARSER_P + OptionHandler.EXPECTS_VALUE, PARSER_D);
    optionHandler = new OptionHandler(parameterToDescription, this.getClass().getName());
  }

  /**
   * The run method encapsulated in measure of runtime. An extending class
   * needs not to take care of runtime itself.
   *
   * @param database the database to run the algorithm on
   * @throws IllegalStateException if the algorithm has not been initialized properly (e.g. the
   *                               setParameters(String[]) method has been failed to be called).
   */
  protected void runInTime(Database<O> database) throws IllegalStateException {
    getDistanceFunction().setDatabase(database, isVerbose(), isTime());
    try {
      for (ObjectAndAssociations<O> objectAndAssociations : insertions) {
        insert(database, objectAndAssociations);
      }
    }
    catch (UnableToComplyException e) {
      throw new IllegalStateException(e);
    }

    result = new LOFResult<O>(database, lofTable, nnTable);
  }

  /**
   * Returns a description of the algorithm.
   *
   * @return a description of the algorithm
   */
  public Description getDescription() {
    return new Description("OnlineLOF", "Online Local Outlier Factor",
                           "Algorithm to efficiently update density-based local outlier factors in a database " +
                           "after insertion or deletion of new objects. ",
                           "unpublished.");
  }

  /**
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#setParameters(String[])
   */
  public String[] setParameters(String[] args) throws ParameterException {
    String[] remainingParameters = super.setParameters(args);

    // lofTable
    String lofString = optionHandler.getOptionValue(LOF_P);
    try {
      lofTable = new LOFTable(lofString, pageSize, cacheSize, minpts);
    }
    catch (IOException e) {
      throw new WrongParameterValueException(LOF_P, lofString, LOF_D, e);
    }

    // nnTable
    String nnString = optionHandler.getOptionValue(NN_P);
    try {
      nnTable = new NNTable(nnString, pageSize, cacheSize, minpts);
    }
    catch (IOException e) {
      throw new WrongParameterValueException(NN_P, nnString, NN_D, e);
    }

    // parser for insertions
    String parserClass = optionHandler.isSet(PARSER_P) ?
                         optionHandler.getOptionValue(PARSER_P) :
                         DEFAULT_PARSER;
    Parser<O> parser;
    try {
      //noinspection unchecked
      parser = Util.instantiate(Parser.class, parserClass);
    }
    catch (UnableToComplyException e) {
      throw new WrongParameterValueException(PARSER_P, parserClass, PARSER_D);
    }

    // insertions
    String insertionString = optionHandler.getOptionValue(INSERTIONS_P);
    try {
      InputStream in = new FileInputStream(insertionString);
      ParsingResult<O> parsingResult = parser.parse(in);
      insertions = transformObjectAndLabels(parsingResult.getObjectAndLabelList());
    }
    catch (FileNotFoundException e) {
      throw new WrongParameterValueException(INSERTIONS_P, insertionString, INSERTIONS_D, e);
    }

    setParameters(args, remainingParameters);
    return remainingParameters;
  }

  private void insert(Database<O> database, ObjectAndAssociations<O> objectAndAssociation) throws UnableToComplyException {
    // insert o into db
    Integer o = database.insert(objectAndAssociation);
    // get neighbors and reverse nearest neighbors of o
    List<QueryResult<DoubleDistance>> neighbors = database.kNNQueryForID(o, minpts + 1, getDistanceFunction());
    List<QueryResult<DoubleDistance>> reverseNeighbors = database.reverseKNNQuery(o, minpts + 1, getDistanceFunction());
    neighbors.remove(0);

    if (DEBUG) {
      StringBuffer msg = new StringBuffer();
      msg.append("\nkNNs " + neighbors);
      msg.append("\nrNNs " + reverseNeighbors);
      logger.fine(msg.toString());
    }

    // 0. insert update-object o in NNTable and LOFTable
    insertObjectIntoTables(o, neighbors);

    // Changes on existing objects
    double kNNDist_o = neighbors.get(minpts - 1).getDistance().getDoubleValue();
    for (int i = 0; i < reverseNeighbors.size(); i++) {
      QueryResult<DoubleDistance> qr = reverseNeighbors.get(i);
      Integer p = qr.getID();

      double dist_po = getDistanceFunction().distance(p, o).getDoubleValue();
      double reachDist_po = Math.max(kNNDist_o, dist_po);

      // 1. Consequences of changing neighbors(p),
      // i.e. o has been added to the neighbors(p),
      // therefor another object is no longer in neighbors(p)
      NeighborList neighbors_p_old = nnTable.getNeighbors(p);

      // 1.1 determine index von o in neighbors(p)
      int index = 0;
      for (index = 0; index < neighbors_p_old.size(); index++) {
        Neighbor neighbor_p_old = neighbors_p_old.get(index);
        if (dist_po < neighbor_p_old.getDistance() ||
            (dist_po == neighbor_p_old.getDistance() && p < neighbor_p_old.getNeighborID()))
          break;
      }

      // 1.2 insert o as index-th neighbor of p in nnTable
      Neighbor neighbor_p_new = new Neighbor(p, index, o, reachDist_po, dist_po);
      Neighbor neighbor_p_old = nnTable.insertAndMove(neighbor_p_new);
      double knnDistance_p = Math.max(dist_po, neighbors_p_old.get(minpts - 2).getDistance());

      // 1.3.1 update sum1 of lof(p)
      LOFEntry lof_p = lofTable.getLOFEntryForUpdate(p);
      double sum1_p = lof_p.getSum1()
                      - neighbor_p_old.getReachabilityDistance()
                      + neighbor_p_new.getReachabilityDistance();
      lof_p.setSum1(sum1_p);

      // 1.3.2 update sum2 of lof(p)
      double sumReachDists_p = nnTable.getSumOfReachabiltyDistances(o);
      lof_p.insertAndMoveSum2(index, sumReachDists_p);

      NeighborList rnns_p = nnTable.getReverseNeighbors(p);
      for (Neighbor q : rnns_p) {
        // 1.4 for all q in rnn(p): update sum2 of lof(q)
        LOFEntry lof_q = lofTable.getLOFEntryForUpdate(q.getObjectID());
        double sum2_q_old = lof_q.getSum2(q.getIndex());
        double sum2_q = sum2_q_old
                        - neighbor_p_old.getReachabilityDistance()
                        + neighbor_p_new.getReachabilityDistance();
        lof_q.insertAndMoveSum2(q.getIndex(), sum2_q);

        // 2. Consequences of changing reachdist(q,p)
        double reachDist_qp_old = q.getReachabilityDistance();
        double reachDist_qp_new = Math.max(q.getDistance(), knnDistance_p);
        if (reachDist_qp_old != reachDist_qp_new) {
          // 2.1 update reachdist(q,p)
          nnTable.setReachabilityDistance(q, reachDist_qp_new);

          // 2.2 update sum1 of lof(q)
          double sum1_q = lof_q.getSum1()
                          - reachDist_qp_old
                          + reachDist_qp_new;
          lof_q.setSum1(sum1_q);

          // 2.3 for all r in rnn(q): update sum2 of lof(r)
          NeighborList rnns_q = nnTable.getReverseNeighborsForUpdate(q.getObjectID());
          for (Neighbor r : rnns_q) {
            LOFEntry lof_r = lofTable.getLOFEntryForUpdate(r.getObjectID());
            double sum2_r = lof_r.getSum2(r.getIndex())
                            - reachDist_qp_old
                            + reachDist_qp_new;
            lof_r.insertAndMoveSum2(r.getIndex(), sum2_r);
          }
        }
      }
    }
  }

  private void insertObjectIntoTables(Integer id, List<QueryResult<DoubleDistance>> neighbors) {
    double sum1 = 0;
    double[] sum2 = new double[minpts];

    for (int i = 0; i < minpts; i++) {
      QueryResult<DoubleDistance> qr = neighbors.get(i);
      Integer p = qr.getID();

      // insert into NNTable
      NeighborList neighbors_p = nnTable.getNeighbors(p);
      double knnDist_p = neighbors_p.get(minpts - 1).getDistance();
      double dist = getDistanceFunction().distance(id, p).getDoubleValue();
      double reachDist = Math.max(knnDist_p, dist);
      Neighbor neighbor = new Neighbor(id, i, p, reachDist, dist);
      nnTable.insert(neighbor);

      // sum1 von LOF (ok)
      sum1 += reachDist;

      // sum2 von lof (can be changed)
      double sum = 0;
      for (Neighbor q : neighbors_p) {
        sum += q.getReachabilityDistance();
      }
      sum2[i] = sum;
    }
    LOFEntry lofEntry = new LOFEntry(sum1, sum2);
    lofTable.insert(id, lofEntry);

    if (DEBUG) {
      logger.fine("LOF " + id + " " + lofEntry);
    }
  }

  /**
   * Transforms the specified list of objects and their labels into a list of
   * objects and their associtaions.
   *
   * @param objectAndLabelsList the list of object and their labels to be transformed
   * @return a list of objects and their associations
   */
  private List<ObjectAndAssociations<O>> transformObjectAndLabels(List<ObjectAndLabels<O>> objectAndLabelsList) {
    List<ObjectAndAssociations<O>> result = new ArrayList<ObjectAndAssociations<O>>();

    for (ObjectAndLabels<O> objectAndLabels : objectAndLabelsList) {
      List<String> labels = objectAndLabels.getLabels();

      StringBuffer label = new StringBuffer();
      for (int i = 0; i < labels.size(); i++) {
        String l = labels.get(i).trim();
        if (l.length() != 0) {
          if (label.length() == 0) {
            label.append(l);
          }
          else {
            label.append(AbstractDatabaseConnection.LABEL_CONCATENATION);
            label.append(l);
          }
        }
      }

      Map<AssociationID, Object> associationMap = new Hashtable<AssociationID, Object>();
      if (label.length() != 0)
        associationMap.put(AssociationID.LABEL, label.toString());

      result.add(new ObjectAndAssociations<O>(
        objectAndLabels.getObject(), associationMap));
    }
    return result;
  }
}
