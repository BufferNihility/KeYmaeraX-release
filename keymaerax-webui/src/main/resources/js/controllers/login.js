angular.module('keymaerax.controllers').controller('LoginCtrl',
  function ($scope, $cookies, $cookieStore, $uibModal, $http) {
    $scope.defaultLogin = function() { login("guest", "guest") }

    $scope.username = ""
    $scope.password = ""

    $scope.processLogin = function() { login($scope.username, $scope.password) }

    $scope.processRegistration = function() {
      $http.post("/user/" + $scope.username + "/" + $scope.password)
        .then(function(response) {
          if (response.data.success === true) { $scope.processLogin(); }
          else { showMessage($uibModal, "Registration failed", "Sorry, user name is already taken. Please choose a different name."); }
        });
    }

    login = function(username, password) {
      $http.get("/user/" + username + "/" + password)
        .then(function(response) {
          if(response.data.type == "LoginResponse") {
            if(response.data.success) {
              //@todo $cookieStore; also: AuthenticationService
              document.cookie = response.data.key + " = " + response.data.value + "; path=/";
              document.location.href = "/dashboard.html"
            } else {
              showMessage($uibModal, "Login failed", "Please check user name and/or password");
            }
          }
        });
    }
  });
