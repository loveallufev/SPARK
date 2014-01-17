package test

object test3 {
    def product(f: Int => Int)(a: Int, b: Int): Int =
        if (a > b) 1
        else f(a) * product(f)(a + 1, b)          //> product: (f: Int => Int)(a: Int, b: Int)Int

    product(x => x * x)(3, 4);                    //> res0: Int = 144

    def factorial(n: Int) = product(x => x)(1, n) //> factorial: (n: Int)Int

    factorial(3)                                  //> res1: Int = 6

    def abs(x: Double) = if (x < 0) -x else x     //> abs: (x: Double)Double

    def isCloseEnough(a: Double, b: Double) =
        if (abs(a - b) / b < 0.0001) true else false
                                                  //> isCloseEnough: (a: Double, b: Double)Boolean

    def fixedPoint(f: Double => Double)(firstPoint: Double) = {
        def iter(guess: Double) :Double = {
            val nextGuess = f(guess)
            if (isCloseEnough(guess, nextGuess)) guess
            else iter(nextGuess)
        }
        
        iter(firstPoint)
    }                                             //> fixedPoint: (f: Double => Double)(firstPoint: Double)Double
    
    def averageDamp(f: Double => Double)(x: Double) = (x + f(x))/2
                                                  //> averageDamp: (f: Double => Double)(x: Double)Double
		
		def sqrt(x:Double) = fixedPoint(averageDamp(y => x/y))(1)
                                                  //> sqrt: (x: Double)Double
		
		sqrt(4)                           //> res2: Double = 2.0000000929222947
}