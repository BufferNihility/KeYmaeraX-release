package testHelper

import java.io.File

import edu.cmu.cs.ls.keymaera.api.ComponentConfig
import edu.cmu.cs.ls.keymaera.core.{Sequent, Formula}
import edu.cmu.cs.ls.keymaera.parser.KeYmaeraParser

/**
 * Created by ran on 2/4/15.
 * @author Ran Ji
 * @author Stefan Mitsch
 */
object ParserFactory {

  /**
   * Returns the sequent from an input stream.
   * @param in The input stream.
   * @return The sequent.
   */
  def parseToSequent(in: java.io.InputStream) = {
    val content = io.Source.fromInputStream(in).mkString
    new KeYmaeraParser(false, ComponentConfig).runParser(content) match {
      case f: Formula => Sequent(List(), collection.immutable.IndexedSeq[Formula](), collection.immutable.IndexedSeq[Formula](f))
      case a => throw new IllegalArgumentException("Parsing the input did not result in a formula but in: " + a)
    }
  }

  /**
   * Return the formula from a .key input stream.
   * @param in The input stream.
   * @return The formula.
   */
  def parseToFormula(in: java.io.InputStream) = {
    val content = io.Source.fromInputStream(in).mkString
    new KeYmaeraParser(false, ComponentConfig).runParser(content) match {
      case f: Formula => f
      case a => throw new IllegalArgumentException("Parsing the input did not result in a formula but in: " + a)
    }
  }
}
