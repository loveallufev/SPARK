package machinelearning
import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._

object Test {
	def main(args : Array[String]) = {
	    val context = new SparkContext("local", "SparkContext")

	    val dataInputURL = "/home/loveallufev/semester_project/input/small_input2"
	
	    val myDataFile = context.textFile(dataInputURL, 1)
	    val metadata = context.textFile("/home/loveallufev/semester_project/input/tag_small_input2", 1)
	
	    val tree = new RegressionTree(myDataFile, metadata, context, 4)
	    val stime = System.nanoTime()
	    println(tree.buildTree())
	    println("Finish in %f second(s)".format((System.nanoTime() - stime)/1e9))
	}
}