(ns converter.cli-profile-rekordbox-test
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
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.universal.core :as u]))

(def dir (profile/tmpdir))

(def input-file "cli-profile-rekordbox.xml")

(def arguments {:input-file (str (io/file dir input-file))
                :output-file (str (io/file dir "cli-profile-traktor.nml"))})

(def config (config/arguments->config arguments))

(defn with-rekordbox-xml
  [f]
  (profile/setup dir
                 input-file
                 (r/dj-playlists-spec config)
                 (spec/such-that-spec u/item-from-rekordbox-spec
                                      u/item-contains-total-time? 100)
                 r/xml-transformer
                 100)
  (f)
  (profile/teardown dir))

(use-fixtures :each with-rekordbox-xml)

(deftest ^:profile rekordbox->traktor
  (let [result (cli/process app/basic-edition
                            arguments
                            {:profile-min-level 0}
                            config)]
    (is (= 0 (first result)))))

