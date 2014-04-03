package rtreelib.core
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import rtreelib.core._
import rtreelib.evaluation.Evaluation

object Pruning {
    private val DEBUG : Boolean = false
    
    var maxcp = 0.0
    
    private def risk(node : Node) : Double = {
        val EX = node.statisticalInformation.sumY / node.statisticalInformation.numberOfInstances
        val EX2 = node.statisticalInformation.sumOfYPower2 / node.statisticalInformation.numberOfInstances 
        //math.sqrt(EX2 - EX*EX)*math.sqrt(node.statisticalInformation.numberOfInstances)///node.statisticalInformation.numberOfInstances 	// MSE
        (EX2 - EX*EX)*(node.statisticalInformation.numberOfInstances)
    }
    
    /**
     * Prune the full tree to get tree T1 which has the same error rate but smaller size
     */
    private def firstPruneTree(root: Node): Set[BigInt] = {
        
        var leafNodes = Set[BigInt]()
        def isLeaf(node : Node, id : BigInt) = {
            node.isEmpty || leafNodes.contains(id)
        }
        
        def firstPruneTreeIter(node: Node, currentId : BigInt) : Unit = {
            
            if (!node.isEmpty){
                firstPruneTreeIter(node.left, currentId << 1)
                firstPruneTreeIter(node.right, (currentId << 1) + 1)
                if (isLeaf(node.left, currentId << 1) 
                        && isLeaf(node.right, (currentId << 1) + 1)) {
                    val Rl = risk(node.left)
	                val Rr = risk(node.right)
	                val R = risk(node)
	                if (R == (Rl + Rr))
	                    leafNodes = leafNodes + currentId
                }
            }
        }
            
        firstPruneTreeIter(root, 1)
        leafNodes
    }
    
   /**
     * Calculate g(t) of every node
     *
     * @return (id_of_node_will_be_pruned, alpha)
     */
    def selectNodesToPrune(root : Node, already_pruned_node : Set[BigInt]) : (Set[BigInt], Double) = {	// (prunedIDs, alpha)
        var result = Set[BigInt]()
        var currentMin : Double = Double.MaxValue
        var currentID : BigInt = 0

        def selectNodeToPrunIter(node: Node, id: BigInt): (Double, Int) = { // (riskOfBranch, numberChildren)
            //println("currentID:" + id + " info:" + node.statisticalInformation)
            // if this is an internal node, and the branch of this node hasn't pruned
            if (!node.isEmpty && !already_pruned_node.contains(id)) {

                var (riskBranchLeft, numLeft) = selectNodeToPrunIter(node.left, (id << 1) )
                var (riskBranchRight, numRight) = selectNodeToPrunIter(node.right, (id << 1) + 1)
                var gT = (risk(node) - (riskBranchLeft + riskBranchRight)) / (numLeft + numRight - 1)
                //println("Node " + id + " r(t)=" + risk(node) +  "  g(t)=" + gT +
                //" (numLeft:%d,gLeft=%f) (numRight=%d, gRight=%f)".format(numLeft, riskBranchLeft, numRight, riskBranchRight))

                if (gT == currentMin) {
                    result = result + id
                }
                else if (gT < currentMin) {
                    result = Set[BigInt]()
                    result = result + id
                    currentMin = gT
                }

								//println("minGT = " + currentMin)
                (riskBranchLeft + riskBranchRight, numLeft + numRight)
            } else {
                (risk(node), 1) // one leaf node
            }
        }
        
        selectNodeToPrunIter(root, 1)
        
        //println("result:" + result)
        // union with the previous set of pruned nodes
        result = result ++ already_pruned_node;
        var pruned_node_list = result.toList.sortWith((a,b) => a < b)
        
        //filter useless pruned nodes
        // for example, if this set contain 3 and 7;
        // 3 is the parent of 7, we don't need to keep 7 in this set
        pruned_node_list.foreach(id => {
            var tmp = id
            while (tmp > 0){
                if (result.contains(tmp >> 1)){
                    result = result - id
                    tmp = 0
                }
                tmp = tmp >> 1
            }
        })
        
        //println("final result" + result)
        
        
        (result, currentMin)
    }

