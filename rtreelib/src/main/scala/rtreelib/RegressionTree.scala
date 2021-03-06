package rtreelib

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import scala.util.Random
import scala.util.Marshal
import scala.io.Source
import java.io._
import scala.actors.remote.JavaSerializer
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.DataInputStream
import java.io.FileInputStream

import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.KryoRegistrator

/**
 * We will use this class to improve de-serialize speed in future, but not now !!!
 */
class MyRegistrator extends KryoRegistrator {
    override def registerClasses(kryo: Kryo) {
        kryo.register(classOf[RegressionTree])
        kryo.register(classOf[FeatureValueAggregate])
        kryo.register(classOf[SplitPoint])
        kryo.register(classOf[FeatureSet])
    }
}

/**
 * This class is representative for each value of each feature in the data set
 * @index: index of the feature in the whole data set, based zero
 * @xValue: value of the current feature
 * @yValue: value of the Y feature associated (target, predicted feature)
 * @frequency : frequency of this value
 */
class FeatureValueAggregate(val index: Int, var xValue: Any, var yValue: Double, var frequency: Int) extends Serializable {
    def addFrequency(acc: Int): FeatureValueAggregate = { FeatureValueAggregate.this.frequency = FeatureValueAggregate.this.frequency + acc; FeatureValueAggregate.this }
    def +(that: FeatureValueAggregate) = {
        FeatureValueAggregate.this.frequency = FeatureValueAggregate.this.frequency + that.frequency
        FeatureValueAggregate.this.yValue = FeatureValueAggregate.this.yValue + that.yValue
        FeatureValueAggregate.this
    }
    override def toString() = "Feature(index:" + index + " | xValue:" + xValue +
        " | yValue" + yValue + " | frequency:" + frequency + ")";
}

// index : index of the feature
// point: the split point of this feature (it can be a Set, or a Double)
// weight: the weight we get if we apply this splitting
class SplitPoint(val index: Int, val point: Any, val weight: Double) extends Serializable {
    override def toString = index.toString + "," + point.toString + "," + weight.toString // for debugging
}

class RegressionTree(metadata: Array[String]) extends Serializable {
      
    // delimiter of fields in data set
    // pm: this is very generic. You could instead assume your input data is always
    // a CSV file. If it is not, then you have a pre-proccessing job to make it so
    var delimiter = ','
    
    // set of feature in dataset
    // pm: this is very generic, and indeed it depends on the input data
    // however, in "production", you wouldn't do this, as you specialize for a particular kind of data
    // this variable is a part of instance of RegessionTree class, which will be dispatched to 
    // every worker also. So, should we broadcast something always transfered to the workers ?
    var featureSet = new FeatureSet(metadata)
    
    // coefficient of variation
    var threshold : Double = 0.1

    // Default index of Y feature
    var yIndex = featureSet.numberOfFeature - 1	// = number_of_feature - 1
    
    // Default indices/indexes of X feature
    // this variable can be infered from featureSet and yIndex
    // but because it will be used in functions processLine, and buidingTree
    // so we don't want to calculate it multiple time
    var xIndexs = featureSet.data.map(x => x.index).filter(x => (x != yIndex)).toSet[Int]

    // Tree model
    private var tree: Node = new Empty("Nil")

    // Minimum records to do a splitting
    var minsplit = 10

    // user partitioner or not
    //var usePartitioner = true
    //val partitioner = new HashPartitioner(contextBroadcast.value.defaultParallelism)

    def setDelimiter(c: Char) = { delimiter = c }

    /**
     * Set the minimum records of splitting
     * It's mean if a node have the number of records <= minsplit, it can't be splitted anymore
     * @xMinSplit: new minimum records for splitting
     */
    def setMinSplit(xMinSlit: Int) = { this.minsplit = xMinSlit }

    /**
     * Set threshold for stopping criterion. This threshold is coefficient of variation
     * A node will stop expand if Dev(Y)/E(Y) < threshold
     * In which:
     * Dev(Y) is standard deviation
     * E(Y) is medium of Y
     * @xThreshold: new threshold
     */
    def setThreshold(xThreshlod: Double) = { threshold = xThreshlod }

    //def setUsingPartitioner(value: Boolean) = { usePartitioner = value }

