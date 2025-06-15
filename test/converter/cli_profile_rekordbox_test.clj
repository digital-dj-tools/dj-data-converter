(ns converter.cli-profile-rekordbox-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.test.check.generators]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.cli-profile :as profile]
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.test-utils :as test]
   [converter.universal.core :as u]))

; https://stackoverflow.com/questions/31735423/how-to-pass-a-value-from-a-fixture-to-a-test-with-clojure-test

(def ^:dynamic *dir* nil)

(defn with-tmp-dir
  [f]
  (binding [*dir* (test/tmpdir)]
    (.mkdir (io/file *dir*))
    (f)
    (test/rmdir (io/file *dir*))))

(def input-file "cli-profile-rekordbox.xml")

(defn with-input-file
  [f]
  (profile/setup (io/file *dir* input-file)
                 (r/dj-playlists-spec test/config)
                 (spec/such-that-spec u/item-from-rekordbox-spec
                                      u/item-contains-total-time?
                                      100)
                 r/xml-transformer
                 100)
  (f))

(use-fixtures :each with-tmp-dir with-input-file)

(deftest ^:profile rekordbox->traktor
  (let [arguments {:input-file (str (io/file *dir* input-file))
                   :output-file (str (io/file *dir* "cli-profile-traktor.nml"))}
        options {:profile-min-level 0}
        result (cli/process app/basic-edition arguments options)]
    (is (= 0 (first result)))))