package de.lmu.ifi.dbs.elki.math.statistics.dependence;

import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.NumberArrayAdapter;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Measure the dependence of two variables.
 * 
 * @author Erich Schubert
 */
public interface DependenceMeasure {
  /**
   * Measure the dependence of two variables.
   * 
   * This is the more flexible API, which allows using different internal data
   * representations.
   * 
   * @param adapter1 First data adapter
   * @param data1 First data set
   * @param adapter2 Second data adapter
   * @param data2 Second data set
   * @param <A> First array type
   * @param <B> Second array type
   * @return Dependence measure
   */
  <A, B> double dependence(NumberArrayAdapter<?, A> adapter1, A data1, NumberArrayAdapter<?, B> adapter2, B data2);

  /**
   * Measure the dependence of two variables.
   * 
   * @param data1 First data set
   * @param data2 Second data set
   * @return Dependence measure
   */
  double dependence(double[] data1, double[] data2);
}
