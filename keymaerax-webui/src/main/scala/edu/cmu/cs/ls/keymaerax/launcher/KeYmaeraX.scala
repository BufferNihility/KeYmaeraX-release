package edu.cmu.cs.ls.keymaerax.launcher

import java.io.PrintWriter

import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ClassGenerator
import edu.cmu.cs.ls.keymaerax.core.{And, Formula, Sequent, Variable}
import edu.cmu.cs.ls.keymaerax.parser.{KeYmaeraXPrettyPrinter, KeYmaeraXProblemParser, KeYmaeraXParser}
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.Tactic
import edu.cmu.cs.ls.keymaerax.tactics.{Interpreter, TacticWrapper, Tactics, RootNode}
import edu.cmu.cs.ls.keymaerax.tactics.ModelplexTacticImpl.{modelplexControllerMonitorTrafo, modelplexInPlace}
import edu.cmu.cs.ls.keymaerax.tactics.SearchTacticsImpl.locateSucc
import edu.cmu.cs.ls.keymaerax.tools.{Mathematica, KeYmaera}
import edu.cmu.cs.ls.keymaerax.codegen.{CGenerator, SpiralHeaderGenerator, SpiralMonitorGenerator}


import scala.collection.immutable
import scala.reflect.runtime._
import scala.tools.reflect.ToolBox

/**
 * Created by smitsch on 7/13/15.
 * @author Stefan Mitsch
 */
object KeYmaeraX {

  private type OptionMap = Map[Symbol, Any]

  private val usage =
    """KeYmaera X Prover
      |
      |Usage: KeYmaeraX [-mathkernel MathKernel(.exe) -jlink path/to/jlinkNativeLib]
      |  -prove filename -tactic filename [-out filename] |
      |  -modelplex filename [-vars var1,var2,...,varn] [-out filename] |
      |  -codegen filename [-format Spiral|C] [-out filename]
      |
      |Additional options:
      |  -verify   check the resulting proof certificate (recommended)
      |  -noverify skip checking proof certificates
      |
      |Copyright (c) Carnegie Mellon University.
      |See LICENSE.txt for the conditions of this license.
      |""".stripMargin

