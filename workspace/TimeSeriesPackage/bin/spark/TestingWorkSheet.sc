package spark

import collection.immutable.TreeMap
import org.apache.spark._
import org.apache.spark.SparkContext._

object TestingWorkSheet {

    class FeatureAggregateInfo(val index: Int, var xValue: Any, var yValue: Double, var frequency: Int) extends Serializable {
        def addFrequency(acc: Int): FeatureAggregateInfo = { this.frequency = this.frequency + acc; this }
        def +(that: FeatureAggregateInfo) = {
            this.frequency = this.frequency + that.frequency
            this.yValue = this.yValue + that.yValue
            this
        }
        override def toString() = "Feature(index:" + index + " | xValue:" + xValue +
            " | yValue" + yValue + " | frequency:" + frequency + ")";
    }

    case class FeatureSet(file: String, val context: SparkContext) {
        def this(file: String) = this(file, new SparkContext("local", "SparkContext"))
        private def loadFromFile() = {

            //val input_fileName: String = "/home/loveallufev/semester_project/input/small_input";
            val myTagInputFile = context.textFile(file, 1)

            var tags = myTagInputFile.take(2).flatMap(line => line.split(",")).toSeq.toList

            // ( index_of_feature, (Feature_Name, Feature_Type))
            //( (0,(Temperature,1))  , (1,(Outlook,1)) ,  (2,(Humidity,1)) , ... )
            (((0 until tags.length / 2) map (index => (tags(index), tags(index + tags.length / 2)))) zip (0 until tags.length))
                .map(x => FeatureInfo(x._1._1, x._1._2, x._2)).toList
        }

        lazy val data = loadFromFile()
        lazy val numberOfFeature = data.length
    }

    def parseDouble(s: String) = try { Some(s.toDouble) } catch { case _ => None }
                                                  //> parseDouble: (s: String)Option[Double]
    val context = new SparkContext("local", "SparkContext")
                                                  //> 14/01/12 16:41:22 WARN util.Utils: Your hostname, ubuntu resolves to a loop
                                                  //| back address: 127.0.1.1; using 192.168.190.138 instead (on interface eth0)
                                                  //| 14/01/12 16:41:22 WARN util.Utils: Set SPARK_LOCAL_IP if you need to bind t
                                                  //| o another address
                                                  //| 14/01/12 16:41:23 INFO slf4j.Slf4jEventHandler: Slf4jEventHandler started
                                                  //| 14/01/12 16:41:23 INFO spark.SparkEnv: Registering BlockManagerMaster
                                                  //| 14/01/12 16:41:24 INFO storage.MemoryStore: MemoryStore started with capaci
                                                  //| ty 390.7 MB.
                                                  //| 14/01/12 16:41:24 INFO storage.DiskStore: Created local directory at /tmp/s
                                                  //| park-local-20140112164124-e036
                                                  //| 14/01/12 16:41:24 INFO network.ConnectionManager: Bound socket to port 5797
                                                  //| 8 with id = ConnectionManagerId(ubuntu.local,57978)
                                                  //| 14/01/12 16:41:24 INFO storage.BlockManagerMaster: Trying to register Block
                                                  //| Manager
                                                  //| 14/01/12 16:41:24 INFO storage.BlockManagerMasterActor$BlockManagerInfo: Re
                                                  //| gistering block manager ubuntu.local:57978 with
                                                  //| Output exceeds cutoff limit.
    
    def processLine(line: Array[String], numberFeatures: Int, fTypes: Vector[String]): org.apache.spark.rdd.RDD[FeatureAggregateInfo] = {
        val length = numberFeatures
        var i = -1;
        parseDouble(line(length - 1)) match {
            case Some(yValue) => { // check type of Y : if isn't continuos type, return nothing
                context.parallelize(line.map(f => {
                    i = (i + 1) % length
                    fTypes(i) match {
                        case "0" => {	// If this is a numerical feature => parse value from string to double
                            val v = parseDouble(f);
                            v match {
                                case Some(d) => new FeatureAggregateInfo(i, d, yValue, 1)
                                case None => new FeatureAggregateInfo(-1, f, 0, 0)
                            }
                        }
                        // if this is a categorial feature => return a FeatureAggregateInfo
                        case "1" => new FeatureAggregateInfo(i, f, yValue, 1)
                    }
                }))
            }
            //case None => org.apache.spark.rdd.RDD[FeatureAggregateInfo]()
        }

    }                                             //> processLine: (line: Array[String], numberFeatures: Int, fTypes: Vector[Stri
                                                  //| ng])org.apache.spark.rdd.RDD[spark.TestingWorkSheet.FeatureAggregateInfo]

    
    
