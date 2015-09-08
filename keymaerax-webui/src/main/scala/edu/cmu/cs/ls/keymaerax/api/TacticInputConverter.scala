/**
* Copyright (c) Carnegie Mellon University. CONFIDENTIAL
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.api

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraParser
import edu.cmu.cs.ls.keymaerax.tactics._

import scala.reflect.runtime.universe.{TypeTag,typeOf}

/**
 * Converts string input into core data structures
 * @author Stefan Mitsch
 */
object TacticInputConverter {

  /**
   * Converts string input into core data structures
   * @param params The string input
   * @param t The tag of the desired type
   * @tparam T The desired type
   * @return The string input converted to the specified type.
   */
  def convert[T](params: Map[Int,String], t: TypeTag[T]): T = {
    if (t.tpe <:< typeOf[Option[_]] && params.size == 0) {
      None.asInstanceOf[T]
    } else {
      assert(params.size == 1)
      params.map({ case (k,v) => convert(v, t) }).head
    }
  }

  /**
   * Converts string input into core data structures
   * @param params The string input
   * @param t The tags of the desired types
   * @tparam T The desired type of the first parameter
   * @tparam U The desired type of the second parameter
   * @return The string input converted to the specified tuple type
   */
  def convert2[T,U](params: Map[Int,String], t: (TypeTag[_], TypeTag[_])): (T,U) = {
    assert(params.size == 2)
    val theParams = params.map({ case (k,v) => (k, convert(v, t.productElement(k).asInstanceOf[TypeTag[_]])) })
    (theParams.getOrElse(0, throw new IllegalStateException("Converter messed up parameter 0")).asInstanceOf[T],
      theParams.getOrElse(1, throw new IllegalStateException("Converter messed up parameter 1")).asInstanceOf[U])
  }

  /**
   * Converts string input into core data structures
   * @param params The string input
   * @param t The tags of the desired types
   * @tparam T The desired type of the first parameter
   * @tparam U The desired type of the second parameter
   * @tparam V The desired type of the third parameter
   * @return The string input converted to the specified tuple type
   */
  def convert3[T,U,V](params: Map[Int,String], t: (TypeTag[_], TypeTag[_], TypeTag[_])): (T,U,V) = {
    assert(params.size == 3)
    val theParams = params.map({ case (k,v) => (k, convert(v, t.productElement(k).asInstanceOf[TypeTag[_]])) })
    (theParams.getOrElse(0, throw new IllegalStateException("Converter messed up parameter 0")).asInstanceOf[T],
      theParams.getOrElse(1, throw new IllegalStateException("Converter messed up parameter 1")).asInstanceOf[U],
      theParams.getOrElse(2, throw new IllegalStateException("Converter messed up parameter 2")).asInstanceOf[V])
  }

  /**
   * Converts string input into core data structures
   * @param param The string input
   * @param t The tag of the desired type
   * @tparam T The desired type
   * @return The string input converted to the specified type.
   */
  private def convert[T](param: String, t: TypeTag[T]): T = {
    if (t.tpe =:= typeOf[Option[Formula]]) new KeYmaeraParser().parseBareExpression(param) match {
      case Some(f: Formula) => Some(f).asInstanceOf[T]
      case None => throw new IllegalArgumentException("Cannot parse " + param)
    } else if (t.tpe =:= typeOf[Formula]) new KeYmaeraParser().parseBareExpression(param) match {
      case Some(f: Formula) => f.asInstanceOf[T]
      case None => throw new IllegalArgumentException("Cannot parse " + param)
    } else if (t.tpe =:= typeOf[String]) {
      param.asInstanceOf[T]
    } else if (t.tpe =:= typeOf[Boolean]) {
      param.toBoolean.asInstanceOf[T]
    } else if (t.tpe =:= typeOf[Position]) {
      // The input string will have one of the following forms:
      // ante|succ:N,N,...,N
      val regex = "(ante|succ):(\\d*,?)*"
      assert(param.matches(regex) && !param.endsWith(","),
      "input string should have specified form ante|succ:N,...,N but found " + param)
      val nameAndPos = param.split(":")
      val pos = nameAndPos(1).split(",").map(_.toInt)
      val posInExpr = if (pos.length > 1) PosInExpr(pos.splitAt(1)._2.toList) else HereP
      if (param.startsWith("ante:")) new AntePosition(pos(0), posInExpr).asInstanceOf[T]
      else new SuccPosition(pos(0), posInExpr).asInstanceOf[T]
    } else if (t.tpe =:= typeOf[Variable]) {
      Variable(param, None, Real).asInstanceOf[T]
    } else if (t.tpe =:= typeOf[Term]) new KeYmaeraParser().parseBareTerm(param) match {
        case Some(t: Term) => t.asInstanceOf[T]
        case None => throw new IllegalArgumentException("Cannot parse " + param)
    } else throw new IllegalArgumentException("Unknown parameter type")
  }

}
