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

; (deftest convert-without-check-error-test
;   (let [config {:converter app/traktor->rekordbox}
;         options {}]
;     (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) (app/convert-data {} config options)))
;     (try (app/convert-data {} config options)
;          (catch #?(:clj Throwable :cljs :default) e
;            (is (= ::spec/decode (:type (ex-data e))))))))

; (deftest convert-with-check-error-test
;   (let [config {:converter app/traktor->rekordbox}
;         options {:check-input true}]
;     (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) (app/convert-data {} config options)))
;     (try (app/convert-data {} config options)
;          (catch #?(:clj Throwable :cljs :default) e
;            (is (= ::app/convert (:type (ex-data e))))))))
