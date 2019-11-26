(ns converter.cli-profile-traktor-test
  (:require
   [clojure.data.xml :as xml]
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.test.check.generators]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.cli-profile :as profile]
   [converter.config :as config]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.test-utils :as test]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]))

; https://stackoverflow.com/questions/31735423/how-to-pass-a-value-from-a-fixture-to-a-test-with-clojure-test

(def ^:dynamic *dir* nil)

(defn with-tmp-dir
  [f]
  (binding [*dir* (test/tmpdir)]
    (.mkdir (io/file *dir*))
    (f)
    (test/rmdir (io/file *dir*))))

(def input-file "cli-profile-traktor.nml")

(defn with-input-file
  [f]
  (profile/setup (io/file *dir* input-file)
                 (t/nml-spec test/config)
                 (spec/such-that-spec u/item-from-traktor-spec
                                      u/item-contains-total-time?
                                      100)
                 t/xml-transformer
                 100)
  (f))

(use-fixtures :each with-tmp-dir with-input-file)

(deftest ^:profile traktor->rekordbox
  (let [arguments {:input-file (str (io/file *dir* input-file))
                   :output-file (str (io/file *dir* "cli-profile-rekordbox.xml"))}
        options {:profile-min-level 0}
        result (cli/process app/basic-edition arguments options)]
    (is (= 0 (first result)))))

