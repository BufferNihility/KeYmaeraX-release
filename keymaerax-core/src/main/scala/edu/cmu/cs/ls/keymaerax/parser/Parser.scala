/**
 * Differential Dynamic Logic parser for concrete KeYmaera X notation.
 * @author aplatzer
 * @see "Andre Platzer. A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981, 2015."
 */
package edu.cmu.cs.ls.keymaerax.parser

import edu.cmu.cs.ls.keymaerax.core.{Expression, Term, Formula, Program}

/**
 * Parser interface for KeYmaera X.
 * @author aplatzer
 */
trait Parser extends (String => Expression) {

  def termParser: (String => Expression)

  def formulaParser: (String => Expression)

  def programParser: (String => Expression)
}
