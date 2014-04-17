import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import rtreelib._
import rtreelib.core.RegressionTree
import rtreelib.evaluation.Evaluation
import rtreelib.core._
import scala.collection.immutable._
import bigfoot.helpers._
import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.KryoRegistrator
import java.io.File

object NextLocationPrediction {
    def main(args: Array[String]): Unit = {

        val LOCAL = true

        var inputTrainingPath = "/Users/loveallufev/Documents/MATLAB/mobile-locations-training.txt";
        var inputTestingPath = "/Users/loveallufev/Documents/MATLAB/mobile-locations-testing.txt";
        var outputDir = "";
        var pathOfFullTree = ""
        var pathOfPrunedTree = ""

        var conf = (new SparkConf()
            .setMaster("local")
            .setAppName("Swisscom"))

        if (!LOCAL) {
            inputTrainingPath = "hdfs://spark-master-001:8020/user/input/MIT/mobile-locations-training.txt";
            inputTestingPath = "hdfs://spark-master-001:8020/user/input/MIT/mobile-locations-testing.txt";
            conf = (new SparkConf()
                .setMaster("spark://spark-master-001:7077")
                .setAppName("Swisscom")
                .setSparkHome("/opt/spark")
                .set("spark.executor.memory", "2000m"))
        }

        val context = new SparkContext(conf)

        val trainingData = context.textFile(inputTrainingPath, 1)
        val testingData = context.textFile(inputTestingPath, 1)

        var USERID = "20"

        val treeForSingleUser = new RegressionTree();
        val treeForAllUser = new RegressionTree();

        var filteredData = trainingData.filter(line => {
            var values = line.split(",")
            if (values(3) == "1") false
            else true
        }) // filter no signal records

        treeForAllUser.setDataset(filteredData, false)
        treeForAllUser.setFeatureNames(Array("UserID", "Year", "Month", "DayOfMonth",
            "DayOfWeek", "Hour", "Minute", "Area-Cell", "Area-Cell-Index"))
        treeForAllUser.treeBuilder.setMinSplit(1)
        treeForAllUser.treeBuilder.setThreshold(0)
        treeForAllUser.treeBuilder.setMaximumComplexity(0)

        //20,2004,9,1,Wed,17,29,5119.40332,17

        println("Tree for all users:\n" +
            treeForAllUser.buildTree("Area-Cell-Index", Set(as.String("Month"), as.String("DayOfWeek"), as.Number("Hour"))))

        println("Tree after pruning:\n" +
            Pruning.Prune(treeForAllUser.treeModel, 0.01, filteredData, 5))
        treeForAllUser.writeModelToFile("/tmp/allusers.model")

        for (i <- (1 to 106)) {
            try {
                USERID = i.toString

                var fiteredDataForSingleUser = filteredData.filter(line =>
                    {
                        var values = line.split(",")
                        if (values(0) != USERID) false
                        else true
                    })

                treeForSingleUser.setDataset(fiteredDataForSingleUser, false)
                treeForSingleUser.setFeatureNames(Array("UserID", "Year", "Month", "DayOfMonth",
                    "DayOfWeek", "Hour", "Minute", "Area-Cell", "Area-Cell-Index"))
                treeForSingleUser.treeBuilder.setMinSplit(1)
                treeForSingleUser.treeBuilder.setThreshold(0)
                treeForSingleUser.treeBuilder.setMaximumComplexity(0)

                println("Tree for single users:\n" +
                    treeForSingleUser.buildTree("Area-Cell-Index", Set(as.String("Month"), as.String("DayOfWeek"), as.Number("Hour"))))

                println("Tree after pruning:\n" +
                    Pruning.Prune(treeForSingleUser.treeModel, 0.01, filteredData, 5))
                treeForSingleUser.writeModelToFile("/tmp/user" + USERID + ".model")
            } catch {
                case e: Throwable => {
                    println("ERROR:Can not build model for user " + USERID + "\n. Error:\n")
                    e.printStackTrace()
                }
            }
        }

    }
}