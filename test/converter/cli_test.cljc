(ns converter.cli-test
  (:require
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   [clojure.test :refer [deftest testing is]]
   [converter.app :as app]
   [converter.cli :as cli]))

(deftest process-test
  (testing "Traktor collection file is present and valid"
    (let [config {:converter app/traktor->rekordbox}
          arguments {:input-file "test-resources/collection.nml"
                     :output-file "/tmp/rekordbox.xml"}
          options {}
          result (cli/process config arguments options)]
      (is (= 0 (first result)))))
  (testing "Traktor collection file is missing"
    (let [config {:converter app/traktor->rekordbox}
          arguments {:input-file "test-resources/collection-missing.nml"
                     :output-file "/tmp/rekordbox.xml"}
          options {}
          result (cli/process config arguments options)]
      (is (= 2 (first result))))))