package spark

import collection.immutable.TreeMap

trait Node {
	def condition: Nothing
	var feature: FeatureInfo
	def left : Node
	def right : Node
	def isEmpty : Boolean
	def toStringWithLevel(level : Int) :String
}

case class Empty extends Node {
    def isEmpty = true
    def condition = throw new NoSuchElementException ("empty.condition")
    def left = throw new NoSuchElementException ("empty.left")
    def right = throw new NoSuchElementException ("empty.right")
    var feature : FeatureInfo =  FeatureInfo("Empty", "0" ,0)
    def toStringWithLevel(level : Int) = "".padTo(2*level, "-") + "Empty"
}

case class NonEmpty(xFeature : FeatureInfo, xCondition : Nothing, xLeft : Node, xRight: Node) extends Node {
    def isEmpty = false
    def condition = xCondition
    def left = xLeft
    def right = xRight
    var feature : FeatureInfo =  xFeature
    
    def toStringWithLevel(level : Int) = "".padTo(2*level, "-") + feature.Name + "\n" + "|" + xLeft.toStringWithLevel(level+1)
}

object TestingWorkSheet {

		var v2 = Vector(0,1,2,3,4,5,6,7)
		v2.take(0)
    case class FeatureValueMap[+T](val fValue: T, var frequency: Int, var sumYValue: Double) {
    }

    var node : Node =
    	new NonEmpty(
    				FeatureInfo("F 1", "1", 0),
    				"x < 3",
    				NonEmpty(
    					FeatureInfo("F 2", "1", 1),
    					"y > 3",
    					Empty(),
    					Empty()
    				),
    				NonEmpty(
    					FeatureInfo("F 3", "1", 1),
    					"z > 3",
    					Empty(),
    					Empty()
    				)
    	)
}