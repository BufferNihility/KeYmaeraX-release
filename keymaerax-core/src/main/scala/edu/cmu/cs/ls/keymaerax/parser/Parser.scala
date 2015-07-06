/**
 * Differential Dynamic Logic parser for concrete KeYmaera X notation.
 * @author aplatzer
 * @see "Andre Platzer. A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981, 2015."
 */
package edu.cmu.cs.ls.keymaerax.parser

import edu.cmu.cs.ls.keymaerax.core._

/**
 * Parser interface for KeYmaera X.
 * @author aplatzer
 */
trait Parser extends (String => Expression) {

  /** Parse the input string in the concrete syntax as a differential dynamic logic expression */
  def apply(input: String): Expression

  /** Parse the input string in the concrete syntax as a differential dynamic logic term */
  def termParser: (String => Term)

  /** Parse the input string in the concrete syntax as a differential dynamic logic formula */
  def formulaParser: (String => Formula)

  /** Parse the input string in the concrete syntax as a differential dynamic logic program */
  def programParser: (String => Program)

  /** Parse the input string in the concrete syntax as a differential dynamic logic differential program */
  def differentialProgramParser: (String => DifferentialProgram)

  /** A pretty-printer that can write the output that this parser reads */
  def printer: PrettyPrinter

}
