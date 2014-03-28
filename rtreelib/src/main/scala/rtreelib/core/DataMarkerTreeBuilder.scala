package rtreelib.core

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.PairRDDFunctions
import rtreelib.core._


/**
 * This class is representative for each value of each feature in the data set
 * @param index Index of the feature in the whole data set, based zero
 * @param xValue Value of the current feature
 * @param yValue Value of the Y feature associated (target, predicted feature)
 * @param frequency Frequency of this value
 */
class FeatureValueLabelAggregate(var index: Int, var xValue: Any, var yValue: Double, var yValuePower2: Double, var frequency: Int, var label: BigInt = 1)
    extends Serializable {

    /**
     * Sum two FeatureValueAggregates (sum two yValues and two frequencies)
     */
    def +(that: FeatureValueLabelAggregate) = {
        new FeatureValueLabelAggregate(this.index, this.xValue,
            this.yValue + that.yValue,
            this.yValuePower2 + that.yValuePower2,
            this.frequency + that.frequency,
            this.label)
    }

    override def toString() = 
        "Feature(index:%d | xValue:%f | yValue:%f | frequency:%d | label:%d)".format(
        		index,xValue,yValue,frequency, label
        )
        //"Feature(index:" + index + " | xValue:" + xValue +
        //" | yValue" + yValue + " | frequency:" + frequency + " | label:" + label + ")";
}

/**
 * Build tree based on marking label on data.
 * This approach will try to expand all nodes in the same level in one job
 *
 * @param featureSet feature information of input data
 */
