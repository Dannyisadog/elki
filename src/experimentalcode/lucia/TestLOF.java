package experimentalcode.lucia;

import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import org.junit.Test;

import de.lmu.ifi.dbs.elki.algorithm.outlier.LOF;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.evaluation.roc.ComputeROCCurve;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.exceptions.UnableToComplyException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;


/**
 * Tests the LOF algorithm. 
 * @author lucia
 * 
 */
public class TestLOF extends OutlierTest{
  // the following values depend on the data set used!
  static String dataset = "src/experimentalcode/lucia/datensaetze/hochdimensional.csv";
  static int k = 10;


  @Test
  public void testLOF() throws UnableToComplyException {

    //get database
    Database<DoubleVector> db = getDatabase(dataset);

    //Parameterization
    ListParameterization params = new ListParameterization();
    params.addParameter(LOF.K_ID, k);
    params.addParameter(ComputeROCCurve.POSITIVE_CLASS_NAME_ID, "Noise");

    // run LOF
    OutlierResult result = runLOF(db, params);
    AnnotationResult<Double> scores = result.getScores();

    
    //check Outlier Score of Point 1280
    int id = 1280;
    double score = scores.getValueFor(DBIDUtil.importInteger(id));
//    System.out.println("Outlier Score of the Point with id " + id + ": " + score);
    Assert.assertEquals("Outlier Score of Point with id " + id + " not right.", 1.1945314199156365, score, 0.0001);


    //get ROC AUC
    List<Double> auc = getROCAUC(db, result, params);
    Iterator<Double> iter = auc.listIterator();
    double actual;
    while(iter.hasNext()){
      actual = iter.next();
//      System.out.println("LOF(k="+ k + ") ROC AUC: " + actual);
      Assert.assertEquals("ROC AUC not right.", 0.89216807, actual, 0.00001);
    }
    
  }


  
  private static OutlierResult runLOF(Database<DoubleVector> db, ListParameterization params) {
    // setup algorithm
    LOF<DoubleVector, DoubleDistance> lof = null;
    Class<LOF<DoubleVector, DoubleDistance>> lofcls = ClassGenericsUtil.uglyCastIntoSubclass(LOF.class);
    lof = params.tryInstantiate(lofcls, lofcls);
    params.failOnErrors();

    // run LOF on database
    return lof.run(db);
  }

}
