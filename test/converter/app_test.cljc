(ns converter.app-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [converter.app :as app]
   [plumula.mimolette.alpha :refer [defspec-test]]))

(defspec-test
  convert
  `app/convert
  {:opts {:num-tests 10}})

(deftest convert-decode-error-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) (app/convert {}))))