    /**
     * Process a line of data set
     * For each value of each feature, encapsulate it into a FeatureAgregateInfo(fetureIndex, xValue, yValue, frequency)
     * @line: array of value of each feature in a "record"
     * @numbeFeatures: the TOTAL number of feature in data set (include features which may be not processed)
     * @fTypes: type of each feature in each line (in ordered)
     * @return: an array of FeatureAggregateInfo, each element is a value of each feature on this line
     */
    private def processLine(line: Array[String], numberFeatures: Int, featureSet : FeatureSet): Array[FeatureValueAggregate] = {
        val length = numberFeatures
        var i = -1;

        parseDouble(line(yIndex)) match {
            case Some(yValue) => { // check type of Y : if isn't continuous type, return nothing
                line.map(f => { // this map is not parallel, it is executed by each worker on their part of the input RDD
                    i = (i + 1) % length
                    if (xIndexs.contains(i)) {
                        featureSet.data(i) match {
                            case nFeature : NumericalFeature => { // If this is a numerical feature => parse value from string to double
                                val v = parseDouble(f);
                                v match {
                                    case Some(d) => new FeatureValueAggregate(i, d, yValue, 1)
                                    case None => new FeatureValueAggregate(-1, f, 0, 0)
                                }
                            }
                            // if this is a categorical feature => return a FeatureAggregateInfo
                            case cFeature : CategoricalFeature => new FeatureValueAggregate(i, f, yValue, 1)
                        } // end match fType(i)
                    } // end if
                    else new FeatureValueAggregate(-1, f, 0, 0)
                }) // end map
            } // end case Some(yvalue)
            case None => { println("Y value is invalid:(%s)".format(line(yIndex))); Array[FeatureValueAggregate]() }
        } // end match Y value
    }

    /**
     * Check a sub data set has meet stop criterion or not
     * @data: data set
     * @return: true/false and average of Y
     */
    def checkStopCriterion(data: RDD[FeatureValueAggregate]): (Boolean, Double, Int) = { //PM: since it operates on RDD it is parallel
        val yFeature = data.filter(x => x.index == yIndex)

        //yFeature.collect.foreach(println)
        
        val numTotalRecs = yFeature.reduce(_ + _).frequency
        
        
        val yValues = yFeature.groupBy(_.yValue)

        val yAggregate = yFeature.map(x => (x.yValue, x.yValue * x.yValue))

        val ySumValue = yAggregate.reduce((x, y) => (x._1 + y._1, x._2 + y._2))
        
        
        
        val EY = ySumValue._1 / numTotalRecs
        val EY2 = ySumValue._2 / numTotalRecs

        val standardDeviation = math.sqrt(EY2 - EY * EY)

        (		(	// the first component of tuple
                (numTotalRecs <= this.minsplit) // or the number of records is less than minimum
                || (((standardDeviation < this.threshold) && (EY == 0)) || (standardDeviation / EY < threshold)) // or standard devariance of values of Y feature is small enough
                ),
                EY	// the second component of tuple
                , numTotalRecs
        )

    }

