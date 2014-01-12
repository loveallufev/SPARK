package spark

import org.apache.spark._
import org.apache.spark.SparkContext._

// This class will load Feature Set from a file
case class FeatureSet(file: String, val context: SparkContext) {
    def this(file: String) = this(file, new SparkContext("local", "SparkContext"))
    private def loadFromFile() = {

        //val input_fileName: String = "/home/loveallufev/semester_project/input/small_input";
        val myTagInputFile = context.textFile(file, 1)

        var tags = myTagInputFile.take(2).flatMap(line => line.split(",")).toSeq.toList

        // ( index_of_feature, (Feature_Name, Feature_Type))
        //( (0,(Temperature,1))  , (1,(Outlook,1)) ,  (2,(Humidity,1)) , ... )
        (((0 until tags.length / 2) map (index => (tags(index), tags(index + tags.length / 2)))) zip (0 until tags.length))
        .map (x => FeatureInfo(x._1._1, x._1._2, x._2)).toList
    }
    

    lazy val data = loadFromFile()
    lazy val numberOfFeature = data.length
}