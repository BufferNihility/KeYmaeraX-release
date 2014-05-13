KeYmaera4
=========

Repository for the reimplementation of KeYmaera.
This reimplementation should include parallel proof search and a simpler
language to define strategies. Furthermore, there should be support for user
defined strategies.

Building
========

We use the Scala Build Tool (sbt). Installation instructions are available
at the link following:

http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html

Instructions are available at that website as well. Briefly, run "sbt" in
this directory to get an SBT prompt and then run "run" to run a main method
or "test" to run the tests.

To make sure sbt does not throw java.lang.OutOfMemoryError: PermGen space
  edit ~/.sbtconfig
  export SBT_OPTS=-XX:MaxPermSize=256M 

Source code is in src/main/scala and tests are in src/test/scala.

Configuration
=============

If KeYmaera has difficulties finding the MathKernel throwing com.wolfram.jlink.MathLinkException: Could not find MathLink executable
then choose some directory, say, ABC in your path and on Mac OS X run
ln -s /Applications/Mathematica.app/Contents/MacOS/MathKernel ABC/math

Specification
=============

The goal of KeYmaera4 is to implement the proof calculus of differential dynamic logic in a way that is amenable to soundness ensurance by way of a small trusted LCF-style core while still being amenable to automatic theorem proving.
Differential dynamic logic and its Hilbert-type and sequent proof calculi have been described and specified in more detail in:

André Platzer.
Logics of dynamical systems.
ACM/IEEE Symposium on Logic in Computer Science, LICS 2012, June 25–28, 2012, Dubrovnik, Croatia, pages 13-24. IEEE 2012.

André Platzer.
Differential dynamic logic for hybrid systems.
Journal of Automated Reasoning, 41(2), pages 143-189, 2008.

André Platzer.
Logical Analysis of Hybrid Systems: Proving Theorems for Complex Dynamics.
Springer, 2010. 426 p. ISBN 978-3-642-14508-7. 

André Platzer.
Differential dynamic logic for verifying parametric hybrid systems.
In Nicola Olivetti, editor, Automated Reasoning with Analytic Tableaux and Related Methods, International Conference, TABLEAUX 2007, Aix en Provence, France, July 3-6, 2007, Proceedings, volume 4548 of LNCS, pages 216-232. Springer, 2007. 


The advanced proof techniques of differential invariants, differential cuts, and differential ghosts are described and specified in

André Platzer.
The structure of differential invariants and differential cut elimination.
Logical Methods in Computer Science, 8(4), pages 1-38, 2012. 

A secondary goal of KeYmaera4 is to also make it possible to implement extensions of differential dynamic logic, such as quantified differential dynamic logic, which, along with its proof calculus, has been described and specified in

André Platzer.
A complete axiomatization of quantified differential dynamic logic for distributed hybrid systems.
Logical Methods in Computer Science, 8(4), pages 1-44, 2012.
Special issue for selected papers from CSL'10. 

André Platzer.
Dynamic logics of dynamical systems.
May 2012.
arXiv:1205.4788

Jan-David Quesel and André Platzer.
Playing hybrid games with KeYmaera.
In Bernhard Gramlich, Dale Miller, and Ulrike Sattler, editors, Automated Reasoning, Sixth International Joint Conference, IJCAR 2012, Manchester, UK, Proceedings, volume 7364 of LNCS, pages 439-453. Springer, June 2012

Background & References
=======================

Background material and more material can be found at

http://symbolaris.com/pub/

http://symbolaris.com/info/KeYmaera.html
