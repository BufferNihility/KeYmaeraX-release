﻿Differential Dynamic Logic
==========================

Grammar of Concrete Syntax
--------------------------

The grammar for the concrete syntax of differential dynamic logic is given below in order of ascending precedence, i.e. operators towards the beginning of the grammar bind stronger than later operators. Unary operators bind stronger than binary operators, which is why unary operators come later. Also ; binds stronger than ++ and & stronger than | stronger than both -> and <->. That is precedence is the following order with stronger precedence listed first and equal precedence delimited by `,`

    '   -   ^   *,/   +,-
    '   !   &     |   ->,<-,<->
        *   ;     ++

Associativity is left-associative, i.e. x-y-z is (x-y)-z unless noted otherwise.

==Terms==

    T ::= x | x' | num | ∙ | f(T) | f() | -T | T^T | T*T | T/T | T+T | T-T | (T)' | (T) 

with `T^T` right-associative, i.e. x^4^2 is x^(4^2)

==Formulas==

    F ::= T=T | T!=T | T>=T | T>T | T<=T | T<T | p(T) | p() | C{F} | ⎵
        | !F | \forall x F | \exists x F | [P]F | <P>F 
        | F&F | F|F | F->F | F<->F | true | false | (F)' | (F)

with `->` right-associative, i.e. p()->q()->r() is p()->(q()->r()) and no precedence for `->` versus `<->`

==Programs==

    P ::= a; | x:=T; | x':=T; | ?F; | {D&F} | {P}* | P P | P++P | {P}

with `++` and the invisible `;` in P P right-associative, i.e. x:=1;x:=2;x:=3; is x:=1;{x:=2;x:=3;}

==Differential Programs==

    D ::= c | x'=T | D,D

with `D,D` right-associative, i.e. x'=1,y'=2,z'=3 is x'=1,(y'=2,z'=3)

==Types==

It is considered an error to use the same name with different types in different places, such as in `x() -> [x:=x(x);]x()>x(x,x())`


==Contrast to Theory==

The grammar of the concrete syntax for programs is to be contrasted with the abstract dL grammar in theory:

    P ::= a | x:=T | x':=T | ?F | D&F | P* | P;P | P++P | (P)

with the rather visible `;` in P;P right-associative (and `++` being still right-associative).
