package test

object test5 {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(61); 
  var node1:IntSet = new Empty();System.out.println("""node1  : test.IntSet = """ + $show(node1 ));$skip(26); 
  node1 = node1.addInt(3);$skip(26); 
  node1 = node1.addInt(5);$skip(8); val res$0 = 
  node1;System.out.println("""res0: test.IntSet = """ + $show(res$0));$skip(64); 
  
  var node2: IntSet = new Empty() addInt 1 addInt 2 addInt 5;System.out.println("""node2  : test.IntSet = """ + $show(node2 ));$skip(20); val res$1 = 
  node2 union node1;System.out.println("""res1: test.IntSet = """ + $show(res$1))}

}


abstract class IntSet {
	def isContain(x : Int) : Boolean;
	def addInt(x : Int) : IntSet;
	def union(that: IntSet)  :IntSet
}

class Empty extends IntSet {
	override def isContain(x: Int) = false
	override def addInt(x: Int) = new Node(x, new Empty(), new Empty())
	override def union(that: IntSet) = that
	override def toString() = "."
}

class Node(value : Int, left: IntSet, right:IntSet) extends IntSet{
	override def isContain(x: Int) =
		if (x < value) left.isContain(x)
		else if (x > value) right.isContain(x)
		else true
		
	override def addInt(x : Int) =
		if (x < value) new Node(value, left.addInt(x), right)
		else if (x > value) new Node(value, left, right.addInt(x))
		else this
		
	override def union(that: IntSet) : IntSet =
			((left union that) union right ).addInt(value)
		
	override def toString() = "{" + left + value + right + "}"
}