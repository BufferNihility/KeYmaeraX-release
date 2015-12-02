angular.module('sequentproof', ['ngSanitize','sequent','formula'])
  /**
   * A sequent deduction view focused on a single path through the deduction, with links to sibling goals when
   * branching occurs.
   * {{{
   *      <k4-sequentproof userId="1" proofId="35" nodeId="N1"
                           deductionRoot="..." agenda="..." read-only="false"></k4-sequentproof>
   * }}}
   * @param userId          The user ID; for interaction with the server.
   * @param proofId         The current proof; for interaction with the server.
   * @param nodeId          The node (=task); for interaction with the server.
   * @param goalId          The goal (sequent) for cross-referencing agenda items.
   * @param deductionPath   Identifies the path to the goal (as far as loaded); Array[goalId]
   * @param proofTree       The proof tree, see provingawesome.js for schema.
   * @param agenda          The agenda, see provingawesome.js for schema.
   * @param readOnly        Indicates whether or not the proof steps should allow interaction (optional).
   */
  .directive('k4Sequentproof', ['$http', function($http) {
    /* The directive's internal control. */
    function link(scope, element, attrs) {
      /**
       * Fetches the parent of goal 'goalId' and updates the agenda item 'nodeId' to show an extended sequent
       * (parent appended as previous proof step below deduction view).
       */
      scope.fetchParent = function(goalId) {
        $http.get('proofs/user/' + scope.userId + '/' + scope.proofId + '/' + scope.nodeId + '/' + goalId + '/parent').success(function(data) {
          // add node to proof tree if not already present; otherwise, update node with fetched rule and children
          if (scope.proofTree.nodesMap[data.id] === undefined) {
            scope.proofTree.nodesMap[data.id] = data;
          } else {
            scope.proofTree.nodesMap[data.id].children = data.children;
            scope.proofTree.nodesMap[data.id].rule = data.rule;
          }

          // append parent at the end of the deduction path of all relevant agenda items
          var items = $.map(data.children, function(e) { return scope.agenda.itemsByProofStep(e); }); // JQuery flat map
          $.each(items, function(i, v) {
            var lastPathElem = (v.path.length === 0 ? v.goal : v.path[v.path.length - 1]);
            if ($.inArray(lastPathElem, data.children) < 0) {
              console.error('Expected last path element to be a child of ' + data.id + ', but agenda item ' + v.id +
                ' has ' + lastPathElem.id + ' as last path element');
            } else v.path.push(data.id); });
        });
      }

      /** Pretty prints sequent JSON into HTML */
      scope.tooltip = function(sequent) {
        // TODO call the pretty printer
        return sequent;
      }

      /** Filters sibling candidates: removes this item's goal and path */
      scope.siblingCandidates = function(candidates) {
        var item = scope.agenda.itemsMap[scope.nodeId];
        return candidates.filter(function(e) { return e !== item.goal && item.path.indexOf(e) < 0; });
      }

      scope.onUseAt = function(formulaId, axiomId) {
        $http.get('proofs/user/' + scope.userId + '/' + scope.proofId + '/' + scope.nodeId + '/' + scope.goalId + '/' + formulaId + '/use/' + axiomId).success(function(data) {
          scope.proofTree.nodesMap[data.id] = data;
          scope.proofTree.nodesMap[data.parent].children = [data.id];
          scope.proofTree.nodesMap[data.parent].rule = data.byRule;
          // got new open goal, prepend previous open goal to deduction path
          var item = scope.agenda.itemsMap[scope.nodeId];
          item.path.unshift(item.goal);
          item.goal = data.id;
          scope.goalId = data.id;
        });
      }
    }

    return {
        restrict: 'AE',
        scope: {
            userId: '=',
            proofId: '=',
            nodeId: '=',
            goalId: '=',
            deductionPath: '=',
            proofTree: '=',
            agenda: '=',
            readOnly: '=?'
        },
        link: link,
        templateUrl: 'partials/singletracksequentproof.html'
    };
  }]);