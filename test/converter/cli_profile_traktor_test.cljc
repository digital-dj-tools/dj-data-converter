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
   [converter.traktor.core :as t]
   [converter.universal.core :as u]))

(def dir (profile/tmpdir))

(def input-file "cli-profile-traktor.nml")

(def arguments {:input-file (str (io/file dir input-file))
                :output-file (str (io/file dir "cli-profile-rekordbox.xml"))})

(def config (config/arguments->config arguments))

(defn with-traktor-nml
  [f]
  (profile/setup dir
                 input-file
                 (t/nml-spec config)
                 (spec/such-that-spec u/item-from-traktor-spec
                                      u/item-contains-total-time? 100)
                 t/xml-transformer
                 100)
  (f)
  (profile/teardown dir))

(use-fixtures :each with-traktor-nml)

(deftest ^:profile traktor->rekordbox
  (let [result (cli/process app/basic-edition
                            arguments
                            {:profile-min-level 0}
                            config)]
    (is (= 0 (first result)))))