    val dataInputURL = "/home/loveallufev/semester_project/input/small_input2"
                                                  //> dataInputURL  : java.lang.String = /home/loveallufev/semester_project/input
                                                  //| /small_input2

    var featureSet = new FeatureSet("/home/loveallufev/semester_project/input/tag_small_input2", context)
                                                  //> featureSet  : spark.TestingWorkSheet.FeatureSet = FeatureSet(/home/loveallu
                                                  //| fev/semester_project/input/tag_small_input2,org.apache.spark.SparkContext@5
                                                  //| 4217c70)
		val myDataFile = context.textFile(dataInputURL, 1)
                                                  //> 14/01/12 16:41:26 INFO storage.MemoryStore: ensureFreeSpace(33736) called w
                                                  //| ith curMem=0, maxMem=409699614
                                                  //| 14/01/12 16:41:26 INFO storage.MemoryStore: Block broadcast_0 stored as val
                                                  //| ues to memory (estimated size 32.9 KB, free 390.7 MB)
                                                  //| myDataFile  : org.apache.spark.rdd.RDD[String] = MappedRDD[1] at textFile a
                                                  //| t spark.TestingWorkSheet.scala:72
    var myDataFile2 = scala.io.Source.fromFile(dataInputURL).getLines.toList
                                                  //> myDataFile2  : List[String] = List(hot,sunny,high,false,10,0, hot,sunny,hig
                                                  //| h,true,8,0, hot,overcast,high,false,12,1, cool,rainy,normal,false,14.5,1, c
                                                  //| ool,overcast,normal,true,6.24,1, mild,sunny,high,false,8,0, cool,sunny,norm
                                                  //| al,false,30,1, mild,rainy,normal,false,10,1, mild,sunny,normal,true,1,1, mi
                                                  //| ld,overcast,high,true,7,1, hot,overcast,normal,false,9,1, mild,rainy,high,t
                                                  //| rue,10,0, cool,rainy,normal,true,5,0, mild,rainy,high,false,7,1)

