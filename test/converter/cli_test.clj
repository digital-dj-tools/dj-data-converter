(ns converter.cli-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.test-utils :as test]))

; https://stackoverflow.com/questions/31735423/how-to-pass-a-value-from-a-fixture-to-a-test-with-clojure-test

(def ^:dynamic *dir* nil)

(defn with-tmp-dir
  [f]
  (binding [*dir* (test/tmpdir)]
    (.mkdir (io/file *dir*))
    (f)
    (test/rmdir (io/file *dir*))))

(use-fixtures :each with-tmp-dir)

; TODO use generated test data instead of fixed test data

(deftest traktor-to-rekordbox-test
  (testing "Traktor to Rekordbox, Traktor file is present and valid"
    (let [arguments {:input-file (str (io/file "test-resources" "collection.nml"))
                     :output-file (str (io/file *dir* "rekordbox.xml"))}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 0 (first result))))))

(deftest traktor-to-rekordbox-input-missing-test
  (testing "Traktor to Rekordbox, Traktor file is missing"
    (let [arguments {:input-file "collection-missing.nml"
                     :output-file (str (io/file *dir* "rekordbox.xml"))}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 2 (first result))))))

(deftest rekordbox-to-traktor-test
  (testing "Rekordbox to Traktor, Rekordbox file is present and valid"
    (let [arguments {:input-file (str (io/file "test-resources" "rekordbox.xml"))
                     :output-file (str (io/file *dir* "collection.nml"))}
          options {}
          result (cli/process app/basic-edition arguments options)]
      (is (= 0 (first result))))))
