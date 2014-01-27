package test
import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import machinelearning.RegressionTree

object Test {
	def main(args : Array[String]) = {
	    val context = new SparkContext("local", "SparkContext")
	    
	    val dataInputURL = "data/playgolf.csv"
	
	    
	    /*  TEST WITH PLAYGOLF DATASET */
	    val playgolf_data = context.textFile(dataInputURL, 1)
	    val playgolf_metadata = context.textFile("data/playgolf.tag", 1)
	
	    val tree = new RegressionTree(context, playgolf_metadata)
	    tree.setMinSplit(1)
	    var stime = System.nanoTime()
	    println(tree.buildTree(playgolf_data))
	    println("Build tree in %f second(s)".format((System.nanoTime() - stime)/1e9))
	    println("Predict:" + tree.predict("cool,sunny,normal,false,30,1".split(",")))
	    
	    
	    /* TEST WITH BODYFAT DATASET */
	    val bodyfat_data = context.textFile("data/bodyfat.csv", 1)
	    val bodyfat_metadata = context.textFile("data/bodyfat.tag", 1)
	    
	    val tree2 = new RegressionTree(context, bodyfat_metadata)
	    stime = System.nanoTime()
	    println(tree2.buildTree(bodyfat_data, "DEXfat", Set("age", "waistcirc","hipcirc","elbowbreadth","kneebreadth")))
	    println("Build tree in %f second(s)".format((System.nanoTime() - stime)/1e9))
	    println("Predict:" + tree2.predict("53,56,29.83,81,103,6.9,8.9,4.14,4.52,4.31,5.69".split(",")))

	    // write model to file
	    tree2.writeTreeToFile("/home/loveallufev/Documents/tree2.model")
	    
	    
	    /* LOAD TREE MODEL FROM FILE */
	    // create a new regression tree; context is any spark context
	    val tree3 = new RegressionTree (context);
	    
	    // load tree model from a file
	    tree3.loadTreeFromFile("/home/loveallufev/Documents/tree2.model")
	    println("Load tree from model and use that tre to predict")
	    
	    // use loaded model to predict
	    println("Predict:" + tree3.predict("53,56,29.83,81,103,6.9,8.9,4.14,4.52,4.31,5.69".split(",")))
	    
	    // evaluate with trained dataset
	    tree3.evaluate(bodyfat_data)
	    
	}
}