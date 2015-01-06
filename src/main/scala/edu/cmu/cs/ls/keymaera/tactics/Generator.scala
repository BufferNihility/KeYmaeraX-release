package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core._

/**
 * Created by smitsch on 12/23/14.
 */

/**
 * apply results in a formula to try.
 * Results do not have to be deterministic, e.g., calls to apply might advance to the next candidate.
 * Results can also be deterministic.
 */
trait Generator[A] extends ((Sequent, Position) => Option[A]) {
  def peek(s: Sequent, p: Position): Option[A]
}

class Generate[A](f: A) extends Generator[A] {
  def apply(s: Sequent, p: Position) = Some(f)
  def peek(s: Sequent, p: Position) = Some(f)
}

class NoneGenerate[A] extends Generator[A] {
  def apply(s: Sequent, p: Position) = None
  def peek(s: Sequent, p: Position) = None
}

class ConfigurableGenerate[A] extends Generator[A] {
  var products = Map[Expr,A]()
  def apply(s: Sequent, p: Position) = s.apply(p) match {
    case BoxModality(prg, _) => products.get(prg)
    case DiamondModality(prg, _) => products.get(prg)
    case _ => products.get(s.apply(p))
  }
  def peek(s: Sequent, p: Position) = s.apply(p) match {
    case BoxModality(prg, _) => products.get(prg)
    case DiamondModality(prg, _) => products.get(prg)
    case _ => products.get(s.apply(p))
  }
}