class DataMarkerTreeBuilder(_featureSet: FeatureSet, _usefulFeatureSet : FeatureSet) 
	extends TreeBuilder(_featureSet, _usefulFeatureSet) {

    /**
     * Temporary model file
     */
    val temporaryModelFile = "/tmp/model.temp"

    var regions = List[(BigInt, List[Condition])]()

    /**
     * Process a line of data set
     * For each value of each feature, encapsulate it into a FeatureAgregateInfo(fetureIndex, xValue, yValue, frequency)
     *
     * @param line			array of value of each feature in a "record"
     * @param numbeFeatures	the TOTAL number of feature in data set (include features which may be not processed)
     * @param fTypes		type of each feature in each line (in ordered)
     * @return an array of FeatureAggregateInfo, each element is a value of each feature on this line
     */
    private def convertArrayValuesToObjects(arrayValues: Array[String]): Array[rtreelib.core.FeatureValueLabelAggregate] = {
        var yValue = arrayValues(yIndex).toDouble
        var i = -1
        //Utility.parseDouble(arrayValues(yIndex)) match {
        //    case Some(yValue) => { // check type of Y : if isn't continuous type, return nothing
        arrayValues.map {
            element =>
                {
                    i = (i + 1) % usefulFeatureSet.numberOfFeature
                    if (!this.xIndexes.contains(i)) {
                        var f = encapsulateValueIntoObject(-i - 1, "0", 0, FeatureType.Numerical)
                        f.frequency = -1
                        f
                    } else
                        usefulFeatureSet.data(i).Type match {
                            case FeatureType.Categorical => encapsulateValueIntoObject(i, element, yValue, FeatureType.Categorical)
                            case FeatureType.Numerical => encapsulateValueIntoObject(i, element, yValue, FeatureType.Numerical)
                        }
                }
        }
    }

    def encapsulateValueIntoObject(index: Int, value: String, yValue: Double, featureType: FeatureType.Value): FeatureValueLabelAggregate = {
        featureType match {
            case FeatureType.Categorical => new FeatureValueLabelAggregate(index, value, yValue, yValue * yValue, 1)
            case FeatureType.Numerical => new FeatureValueLabelAggregate(index, value.toDouble, yValue, yValue * yValue, 1)
        }
    }

    /**
     * Check a sub data set has meet stop criterion or not
     *
     * @param data data set
     * @return <code>true</code>/<code>false</code> and the average of value of target feature
     */
    def checkStopCriterion(data: RDD[((BigInt, Int, Any), FeatureValueLabelAggregate)]): Array[(BigInt, Boolean, StatisticalInformation)] = {
        // select only 1 feature of each region
        val firstFeature = data.filter(_._1._2 == this.xIndexes.head).map(x => (x._1._1, x._2)) // (label, feature)

        //yFeature.collect.foreach(println)

        val aggregateFeatures = firstFeature.reduceByKey(_ + _) // sum by label

        val standardDeviations = aggregateFeatures.collect.map(f => {
            val feature = f._2
            val meanY = feature.yValue / feature.frequency
            val meanOfYPower2 = feature.yValuePower2 / feature.frequency
            (f._1, math.sqrt(meanOfYPower2 - meanY * meanY), feature.frequency, feature.yValue, feature.yValuePower2)
            // (label, standardDeviation, numberOfRecords, yValue, yValuePower2)
        })

        // Array[(Label, isStop, meanY, standardDeviation)]	// we use standard deviation is a error metric
        var result = standardDeviations.map(
            label_sd_fre_yValue_yValuePower2 => {
                var (label, standardDeviation, numInstances,sumYValue, sumYValuePower2) = label_sd_fre_yValue_yValuePower2
                var statisticalInformation = new StatisticalInformation(sumYValue, sumYValuePower2, numInstances)
                var meanY = sumYValue/numInstances
                (
                    label,	// label
                    (
                        (numInstances <= this.minsplit) // or the number of records is less than minimum
                        || (((standardDeviation < this.threshold) && (meanY == 0))
                            || (standardDeviation / meanY < this.threshold)) // or standard devariance of values of Y feature is small enough
                     ),
                     statisticalInformation
                )
            })
            
            if (!this.treeModel.tree.isEmpty){

            result.map(x => {

                val (label, isStop, statisticalInfor) = x

                var parent = getNodeByID(label >> 1)
                if (parent != null) {
                    var EY2OfParent: Double = parent.statisticalInformation.sumOfYPower2 / parent.statisticalInformation.numberOfInstances
                    var EYOfParent: Double = parent.statisticalInformation.sumY / parent.statisticalInformation.numberOfInstances
                    var MSEOfParent = (EY2OfParent - EYOfParent * EYOfParent) * parent.statisticalInformation.numberOfInstances.toInt

                    val EY2: Double = statisticalInfor.sumOfYPower2 / statisticalInfor.numberOfInstances.toInt
                    val EY: Double = statisticalInfor.sumY / statisticalInfor.numberOfInstances.toInt
                    val MSE = (EY2 - EY * EY) * statisticalInfor.numberOfInstances.toInt
                    println("label " + label + " current MSE:" + MSE + " parent MSE: " + MSEOfParent + " statisParent:" + parent.statisticalInformation)
                    if ((math.abs(MSE - MSEOfParent) / MSEOfParent) <= this.maximumComplexity) {
                        (label, true, statisticalInfor)
                    } else {
                        x
                    }
                } else x

            })
        } else result

    }

    private def getNodeByID(id: BigInt): Node = {
        if (id != 0){
	        val level = (Math.log(id.toDouble) / Math.log(2)).toInt
	        var i: Int = level -1
	        var TWO: BigInt = 2
	        var parent = treeModel.tree; // start adding from root node
	        try{
	        while (i > 0) {
	
	            if ((id / (TWO << i - 1)) % 2 == 0) {
	                // go to the left
	                parent = parent.left
	            } else {
	                // go go the right
	                parent = parent.right
	            }
	            i -= 1
	        } // end while
	        }catch {case e : Throwable => { 
	            e.printStackTrace()
	            println("currentID:" + id)
	            println("currentTree:\n" + treeModel.tree)
	            throw e
	        }}
	
	        parent
        }
        else{
            null
        }
    } 
    private def updateModel(info: Array[(BigInt, SplitPoint, StatisticalInformation)], isStopNode: Boolean = false) = {
        info.foreach(stoppedRegion =>
            {

                var (label,splitPoint, statisticalInformation) = stoppedRegion
                
                println("update model with label=%d splitPoint:%s".format(
                    label,
                    splitPoint))

                var newnode = (
                    if (isStopNode) {
                        new LeafNode(splitPoint.point.toString)
                    } else {
                        val chosenFeatureInfoCandidate = usefulFeatureSet.data.find(f => f.index == splitPoint.index)
                        chosenFeatureInfoCandidate match {
                            case Some(chosenFeatureInfo) => {
                                new NonLeafNode(chosenFeatureInfo,
                                    splitPoint,
                                    new LeafNode("empty.left"),
                                    new LeafNode("empty.right"));
                            }
                            case None => { new LeafNode(this.ERROR_SPLITPOINT_VALUE) }
                        }
                    }) // end of assign value for new node

                if (newnode.value == this.ERROR_SPLITPOINT_VALUE) {
                    println("Value of job id=" + label + " is invalid")
                } else {
                	val meanY : Double = 
                	    if (statisticalInformation.numberOfInstances == 0) 
                	        0
                	    else
                	        statisticalInformation.sumY/statisticalInformation.numberOfInstances.toInt
                	        
                    newnode.value = meanY
                    newnode.statisticalInformation = statisticalInformation
                    
                    // If tree has zero node, create a root node
                    if (treeModel.tree.isEmpty) {
                        treeModel.tree = newnode;

                    } else //  add new node to current model
                    {

                        val level = (Math.log(label.toDouble) / Math.log(2)).toInt
                        var i: Int = level - 1
                        var TWO : BigInt = 2
                        var parent = treeModel.tree; // start adding from root node
                        while (i > 0) {

                            if ((label / (TWO << i - 1)) % 2 == 0) {
                                // go to the left
                                parent = parent.left
                            } else {
                                // go go the right
                                parent = parent.right
                            }
                            i -= 1
                        } // end while

                        if (label % 2 == 0) {
                            parent.setLeft(newnode)
                        } else {
                            parent.setRight(newnode)
                        }
                    }
                }
            })
    }


    /**
     * Building tree, bases on:
     *
     * @parm yFeature 	predicted feature
     * @param xFeature	input features
     *
     * @return: <code>TreeModel</code> : root of the tree
     */
    override def startBuildTree(trainingData: RDD[String]) = {

        var rootID = 1
        
        var expandingNodeIndexes = Set[BigInt]()
        
        var map_label_to_splitpoint = Map[BigInt, SplitPoint]()

        def finish() = {
            expandingNodeIndexes.isEmpty
            //map_label_to_splitpoint.isEmpty
        }

        // parse raw data
        val mydata = trainingData.map(line => line.split(delimiter))

        /* REGION TRANSFORMING */

        // encapsulate each value of each feature in each line into a object
        var transformedData = mydata.map(
            arrayValues => {
                convertArrayValuesToObjects(arrayValues)
            })

        // filter the 'line' which contains the invalid or missing data
        transformedData = transformedData.filter(x => (x.length > 0))

        /* END OF REGION TRANSFORMING */

        // set label for the first job
        // already set by default constructor of class FeatureValueLabelAggregate , so we don't need to put data to regions
        // if this function is called by ContinueFromIncompleteModel, mark the data by the last labels
        transformedData = markDataByLabel(transformedData, regions)

        // NOTE: label == x, means, this is data used for building node id=x

        //var map_label_to_splitpoint = Map[BigInt, SplitPoint]()
        var isError = false;
        var errorStack : String = ""

        var iter = 0;


        do {
            iter = iter + 1
            
            try {
                //if (iter == 5)
                //    throw new Exception("Break for debugging")

                println("\n\n\nITERATION---------------------%d------------- expands from %d node\n\n".format(iter, expandingNodeIndexes.count(p => true)))

                
                // save current model before growing tree
                this.treeModel.writeToFile(this.temporaryModelFile)
                
                var data = transformedData.flatMap(x => x.toSeq).filter(x => (x.index >= 0))
                
                var featureValueAggregate = data.map(x => ((x.label, x.index, x.xValue), x)).reduceByKey((x, y) => x + y)
                
                var checkedStopExpanding = checkStopCriterion(featureValueAggregate)
                // we get Array[(label, isStop, statisticalInformation)]
                println("Checked stop expanding:\n%s".format(checkedStopExpanding.mkString("\n")))
                
                
                // if the tree height enough, mark all node is stop node
                if (iter > this.maxDepth)
                    checkedStopExpanding = checkedStopExpanding.map(x => (x._1, true, x._3))
                    
                    
                // select stopped group
                val stopExpandingGroups = checkedStopExpanding.filter(v => v._2).
                    map(x => (x._1, new SplitPoint(-1, x._3.sumY/x._3.numberOfInstances, 0), x._3))	// (label, splitpoint, statisticalInformation)

                // become: Array[(BigInt, SplitPoint)] == Array[(label, SplitPoint)]

                // update model with in-expandable group
                updateModel(stopExpandingGroups, true)

                // select indexes/labels of expanding groups
                val continueExpandingGroups = checkedStopExpanding.filter(v => !v._2)
                val expandingLabels = continueExpandingGroups.map(x => x._1).toSet
                var mapLabel_To_CheckStopResult_Of_ExpandingNodes = Map[BigInt, StatisticalInformation]()
                continueExpandingGroups.foreach{ case (label, isStop, statisticalInfo) => {
                    mapLabel_To_CheckStopResult_Of_ExpandingNodes = 
                        mapLabel_To_CheckStopResult_Of_ExpandingNodes.+(label -> statisticalInfo)
                }}
                
                featureValueAggregate = featureValueAggregate.filter(f => expandingLabels.contains(f._1._1))
                
                val sortedFeatureValueAggregates = (
                    featureValueAggregate.map(x => ((x._1._1, x._1._2), x._2)) // ((label,index), feature)
                    .groupByKey()
                    .map (x =>
                        (x._1, x._2.sortBy(
                            v => v.xValue match {
                                case d: Double => d // sort by xValue if this is numerical feature
                                case s: String => v.yValue / v.frequency // sort by the average of Y if this is categorical value
                            }))))

                val splittingPointFeatureOfEachRegion =
                    (sortedFeatureValueAggregates.map(x => {
                        val index = x._1._2
                        val region = x._1._1
                        this.usefulFeatureSet.data(index).Type match {
                            case FeatureType.Numerical => {
                                (region, findBestSplitPointForNumericalFeature(region, index, x._2))
                            }

                            case FeatureType.Categorical => {
                                (region, findBestSplitPointForCategoricalFeature(region, index, x._2))
                            }
                        }
                    }) // find best split point of all features
                        .groupBy(_._1) // group by region
                        .collect
                        .map(f => f._2.maxBy(region_sp => region_sp._2.weight))
                    )

                
                // process split points
                val validSplitPoint = splittingPointFeatureOfEachRegion.filter(_._2.index != -9)	// (label, splitpoint)

                // select split point of region with has only one feature --> it is a leaf node
                val stoppedSplitPoints = validSplitPoint.filter(_._2.index == -1).
                	map(x => {
                	    val checkStopResult = mapLabel_To_CheckStopResult_Of_ExpandingNodes.getOrElse(x._1, new StatisticalInformation())
                	    (x._1, x._2, checkStopResult)
                	})

                val nonstoppedSplitPoints = validSplitPoint.filter(_._2.index != -1).
                	map(x => {
                	    val checkStopResult = mapLabel_To_CheckStopResult_Of_ExpandingNodes.getOrElse(x._1, new StatisticalInformation())
                	    (x._1, x._2, checkStopResult)
                	})

                updateModel(stoppedSplitPoints, true)
                updateModel(nonstoppedSplitPoints, false)

                
                expandingNodeIndexes = Set[BigInt]()
                if (iter >= 2)
                	map_label_to_splitpoint = map_label_to_splitpoint.filter(p => p._1 > (1 << iter))
                
                nonstoppedSplitPoints.foreach(point =>
                    // add expanding Indexes into set
                    {
                        expandingNodeIndexes = expandingNodeIndexes + (point._1)
                        map_label_to_splitpoint = map_label_to_splitpoint + (point._1 -> point._2) // label -> splitpoint
                    })

                //println("expandingNodeIndexes:" + expandingNodeIndexes)
                //println("map_label_to_splitpoint:%s\n\n".format(map_label_to_splitpoint))
                
                // mark new label for expanding data
                transformedData = updateLabels(transformedData, map_label_to_splitpoint)
                
            } catch {
                case e: Exception => {
                    isError = true;
                    errorStack = e.getStackTraceString
                    expandingNodeIndexes = Set[BigInt]()
                }
            }
        } while (!finish)

        treeModel.isComplete = !isError;

        /* FINALIZE THE ALGORITHM */
        if (!isError) {
            this.treeModel.isComplete = true
            println("\n------------------DONE WITHOUT ERROR------------------\n")
        } else {
            this.treeModel.isComplete = false
            println("\n--------FINISH with some failed jobs at iteration " + iter + " ----------\n")
            println("Error Message: \n%s\n".format(errorStack))
            println("Temporaty Tree model is stored at " + this.temporaryModelFile + "\n")
        }
    }

    private def updateLabels(data : RDD[Array[FeatureValueLabelAggregate]],
            map_label_to_splitpoint: Map[BigInt, SplitPoint]) 
    = {
        data.map(array => {

                    val currentLabel = array(0).label
                    		
                    val splitPoint = map_label_to_splitpoint.getOrElse(currentLabel, new SplitPoint(-9, 0, 0))
   
                    if (splitPoint.index < 0) { // this is stop node
                        //println("split point index:" + splitPoint.index)
                        array.foreach(element => { element.index = -9 })
                    } else { // this is expanding node => change label of its data
                        splitPoint.point match {
                            // split on numerical feature
                            case d: Double =>
                                {
                                    if (array(splitPoint.index).xValue.asInstanceOf[Double] < splitPoint.point.asInstanceOf[Double]) {
                                        array.foreach(element => element.label = (element.label << 1))
                                    } else {
                                        array.foreach(element => element.label = (element.label << 1 ) +  1)
                                    }
                                }

                            // split on categorical feature    
                            case s: Set[String] =>
                                {
                                    if (splitPoint.point.asInstanceOf[Set[String]].contains(array(splitPoint.index).xValue.asInstanceOf[String])) {
                                        array.foreach(element => element.label = (element.label << 1))
                                    } else {
                                        array.foreach(element => element.label = (element.label << 1 ) + 1)
                                    }
                                }
                        }
                    }
                    array
                })
    }
    
    private def findBestSplitPointForNumericalFeature(label: BigInt, index: Int, allValues: Seq[FeatureValueLabelAggregate]): rtreelib.core.SplitPoint = {
        var acc: Int = 0 // number of records on the left of the current element
        var currentSumY: Double = 0
        //val numRecs: Int = x._2.foldLeft(0)(_ + _.frequency)
        //val sumY = x._2.foldLeft(0.0)(_ + _.yValue)
        var temp = allValues.reduce((f1, f2) => f1 + f2)
        val numRecs = temp.frequency
        val sumY = temp.yValue

        var posibleSplitPoint: Double = 0
        var lastFeatureValue = new FeatureValueLabelAggregate(-1, 0, 0, 0, 0, label)

        var bestSplitPoint = new SplitPoint(index, posibleSplitPoint, 0)
        var maxWeight = Double.MinValue
        var currentWeight: Double = 0

        if (allValues.length == 1) {
            new SplitPoint(-1, 0.0, 0.0) // sign of stop node
        } else {
            allValues.foreach(f => {

                if (lastFeatureValue.index == -1) {
                    lastFeatureValue = f
                } else {
                    posibleSplitPoint = (f.xValue.asInstanceOf[Double] + lastFeatureValue.xValue.asInstanceOf[Double]) / 2;
                    currentSumY = currentSumY + lastFeatureValue.yValue
                    acc = acc + lastFeatureValue.frequency
                    currentWeight = currentSumY * currentSumY / acc + (sumY - currentSumY) * (sumY - currentSumY) / (numRecs - acc)
                    lastFeatureValue = f
                    if (currentWeight > maxWeight) {
                        bestSplitPoint.point = posibleSplitPoint
                        bestSplitPoint.weight = currentWeight
                        maxWeight = currentWeight
                    }
                }
            })
            bestSplitPoint
        }
    }

    private def findBestSplitPointForCategoricalFeature(label: BigInt, index: Int, allValues: Seq[FeatureValueLabelAggregate]): rtreelib.core.SplitPoint = {
        if (allValues.length == 1) {
            new SplitPoint(-1, 0.0, 0.0) // sign of stop node
        } else {

            var currentSumY: Double = 0 // current sum of Y of elements on the left of current feature
            var temp = allValues.reduce((f1, f2) => f1 + f2)
            val numRecs = temp.frequency
            val sumY = temp.yValue
            var splitPointIndex: Int = 0
            var lastFeatureValue = new FeatureValueLabelAggregate(-1, 0, 0, 0, 0, label)
            var acc: Int = 0
            var bestSplitPoint = new SplitPoint(index, splitPointIndex, 0)
            var maxWeight = Double.MinValue
            var currentWeight: Double = 0

            allValues.foreach(f => {

                if (lastFeatureValue.index == -1) {
                    lastFeatureValue = f
                } else {
                    currentSumY = currentSumY + lastFeatureValue.yValue
                    splitPointIndex = splitPointIndex + 1
                    acc = acc + lastFeatureValue.frequency
                    currentWeight = currentSumY * currentSumY / acc + (sumY - currentSumY) * (sumY - currentSumY) / (numRecs - acc)
                    lastFeatureValue = f
                    if (currentWeight > maxWeight) {
                        bestSplitPoint.point = splitPointIndex
                        bestSplitPoint.weight = currentWeight
                        maxWeight = currentWeight
                    }
                }
            })

            var splitPointValue = allValues.map(f => f.xValue).take(splitPointIndex).toSet
            bestSplitPoint.point = splitPointValue
            bestSplitPoint
        }
    }

    /**
     * Recover, repair and continue build tree from the last state
     *
     * @throw Exception if the tree is never built before
     */
    override def continueFromIncompleteModel(trainingData: RDD[String]) = {
        if (treeModel == null) {
            throw new Exception("The tree model is empty because of no building. Please build it first")
        }

        if (treeModel.isComplete) {
            println("This model is already complete")
        } else {
            println("Recover from the last state")
            /* INITIALIZE */
            this.featureSet = treeModel.featureSet
            this.usefulFeatureSet = treeModel.usefulFeatureSet
            this.xIndexes = treeModel.xIndexes
            this.yIndex = treeModel.yIndex

            startBuildTree(trainingData)

        }
    }

    private def markDataByLabel(data: RDD[Array[FeatureValueLabelAggregate]], regions: List[(BigInt, List[Condition])]): RDD[Array[FeatureValueLabelAggregate]] = {
        var newdata =
            if (regions.length > 0) {
                data.map(line => {
                    var labeled = false

                    // if a line can match one of the Conditions of a region, label it by the ID of this region
                    regions.foreach(region => {
                        if (region._2.forall(c => c.check(line(c.splitPoint.index).xValue))) {
                            line.foreach(element => element.label = region._1)
                            labeled = true
                        }
                    })

                    // if this line wasn't marked, it means this line isn't used for building tree
                    if (!labeled) line.foreach(element => element.index = -9)
                    line
                })
            } else data

        newdata
    }

    /**
     * Init the last labels from the leaf nodes
     */
    private def initTheLastLabelsFromLeafNodes() = {

        var jobIDList = List[(BigInt, List[Condition])]()

        def generateJobIter(currentNode: Node, id: BigInt, conditions: List[Condition]): Unit = {

            if (currentNode.isEmpty &&
                (currentNode.value == "empty.left" || currentNode.value == "empty.right")) {
                jobIDList = jobIDList :+ (id, conditions)
            }

            if (!currentNode.isEmpty) { // it has 2 children
                var newConditionsLeft = conditions :+
                    new Condition(new SplitPoint(currentNode.feature.index, currentNode.splitpoint, 0), true)
                generateJobIter(currentNode.left, id * 2, newConditionsLeft)

                var newConditionsRight = conditions :+
                    new Condition(new SplitPoint(currentNode.feature.index, currentNode.splitpoint, 0), false)
                generateJobIter(currentNode.right, id * 2 + 1, newConditionsRight)
            }
        }

        generateJobIter(treeModel.tree, 1, List[Condition]())

        jobIDList.sortBy(-_._1) // sort jobs by ID descending

        var highestLabel = Math.log(jobIDList(0)._1.toDouble) / Math.log(2)
        jobIDList.filter(x => Math.log(x._1.toDouble) / Math.log(2) == highestLabel)

        regions = jobIDList

    }

    override def createNewInstance(featureSet: FeatureSet, usefulFeatureSet: FeatureSet) : TreeBuilder = {
    	var tb : TreeBuilder = new DataMarkerTreeBuilder(featureSet, usefulFeatureSet)
    	tb.setMinSplit(this.minsplit)
    	tb.setMaxDepth( this.maxDepth)
    	tb.setDelimiter(this.delimiter)
    	tb.setMaximumComplexity(this.maximumComplexity)
    	tb
    }
}
