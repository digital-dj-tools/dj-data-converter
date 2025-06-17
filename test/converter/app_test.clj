(ns converter.app-test
  (:require
   [converter.app :as app] 
   [converter.test-utils :refer [deftest-check]]))

(deftest-check test-convert `app/convert 10)