    private def pruneBranchs(root: Node, prunedNodeIDs: Set[BigInt]): Node = {

        def pruneBranchsIter(node: Node, id: BigInt): Node = {
            if (!node.isEmpty) {
                if (prunedNodeIDs.contains(id)) {
                    node.toLeafNode
                } else {
                    node.setLeft(pruneBranchsIter(node.left, id << 1))
                    node.setRight(pruneBranchsIter(node.right, (id << 1) + 1))
                    node
                }
            } else {
                node
            }
        }
        
        pruneBranchsIter(root, 1)
        
    }
    private def pruneBranch(root: Node, prunedNodeID: BigInt): Node = {
        var currentid: BigInt = 0

        val level = (Math.log(prunedNodeID.toDouble) / Math.log(2)).toInt
        var i: Int = level - 1
        var parent = root; // start adding from root node
        
        // try to find the parent of pruned node
        while (i > 0) {

            if ((prunedNodeID / (2 << i - 1)) % 2 == 0) {
                // go to the left
                parent = parent.left
            } else {
                // go go the right
                parent = parent.right
            }
            i -= 1
        } // end while

        if (prunedNodeID % 2 == 0) {
            parent.setLeft(parent.left.toLeafNode)
        } else {
            parent.setRight(parent.right.toLeafNode)
        }
        root

    }
    
    
    def getSubTreeSequence(tree : Node) : List[(Set[BigInt], Double)] = {
        
        var leafNodesOfT1 = firstPruneTree(tree)	// get T1
	    
	    var sequence_alpha_tree = List[(Set[BigInt], Double)]((leafNodesOfT1, 0))
	    
	    var finish = false
	    
	    var prunedNodeSet = leafNodesOfT1
	    
	    // SELECT SEQUENCE OF BEST SUB-TREE AND INTERVALS OF ALPHA
	    do {
	        var (nodesNeedToBePruned, alpha) = selectNodesToPrune(tree, prunedNodeSet)
	        //println("select node to prune:" + nodesNeedToBePruned)
	        
	        sequence_alpha_tree = sequence_alpha_tree.:+((nodesNeedToBePruned, alpha))
	        //println("sequence alpha-tree:" + sequence_alpha_tree)
	        
	        prunedNodeSet = nodesNeedToBePruned
	        
	         finish = (nodesNeedToBePruned.contains(1))	// until we prune branch of the root node
	    } while (!finish)
	        
	    sequence_alpha_tree    
    }
    
