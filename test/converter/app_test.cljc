(ns converter.app-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [converter.app :as app]
   [converter.spec :as spec]
   [plumula.mimolette.alpha :refer [defspec-test]]))

(defspec-test
  convert
  `app/convert
  {:opts {:num-tests 10}})

(deftest convert-without-check-error-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) (app/convert {} {})))
  (try (app/convert {} {})
       (catch #?(:clj Throwable :cljs :default) e
         (is (= ::spec/decode (:type (ex-data e)))))))

(deftest convert-with-check-error-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) (app/convert {} {:check-input true})))
  (try (app/convert {} {:check-input true})
       (catch #?(:clj Throwable :cljs :default) e
         (is (= ::app/convert (:type (ex-data e)))))))
