package parserTests

import edu.cmu.cs.ls.keymaerax.parser.{KeYmaeraXLexer, KeYmaeraXAxiomParser}
import org.scalatest.{PrivateMethodTester, Matchers, FlatSpec}

/**
 * Created by nfulton on 6/18/15.
 */
class AxiomFileParser extends FlatSpec with Matchers with PrivateMethodTester {

  "The AxiomFileParser" should "parse the axiom file" in {
    val axiomFile = edu.cmu.cs.ls.keymaerax.core.AxiomBase.loadAxiomString()
    KeYmaeraXAxiomParser(axiomFile)
  }
}
