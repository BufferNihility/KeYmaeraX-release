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

      scope.fetchBranchRoot = function(sectionIdx) {
        var section = scope.deductionPath.sections[sectionIdx];
        var sectionEnd = section.path[section.path.length-1];
        $http.get('proofs/user/' + scope.userId + '/' + scope.proofId + '/' + scope.nodeId + '/' + sectionEnd + '/branchroot').success(function(data) {
          addBranchRoot(data, scope.agenda.itemsMap[scope.nodeId], sectionIdx);
        });
      }

      scope.fetchPathAll = function(sectionIdx) {
        var section = scope.deductionPath.sections[sectionIdx];
        var sectionEnd = section.path[section.path.length-1];
        if (sectionEnd !== scope.proofTree.root) {
          $http.get('proofs/user/' + scope.userId + '/' + scope.proofId + '/' + scope.nodeId + '/' + sectionEnd + '/pathall').success(function(data) {
            // TODO use numParentsUntilComplete to display some information
            $.each(data.path, function(i, ptnode) { updateProof(ptnode); });
          });
        }
      }

      /**
       * Adds a node to proof tree if not already present; otherwise, updates the node with fetched rule and children
       * @param proofTreeNode The node to add.
       */
      addProofTreeNode = function(proofTreeNode) {
        if (scope.proofTree.nodesMap[proofTreeNode.id] === undefined) {
          scope.proofTree.nodesMap[proofTreeNode.id] = proofTreeNode;
        } else {
          scope.proofTree.nodesMap[proofTreeNode.id].children = proofTreeNode.children;
          scope.proofTree.nodesMap[proofTreeNode.id].rule = proofTreeNode.rule;
        }
        // update parent pointer of children, if loaded
        $.each(proofTreeNode.children, function(i, v) {
          var child = scope.proofTree.nodesMap[v];
          if (child !== undefined) scope.proofTree.nodesMap[v].parent = proofTreeNode.id;
        });
      }

      /**
       * Updates the specified section by adding the proof tree node. If the node has more than 1 child, a new section
       * after the specified section is started.
       * @param proofTreeNode The node to add.
       * @param sectionIdx The section where to add the proof node.
       */
      updateSection = function(proofTreeNode, agendaItem, sectionIdx) {
        var section = agendaItem.deduction.sections[sectionIdx];
        var sectionEnd = section.path[section.path.length-1];
        if (proofTreeNode.children.length > 1) {
          if (sectionIdx+1 >= agendaItem.deduction.sections.length || agendaItem.deduction.sections[sectionIdx+1].path[0] !== proofTreeNode.id) {
            // start new section with parent, parent section is complete if parent is root
            agendaItem.deduction.sections.splice(sectionIdx+1, 0, {path: [proofTreeNode.id], isCollapsed: false, isComplete: proofTreeNode.parent === proofTreeNode.id});
          } // else: parent already has its own section, see fetchBranchRoot
          // in any case: child's section is loaded completely if its ending in one of the children of the proof tree node
          section.isComplete = proofTreeNode.children.indexOf(sectionEnd) >= 0;
        } else {
          // parent has exactly 1 child, append parent to child's section
          if (sectionIdx === -1) {
            console.error('Expected a unique path section ending in a child of ' + proofTreeNode.id + ', but agenda item ' + agendaItem.id +
              ' has ' + agendaItem.sections + ' as path sections');
          } else if (proofTreeNode.parent !== proofTreeNode.id) {
            section.path.push(proofTreeNode.id);
            var parentCandidate =
              (sectionIdx+1 < agendaItem.deduction.sections.length
              ? scope.proofTree.nodesMap[agendaItem.deduction.sections[sectionIdx+1].path[0]]
              : undefined);
            section.isComplete =
              parentCandidate !== undefined && parentCandidate.children.indexOf(proofTreeNode.id) >= 0;
          } else {
            if (sectionIdx+1 < agendaItem.deduction.sections.length) {
              console.error('Received proof tree root, which can only be added to last section, but ' + sectionIdx +
                ' is not last section in ' + agendaItem.deduction.sections);
            } else {
              agendaItem.deduction.sections.splice(sectionIdx+1, 0, {path: [proofTreeNode.id], isCollapsed: false, isComplete: true});
              section.isComplete = proofTreeNode.children.indexOf(sectionEnd) >= 0;
            }
          }
        }
      }

      /**
       * Adds a proof tree node and updates the agenda sections.
       */
      updateProof = function(proofTreeNode) {
        addProofTreeNode(proofTreeNode);

        // append parent to the appropriate section in all relevant agenda items
        var items = $.map(proofTreeNode.children, function(e) { return scope.agenda.itemsByProofStep(e); }); // JQuery flat map
        $.each(items, function(i, v) {
          var childSectionIdx = childSectionIndex(v, proofTreeNode);
          updateSection(proofTreeNode, v, childSectionIdx);
        });
      }

      /**
       * Adds the specified proof tree node as branch root to the specified section.
       */
      addBranchRoot = function(proofTreeNode, agendaItem, sectionIdx) {
        addProofTreeNode(proofTreeNode);
        updateSection(proofTreeNode, agendaItem, sectionIdx);

        // append parent to the appropriate section in all relevant agenda items
        var items = $.map(proofTreeNode.children, function(e) { return scope.agenda.itemsByProofStep(e); });
        $.each(items, function(i, v) {
          var childSectionIdx = childSectionIndex(v, proofTreeNode);
          updateSection(proofTreeNode, v, childSectionIdx);
        });
      }

      /**
       *  Returns the index of the specified proof tree node's child that is referred to in agendaItem (only a unique
       *  such child exists).
       */
      childSectionIndex = function(agendaItem, proofTreeNode) {
        for (var i = 0; i < agendaItem.deduction.sections.length; i++) {
          var section = agendaItem.deduction.sections[i];
          if (proofTreeNode.children.indexOf(section.path[section.path.length - 1]) >= 0) return i;
        }
        return -1;
      }

      /** Pretty prints sequent JSON into HTML */
      scope.tooltip = function(sequent) {
        // TODO call the pretty printer
        return sequent;
      }

      /** Filters sibling candidates: removes this item's goal and path */
      scope.siblingCandidates = function(candidates) {
        var item = scope.agenda.itemsMap[scope.nodeId];
        var fp = flatPath(item);
        return candidates.filter(function(e) { return fp.indexOf(e) === -1; });
      }

      scope.onUseAt = function(formulaId, axiomId) {
        $http.get('proofs/user/' + scope.userId + '/' + scope.proofId + '/' + scope.nodeId + '/' + scope.deductionPath.sections[0].path[0] + '/' + formulaId + '/use/' + axiomId).success(function(data) {
          scope.proofTree.nodesMap[data.id] = data;
          scope.proofTree.nodesMap[data.parent].children = [data.id];
          scope.proofTree.nodesMap[data.parent].rule = data.byRule;
          // prepend new open goal to deduction path
          scope.agenda.itemsMap[scope.nodeId].deduction.sections[0].path.unshift(data.id);
        });
      }

      scope.fetchParentRightClick = function(event) {
        event.stopPropagation();
        // emulate hoverable popover (to come in later ui-bootstrap version) with hide on blur (need to focus for blur)
        event.target.focus();
      }

      flatPath = function(item) {
        var result = [];
        $.each(item.deduction.sections, function(i, v) { $.merge(result, v.path); });
        return result;
      }

      /**
       * Fetches the parent of goal 'goalId' and updates the agenda item 'nodeId' to show an extended sequent
       * (parent appended as previous proof step below deduction view).
       */
      scope.fetchSectionParent = function(section) {
        var goalId = section.path[section.path.length - 1];
        $http.get('proofs/user/' + scope.userId + '/' + scope.proofId + '/' + scope.nodeId + '/' + goalId + '/parent').success(function(data) {
          updateProof(data);
        });
      }

      scope.deductionPath.sections = $.map(scope.deductionPath.sections, function(v, i) { return {path: v, isCollapsed: false}; });
      scope.deductionPath.isCollapsed = false;
    }

    return {
        restrict: 'AE',
        scope: {
            userId: '=',
            proofId: '=',
            nodeId: '=',
            deductionPath: '=',
            proofTree: '=',
            agenda: '=',
            readOnly: '=?'
        },
        link: link,
        templateUrl: 'partials/singletracksequentproof.html'
    };
  }]);