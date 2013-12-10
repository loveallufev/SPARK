package core

import org.apache.spark._
import org.apache.spark.SparkContext._

class Reader (xInput : String, xOutput:String, 
        xhandler: LineHandler, xcontext : SparkContext) {
	val inputDir  	= xInput
	val outputDir  = xOutput
	val handler = xhandler
	val context = xcontext
	var result : org.apache.spark.rdd.RDD[String] = {
	    val myfile = context.textFile(inputDir, 1);
	    result = myfile.map(line => handler.process(line))
	    result
	}
	
	def read() : org.apache.spark.rdd.RDD[String] = {
	    result
	}
	
	// Write the input stream after read and process
	def write() = {
	    //result.saveAsTextFile(outputDir)
	    result.saveAsTextFile(outputDir)
	}
}