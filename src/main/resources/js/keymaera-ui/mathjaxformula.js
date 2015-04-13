angular.module('mathjaxformula', ['ngSanitize','mathjaxbind'])
  .directive('k4Mathjaxformula', function() {
    return {
        restrict: 'AE',
        scope: {
            formula: '=',
            delimiter: '='
        },
        controller: function($scope, $sce, $attrs) {
            function needsParens(parent, child) {
                var precedence =
                  [
                  //Terms.
                  "add" ,
                  "subtract" ,
                  "multiply" ,
                  "divide" ,
                  "exp" ,
                  "neg" ,
                  "function" ,
                  "programconstant" , //real-valued.
                  "number"   ,
                  //Formulas
                  "equiv" ,
                  "imply" ,
                  "or" ,
                  "and" ,
                  "not" ,
                  "boxmodality"  ,
                  "diamondmodality" ,
                  "modality" ,
                  "forall" ,
                  "exists" ,
                  "equal" ,
                  "notEqual" ,
                  "lt"  ,
                  "leq" ,
                  "geq" ,
                  "gt" ,
                  "formuladerivative" ,
                  "predicateconstant" ,
                  //Programs.
                  "Choice" ,
                  "Sequence" ,
                  "Loop" ,
                  "Assign" ,
                  "NDetAssign" ,
                  "Test" ,
                  "NFODEProduct" ,
                  "ODEProduct" ,
                  "AtomicODE" ,
                  // Atoms
                  "ProgramConstant" ,
                  "ContEvolveProgramConstant",
                  "applypredicate" ,
                  "true" ,
                  "false" ,
                  "apply",
                  "derivative" ,
                  "Variable",
                  "Number"].reverse()

                var childPrecedence = precedence.indexOf(child.name);
                var parentPrecedence = precedence.indexOf(parent.name);
                return childPrecedence > parentPrecedence;
            }

            function parensIfNeeded(parent, child, depth) {
                var parens = [ "(", ")" ]
//                  if(child.isInstanceOf[Program]) ["{","}"]
//                  else ["(",")"]

                if(needsParens(parent, child)) {
                  return parens[0] + parseFormulaHelper(child, depth) + parens[1]
                } else {
                  return parseFormulaHelper(child, depth)
                }
            }

            // Recursively generate LaTeX
            function parseFormulaHelper(json, depth) {
                var items = [];
                if (json.hasOwnProperty("children") && $.isArray(json.children)) {
                    var c = json.children;
                    var content;
                    switch (json.name) {
                        case "not":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            content = " \\neg " + left;
                            break;

                        case "and":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\wedge " + right;
                            break;

                        case "or":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\vee " + right;
                            break;

                        case "imply":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\rightarrow " + right;
                            break;

                        case "equiv":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\equiv " + right;
                            break;

                        case "lt":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\lt " + right;
                            break;

                        case "leq":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\le " + right;
                            break;

                        case "equals":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " = " + right;
                            break;

                        case "notEquals":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\neq " + right;
                            break;

                        case "geq":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\ge " + right;
                            break;

                        case "gt":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " \\gt " + right;
                            break;

                        case "neg":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            content = " -" + left;
                            break;

                        case "add":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " + " + right;
                            break;

                        case "subtract":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " - " + right;
                            break;

                        case "multiply":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " " + right;
                            break;

                        case "divide":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = "\\frac{" + left + "}{" + right + "}";
                            break;

                        case "exp":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + "^" + right;
                            break;

                        case "forall":
                            var vars = json.variables[0];
                            for (var i = 1; i < json.variables.length; i++) {
                                vars = vars + "," + json.variables[i];
                            }
                            content = "\\forall " + vars + ". (" + parseFormulaHelper(c[0], depth + 1) + ")"
                            break;

                        case "exists":
                            var vars = json.variables[0];
                            for (var i = 1; i < json.variables.length; i++) {
                                vars = vars + "," + json.variables[i];
                            }
                            content = "\\exist " + vars + ". (" + parseFormulaHelper(c[0], depth + 1) + ")"
                            break;

                        case "boxmodality":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = "\\left[" + left + "\\right]" + right;
                            break;

                        case "Assign":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " := " + right;
                            break;

                        case "NDetAssign":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            content = left + ":= *";
                            break;

                        case "Test":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            content = "?" + left;
                            break;

                        case "Loop":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            content = "\\left\\{" + left + "\\right\\}^*";
                            break;

                        case "Sequence":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + ";~" + right;
                            break;

                        case "Choice":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " ~\\cup~ " + right;
                            break;

                        case "AtomicODE":
                            var x = parensIfNeeded(json, c[0], depth + 1);
                            var theta = parensIfNeeded(json, c[1], depth + 1);
                            content = x + " = " + theta;
                            break;

                        case "ODEProduct":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            if (c[1].name != "EmptyODE") {
                              content = left + ", " + right;
                            } else {
                              content = left;
                            }
                            break;

                        case "NFODEProduct":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            var right = parensIfNeeded(json, c[1], depth + 1);
                            content = left + " ~\\&~ " + right;
                            break;

                        case "formuladerivative":
                            content = "(" + parseFormulaHelper(c[0], depth) + ")'"
                            break;

                        case "derivative":
                            var left = parensIfNeeded(json, c[0], depth + 1);
                            content = "\\dot{" + left + "}";
                            break;

                        case "Anything": content = "?"; break;
                        case "Nothing": content = ""; break;

                        case "apply":
                            var name = c[0]
                            if (c[1].name != "Nothing") {
                              content = name + parensIfNeeded(json, c[1], depth + 1);
                            } else {
                              content = name + "()";
                            }
                            break;

                        default:
                            var seqs = [];
                            for (var i = 0; i < c.length; i++) {
                                seqs.push(parseFormulaHelper(c[i]));
                            }
                            content = json.name + "(" + seqs.join(", ") + ")";
                            break;
                    }
                    items.push(content);
                } else {
                    items.push(json.name);
                }
                return items.join("");
            }

            $scope.parseFormula = function(json, delimiter) {
                if (delimiter == "display") { return "\\[" + parseFormulaHelper(json, 0) + "\\]"; }
                else { return "\\(" + parseFormulaHelper(json, 0) + "\\)"; }
            };
        },
        template: '<span k4-mathjaxbind="parseFormula(formula, delimiter)"></span>'
    };
  });