    def getTreeIndexByAlpha(givenAlpha : Double, sequence_tree_alpha : List[(Set[BigInt], Double)]) : Int = {
        if (givenAlpha == 0.0)
            0
        else {
            var i = 0
            val sequence_tree_alpha_with_infinity_alpha = sequence_tree_alpha :+ (Set[BigInt](), Double.MaxValue)
            var result: Int =
                sequence_tree_alpha_with_infinity_alpha.indexWhere {
                    case (leafNodes, alpha) =>
                        {
                            alpha >= givenAlpha
                        }
                } - 1

            result
        }
    }
    /**
     * @param treeModel
     * @param complexityParamter
     * @param dataset
     * @return
     */
    def Prune(treeModel : TreeModel, complexityParamter : Double, dataset: RDD[String], foldTimes : Int = 5) : TreeModel = {
	    
        
        this.maxcp = complexityParamter
	    
	    var sequence_alpha_tree = getSubTreeSequence(treeModel.tree) 
	        
	        
	    // CROSS-VALIDATION
	    
	    
	    val N = foldTimes
        var newdata = dataset.mapPartitions(partition => {
            var i = -1
            partition.map(x => {
            	i = (i + 1) % N
            	(i, x)
            })
        })
        
        var yIndex = treeModel.featureSet.getIndex(treeModel.usefulFeatureSet.data(treeModel.yIndex).Name)
        var mapTreeIndexToListErrorMetric : Map[Int, List[Double]] = Map[Int, List[Double]]()
        
        println("Start CROSS-VALIDATION")
        
        for (fold <- (0 to N -1)){
            println("\n ==================== Begin round %d ====================\n".format(fold))
            // split dataset into training data and testing data
            var datasetOfThisFold = newdata.filter(x => x._1 != fold).map(x => x._2)
            var testingData = newdata.filter(x => x._1 == fold).map(x => x._2)
            
            // build the full tree with the new training data
            val tree = new RegressionTree()
            tree.setDataset(datasetOfThisFold)
            if (DEBUG) println("feature names:" + treeModel.featureSet.data.map(x => x.Name).toArray.mkString(","))
            tree.setFeatureNames(treeModel.featureSet.data.map(x => x.Name).toArray)
            tree.treeBuilder.setMinSplit(treeModel.minsplit)
            tree.treeBuilder.setMaxDepth(treeModel.maxDepth)
            tree.treeBuilder.setThreshold(treeModel.threshold)
            tree.treeBuilder.setMaximumComplexity(treeModel.maximumComplexity)
            
            val treeModelOfThisFold = tree.buildTree(treeModel.yFeature, treeModel.xFeatures)
            
            //println("new model:\n" + treeModelOfThisFold)
            
            // broadcast the tre for using later (in prediction)
            var tree_broadcast = dataset.context.broadcast(tree)
            
            // get 'best' pruned tree candidates of the current tree 
            val sequence_alpha_tree_this_fold = getSubTreeSequence(treeModelOfThisFold.tree)
            
            if (DEBUG) println("sequence_alpha_tree_this_fold\n%s".format(sequence_alpha_tree_this_fold.mkString(",")))
            
            // init the list of (alpha,sub-tree) pairs
            var list_subtree_correspoding_to_beta = List[(Int,Set[BigInt])]()
            
            // beta is the typical value of each interval of alpha
            // this is the geometric midpoint of the interval: [alpha1, alpha2] => beta1 = sqrt(alpha1*alpha2)
            // get the list of sub-tree depends on value of beta
            for (i <- (0 to sequence_alpha_tree.length -2)){
                val beta = math.sqrt(sequence_alpha_tree(i)._2 * sequence_alpha_tree(i + 1)._2)
                val index = getTreeIndexByAlpha(beta, sequence_alpha_tree_this_fold)
                if (index < 0 && DEBUG) println("current beta:" + beta)
                list_subtree_correspoding_to_beta = list_subtree_correspoding_to_beta.:+( i , sequence_alpha_tree_this_fold(index)._1)
            }
            
            // get the prediction of all sub-tree in this fold
            // we don't want to send many tree model through the network
            // so, the solution is: send the 'full' tree model (or the tree has been pre-processed already)
            // and the lists of leaf nodes of each pruned-tree candidates
            var predicted_value_by_subtrees = testingData.map(line => {
		        var record = line.split(",")
		        (
		        		list_subtree_correspoding_to_beta.map(sequence => {
				            (sequence._1, tree_broadcast.value.predictOneInstance(record, sequence._2))
				        }),
		        	record(yIndex)
		        )
            })
            
            var diff_predicted_value_and_true_value = (
                    predicted_value_by_subtrees.flatMap{
                        case (list_of_index_predictedValue, truevalue) =>{
                            list_of_index_predictedValue.map{case (index, predictedValue) => (index, predictedValue, truevalue)}
                        }
                    }
            )
            // RDD[(index, predictedValue, trueValue)]
            
            var valid_diff_predicted_value_and_true_value = diff_predicted_value_and_true_value.filter(v => (!v._2.equals("???") 
            && (Utility.parseDouble(v._3) match {
                case Some(d :Double) => true 
                case None => false 
            }))) // filter invalid record, v._2 is predicted value
            
            val diff = (valid_diff_predicted_value_and_true_value
                    .map{ case (index, predictedStringValue, trueStringValue) => (index, predictedStringValue.toDouble, trueStringValue.toDouble, 1)}
                    .map{ case (index, pValue, tValue, counter) => (index, (pValue - tValue, (pValue-tValue)*(pValue-tValue), counter))}
            	)
            	
            val sums = diff.reduceByKey((x,y) => {
                (x._1 + y._1, x._2 + y._2, x._3 + y._3)
            })
            
            val squareErrors = sums.map { 
                case (index, (sumDiff, sumDiffPower2, numInstances)) => (index, sumDiffPower2/numInstances) 
            }.collect
            
            squareErrors.foreach {
                case (idx, mse) => {
                    var errors = mapTreeIndexToListErrorMetric.getOrElse(idx, List[Double]())
                    errors = errors.+:(mse)
                    mapTreeIndexToListErrorMetric = mapTreeIndexToListErrorMetric.updated(idx, errors)
                }
            }
            println("\n ==================== End round %d ====================\n".format(fold))
            
        }	// END CROSS-VALIDATION
        
        var indexOfTreeHasMinAverageError = 0
        var minAverageError = Double.MaxValue
        
        if (DEBUG)
            mapTreeIndexToListErrorMetric.foreach {
            case (key, value) => {
                println("index:" + key + " List of errors:" + value.mkString(","))
            }
        }
        
        mapTreeIndexToListErrorMetric.foreach{
            case (key, value) => {
                var sumError : Double = 0.0
                var numElement = 0
                value.foreach(error => { numElement = numElement + 1; sumError = sumError + error})
                val averageError = sumError/numElement
                if (averageError < minAverageError){
                    minAverageError = averageError
                    indexOfTreeHasMinAverageError = key
                }
            }
        }
        
        println("index of tree having min average error:" + indexOfTreeHasMinAverageError)
        println("min average: " + minAverageError)
        
        var leafNodesOfTheBestTree = sequence_alpha_tree(indexOfTreeHasMinAverageError)._1
        println("the final leaf nodes:" + leafNodesOfTheBestTree)
        
        
        pruneBranchs(treeModel.tree, leafNodesOfTheBestTree)
        
        //pruneBranch(treeModel., prunedNodeID)
        
        treeModel
	}
}