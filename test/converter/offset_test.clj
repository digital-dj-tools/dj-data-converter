(ns converter.offset-test
  (:require
   [converter.offset :as o]
   [converter.test-utils :refer [deftest-check]]))

(deftest-check test-signed-value `o/signed-value 100)