    /**
     * Building tree bases on:
     * @yFeature: predicted feature
     * @xFeature: input features
     * @return: root of tree
     */
    def buildTree(trainingData: RDD[String], yFeature: String = featureSet.data(yIndex).Name, xFeatures: Set[String] = Set[String]()): Node = {
        // parse raw data
    	val mydata = trainingData.map(line => line.split(delimiter))
    
        //def buildIter(rawdata: RDD[Array[FeatureAggregateInfo]]): Node = {
        def buildIter(rawdata: RDD[Array[FeatureValueAggregate]]): Node = {

            var data = rawdata.flatMap(x => x.toSeq)
            
            val (stopExpand, eY, numRecs) = checkStopCriterion(data)
            if (stopExpand) {
                new Empty(eY.toString)
            } else {

                val groupFeatureByIndexAndValue = 
                    data.groupBy(x => (x.index, x.xValue)) // PM: this operates on an RDD => in parallel

                var featureValueSorted = (
                    //data.groupBy(x => (x.index, x.xValue))
                    groupFeatureByIndexAndValue // PM: this is an RDD hence you do the map and fold in parallel (in MapReduce this would be the "reducer")
                    .map(x => (new FeatureValueAggregate(x._1._1, x._1._2, 0, 0)
                        + x._2.foldLeft(new FeatureValueAggregate(x._1._1, x._1._2, 0, 0))(_ + _)))
                    // sample results
                    //Feature(index:2 | xValue:normal | yValue6.0 | frequency:7)
                    //Feature(index:1 | xValue:sunny | yValue2.0 | frequency:5)
                    //Feature(index:2 | xValue:high | yValue3.0 | frequency:7)

                    .groupBy(x => x.index) // This is again operating on the RDD, and actually is like the continuation of the "reducer" code above
                    .map(x =>
                        (x._1, x._2.toSeq.sortBy(
                            v => v.xValue match {
                                case d: Double => d // sort by xValue if this is numerical feature
                                case s: String => v.yValue / v.frequency // sort by the average of Y if this is categorical value
                            }))))

                var splittingPointFeature = featureValueSorted.map(x => // operates on an RDD, so this is in parallel
                    x._2(0).xValue match {
                        case s: String => // process with categorical feature
                            {
                                var acc: Int = 0; // the number records on the left of current feature
                                var currentSumY: Double = 0 // current sum of Y of elements on the left of current feature
                                val numRecs: Int = x._2.foldLeft(0)(_ + _.frequency) // number of records
                                val sumY = x._2.foldLeft(0.0)(_ + _.yValue) // total sum of Y

                                var splitPoint: Set[String] = Set[String]()
                                var lastFeatureValue = new FeatureValueAggregate(-1, 0, 0, 0)
                                try {
                                    x._2.map(f => {

                                        if (lastFeatureValue.index == -1) {
                                            lastFeatureValue = f
                                            new SplitPoint(x._1, Set(), 0.0)
                                        } else {
                                            currentSumY = currentSumY + lastFeatureValue.yValue
                                            splitPoint = splitPoint + lastFeatureValue.xValue.asInstanceOf[String]
                                            acc = acc + lastFeatureValue.frequency
                                            val weight = currentSumY * currentSumY / acc + (sumY - currentSumY) * (sumY - currentSumY) / (numRecs - acc)
                                            lastFeatureValue = f
                                            new SplitPoint(x._1, splitPoint, weight)
                                        }
                                    }).drop(1).maxBy(_.weight) // select the best split // PM: please explain this trick with an example
                                    // we drop 1 element because with Set{A,B,C} , the best split point only be {A} or {A,B}
                                } catch {
                                    case e: UnsupportedOperationException => new SplitPoint(-1, 0.0, 0.0)
                                }
                            }
                        case d: Double => // process with numerical feature
                            {
                                var acc: Int = 0 // number of records on the left of the current element
                                val numRecs: Int = x._2.foldLeft(0)(_ + _.frequency)
                                var currentSumY: Double = 0
                                val sumY = x._2.foldLeft(0.0)(_ + _.yValue)
                                var posibleSplitPoint: Double = 0
                                var lastFeatureValue = new FeatureValueAggregate(-1, 0, 0, 0)
                                try {
                                    x._2.map(f => {

                                        if (lastFeatureValue.index == -1) {
                                            lastFeatureValue = f
                                            new SplitPoint(x._1, 0.0, 0.0)
                                        } else {
                                            posibleSplitPoint = (f.xValue.asInstanceOf[Double] + lastFeatureValue.xValue.asInstanceOf[Double]) / 2;
                                            currentSumY = currentSumY + lastFeatureValue.yValue
                                            acc = acc + lastFeatureValue.frequency
                                            val weight = currentSumY * currentSumY / acc + (sumY - currentSumY) * (sumY - currentSumY) / (numRecs - acc)
                                            lastFeatureValue = f
                                            new SplitPoint(x._1, posibleSplitPoint, weight)
                                        }
                                    }).drop(1).maxBy(_.weight) // select the best split
                                } catch {
                                    case e: UnsupportedOperationException => new SplitPoint(-1, 0.0, 0.0)
                                }
                            } // end of matching double
                    } // end of matching xValue
                    ).
                    filter(_.index != yIndex).collect.
                    maxBy(_.weight) // select best feature to split
                    // PM: collect here means you're sending back all the data to a single machine (the driver).

                if (splittingPointFeature.index == -1) { // the chosen feature has only one value
                    //val commonValueY = yFeature.reduce((x, y) => if (x._2.length > y._2.length) x else y)._1
                    //new Empty(commonValueY.toString)
                    new Empty(eY.toString)
                } else {
                    val chosenFeatureInfo = featureSet.data.filter(f => f.index == splittingPointFeature.index).first

                    splittingPointFeature.point match {
                        case s: Set[String] => { // split on categorical feature
                            // please check that you're caching rawdata, otherwise you're reading it back from disk
                            val left = rawdata.filter(x => s.contains(x(chosenFeatureInfo.index).xValue.asInstanceOf[String]))
                            val right = rawdata.filter(x => !s.contains(x(chosenFeatureInfo.index).xValue.asInstanceOf[String]))
                            new NonEmpty(
                                chosenFeatureInfo, // featureInfo
                                s, // left + right conditions
                                buildIter(left), // left
                                buildIter(right) // right
                                // PM: this is the most important idea of Trung's algorithm. Essentially, you're "streaming" two new RDDs to a new iteration
                                )
                        }
                        case d: Double => { // split on numerical feature
                            val left = rawdata.filter(x => (x(chosenFeatureInfo.index).xValue.asInstanceOf[Double] < d))
                            val right = rawdata.filter(x => (x(chosenFeatureInfo.index).xValue.asInstanceOf[Double] >= d))
                            new NonEmpty(
                                chosenFeatureInfo, // featureInfo
                                d, // left + right conditions
                                buildIter(left), // left
                                buildIter(right) // right
                                )
                        }
                    } // end of matching
                } // end of if index == -1
            }
        }

        var fYindex = featureSet.data.findIndexOf(p => p.Name == yFeature)

        // PM: You're sending from the "driver" to all workers the index of the Y feature, the one you're trying to predict
        if (fYindex >= 0) yIndex = featureSet.data(fYindex).index

        xIndexs =
            if (xFeatures.isEmpty) // if user didn't specify xFeature, we will process on all feature, include Y feature (to check stop criterion)
                featureSet.data.map(x => x.index).toSet[Int]
            else xFeatures.map(x => featureSet.getIndex(x)) + yIndex

        val new_data = mydata.map(x =>  processLine(x, featureSet.numberOfFeature, featureSet))
        // problem with cache --> change RDD
        
        tree = buildIter(new_data)
                    
        tree
    }