  def main (args: Array[String]) {
    println("KeYmaera X Prover\n" +
      "Use option -help for usage information")
    if (args.length == 0 || args==Array("-help") || args==Array("--help") || args==Array("-h")) {println(usage); sys.exit(1)}
    else {
      def makeVariables(varNames: Array[String]): Array[Variable] = {
        varNames.map(vn => KeYmaeraXParser(vn) match {
          case v: Variable => v
          case v => throw new IllegalArgumentException("String " + v + " is not a valid variable name")
        })
      }

      def nextOption(map: OptionMap, list: List[String]): OptionMap = {
        list match {
          case Nil => map
          case "-prove" :: value :: tail => nextOption(map ++ Map('mode -> "prove", 'in -> value), tail)
          case "-modelplex" :: value :: tail => nextOption(map ++ Map('mode -> "modelplex", 'in -> value), tail)
          case "-codegen" :: value :: tail => nextOption(map ++ Map('mode -> "codegen", 'in -> value), tail)
          case "-out" :: value :: tail => nextOption(map ++ Map('out -> value), tail)
          case "-vars" :: value :: tail => nextOption(map ++ Map('vars -> makeVariables(value.split(","))), tail)
          case "-format" :: value :: tail => nextOption(map ++ Map('format -> value), tail)
          case "-tactic" :: value :: tail => nextOption(map ++ Map('tactic -> value), tail)
          case "-mathkernel" :: value :: tail => nextOption(map ++ Map('mathkernel -> value), tail)
          case "-jlink" :: value :: tail => nextOption(map ++ Map('jlink -> value), tail)
          // additional options
          case "-noverify" :: tail => nextOption(map ++ Map('verify -> false), tail)
          case "-verify" :: tail => nextOption(map ++ Map('verify -> true), tail)
          case "-help" :: _ => {println(usage); sys.exit(1)}
          case option :: tail => println("Unknown option " + option + "\n" + usage); sys.exit(1)
        }
      }

      val options = nextOption(Map(), args.toList)
      require(options.contains('mode))

      initializeProver(options)

      options.get('mode) match {
        case Some("prove") => prove(options)
        case Some("modelplex") => modelplex(options)
        case Some("codegen") => codegen(options)
      }

      shutdownProver()
    }
  }

  def initializeProver(options: OptionMap) = {
    val mathematicaConfig =
      if (options.contains('mathkernel) && options.contains('jlink)) Map("linkName" -> options.get('mathkernel).get.toString,
                                                                         "libDir" -> options.get('jlink).get.toString)
      else DefaultConfiguration.defaultMathematicaConfig

    require(mathematicaConfig.contains("linkName") && mathematicaConfig.contains("libDir"),
      if (!options.contains('mathkernel)) "Cannot find Mathematica at default location, please use command line options\n" + usage)

    Tactics.KeYmaeraScheduler = new Interpreter(KeYmaera)
    Tactics.MathematicaScheduler = new Interpreter(new Mathematica)

    Tactics.KeYmaeraScheduler.init(Map())
    Tactics.MathematicaScheduler.init(mathematicaConfig)
  }

  def shutdownProver() = {
    if (Tactics.KeYmaeraScheduler != null) {
      Tactics.KeYmaeraScheduler.shutdown()
      Tactics.KeYmaeraScheduler = null
    }
    if (Tactics.MathematicaScheduler != null) {
      Tactics.MathematicaScheduler.shutdown()
      Tactics.MathematicaScheduler = null
    }
  }

  def modelplex(options: OptionMap) = {
    require(options.contains('in), usage)
    require(options.contains('vars), usage)

    val inputFileName = options.get('in).get.toString
    val input = scala.io.Source.fromFile(inputFileName).mkString
    val inputModel = KeYmaeraXProblemParser(input)

    val mxInputFml = modelplexControllerMonitorTrafo(inputModel, options.get('vars).get.asInstanceOf[Array[Variable]]:_*)
    val tactic = locateSucc(modelplexInPlace(useOptOne=true))
    val rootNode = new RootNode(Sequent(Nil, immutable.IndexedSeq[Formula](), immutable.IndexedSeq(mxInputFml)))
    Tactics.KeYmaeraScheduler.dispatch(new TacticWrapper(tactic, rootNode))

    assert(rootNode.openGoals().size == 1 && rootNode.openGoals().head.sequent.ante.size == 1 &&
      rootNode.openGoals().head.sequent.succ.size == 1, "Modelplex failed to provide a single formula")
    val outputFml = And(rootNode.openGoals().head.sequent.ante.head, rootNode.openGoals().head.sequent.succ.head)
    val output = KeYmaeraXPrettyPrinter(outputFml)

    if (options.contains('verify) && options.get('verify)==true) {
      //@todo check that when assuming the output formula as an additional untrusted lemma, the Provable isProved.
      System.err.println("Cannot yet verify modelplex proof certificates")
    }

    val pw = new PrintWriter(options.getOrElse('out, inputFileName + ".mx").toString)
    pw.write(output)
    pw.close()
  }

  def prove(options: OptionMap) = {
    require(options.contains('in), usage)
    require(options.contains('tactic), usage)

    val tacticFileName = options.get('tactic).get.toString
    val tacticSource = scala.io.Source.fromFile(tacticFileName).mkString

    val cm = universe.runtimeMirror(getClass.getClassLoader)
    val tb = cm.mkToolBox()
    val tacticGenerator = tb.eval(tb.parse(tacticSource)).asInstanceOf[() => Tactic]

    val tactic = tacticGenerator()

    val inputFileName = options.get('in).get.toString
    val input = scala.io.Source.fromFile(inputFileName).mkString
    val inputModel = KeYmaeraXProblemParser(input)
    val rootNode = new RootNode(Sequent(Nil, immutable.IndexedSeq[Formula](), immutable.IndexedSeq(inputModel)))
    Tactics.KeYmaeraScheduler.dispatch(new TacticWrapper(tactic, rootNode))

    if (rootNode.isClosed()) {
      assert(rootNode.openGoals().isEmpty)
      if (options.contains('verify) && options.get('verify)==true) {
        val witness = rootNode.provableWitness
        val proved = witness.proved
        assert(KeYmaeraXParser(input) == proved, "Proved the original problem and not something else")
        println("Proof certificate: Passed")
      } else {
        println("Proof certificate: Skipped")
      }
      //@note printing original input rather than a pretty-print of proved ensures that @invariant annotations are preserved for reproves.
      val evidence =
        s"""Tool.
          |  input "$input"
          |  tactic "${scala.io.Source.fromFile(tacticFileName).mkString}"
          |  proof ""
          |End.
        """.stripMargin
      //@todo why is this of the form bla <-> true instead of just bla?
      val lemmaContent =
        s"""Lemma "${inputFileName.substring(inputFileName.lastIndexOf('/')+1)}".
          | (${KeYmaeraXPrettyPrinter(inputModel)}) <-> true
          |End.
        """.stripMargin

      val pw = new PrintWriter(options.getOrElse('out, inputFileName + ".proof").toString)
      pw.write(lemmaContent + "\n" + evidence)
      pw.close()
    } else {
      assert(!rootNode.isClosed())
      assert(!rootNode.openGoals().isEmpty)
      println("Unsuccessful proof: unfinished")
      System.err.println("Unsuccessful proof: unfinished")
      sys.exit(-1)
      // TODO what to to when proof cannot be checked?
    }
  }

  def codegen(options: OptionMap) = {
    require(options.contains('in), usage)
    require(options.contains('format), usage)

    val inputFileName = options.get('in).get.toString
    val input = scala.io.Source.fromFile(inputFileName).mkString
    val inputFormula = KeYmaeraXParser(input)
    var output = ""

    if(options.get('format).get.toString == "C") {
      val cGen = new CGenerator()
      output = cGen.apply(inputFormula)
      val pw = new PrintWriter(options.getOrElse('out, inputFileName + ".c").toString)
      pw.write(output)
      pw.close()
    } else if(options.get('format).get.toString == "Spiral") {
      val sGen = new SpiralMonitorGenerator
      output = sGen.apply(inputFormula)
      val pw = new PrintWriter(options.getOrElse('out, inputFileName + ".g").toString)
      pw.write(output)
      pw.close()
    } else throw new IllegalArgumentException("-format should be specified and only be C or Spiral")
  }
}
