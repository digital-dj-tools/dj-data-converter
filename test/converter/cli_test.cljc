(ns converter.cli-test
  (:require
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   [clojure.test :refer [deftest testing is]]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.test-utils :as test]))

(deftest process-test
  (testing "Traktor to Rekordbox, Traktor file is present and valid"
    (let [arguments {:input-file "test-resources/collection.nml"
                     :output-file "/tmp/rekordbox.xml"}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 0 (first result)))))
  (testing "Traktor to Rekordbox, Traktor file is missing"
    (let [arguments {:input-file "test-resources/collection-missing.nml"
                     :output-file "/tmp/rekordbox.xml"}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 2 (first result)))))
  (testing "Rekordbox to Traktor, Rekordbox file is present and valid"
    (let [arguments {:input-file "test-resources/rekordbox.xml"
                     :output-file "/tmp/collection.nml"}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 0 (first result))))))