    /**
     * Parse a string to double
     */
    private def parseDouble(s: String) = try { Some(s.toDouble) } catch { case _ => None }

    /**
     * Predict Y base on input features
     * @record: an array, which its each element is a value of each input feature
     */
    def predict(record: Array[String]): String = {
        def predictIter(root: Node): String = {
            if (root.isEmpty) root.value.toString
            else root.condition match {
                case s: Set[String] => {
                    if (s.contains(record(root.feature.index))) predictIter(root.left)
                    else predictIter(root.right)
                }
                case d: Double => {
                    if (record(root.feature.index).toDouble < d) predictIter(root.left)
                    else predictIter(root.right)
                }
            }

        }
        if (tree.isEmpty) "Please build tree first"
        else predictIter(tree)
    }

    
    /**
     * Evaluate the accuracy of regression tree
     * @input: an input record (uses the same delimiter with trained data set)
     */
    def evaluate(input: RDD[String], delimiter : Char = ',') {
        if (!tree.isEmpty){
            val numTest = input.count
            val inputdata = input.map(x => x.split(delimiter))
            val diff = inputdata.map(x => (predict(x).toDouble, x(yIndex).toDouble)).map(x => (x._2 - x._1, (x._2 - x._1)*(x._2-x._1)))

            val sums = diff.reduce((x,y) => (x._1 + y._1, x._2 + y._2))

            val meanDiff = sums._1/numTest
            val meanDiffPower2 = sums._2/numTest
            val deviation = math.sqrt(meanDiffPower2 - meanDiff*meanDiff)
            val SE = deviation/numTest
            
            println("Mean of different:%f\nDeviation of different:%f\nSE of different:%f".format(meanDiff, deviation, SE) )
        }else {
            "Please build tree first"
        }
    }
    
    def writeTreeToFile(path: String) = {
        
        val js = new JavaSerializer(null, null)
        val os = new DataOutputStream(new FileOutputStream(path))
        
        //js.writeObject(os, featureSet.data)
        //js.writeObject(os, tree)
        //js.writeObject(os, yIndex.value : Integer)
        js.writeObject(os, this)
        os.close
    }
    /*
    def loadTreeFromFile(path: String) = {
       
        val js = new JavaSerializer(null, null)
    	val is = new DataInputStream(new FileInputStream(path))

        val fSet = new FeatureSet(contextBroadcast.value.makeRDD(List[String]()))
        fSet.rawData = js.readObject(is).asInstanceOf[List[FeatureInfo]]
        this.featureSet = contextBroadcast.value.broadcast(fSet)
        println("FeatureSet after reading:" + featureSet.value);
        
        tree = js.readObject(is).asInstanceOf[Node]
        
        
        yIndex = contextBroadcast.value.broadcast(js.readObject(is).asInstanceOf[Int])
        xIndexs = contextBroadcast.value.broadcast((0 until this.featureSet.value.data.length).filter(yIndex.value !=).toSet)

        is.close
    }
     */
}

object RegressionTree extends Serializable {
    
    def apply(metadata: Array[String]) = 
        new RegressionTree(metadata)
    def loadTreeFromFile(path: String) = {
        val js = new JavaSerializer(null, null)
    	val is = new DataInputStream(new FileInputStream(path))
        val rt = js.readObject(is).asInstanceOf[RegressionTree]
        rt
    }
}