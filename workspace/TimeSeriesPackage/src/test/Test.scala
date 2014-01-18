package test
import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import machinelearning.RegressionTree

object Test {
	def main(args : Array[String]) = {
	    val context = new SparkContext("local", "SparkContext")
	    
	    val dataInputURL = "/home/loveallufev/semester_project/input/small_input2"
	
	    val myDataFile = context.textFile(dataInputURL, 1)
	    val metadata = context.textFile("/home/loveallufev/semester_project/input/tag_small_input2", 1)
	
	    val tree = new RegressionTree(myDataFile, metadata, context)
	    val stime = System.nanoTime()
	    println(tree.buildTree())
	    println("Build tree in %f second(s)".format((System.nanoTime() - stime)/1e9))
	    println("Predict:" + tree.predict("cool,sunny,normal,false,30,1".split(",")))
	    
	    val bodyfat_data = context.textFile("data/bodyfat.csv", 1)
	    val bodyfat_metadata = context.textFile("data/bodyfat.tag", 1)
	    val tree2 = new RegressionTree(bodyfat_data, bodyfat_metadata, context,2)
	    println(tree2.buildTree())
	    
	}
}