    var mydata = myDataFile.map(line => line.split(","))
                                                  //> mydata  : org.apache.spark.rdd.RDD[Array[java.lang.String]] = MappedRDD[2] 
                                                  //| at map at spark.TestingWorkSheet.scala:75
    val number_of_features = mydata.take(1)(0).length
                                                  //> 14/01/12 16:41:26 WARN util.NativeCodeLoader: Unable to load native-hadoop 
                                                  //| library for your platform... using builtin-java classes where applicable
                                                  //| 14/01/12 16:41:26 WARN snappy.LoadSnappy: Snappy native library not loaded
                                                  //| 14/01/12 16:41:26 INFO mapred.FileInputFormat: Total input paths to process
                                                  //|  : 1
                                                  //| 14/01/12 16:41:26 INFO spark.SparkContext: Starting job: take at spark.Test
                                                  //| ingWorkSheet.scala:76
                                                  //| 14/01/12 16:41:26 INFO scheduler.DAGScheduler: Got job 0 (take at spark.Tes
                                                  //| tingWorkSheet.scala:76) with 1 output partitions (allowLocal=true)
                                                  //| 14/01/12 16:41:26 INFO scheduler.DAGScheduler: Final stage: Stage 0 (take a
                                                  //| t spark.TestingWorkSheet.scala:76)
                                                  //| 14/01/12 16:41:26 INFO scheduler.DAGScheduler: Parents of final stage: List
                                                  //| ()
                                                  //| 14/01/12 16:41:26 INFO scheduler.DAGScheduler: Missing parents: List()
                                                  //| 14/01/12 16:41:26 INFO scheduler.DAGScheduler: Computing the requested part
                                                  //| ition locally
                                                  //| 14/01/12 16:41:26 
                                                  //| Output exceeds cutoff limit.
    val featureTypes = Vector[String]() ++ featureSet.data.map(x => x.Type)
                                                  //> 14/01/12 16:41:27 INFO storage.MemoryStore: ensureFreeSpace(33744) called w
                                                  //| ith curMem=33736, maxMem=409699614
                                                  //| 14/01/12 16:41:27 INFO storage.MemoryStore: Block broadcast_1 stored as val
                                                  //| ues to memory (estimated size 33.0 KB, free 390.7 MB)
                                                  //| 14/01/12 16:41:27 INFO mapred.FileInputFormat: Total input paths to process
                                                  //|  : 1
                                                  //| 14/01/12 16:41:27 INFO spark.SparkContext: Starting job: take at spark.Test
                                                  //| ingWorkSheet.scala:27
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Got job 1 (take at spark.Tes
                                                  //| tingWorkSheet.scala:27) with 1 output partitions (allowLocal=true)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Final stage: Stage 1 (take a
                                                  //| t spark.TestingWorkSheet.scala:27)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Parents of final stage: List
                                                  //| ()
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Missing parents: List()
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Computing the requested part
                                                  //| ition locally
    val aggregateData = mydata.map(processLine(_, number_of_features, featureTypes))
                                                  //> aggregateData  : org.apache.spark.rdd.RDD[org.apache.spark.rdd.RDD[spark.Te
                                                  //| stingWorkSheet.FeatureAggregateInfo]] = MappedRDD[5] at map at spark.Testin
                                                  //| gWorkSheet.scala:78
    println(buildTree(aggregateData))             //> 14/01/12 16:41:27 INFO spark.SparkContext: Starting job: take at spark.Test
                                                  //| ingWorkSheet.scala:85
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Registering RDD 7 (groupBy a
                                                  //| t spark.TestingWorkSheet.scala:85)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Got job 2 (take at spark.Tes
                                                  //| tingWorkSheet.scala:85) with 1 output partitions (allowLocal=true)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Final stage: Stage 2 (take a
                                                  //| t spark.TestingWorkSheet.scala:85)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Parents of final stage: List
                                                  //| (Stage 3)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Missing parents: List(Stage 
                                                  //| 3)
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Submitting Stage 3 (MappedRD
                                                  //| D[7] at groupBy at spark.TestingWorkSheet.scala:85), which has no missing p
                                                  //| arents
                                                  //| 14/01/12 16:41:27 INFO scheduler.DAGScheduler: Failed to run take at spark.
                                                  //| TestingWorkSheet.scala:85
                                                  //| org.apache.spark.Spar
                                                  //| Output exceeds cutoff limit.

    //def buildTree(data: List[FeatureAggregateInfo]): Unit = {

		def buildTree(data: org.apache.spark.rdd.RDD[org.apache.spark.rdd.RDD[FeatureAggregateInfo]]): Node = {
				
				var yFeature = data.map(x => x.filter(y => (y.index == number_of_features - 1)).first ).groupBy(x => x.index).take(1)(0)
				if (yFeature._2.length == 1) new Empty(yFeature._2(0).toString)
				
        var featureValueSorted = (data.reduce(_ union _)
        													.groupBy(x => (x.index, x.xValue))
            .map(x => (new FeatureAggregateInfo(x._1._1, x._1._2, 0, 0)
                + x._2.foldLeft(new FeatureAggregateInfo(x._1._1, x._1._2, 0, 0))(_ + _)))
            /*
                																	Feature(index:2 | xValue:normal | yValue6.0 | frequency:7)
                                                  Feature(index:1 | xValue:sunny | yValue2.0 | frequency:5)
                                                  Feature(index:4 | xValue:14.5 | yValue1.0 | frequency:1)
                                                  Feature(index:2 | xValue:high | yValue3.0 | frequency:7)
                  */
            .groupBy(x => x.index)
            .map(x =>
            	(x._1, x._2.toList.sortBy(
            		v => v.xValue match {
	                case d: Double => d // sort by xValue if this is numerical feature
	                case s: String => v.yValue / v.frequency // sort by the average of Y if this is categorical value
            		})
            	))
            	)
         
         var splittingPointFeature = featureValueSorted.map(x =>
            				(	x._1,
            					x._2(0).xValue match {
            						case s: String => // process with categorical feature
            							//x._2.map (f => f)
	        							{
	        								var acc: Int = 0; // the number records on the left of current feature
	        								var currentSumY : Double = 0	// current sum of Y of elements on the left of current feature
	        								val numRecs : Int = x._2.foldLeft(0)(_ + _.frequency)	// number of records
	        								val sumY = x._2.foldLeft(0.0)(_ + _.yValue)	// total sum of Y
	        								var splitPoint: Set[String] = Set[String]()
	        								var lastFeatureValue = new FeatureAggregateInfo(-1,0,0,0)
	        								x._2.map(f => {
	        																
	        																if (lastFeatureValue.index == -1){
	        																	lastFeatureValue = f
	        																	(0.0,0.0)
	        																}else {
	        																	currentSumY = currentSumY + lastFeatureValue.yValue
	        																	splitPoint = splitPoint + lastFeatureValue.xValue.asInstanceOf[String]
	        																	acc = acc + lastFeatureValue.frequency
	        																	val weight = currentSumY*currentSumY/acc + (sumY - currentSumY)*(sumY - currentSumY)/(numRecs - acc)
	        																	lastFeatureValue = f
	        																	(splitPoint, weight)
	        																}
	        															}
	        												).drop(1).maxBy(_._2) // select the best split
	        							}
            						case d: Double => // process with numerical feature
            						{
            							var acc: Int = 0	// number of records on the left of the current element
            							val numRecs : Int = x._2.foldLeft(0)(_ + _.frequency)
            							var currentSumY : Double = 0
            							val sumY = x._2.foldLeft(0.0)(_ + _.yValue)
            							var posibleSplitPoint : Double = 0
            							var lastFeatureValue = new FeatureAggregateInfo(-1,0,0,0)
            							x._2.map (f => {
            							
            														if (lastFeatureValue.index == -1){
            															lastFeatureValue = f
            															(0.0,0.0)
            														}
            														else {
            															posibleSplitPoint = (f.xValue.asInstanceOf[Double] + lastFeatureValue.xValue.asInstanceOf[Double])/2;
            															currentSumY = currentSumY + lastFeatureValue.yValue
            															acc = acc + lastFeatureValue.frequency
            															val weight = currentSumY*currentSumY/acc + (sumY - currentSumY)*(sumY - currentSumY)/(numRecs - acc)
	        																lastFeatureValue = f
	        																(posibleSplitPoint, weight)
            														}
	            													
            													}).drop(1).maxBy(_._2)	// select the best split
            						}	// end of matching double
            					}	// end of matching xValue
            				)	// end of pair
            		).filter(_._1 != number_of_features - 1).collect.toList.maxBy(_._2._2)	// select best feature to split
        
        val chosenFeatureInfo = featureSet.data.filter(f => f.index == splittingPointFeature._1)(0)
        
        splittingPointFeature match {
        	case fs : (Int, (Set[String], Double)) => {	// split on categorical feature
        		//val left = data filter (x => fs._2._1.contains(x(chosenFeatureInfo.index).xValue.asInstanceOf[String]))
            //val right = data filter (x => !fs._2._1.contains(x(chosenFeatureInfo.index).xValue.asInstanceOf[String]))
            val left = data.filter {
            	x => (
            	x.filter( y => ( y.index == chosenFeatureInfo.index && fs._2._1.contains(y.xValue.asInstanceOf[String]))).count > 0
            	)
            }
            val right = data.filter {
            	x => (
            	x.filter( y => ( y.index == chosenFeatureInfo.index && fs._2._1.contains(y.xValue.asInstanceOf[String]))).count == 0
            	)
            }
                    new NonEmpty(
                        chosenFeatureInfo, // featureInfo
                        (fs._2._1.toString, "Not in " + fs._2._1.toString), // left + right conditions
                        buildTree(left), // left
                        buildTree(right) // right
                        )
        	}
        	case fd : (Int, (Double, Double)) => {	// split on numerical feature
        		//val left = data filter (x => (x(chosenFeatureInfo.index).xValue.asInstanceOf[Double] < fd._2._1))
            //val right = data filter (x => (x(chosenFeatureInfo.index).xValue.asInstanceOf[Double] >= fd._2._1))
            val left = data.filter {
            	x => (
            	x.filter( y => ( y.index == chosenFeatureInfo.index &&  y.xValue.asInstanceOf[Double] < fd._2._1)).count > 0
            	)
            }
            val right = data.filter {
            	x => (
            	x.filter( y => ( y.index == chosenFeatureInfo.index &&  y.xValue.asInstanceOf[Double] < fd._2._1)).count == 0
            	)
            }
        		new NonEmpty(
                        chosenFeatureInfo, // featureInfo
                        (chosenFeatureInfo.Name + " < " + fd._2._1, chosenFeatureInfo.Name + " >= " + fd._2._1), // left + right conditions
                        buildTree(left), // left
                        buildTree(right) // right
                        )
        	}
        	
        }
        //splittingPointFeature.foreach(x => println(x))
        //println(tmp.mkString("***"))
    }

}