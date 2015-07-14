package edu.cmu.cs.ls.keymaerax.launcher

import java.io.PrintWriter

import edu.cmu.cs.ls.keymaerax.core.{And, Formula, Sequent, Variable}
import edu.cmu.cs.ls.keymaerax.parser.{KeYmaeraXPrettyPrinter, KeYmaeraXProblemParser, KeYmaeraXParser}
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.Tactic
import edu.cmu.cs.ls.keymaerax.tactics.{Interpreter, TacticWrapper, Tactics, RootNode}
import edu.cmu.cs.ls.keymaerax.tactics.ModelplexTacticImpl.{modelplexControllerMonitorTrafo, modelplexInPlace}
import edu.cmu.cs.ls.keymaerax.tactics.SearchTacticsImpl.locateSucc
import edu.cmu.cs.ls.keymaerax.tools.{Mathematica, KeYmaera}

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
    """Usage: KeYmaeraX [-mathkernel MathKernel(.exe) -jlink path/to/jlinkNativeLib]
      |  -prove filename -tactic filename [-out filename] |
      |  -modelplex filename [-vars var1,var2,...,varn] [-out filename] |
      |  -codegen filename [-format Spiral|C] [-out filename]""".stripMargin

  def main (args: Array[String]) {
    if (args.length == 0 || args==Array("-help") || args==Array("--help") || args==Array("-h")) println(usage)
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
          case option :: tail => println("Unknown option " + option + "\n" + usage); sys.exit(1)
        }
      }

      val options = nextOption(Map(), args.toList)
      require(options.contains('mode))

      initializeProver(options)

      options.get('mode) match {
        case Some("prove") => prove(options)
        case Some("modelplex") => modelplex(options)
        case Some("codegen") =>
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

    if (rootNode.openGoals().isEmpty) {
      val evidence =
        s"""Tool.
          |  input "$input"
          |  tactic "${scala.io.Source.fromFile(tacticFileName).mkString}"
          |  proof ""
          |End.
        """.stripMargin
      val lemmaContent =
        s"""Lemma "${inputFileName.substring(inputFileName.lastIndexOf('/')+1)}".
          | (${KeYmaeraXPrettyPrinter(inputModel)}) <-> true
          |End.
        """.stripMargin

      val pw = new PrintWriter(options.getOrElse('out, inputFileName + ".proof").toString)
      pw.write(lemmaContent + "\n" + evidence)
      pw.close()
    } else {
      // TODO what to to when proof cannot be checked?
    }
  }
}
