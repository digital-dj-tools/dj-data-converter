(ns converter.app-test
  (:require
   [converter.app :as app]
   [converter.spec :as spec]
   [plumula.mimolette.alpha :refer [defspec-test]]))

(defspec-test
  convert
  `app/convert
  {:opts {:num-tests 10}})
