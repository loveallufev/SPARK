package test

object test5 {
  var node1:IntSet = new Empty()                  //> node1  : test.IntSet = .
  node1 = node1.addInt(3)
  node1 = node1.addInt(5)
  node1                                           //> res0: test.IntSet = {.3{.5.}}
  
  var node2: IntSet = new Empty() addInt 1 addInt 2 addInt 5
                                                  //> node2  : test.IntSet = {.1{.2{.5.}}}
  node2 union node1                               //> res1: test.IntSet = {{{.1.}2{.3.}}5.}

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