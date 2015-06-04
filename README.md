KeYmaera4
=========

Repository for the clean-slate reimplementation of KeYmaera,
a theorem prover for hybrid systems.
KeYmaera 4 combines a small isolated prover core responsible
for sound implementations of the fundamental proof rules as well
as a flexible tactic language layer on top that makes it easy to
provide user-defined proof strategies. KeYmaera 4 is also
intended to include possibilities for parallel proof search.
The prover front-end, middleware, and back-end are separated by
design.

Installation
============

The easiest way to run KeYmaera4 from source is to install its dependencies and run HyDRA via SBT:

    * Install MongoDB. Be sure that that your machine is behind a firewall and/or edit the MongoDB configuration file so that the server binds to the loopback address.
    * Install Scala (and the JRE).
    * Install SBT and following the instructions in the Building section.

Building
========

We use the Scala Build Tool (sbt). Installation instructions are available
at the link following:

http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html

Instructions are available at that website as well. Briefly, type

    sbt compile

to compile the source code. First-time compilation may take a while, since it downloads all necessary libraries,
including Scala itself. The build file includes paths to the Mathematica JLink jar, for example for Mac as follows.

    unmanagedJars in Compile += file("/Applications/Mathematica.app/SystemFiles/Links/JLink/JLink.jar")

If JLink.jar is in a non-default location on your computer, add a new line to build.sbt.

Type

    sbt test

to run the regression test case suite. To run the KeYmaera parser use:

    sbt run

If you run into problems during the compilation process, use "sbt clean" for a fresh start to remove stale files.

To make sure sbt does not throw java.lang.OutOfMemoryError: PermGen space

    edit ~/.sbtconfig
    SBT_OPTS="-XX:MaxPermSize=256M"

The Wiki contains extended build instructions and solutions to toher
common sbt problems:

https://github.com/LS-Lab/KeYmaera4/wiki/Building-Instructions

IntelliJ IDEA
=============

Installation
- Install IntelliJ IDEA
- Install the Scala plugin

Project Setup
- Create a new Scala project, backed by SBT
- Select a JDK as your project SDK, add a new one if not previously added
- Check update automatically (not checked by default), so that updates to build.sbt are reflected automatically in the project

Create a new run configuration of type Application.
- Main class: edu.cmu.cs.ls.keymaerax.hydra.Boot
- Set the working directory to the project path
- Use the classpath of your project module

Front End
=========

    mongod --config /usr/local/etc/mongod.conf
    sbt "~ re-start"
    open http://localhost:8090/index_bootstrap.html

The option re-start ensures that the server is restarted whenever a source file changes.

Errors related to JLinkNative Library are caused by Java 1.8 in combination with Mathematica 9.
Either run using Java 1.7, or update to Mathematica 10.

KeYmaera is successfully started when you see the following console output

    Bound to localhost/127.0.0.1:8090

Proof Tree Viewer
=================

To view proof trees converted to JSON by TermTests.print you can use
jsgui/proofviewer.html

Simply deploy proofviewer.html to any webserver and put the JSON
descirption into resources/proof.json in the same directory on the
webserver. Please note that JSON sources cannot be read from file-urls
and thus have to be accessed through a webserver. There are no
specific requirements for the webserver since it is only servering
html and the JavaScript part is interpreted in the browser on the
client anyway.

Source Layout
=============

build.sbt - SBT configuration file

jsgui/ - The javascript front-end

src/ - Source code directory

src/main/scala - source code (edu.cmu.cs.ls.keymaerax)

Within the keymaera namespace, code is separated according to functionality:

    .core    - Soundness-critical core
    .parser  - Parsing and pretty printing
    .tactics - Tactic framework, including tactic implementations and the scheduler
    .tools   - Arithmetic back-ends

src/test/scala - tests run by `sbt test`

The wiki contains an introduction to the testing framework:
https://github.com/LS-Lab/KeYmaera4/wiki/How-to-Add-Tests
http://www.scalatest.org/user_guide

target/ - Created by sbt on first compilation.

target/scala-2.10/classes/ - Target directory for sbt compilation.

Test Cases
==========

The full test suite can be run by

    sbt test

Selectively running individual test cases within sbt:

    sbt
    sbt>  test-only *USubst*

Or, run on a more fine-grained level within a class use
object MyTest extends Tag("MyTest")

    object MyTest extends Tag("MyTest")
    it should "do something useful" taggedAs(MyTest) in {....}
    it should "do anything useful" taggedAs(MyTest) in {....}
    it should "do more good" taggedAs(MoreTest) in {....}

Then in sbt interactive mode run   

    sbt>  test-only -- -n "MyTest MoreTest"

To inline scala console output alongside the test suite information, first do:

    sbt>  set logBuffered in Test := false

Proof Tree Viewer
=================

To view proof trees converted to JSON by TermTests.print you can use
jsgui/proofviewer.html

Simply deploy proofviewer.html to any webserver and put the JSON
descirption into resources/proof.json in the same directory on the
webserver. Please note that JSON sources cannot be read from file-urls
and thus have to be accessed through a webserver. There are no
specific requirements for the webserver since it is only servering
html and the JavaScript part is interpreted in the browser on the
client anyway.

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

André Platzer. 
Differential game logic. 
August 2014.
arXiv:1408.1980

Jan-David Quesel and André Platzer.
Playing hybrid games with KeYmaera.
In Bernhard Gramlich, Dale Miller, and Ulrike Sattler, editors, Automated Reasoning, Sixth International Joint Conference, IJCAR 2012, Manchester, UK, Proceedings, volume 7364 of LNCS, pages 439-453. Springer, June 2012

Licenses of External Tools
==========================

KeYmaera X distribution contains external tools. A list of tools and their licenses can be found at

src/main/resources/license/tools_licenses

Background & References
=======================

Background material and more material can be found at

http://symbolaris.com/pub/

http://symbolaris.com/info/KeYmaera.html

