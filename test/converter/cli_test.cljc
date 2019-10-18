(ns converter.cli-test
  (:require
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   [clojure.test :refer [deftest is testing use-fixtures]]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.test-utils :as test]))

(def dir (test/tmpdir))

(defn with-tmp-dir
  [f]
  (.mkdir (io/file dir))
  (f)
  (test/rmdir (io/file dir)))

(use-fixtures :each with-tmp-dir)

(deftest process-test
  (testing "Traktor to Rekordbox, Traktor file is present and valid"
    (let [arguments {:input-file "test-resources/collection.nml"
                     :output-file (io/file dir "rekordbox.xml")}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 0 (first result)))))
  (testing "Traktor to Rekordbox, Traktor file is missing"
    (let [arguments {:input-file "test-resources/collection-missing.nml"
                     :output-file (io/file dir "rekordbox.xml")}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 2 (first result)))))
  (testing "Rekordbox to Traktor, Rekordbox file is present and valid"
    (let [arguments {:input-file "test-resources/rekordbox.xml"
                     :output-file (io/file dir "collection.nml")}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 0 (first result))))))