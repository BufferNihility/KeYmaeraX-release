Differential Dynamic Logic
==========================

Grammar of Concrete Syntax
--------------------------

The grammar for the concrete syntax of differential dynamic logic is given below in order of ascending precedence, i.e. operators towards the end of the grammar bind stronger than previous operators. Unary operators bind stronger than binary operators, which is why unary operators come later. Also ; binds stronger than ++ and & stronger than | stronger than both -> and <->.
Associativity is left-associative, i.e. x-y-z is (x-y)-z unless noted otherwise.

==Terms==

    T ::= x | x' | num | ∙ | f(T) | T+T | T-T | T*T | T/T | T^T | -T | (T)

with `T^T` right-associative, i.e. x^4^2 is (x^4)^2

==Formulas==

    F ::= T=T | T!=T | T>=T | T>T | T<=T | T<T | p(T) | C{F} | ⎵
        | F<->F | F->F | F|F | F&F
        | \forall x F | \exists x F | [P]F | <P>F | !F| (F)

with `->` right-associative and no precedence for `->` versus `<->`

==Programs==

    P ::= a | x:=T | x':=T | ?F | {D&F} | P++P | P;P | P* | {P}

==Differential programs==

    D ::= c | x'=T | D,D

with `D,D` right-associative

Parser challenge:  < ? p > q > p > 1