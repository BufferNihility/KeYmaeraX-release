/**
* Copyright (c) Carnegie Mellon University. CONFIDENTIAL
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.tools

import java.io.{InputStream, FileOutputStream, FileWriter, File}
import java.nio.channels.Channels
import java.util.Locale

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.{ParseException, KeYmaeraXParser}
import scala.collection.immutable
import scala.sys.process._

/**
 * Created by ran on 3/27/15.
 * @author Ran Ji
 */
class Z3Solver extends SMTSolver {

  private val pathToZ3 : String = {
    val z3TempDir = System.getProperty("user.home") + File.separator + ".keymaerax"
    if(!new File(z3TempDir).exists) new File(z3TempDir).mkdirs
    val osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
    if(osName.contains("windows") && new File(z3TempDir+"z3.exe").exists()) {
      z3TempDir+"z3.exe"
    } else if(new File(z3TempDir+"z3").exists()) {
      z3TempDir+"z3"
    } else {
      val osArch = System.getProperty("os.arch")
      var resource : InputStream = null
      if(osName.contains("mac")) {
        if(osArch.contains("64")) {
          resource = this.getClass.getResourceAsStream("/z3/mac64/z3")
        }
      } else if(osName.contains("windows")) {
        if(osArch.contains("64")) {
          resource = this.getClass.getResourceAsStream("/z3/windows64/z3.exe")
        } else {
          resource = this.getClass.getResourceAsStream("/z3/windows32/z3.exe")
        }
      } else if(osName.contains("linux")) {
        if(osArch.contains("64")) {
          resource = this.getClass.getResourceAsStream("/z3/ubuntu64/z3")
        } else {
          resource = this.getClass.getResourceAsStream("/z3/ubuntu32/z3")
        }
      } else if(osName.contains("freebsd")) {
        if(osArch.contains("64")) {
          resource = this.getClass.getResourceAsStream("/z3/freebsd64/z3")
        }
      } else {
        throw new Exception("Z3 solver is currently not supported in your operating system.")
      }
      if(resource == null)
        throw new Exception("Could not find Z3 in classpath: " + System.getProperty("user.dir"))
      val z3Source = Channels.newChannel(resource)
      val z3Temp = {
        if(osName.contains("windows")) {
          new File(z3TempDir, "z3.exe")
        } else {
          new File(z3TempDir, "z3")
        }
      }

      // Get a stream to the script in the resources dir
      val z3Dest = new FileOutputStream(z3Temp)
      // Copy file to temporary directory
      z3Dest.getChannel.transferFrom(z3Source, 0, Long.MaxValue)
      val z3AbsPath = z3Temp.getAbsolutePath
      //@todo preexisting files shouldn't be modified permissions
      //@todo what's with Windows?
      Runtime.getRuntime.exec("chmod u+x " + z3AbsPath)
      z3Source.close()
      z3Dest.close()
      assert(new File(z3AbsPath).exists())
      z3AbsPath
    }
  }

  def run(cmd: String) = {
    val output : String = cmd.!!
    println("[Z3 result] \n" + output + "\n")
    // TODO So far does not handle get-model or unsat-core
    val result = {
      //@todo very dangerous code: Example output "sorry I couldn't prove its unsat, no luck today". Variable named unsat notunsat
      //@todo investigate Z3 binding for Scala
      if (output.contains("unsat")) True
        //@todo incorrect answer. It's not equivalent to False just because it's not unsatisfiable. Could be equivalent to x>5
      else if(output.contains("sat")) False
      else if(output.contains("unknown")) False
      else throw new SMTConversionException("Conversion of Z3 result \n" + output + "\n is not defined")
    }
    (output, result)
  }

  def qe(f : Formula) : Formula = {
    qeEvidence(f)._1
  }

  def qeEvidence(f : Formula) : (Formula, Evidence) = {
    val smtCode = SMTConverter(f, "Z3") + "\n(check-sat)\n"
    println("[Solving with Z3...] \n" + smtCode)
    val smtFile = getUniqueSmt2File()
    val writer = new FileWriter(smtFile)
    writer.write(smtCode)
    writer.flush()
    writer.close()
    val cmd = pathToZ3 + " " + smtFile.getAbsolutePath
    val (output, result) = run(cmd)
    result match {
      case f : Formula => (f, new ToolEvidence(immutable.Map("input" -> smtCode, "output" -> output)))
      case _ => throw new Exception("Expected a formula from QE call but got a non-formula expression.")
    }
  }

  def simplify(t: Term) = {
    val smtCode = SMTConverter.generateSimplify(t, "Z3")
//    println("[Simplifying with Z3 ...] \n" + smtCode)
    val smtFile = getUniqueSmt2File()
    val writer = new FileWriter(smtFile)
    writer.write(smtCode)
    writer.flush()
    writer.close()
    val cmd = pathToZ3 + " " + smtFile.getAbsolutePath
    val output: String = cmd.!!
    println("[Z3 simplify result] \n" + output + "\n")
    try {
      KeYmaeraXParser.termParser(output)
    } catch {
      case e: ParseException => t
    }
  }
}
