(ns converter.offset-test
  (:require
   [converter.offset :as o]
   [converter.spec :as spec]
   [plumula.mimolette.alpha :refer [defspec-test]]))

(defspec-test
  sign
  `o/sign
  {:opts {:num-tests 100}})
