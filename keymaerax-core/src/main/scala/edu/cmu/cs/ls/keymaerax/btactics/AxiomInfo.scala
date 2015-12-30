package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax.btactics.AxiomInfo.AxiomNotFoundException
import edu.cmu.cs.ls.keymaerax.core.{Axiom, Formula}

/**
  * Since axioms are always referred to by their names (which are strings), we have the following problems:
  * 1) It's hard to keep everything up to date when a new axiom is added
  * 2) We don't get any static exhaustiveness checking when we case on an axiom
  *
  * AxiomInfo exists to help fix that. An AxiomInfo is just a collection of per-axiom information. The tests for
  * this object dynamically ensure it is exhaustive with respect to AxiomBase and DerivedAxioms. By adding a new
  * field to AxiomInfo you can ensure that all new axioms will have to have that field.
  * Created by bbohrer on 12/28/15.
  */
object AxiomInfo {
  case class AxiomNotFoundException(axiomName: String) extends Exception

  def apply(axiomName: String): AxiomInfo = {
    axiomName match {
      case "chain rule" => new AxiomInfo("chain rule", "o'")
      case "V vacuous" => new AxiomInfo("V vacuous", "V")
      case "K modal modus ponens" => new AxiomInfo("K modal modus ponens", "K")
      case "I induction" => new AxiomInfo("I induction", "I")
      case "all instantiate" => new AxiomInfo("all instantiate", "alli")
      case "all eliminate" => new AxiomInfo("all eliminate", "alle")
      case "exists eliminate" => new AxiomInfo("exists eliminate", "existse")
      case "vacuous all quantifier" => new AxiomInfo("vacuous all quantifier", "Vall")
      case "vacuous exists quantifier" => new AxiomInfo("vacuous exists quantifier", "Vexists")
      case "all dual" => new AxiomInfo("all dual", "alld")
      case "exists dual" => new AxiomInfo("exists dual", "existsd")
      case "const congruence" => new AxiomInfo("const congruence", "CCE")
      case "const formula congruence" => new AxiomInfo("const formula congruence", "CCQ")
      // [a] modalities and <a> modalities
      case "<> dual" => new AxiomInfo("<> dual", "<.>")
      case "[] dual" => new AxiomInfo("[] dual", "[.]")
      case "[:=] assign" => new AxiomInfo("[:=] assign", "[:=]")
      case "<:=> assign" => new AxiomInfo("<:=> assign", "<:=>")
      case "[':=] differential assign" => new AxiomInfo("[':=] differential assign", "[':=]")
      case "<':=> differential assign" => new AxiomInfo("<':=> differential assign", "<':=>")
      case "[:=] assign equational" => new AxiomInfo("[:=] assign equational", "[:=]=")
      case "<:=> assign equational" => new AxiomInfo("<:=> assign equational", "<:=>=")
      case "[:=] assign update" => new AxiomInfo("[:=] assign update", "[:=]")
      case "<:=> assign update" => new AxiomInfo("<:=> assign update", "<:=>")
      case "[:*] assign nondet" => new AxiomInfo("[:*] assign nondet", "[:*]")
      case  "<:*> assign nondet" => new AxiomInfo("<:*> assign nondet", "<:*>")
      case "[?] test"    => new AxiomInfo("[?] test", "[?]")
      case "<?> test"    => new AxiomInfo("<?> test", "<?>")
      case "[++] choice" => new AxiomInfo("[++] choice", "[++]")
      case "<++> choice" => new AxiomInfo("<++> choice", "<++>")
      case "[;] compose" => new AxiomInfo("[;] compose", "[;]")
      case "<;> compose" => new AxiomInfo("<;> compose", "<;>")
      case "[*] iterate" => new AxiomInfo("[*] iterate", "[*]")
      case "<*> iterate" => new AxiomInfo("<*> iterate", "<*>")

      case "DW"              => new AxiomInfo("DW", "DW")
      case "DW differential weakening" => new AxiomInfo("DW differential weakening", "DW")
      case "DC differential cut" => new AxiomInfo("DC differential cut", "DC")
      case "DE differential effect system" => new AxiomInfo("DE differential effect system", "DE")
      case "DE differential effect" => new AxiomInfo("DE differential effect", "DE")
      case "DE differential effect (system)" => new AxiomInfo("DE differential effect (system)", "DE")
      case "DI differential invariant" => new AxiomInfo("DI differential invariant", "DI")
      case "DG differential ghost" => new AxiomInfo("DG differential ghost", "DG")
      case "DG differential Lipschitz ghost system" => new AxiomInfo("DG differential Lipschitz ghost system", "DG")
      case "DG differential pre-ghost" => new AxiomInfo("DG differential pre-ghost", "DG")
      case "DG++ System" => new AxiomInfo("DG++ System", "DG++")
      case "DG++" => new AxiomInfo("DG++", "DG++")
      case ", commute" => new AxiomInfo(", commute", ",")
      case "DS differential equation solution" => new AxiomInfo("DS differential equation solution", "DS")
      case "Dsol& differential equation solution" => new AxiomInfo("Dsol& differential equation solution", "DS&")
      case "Dsol differential equation solution" => new AxiomInfo("Dsol differential equation solution", "DS")
      case "DS& differential equation solution" => new AxiomInfo("DS& differential equation solution", "DS&")
      case "DX differential skip" => new AxiomInfo("DX differential skip", "DX")
      case "DX diamond differential skip" => new AxiomInfo("DX diamond differential skip", "DX")
      // Derivatives
      case "&' derive and" => new AxiomInfo("&' derive and", "&'")
      case "|' derive or" => new AxiomInfo("|' derive or", "|'")
      case "->' derive imply" => new AxiomInfo("->' derive imply", "->'")
      case "forall' derive forall" => new AxiomInfo("forall' derive forall", "forall'")
      case "exists' derive exists" => new AxiomInfo("exists' derive exists", "exists'")
      case "c()' derive constant fn" => new AxiomInfo("c()' derive constant fn", "c()'")
      case "=' derive ="   => new AxiomInfo("=' derive =", "='")
      case ">=' derive >=" => new AxiomInfo(">=' derive >=", ">='")
      case ">' derive >"   => new AxiomInfo(">' derive >", ">'")
      case "<=' derive <=" => new AxiomInfo("<=' derive <=", "<='")
      case "<' derive <"   => new AxiomInfo("<' derive <", "<'")
      case "!=' derive !=" => new AxiomInfo("!=' derive !=", "!='")
      case "-' derive neg"   => new AxiomInfo("-' derive neg", "-'")
      case "+' derive sum"   => new AxiomInfo("+' derive sum", "+'")
      case "-' derive minus" => new AxiomInfo("-' derive minus", "-'")
      case "*' derive product" => new AxiomInfo("*' derive product", "*'")
      case "/' derive quotient" => new AxiomInfo("/' derive quotient", "/'")
      case "^' derive power" => new AxiomInfo("^' derive power", "^'")
      case "x' derive variable" => new AxiomInfo("x' derive variable", "x'")
      case "x' derive var"   => new AxiomInfo("x' derive var", "x'")

      // derived axioms
      case "' linear" => new AxiomInfo("' linear", "l'")
      case "' linear right" => new AxiomInfo("' linear right", "l'")
      case "!& deMorgan" => new AxiomInfo("!& deMorgan", "!&")
      case "!| deMorgan" => new AxiomInfo("!| deMorgan", "!|")
      case "!-> deMorgan" => new AxiomInfo("!-> deMorgan", "!->")
      case "!<-> deMorgan" => new AxiomInfo("!<-> deMorgan", "!<->")
      case "!all" => new AxiomInfo("!all", "!all")
      case "!exists" => new AxiomInfo("!exists", "!exists")
      case "![]" => new AxiomInfo("![]", "![]")
      case "!<>" => new AxiomInfo("!<>", "!<>")
      case "[] split" => new AxiomInfo("[] split", "[]&")
      case "<> split" => new AxiomInfo("<> split", "<>|")
      case "[] split left" => new AxiomInfo("[] split left", "[]&<-")
      case "[] split right" => new AxiomInfo("[] split right", "[]&->")
      case "<*> approx" => new AxiomInfo("<*> approx", "<*> approx")
      case "<*> stuck" => new AxiomInfo("<*> stuck", "<*> stuck")
      case "<'> stuck" => new AxiomInfo("<'> stuck", "<'> stuck")
      case "[] post weaken" => new AxiomInfo("[] post weaken", "[]PW")
      case "+<= up" => new AxiomInfo("+<= up", "+<=")
      case "-<= up" => new AxiomInfo("-<= up", "-<=")
      case "<=+ down" => new AxiomInfo("<=+ down", "<=+")
      case "<=- down" => new AxiomInfo("<=- down", "<=-")
      case "<-> reflexive" => new AxiomInfo("<-> reflexive", "<->R")
      case "-> distributes over &" => new AxiomInfo("-> distributes over &", "->&")
      case "-> distributes over <->" => new AxiomInfo("-> distributes over <->", "-><->")
      case "-> weaken" => new AxiomInfo("-> weaken", "->W")
      case "!! double negation" => new AxiomInfo("!! double negation", "!!")
      case ":= assign dual" => new AxiomInfo(":= assign dual", ":=D")
      case "[:=] vacuous assign" => new AxiomInfo("[:=] vacuous assign", "V[:=]")
      case "<:=> vacuous assign" => new AxiomInfo("<:=> vacuous assign", "V<:=>")
      case "[*] approx" => new AxiomInfo("[*] approx", "[*] approx")
      case "exists generalize" => new AxiomInfo("exists generalize", "existsG")
      case "all substitute" => new AxiomInfo("all substitute", "allS")
      case "V[:*] vacuous assign nondet" => new AxiomInfo("V[:*] vacuous assign nondet", "V[:*]")
      case "V<:*> vacuous assign nondet" => new AxiomInfo("V<:*> vacuous assign nondet", "V<:*>")
      case "Domain Constraint Conjunction Reordering" => new AxiomInfo("Domain Constraint Conjunction Reordering", "DCCR") //@todo shortname
      case "& commute" => new AxiomInfo("& commute", "&C")
      case "& associative" => new AxiomInfo("& associative", "&A")
      case "-> expand" => new AxiomInfo("-> expand", "->E")
      case "-> tautology" => new AxiomInfo("-> tautology", "->taut")
      case "\\forall->\\exists" => new AxiomInfo("\\forall->\\exists", "all->exists")
      case "->true" => new AxiomInfo("->true", "->T")
      case "true->" => new AxiomInfo("true->", "T->")
      case "&true" => new AxiomInfo("&true", "&T")
      case "true&" => new AxiomInfo("true&", "T&")
      case "0*" => new AxiomInfo("0*", "0*")
      case "0+" => new AxiomInfo("0+", "0+")
      case "= reflexive" => new AxiomInfo("= reflexive", "=R")
      case "* commute" => new AxiomInfo("* commute", "*C")
      case "= commute" => new AxiomInfo("= commute", "=C")
      case "<=" => new AxiomInfo("<=", "<=")
      case "= negate" => new AxiomInfo("= negate", "!!=")
      case "!= negate" => new AxiomInfo("!= negate", "! =")
      case "! <" => new AxiomInfo("! <", "!<")
      case "! >" => new AxiomInfo("! >", "!>")
      case "< negate" => new AxiomInfo("< negate", "!<=")
      case ">= flip" => new AxiomInfo(">= flip", ">=F")
      case "> flip" => new AxiomInfo("> flip", ">F")
      case "<" => new AxiomInfo("<", "<")
      case ">" => new AxiomInfo(">", ">")
      case "abs" => new AxiomInfo("abs", "abs")
      case "min" => new AxiomInfo("min", "min")
      case "max" => new AxiomInfo("max", "max")
      case "*<= up" => new AxiomInfo("*<= up", "*<=")
      case "1Div<= up" => new AxiomInfo("1Div<= up", "1/<=")
      case "Div<= up" => new AxiomInfo("Div<= up", "/<=")
      case "<=* down" => new AxiomInfo("<=* down", "<=*")
      case "<=1Div down" => new AxiomInfo("<=1Div down", "<=1/")
      case "<=Div down" => new AxiomInfo("<=Div down", "<=/")
      case "! !=" => new AxiomInfo("! !=", "!!=")
      case "! =" => new AxiomInfo("! =", "! =")
      case "! <=" => new AxiomInfo("! <=", "!<=")
      case "* associative" => new AxiomInfo("* associative", "*A")
      case "* commutative" => new AxiomInfo("* commutative", "*C")
      case "* inverse" => new AxiomInfo("* inverse", "*i")
      case "* closed" => new AxiomInfo("* closed", "*c")
      case "* identity" => new AxiomInfo("* identity", "*I")
      case "+ associative" => new AxiomInfo("+ associative", "+A")
      case "+ commutative" => new AxiomInfo("+ commutative", "+C")
      case "+ inverse" => new AxiomInfo("+ inverse", "+i")
      case "+ closed" => new AxiomInfo("+ closed", "+c")
      case "positivity" => new AxiomInfo("positivity", "Pos")
      case "distributive" => new AxiomInfo("distributive", "*+")
      case "all distribute" => new AxiomInfo("all distribute", "Dall")
      case "[]~><> propagation" => new AxiomInfo("[]~><> propagation", "[]~><>")
      case "K1" => new AxiomInfo("K1", "K1")
      case "K2" => new AxiomInfo("K2", "K2")
      case "P1" => new AxiomInfo("P1", "P1")
      case "P2" => new AxiomInfo("P2", "P2")
      case "P3" => new AxiomInfo("P3", "P3")
      case "P9" => new AxiomInfo("P9", "P9")
      case "P10" => new AxiomInfo("P10", "P10")
      // tactics for unit tests
      case "exists dual dummy" => new AxiomInfo("exists dual dummy", "DUMMY")
      case "all dual dummy" => new AxiomInfo("all dual dummy", "DUMMY")
      case "all dual dummy 2" => new AxiomInfo("all dual dummy 2", "DUMMY")
      case "+id' dummy" => new AxiomInfo("+id' dummy", "DUMMY")
      case "+*' reduce dummy" => new AxiomInfo("+*' reduce dummy", "DUMMY")
      case "+*' expand dummy" => new AxiomInfo("+*' expand dummy", "DUMMY")
      case "^' dummy" => new AxiomInfo("^' dummy", "DUMMY")
      case _ => throw new AxiomNotFoundException(axiomName)
    }
  }
}
/** The short name for an axiom is a string intended for use in the UI where space is a concern (e.g. when
  * displaying tree-style proofs). Since the goal is to be as short as possible, they are not required to be
  * unique, but should still be as suggestive as possible of what the axiom does.*/
//@todo could case class, right?
class AxiomInfo (val canonicalName: String, val shortName: String) {
  def formula: Formula =
    DerivedAxioms.derivedAxiomMap.get(canonicalName) match {
      case Some(fml) => fml._1
      case None =>
        Axiom.axioms.get(canonicalName) match {
          case Some(fml) => fml
          case None => throw new AxiomNotFoundException("No formula for axiom " + canonicalName)
        }
    }
}