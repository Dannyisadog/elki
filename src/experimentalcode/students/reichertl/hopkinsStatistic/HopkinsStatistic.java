package experimentalcode.students.reichertl.hopkinsStatistic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import de.lmu.ifi.dbs.elki.algorithm.AbstractPrimitiveDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.KNNOutlier;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.result.BasicResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.DatabaseUtil;
import de.lmu.ifi.dbs.elki.utilities.Util;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.AllOrNoneMustBeSetGlobalConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.EqualSizeGlobalConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleListParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ListParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.LongParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Parameter;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;

/**
 * The Hopkins Statistic measures the probability that a dataset is generated by
 * a uniform data distribution. Data Mining Concepts and Techniques S. 484-485
 * 
 * @author Lisa Reichert
 * 
 * 
 * @param <O> Database object type
 * @param <D> Distance type
 */
public class HopkinsStatistic<V extends NumberVector<V, ?>, D extends NumberDistance<D, ?>> extends AbstractPrimitiveDistanceBasedAlgorithm<V, D, Result> {
  /**
   * The logger for this class.
   */
  private static final Logging logger = Logging.getLogger(KNNOutlier.class);

  public static final OptionID SAMPLESIZE_ID = OptionID.getOrCreateOptionID("hopkins.samplesize", "Size of the datasample.");

  /**
   * The parameter sampleSize
   */
  private int sampleSize;

  /**
   * Parameter to specify the random generator seed.
   */
  public static final OptionID SEED_ID = OptionID.getOrCreateOptionID("hopkins.seed", "The random number generator seed.");

  /**
   * Holds the value of {@link #SEED_ID}.
   */
  private Long seed;

  /**
   * Parameter for minimum.
   */
  public static final OptionID MINIMA_ID = OptionID.getOrCreateOptionID("normalize.min", "a comma separated concatenation of the minimum values in each dimension that are mapped to 0. If no value is specified, the minimum value of the attribute range in this dimension will be taken.");

  /**
   * Parameter for maximum.
   */
  public static final OptionID MAXIMA_ID = OptionID.getOrCreateOptionID("normalize.max", "a comma separated concatenation of the maximum values in each dimension that are mapped to 1. If no value is specified, the maximum value of the attribute range in this dimension will be taken.");

  /**
   * Stores the maximum in each dimension.
   */
  private double[] maxima = new double[0];

  /**
   * Stores the minimum in each dimension.
   */
  private double[] minima = new double[0];

  public HopkinsStatistic(PrimitiveDistanceFunction<? super V, D> distanceFunction, int sampleSize, Long seed, double[] minima, double[] maxima) {
    super(distanceFunction);
    this.sampleSize = sampleSize;
    this.seed = seed;
    this.minima = minima;
    this.maxima = maxima;
  }

  /**
   * Runs the algorithm in the timed evaluation part.
   */
  public HopkinsResult run(Database database, Relation<V> relation) {
    final DistanceQuery<V, D> distanceQuery = database.getDistanceQuery(relation, getDistanceFunction());
    final Random masterRandom = (this.seed != null) ? new Random(this.seed) : new Random();

    KNNQuery<V, D> knnQuery = database.getKNNQuery(distanceQuery, 1);

    Collection<V> uniformObjs = getUniformObjs(relation, masterRandom);
    // wenn seed nicht initialisiert??? geht das so??? oder bei DBIDUtil noch
    // methode ohne seed
    if(this.seed == null)
      seed = System.currentTimeMillis();
    ModifiableDBIDs dataSampleIds = DBIDUtil.randomSample(relation.getDBIDs(), sampleSize, this.seed);

    // Compute Hopkins Statistic
    Iterator<V> iter = uniformObjs.iterator();
    double a = knnQuery.getKNNForObject(iter.next(), 1).get(0).getDistance().doubleValue();
    while(iter.hasNext()) {
      a += knnQuery.getKNNForObject(iter.next(), 1).get(0).getDistance().doubleValue();
    }

    Iterator<DBID> iter2 = dataSampleIds.iterator();
    double b = knnQuery.getKNNForDBID(iter2.next(), 1).get(0).getDistance().doubleValue();
    while(iter.hasNext()) {
      b += knnQuery.getKNNForDBID(iter2.next(), 1).get(0).getDistance().doubleValue();
    }
    double result = a / (a + b);
    return new HopkinsResult(result);
  }

