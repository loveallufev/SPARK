package test

import org.apache.spark._
import org.apache.spark.SparkContext._
import core._
import akka.actor.{ActorSystem, Props}

object Test {
	def main (args : Array[String]){
	    var handler = new EngeryLineHandler()
	    var context = new SparkContext("local", "SparkContext")
	    
	    val input :String ="/home/loveallufev/semester_project/spark-0.8.0-incubating/README.md"
	    val output :String = "/home/loveallufev/semester_project/output/wordcount"
	    val reader : Reader = new Reader(input, output, handler, context)
	    reader.read()
	    reader.write()
	}
}