package learnscala1

import test._

object test7 {
  val list = new Cons(1, new Cons(2, new Cons(3, new Nil)))
                                                  //> list  : learnscala1.Cons[Int] = learnscala1.Cons@271816d1
  
  list.findElementAtIndex(0)                      //> res0: Int = 1
 	var x = List(1,2)                         //> x  : learnscala1.Cons[Int] = learnscala1.Cons@430eb552
}