  public <T extends V> Collection<V> getUniformObjs(Relation<V> relation, Random masterRandom) {
    int dim = DatabaseUtil.dimensionality(relation);
    ArrayList<V> result = new ArrayList<V>(this.sampleSize);
    double[] vec = new double[dim];
    V factory = DatabaseUtil.assumeVectorField(relation).getFactory();
    // if no parameter for min max compute from dataset
    if(this.minima == null || this.maxima == null || this.minima.length == 0 || this.maxima.length == 0) {
      Pair<V, V> minmax = DatabaseUtil.computeMinMax(relation);
      for(int i = 0; i < this.sampleSize; i++) {
        for(int d = 0; d < dim; d++) {
          vec[d] = minmax.first.doubleValue(d + 1) + (new Random(masterRandom.nextLong()).nextDouble()) % (minmax.second.doubleValue(d + 1) - minmax.first.doubleValue(d + 1) + 1.0);
        }
        V newp = factory.newInstance(vec);
        result.add(newp);
      }
    }
    else {
      if(this.minima.length == 1 || this.maxima.length == 1) {
        double val = minima[0];
        for(int i = 0; i < dim; i++) {
          minima[i] = val;
        }
        val = maxima[0];
        for(int i = 0; i < dim; i++) {
          maxima[i] = val;
        }
      }
      for(int i = 0; i < this.sampleSize; i++) {
        for(int d = 0; d < dim; d++) {
          // TODO: das ist noch etwas unguenstig - erzeuge d Random Objekte in einer eigenen Schleife und verwende die für die jeweilige Dimension in dieser Schleife
          vec[d] = minima[d] + (new Random(masterRandom.nextLong()).nextDouble()) % (maxima[d] - minima[d] + 1.0); // TODO ist Modulo hier richtig?
        }
        V newp = factory.newInstance(vec);
        result.add(newp);
      }

    }
    return result;
  }

  // public double[][] computeMinMax(Collection<DoubleVector> data){
  // double[] maxValues = {};
  // double[] minValues = {};
  // Iterator<DoubleVector> iter = data.iterator();
  // // initialize minvalues maxvalues
  // if(iter.hasNext()) {
  // DoubleVector object = iter.next();
  // for(int i = 0; i < object.getDimensionality(); i++) {
  // minValues[i] = object.doubleValue(i);
  // maxValues[i] = object.doubleValue(i);
  // }
  // }
  // while(iter.hasNext()) {
  // DoubleVector object = iter.next();
  // for(int i = 0; i < object.getDimensionality(); i++) {
  // if(object.doubleValue(i) < minValues[i]) {
  // minValues[i] = object.doubleValue(i);
  // }
  // if(object.doubleValue(i) > maxValues[i]) {
  // maxValues[i] = object.doubleValue(i);
  // }
  // }
  // }
  // double[][] result= {{},{}};
  // result[0] = maxValues;
  // result[1] = minValues;
  // return result;
  // }

  @Override
  protected Logging getLogger() {
    return logger;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  // TODO: update comments and author information after copy&paste ;-)
  /**
   * Result object for ROC curves.
   * 
   * @author Erich Schubert
   */
  public static class HopkinsResult extends BasicResult {
    /**
     * hopkins value
     */
    private double hopkins;

    /**
     * Constructor.
     * 
     * @param hokins result value Hopkinsstatistic
     */
    public HopkinsResult(double hopkins) {
      super("Hopkinsstatistic", "hopkins");
      this.hopkins = hopkins;
    }

    /**
     * @return the area under curve
     */
    public double getHopkins() {
      return hopkins;
    }
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<V extends NumberVector<V, ?>, D extends NumberDistance<D, ?>> extends AbstractPrimitiveDistanceBasedAlgorithm.Parameterizer<V, D> {

    protected Integer sampleSize;

    protected Long seed;

    /**
     * Stores the maximum in each dimension.
     */
    private double[] maxima = new double[0];

    /**
     * Stores the minimum in each dimension.
     */
    private double[] minima = new double[0];

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);

      IntParameter sample = new IntParameter(SAMPLESIZE_ID, new GreaterConstraint(0));
      if(config.grab(sample)) {
        sampleSize = sample.getValue();
      }
      LongParameter seedP = new LongParameter(SEED_ID, true);
      if(config.grab(seedP)) {
        seed = seedP.getValue();
      }
      DoubleListParameter minimaP = new DoubleListParameter(MINIMA_ID, true);
      if(config.grab(minimaP)) {
        List<Double> min_list = minimaP.getValue();
        minima = Util.unbox(min_list.toArray(new Double[min_list.size()]));
      }
      DoubleListParameter maximaP = new DoubleListParameter(MAXIMA_ID, true);
      if(config.grab(maximaP)) {
        List<Double> max_list = maximaP.getValue();
        maxima = Util.unbox(max_list.toArray(new Double[max_list.size()]));
      }

      ArrayList<Parameter<?, ?>> global_1 = new ArrayList<Parameter<?, ?>>();
      global_1.add(minimaP);
      global_1.add(maximaP);
      config.checkConstraint(new AllOrNoneMustBeSetGlobalConstraint(global_1));

      ArrayList<ListParameter<?>> global = new ArrayList<ListParameter<?>>();
      global.add(minimaP);
      global.add(maximaP);
      config.checkConstraint(new EqualSizeGlobalConstraint(global));

    }

    @Override
    protected HopkinsStatistic<V, D> makeInstance() {
      return new HopkinsStatistic<V, D>(distanceFunction, sampleSize, seed, minima, maxima);
    }
  }
}
