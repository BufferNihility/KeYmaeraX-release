import org.scalatest._
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics._
import edu.cmu.cs.ls.keymaera.tools._
import java.math.BigDecimal
import java.io.File

class TacticTests extends FlatSpec with Matchers {
  var qet : QETool = new JLinkMathematicaLink()
  val x = Variable("x", None, Real)
  val y = Variable("y", None, Real)

  val zero = Number(new BigDecimal("0"))

  def num(n : Integer) = Number(new BigDecimal(n.toString()))
  def snum(n : String) = Number(new BigDecimal(n))

  "We" should "learn a lemma from (x > 0 & y > x) -> x >= 0" in {
    val f = TacticLibrary.universalClosure(Imply(And(GreaterThan(Real, x, zero), GreaterThan(Real, y, x)), GreaterEquals(Real, x, zero)))
    qet.qe(f) should be (True)
    val (file, id, res) = TacticLibrary.addRealArithLemma(qet, f)
    (res match {
      case Equiv(_, True) => true
      case _ => false
    }) should be (True) 
    LookupLemma(file,id)
  }
}
