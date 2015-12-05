angular.module('sequent', ['ngSanitize','formula'])
  .directive('k4Sequent', function() {
    return {
        restrict: 'AE',
        scope: {
            userId: '=',
            proofId: '=',
            nodeId: '=',
            goalId: '=',
            sequent: '=',
            readOnly: '=?',
            collapsed: '=?',
            onUseAt: '&'
        },
        link: function($scope, $sce, $uibModal, $http, $cookies, Tactics) {
            // TODO should issue events other controllers can subscribe to
            $scope.handleFormulaClick = function(f,isAnte) {
                var modalInstance = $uibModal.open({
                  templateUrl: 'partials/proofruledialog.html',
                  controller: 'ProofRuleDialogCtrl',
                  size: 'lg',
                  resolve: {
                    proofId: function() { return $scope.proofId; },
                    nodeId: function() { return $scope.nodeId; },
                    formula: function() { return f; },
                    isAnte: function() { return isAnte; }
                  }
                });
            };

            $scope.applyTacticsByName = function(tName) {
                $scope.applyTacticsOnFormulaByName('sequent', tName)
            }
            $scope.applyTacticsOnFormulaByName = function(formula, tName) {
                var uri = 'proofs/user/' + $cookies.get('userId') + '/' + $scope.proofId + '/nodes/' + $scope.nodeId
                        + '/formulas/' + formula + '/tactics'
                $http.post(uri + "/runByName/" + tName)
                        .success(function(data) {
                    var dispatchedTacticId = data.tacticInstId;
                    Tactics.addDispatchedTactics(dispatchedTacticId);
                    Tactics.getDispatchedTacticsNotificationService().broadcastDispatchedTactics(dispatchedTacticId);
                });
            }

            $scope.getCounterExample = function() {
                $uibModal.open({
                    templateUrl: 'partials/counterExample.html',
                    controller: 'counterExampleCtrl',
                    size: 'lg',
                    resolve: {
                      proofId: function() { return $scope.proofId; },
                      nodeId: function() { return $scope.nodeId; }
                    }
                    });
            }

            $scope.handleTurnstileClick = function() {
                var modalInstance = $uibModal.open({
                  templateUrl: 'partials/proofruledialog.html',
                  controller: 'ProofRuleDialogCtrl',
                  size: 'lg',
                  resolve: {
                    proofId: function() { return $scope.proofId; },
                    nodeId: function() { return $scope.nodeId; },
                    formula: function() { return undefined; },
                    isAnte: function() { return undefined; }
                  }
                });
            }

            $scope.onAxiom = function(formulaId, axiomId) { $scope.onUseAt({formulaId: formulaId, axiomId: axiomId}); }
        },
        templateUrl: 'partials/collapsiblesequent.html'
    };
  });